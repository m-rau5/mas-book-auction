package Auction;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class AuctionManagerAgent extends Agent {

    private final Map<String, AuctionInfo> auctions = new HashMap<>();
    private final Queue<ACLMessage> pendingAuctions = new LinkedList<>();
    private final Map<String, List<ACLMessage>> pendingBlindBids = new HashMap<>();

    // A bit messy rn, will refactor
    protected void setup() {
        System.out.println(getLocalName() + " ready.");

        // handle new auction requests and bidder registrations
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST: // NEW_AUCTION
                            if (msg.getContent().startsWith("NEW_AUCTION")) {
                                if (isAuctionActive()) {
                                    pendingAuctions.add(msg);
                                    System.out.println("~~ Auction queued: " + msg.getContent() + " ~~");
                                } else {
                                    startAuctionFromMessage(msg);
                                }
                            }
                            break;

                        case ACLMessage.SUBSCRIBE: // Bidder registers
                            if ("REGISTER".equals(msg.getOntology())) {
                                String auctionId = msg.getContent();
                                AuctionInfo ai = auctions.get(auctionId);
                                if (ai != null && ai.active) {
                                    ai.bidders.add(msg.getSender());
                                    String buyerName = msg.getSender().getLocalName().split("-bidder-")[0];
                                    sendReputationUpdate(buyerName, "joined");
                                }
                            }
                            break;

                        case ACLMessage.CANCEL: // this is to remove the bidders that are no longer bidding on something
                            if (msg.getOntology().equals("DEREGISTER")) {
                                String auctionId = msg.getContent();
                                AuctionInfo ai = auctions.get(auctionId);
                                if (ai != null) {
                                    ai.bidders.remove(msg.getSender());
                                    System.out.printf("AUCTION QUIT: %s left auction %s (remaining: %d)%n",
                                            msg.getSender().getLocalName(),
                                            auctionId,
                                            ai.bidders.size()
                                    );
                                    String buyerName = msg.getSender().getLocalName().split("-bidder-")[0];
                                    sendReputationUpdate(buyerName, "earlyExit");
                                }
                            }
                            break;
                        case ACLMessage.PROPOSE:
                            for (String auctionId : auctions.keySet()) {
                                AuctionInfo ai = auctions.get(auctionId);
                                if (ai.type == AuctionType.BLIND && ai.active && ai.bidders.contains(msg.getSender())) {
                                    pendingBlindBids.computeIfAbsent(auctionId, _ -> new ArrayList<>()).add(msg);
                                    //debug - to see if stored right
//                                    System.out.printf("Stored blind bid for %s: %s -> %s%n", auctionId, msg.getSender().getLocalName(), msg.getContent());
                                }
                            }
                            break;
                    }
                } else {
                    block();
                }
            }
        });

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                MessageTemplate mt = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                        MessageTemplate.MatchOntology("USER_BID")
                );
                ACLMessage m = receive(mt);
                if (m != null) {
                    String aucId = m.getConversationId();
                    AuctionInfo ai = auctions.get(aucId);
                    if (ai != null && ai.active) {
                        // If it’s a blind auction, stash it instead of updating currentPrice
                        if (ai.type == AuctionType.BLIND) {
                            pendingBlindBids
                                    .computeIfAbsent(aucId, _ -> new ArrayList<>())
                                    .add(m);
                            System.out.printf("[AuctionManager] Stored sealed user bid: %s%n", m.getContent());
                        } else {
                            // Existing immediate update for English/Dutch
                            double bid = Double.parseDouble(m.getContent());
                            if (bid > ai.currentPrice) {
                                ai.currentPrice = bid;
                                ai.highestBidder = m.getSender();
                                ai.finalWinner   = m.getSender();
//                                System.out.printf("[AuctionManager] Manual bid: %.2f (prev: %.2f)%n",
//                                        bid, prev);
                            }
                        }
                    }
                } else {
                    block();
                }
            }
        });

        // auction logic runs every 2 seconds
        addBehaviour(new TickerBehaviour(this, 2000) {
            protected void onTick() {
                for (AuctionInfo ai : auctions.values()) {
                    if (!ai.active) continue;
                    ai.totalRounds++;

                    // ✅ Check for zero remaining bidders
                    if (ai.bidders.isEmpty()) {
                        ai.active = false;
                        System.out.printf("STOP AUCTION: Auction %s is CLOSED — no more bidders.%n", ai.id);

                        ACLMessage endMsg = new ACLMessage(ACLMessage.INFORM);
                        endMsg.addReceiver(ai.seller);
                        endMsg.setContent("Auction " + ai.id + " closed — no more active bidders.");
                        send(endMsg);

                        // Start next pending auction if any
                        if (!pendingAuctions.isEmpty()) {
                            ACLMessage next = pendingAuctions.poll();
                            startAuctionFromMessage(next);
                        }

                        continue; // Skip rest of this auction loop
                    }

                    // Auction logic for ENGLISH and DUTCH auctions
                    // BLIND also implemented but in startAuctionFromMessage, will be integrated here later TBD
                    if (ai.type == AuctionType.ENGLISH) {
                        List<AID> biddersSnapshot = new ArrayList<>(ai.bidders);
                        for (AID bidder : biddersSnapshot) {
//                        System.out.println("-------DEBUG-- " + ai.bidders.size() + "   " + ai.highestBidder + "  " + bidder);
                            // edge-case - solo bid
                            if (ai.bidders.size() == 1 && bidder.equals(ai.highestBidder)){
                                continue;
                            }
                            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                            cfp.addReceiver(bidder);
                            cfp.setContent(String.valueOf(ai.currentPrice));
                            send(cfp);
                        }

                        long end = System.currentTimeMillis() + 1000;
                        // since we treat user bid a bit more specially, ignore their bids
                        MessageTemplate mt = MessageTemplate.and(
                                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                                MessageTemplate.not(MessageTemplate.MatchOntology("USER_BID"))
                        );
                        boolean gotNewBid = false;

                        while (System.currentTimeMillis() < end) {
                            ACLMessage prop = receive(mt);
                            if (prop != null) {
                                double offered = Double.parseDouble(prop.getContent());
                                if (offered > ai.currentPrice) {
                                    ai.currentPrice = offered;
                                    ai.highestBidder = prop.getSender();
                                    ai.finalWinner = prop.getSender(); // track final winner
                                    gotNewBid = true;
                                }
                            }
                        }


                        if (!gotNewBid || ai.bidders.size() == 1 || ai.totalRounds >= 5) {
                            ai.roundsWithoutBid++;
                            if (ai.roundsWithoutBid >= 1) { // end auction after 1 empty round -> catches 1-bidder edge case
//                            System.out.println("CURRENT BIDDERS= " + ai.bidders);
                                ai.active = false;

                                String rawWinner = ai.finalWinner != null ? ai.finalWinner.getLocalName() : "None";
                                String shortWinner = rawWinner.contains("-") ? rawWinner.split("-")[0]  : rawWinner;

                                String result = String.format(
                                        "Auction %s CLOSED. Winner: %s | Final Price: %.2f | Book Info: %s",
                                        ai.id,
                                        shortWinner,
                                        ai.currentPrice,
                                        ai.metadata
                                );
                                if (ai.totalRounds >= 5){
                                    result = result + "\n--Closed due to 5 round limit.";
                                }
                                System.out.println(result);

                                // notify seller, winner, gui
                                ACLMessage informSeller = new ACLMessage(ACLMessage.INFORM);
                                informSeller.addReceiver(ai.seller);
                                informSeller.setContent(result);
                                send(informSeller);

                                if (ai.finalWinner != null) {
                                    String bidderName = ai.finalWinner.getLocalName(); // e.g., buyer2-bidder-abc
                                    String buyerName = bidderName.split("-bidder-")[0]; // e.g., buyer2
                                    sendReputationUpdate(buyerName, "won");

                                    ACLMessage winnerMsg = new ACLMessage(ACLMessage.INFORM);
                                    winnerMsg.addReceiver(new AID(buyerName, AID.ISLOCALNAME));
                                    winnerMsg.setOntology("AUCTION_RESULT");
                                    winnerMsg.setContent("You won auction " + ai.id + " at price " + ai.currentPrice);
                                    send(winnerMsg);

                                    // notify user only
                                    ACLMessage guiMsg = new ACLMessage(ACLMessage.INFORM);
                                    guiMsg.addReceiver(new AID("user-agent", AID.ISLOCALNAME));
                                    guiMsg.setOntology("AUCTION_RESULT");
                                    guiMsg.setContent("Another buyer won this auction.");
                                    send(guiMsg);
                                }

                                ACLMessage informGui = new ACLMessage(ACLMessage.INFORM);
                                informGui.addReceiver(new AID("auction-gui", AID.ISLOCALNAME));
                                informGui.setOntology("AUCTION_RESULT");
                                informGui.setContent(result);
                                send(informGui);

                                if (!pendingAuctions.isEmpty()) {
                                    ACLMessage next = pendingAuctions.poll();
                                    startAuctionFromMessage(next);
                                }
                            }
                        } else {
                            ai.roundsWithoutBid = 0;
                            ai.highestBidder = null;
                        }
                    } else if (ai.type == AuctionType.DUTCH) {
                        // lower price by 5% every auction cycle
                        ai.currentPrice -= ai.currentPrice * 0.05;
                        if (ai.currentPrice < 1) {
                            ai.currentPrice = 1;
                        }
                        System.out.printf("Dutch current price: [%.6f]%n", ai.currentPrice);

                        // broadcast a CFP
                        ACLMessage dutchMsg = new ACLMessage(ACLMessage.CFP);
                        for (AID bidder : ai.bidders) {
                            dutchMsg.addReceiver(bidder);
                        }
                        dutchMsg.setContent(String.valueOf(ai.currentPrice));
                        send(dutchMsg);

                        // collect PROPOSES from potential bidders, 2s buffer
                        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                        long deadline = System.currentTimeMillis() + 2000;
                        List<ACLMessage> proposals = new ArrayList<>();
                        while (System.currentTimeMillis() < deadline) {
                            ACLMessage p = receive(mt);
                            if (p != null) {
                                proposals.add(p);
                            } else {
                                block(deadline - System.currentTimeMillis());
                            }
                        }

                        // if anyone bid, pick the one with highest threshold
                        if (!proposals.isEmpty()) {
                            AID winner = null;
                            double bestThreshold = -1;
                            for (ACLMessage p : proposals) {
                                String content = p.getContent().trim();
                                double threshold;
                                if (content.contains(";")) {
                                    // BOT sent "price;threshold"
                                    threshold = Double.parseDouble(content.split(";")[1]);
                                } else {
                                    // GUI sent just the number → use that as threshold
                                    threshold = Double.parseDouble(content);
                                }
                                if (threshold > bestThreshold) {
                                    bestThreshold   = threshold;
                                    winner          = p.getSender();
                                }
                            }

                            // auction end logic
                            ai.finalWinner = winner;
                            ai.active = false;
                            String winnerName = winner.getLocalName().split("-")[0];
                            String result = String.format(
                                    "Dutch auction %s CLOSED. Winner: %s | Final Price: %.2f | Book Info: %s",
                                    ai.id, winnerName, ai.currentPrice, ai.metadata
                            );
                            System.out.println(result);

                            // notify each party: seller -> winner -> user gui
                            sendReputationUpdate(winnerName, "won");
                            ACLMessage informSeller = new ACLMessage(ACLMessage.INFORM);
                            informSeller.addReceiver(ai.seller);
                            informSeller.setContent(result);
                            send(informSeller);

                            ACLMessage informWinner = new ACLMessage(ACLMessage.INFORM);
                            informWinner.setOntology("AUCTION_RESULT");
                            informWinner.addReceiver(new AID(winnerName, AID.ISLOCALNAME));
                            informWinner.setContent("You won Dutch auction " + ai.id + " at price " + ai.currentPrice);
                            send(informWinner);

                            ACLMessage guiMsg = new ACLMessage(ACLMessage.INFORM);
                            guiMsg.addReceiver(new AID("user-agent", AID.ISLOCALNAME));
                            guiMsg.setOntology("AUCTION_RESULT");
                            guiMsg.setContent(result);
                            send(guiMsg);

                            // bidder cleanup logic + next auction
                            for (AID b : ai.bidders) {
                                ACLMessage canc = new ACLMessage(ACLMessage.CANCEL);
                                canc.addReceiver(b);
                                send(canc);
                            }

                            if (!pendingAuctions.isEmpty()) {
                                ACLMessage next = pendingAuctions.poll();
                                startAuctionFromMessage(next);
                            }
                        }
                    }
                }
            }
        });
    }

    private boolean isAuctionActive() {
        return auctions.values().stream().anyMatch(ai -> ai.active);
    }

    private void sendReputationUpdate(String buyerName, String event) {
        ACLMessage update = new ACLMessage(ACLMessage.INFORM);
        update.setOntology("REPUTATION_UPDATE");
        update.setContent(buyerName + ";" + event);
        update.addReceiver(new AID("reputation-manager", AID.ISLOCALNAME));
        send(update);
    }

    // helper function to parse the string with all the auction details
    // TEMPORARY: also handles some BLIND bidding auction logic (will be moved to a separate function)
    private void startAuctionFromMessage(ACLMessage msg) {
        String auctionId = UUID.randomUUID().toString();
        String[] parts = msg.getContent().split(";");
        double start = Double.parseDouble(
                msg.getContent().split("StartingPrice=")[1].split(";")[0]
        );
        String typeStr = Arrays.stream(parts)
                .filter(p -> p.startsWith("Type="))
                .findFirst()
                .map(p -> p.substring(5))
                .orElse("ENGLISH");
        AuctionType type = AuctionType.valueOf(typeStr.toUpperCase());

        AuctionInfo ai = new AuctionInfo(auctionId, msg.getSender(), msg.getContent(), start, type);
        auctions.put(auctionId, ai);

        System.out.println("Started auction: " + ai.metadata);

        // notify notification agent to inform interested buyers
        ACLMessage announce = new ACLMessage(ACLMessage.INFORM);
        announce.setOntology("AUCTION_ANNOUNCE");
        announce.setContent(auctionId + ";" + msg.getContent());
        announce.addReceiver(new AID("notification", AID.ISLOCALNAME));
        announce.addReceiver(new AID("user-agent", AID.ISLOCALNAME)); // for user
        send(announce);

        // BLIND Auction logic
        if (type == AuctionType.BLIND){
            addBehaviour(new WakerBehaviour(this, 10000) {
                protected void onWake() {
                    //brodcast the CFP
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (AID b : ai.bidders) {
                        cfp.addReceiver(b);
                    }
                    cfp.setContent(String.valueOf(ai.currentPrice));
                    send(cfp);

                    //  collect the COMPUTER blind bids for 2s (user's are collected separately, before this)
                    Map<AID, Double> sealed = new HashMap<>();
                    MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                    long deadline = System.currentTimeMillis() + 2000;
                    while (System.currentTimeMillis() < deadline) {
                        ACLMessage bid = receive(mt);
                        if (bid != null) {
                            try {
                                double val = Double.parseDouble(bid.getContent());
                                sealed.put(bid.getSender(), val);
                            } catch (NumberFormatException ignored) {}
                        } else {
                            block(deadline - System.currentTimeMillis());
                        }
                    }

                    // pick winner, build winning string and send it + notify everyone else of results
                    AID winner = null;
                    double max = -1;
                    for (var e : sealed.entrySet()) {
                        if (e.getValue() > max) {
                            max = e.getValue();
                            winner = e.getKey();
                        }
                    }
                    ai.active = false;
                    ai.finalWinner = winner;
                    ai.currentPrice = max;

                    String who = (winner != null) ? winner.getLocalName().split("-")[0] : "None";
                    String result = String.format(
                            "Blind auction %s CLOSED. Winner: %s | Final Price: %.2f | Book Info: %s",
                            ai.id, who, max, ai.metadata
                    );

                    ACLMessage infS = new ACLMessage(ACLMessage.INFORM);
                    infS.addReceiver(ai.seller);
                    infS.setContent(result);
                    send(infS);

                    if (winner != null) {
                        ACLMessage infW = new ACLMessage(ACLMessage.INFORM);
                        infW.setOntology("AUCTION_RESULT");
                        infW.addReceiver(new AID(who, AID.ISLOCALNAME));
                        infW.setContent("You won Blind auction " + ai.id + " at price " + max);
                        send(infW);
                    }

                    ACLMessage gui = new ACLMessage(ACLMessage.INFORM);
                    gui.setOntology("AUCTION_RESULT");
                    gui.addReceiver(new AID("user-agent", AID.ISLOCALNAME));
                    gui.setContent(result);
                    send(gui);

                    // start next auction if any
                    if (!pendingAuctions.isEmpty()) {
                        startAuctionFromMessage(pendingAuctions.poll());
                    }
                }
            });
        }
    }
}
