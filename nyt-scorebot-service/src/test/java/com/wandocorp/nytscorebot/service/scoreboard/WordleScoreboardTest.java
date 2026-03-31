package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WordleScoreboardTest {

    private static final String WORDLE_6 =
            "Wordle 1738 6/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛⬛🟨\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩⬛\n⬛🟨🟩🟨⬛\n🟩🟩🟩🟩⬛";
    private static final String WORDLE_4 =
            "Wordle 1738 4/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛⬛🟨\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩🟩";
    private static final String WORDLE_FAILED =
            "Wordle 1738 X/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛⬛🟨\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩⬛\n⬛🟨🟩🟨⬛\n🟩🟩🟩🟩⬛";

    private final WordleScoreboard scoreboard = new WordleScoreboard();

    private Scoreboard sbWith(WordleResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb.setWordleResult(result);
        return sb;
    }

    private WordleResult result(String raw, int attempts, boolean completed) {
        return new WordleResult(raw, "author", null, 1738, attempts, completed, false);
    }

    @Test
    void williamSixVsConorFour_conorWins() {
        Scoreboard william = sbWith(result(WORDLE_6, 6, true));
        Scoreboard conor = sbWith(result(WORDLE_4, 4, true));

        ComparisonOutcome outcome = scoreboard.determineOutcome(william, "William", conor, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Conor");
        assertThat(win.differential()).isEqualTo(2);
    }

    @Test
    void bothFourAttempts_tie() {
        Scoreboard p1 = sbWith(result(WORDLE_4, 4, true));
        Scoreboard p2 = sbWith(result(WORDLE_4, 4, true));

        ComparisonOutcome outcome = scoreboard.determineOutcome(p1, "William", p2, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void bothFailed_tie() {
        Scoreboard p1 = sbWith(result(WORDLE_FAILED, 0, false));
        Scoreboard p2 = sbWith(result(WORDLE_FAILED, 0, false));

        ComparisonOutcome outcome = scoreboard.determineOutcome(p1, "William", p2, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void oneCompleteOneFailed_completedWinsNoDifferential() {
        Scoreboard completed = sbWith(result(WORDLE_4, 4, true));
        Scoreboard failed = sbWith(result(WORDLE_FAILED, 0, false));

        ComparisonOutcome outcome = scoreboard.determineOutcome(completed, "William", failed, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("William");
        assertThat(win.differential()).isNull();
    }

    @Test
    void failedVsCompleted_completedWinsNoDifferential() {
        Scoreboard failed = sbWith(result(WORDLE_FAILED, 0, false));
        Scoreboard completed = sbWith(result(WORDLE_4, 4, true));

        ComparisonOutcome outcome = scoreboard.determineOutcome(failed, "William", completed, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Conor");
        assertThat(win.differential()).isNull();
    }

    @Test
    void scoreLabelCompleted() {
        Scoreboard sb = sbWith(result(WORDLE_4, 4, true));
        assertThat(scoreboard.scoreLabel(sb)).isEqualTo("4");
    }

    @Test
    void scoreLabelFailed() {
        Scoreboard sb = sbWith(result(WORDLE_FAILED, 0, false));
        assertThat(scoreboard.scoreLabel(sb)).isEqualTo("X");
    }

    @Test
    void emojiGridRowsExtractsCorrectLines() {
        Scoreboard sb = sbWith(result(WORDLE_6, 6, true));
        List<String> rows = scoreboard.emojiGridRows(sb);
        assertThat(rows).hasSize(6);
        assertThat(rows.get(0)).isEqualTo("⬛⬛⬛🟨⬛");
        assertThat(rows.get(5)).isEqualTo("🟩🟩🟩🟩⬛");
    }

    @Test
    void emojiGridRowsSkipsNonEmojiLines() {
        Scoreboard sb = sbWith(result(WORDLE_4, 4, true));
        List<String> rows = scoreboard.emojiGridRows(sb);
        assertThat(rows).hasSize(4);
        assertThat(rows.get(3)).isEqualTo("🟩🟩🟩🟩🟩");
    }
}
