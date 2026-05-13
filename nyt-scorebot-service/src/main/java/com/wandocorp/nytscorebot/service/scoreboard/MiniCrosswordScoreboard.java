package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.service.history.CrosswordGame;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryService;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryStats;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
@Order(5)
@RequiredArgsConstructor
public class MiniCrosswordScoreboard implements GameComparisonScoreboard {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy");

    private final CrosswordHistoryService historyService;

    @Override
    public String gameType() {
        return BotText.GAME_LABEL_MINI;
    }

    @Override
    public boolean hasResult(Scoreboard scoreboard) {
        return scoreboard != null
                && scoreboard.getMiniCrosswordResult() != null
                && scoreboard.getMiniCrosswordResult().getRawContent() != null;
    }

    @Override
    public String header(Scoreboard scoreboard) {
        CrosswordResult r = scoreboard.getMiniCrosswordResult();
        String dateStr = r.getDate() != null ? r.getDate().format(DATE_FMT) : "?";
        return String.format(BotText.SCOREBOARD_MINI_HEADER, dateStr);
    }

    @Override
    public String scoreLabel(Scoreboard scoreboard) {
        return scoreboard.getMiniCrosswordResult().getTimeString();
    }

    @Override
    public List<String> emojiGridRows(Scoreboard scoreboard) {
        return List.of();
    }

    @Override
    public ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2) {
        int t1 = s1.getMiniCrosswordResult().getTotalSeconds();
        int t2 = s2.getMiniCrosswordResult().getTotalSeconds();
        if (t1 == t2) return new ComparisonOutcome.Nuke();
        if (t1 < t2) return new ComparisonOutcome.Win(name1, MainCrosswordScoreboard.formatMmSs(t2 - t1));
        return new ComparisonOutcome.Win(name2, MainCrosswordScoreboard.formatMmSs(t1 - t2));
    }

    @Override
    public List<ExtraRow> extraRowsBelowOutcome(Scoreboard left, Scoreboard right) {
        CrosswordHistoryStats l = historyService.getStats(left.getUser(), CrosswordGame.MINI, Optional.empty());
        CrosswordHistoryStats r = historyService.getStats(right.getUser(), CrosswordGame.MINI, Optional.empty());
        return CrosswordExtraRows.avgPbRows(l, r);
    }

    @Override public int leadingSpaces() { return 0; }
    @Override public int baseGap() { return 0; }
    @Override public int maxEmojisPerRow() { return 0; }
    @Override public boolean usesScoreLabelRow() { return true; }
    @Override public boolean usesCrosswordLayout() { return true; }
}
