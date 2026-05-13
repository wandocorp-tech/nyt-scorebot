package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.service.history.CrosswordGame;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryService;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryStats;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MainCrosswordScoreboardTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 31);
    private final CrosswordHistoryService historyService = Mockito.mock(CrosswordHistoryService.class);
    private final MainCrosswordScoreboard scoreboard = new MainCrosswordScoreboard(historyService);

    {
        Mockito.when(historyService.getStats(any(), any(), any())).thenReturn(CrosswordHistoryStats.EMPTY);
    }

    private Scoreboard sbWith(MainCrosswordResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), DATE);
        sb.addResult(result);
        return sb;
    }

    private MainCrosswordResult result(String time, int seconds) {
        return new MainCrosswordResult("raw", "author", null, time, seconds, DATE);
    }

    private MainCrosswordResult resultAided(String time, int seconds, Boolean check, Integer lookups) {
        MainCrosswordResult r = result(time, seconds);
        r.setCheckUsed(check);
        r.setLookups(lookups);
        return r;
    }

    private MainCrosswordResult resultDuo(String time, int seconds) {
        MainCrosswordResult r = result(time, seconds);
        r.setDuo(true);
        return r;
    }

    @Test
    void hasResultReturnsTrueWhenPresent() {
        assertThat(scoreboard.hasResult(sbWith(result("15:00", 900)))).isTrue();
    }

    @Test
    void hasResultReturnsFalseWhenNull() {
        assertThat(scoreboard.hasResult(new Scoreboard(new User("c1", "test", "u1"), DATE))).isFalse();
    }

    @Test
    void headerContainsDateFormatted() {
        String header = scoreboard.header(sbWith(result("15:00", 900)));
        assertThat(header).isEqualTo("Tuesday - 3/31/2026");
    }

    @Test
    void scoreLabelReturnsTimeString() {
        assertThat(scoreboard.scoreLabel(sbWith(result("15:42", 942)))).isEqualTo("15:42");
    }

    @Test
    void gameTypeIsMain() {
        assertThat(scoreboard.gameType()).isEqualTo("Main");
    }

    // ── Outcome determination ─────────────────────────────────────────────────

    @Test
    void neitherAidedFasterTimeWins() {
        Scoreboard fast = sbWith(result("10:00", 600));
        Scoreboard slow = sbWith(result("15:00", 900));
        ComparisonOutcome outcome = scoreboard.determineOutcome(fast, "Alice", slow, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Alice");
        assertThat(win.differentialLabel()).isEqualTo("5:00");
    }

    @Test
    void neitherAidedSlowerTimeLoses() {
        Scoreboard slow = sbWith(result("20:00", 1200));
        Scoreboard fast = sbWith(result("12:00", 720));
        ComparisonOutcome outcome = scoreboard.determineOutcome(slow, "Alice", fast, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob");
        assertThat(win.differentialLabel()).isEqualTo("8:00");
    }

    @Test
    void neitherAidedSameTimeIsNuke() {
        Scoreboard s1 = sbWith(result("15:00", 900));
        Scoreboard s2 = sbWith(result("15:00", 900));
        ComparisonOutcome outcome = scoreboard.determineOutcome(s1, "Alice", s2, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Nuke.class);
    }

    @Test
    void oneUsedCheckOtherUnaidedUnaidedWins() {
        Scoreboard aided = sbWith(resultAided("8:00", 480, true, null));
        Scoreboard clean = sbWith(result("20:00", 1200));
        ComparisonOutcome outcome = scoreboard.determineOutcome(aided, "Alice", clean, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob");
        assertThat(win.differentialLabel()).isNull();
    }

    @Test
    void oneUsedLookupsOtherUnaidedUnaidedWins() {
        Scoreboard aided = sbWith(resultAided("8:00", 480, null, 3));
        Scoreboard clean = sbWith(result("20:00", 1200));
        ComparisonOutcome outcome = scoreboard.determineOutcome(clean, "Alice", aided, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Alice");
        assertThat(win.differentialLabel()).isNull();
    }

    @Test
    void bothAidedIsTie() {
        Scoreboard a = sbWith(resultAided("8:00", 480, true, null));
        Scoreboard b = sbWith(resultAided("9:00", 540, null, 2));
        ComparisonOutcome outcome = scoreboard.determineOutcome(a, "Alice", b, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void zeroLookupsAndNullCheckIsNotAided() {
        Scoreboard a = sbWith(resultAided("10:00", 600, null, 0));
        Scoreboard b = sbWith(resultAided("12:00", 720, false, 0));
        ComparisonOutcome outcome = scoreboard.determineOutcome(a, "Alice", b, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Alice");
        assertThat(win.differentialLabel()).isEqualTo("2:00");
    }

    @Test
    void duoWinnerGetsEtAlSuffixOnTimeWin() {
        Scoreboard duo = sbWith(resultDuo("10:00", 600));
        Scoreboard solo = sbWith(result("15:00", 900));
        ComparisonOutcome outcome = scoreboard.determineOutcome(duo, "Alice", solo, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Alice et al.");
        assertThat(win.differentialLabel()).isEqualTo("5:00");
    }

    @Test
    void duoWinnerGetsEtAlSuffixOnDisqualificationWin() {
        Scoreboard aided = sbWith(resultAided("8:00", 480, true, null));
        MainCrosswordResult duo = resultDuo("20:00", 1200);
        Scoreboard cleanDuo = sbWith(duo);
        ComparisonOutcome outcome = scoreboard.determineOutcome(aided, "Alice", cleanDuo, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob et al.");
        assertThat(win.differentialLabel()).isNull();
    }

    // ── Flags row rendering ───────────────────────────────────────────────────

    @Test
    void noFlagsReturnsEmptyString() {
        Scoreboard sb = sbWith(result("15:00", 900));
        assertThat(scoreboard.flagsRow(sb)).isEmpty();
    }

    @Test
    void emojiGridRowsAlwaysEmpty() {
        MainCrosswordResult r = result("15:00", 900);
        r.setDuo(true);
        assertThat(scoreboard.emojiGridRows(sbWith(r))).isEmpty();
    }

    @Test
    void duoOnlyReturnsDuoFlag() {
        MainCrosswordResult r = result("15:00", 900);
        r.setDuo(true);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("👫");
    }

    @Test
    void lookupsOnlyReturnsLookupsFlag() {
        MainCrosswordResult r = result("15:00", 900);
        r.setLookups(3);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("🔍×3");
    }

    @Test
    void checkOnlyReturnsCheckFlag() {
        MainCrosswordResult r = result("15:00", 900);
        r.setCheckUsed(true);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("✅");
    }

    @Test
    void allFlagsReturnsCombinedString() {
        MainCrosswordResult r = result("15:00", 900);
        r.setDuo(true);
        r.setLookups(2);
        r.setCheckUsed(true);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("👫 🔍×2 ✅");
    }

    @Test
    void zeroLookupsDoesNotShowFlag() {
        MainCrosswordResult r = result("15:00", 900);
        r.setLookups(0);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEmpty();
    }

    @Test
    void falseDuoDoesNotShowFlag() {
        MainCrosswordResult r = result("15:00", 900);
        r.setDuo(false);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEmpty();
    }

    // ── Avg / PB extra rows ───────────────────────────────────────────────────

    @Test
    void extraRowsReturnDashWhenNoHistory() {
        // DATE is 2026-03-31 (Tuesday); historyService already mocked to return EMPTY
        Scoreboard sb1 = sbWith(result("15:00", 900));
        Scoreboard sb2 = sbWith(result("16:00", 960));
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
        sb1.addResult(result("15:00", 900));
        Scoreboard sb2 = new Scoreboard(u2, DATE);
        sb2.addResult(result("16:00", 960));

        CrosswordHistoryStats stats1 = new CrosswordHistoryStats(OptionalInt.of(840), OptionalInt.of(700));
        CrosswordHistoryStats stats2 = new CrosswordHistoryStats(OptionalInt.of(920), OptionalInt.of(800));
        when(historyService.getStats(eq(u1), eq(CrosswordGame.MAIN), eq(Optional.of(DayOfWeek.TUESDAY)))).thenReturn(stats1);
        when(historyService.getStats(eq(u2), eq(CrosswordGame.MAIN), eq(Optional.of(DayOfWeek.TUESDAY)))).thenReturn(stats2);

        List<ExtraRow> rows = scoreboard.extraRowsBelowOutcome(sb1, sb2);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).leftValue()).isEqualTo("14:00");
        assertThat(rows.get(0).rightValue()).isEqualTo("15:20");
        assertThat(rows.get(1).leftValue()).isEqualTo("11:40");
        assertThat(rows.get(1).rightValue()).isEqualTo("13:20");
    }

    @Test
    void extraRowsQueryWithCorrectWeekday() {
        // DATE (2026-03-31) is a Tuesday
        Scoreboard sb1 = sbWith(result("15:00", 900));
        Scoreboard sb2 = sbWith(result("16:00", 960));
        scoreboard.extraRowsBelowOutcome(sb1, sb2);
        Mockito.verify(historyService, Mockito.times(2))
                .getStats(any(), eq(CrosswordGame.MAIN), eq(Optional.of(DayOfWeek.TUESDAY)));
    }
}
