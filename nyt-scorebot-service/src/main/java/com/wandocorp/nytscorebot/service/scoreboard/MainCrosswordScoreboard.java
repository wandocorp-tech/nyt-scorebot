package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(7)
public class MainCrosswordScoreboard implements GameComparisonScoreboard {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy");

    @Override
    public String gameType() {
        return BotText.GAME_LABEL_MAIN;
    }

    @Override
    public boolean hasResult(Scoreboard scoreboard) {
        return scoreboard != null
                && scoreboard.getMainCrosswordResult() != null
                && scoreboard.getMainCrosswordResult().getRawContent() != null;
    }

    @Override
    public String header(Scoreboard scoreboard) {
        MainCrosswordResult r = scoreboard.getMainCrosswordResult();
        String dateStr = r.getDate() != null ? r.getDate().format(DATE_FMT) : "?";
        return String.format(BotText.SCOREBOARD_MAIN_HEADER, dateStr);
    }

    @Override
    public String scoreLabel(Scoreboard scoreboard) {
        return scoreboard.getMainCrosswordResult().getTimeString();
    }

    @Override
    public List<String> emojiGridRows(Scoreboard scoreboard) {
        MainCrosswordResult r = scoreboard.getMainCrosswordResult();
        String flags = buildFlagsString(r);
        return flags.isEmpty() ? List.of() : List.of(flags);
    }

    @Override
    public ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2) {
        int t1 = s1.getMainCrosswordResult().getTotalSeconds();
        int t2 = s2.getMainCrosswordResult().getTotalSeconds();
        if (t1 == t2) return new ComparisonOutcome.Tie();
        if (t1 < t2) return new ComparisonOutcome.Win(name1, t2 - t1);
        return new ComparisonOutcome.Win(name2, t1 - t2);
    }

    @Override public int leadingSpaces() { return 1; }
    @Override public int baseGap() { return 3; }
    @Override public int maxEmojisPerRow() { return 6; }

    static String buildFlagsString(MainCrosswordResult r) {
        List<String> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(r.getDuo())) {
            parts.add(BotText.FLAG_DUO);
        }
        if (r.getLookups() != null && r.getLookups() > 0) {
            parts.add(String.format(BotText.FLAG_LOOKUPS, r.getLookups()));
        }
        if (Boolean.TRUE.equals(r.getCheckUsed())) {
            parts.add(BotText.FLAG_CHECK);
        }
        return String.join(" ", parts);
    }
}
