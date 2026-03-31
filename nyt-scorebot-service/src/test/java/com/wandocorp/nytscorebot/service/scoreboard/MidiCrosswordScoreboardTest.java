package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.CrosswordType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MidiCrosswordScoreboardTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 31);
    private final MidiCrosswordScoreboard scoreboard = new MidiCrosswordScoreboard();

    private Scoreboard sbWith(CrosswordResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), DATE);
        sb.setMidiCrosswordResult(result);
        return sb;
    }

    private CrosswordResult result(String time, int seconds) {
        return new CrosswordResult("raw", "author", null, CrosswordType.MIDI, time, seconds, DATE);
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
        assertThat(win.differential()).isEqualTo(90);
    }

    @Test
    void slowerTimeLoses() {
        Scoreboard slow = sbWith(result("5:00", 300));
        Scoreboard fast = sbWith(result("3:00", 180));
        ComparisonOutcome outcome = scoreboard.determineOutcome(slow, "Alice", fast, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Bob");
        assertThat(win.differential()).isEqualTo(120);
    }

    @Test
    void sameTimeTies() {
        Scoreboard s1 = sbWith(result("3:00", 180));
        Scoreboard s2 = sbWith(result("3:00", 180));
        ComparisonOutcome outcome = scoreboard.determineOutcome(s1, "Alice", s2, "Bob");
        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void gameTypeIsMidi() {
        assertThat(scoreboard.gameType()).isEqualTo("Midi");
    }
}
