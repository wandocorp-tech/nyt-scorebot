package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.GameType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ScoreboardRenderer {

    private static final String SEP = "-".repeat(BotText.MAX_LINE_WIDTH);
    private static final String SINGLE_SEP = "-".repeat(BotText.SINGLE_PLAYER_LINE_WIDTH);
    /** Column width for player name; names longer than this will misalign the layout. */
    private static final int PLAYER_COL_WIDTH = 15;

    /** Layout for the optional extra rows (avg/pb). The {@code +} positions match the {@code |} positions. */
    private static final String EXTRA_ROW_FMT = " %-3s |%10s | %s";
    private static final String EXTRA_DIVIDER = "-----+-----------+---------------";

    private final List<GameComparisonScoreboard> games;

    public Map<String, String> renderAll(Scoreboard sb1, String name1, Scoreboard sb2, String name2,
                                          Map<String, Map<GameType, Integer>> streaks) {
        Map<String, String> result = new LinkedHashMap<>();
        for (GameComparisonScoreboard game : games) {
            render(game, sb1, name1, sb2, name2, streaks).ifPresent(s -> result.put(game.gameType(), s));
        }
        return result;
    }

    /** Renders a single game type by name. Returns empty if the game type is unknown or neither player has a result. */
    public Optional<String> renderByGameType(String gameType, Scoreboard sb1, String name1,
                                              Scoreboard sb2, String name2,
                                              Map<String, Map<GameType, Integer>> streaks) {
        return games.stream()
                .filter(g -> g.gameType().equals(gameType))
                .findFirst()
                .flatMap(game -> render(game, sb1, name1, sb2, name2, streaks));
    }

    /** Layout for a two-player comparison: the player with more emoji rows is placed on the left. */
    private record TwoPlayerLayout(Scoreboard leftSb, String leftName,
                                   Scoreboard rightSb, String rightName) {}

    public Optional<String> render(GameComparisonScoreboard game, Scoreboard sb1, String name1,
                                    Scoreboard sb2, String name2,
                                    Map<String, Map<GameType, Integer>> streaks) {
        boolean has1 = game.hasResult(sb1);
        boolean has2 = game.hasResult(sb2);

        if (!has1 && !has2) return Optional.empty();

        Scoreboard headerSource = has1 ? sb1 : sb2;
        String header = game.header(headerSource);

        if (!has1 || !has2) {
            Scoreboard presentSb = has1 ? sb1 : sb2;
            String presentName = has1 ? name1 : name2;
            String missingName = has1 ? name2 : name1;
            return Optional.of(renderSinglePlayer(game, header, presentSb, presentName, missingName,
                    streaks));
        }

        TwoPlayerLayout layout = determineLayout(game, sb1, name1, sb2, name2);
        ComparisonOutcome outcome = game.determineOutcome(sb1, name1, sb2, name2);
        return Optional.of(renderTwoPlayer(game, header, layout, outcome, streaks));
    }

    /** Places the player with more emoji rows on the left for better visual alignment. */
    private TwoPlayerLayout determineLayout(GameComparisonScoreboard game,
                                             Scoreboard sb1, String name1,
                                             Scoreboard sb2, String name2) {
        if (game.emojiGridRows(sb2).size() > game.emojiGridRows(sb1).size()) {
            return new TwoPlayerLayout(sb2, name2, sb1, name1);
        }
        return new TwoPlayerLayout(sb1, name1, sb2, name2);
    }

    private String renderSinglePlayer(GameComparisonScoreboard game, String header,
                                       Scoreboard presentSb, String presentName, String missingName,
                                       Map<String, Map<GameType, Integer>> streaks) {
        String nameRow = String.format("%" + PLAYER_COL_WIDTH + "s", presentName);
        String leading = " ".repeat(game.leadingSpaces());

        StringBuilder sb = new StringBuilder();
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);
        sb.append(" ").append(header).append("\n");
        sb.append(" \n");
        sb.append(SINGLE_SEP).append("\n");
        sb.append(nameRow).append("\n");
        sb.append(SINGLE_SEP).append("\n");
        for (String row : game.emojiGridRows(presentSb)) {
            sb.append(leading).append(row).append("\n");
        }
        if (game.usesScoreLabelRow()) {
            sb.append(String.format("%" + PLAYER_COL_WIDTH + "s", game.scoreLabel(presentSb))).append("\n");
            String flags = game.flagsRow(presentSb);
            if (!flags.isEmpty()) {
                sb.append(SINGLE_SEP).append("\n");
                sb.append(String.format("%" + PLAYER_COL_WIDTH + "s", flags)).append("\n");
            }
        }
        sb.append(SINGLE_SEP).append("\n");

        if (game.usesStreakDisplay()) {
            sb.append(buildSingleStreakRow(game, presentName, streaks)).append("\n");
        } else {
            sb.append(" ").append(BotText.SCOREBOARD_WAITING_SINGLE).append("\n");
        }

        sb.append(SINGLE_SEP).append("\n");
        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    private String renderTwoPlayer(GameComparisonScoreboard game, String header,
                                    TwoPlayerLayout layout,
                                    ComparisonOutcome outcome,
                                    Map<String, Map<GameType, Integer>> streaks) {
        List<String> leftRows = game.emojiGridRows(layout.leftSb);
        List<String> rightRows = game.emojiGridRows(layout.rightSb);
        String nameRow = String.format("%" + PLAYER_COL_WIDTH + "s  |  %s",
                layout.leftName,
                layout.rightName);
        String leading = " ".repeat(game.leadingSpaces());

        StringBuilder sb = new StringBuilder();
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);
        sb.append(" ").append(header).append("\n");
        sb.append(" \n");
        sb.append(SEP).append("\n");
        sb.append(nameRow).append("\n");
        sb.append(SEP).append("\n");

        int maxRows = Math.max(leftRows.size(), rightRows.size());
        for (int i = 0; i < maxRows; i++) {
            if (i < leftRows.size()) {
                String leftRow = leftRows.get(i);
                if (i < rightRows.size()) {
                    int leftEmojiCount = leftRow.codePointCount(0, leftRow.length());
                    int extra = (game.maxEmojisPerRow() - leftEmojiCount) * 2;
                    String gap = " ".repeat(game.baseGap() + extra);
                    sb.append(leading).append(leftRow).append(gap).append(rightRows.get(i)).append("\n");
                } else {
                    sb.append(leading).append(leftRow).append("\n");
                }
            }
        }

        if (game.usesScoreLabelRow()) {
            String scoreRow = String.format("%" + PLAYER_COL_WIDTH + "s     %s",
                    game.scoreLabel(layout.leftSb), game.scoreLabel(layout.rightSb));
            sb.append(scoreRow).append("\n");
            String leftFlags = game.flagsRow(layout.leftSb);
            String rightFlags = game.flagsRow(layout.rightSb);
            if (!leftFlags.isEmpty() || !rightFlags.isEmpty()) {
                sb.append(SEP).append("\n");
                String flagsRow = String.format("%" + PLAYER_COL_WIDTH + "s     %s", leftFlags, rightFlags);
                sb.append(flagsRow).append("\n");
            }
        }

        sb.append(SEP).append("\n");

        if (game.usesStreakDisplay()) {
            sb.append(buildStreakRow(game, layout.leftName, layout.rightName, streaks)).append("\n");
        } else {
            sb.append(" ").append(buildResultMessage(outcome)).append("\n");
        }

        sb.append(SEP).append("\n");

        List<ExtraRow> extras = game.extraRowsBelowOutcome(layout.leftSb, layout.rightSb);
        for (int i = 0; i < extras.size(); i++) {
            ExtraRow row = extras.get(i);
            sb.append(String.format(EXTRA_ROW_FMT, row.label(), row.leftValue(), row.rightValue())).append("\n");
            sb.append(i < extras.size() - 1 ? EXTRA_DIVIDER : SEP).append("\n");
        }

        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    private String buildStreakRow(GameComparisonScoreboard game,
                                   String name1, String name2,
                                   Map<String, Map<GameType, Integer>> streaks) {
        GameType gameType = GameType.fromLabel(game.gameType());
        int streak1 = getStreakValue(streaks, name1, gameType);
        int streak2 = getStreakValue(streaks, name2, gameType);

        String leftStreak = String.format(BotText.SCOREBOARD_STREAK, streak1);
        String rightStreak = String.format(BotText.SCOREBOARD_STREAK, streak2);

        return String.format("%" + PLAYER_COL_WIDTH + "s  |  %s", leftStreak, rightStreak);
    }

    private String buildSingleStreakRow(GameComparisonScoreboard game,
                                         String playerName,
                                         Map<String, Map<GameType, Integer>> streaks) {
        int streak = getStreakValue(streaks, playerName, GameType.fromLabel(game.gameType()));
        String streakStr = String.format(BotText.SCOREBOARD_STREAK, streak);
        return String.format("%" + PLAYER_COL_WIDTH + "s", streakStr);
    }

    private static int getStreakValue(Map<String, Map<GameType, Integer>> streaks,
                                       String playerName, GameType gameType) {
        if (streaks == null) return 0;
        Map<GameType, Integer> playerStreaks = streaks.get(playerName);
        if (playerStreaks == null) return 0;
        return playerStreaks.getOrDefault(gameType, 0);
    }

    private String buildResultMessage(ComparisonOutcome outcome) {
        if (outcome instanceof ComparisonOutcome.Tie) {
            return BotText.SCOREBOARD_TIE;
        } else if (outcome instanceof ComparisonOutcome.Nuke) {
            return BotText.SCOREBOARD_NUKE;
        } else if (outcome instanceof ComparisonOutcome.Win w) {
            return w.differentialLabel() != null
                    ? String.format(BotText.SCOREBOARD_WIN_WITH_DIFF, w.winnerName(), w.differentialLabel())
                    : String.format(BotText.SCOREBOARD_WIN_NO_DIFF, w.winnerName());
        } else if (outcome instanceof ComparisonOutcome.WaitingFor wf) {
            return String.format(BotText.SCOREBOARD_WAITING, wf.missingPlayerName());
        }
        throw new IllegalStateException("Unknown ComparisonOutcome: " + outcome);
    }
}
