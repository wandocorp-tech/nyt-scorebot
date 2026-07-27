package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TripleCrownServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 17);
    private static final String ALICE = "Alice";
    private static final String BOB = "Bob";

    private TripleCrownService service;
    private Scoreboard sb1;
    private Scoreboard sb2;

    @BeforeEach
    void setUp() {
        service = new TripleCrownService();
        sb1 = new Scoreboard(new User("c1", ALICE, "u1"), TODAY);
        sb2 = new Scoreboard(new User("c2", BOB, "u2"), TODAY);
    }

    private static Map<GameType, ComparisonOutcome> outcomes(ComparisonOutcome mini,
                                                             ComparisonOutcome midi,
                                                             ComparisonOutcome main) {
        Map<GameType, ComparisonOutcome> map = new EnumMap<>(GameType.class);
        map.put(GameType.MINI_CROSSWORD, mini);
        map.put(GameType.MIDI_CROSSWORD, midi);
        map.put(GameType.MAIN_CROSSWORD, main);
        return map;
    }

    private static ComparisonOutcome win(String name) {
        return new ComparisonOutcome.Win(name, "+0:10");
    }

    private static ComparisonOutcome forfeitWin(String name) {
        return new ComparisonOutcome.Win(name, null);
    }

    private Optional<String> detect(Map<GameType, ComparisonOutcome> outcomes) {
        return service.detect(outcomes, sb1, ALICE, sb2, BOB);
    }

    private void giveAliceMainResult(Boolean duo) {
        MainCrosswordResult main = new MainCrosswordResult("raw", "u1", null, "5:00", 300, TODAY);
        main.setDuo(duo);
        sb1.addResult(main);
    }

    private void giveBobMainResult(Boolean duo) {
        MainCrosswordResult main = new MainCrosswordResult("raw", "u2", null, "6:00", 360, TODAY);
        main.setDuo(duo);
        sb2.addResult(main);
    }

    // ── Sweep detection ──────────────────────────────────────────────────────

    @Test
    void allThreeWonBySamePlayerAwardsCrown() {
        assertThat(detect(outcomes(win(ALICE), win(ALICE), win(ALICE)))).contains(ALICE);
    }

    @Test
    void winsSplitBetweenPlayersAwardsNoCrown() {
        assertThat(detect(outcomes(win(ALICE), win(ALICE), win(BOB)))).isEmpty();
    }

    @Test
    void tieOnAnyGameBlocksCrown() {
        assertThat(detect(outcomes(win(ALICE), win(ALICE), new ComparisonOutcome.Tie()))).isEmpty();
    }

    @Test
    void nukeOnAnyGameBlocksCrown() {
        assertThat(detect(outcomes(win(ALICE), new ComparisonOutcome.Nuke(), win(ALICE)))).isEmpty();
    }

    @Test
    void waitingForOnAnyGameBlocksCrown() {
        assertThat(detect(outcomes(win(ALICE), win(ALICE), new ComparisonOutcome.WaitingFor(BOB))))
                .isEmpty();
    }

    @Test
    void emptyOutcomesAwardsNoCrown() {
        assertThat(detect(Map.of())).isEmpty();
        assertThat(service.detect(null, sb1, ALICE, sb2, BOB)).isEmpty();
    }

    @Test
    void missingGameFromOutcomeMapBlocksCrown() {
        Map<GameType, ComparisonOutcome> partial = new EnumMap<>(GameType.class);
        partial.put(GameType.MINI_CROSSWORD, win(ALICE));
        partial.put(GameType.MIDI_CROSSWORD, win(ALICE));
        assertThat(detect(partial)).isEmpty();
    }

    @Test
    void winnerNotMatchingEitherConfiguredPlayerAwardsNoCrown() {
        assertThat(detect(outcomes(win("Ghost"), win("Ghost"), win("Ghost")))).isEmpty();
    }

    // ── Forfeit wins count ───────────────────────────────────────────────────

    @Test
    void allThreeWonByForfeitAwardsCrown() {
        assertThat(detect(outcomes(forfeitWin(ALICE), forfeitWin(ALICE), forfeitWin(ALICE))))
                .contains(ALICE);
    }

    @Test
    void mixedDifferentialAndForfeitWinsAwardsCrown() {
        assertThat(detect(outcomes(win(ALICE), forfeitWin(ALICE), win(ALICE)))).contains(ALICE);
    }

    // ── Duo rules ────────────────────────────────────────────────────────────

    @Test
    void winnerDuoOnMainBlocksCrown() {
        giveAliceMainResult(true);
        assertThat(detect(outcomes(win(ALICE), win(ALICE), win(ALICE)))).isEmpty();
    }

    @Test
    void loserDuoOnMainDoesNotBlockCrown() {
        giveAliceMainResult(false);
        giveBobMainResult(true);
        assertThat(detect(outcomes(win(ALICE), win(ALICE), win(ALICE)))).contains(ALICE);
    }

    @Test
    void nullDuoFlagDoesNotBlockCrown() {
        giveAliceMainResult(null);
        assertThat(detect(outcomes(win(ALICE), win(ALICE), win(ALICE)))).contains(ALICE);
    }

    @Test
    void absentMainResultDoesNotBlockCrown() {
        assertThat(detect(outcomes(win(ALICE), win(ALICE), win(ALICE)))).contains(ALICE);
    }

    @Test
    void secondPlayerCanAlsoWinTheCrown() {
        giveBobMainResult(false);
        assertThat(detect(outcomes(win(BOB), win(BOB), win(BOB)))).contains(BOB);
    }

    @Test
    void secondPlayerDuoOnMainBlocksTheirOwnCrown() {
        giveBobMainResult(true);
        assertThat(detect(outcomes(win(BOB), win(BOB), win(BOB)))).isEmpty();
    }
}
