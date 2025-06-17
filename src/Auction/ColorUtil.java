package Auction;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// this is used to color
public class ColorUtil {
    private static final String[] COLORS = {
            "\u001B[31m", // Red
            "\u001B[32m", // Green
            "\u001B[33m", // Yellow
            "\u001B[34m", // Blue
            "\u001B[35m", // Magenta
            "\u001B[36m", // Cyan
            "\u001B[91m", // Bright Red
            "\u001B[92m", // Bright Green
            "\u001B[94m", // Bright Blue
            "\u001B[95m", // Bright Magenta
    };

    private static final Map<String, String> colorMap = new HashMap<>();
    private static final Random rand = new Random();

    public static String colorize(String name) {
        return getColor(name) + name + "\u001B[0m";
    }

    private static String getColor(String name) {
        return colorMap.computeIfAbsent(name, _ -> COLORS[rand.nextInt(COLORS.length)]);
    }
}