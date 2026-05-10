package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.entity.PersonalBestSource;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MidiCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.repository.PersonalBestRepository;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.service.PersonalBestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersonalBestBackfillRunnerTest {

    private ScoreboardRepository scoreboardRepository;
    private PersonalBestRepository personalBestRepository;
    private PersonalBestService personalBestService;
    private PersonalBestBackfillRunner runner;
    private User user;

    @BeforeEach
    void setUp() {
        scoreboardRepository = mock(ScoreboardRepository.class);
        personalBestRepository = mock(PersonalBestRepository.class);
        personalBestService = mock(PersonalBestService.class);
        runner = new PersonalBestBackfillRunner(scoreboardRepository, personalBestRepository, personalBestService);
        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
    }

    private Scoreboard sb(LocalDate date) {
        return new Scoreboard(user, date);
    }

    private static MainCrosswordResult main(int seconds) {
        return new MainCrosswordResult("", "", null, "0:00", seconds, LocalDate.of(2026, 1, 1));
    }

    private static MainCrosswordResult assistedMain(int seconds, boolean duo, boolean check, Integer lookups) {
        MainCrosswordResult m = main(seconds);
        m.setDuo(duo);
        m.setCheckUsed(check);
        m.setLookups(lookups);
        return m;
    }

    private static MiniCrosswordResult mini(int seconds) {
        return new MiniCrosswordResult("", "", null, "0:00", seconds, LocalDate.of(2026, 1, 1));
    }

    private static MidiCrosswordResult midi(int seconds) {
        return new MidiCrosswordResult("", "", null, "0:00", seconds, LocalDate.of(2026, 1, 1));
    }

    @Test
    void noOpWhenComputedRowExists() {
        when(personalBestRepository.existsBySource(PersonalBestSource.COMPUTED)).thenReturn(true);
        runner.run(null);
        verify(scoreboardRepository, never()).findAll();
        verifyNoInteractions(personalBestService);
    }

    @Test
    void noOpWhenScoreboardEmpty() {
        when(personalBestRepository.existsBySource(PersonalBestSource.COMPUTED)).thenReturn(false);
        when(scoreboardRepository.findAll()).thenReturn(List.of());
        runner.run(null);
        verifyNoInteractions(personalBestService);
    }

    @Test
    void seedsMinPerScopeAndIgnoresAssistedMain() {
        // Saturday: clean 700, then clean 600 (PB), assisted 500 (excluded)
        Scoreboard a = sb(LocalDate.of(2026, 5, 9)); // Sat
        a.addResult(main(700));

        Scoreboard b = sb(LocalDate.of(2026, 5, 16)); // Sat
        b.addResult(main(600));

        Scoreboard c = sb(LocalDate.of(2026, 5, 23)); // Sat
        c.addResult(assistedMain(500, true, false, null));

        // A Mini on a different day
        Scoreboard d = sb(LocalDate.of(2026, 5, 6));
        d.addResult(mini(60));

        when(personalBestRepository.existsBySource(PersonalBestSource.COMPUTED)).thenReturn(false);
        when(scoreboardRepository.findAll()).thenReturn(List.of(a, b, c, d));

        runner.run(null);

        // 1 Main-Sat seed (600), 1 Mini seed (60), no Tuesday/etc.
        verify(personalBestService).seedFromHistory(eq(user), eq(GameType.MAIN_CROSSWORD),
                eq(Optional.of(DayOfWeek.SATURDAY)), eq(600), eq(LocalDate.of(2026, 5, 16)));
        verify(personalBestService).seedFromHistory(eq(user), eq(GameType.MINI_CROSSWORD),
                eq(Optional.empty()), eq(60), eq(LocalDate.of(2026, 5, 6)));
        verify(personalBestService, times(2)).seedFromHistory(any(), any(), any(), anyInt(), any());
    }

    @Test
    void assistedOnlyHistoryProducesNoSeeds() {
        Scoreboard s = sb(LocalDate.of(2026, 5, 9));
        s.addResult(assistedMain(500, false, true, null));

        when(personalBestRepository.existsBySource(PersonalBestSource.COMPUTED)).thenReturn(false);
        when(scoreboardRepository.findAll()).thenReturn(List.of(s));

        runner.run(null);

        verify(personalBestService, never()).seedFromHistory(any(), any(), any(), anyInt(), any());
    }

    @Test
    void mainPartitionedSeparatelyByDoW() {
        Scoreboard sat = sb(LocalDate.of(2026, 5, 9));
        sat.addResult(main(900));

        Scoreboard tue = sb(LocalDate.of(2026, 5, 5));
        tue.addResult(main(300));

        when(personalBestRepository.existsBySource(PersonalBestSource.COMPUTED)).thenReturn(false);
        when(scoreboardRepository.findAll()).thenReturn(List.of(sat, tue));

        runner.run(null);

        ArgumentCaptor<Optional<DayOfWeek>> dowCap = ArgumentCaptor.forClass(Optional.class);
        ArgumentCaptor<Integer> secCap = ArgumentCaptor.forClass(Integer.class);
        verify(personalBestService, times(2)).seedFromHistory(eq(user), eq(GameType.MAIN_CROSSWORD),
                dowCap.capture(), secCap.capture(), any());
        assertThat(dowCap.getAllValues())
                .containsExactlyInAnyOrder(Optional.of(DayOfWeek.SATURDAY), Optional.of(DayOfWeek.TUESDAY));
        assertThat(secCap.getAllValues()).containsExactlyInAnyOrder(900, 300);
    }

    @Test
    void midiSeededLikeMini() {
        Scoreboard s = sb(LocalDate.of(2026, 5, 9));
        s.addResult(midi(120));

        when(personalBestRepository.existsBySource(PersonalBestSource.COMPUTED)).thenReturn(false);
        when(scoreboardRepository.findAll()).thenReturn(List.of(s));

        runner.run(null);

        verify(personalBestService).seedFromHistory(eq(user), eq(GameType.MIDI_CROSSWORD),
                eq(Optional.empty()), eq(120), eq(LocalDate.of(2026, 5, 9)));
    }
}
