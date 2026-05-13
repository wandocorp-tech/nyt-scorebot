package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.MidiCrosswordResult;
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

class MidiCrosswordScoreboardTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 31);
    private final CrosswordHistoryService historyService = Mockito.mock(CrosswordHistoryService.class);
    private final MidiCrosswordScoreboard scoreboard = new MidiCrosswordScoreboard(historyService);

    {
        when(historyService.getStats(any(), any(), any())).thenReturn(CrosswordHistoryStats.EMPTY);
    }

    private Scoreboard sbWith(CrosswordResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), DATE);
        sb.addResult(result);
        return sb;
    }

    private CrosswordResult result(String time, int seconds) {
        return new MidiCrosswordResult("raw", "author", null, time, seconds, DATE);
    }

    @Test
    void hasResultReturnsTrueWhenPresent() {
        assertThat(scoreboard.hasResult(sbWith(result("3:00", 180)))).isTrue();
    }

    @Test
    void hasResultReturnsFalseWhenNull() {
        assertThat(scoreboard.hasResult(new Scoreboard(new User("c1", "test", "u1"), DATE))).isFalse();
    }

    @Test
    void headerContainsDateFormatted() {
        String header = scoreboard.header(sbWith(result("3:00", 180)));
        assertThat(header).isEqualTo("Midi - 3/31/2026");
    }

    @Test
    void scoreLabelReturnsTimeString() {
        assertThat(scoreboard.scoreLabel(sbWith(result("3:45", 225)))).isEqualTo("3:45");
    }

    @Test
    void emojiGridRowsReturnsEmpty() {
        assertThat(scoreboard.emojiGridRows(sbWith(result("3:00", 180)))).isEmpty();
    }

    @Test
    void fasterTimeWins() {
        Scoreboard fast = sbWith(result("2:30", 150));
        Scoreboard slow = sbWith(result("4:00", 240));
        ComparisonOutcome outcome = scoreboard.determineOutcome(fast, "Alice", slow, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Alice");
        assertThat(win.differentialLabel()).isEqualTo("1:30");
    }

    @Test
    void slowerTimeLoses() {
        Scoreboard slow = sbWith(result("5:00", 300));
        Scoreboard fast = sbWith(result("3:00", 180));
        ComparisonOutcome outcome = scoreboard.determineOutcome(slow, "Alice", fast, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob");
        assertThat(win.differentialLabel()).isEqualTo("2:00");
    }

    @Test
    void sameTimeIsNuke() {
        Scoreboard s1 = sbWith(result("3:00", 180));
        Scoreboard s2 = sbWith(result("3:00", 180));
        ComparisonOutcome outcome = scoreboard.determineOutcome(s1, "Alice", s2, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Nuke.class);
    }

    @Test
    void gameTypeIsMidi() {
        assertThat(scoreboard.gameType()).isEqualTo("Midi");
    }

    // ── Avg / PB extra rows ───────────────────────────────────────────────────

    @Test
    void extraRowsReturnDashWhenNoHistory() {
        Scoreboard sb1 = sbWith(result("3:00", 180));
        Scoreboard sb2 = sbWith(result("4:00", 240));
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
        sb1.addResult(result("3:00", 180));
        Scoreboard sb2 = new Scoreboard(u2, DATE);
        sb2.addResult(result("4:00", 240));

        CrosswordHistoryStats stats1 = new CrosswordHistoryStats(OptionalInt.of(200), OptionalInt.of(150));
        CrosswordHistoryStats stats2 = new CrosswordHistoryStats(OptionalInt.of(260), OptionalInt.of(220));
        when(historyService.getStats(eq(u1), eq(CrosswordGame.MIDI), eq(Optional.empty()))).thenReturn(stats1);
        when(historyService.getStats(eq(u2), eq(CrosswordGame.MIDI), eq(Optional.empty()))).thenReturn(stats2);

        List<ExtraRow> rows = scoreboard.extraRowsBelowOutcome(sb1, sb2);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).leftValue()).isEqualTo("3:20");
        assertThat(rows.get(0).rightValue()).isEqualTo("4:20");
        assertThat(rows.get(1).leftValue()).isEqualTo("2:30");
        assertThat(rows.get(1).rightValue()).isEqualTo("3:40");
    }

    @Test
    void extraRowsQueryWithEmptyWeekday() {
        Scoreboard sb1 = sbWith(result("3:00", 180));
        Scoreboard sb2 = sbWith(result("4:00", 240));
        scoreboard.extraRowsBelowOutcome(sb1, sb2);
        Mockito.verify(historyService, Mockito.times(2))
                .getStats(any(), eq(CrosswordGame.MIDI), eq(Optional.empty()));
    }
}
