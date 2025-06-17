package Auction;

import java.util.Random;


// Some strategies i got from researching bidding strategies
public enum BiddingStrategies {
    ONESHOT, PERIODIC, ALWAYSFIRST, CAUTIOUS
}

interface BiddingStrategy {
    double calculateBid(double currentPrice, double budget);
}

// ONESHOT: fixed-value early bid, 50–70% of the budget or 10–30% above current
class OneShotStrategy implements BiddingStrategy {
    private boolean hasBid = false;

    @Override
    public double calculateBid(double currentPrice, double budget) {
        if (hasBid) return -1;  // to exit if bid is invalid
        hasBid = true;

        double maxWilling = Math.min(budget, currentPrice * 1.3);
        return Math.max(currentPrice + 1, Math.min(budget, maxWilling));
    }
}

// PERIODIC: adaptive incremental bidding (5–20% above current) with budget safety
class PeriodicStrategy implements BiddingStrategy {
    private final Random rnd = new Random();

    @Override
    public double calculateBid(double currentPrice, double budget) {
        double incrementPercent = 0.05 + rnd.nextDouble() * 0.15; // 5% to 20%
        double increment = currentPrice * incrementPercent;
        double bid = currentPrice + increment;
        bid = Math.min(bid, budget);
        return bid;
    }
}

// ALWAYSFIRST: aggressive — immediately jumps near budget unless currentPrice is already near
class AlwaysFirstStrategy implements BiddingStrategy {
    @Override
    public double calculateBid(double currentPrice, double budget) {
        double jumpAbove = Math.max(1.0, (budget - currentPrice) * 0.5); // halfway up to budget
        double bid = currentPrice + jumpAbove;
        bid = Math.min(bid, budget);
        return bid;
    }
}

// CAUTIOUS: Basically a periodic strategy with a 70-80% budget cutoff to avoid overspending
class CautiousStrategy implements BiddingStrategy {
    private final Random rnd = new Random();
    private final double cutoffRatio;
    private boolean explainedCutoff = false; // so no repeated prints

    public CautiousStrategy() {
        this.cutoffRatio = 0.7 + rnd.nextDouble() * 0.1; // somwhere between 70% and 80%
    }

    @Override
    public double calculateBid(double currentPrice, double budget) {
        double cutoff = budget * cutoffRatio;

        if (currentPrice >= cutoff) {
            if (!explainedCutoff) {
                System.out.printf("CautiousStrategy -> cutoff reached (%.2f >= %.2f)\n", currentPrice, cutoff);
                explainedCutoff = true;
            }
            return -1; // if cutoff, too expensive => quit
        }

        double increment = currentPrice * (0.05 + rnd.nextDouble() * 0.1); // +5–15%
        double bid = currentPrice + increment;
        return Math.min(bid, cutoff);
    }
}

