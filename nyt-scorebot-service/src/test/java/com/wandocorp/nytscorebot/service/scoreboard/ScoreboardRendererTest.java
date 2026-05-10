package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MidiCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

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
    void singlePlayerRender_separatorIsHalfWidth() {
        Scoreboard william = sbWith(wordle(WORDLE_4, 4, true));

        Optional<String> rendered = renderer.render(wordleGame, william, "William", null, "Conor", STREAKS);

        assertThat(rendered).isPresent();
        String halfWidthSep = "-".repeat(com.wandocorp.nytscorebot.BotText.SINGLE_PLAYER_LINE_WIDTH);
        String fullWidthSep = "-".repeat(com.wandocorp.nytscorebot.BotText.MAX_LINE_WIDTH);
        assertThat(rendered.get()).contains(halfWidthSep);
        assertThat(rendered.get()).doesNotContain(fullWidthSep);
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
        assertThat(output).contains("Solo");
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

    @Test
    void crosswordScoreboardRendersNukeOnEqualUnaidedTimes() {
        MiniCrosswordScoreboard miniGame = new MiniCrosswordScoreboard();
        ScoreboardRenderer crosswordRenderer = new ScoreboardRenderer(List.of(miniGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MiniCrosswordResult("raw", "a", null, "0:30", 30, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MiniCrosswordResult("raw", "a", null, "0:30", 30, LocalDate.now()));

        Optional<String> rendered = crosswordRenderer.render(miniGame, sb1, "William", sb2, "Conor", Map.of());

        assertThat(rendered).isPresent();
        String output = rendered.get();
        assertThat(output).contains("☢️ Nuke!");
        assertThat(output).doesNotContain("🤝");
        assertThat(output).doesNotContain("🏆");
    }

    // ── Inline PB / Δ avg rows (Group 8.6 / 8.7) ──────────────────────────────

    private static CrosswordPbLookup pbLookup(Map<String, CrosswordPbStats> byName) {
        return (name, gt) -> byName.getOrDefault(name, CrosswordPbStats.EMPTY);
    }

    private static CrosswordPbStats stats(int avg, int pb) {
        return new CrosswordPbStats(OptionalInt.of(avg), OptionalInt.of(pb));
    }

    private static void assertEveryLineWithinMaxWidth(String rendered) {
        for (String line : rendered.split("\n")) {
            // Strip Discord code fences from width consideration.
            if (line.equals("```")) continue;
            assertThat(line.length())
                    .as("line exceeds %s cols: '%s' (len=%d)",
                            com.wandocorp.nytscorebot.BotText.MAX_LINE_WIDTH, line, line.length())
                    .isLessThanOrEqualTo(com.wandocorp.nytscorebot.BotText.MAX_LINE_WIDTH);
        }
    }

    @Test
    void mainTwoPlayer_bothHaveHistory_rendersDeltaAndPb() {
        MainCrosswordScoreboard mainGame = new MainCrosswordScoreboard();
        ScoreboardRenderer r = new ScoreboardRenderer(List.of(mainGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MainCrosswordResult("raw", "a", null, "10:00", 600, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MainCrosswordResult("raw", "a", null, "12:30", 750, LocalDate.now()));

        Map<String, CrosswordPbStats> data = Map.of(
                "William", stats(720, 480),  // avg 12:00, pb 8:00; today 10:00 → -2:00
                "Conor",   stats(900, 600)   // avg 15:00, pb 10:00; today 12:30 → -2:30
        );

        Optional<String> rendered = r.render(mainGame, sb1, "William", sb2, "Conor",
                Map.of(), pbLookup(data));

        assertThat(rendered).isPresent();
        String out = rendered.get();
        assertThat(out).contains(BotText.SCOREBOARD_DELTA_AVG_HEADER);
        assertThat(out).contains("-2:00");
        assertThat(out).contains("-2:30");
        assertThat(out).contains("PB:8:00");
        assertThat(out).contains("PB:10:00");
        assertEveryLineWithinMaxWidth(out);
    }

    @Test
    void mainTwoPlayer_oneSideEmpty_blanksThatColumn() {
        MainCrosswordScoreboard mainGame = new MainCrosswordScoreboard();
        ScoreboardRenderer r = new ScoreboardRenderer(List.of(mainGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MainCrosswordResult("raw", "a", null, "10:00", 600, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MainCrosswordResult("raw", "a", null, "12:30", 750, LocalDate.now()));

        Map<String, CrosswordPbStats> data = Map.of("William", stats(720, 480));

        Optional<String> rendered = r.render(mainGame, sb1, "William", sb2, "Conor",
                Map.of(), pbLookup(data));

        assertThat(rendered).isPresent();
        String out = rendered.get();
        assertThat(out).contains(BotText.SCOREBOARD_DELTA_AVG_HEADER);
        assertThat(out).contains("PB:8:00");
        assertThat(out).doesNotContain("PB:10:00");
        // Conor's delta cell is blank — only William's -2:00 should appear.
        assertThat(out).contains("-2:00");
        assertEveryLineWithinMaxWidth(out);
    }

    @Test
    void mainTwoPlayer_bothEmpty_omitsAllPbRows() {
        MainCrosswordScoreboard mainGame = new MainCrosswordScoreboard();
        ScoreboardRenderer r = new ScoreboardRenderer(List.of(mainGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MainCrosswordResult("raw", "a", null, "10:00", 600, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MainCrosswordResult("raw", "a", null, "12:30", 750, LocalDate.now()));

        Optional<String> rendered = r.render(mainGame, sb1, "William", sb2, "Conor",
                Map.of(), CrosswordPbLookup.EMPTY);

        assertThat(rendered).isPresent();
        String out = rendered.get();
        assertThat(out).doesNotContain(BotText.SCOREBOARD_DELTA_AVG_HEADER);
        assertThat(out).doesNotContain("PB:");
        assertEveryLineWithinMaxWidth(out);
    }

    @Test
    void mainSinglePlayer_withHistory_rendersDeltaAndPb() {
        MainCrosswordScoreboard mainGame = new MainCrosswordScoreboard();
        ScoreboardRenderer r = new ScoreboardRenderer(List.of(mainGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MainCrosswordResult("raw", "a", null, "10:00", 600, LocalDate.now()));

        Map<String, CrosswordPbStats> data = Map.of("William", stats(720, 480));

        Optional<String> rendered = r.render(mainGame, sb1, "William", null, "Conor",
                Map.of(), pbLookup(data));

        assertThat(rendered).isPresent();
        String out = rendered.get();
        assertThat(out).contains(BotText.SCOREBOARD_DELTA_AVG_HEADER);
        assertThat(out).contains("-2:00");
        assertThat(out).contains("PB:8:00");
        assertEveryLineWithinMaxWidth(out);
    }

    @Test
    void miniTwoPlayer_withHistory_rendersDeltaAndPb() {
        MiniCrosswordScoreboard miniGame = new MiniCrosswordScoreboard();
        ScoreboardRenderer r = new ScoreboardRenderer(List.of(miniGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MiniCrosswordResult("raw", "a", null, "0:30", 30, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MiniCrosswordResult("raw", "a", null, "1:00", 60, LocalDate.now()));

        Map<String, CrosswordPbStats> data = Map.of(
                "William", stats(45, 25),
                "Conor",   stats(90, 50)
        );

        Optional<String> rendered = r.render(miniGame, sb1, "William", sb2, "Conor",
                Map.of(), pbLookup(data));

        assertThat(rendered).isPresent();
        String out = rendered.get();
        assertThat(out).contains(BotText.SCOREBOARD_DELTA_AVG_HEADER);
        assertThat(out).contains("-0:15");  // 30-45
        assertThat(out).contains("-0:30");  // 60-90
        assertThat(out).contains("PB:0:25");
        assertThat(out).contains("PB:0:50");
        assertEveryLineWithinMaxWidth(out);
    }

    @Test
    void midiTwoPlayer_withHistory_rendersDeltaAndPb() {
        MidiCrosswordScoreboard midiGame = new MidiCrosswordScoreboard();
        ScoreboardRenderer r = new ScoreboardRenderer(List.of(midiGame));

        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(new MidiCrosswordResult("raw", "a", null, "2:00", 120, LocalDate.now()));
        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MidiCrosswordResult("raw", "a", null, "3:30", 210, LocalDate.now()));

        Map<String, CrosswordPbStats> data = Map.of(
                "William", stats(180, 100),
                "Conor",   stats(240, 180)
        );

        Optional<String> rendered = r.render(midiGame, sb1, "William", sb2, "Conor",
                Map.of(), pbLookup(data));

        assertThat(rendered).isPresent();
        String out = rendered.get();
        assertThat(out).contains(BotText.SCOREBOARD_DELTA_AVG_HEADER);
        assertThat(out).contains("-1:00");
        assertThat(out).contains("-0:30");
        assertThat(out).contains("PB:1:40");
        assertThat(out).contains("PB:3:00");
        assertEveryLineWithinMaxWidth(out);
    }

    @Test
    void mainAssistedToday_stillRendersAgainstAvgAndPb() {
        // An assisted (duo/lookup/check) result today does not change the renderer's
        // behaviour — it still computes Δ vs the lookup's avg. The lookup itself is
        // expected to have already excluded prior assisted results when computing avg/PB.
        MainCrosswordScoreboard mainGame = new MainCrosswordScoreboard();
        ScoreboardRenderer r = new ScoreboardRenderer(List.of(mainGame));

        MainCrosswordResult assisted = new MainCrosswordResult("raw", "a", null, "10:00", 600, LocalDate.now());
        assisted.setDuo(true);
        assisted.setLookups(2);
        Scoreboard sb1 = new Scoreboard(new User("c1", "test", "u1"), LocalDate.now());
        sb1.addResult(assisted);

        Scoreboard sb2 = new Scoreboard(new User("c2", "test", "u2"), LocalDate.now());
        sb2.addResult(new MainCrosswordResult("raw", "a", null, "12:30", 750, LocalDate.now()));

        Map<String, CrosswordPbStats> data = Map.of(
                "William", stats(720, 480),
                "Conor",   stats(900, 600)
        );

        Optional<String> rendered = r.render(mainGame, sb1, "William", sb2, "Conor",
                Map.of(), pbLookup(data));

        assertThat(rendered).isPresent();
        String out = rendered.get();
        assertThat(out).contains("-2:00");
        assertThat(out).contains("PB:8:00");
        assertEveryLineWithinMaxWidth(out);
    }
}
