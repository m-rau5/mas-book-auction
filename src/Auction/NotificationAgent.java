package Auction;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class NotificationAgent extends Agent {
    // Map of buyer name → subscribed keywords (genres + authors)
    private final Map<String, Set<String>> subs = new HashMap<>();

    protected void setup() {
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {

                    // handle buyer subscriptions
                    if (msg.getPerformative() == ACLMessage.SUBSCRIBE) {
                        String senderName = msg.getSender().getLocalName();

                        // ignore temporary bidder agents
                        if (senderName.contains("-bidder-")) return;

                        String[] interests = msg.getContent().split(",");
                        subs.put(senderName, new HashSet<>(Arrays.asList(interests)));

                        System.out.printf("[NotificationAgent] SUBSCRIPTION: %s subscribed to: %s%n", senderName, subs.get(senderName));
                    }

                    // handle new auctions
                    else if (msg.getPerformative() == ACLMessage.INFORM &&
                            "AUCTION_ANNOUNCE".equals(msg.getOntology())) {

                        // get genre,author and anounce new auction
                        String genre = "", author = "";
                        for (String part : msg.getContent().split(";")) {
                            if (part.startsWith("Genre=")) genre = part.substring(6);
                            if (part.startsWith("Author=")) author = part.substring(7);
                        }

                        System.out.printf("[NotificationAgent] ALERT: New auction — Genre: %s | Author: %s — notifying buyers%n", genre, author);

                        // alert ONLY the interested buyers by looking if at least one of the genre or the author match
                        for (String buyer : subs.keySet()) {
                            Set<String> interests = subs.get(buyer);

                            boolean genreMatch = interests.contains(genre);
                            boolean authorMatch = interests.contains(author);

                            if (genreMatch || authorMatch) {
                                ACLMessage inf = new ACLMessage(ACLMessage.INFORM);
                                inf.addReceiver(new AID(buyer, AID.ISLOCALNAME));
                                inf.setOntology("AUCTION_ANNOUNCE");
                                inf.setContent(msg.getContent());
                                send(inf);

                                String reason = genreMatch ? "genre" : "author";
                                System.out.printf("NOTIFICATION: Notified %s (match: %s)%n", buyer, reason);
                            }
                        }
                    }

                } else {
                    block();
                }
            }
        });
    }
}
