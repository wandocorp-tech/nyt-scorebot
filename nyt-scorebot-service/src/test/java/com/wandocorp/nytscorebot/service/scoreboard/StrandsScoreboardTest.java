package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.StrandsResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrandsScoreboardTest {

    private static final String STRANDS_RAW =
            "Strands #123\n\"Test Theme\"\n🔵🟡🟠🟢\n🔴🟣🔵🔵";

    private final StrandsScoreboard scoreboard = new StrandsScoreboard();

    private Scoreboard sbWith(StrandsResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb.setStrandsResult(result);
        return sb;
    }

    private StrandsResult result(int hints) {
        return new StrandsResult(STRANDS_RAW, "author", null, 123, hints);
    }

    @Test
    void williamTwoHintsVsConorZero_conorWins() {
        Scoreboard william = sbWith(result(2));
        Scoreboard conor = sbWith(result(0));

        ComparisonOutcome outcome = scoreboard.determineOutcome(william, "William", conor, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Conor");
        assertThat(win.differential()).isEqualTo(2);
    }

    @Test
    void conorZeroHintsVsWilliamTwoHints_conorWins() {
        Scoreboard conor = sbWith(result(0));
        Scoreboard william = sbWith(result(2));

        ComparisonOutcome outcome = scoreboard.determineOutcome(conor, "Conor", william, "William");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Win.class);
        ComparisonOutcome.Win win = (ComparisonOutcome.Win) outcome;
        assertThat(win.winnerName()).isEqualTo("Conor");
        assertThat(win.differential()).isEqualTo(2);
    }

    @Test
    void sameHints_tie() {
        Scoreboard p1 = sbWith(result(1));
        Scoreboard p2 = sbWith(result(1));

        ComparisonOutcome outcome = scoreboard.determineOutcome(p1, "William", p2, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void bothZeroHints_tie() {
        Scoreboard p1 = sbWith(result(0));
        Scoreboard p2 = sbWith(result(0));

        ComparisonOutcome outcome = scoreboard.determineOutcome(p1, "William", p2, "Conor");

        assertThat(outcome).isInstanceOf(ComparisonOutcome.Tie.class);
    }

    @Test
    void emojiGridRowsExtractsCorrectRows() {
        Scoreboard sb = sbWith(result(0));
        List<String> rows = scoreboard.emojiGridRows(sb);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo("🔵🟡🟠🟢");
        assertThat(rows.get(1)).isEqualTo("🔴🟣🔵🔵");
    }

    @Test
    void emojiGridRowsSkipsNonEmojiLines() {
        Scoreboard sb = sbWith(result(0));
        List<String> rows = scoreboard.emojiGridRows(sb);
        rows.forEach(row -> assertThat(row).doesNotContain("Strands").doesNotContain("\""));
    }

    @Test
    void scoreLabel() {
        Scoreboard sb = sbWith(result(3));
        assertThat(scoreboard.scoreLabel(sb)).isEqualTo("3");
    }

    @Test
    void header() {
        Scoreboard sb = sbWith(result(0));
        assertThat(scoreboard.header(sb)).isEqualTo("Strands #123");
    }

    @Test
    void hasResultReturnsFalseForNull() {
        assertThat(scoreboard.hasResult(null)).isFalse();
    }

    @Test
    void hasResultReturnsTrueWhenResultPresent() {
        Scoreboard sb = sbWith(result(0));
        assertThat(scoreboard.hasResult(sb)).isTrue();
    }
}
