package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MidiCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import com.wandocorp.nytscorebot.service.scoreboard.MainCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MidiCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MiniCrosswordScoreboard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrosswordWinStreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);

    private WinStreakService winStreakService;
    private MiniCrosswordScoreboard miniSb;
    private MidiCrosswordScoreboard midiSb;
    private MainCrosswordScoreboard mainSb;
    private CrosswordWinStreakService service;
    private User alice;
    private User bob;
    private Scoreboard sb1;
    private Scoreboard sb2;

    @BeforeEach
    void setUp() {
        winStreakService = mock(WinStreakService.class);
        miniSb = mock(MiniCrosswordScoreboard.class);
        midiSb = mock(MidiCrosswordScoreboard.class);
        mainSb = mock(MainCrosswordScoreboard.class);
        service = new CrosswordWinStreakService(winStreakService, miniSb, midiSb, mainSb);

        alice = new User("c1", "Alice", "u1");
        bob = new User("c2", "Bob", "u2");
        sb1 = new Scoreboard(alice, TODAY);
        sb2 = new Scoreboard(bob, TODAY);
    }

    @Test
    void updateAllInvokesPerGameRouting() {
        sb1.addResult(new MiniCrosswordResult("raw", "u1", null, "0:30", 30, TODAY));
        sb2.addResult(new MiniCrosswordResult("raw", "u2", null, "0:45", 45, TODAY));
        when(miniSb.determineOutcome(any(), any(), any(), any()))
                .thenReturn(new ComparisonOutcome.Win("Alice", "0:15"));

        service.updateAll(sb1, "Alice", sb2, "Bob", TODAY);

        verify(winStreakService).applyOutcome(eq(GameType.MINI_CROSSWORD), eq(alice), eq(false),
                eq(bob), eq(false), any(ComparisonOutcome.Win.class), eq(TODAY));
        // Midi and Main: both missing → WaitingFor outcome still triggers applyOutcome
        verify(winStreakService).applyOutcome(eq(GameType.MIDI_CROSSWORD), any(), anyBoolean(),
                any(), anyBoolean(), any(ComparisonOutcome.WaitingFor.class), eq(TODAY));
        verify(winStreakService).applyOutcome(eq(GameType.MAIN_CROSSWORD), any(), anyBoolean(),
                any(), anyBoolean(), any(ComparisonOutcome.WaitingFor.class), eq(TODAY));
    }

    @Test
    void updateAllNoOpWhenScoreboardMissing() {
        service.updateAll(null, "Alice", sb2, "Bob", TODAY);
        verify(winStreakService, never()).applyOutcome(any(), any(), anyBoolean(), any(), anyBoolean(), any(), any());
    }

    @Test
    void updateGameMainWithDuoFlagPropagated() {
        MainCrosswordResult r1 = new MainCrosswordResult("raw", "u1", null, "15:00", 900, TODAY);
        r1.setDuo(true);
        sb1.addResult(r1);
        sb2.addResult(new MainCrosswordResult("raw", "u2", null, "16:00", 960, TODAY));
        when(mainSb.determineOutcome(any(), any(), any(), any()))
                .thenReturn(new ComparisonOutcome.Win("Alice et al.", "1:00"));

        service.updateGame(GameType.MAIN_CROSSWORD, sb1, "Alice", sb2, "Bob", TODAY);

        ArgumentCaptor<Boolean> duoA = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> duoB = ArgumentCaptor.forClass(Boolean.class);
        verify(winStreakService).applyOutcome(eq(GameType.MAIN_CROSSWORD), eq(alice), duoA.capture(),
                eq(bob), duoB.capture(), any(), eq(TODAY));
        assertThat(duoA.getValue()).isTrue();
        assertThat(duoB.getValue()).isFalse();
    }

    @Test
    void updateGameNonCrosswordIgnored() {
        service.updateGame(GameType.WORDLE, sb1, "Alice", sb2, "Bob", TODAY);
        verify(winStreakService, never()).applyOutcome(any(), any(), anyBoolean(), any(), anyBoolean(), any(), any());
    }

    @Test
    void updateGameMissingResultProducesWaitingFor() {
        sb1.addResult(new MidiCrosswordResult("raw", "u1", null, "3:00", 180, TODAY));
        // sb2 has no midi result
        service.updateGame(GameType.MIDI_CROSSWORD, sb1, "Alice", sb2, "Bob", TODAY);

        ArgumentCaptor<ComparisonOutcome> outcome = ArgumentCaptor.forClass(ComparisonOutcome.class);
        verify(winStreakService).applyOutcome(eq(GameType.MIDI_CROSSWORD), any(), anyBoolean(),
                any(), anyBoolean(), outcome.capture(), eq(TODAY));
        assertThat(outcome.getValue()).isInstanceOf(ComparisonOutcome.WaitingFor.class);
        assertThat(((ComparisonOutcome.WaitingFor) outcome.getValue()).missingPlayerName()).isEqualTo("Bob");
    }

    @Test
    void midiInvokesMidiScoreboard() {
        sb1.addResult(new MidiCrosswordResult("raw", "u1", null, "3:00", 180, TODAY));
        sb2.addResult(new MidiCrosswordResult("raw", "u2", null, "4:00", 240, TODAY));
        when(midiSb.determineOutcome(any(), any(), any(), any()))
                .thenReturn(new ComparisonOutcome.Win("Alice", "1:00"));

        service.updateGame(GameType.MIDI_CROSSWORD, sb1, "Alice", sb2, "Bob", TODAY);
        verify(midiSb, times(1)).determineOutcome(any(), any(), any(), any());
    }
}
