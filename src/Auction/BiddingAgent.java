package Auction;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;


// bidder -> the bidder as a "concept", this is the bidder overall, no matter the auction
public class BiddingAgent extends Agent {

    private String auctionId;
    private String metadata;
    private double startPrice;      // (for DUTCH) - starter price
    private BiddingStrategy strategy;
    private double budget;
    private double lastOwnBid = -1;  // track last bid placed

    @Override
    protected void setup() {
        Object[] args = getArguments();
        auctionId = (String) args[0];
        metadata = (String) args[1];
        String buyerName = (String) args[2];
        startPrice = (double) args[3];  // store opening price
        BiddingStrategies stratType = (BiddingStrategies) args[4];
        budget = (double) args[5];
        String cname = buyerName;

        try {
            cname = ColorUtil.colorize(buyerName);
        } catch (Exception ignored) {}

        switch (stratType) {
            case ONESHOT    -> strategy = new OneShotStrategy();
            case PERIODIC   -> strategy = new PeriodicStrategy();
            case ALWAYSFIRST-> strategy = new AlwaysFirstStrategy();
            case CAUTIOUS   -> strategy = new CautiousStrategy();
        }

        System.out.printf("[%s] Starting to bid on Auction ID: %s | Strategy: %s | Budget: %.2f | Start Price: %.2f\n",
                cname, auctionId, stratType.name(), budget, startPrice);

        // register bid with AuctionManager
        ACLMessage reg = new ACLMessage(ACLMessage.SUBSCRIBE);
        reg.addReceiver(new AID("auction-manager", AID.ISLOCALNAME));
        reg.setOntology("REGISTER");
        reg.setContent(auctionId);
        send(reg);

        // handle BLIND auctions: wait for single CFP then bid and die
        if (isBlindAuction(metadata)) {
            addBehaviour(new CyclicBehaviour() {
                public void action() {
                    ACLMessage cfp = receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
                    if (cfp != null) {
                        double highest = Double.parseDouble(cfp.getContent());
                        double bidVal = strategy.calculateBid(highest, budget);
                        if (bidVal > 0 && bidVal <= budget) {
                            ACLMessage bid = new ACLMessage(ACLMessage.PROPOSE);
                            bid.addReceiver(new AID("auction-manager", AID.ISLOCALNAME));
                            bid.setContent(String.valueOf(bidVal));
                            send(bid);
                            System.out.printf("[%s] ~~ Blind bid placed: %.2f%n", getLocalName(), bidVal);
                        } else {
                            System.out.printf("[%s] XX Skipped blind auction (calculated: %.2f)%n",
                                    getLocalName(), bidVal);
                        }
                        doDelete();
                    } else {
                        block();
                    }
                }
            });
            return;
        }

        // Handle CFPs for ENGLISH / DUTCH auctions
        String finalCname = cname;
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage cfp = receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
                if (cfp != null) {
                    double calledPrice = Double.parseDouble(cfp.getContent());

                    // DUTCH auction logic: wait until price <= threshold
                    if (metadata.contains("Type=DUTCH")) {
                        // compute the threshold for acceptance based on startPrice
                        double threshold = strategy.calculateBid(startPrice, budget);
                        if (calledPrice <= threshold) {
                            // Accept: send both price and threshold
                            ACLMessage bid = cfp.createReply();
                            bid.setPerformative(ACLMessage.PROPOSE);
                            bid.setOntology("BID_ACCEPTED");
                            bid.setContent(String.format("%.2f;%.2f", calledPrice, threshold));
                            send(bid);
                            System.out.printf("[%s] Dutch bid ACCEPTED at %.2f (threshold: %.2f)%n",
                                    finalCname, calledPrice, threshold);
                            doDelete();  // exit after bidding
                        }
                    }
                    // ENGLISH auction logic
                    else {
                        if (lastOwnBid == calledPrice) {
                            System.out.printf("[%s] Skipping bid — already highest (%.2f)%n", finalCname, calledPrice);
                            return;
                        }

                        // compute threshold, bid if still in accepted limit
                        double bidVal = strategy.calculateBid(calledPrice, budget);
                        if (bidVal > calledPrice && bidVal <= budget) {
                            lastOwnBid = bidVal;
                            ACLMessage bid = cfp.createReply();
                            bid.setPerformative(ACLMessage.PROPOSE);
                            bid.setContent(String.valueOf(bidVal));
                            send(bid);
                            System.out.printf("[%s] Bid %.2f (prev highest: %.2f)%n", finalCname, bidVal, calledPrice);
                        } else {
                            System.out.printf("[%s] Not bidding (calculated: %.2f, budget: %.2f, current: %.2f)%n",
                                    finalCname, bidVal, budget, calledPrice);

                            // not bidding -> deregister
                            ACLMessage dereg = new ACLMessage(ACLMessage.CANCEL);
                            dereg.setOntology("DEREGISTER");
                            dereg.addReceiver(new AID("auction-manager", AID.ISLOCALNAME));
                            dereg.setContent(auctionId);
                            send(dereg);
                            System.out.printf("[%s] Exiting auction %s — no longer bidding.%n",
                                    finalCname, auctionId);
                            doDelete();
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }

    private boolean isBlindAuction(String metadata) {
        return metadata != null && metadata.contains("Type=BLIND");
    }
}
