package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;

import java.util.List;
import java.util.Map;

/**
 * Builds the crossword win streak summary message — a code-block table with
 * one row per crossword game (Mini, Midi, Main) and one column per player.
 * Streak values ≥ {@link BotText#WIN_STREAK_FIRE_THRESHOLD} are decorated
 * with the fire emoji.
 */
public final class WinStreakSummaryBuilder {

    private static final List<GameType> ROWS = List.of(
            GameType.MINI_CROSSWORD,
            GameType.MIDI_CROSSWORD,
            GameType.MAIN_CROSSWORD
    );

    private WinStreakSummaryBuilder() {}

    /**
     * @param player1   first player
     * @param name1     display name for player 1
     * @param player2   second player (may be the same User in single-player tests, but typically distinct)
     * @param name2     display name for player 2
     * @param streaksByUser map from User → (GameType → currentStreak); missing entries treated as 0
     */
    public static String build(User player1, String name1,
                               User player2, String name2,
                               Map<User, Map<GameType, Integer>> streaksByUser) {
        Map<GameType, Integer> s1 = streaksByUser.getOrDefault(player1, Map.of());
        Map<GameType, Integer> s2 = streaksByUser.getOrDefault(player2, Map.of());

        int gameCol = Math.max(BotText.WIN_STREAK_GAME_COL_LABEL.length(),
                ROWS.stream().mapToInt(g -> g.label().length()).max().orElse(4));

        // Pre-render each cell so we can size the player columns correctly.
        String[] r1 = new String[ROWS.size()];
        String[] r2 = new String[ROWS.size()];
        for (int i = 0; i < ROWS.size(); i++) {
            r1[i] = renderValue(s1.getOrDefault(ROWS.get(i), 0));
            r2[i] = renderValue(s2.getOrDefault(ROWS.get(i), 0));
        }

        int col1 = Math.max(name1.length(), maxRendered(r1));
        int col2 = Math.max(name2.length(), maxRendered(r2));

        StringBuilder sb = new StringBuilder();
        sb.append(BotText.WIN_STREAK_HEADER).append("\n\n");
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);
        sb.append(rpad(BotText.WIN_STREAK_GAME_COL_LABEL, gameCol)).append(" |");
        sb.append(" ").append(rpad(name1, col1)).append(" |");
        sb.append(" ").append(rpad(name2, col2)).append("\n");
        sb.append("-".repeat(gameCol)).append(BotText.STATUS_COL_SEPARATOR)
                .append("-".repeat(col1)).append(BotText.STATUS_COL_SEPARATOR)
                .append("-".repeat(col2)).append("\n");
        for (int i = 0; i < ROWS.size(); i++) {
            sb.append(rpad(ROWS.get(i).label(), gameCol)).append(" |");
            sb.append(" ").append(rpad(r1[i], col1)).append(" |");
            sb.append(" ").append(rpad(r2[i], col2)).append("\n");
        }
        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    static String renderValue(int streak) {
        if (streak >= BotText.WIN_STREAK_FIRE_THRESHOLD) {
            return streak + " " + BotText.WIN_STREAK_FIRE_EMOJI;
        }
        return Integer.toString(streak);
    }

    private static int maxRendered(String[] values) {
        int max = 0;
        for (String v : values) {
            int r = renderedLength(v);
            if (r > max) max = r;
        }
        return max;
    }

    /** Counts the fire emoji as 2-wide to match Discord monospace rendering. */
    static int renderedLength(String s) {
        int extra = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            // Most emoji (incl. 🔥 U+1F525) are 2-wide in Discord monospace.
            if (cp >= 0x1F300 && cp <= 0x1FAFF) {
                extra++;
            } else if (cp == 0x2705 || cp == 0x23F3 || cp == 0x274C) {
                extra++;
            }
            i += Character.charCount(cp);
        }
        return s.codePointCount(0, s.length()) + extra;
    }

    private static String rpad(String s, int target) {
        int spaces = target - renderedLength(s);
        return spaces > 0 ? s + " ".repeat(spaces) : s;
    }
}
