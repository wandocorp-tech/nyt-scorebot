package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
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
    private static final int PLAYER_COL_WIDTH = 15;

    private final List<GameComparisonScoreboard> games;

    public Map<String, String> renderAll(Scoreboard sb1, String name1, Scoreboard sb2, String name2,
                                          Map<String, Map<String, Integer>> streaks) {
        Map<String, String> result = new LinkedHashMap<>();
        for (GameComparisonScoreboard game : games) {
            render(game, sb1, name1, sb2, name2, streaks).ifPresent(s -> result.put(game.gameType(), s));
        }
        return result;
    }

    /** Renders a single game type by name. Returns empty if the game type is unknown or neither player has a result. */
    public Optional<String> renderByGameType(String gameType, Scoreboard sb1, String name1,
                                              Scoreboard sb2, String name2,
                                              Map<String, Map<String, Integer>> streaks) {
        return games.stream()
                .filter(g -> g.gameType().equals(gameType))
                .findFirst()
                .flatMap(game -> render(game, sb1, name1, sb2, name2, streaks));
    }

    public Optional<String> render(GameComparisonScoreboard game, Scoreboard sb1, String name1,
                                    Scoreboard sb2, String name2,
                                    Map<String, Map<String, Integer>> streaks) {
        boolean has1 = game.hasResult(sb1);
        boolean has2 = game.hasResult(sb2);

        if (!has1 && !has2) return Optional.empty();

        Scoreboard sbRef = has1 ? sb1 : sb2;
        String header = game.header(sbRef);

        if (!has1 || !has2) {
            Scoreboard presentSb = has1 ? sb1 : sb2;
            String presentName = has1 ? name1 : name2;
            String missingName = has1 ? name2 : name1;
            return Optional.of(renderSinglePlayer(game, header, presentSb, presentName, missingName,
                    streaks, name1, name2));
        }

        List<String> rows1 = game.emojiGridRows(sb1);
        List<String> rows2 = game.emojiGridRows(sb2);

        Scoreboard leftSb, rightSb;
        String leftName, rightName;
        if (rows2.size() > rows1.size()) {
            leftSb = sb2; leftName = name2;
            rightSb = sb1; rightName = name1;
        } else {
            leftSb = sb1; leftName = name1;
            rightSb = sb2; rightName = name2;
        }

        ComparisonOutcome outcome = game.determineOutcome(sb1, name1, sb2, name2);
        return Optional.of(renderTwoPlayer(game, header, leftSb, leftName, rightSb, rightName, outcome, streaks));
    }

    private String renderSinglePlayer(GameComparisonScoreboard game, String header,
                                       Scoreboard presentSb, String presentName, String missingName,
                                       Map<String, Map<String, Integer>> streaks,
                                       String name1, String name2) {
        String nameRow = String.format("%" + PLAYER_COL_WIDTH + "s", presentName);
        String leading = " ".repeat(game.leadingSpaces());

        StringBuilder sb = new StringBuilder();
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);
        sb.append(" ").append(header).append("\n");
        sb.append(" \n");
        sb.append(SEP).append("\n");
        sb.append(nameRow).append("\n");
        sb.append(SEP).append("\n");
        for (String row : game.emojiGridRows(presentSb)) {
            sb.append(leading).append(row).append("\n");
        }
        sb.append(SEP).append("\n");

        if (game.usesStreakDisplay()) {
            sb.append(buildSingleStreakRow(game, presentName, streaks)).append("\n");
        } else {
            sb.append(" ").append(String.format(BotText.SCOREBOARD_WAITING, missingName)).append("\n");
        }

        sb.append(SEP).append("\n");
        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    private String renderTwoPlayer(GameComparisonScoreboard game, String header,
                                    Scoreboard leftSb, String leftName,
                                    Scoreboard rightSb, String rightName,
                                    ComparisonOutcome outcome,
                                    Map<String, Map<String, Integer>> streaks) {
        List<String> leftRows = game.emojiGridRows(leftSb);
        List<String> rightRows = game.emojiGridRows(rightSb);
        String nameRow = String.format("%" + PLAYER_COL_WIDTH + "s  |  %s",
                leftName,
                rightName);
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

        sb.append(SEP).append("\n");

        if (game.usesStreakDisplay()) {
            sb.append(buildStreakRow(game, leftName, rightName, streaks)).append("\n");
        } else {
            sb.append(" ").append(buildResultMessage(outcome)).append("\n");
        }

        sb.append(SEP).append("\n");
        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    private String buildStreakRow(GameComparisonScoreboard game,
                                   String name1, String name2,
                                   Map<String, Map<String, Integer>> streaks) {
        String gameType = game.gameType();
        int streak1 = getStreakValue(streaks, name1, gameType);
        int streak2 = getStreakValue(streaks, name2, gameType);

        String leftStreak = String.format(BotText.SCOREBOARD_STREAK, streak1);
        String rightStreak = String.format(BotText.SCOREBOARD_STREAK, streak2);

        return String.format("%" + PLAYER_COL_WIDTH + "s  |  %s", leftStreak, rightStreak);
    }

    private String buildSingleStreakRow(GameComparisonScoreboard game,
                                         String playerName,
                                         Map<String, Map<String, Integer>> streaks) {
        int streak = getStreakValue(streaks, playerName, game.gameType());
        String streakStr = String.format(BotText.SCOREBOARD_STREAK, streak);
        return String.format("%" + PLAYER_COL_WIDTH + "s", streakStr);
    }

    private static int getStreakValue(Map<String, Map<String, Integer>> streaks,
                                       String playerName, String gameType) {
        if (streaks == null) return 0;
        Map<String, Integer> playerStreaks = streaks.get(playerName);
        if (playerStreaks == null) return 0;
        return playerStreaks.getOrDefault(gameType, 0);
    }

    private String buildResultMessage(ComparisonOutcome outcome) {
        if (outcome instanceof ComparisonOutcome.Tie) {
            return BotText.SCOREBOARD_TIE;
        } else if (outcome instanceof ComparisonOutcome.Win w) {
            return w.differential() != null
                    ? String.format(BotText.SCOREBOARD_WIN_WITH_DIFF, w.winnerName(), w.differential())
                    : String.format(BotText.SCOREBOARD_WIN_NO_DIFF, w.winnerName());
        } else if (outcome instanceof ComparisonOutcome.WaitingFor wf) {
            return String.format(BotText.SCOREBOARD_WAITING, wf.missingPlayerName());
        }
        throw new IllegalStateException("Unknown ComparisonOutcome: " + outcome);
    }
}
