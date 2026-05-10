package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.config.StatsProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import com.wandocorp.nytscorebot.service.scoreboard.MainCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MidiCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MiniCrosswordScoreboard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrosswordStatsServiceTest {

    // Monday-through-Sunday covering 4+ weeks — enables DoW block
    private static final LocalDate FROM = LocalDate.of(2025, 1, 6);  // Mon
    private static final LocalDate TO   = LocalDate.of(2025, 2, 2);  // Sun (28 days)

    private StatsProperties statsProperties;
    private ScoreboardRepository scoreboardRepository;
    private UserRepository userRepository;
    private MiniCrosswordScoreboard miniSb;
    private MidiCrosswordScoreboard midiSb;
    private MainCrosswordScoreboard mainSb;
    private CrosswordStatsService service;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        statsProperties       = mock(StatsProperties.class);
        scoreboardRepository  = mock(ScoreboardRepository.class);
        userRepository        = mock(UserRepository.class);
        miniSb                = mock(MiniCrosswordScoreboard.class);
        midiSb                = mock(MidiCrosswordScoreboard.class);
        mainSb                = mock(MainCrosswordScoreboard.class);

        alice = new User("ch1", "Alice", "u1");
        bob   = new User("ch2", "Bob",   "u2");
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(statsProperties.getAnchorDate()).thenReturn(null);

        service = new CrosswordStatsService(statsProperties, scoreboardRepository,
                userRepository, miniSb, midiSb, mainSb);
    }

    @Test
    void cleanWinCreditedToWinner() {
        LocalDate day = LocalDate.of(2025, 1, 6);
        Scoreboard sa = scoreboardWithMini(alice, day, 30);
        Scoreboard sb = scoreboardWithMini(bob,   day, 45);
        when(scoreboardRepository.findAllByDateBetweenWithUser(day, day))
                .thenReturn(List.of(sa, sb));
        when(miniSb.determineOutcome(sa, "Alice", sb, "Bob"))
                .thenReturn(new ComparisonOutcome.Win("Alice", "0:15"));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI, day, day);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MINI_CROSSWORD);
        CrosswordStatsReport.UserGameStats aliceStats = findPlayer(game, "Alice");
        CrosswordStatsReport.UserGameStats bobStats   = findPlayer(game, "Bob");
        assertThat(aliceStats.wins()).isEqualTo(1);
        assertThat(bobStats.wins()).isEqualTo(0);
    }

    @Test
    void forfeitWinWhenOnlyOnePlayerSubmitted() {
        LocalDate day = LocalDate.of(2025, 1, 6);
        Scoreboard sa = scoreboardWithMini(alice, day, 30);
        when(scoreboardRepository.findAllByDateBetweenWithUser(day, day))
                .thenReturn(List.of(sa));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI, day, day);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MINI_CROSSWORD);
        assertThat(findPlayer(game, "Alice").wins()).isEqualTo(1);
        assertThat(findPlayer(game, "Bob").wins()).isEqualTo(0);
    }

    @Test
    void tieYieldsNoWinForEither() {
        LocalDate day = LocalDate.of(2025, 1, 6);
        Scoreboard sa = scoreboardWithMini(alice, day, 30);
        Scoreboard sb = scoreboardWithMini(bob,   day, 30);
        when(scoreboardRepository.findAllByDateBetweenWithUser(day, day))
                .thenReturn(List.of(sa, sb));
        when(miniSb.determineOutcome(sa, "Alice", sb, "Bob"))
                .thenReturn(new ComparisonOutcome.Tie());

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI, day, day);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MINI_CROSSWORD);
        assertThat(findPlayer(game, "Alice").wins()).isEqualTo(0);
        assertThat(findPlayer(game, "Bob").wins()).isEqualTo(0);
    }

    @Test
    void duoWinNotCreditedToWinner() {
        LocalDate day = LocalDate.of(2025, 1, 6);
        Scoreboard sa = scoreboardWithMain(alice, day, 600, true);
        Scoreboard sb = scoreboardWithMain(bob,   day, 900, false);
        when(scoreboardRepository.findAllByDateBetweenWithUser(day, day))
                .thenReturn(List.of(sa, sb));
        when(mainSb.determineOutcome(sa, "Alice", sb, "Bob"))
                .thenReturn(new ComparisonOutcome.Win("Alice et al.", "0:15"));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MAIN, day, day);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MAIN_CROSSWORD);
        // Alice used duo — win NOT credited
        assertThat(findPlayer(game, "Alice").wins()).isEqualTo(0);
        assertThat(findPlayer(game, "Bob").wins()).isEqualTo(0);
    }

    @Test
    void averageTimeComputedCorrectly() {
        LocalDate d1 = LocalDate.of(2025, 1, 6);
        LocalDate d2 = LocalDate.of(2025, 1, 7);
        Scoreboard sa1 = scoreboardWithMini(alice, d1, 60);
        Scoreboard sa2 = scoreboardWithMini(alice, d2, 120);
        when(scoreboardRepository.findAllByDateBetweenWithUser(d1, d2))
                .thenReturn(List.of(sa1, sa2));
        when(miniSb.determineOutcome(any(), any(), any(), any()))
                .thenReturn(new ComparisonOutcome.WaitingFor(""));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI, d1, d2);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MINI_CROSSWORD);
        CrosswordStatsReport.UserGameStats aliceStats = findPlayer(game, "Alice");
        assertThat(aliceStats.avgSeconds().getAsDouble()).isEqualTo(90.0);
        assertThat(aliceStats.gamesPlayed()).isEqualTo(2);
    }

    @Test
    void bestTimeAndDateTracked() {
        LocalDate d1 = LocalDate.of(2025, 1, 6);
        LocalDate d2 = LocalDate.of(2025, 1, 7);
        Scoreboard sa1 = scoreboardWithMini(alice, d1, 90);
        Scoreboard sa2 = scoreboardWithMini(alice, d2, 45);
        when(scoreboardRepository.findAllByDateBetweenWithUser(d1, d2))
                .thenReturn(List.of(sa1, sa2));
        when(miniSb.determineOutcome(any(), any(), any(), any()))
                .thenReturn(new ComparisonOutcome.WaitingFor(""));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI, d1, d2);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MINI_CROSSWORD);
        CrosswordStatsReport.UserGameStats aliceStats = findPlayer(game, "Alice");
        assertThat(aliceStats.bestSeconds().getAsInt()).isEqualTo(45);
        assertThat(aliceStats.bestDate().orElseThrow()).isEqualTo(d2);
    }

    @Test
    void dowBlockPresentWhenWindowIsAtLeast28Days() {
        LocalDate day = LocalDate.of(2025, 1, 6); // Monday
        Scoreboard sa = scoreboardWithMain(alice, day, 300, false);
        when(scoreboardRepository.findAllByDateBetweenWithUser(FROM, TO))
                .thenReturn(List.of(sa));
        when(mainSb.determineOutcome(any(), any(), any(), any()))
                .thenReturn(new ComparisonOutcome.WaitingFor(""));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MAIN, FROM, TO);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MAIN_CROSSWORD);
        assertThat(game.dowBlock()).isPresent();
        CrosswordStatsReport.DowBlock dow = game.dowBlock().get();
        // Monday row should have data for Alice
        CrosswordStatsReport.DowRow monRow = dow.rows().stream()
                .filter(r -> r.dayOfWeek() == DayOfWeek.MONDAY)
                .findFirst().orElseThrow();
        assertThat(monRow.player1Cell()).isPresent();
        assertThat(monRow.player1Cell().get().count()).isEqualTo(1);
    }

    @Test
    void dowBlockAbsentWhenWindowLessThan28Days() {
        LocalDate shortFrom = LocalDate.of(2025, 1, 6);
        LocalDate shortTo   = LocalDate.of(2025, 1, 12); // 7 days
        Scoreboard sa = scoreboardWithMain(alice, shortFrom, 300, false);
        when(scoreboardRepository.findAllByDateBetweenWithUser(shortFrom, shortTo))
                .thenReturn(List.of(sa));
        when(mainSb.determineOutcome(any(), any(), any(), any()))
                .thenReturn(new ComparisonOutcome.WaitingFor(""));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MAIN, shortFrom, shortTo);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MAIN_CROSSWORD);
        assertThat(game.dowBlock()).isEmpty();
    }

    @Test
    void anchorClampsFromDate() {
        LocalDate anchor = LocalDate.of(2025, 1, 10);
        when(statsProperties.getAnchorDate()).thenReturn(anchor);
        when(scoreboardRepository.findAllByDateBetweenWithUser(any(), any())).thenReturn(List.of());

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 20));

        assertThat(report.from()).isEqualTo(anchor);
    }

    @Test
    void throwsWhenEntireWindowBeforeAnchor() {
        LocalDate anchor = LocalDate.of(2025, 2, 1);
        when(statsProperties.getAnchorDate()).thenReturn(anchor);

        assertThatThrownBy(() ->
                service.compute(GameTypeFilter.MINI,
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
                .isInstanceOf(StatsWindowBeforeAnchorException.class)
                .satisfies(e -> assertThat(((StatsWindowBeforeAnchorException) e).getAnchor())
                        .isEqualTo(anchor));
    }

    @Test
    void emptyReportWhenFewerThanTwoUsersRegistered() {
        when(userRepository.findAll()).thenReturn(List.of(alice));
        when(scoreboardRepository.findAllByDateBetweenWithUser(any(), any())).thenReturn(List.of());

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7));

        assertThat(report.games()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Test
    void assistedMainExcludedFromAvgAndBest() {
        LocalDate d1 = LocalDate.of(2025, 1, 6);  // Mon
        LocalDate d2 = LocalDate.of(2025, 1, 7);  // Tue
        LocalDate d3 = LocalDate.of(2025, 1, 8);  // Wed
        // Alice: clean 600, assisted 300 (should be excluded), clean 900 → avg=750, best=600
        Scoreboard sa1 = scoreboardWithMain(alice, d1, 600, false);
        Scoreboard sa2 = scoreboardWithMainAssisted(alice, d2, 300);
        Scoreboard sa3 = scoreboardWithMain(alice, d3, 900, false);
        when(scoreboardRepository.findAllByDateBetweenWithUser(any(), any()))
                .thenReturn(List.of(sa1, sa2, sa3));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MAIN, d1, d3);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MAIN_CROSSWORD);
        CrosswordStatsReport.UserGameStats aliceStats = findPlayer(game, "Alice");
        assertThat(aliceStats.gamesPlayed()).isEqualTo(2);
        assertThat(aliceStats.avgSeconds()).hasValue(750.0);
        assertThat(aliceStats.bestSeconds()).hasValue(600);
        assertThat(aliceStats.excludedAssistedCount()).isEqualTo(1);
    }

    @Test
    void assistedMainStillCountsAsForfeitWin() {
        LocalDate day = LocalDate.of(2025, 1, 6);
        // Only Alice submitted, with an assisted result — forfeit win still applies.
        Scoreboard sa = scoreboardWithMainAssisted(alice, day, 600);
        when(scoreboardRepository.findAllByDateBetweenWithUser(day, day))
                .thenReturn(List.of(sa));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MAIN, day, day);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MAIN_CROSSWORD);
        CrosswordStatsReport.UserGameStats aliceStats = findPlayer(game, "Alice");
        assertThat(aliceStats.wins()).isEqualTo(1);
        assertThat(aliceStats.gamesPlayed()).isEqualTo(0);
        assertThat(aliceStats.excludedAssistedCount()).isEqualTo(1);
    }

    @Test
    void miniUnaffectedByAssistedExclusion() {
        LocalDate day = LocalDate.of(2025, 1, 6);
        Scoreboard sa = scoreboardWithMini(alice, day, 30);
        when(scoreboardRepository.findAllByDateBetweenWithUser(day, day))
                .thenReturn(List.of(sa));

        CrosswordStatsReport report = service.compute(GameTypeFilter.MINI, day, day);

        CrosswordStatsReport.GameStats game = findGame(report, GameType.MINI_CROSSWORD);
        CrosswordStatsReport.UserGameStats aliceStats = findPlayer(game, "Alice");
        assertThat(aliceStats.excludedAssistedCount()).isZero();
        assertThat(aliceStats.gamesPlayed()).isEqualTo(1);
    }

    private static Scoreboard scoreboardWithMini(User user, LocalDate date, int seconds) {
        Scoreboard sb = new Scoreboard(user, date);
        sb.addResult(new MiniCrosswordResult("raw", user.getDiscordUserId(), null,
                formatSeconds(seconds), seconds, date));
        return sb;
    }

    private static Scoreboard scoreboardWithMain(User user, LocalDate date, int seconds, boolean duo) {
        Scoreboard sb = new Scoreboard(user, date);
        MainCrosswordResult result = new MainCrosswordResult("raw", user.getDiscordUserId(), null,
                formatSeconds(seconds), seconds, date);
        result.setDuo(duo);
        sb.addResult(result);
        return sb;
    }

    private static Scoreboard scoreboardWithMainAssisted(User user, LocalDate date, int seconds) {
        Scoreboard sb = new Scoreboard(user, date);
        MainCrosswordResult result = new MainCrosswordResult("raw", user.getDiscordUserId(), null,
                formatSeconds(seconds), seconds, date);
        result.setLookups(2);  // marks isAssisted() == true
        sb.addResult(result);
        return sb;
    }

    private static String formatSeconds(int s) {
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static CrosswordStatsReport.GameStats findGame(CrosswordStatsReport report, GameType gameType) {
        return report.games().stream()
                .filter(g -> g.gameType() == gameType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No game stats for " + gameType));
    }

    private static CrosswordStatsReport.UserGameStats findPlayer(
            CrosswordStatsReport.GameStats game, String name) {
        return game.players().stream()
                .filter(p -> p.playerName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No stats for player " + name));
    }
}
