package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.service.history.CrosswordGame;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryService;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryStats;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MiniCrosswordScoreboardTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 31);
    private final CrosswordHistoryService historyService = Mockito.mock(CrosswordHistoryService.class);
    private final MiniCrosswordScoreboard scoreboard = new MiniCrosswordScoreboard(historyService);

    {
        when(historyService.getStats(any(), any(), any())).thenReturn(CrosswordHistoryStats.EMPTY);
    }

    private Scoreboard sbWith(CrosswordResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), DATE);
        sb.addResult(result);
        return sb;
    }

    private CrosswordResult result(String time, int seconds) {
        return new MiniCrosswordResult("raw", "author", null, time, seconds, DATE);
    }

    @Test
    void hasResultReturnsTrueWhenPresent() {
        assertThat(scoreboard.hasResult(sbWith(result("0:30", 30)))).isTrue();
    }

    @Test
    void hasResultReturnsFalseWhenNull() {
        assertThat(scoreboard.hasResult(new Scoreboard(new User("c1", "test", "u1"), DATE))).isFalse();
    }

    @Test
    void headerContainsDateFormatted() {
        String header = scoreboard.header(sbWith(result("0:30", 30)));
        assertThat(header).isEqualTo("Mini - 3/31/2026");
    }

    @Test
    void scoreLabelReturnsTimeString() {
        assertThat(scoreboard.scoreLabel(sbWith(result("0:30", 30)))).isEqualTo("0:30");
    }

    @Test
    void emojiGridRowsReturnsEmpty() {
        assertThat(scoreboard.emojiGridRows(sbWith(result("0:30", 30)))).isEmpty();
    }

    @Test
    void fasterTimeWins() {
        Scoreboard fast = sbWith(result("0:20", 20));
        Scoreboard slow = sbWith(result("0:45", 45));
        ComparisonOutcome outcome = scoreboard.determineOutcome(fast, "Alice", slow, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Alice");
        assertThat(win.differentialLabel()).isEqualTo("0:25");
    }

    @Test
    void slowerTimeLoses() {
        Scoreboard slow = sbWith(result("1:00", 60));
        Scoreboard fast = sbWith(result("0:30", 30));
        ComparisonOutcome outcome = scoreboard.determineOutcome(slow, "Alice", fast, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob");
        assertThat(win.differentialLabel()).isEqualTo("0:30");
    }

    @Test
    void sameTimeIsNuke() {
        Scoreboard s1 = sbWith(result("0:30", 30));
        Scoreboard s2 = sbWith(result("0:30", 30));
        ComparisonOutcome outcome = scoreboard.determineOutcome(s1, "Alice", s2, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Nuke.class);
    }

    @Test
    void gameTypeIsMini() {
        assertThat(scoreboard.gameType()).isEqualTo("Mini");
    }

    // ── Avg / PB extra rows ───────────────────────────────────────────────────

    @Test
    void extraRowsReturnDashWhenNoHistory() {
        Scoreboard sb1 = sbWith(result("0:30", 30));
        Scoreboard sb2 = sbWith(result("0:45", 45));
        List<ExtraRow> rows = scoreboard.extraRowsBelowOutcome(sb1, sb2);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).label()).isEqualTo("avg");
        assertThat(rows.get(0).leftValue()).isEqualTo("-");
        assertThat(rows.get(0).rightValue()).isEqualTo("-");
        assertThat(rows.get(1).label()).isEqualTo("pb");
    }

    @Test
    void extraRowsShowFormattedTimesWhenHistoryPresent() {
        User u1 = new User("c1", "Alice", "u1");
        User u2 = new User("c2", "Bob", "u2");
        Scoreboard sb1 = new Scoreboard(u1, DATE);
        sb1.addResult(result("0:30", 30));
        Scoreboard sb2 = new Scoreboard(u2, DATE);
        sb2.addResult(result("0:45", 45));

        CrosswordHistoryStats stats1 = new CrosswordHistoryStats(OptionalInt.of(38), OptionalInt.of(28));
        CrosswordHistoryStats stats2 = new CrosswordHistoryStats(OptionalInt.of(50), OptionalInt.of(40));
        when(historyService.getStats(eq(u1), eq(CrosswordGame.MINI), eq(Optional.empty()))).thenReturn(stats1);
        when(historyService.getStats(eq(u2), eq(CrosswordGame.MINI), eq(Optional.empty()))).thenReturn(stats2);

        List<ExtraRow> rows = scoreboard.extraRowsBelowOutcome(sb1, sb2);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).leftValue()).isEqualTo("0:38");
        assertThat(rows.get(0).rightValue()).isEqualTo("0:50");
        assertThat(rows.get(1).leftValue()).isEqualTo("0:28");
        assertThat(rows.get(1).rightValue()).isEqualTo("0:40");
    }

    @Test
    void extraRowsQueryWithEmptyWeekday() {
        Scoreboard sb1 = sbWith(result("0:30", 30));
        Scoreboard sb2 = sbWith(result("0:45", 45));
        scoreboard.extraRowsBelowOutcome(sb1, sb2);
        Mockito.verify(historyService, Mockito.times(2))
                .getStats(any(), eq(CrosswordGame.MINI), eq(Optional.empty()));
    }
}
