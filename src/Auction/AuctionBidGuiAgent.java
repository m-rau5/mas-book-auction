package Auction;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import java.awt.*;

public class AuctionBidGuiAgent extends Agent {
    private JFrame frame;
    private JLabel itemLabel, priceLabel, budgetLabel, statusLabel;
    private JTextField bidField;
    private JButton bidButton;

    private String currentAuctionId = null;
    private double currentPrice = 0.0;
    private double budget = 5000.0;    // user money - hardcoded for now
    private boolean auctionActive = false;
    private boolean isDutch = false;

    @Override
    protected void setup() {
        SwingUtilities.invokeLater(this::buildGui);

        // listen for new‐auction announcements
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchOntology("AUCTION_ANNOUNCE"));
                if (msg != null) {
                    String[] parts = msg.getContent().split(";", 2);
                    currentAuctionId = parts[0];
                    String metadata = parts[1];
                    // parse title, author, genre, startingPrice
                    String title = extract("Title=", metadata);
                    String author = extract("Author=", metadata);
                    double start = Double.parseDouble(extract("StartingPrice=", metadata));
                    isDutch = metadata.contains("Type=DUTCH");

                    currentPrice = start;
                    auctionActive = true;

                    // create the subscription message to the notif_agent
                    ACLMessage reg = new ACLMessage(ACLMessage.SUBSCRIBE);
                    reg.addReceiver(new AID("auction-manager", AID.ISLOCALNAME));
                    reg.setOntology("REGISTER");
                    reg.setContent(currentAuctionId);
                    send(reg);

                    // setup for initial user bidding gui -> changes based on auction type
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Auction Active!");
                        itemLabel.setText(String.format("<html><b>%s</b> by %s</html>", title, author));
                        priceLabel.setText("Current bid: " + currentPrice);
                        bidButton.setEnabled(true);
                        bidButton.setText(isDutch ? "Accept Price" : "Place Bid");
                        bidField.setEnabled(!isDutch);
                        bidField.setVisible(!isDutch);
                    });
                } else {
                    block();
                }
            }
        });

        // if call for proposal from a bidder -> update the bid display
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                ACLMessage cfp = receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
                if (cfp != null && auctionActive) {
                    currentPrice = Double.parseDouble(cfp.getContent());
                    SwingUtilities.invokeLater(() -> priceLabel.setText("Current bid: " + currentPrice));
                } else {
                    block();
                }
            }
        });

        // handle resolution of auctions
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            public void action() {
                ACLMessage res = receive(MessageTemplate.MatchOntology("AUCTION_RESULT"));
                if (res != null && auctionActive) {
                    String content = res.getContent();
                    // detect user winning -> regex detection for winning text... for now
                    if (content.startsWith("You won") || content.contains("Winner: user ")) {
                        double paid;

                        // extract price from the final winning text & update the GUI
                        // 2 cases, to handle winning text inconsistency, will fix with a consistent winning message
                        if (content.startsWith("You won") ){
                            paid = Double.parseDouble(content.replaceAll(".* at price (\\d+\\.?\\d*).*", "$1"));
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(frame, content)
                            );
                        }
                        else{
                            paid = Double.parseDouble(
                                    content.replaceAll(".*Final Price: (\\d+\\.?\\d*).*", "$1")
                            );
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(frame, "You won this auction!")
                            );
                        }
                        budget -= paid;
                        SwingUtilities.invokeLater(() -> budgetLabel.setText("Budget: " + budget));
                    }
                    else{
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(frame, "Another buyer won this auction.")
                        );
                    }

                    // reset when auction ends, clear fields
                    auctionActive = false;
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("No active auction");
                        itemLabel.setText("");
                        priceLabel.setText("Current bid: –");
                        bidField.setText("");
                        bidField.setEnabled(false);
                        bidButton.setEnabled(false);
                    });
                } else {
                    block();
                }
            }
        });
    }

    // GUI building
    private void buildGui() {
        frame = new JFrame("Book Auction – Bidder");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(350, 200);
        frame.setLayout(new GridLayout(5, 1));

        statusLabel = new JLabel("No active auction", SwingConstants.CENTER);
        itemLabel   = new JLabel("", SwingConstants.CENTER);
        priceLabel  = new JLabel("Current bid: –", SwingConstants.CENTER);
        budgetLabel = new JLabel("Budget: " + budget, SwingConstants.CENTER);

        JPanel bidPanel = new JPanel(new FlowLayout());
        bidField  = new JTextField(8);
        bidButton = new JButton("Place Bid");
        bidField.setEnabled(false);
        bidButton.setEnabled(false);
        bidPanel.add(new JLabel("Your bid:"));
        bidPanel.add(bidField);
        bidPanel.add(bidButton);

        // listen for any updates sent to GUI
        bidButton.addActionListener(_ -> {
            if (!auctionActive) {
                JOptionPane.showMessageDialog(frame, "No auction is currently running.");
                return;
            }
            try {
                // extract bid + adapt to DUTCH bid differences
                double yourBid = isDutch ? currentPrice : Double.parseDouble(bidField.getText().trim());
                if (yourBid < currentPrice) {
                    JOptionPane.showMessageDialog(frame, "Your bid must exceed " + currentPrice);
                } else if (yourBid > budget) {
                    JOptionPane.showMessageDialog(frame, "You only have " + budget + " left.");
                } else {
                    // ← capture highest price
                    double prevHigh = currentPrice;

                    // craft + send message
                    ACLMessage bidMsg = new ACLMessage(ACLMessage.PROPOSE);
                    bidMsg.addReceiver(new AID("auction-manager", AID.ISLOCALNAME));
                    bidMsg.setOntology("USER_BID");
                    if (isDutch){
                        bidMsg.setOntology("BID_ACCEPTED");
                    }
                    bidMsg.setConversationId(currentAuctionId);
                    bidMsg.setContent(String.valueOf(yourBid));
                    send(bidMsg);

                    currentPrice = yourBid;
                    SwingUtilities.invokeLater(()-> priceLabel.setText("Current bid: "+currentPrice));
                    String cname = ColorUtil.colorize(getLocalName());
                    System.out.printf("[%s] Bid %.2f (prev highest: %.2f)%n",
                            cname, yourBid, prevHigh);

                    JOptionPane.showMessageDialog(frame, "Bid of " + yourBid + " sent!");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Enter a valid number.");
            }
        });

        frame.add(statusLabel);
        frame.add(itemLabel);
        frame.add(priceLabel);
        frame.add(budgetLabel);
        frame.add(bidPanel);

        frame.setVisible(true);
    }

    // utill: pull a key from metadata (semicolon‐separated)
    private String extract(String key, String metadata) {
        for (String part : metadata.split(";")) {
            if (part.startsWith(key)) {
                return part.substring(key.length());
            }
        }
        return "";
    }
}
