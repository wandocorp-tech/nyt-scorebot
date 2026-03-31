package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardRendererTest {

    private static final String WORDLE_6 =
            "Wordle 1738 6/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛⬛🟨\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩⬛\n⬛🟨🟩🟨⬛\n🟩🟩🟩🟩⬛";
    private static final String WORDLE_4 =
            "Wordle 1738 4/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛⬛🟨\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩🟩";

    private WordleScoreboard wordleGame;
    private ScoreboardRenderer renderer;

    @BeforeEach
    void setUp() {
        wordleGame = new WordleScoreboard();
        renderer = new ScoreboardRenderer(List.of(wordleGame));
    }

    private Scoreboard sbWith(WordleResult result) {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb.setWordleResult(result);
        return sb;
    }

    private WordleResult wordle(String raw, int attempts, boolean completed) {
        return new WordleResult(raw, "author", null, 1738, attempts, completed, false);
    }

    @Test
    void twoPlayerRender_williamSixConorFour() {
        Scoreboard william = sbWith(wordle(WORDLE_6, 6, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor");

        assertThat(rendered).isPresent();
        String output = rendered.get();

        assertThat(output).startsWith("```\n");
        assertThat(output).endsWith("```");
        assertThat(output).contains("Wordle #1738");
        assertThat(output).contains("-----------------------------------");
        assertThat(output).contains("William - 6  |  Conor - 4");
        assertThat(output).contains("🏆 Conor wins! (-2)");
    }

    @Test
    void singlePlayerRender_onlyWilliamSubmitted() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", null, "Conor");

        assertThat(rendered).isPresent();
        String output = rendered.get();

        assertThat(output).contains("William - 4");
        assertThat(output).doesNotContain("|");
        assertThat(output).contains("⏳ Conor hasn't submitted");
    }

    @Test
    void noResults_returnsEmpty() {
        Optional<String> rendered = renderer.render(wordleGame, null, "William", null, "Conor");

        assertThat(rendered).isEmpty();
    }

    @Test
    void columnOrdering_moreRowsGoesLeft() {
        // William has 6 rows, Conor has 4 rows → William goes left
        Scoreboard william = sbWith(wordle(WORDLE_6, 6, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor");

        assertThat(rendered).isPresent();
        // William (6 rows) is left, Conor (4 rows) is right
        assertThat(rendered.get()).contains("William - 6  |  Conor - 4");
    }

    @Test
    void columnOrdering_tieInRowCount_configuredOrderWilliamLeft() {
        // Both have 4 rows → configured order (sb1 = William) goes left
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor");

        assertThat(rendered).isPresent();
        assertThat(rendered.get()).contains("William - 4  |  Conor - 4");
    }

    @Test
    void columnOrdering_sb2MoreRows_sb2GoesLeft() {
        // Conor has 6 rows (sb2), William has 4 rows (sb1) → Conor goes left
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_6, 6, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor");

        assertThat(rendered).isPresent();
        // Conor (6 rows) goes left, William (4 rows) goes right
        assertThat(rendered.get()).contains("Conor - 6  |  William - 4");
    }

    @Test
    void renderAll_returnsMapWithGameType() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        var result = renderer.renderAll(william, "William", conor, "Conor");

        assertThat(result).containsKey("WORDLE");
    }

    @Test
    void renderAll_emptyWhenNeitherHasResult() {
        var result = renderer.renderAll(null, "William", null, "Conor");

        assertThat(result).isEmpty();
    }

    @Test
    void hasResult_returnsFalseForNullScoreboard() {
        assertThat(wordleGame.hasResult(null)).isFalse();
    }

    @Test
    void hasResult_returnsFalseWhenWordleResultNull() {
        Scoreboard sb = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        assertThat(wordleGame.hasResult(sb)).isFalse();
    }
}
