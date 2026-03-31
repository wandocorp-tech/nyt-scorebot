package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.CrosswordType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCrosswordScoreboardTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 31);
    private final MiniCrosswordScoreboard scoreboard = new MiniCrosswordScoreboard();

    private Scoreboard sbWith(CrosswordResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), DATE);
        sb.setMiniCrosswordResult(result);
        return sb;
    }

    private CrosswordResult result(String time, int seconds) {
        return new CrosswordResult("raw", "author", null, CrosswordType.MINI, time, seconds, DATE);
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
        assertThat(win.differential()).isEqualTo(25);
    }

    @Test
    void slowerTimeLoses() {
        Scoreboard slow = sbWith(result("1:00", 60));
        Scoreboard fast = sbWith(result("0:30", 30));
        ComparisonOutcome outcome = scoreboard.determineOutcome(slow, "Alice", fast, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob");
        assertThat(win.differential()).isEqualTo(30);
    }

    @Test
    void sameTimeTies() {
        Scoreboard s1 = sbWith(result("0:30", 30));
        Scoreboard s2 = sbWith(result("0:30", 30));
        ComparisonOutcome outcome = scoreboard.determineOutcome(s1, "Alice", s2, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void gameTypeIsMini() {
        assertThat(scoreboard.gameType()).isEqualTo("Mini");
    }
}
