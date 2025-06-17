package Auction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class SellerAgent extends Agent {
    private String sellerName;
    protected void setup() {
        sellerName = (String) getArguments()[0];

        // create the first auction to get things going
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID("auction-manager", AID.ISLOCALNAME));
                msg.setContent("NEW_AUCTION;Title=The Hobbit;Author=Tolkien;Genre=Fantasy;Type=ENGLISH;StartingPrice=1000.0");
                send(msg);
                System.out.println(sellerName + " published The Hobbit auction.");
            }
        });
    }
}

