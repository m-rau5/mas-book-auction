package Auction;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

import java.util.*;

// the bidder as an "instance", this refers to the bidder in the case of each particular auction
public class BuyerAgent extends Agent {

    private String buyerName;
    private Set<String> genrePreferences;
    private Set<String> authorPreferences;
    private BiddingStrategies strategyType;
    private double remainingBudget;

    private final Map<String, AuctionMetadata> pendingAuctions = new HashMap<>();

    protected void setup() {
        Object[] args = getArguments();
        buyerName = (String) args[0];
        genrePreferences = new HashSet<>(Arrays.asList((String[]) args[1]));
        authorPreferences = new HashSet<>(Arrays.asList((String[]) args[2]));
        strategyType = (BiddingStrategies) args[3];
        double budget = (double) args[4];
        remainingBudget = budget;

        System.out.printf("BIDDER DETAILS - [%s] : genres: %s | authors: %s | strategy: %s | budget: %.2f%n",
                buyerName, genrePreferences, authorPreferences, strategyType, budget);

        // subscription to preferred genre/author
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                ACLMessage sub = new ACLMessage(ACLMessage.SUBSCRIBE);
                sub.addReceiver(new AID("notification", AID.ISLOCALNAME));
                Set<String> combined = new HashSet<>();
                combined.addAll(genrePreferences);
                combined.addAll(authorPreferences);
                sub.setContent(String.join(",", combined));
                send(sub);
            }
        });

        // auction logic -> parse and evaluate if to participate or not
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchOntology("AUCTION_ANNOUNCE"));
                if (msg != null && msg.getOntology().equals("AUCTION_ANNOUNCE")) {
                    String[] parts = msg.getContent().split(";", 2);
                    String auctionId = parts[0];
                    String metadata = parts[1];

                    String genre = extract("Genre=", metadata);
                    String author = extract("Author=", metadata);
                    double startPrice = Double.parseDouble(extract("StartingPrice=", metadata));
                    String type = extract("Type=", metadata);
                    boolean isDutch = "DUTCH".equalsIgnoreCase(type); // if DUTCH, we have no upper budget limit, this is a toggle for that

                    // chec budget + interest
                    boolean budgetOk   = isDutch || remainingBudget >= startPrice;
                    int minRating = metadata.contains("MinRating=") ? Integer.parseInt(extract("MinRating=", metadata)) : 0;

                    boolean genreMatch = genrePreferences.contains(genre);
                    boolean authorMatch = authorPreferences.contains(author);

                    System.out.printf("EVALUATION: [%s] Evaluating auction %s | Genre: %s | Author: %s | Start: %.2f | Budget: %.2f | MinRating: %d%n",
                            buyerName, auctionId, genre, author, startPrice, remainingBudget, minRating);

                    if ((genreMatch || authorMatch) && budgetOk) {
                        pendingAuctions.put(auctionId, new AuctionMetadata(auctionId, metadata, startPrice));

                        // last check -> reputation ok?
                        ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
                        query.addReceiver(new AID("reputation-manager", AID.ISLOCALNAME));
                        query.setOntology("REPUTATION_QUERY");
                        query.setContent(buyerName);
                        send(query);
                    } else {
                        System.out.printf("SKIP: [%s] Skipping auction %s — no interest or budget too low%n", buyerName, auctionId);
                    }
                } else {
                    block();
                }
            }
        });

        // reputation handling
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                // get reputation response inform message => handle the reputation update
                ACLMessage msg = receive(MessageTemplate.MatchOntology("REPUTATION_RESPONSE"));
                if (msg != null) {
                    int score = Integer.parseInt(msg.getContent());
                    for (String auctionId : pendingAuctions.keySet()) {
                        AuctionMetadata meta = pendingAuctions.get(auctionId);
                        int minRating = meta.metadata.contains("MinRating=") ?
                                Integer.parseInt(extract("MinRating=", meta.metadata)) : 0;

                        // score vs. seller required reputation check
                        if (score >= minRating) {
                            System.out.printf("REP CHECK: [%s] Reputation OK (%d >= %d) — joining auction %s%n",
                                    buyerName, score, minRating, auctionId);

                            Object[] bidArgs = new Object[]{
                                    auctionId,
                                    meta.metadata,
                                    buyerName,
                                    meta.startPrice,
                                    strategyType,
                                    remainingBudget
                            };

                            // synchronization for agent joining the auction
                            synchronized(getContainerController()) {
                                AgentController ac;
                                try {
                                    ac = getContainerController()
                                            .createNewAgent(
                                                    buyerName + "-bidder-" + auctionId,
                                                    BiddingAgent.class.getName(),
                                                    bidArgs
                                            );
                                    ac.start();
                                } catch (StaleProxyException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        } else {
                            System.out.printf("SKIP: [%s] Skipped auction %s — insufficient reputation (%d < %d)%n",
                                    buyerName, auctionId, score, minRating);
                        }
                    }
                    pendingAuctions.clear();
                } else {
                    block();
                }
            }
        });

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchOntology("AUCTION_RESULT"));
                if (msg != null &&  msg.getOntology().equals("AUCTION_RESULT")) {
                    String[] parts = msg.getContent().split("at price ");
                    try {
                        double spent = Double.parseDouble(parts[1]);
                        remainingBudget -= spent;
                        String cname = ColorUtil.colorize(buyerName);
                        System.out.printf("WINNER: [%s] Won auction, spent %.2f, remaining budget: %.2f%n",
                                cname, spent, remainingBudget);
                    } catch (Exception e) {
                        System.err.printf("ERROR: [%s] Failed to parse spending from message: %s%n", buyerName, msg.getContent());
                    }
                } else {
                    block();
                }
            }
        });
    }

    private String extract(String key, String metadata) {
        for (String part : metadata.split(";")) {
            if (part.startsWith(key)) {
                return part.substring(key.length());
            }
        }
        return "";
    }

    private static class AuctionMetadata {
        String auctionId;
        String metadata;
        double startPrice;

        AuctionMetadata(String auctionId, String metadata, double startPrice) {
            this.auctionId = auctionId;
            this.metadata = metadata;
            this.startPrice = startPrice;
        }
    }
}
