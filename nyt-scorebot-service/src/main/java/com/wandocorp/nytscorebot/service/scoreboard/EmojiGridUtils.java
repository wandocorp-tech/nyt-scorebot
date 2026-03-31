package com.wandocorp.nytscorebot.service.scoreboard;

import java.util.Set;

/**
 * Shared utility for validating emoji grid rows across game scoreboards.
 */
public final class EmojiGridUtils {

    private EmojiGridUtils() {}

    /**
     * Returns {@code true} if every codepoint in {@code line} is in {@code allowedCodepoints}
     * and the total codepoint count equals {@code expectedCount}.
     * Pass {@code -1} for {@code expectedCount} to accept any positive count (variable-length rows).
     */
    public static boolean isEmojiRow(String line, Set<Integer> allowedCodepoints, int expectedCount) {
        if (line == null || line.isBlank()) return false;
        int count = 0;
        int i = 0;
        while (i < line.length()) {
            int cp = line.codePointAt(i);
            if (!allowedCodepoints.contains(cp)) return false;
            count++;
            i += Character.charCount(cp);
        }
        return expectedCount == -1 ? count > 0 : count == expectedCount;
    }
}
