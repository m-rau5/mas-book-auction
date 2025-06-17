package Auction;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Map;

public class ReputationManagerAgent extends Agent {

    // stores reputation data for each buyer
    private final Map<String, ReputationRecord> reputations = new HashMap<>();

    protected void setup() {
        System.out.println(getLocalName() + " (ReputationManager) ready.");

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {

                    // handle updates
                    if (msg.getPerformative() == ACLMessage.INFORM &&
                            "REPUTATION_UPDATE".equals(msg.getOntology())) {

                        String[] parts = msg.getContent().split(";");
                        if (parts.length == 2) {
                            String buyer = parts[0];
                            String event = parts[1];

                            reputations.putIfAbsent(buyer, new ReputationRecord());
                            ReputationRecord rec = reputations.get(buyer);

                            switch (event) {
                                case "joined" -> rec.joined++;
                                case "won" -> rec.won++;
                                case "earlyExit" -> rec.earlyExits++;
                            }

                            System.out.printf("[ReputationManager] UPDATE: Updated %s — %s ➜ score: %d (joined: %d, won: %d, exits: %d)%n",
                                    buyer, event, rec.score(), rec.joined, rec.won, rec.earlyExits);
                        }
                    }

                    // handle score query
                    else if (msg.getPerformative() == ACLMessage.REQUEST &&
                            msg.getOntology().equals("REPUTATION_QUERY")) {

                        String buyer = msg.getContent();
                        reputations.putIfAbsent(buyer, new ReputationRecord());
                        int score = reputations.get(buyer).score();

                        ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                        response.setOntology("REPUTATION_RESPONSE");
                        response.setContent(String.valueOf(score));
                        response.addReceiver(new AID(buyer, AID.ISLOCALNAME));  // Explicitly target main buyer agent
                        send(response);
                    }

                } else {
                    block();
                }
            }
        });
    }

    // helper class - track stats per buyer
    private static class ReputationRecord {
        int joined = 0;
        int won = 0;
        int earlyExits = 0;

        // this is for rep scores
        int score() {
            double raw = (joined * 0.5) + (won * 2) - (earlyExits * 1.5);
            return (int) Math.round(Math.max(0, Math.min(5, raw)));
        }
    }
}
