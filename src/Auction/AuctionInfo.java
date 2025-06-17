package Auction;

import jade.core.AID;

import java.util.HashSet;
import java.util.Set;

// === AuctionInfo for managing each auction ===
class AuctionInfo {
    String id;
    AID seller;
    String metadata;
    double currentPrice;
    AID highestBidder;
    boolean active;
    AID finalWinner;
    Set<AID> bidders = new HashSet<>();
    int totalRounds = 0;
    int roundsWithoutBid = 0;  // track consecutive empty rounds
    AuctionType type;


    AuctionInfo(String id, AID seller, String metadata, double startPrice, AuctionType type) {
        this.id = id;
        this.seller = seller;
        this.metadata = metadata;
        this.currentPrice = startPrice;
        this.type = type;
        this.active = true;
    }
}