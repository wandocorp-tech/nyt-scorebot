package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.GameResult;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Constructs the status table message for the Discord status channel.
 * Handles table layout and emoji-aware padding.
 */
@RequiredArgsConstructor
public class StatusMessageBuilder {

    private record GameRow(String label, Function<Scoreboard, GameResult> getter) {}

    private static final List<GameRow> GAME_ROWS = List.of(
            new GameRow(BotText.GAME_LABEL_WORDLE,      Scoreboard::getWordleResult),
            new GameRow(BotText.GAME_LABEL_CONNECTIONS, Scoreboard::getConnectionsResult),
            new GameRow(BotText.GAME_LABEL_STRANDS,     Scoreboard::getStrandsResult),
            new GameRow(BotText.GAME_LABEL_MINI,        Scoreboard::getMiniCrosswordResult),
            new GameRow(BotText.GAME_LABEL_MIDI,        Scoreboard::getMidiCrosswordResult),
            new GameRow(BotText.GAME_LABEL_MAIN,        Scoreboard::getMainCrosswordResult)
    );

    private static final int GAME_COL_WIDTH = GAME_ROWS.stream()
            .mapToInt(r -> r.label().length())
            .max().orElse(4);

    private final List<Scoreboard> scoreboards;
    private final List<String> playerNames;
    private final String contextMessage;

    /**
     * Builds the formatted status table message.
     */
    public String build() {
        Map<String, Scoreboard> byName = scoreboards.stream()
                .collect(Collectors.toMap(s -> s.getUser().getName(), s -> s));

        int playerColWidth = playerNames.stream()
                .mapToInt(String::length)
                .max().orElse(8);

        StringBuilder sb = new StringBuilder();
        sb.append(contextMessage).append("\n\n\n");
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);
        sb.append(buildHeaderRow(playerColWidth));
        sb.append(buildSeparatorRow(playerColWidth));
        for (GameRow row : GAME_ROWS) {
            sb.append(buildGameRow(row, byName, playerColWidth));
        }
        sb.append(buildSeparatorRow(playerColWidth));
        sb.append(buildFooterRow(byName, playerColWidth));
        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    /** Builds the column header row: "Connections   | Conor | Will" */
    private String buildHeaderRow(int playerColWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append(rpad(BotText.STATUS_GAME_COL_HEADER, GAME_COL_WIDTH)).append(" |");
        for (int i = 0; i < playerNames.size(); i++) {
            sb.append(" ").append(rpad(playerNames.get(i), playerColWidth));
            if (i < playerNames.size() - 1) sb.append(" |");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Builds a separator row: "-------------+-------+-----" */
    private String buildSeparatorRow(int playerColWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append("-".repeat(GAME_COL_WIDTH)).append(BotText.STATUS_COL_SEPARATOR)
          .append("-".repeat(playerColWidth));
        for (int i = 1; i < playerNames.size(); i++) {
            sb.append(BotText.STATUS_COL_SEPARATOR).append("-".repeat(playerColWidth));
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Builds a single game row with checkmark/hourglass indicators for each player. */
    private String buildGameRow(GameRow row, Map<String, Scoreboard> byName, int playerColWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append(rpad(row.label(), GAME_COL_WIDTH)).append(" |");
        for (int i = 0; i < playerNames.size(); i++) {
            String name = playerNames.get(i);
            Scoreboard s = byName.get(name);
            String indicator = (s != null && isSubmitted(row.getter().apply(s)))
                    ? BotText.SUBMITTED : BotText.PENDING;
            sb.append(" ").append(rpad(indicator, playerColWidth));
            if (i < playerNames.size() - 1) sb.append(" |");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Builds the footer row showing which players have marked themselves finished. */
    private String buildFooterRow(Map<String, Scoreboard> byName, int playerColWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append(rpad(BotText.STATUS_FOOTER_DONE_LABEL, GAME_COL_WIDTH)).append(" |");
        for (int i = 0; i < playerNames.size(); i++) {
            String name = playerNames.get(i);
            Scoreboard s = byName.get(name);
            String indicator = (s != null && s.isFinished()) ? BotText.CHECK_MARK : BotText.CROSS_MARK;
            sb.append(" ").append(rpad(indicator, playerColWidth));
            if (i < playerNames.size() - 1) sb.append(" |");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static boolean isSubmitted(GameResult result) {
        return result != null && result.getRawContent() != null;
    }

    /**
     * Returns the rendered column width of {@code s}, counting ✅ and ⏳ as 2-wide.
     */
    static int renderedLength(String s) {
        int extra = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u2705' || c == '\u23F3' || c == '\u274C') extra++; // ✅ ⏳ ❌ render 2-wide in Discord monospace
        }
        return s.length() + extra;
    }

    /**
     * Right-pads {@code s} to {@code target} rendered columns.
     */
    private static String rpad(String s, int target) {
        int spaces = target - renderedLength(s);
        return spaces > 0 ? s + " ".repeat(spaces) : s;
    }
}
