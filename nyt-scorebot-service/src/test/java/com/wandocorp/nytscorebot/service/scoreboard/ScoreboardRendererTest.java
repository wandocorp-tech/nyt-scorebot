package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.CrosswordType;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardRendererTest {

    private static final String WORDLE_6 =
            "Wordle 1738 6/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛⬛🟨\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩⬛\n⬛🟨🟩🟨⬛\n🟩🟩🟩🟩⬛";
    private static final String WORDLE_4 =
            "Wordle 1738 4/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛⬛🟨\n🟨🟨🟩⬛⬛\n🟩🟩🟩🟩🟩";

    private static final Map<String, Map<String, Integer>> STREAKS = Map.of(
            "William", Map.of("Wordle", 5),
            "Conor", Map.of("Wordle", 3)
    );

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

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor", STREAKS);

        assertThat(rendered).isPresent();
        String output = rendered.get();

        assertThat(output).startsWith("```\n");
        assertThat(output).endsWith("```");
        assertThat(output).contains("Wordle #1738");
        assertThat(output).contains("---------------------------------");
        assertThat(output).contains("William  |  Conor");
        assertThat(output).contains("5  |  3");
        assertThat(output).doesNotContain("🔥");
    }

    @Test
    void singlePlayerRender_onlyWilliamSubmitted() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", null, "Conor", STREAKS);

        assertThat(rendered).isPresent();
        String output = rendered.get();

        assertThat(output).contains("William");
        assertThat(output).doesNotContain("|");
        assertThat(output).contains("5");
        assertThat(output).doesNotContain("🔥");
    }

    @Test
    void noResults_returnsEmpty() {
        Optional<String> rendered = renderer.render(wordleGame, null, "William", null, "Conor", STREAKS);

        assertThat(rendered).isEmpty();
    }

    @Test
    void columnOrdering_moreRowsGoesLeft() {
        Scoreboard william = sbWith(wordle(WORDLE_6, 6, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor", STREAKS);

        assertThat(rendered).isPresent();
        assertThat(rendered.get()).contains("William  |  Conor");
    }

    @Test
    void columnOrdering_tieInRowCount_configuredOrderWilliamLeft() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor", STREAKS);

        assertThat(rendered).isPresent();
        assertThat(rendered.get()).contains("William  |  Conor");
    }

    @Test
    void columnOrdering_sb2MoreRows_sb2GoesLeft() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_6, 6, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor", STREAKS);

        assertThat(rendered).isPresent();
        assertThat(rendered.get()).contains("Conor  |  William");
    }

    @Test
    void renderAll_returnsMapWithGameType() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        var result = renderer.renderAll(william, "William", conor, "Conor", STREAKS);

        assertThat(result).containsKey("Wordle");
    }

    @Test
    void renderAll_emptyWhenNeitherHasResult() {
        var result = renderer.renderAll(null, "William", null, "Conor", STREAKS);

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

    // ── Streak display tests ─────────────────────────────────────────────────

    @Test
    void emojiScoreboardRendersStreakRowInsteadOfOutcome() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor", STREAKS);

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("5  |  3");
        assertThat(output).doesNotContain("🔥");
        assertThat(output).doesNotContain("🤝 Tie!");
        assertThat(output).doesNotContain("🏆");
    }

    @Test
    void crosswordScoreboardRendersOutcomeNotStreak() {
        MiniCrosswordScoreboard miniGame = new MiniCrosswordScoreboard();
        ScoreboardRenderer crosswordRenderer = new ScoreboardRenderer(List.of(miniGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.setMiniCrosswordResult(new CrosswordResult("raw", "a", null, CrosswordType.MINI, "0:30", 30, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.setMiniCrosswordResult(new CrosswordResult("raw", "a", null, CrosswordType.MINI, "1:00", 60, LocalDate.now()));

        Optional<String> rendered = crosswordRenderer.render(miniGame, sb1, "William", sb2, "Conor",
                Map.of("William", Map.of("Mini", 5), "Conor", Map.of("Mini", 3)));

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("🏆 William wins!");
        assertThat(output).doesNotContain("🔥");
    }

    @Test
    void streakRowShowsZeroWhenNoStreakData() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Map<String, Map<String, Integer>> emptyStreaks = Map.of();
        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor", emptyStreaks);

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("0  |  0");
        assertThat(output).doesNotContain("🔥");
    }
}
