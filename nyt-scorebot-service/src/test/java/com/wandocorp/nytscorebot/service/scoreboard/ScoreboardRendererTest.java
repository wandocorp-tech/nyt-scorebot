package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
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

    private static final Map<String, Map<GameType, Integer>> STREAKS = Map.of(
            "William", Map.of(GameType.WORDLE, 5),
            "Conor", Map.of(GameType.WORDLE, 3)
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
        sb.addResult(result);
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
        sb1.addResult(new MiniCrosswordResult("raw", "a", null, "0:30", 30, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MiniCrosswordResult("raw", "a", null, "1:00", 60, LocalDate.now()));

        Optional<String> rendered = crosswordRenderer.render(miniGame, sb1, "William", sb2, "Conor",
                Map.of("William", Map.of(GameType.MINI_CROSSWORD, 5), "Conor", Map.of(GameType.MINI_CROSSWORD, 3)));

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("🏆 William wins!");
        assertThat(output).contains("0:30");
        assertThat(output).contains("1:00");
        assertThat(output).doesNotContain("🔥");
        // Time row uses spaces, not pipe divider
        assertThat(output).contains("0:30     1:00");
    }

    @Test
    void mainCrosswordRendersFlagsRowBelowTimeRow() {
        MainCrosswordScoreboard mainGame = new MainCrosswordScoreboard();
        ScoreboardRenderer crosswordRenderer = new ScoreboardRenderer(List.of(mainGame));

        MainCrosswordResult r1 = new MainCrosswordResult("raw", "a", null, "5:00", 300, LocalDate.now());
        r1.setDuo(true);
        r1.setLookups(2);
        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(r1);

        MainCrosswordResult r2 = new MainCrosswordResult("raw", "a", null, "7:30", 450, LocalDate.now());
        r2.setCheckUsed(true);
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(r2);

        Optional<String> rendered = crosswordRenderer.render(mainGame, sb1, "William", sb2, "Conor",
                Map.of());

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("5:00");
        assertThat(output).contains("7:30");
        assertThat(output).contains("👫 🔍×2");
        assertThat(output).contains("✓");
        // Time row uses spaces, not pipe divider
        assertThat(output).contains("5:00     7:30");

        // Flags row appears after time row
        int timeRowIdx = output.indexOf("5:00");
        int flagsRowIdx = output.indexOf("👫 🔍×2");
        assertThat(flagsRowIdx).isGreaterThan(timeRowIdx);
    }

    @Test
    void crosswordScoreboardSinglePlayerRendersTime() {
        MiniCrosswordScoreboard miniGame = new MiniCrosswordScoreboard();
        ScoreboardRenderer crosswordRenderer = new ScoreboardRenderer(List.of(miniGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MiniCrosswordResult("raw", "a", null, "0:45", 45, LocalDate.now()));

        Optional<String> rendered = crosswordRenderer.render(miniGame, sb1, "William", null, "Conor",
                Map.of());

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("William");
        assertThat(output).contains("0:45");
        assertThat(output).contains("Conor hasn't submitted");
    }

    @Test
    void streakRowShowsZeroWhenNoStreakData() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));
        Scoreboard conor = sbWith(wordle(WORDLE_4, 4, true));

        Map<String, Map<GameType, Integer>> emptyStreaks = Map.of();
        Optional<String> rendered = renderer.render(wordleGame, william, "William", conor, "Conor", emptyStreaks);

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("0  |  0");
        assertThat(output).doesNotContain("🔥");
    }
}
