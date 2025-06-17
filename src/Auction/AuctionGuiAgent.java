package Auction;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;

import javax.swing.*;
import java.awt.*;

// this is the "Add a book" GUI
public class AuctionGuiAgent extends Agent {

    protected void setup() {
        SwingUtilities.invokeLater(this::createAndShowGUI);
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame("Book Auction Publisher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setLayout(new GridLayout(7, 2));

        JTextField titleField = new JTextField();
        JTextField authorField = new JTextField();
        JTextField genreField = new JTextField();
        JComboBox<String> typeDropdown = new JComboBox<>(new String[]{"ENGLISH", "DUTCH", "BLIND"});
        JTextField priceField = new JTextField("1000.0");
        JTextField minRatingField = new JTextField("0");

        JButton submitButton = new JButton("Create Auction");

        frame.add(new JLabel("Title:"));
        frame.add(titleField);
        frame.add(new JLabel("Author:"));
        frame.add(authorField);
        frame.add(new JLabel("Genre:"));
        frame.add(genreField);
        frame.add(new JLabel("Auction Type:"));
        frame.add(typeDropdown);
        frame.add(new JLabel("Starting Price:"));
        frame.add(priceField);
        frame.add(new JLabel("Min Rating (0–5):"));
        frame.add(minRatingField);
        frame.add(new JLabel(""));
        frame.add(submitButton);

        // submit button logic
        submitButton.addActionListener(_ -> {
            String title = titleField.getText().trim();
            String author = authorField.getText().trim();
            String genre = genreField.getText().trim();
            String type = (String) typeDropdown.getSelectedItem();
            String price = priceField.getText().trim();
            String minRating = minRatingField.getText().trim();

            // field validation
            if (title.isEmpty() || author.isEmpty() || genre.isEmpty() || price.isEmpty() || minRating.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please fill in all fields.");
                return;
            }

            try {
                double startPrice = Double.parseDouble(price);
                int rating = Integer.parseInt(minRating);
                if (rating < 0 || rating > 5) {
                    JOptionPane.showMessageDialog(frame, "Minimum rating must be between 0 and 5.");
                    return;
                }

                // craft auction send for the manager
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID("auction-manager", AID.ISLOCALNAME));
                msg.setContent(String.format(
                        "NEW_AUCTION;Title=%s;Author=%s;Genre=%s;Type=%s;StartingPrice=%.2f;MinRating=%d",
                        title, author, genre, type, startPrice, rating
                ));
                send(msg);

                JOptionPane.showMessageDialog(frame, "Auction created!");
                titleField.setText("");
                authorField.setText("");
                genreField.setText("");
                priceField.setText("1000.0");
                minRatingField.setText("0");

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid number format for price or rating.");
            }
        });

        frame.setVisible(true);
    }
}
