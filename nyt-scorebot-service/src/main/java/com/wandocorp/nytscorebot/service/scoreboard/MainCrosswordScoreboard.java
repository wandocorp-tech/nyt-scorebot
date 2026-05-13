package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.service.history.CrosswordGame;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryService;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryStats;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Order(7)
@RequiredArgsConstructor
public class MainCrosswordScoreboard implements GameComparisonScoreboard {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy");

    private final CrosswordHistoryService historyService;

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
        if (r.getDate() == null) {
            return String.format(BotText.SCOREBOARD_MAIN_HEADER, "?");
        }
        String dayName = r.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return String.format(BotText.SCOREBOARD_MAIN_HEADER, dayName + " - " + r.getDate().format(DATE_FMT));
    }

    @Override
    public String scoreLabel(Scoreboard scoreboard) {
        return scoreboard.getMainCrosswordResult().getTimeString();
    }

    @Override
    public List<String> emojiGridRows(Scoreboard scoreboard) {
        return List.of();
    }

    @Override
    public String flagsRow(Scoreboard scoreboard) {
        MainCrosswordResult main = scoreboard.getMainCrosswordResult();
        return buildFlagsString(main);
    }

    @Override
    public ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2) {
        MainCrosswordResult r1 = s1.getMainCrosswordResult();
        MainCrosswordResult r2 = s2.getMainCrosswordResult();
        boolean aided1 = usedAssistance(r1);
        boolean aided2 = usedAssistance(r2);

        if (aided1 && aided2) return new ComparisonOutcome.Tie();
        if (aided1) return new ComparisonOutcome.Win(displayName(name2, r2), null);
        if (aided2) return new ComparisonOutcome.Win(displayName(name1, r1), null);

        int t1 = r1.getTotalSeconds();
        int t2 = r2.getTotalSeconds();
        if (t1 == t2) return new ComparisonOutcome.Nuke();
        if (t1 < t2) return new ComparisonOutcome.Win(displayName(name1, r1), formatMmSs(t2 - t1));
        return new ComparisonOutcome.Win(displayName(name2, r2), formatMmSs(t1 - t2));
    }

    @Override
    public List<ExtraRow> extraRowsBelowOutcome(Scoreboard left, Scoreboard right) {
        MainCrosswordResult source = left.getMainCrosswordResult() != null
                ? left.getMainCrosswordResult()
                : right.getMainCrosswordResult();
        if (source == null || source.getDate() == null) return List.of();
        Optional<java.time.DayOfWeek> dow = Optional.of(source.getDate().getDayOfWeek());
        CrosswordHistoryStats l = historyService.getStats(left.getUser(), CrosswordGame.MAIN, dow);
        CrosswordHistoryStats r = historyService.getStats(right.getUser(), CrosswordGame.MAIN, dow);
        return CrosswordExtraRows.avgPbRows(l, r);
    }

    private static boolean usedAssistance(MainCrosswordResult r) {
        return Boolean.TRUE.equals(r.getCheckUsed())
                || (r.getLookups() != null && r.getLookups() > 0);
    }

    private static String displayName(String name, MainCrosswordResult r) {
        return Boolean.TRUE.equals(r.getDuo()) ? name + " et al." : name;
    }

    static String formatMmSs(int totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override public int leadingSpaces() { return 1; }
    @Override public int baseGap() { return 3; }
    @Override public int maxEmojisPerRow() { return 6; }
    @Override public boolean usesScoreLabelRow() { return true; }

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
