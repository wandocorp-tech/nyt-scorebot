package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.CrosswordType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MainCrosswordScoreboardTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 31);
    private final MainCrosswordScoreboard scoreboard = new MainCrosswordScoreboard();

    private Scoreboard sbWith(CrosswordResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), DATE);
        sb.setMainCrosswordResult(result);
        return sb;
    }

    private CrosswordResult result(String time, int seconds) {
        return new CrosswordResult("raw", "author", null, CrosswordType.MAIN, time, seconds, DATE);
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
        assertThat(header).isEqualTo("Main - 3/31/2026");
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
    void fasterTimeWins() {
        Scoreboard fast = sbWith(result("10:00", 600));
        Scoreboard slow = sbWith(result("15:00", 900));
        ComparisonOutcome outcome = scoreboard.determineOutcome(fast, "Alice", slow, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Alice");
        assertThat(win.differential()).isEqualTo(300);
    }

    @Test
    void slowerTimeLoses() {
        Scoreboard slow = sbWith(result("20:00", 1200));
        Scoreboard fast = sbWith(result("12:00", 720));
        ComparisonOutcome outcome = scoreboard.determineOutcome(slow, "Alice", fast, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob");
        assertThat(win.differential()).isEqualTo(480);
    }

    @Test
    void sameTimeTies() {
        Scoreboard s1 = sbWith(result("15:00", 900));
        Scoreboard s2 = sbWith(result("15:00", 900));
        ComparisonOutcome outcome = scoreboard.determineOutcome(s1, "Alice", s2, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    // ── Flags row rendering ───────────────────────────────────────────────────

    @Test
    void noFlagsReturnsEmptyString() {
        Scoreboard sb = sbWith(result("15:00", 900));
        assertThat(scoreboard.flagsRow(sb)).isEmpty();
    }

    @Test
    void emojiGridRowsAlwaysEmpty() {
        CrosswordResult r = result("15:00", 900);
        r.setDuo(true);
        assertThat(scoreboard.emojiGridRows(sbWith(r))).isEmpty();
    }

    @Test
    void duoOnlyReturnsDuoFlag() {
        CrosswordResult r = result("15:00", 900);
        r.setDuo(true);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("👫");
    }

    @Test
    void lookupsOnlyReturnsLookupsFlag() {
        CrosswordResult r = result("15:00", 900);
        r.setLookups(3);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("🔍×3");
    }

    @Test
    void checkOnlyReturnsCheckFlag() {
        CrosswordResult r = result("15:00", 900);
        r.setCheckUsed(true);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("✓");
    }

    @Test
    void allFlagsReturnsCombinedString() {
        CrosswordResult r = result("15:00", 900);
        r.setDuo(true);
        r.setLookups(2);
        r.setCheckUsed(true);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEqualTo("👫 🔍×2 ✓");
    }

    @Test
    void zeroLookupsDoesNotShowFlag() {
        CrosswordResult r = result("15:00", 900);
        r.setLookups(0);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEmpty();
    }

    @Test
    void falseDuoDoesNotShowFlag() {
        CrosswordResult r = result("15:00", 900);
        r.setDuo(false);
        assertThat(scoreboard.flagsRow(sbWith(r))).isEmpty();
    }
}
