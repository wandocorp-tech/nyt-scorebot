package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.ConnectionsResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionsScoreboardTest {

    private static final String CONNECTIONS_0 =
            "Connections\nPuzzle #123\n🟦🟦🟦🟦\n🟩🟩🟩🟩\n🟨🟨🟨🟨\n🟪🟪🟪🟪";
    private static final String CONNECTIONS_2 =
            "Connections\nPuzzle #123\n🟨🟨🟨🟨\n🟩🟩🟩🟩\n🟦🟦🟦🟦\n🟪🟪🟪🟪";
    private static final String CONNECTIONS_FAILED =
            "Connections\nPuzzle #123\n🟨🟨🟩🟦\n🟨🟨🟩🟦\n🟩🟩🟩🟩\n🟪🟪🟪🟪";

    private final ConnectionsScoreboard scoreboard = new ConnectionsScoreboard();

    private Scoreboard sbWith(ConnectionsResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb.setConnectionsResult(result);
        return sb;
    }

    private ConnectionsResult result(String raw, int mistakes, boolean completed) {
        return new ConnectionsResult(raw, "author", null, 123, mistakes, completed, List.of());
    }

    @Test
    void williamTwoMistakesVsConorZero_conorWins() {
        Scoreboard william = sbWith(result(CONNECTIONS_2, 2, true));
        Scoreboard conor = sbWith(result(CONNECTIONS_0, 0, true));

        ComparisonOutcome outcome = scoreboard.determineOutcome(william, "William", conor, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Conor");
        assertThat(win.differential()).isNull();
    }

    @Test
    void bothZeroMistakes_tie() {
        Scoreboard p1 = sbWith(result(CONNECTIONS_0, 0, true));
        Scoreboard p2 = sbWith(result(CONNECTIONS_0, 0, true));

        ComparisonOutcome outcome = scoreboard.determineOutcome(p1, "William", p2, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void bothNonZeroMistakes_tie() {
        Scoreboard p1 = sbWith(result(CONNECTIONS_2, 1, true));
        Scoreboard p2 = sbWith(result(CONNECTIONS_2, 2, true));

        ComparisonOutcome outcome = scoreboard.determineOutcome(p1, "William", p2, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void bothFailed_tie() {
        Scoreboard p1 = sbWith(result(CONNECTIONS_FAILED, 0, false));
        Scoreboard p2 = sbWith(result(CONNECTIONS_FAILED, 0, false));

        ComparisonOutcome outcome = scoreboard.determineOutcome(p1, "William", p2, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void oneCompleteOneFailed_completedWinsNoDifferential() {
        Scoreboard completed = sbWith(result(CONNECTIONS_0, 0, true));
        Scoreboard failed = sbWith(result(CONNECTIONS_FAILED, 0, false));

        ComparisonOutcome outcome = scoreboard.determineOutcome(completed, "William", failed, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("William");
        assertThat(win.differential()).isNull();
    }

    @Test
    void failedVsCompleted_completedWinsNoDifferential() {
        Scoreboard failed = sbWith(result(CONNECTIONS_FAILED, 0, false));
        Scoreboard completed = sbWith(result(CONNECTIONS_0, 0, true));

        ComparisonOutcome outcome = scoreboard.determineOutcome(failed, "William", completed, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Conor");
        assertThat(win.differential()).isNull();
    }

    @Test
    void emojiGridRowsExtractsCorrectRows() {
        Scoreboard sb = sbWith(result(CONNECTIONS_0, 0, true));
        List<String> rows = scoreboard.emojiGridRows(sb);
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0)).isEqualTo("🟦🟦🟦🟦");
        assertThat(rows.get(3)).isEqualTo("🟪🟪🟪🟪");
    }

    @Test
    void emojiGridRowsSkipsNonEmojiLines() {
        Scoreboard sb = sbWith(result(CONNECTIONS_2, 2, true));
        List<String> rows = scoreboard.emojiGridRows(sb);
        assertThat(rows).hasSize(4);
        rows.forEach(row -> assertThat(row).doesNotContain("Connections").doesNotContain("Puzzle"));
    }

    @Test
    void scoreLabelCompleted() {
        Scoreboard sb = sbWith(result(CONNECTIONS_0, 0, true));
        assertThat(scoreboard.scoreLabel(sb)).isEqualTo("0");
    }

    @Test
    void scoreLabelFailed() {
        Scoreboard sb = sbWith(result(CONNECTIONS_FAILED, 0, false));
        assertThat(scoreboard.scoreLabel(sb)).isEqualTo("X");
    }

    @Test
    void header() {
        Scoreboard sb = sbWith(result(CONNECTIONS_0, 0, true));
        assertThat(scoreboard.header(sb)).isEqualTo("Connections #123");
    }

    @Test
    void hasResultReturnsFalseForNull() {
        assertThat(scoreboard.hasResult(null)).isFalse();
    }

    @Test
    void hasResultReturnsTrueWhenResultPresent() {
        Scoreboard sb = sbWith(result(CONNECTIONS_0, 0, true));
        assertThat(scoreboard.hasResult(sb)).isTrue();
    }
}
