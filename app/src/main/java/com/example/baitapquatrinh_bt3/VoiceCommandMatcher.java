package com.example.baitapquatrinh_bt3;

public class VoiceCommandMatcher {

    // ===== FUZZY MATCHING - Approximate String Matching =====
    public static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j],
                            Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    // Calculate similarity percentage (0-100%)
    public static double similarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 100;
        int distance = levenshteinDistance(a, b);
        return (1 - (double) distance / maxLen) * 100;
    }

    // ===== MATCHING WITH FUZZY LOGIC =====
    private static boolean matchPatterns(String text, String[] patterns) {
        String cleanText = text.toLowerCase().trim();

        // Method 1: Exact matching (contains)
        for (String pattern : patterns) {
            if (cleanText.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        // Method 2: Fuzzy matching if exact match not found
        for (String pattern : patterns) {
            double sim = similarity(cleanText, pattern.toLowerCase());
            if (sim > 75) {
                return true;
            }

            // Check each word separately
            String[] words = cleanText.split(" ");
            for (String word : words) {
                if (similarity(word, pattern.toLowerCase()) > 80) {
                    return true;
                }
            }
        }

        return false;
    }

    // ===== PATTERN MATCHING FOR ENGLISH COMMANDS =====
    public static String matchCommand(String text) {
        String lower = text.toLowerCase().trim();

        // ===== LIGHT/FLASH CONTROL =====
        if (matchPatterns(lower, new String[]{"turn on", "on", "light on", "flash on", "lights on"})) {
            return "LIGHT_ON";
        }
        if (matchPatterns(lower, new String[]{"turn off", "off", "light off", "flash off", "lights off"})) {
            return "LIGHT_OFF";
        }
        if (matchPatterns(lower, new String[]{"brighter", "brightness up", "increase brightness"})) {
            return "BRIGHTNESS_UP";
        }
        if (matchPatterns(lower, new String[]{"darker", "brightness down", "decrease brightness"})) {
            return "BRIGHTNESS_DOWN";
        }

        // ===== VOLUME CONTROL =====
        if (matchPatterns(lower, new String[]{"volume up", "up", "louder", "increase volume"})) {
            return "VOLUME_UP";
        }
        if (matchPatterns(lower, new String[]{"volume down", "down", "quieter", "decrease volume"})) {
            return "VOLUME_DOWN";
        }

        // ===== MUSIC CONTROL =====
        if (matchPatterns(lower, new String[]{"next", "next song", "next track", "skip"})) {
            return "NEXT";
        }
        if (matchPatterns(lower, new String[]{"previous", "prev", "previous song", "back", "last track"})) {
            return "PREVIOUS";
        }

        // ===== AC CONTROL =====
        if (matchPatterns(lower, new String[]{"air conditioner on", "ac on", "cool on", "turn on ac"})) {
            return "AC_POWER_ON";
        }
        if (matchPatterns(lower, new String[]{"air conditioner off", "ac off", "cool off", "turn off ac"})) {
            return "AC_POWER_OFF";
        }

        // ===== TV CONTROL =====
        if (matchPatterns(lower, new String[]{"tv on", "television on", "power on", "turn on tv"})) {
            return "POWER";
        }
        if (matchPatterns(lower, new String[]{"mute", "mute tv", "sound off"})) {
            return "MUTE";
        }
        if (matchPatterns(lower, new String[]{"up", "channel up", "increase channel"})) {
            return "UP";
        }
        if (matchPatterns(lower, new String[]{"down", "channel down", "decrease channel"})) {
            return "DOWN";
        }
        if (matchPatterns(lower, new String[]{"left"})) {
            return "LEFT";
        }
        if (matchPatterns(lower, new String[]{"right"})) {
            return "RIGHT";
        }
        if (matchPatterns(lower, new String[]{"ok", "okay", "select", "enter"})) {
            return "OK";
        }
        if (matchPatterns(lower, new String[]{"source", "input", "switch source"})) {
            return "SOURCE";
        }

        return null;
    }
}
