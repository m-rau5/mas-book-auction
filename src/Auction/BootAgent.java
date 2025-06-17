package Auction;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import java.util.Random;

enum AuctionType { ENGLISH, DUTCH, BLIND}

// the "main" function of the code, we define setup, bidders and start
public class BootAgent {
    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        Random random = new Random();
        ContainerController container = runtime.createMainContainer(profile);
        try {
            container.createNewAgent("auction-manager", AuctionManagerAgent.class.getName(), null).start();
            container.createNewAgent("notification", NotificationAgent.class.getName(), null).start();
            container.createNewAgent("auction-gui", AuctionGuiAgent.class.getName(), null).start();
            container.createNewAgent("reputation-manager", ReputationManagerAgent.class.getName(), null).start();
            container.createNewAgent("user-agent", AuctionBidGuiAgent.class.getName(), null).start();

            // some computer bidders
            container.createNewAgent(
                    "buyer1",
                    BuyerAgent.class.getName(),
                    new Object[]{"buyer1", new String[]{"Fantasy","Sci-Fi","Comedy"},new String[]{"Tolkien", "Asimov"},
                            BiddingStrategies.CAUTIOUS, 1000.0 + random.nextInt(2001) }
            ).start();
            container.createNewAgent(
                    "buyer2",
                    BuyerAgent.class.getName(),
                    new Object[]{"buyer2", new String[]{"Fantasy","Adventure","Comedy"},new String[]{"Tolkien", "Mark Twain"},
                            BiddingStrategies.ALWAYSFIRST, 1000.0 + random.nextInt(2001) }
            ).start();
            container.createNewAgent(
                    "buyer3",
                    BuyerAgent.class.getName(),
                    new Object[]{"buyer3", new String[]{"Romance","Fantasy"},new String[]{"Stephen King", "Tolkien","Jane Austen"},
                            BiddingStrategies.PERIODIC, 1000.0 + random.nextInt(2001) }
            ).start();
            container.createNewAgent(
                    "buyer4",
                    BuyerAgent.class.getName(),
                    new Object[]{"buyer4", new String[]{"Romance","Sci-Fi"},new String[]{"George Orwell","Jane Austen"},
                            BiddingStrategies.ONESHOT, 1000.0 + random.nextInt(2001) }
            ).start();

            // wait for bidders to be ready before creating the first bid
            Thread.sleep(2000);
            container.createNewAgent("seller1", SellerAgent.class.getName(), new Object[]{"seller1"}).start();

        } catch (StaleProxyException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}