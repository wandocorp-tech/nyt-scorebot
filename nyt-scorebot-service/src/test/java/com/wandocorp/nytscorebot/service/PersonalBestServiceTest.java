package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.PersonalBest;
import com.wandocorp.nytscorebot.entity.PersonalBestSource;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.PbDayOfWeek;
import com.wandocorp.nytscorebot.repository.PersonalBestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PersonalBestServiceTest {

    private static final LocalDate SAT = LocalDate.of(2026, 5, 9); // Saturday
    private static final LocalDate TUE = LocalDate.of(2026, 5, 5); // Tuesday

    private PersonalBestRepository repo;
    private PersonalBestService service;
    private User user;

    @BeforeEach
    void setUp() {
        repo = mock(PersonalBestRepository.class);
        service = new PersonalBestService(repo);
        user = mock(User.class);
    }

    private PersonalBest existing(GameType gt, String dow, int sec, PersonalBestSource src) {
        return new PersonalBest(user, gt, dow, sec, LocalDate.of(2025, 1, 1), src);
    }

    @Test
    void recompute_skipsWhenNotClean() {
        PbUpdateOutcome out = service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 600, false);
        assertThat(out).isSameAs(PbUpdateOutcome.NO_CHANGE);
        verifyNoInteractions(repo);
    }

    @Test
    void recompute_firstEverInsertReturnsNewPbWithNullPrior() {
        when(repo.findByUserAndGameTypeAndDayOfWeek(any(), any(), any())).thenReturn(Optional.empty());

        PbUpdateOutcome out = service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 600, true);

        assertThat(out).isInstanceOf(PbUpdateOutcome.NewPb.class);
        PbUpdateOutcome.NewPb npb = (PbUpdateOutcome.NewPb) out;
        assertThat(npb.priorSeconds()).isNull();
        assertThat(npb.newSeconds()).isEqualTo(600);
        assertThat(npb.gameType()).isEqualTo(GameType.MAIN_CROSSWORD);
        assertThat(npb.dayOfWeek()).contains(DayOfWeek.SATURDAY);

        ArgumentCaptor<PersonalBest> cap = ArgumentCaptor.forClass(PersonalBest.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getSource()).isEqualTo(PersonalBestSource.COMPUTED);
        assertThat(cap.getValue().getDayOfWeek()).isEqualTo("SATURDAY");
    }

    @Test
    void recompute_strictlyFasterReplacesAndReturnsNewPbWithPrior() {
        PersonalBest pb = existing(GameType.MAIN_CROSSWORD, "SATURDAY", 700, PersonalBestSource.COMPUTED);
        when(repo.findByUserAndGameTypeAndDayOfWeek(user, GameType.MAIN_CROSSWORD, "SATURDAY"))
                .thenReturn(Optional.of(pb));

        PbUpdateOutcome out = service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 600, true);

        PbUpdateOutcome.NewPb npb = (PbUpdateOutcome.NewPb) out;
        assertThat(npb.priorSeconds()).isEqualTo(700);
        assertThat(npb.newSeconds()).isEqualTo(600);
        assertThat(pb.getBestSeconds()).isEqualTo(600);
        assertThat(pb.getBestDate()).isEqualTo(SAT);
        verify(repo).save(pb);
    }

    @Test
    void recompute_equalTimeNoUpdate() {
        PersonalBest pb = existing(GameType.MAIN_CROSSWORD, "SATURDAY", 600, PersonalBestSource.COMPUTED);
        when(repo.findByUserAndGameTypeAndDayOfWeek(any(), any(), any())).thenReturn(Optional.of(pb));

        PbUpdateOutcome out = service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 600, true);

        assertThat(out).isSameAs(PbUpdateOutcome.NO_CHANGE);
        verify(repo, never()).save(any());
    }

    @Test
    void recompute_slowerNoUpdate() {
        PersonalBest pb = existing(GameType.MAIN_CROSSWORD, "SATURDAY", 500, PersonalBestSource.COMPUTED);
        when(repo.findByUserAndGameTypeAndDayOfWeek(any(), any(), any())).thenReturn(Optional.of(pb));

        PbUpdateOutcome out = service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 700, true);

        assertThat(out).isSameAs(PbUpdateOutcome.NO_CHANGE);
        verify(repo, never()).save(any());
    }

    @Test
    void recompute_manualPreservedAgainstSlowerOrEqualClean() {
        PersonalBest pb = existing(GameType.MAIN_CROSSWORD, "SATURDAY", 500, PersonalBestSource.MANUAL);
        when(repo.findByUserAndGameTypeAndDayOfWeek(any(), any(), any())).thenReturn(Optional.of(pb));

        PbUpdateOutcome out = service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 500, true);

        assertThat(out).isSameAs(PbUpdateOutcome.NO_CHANGE);
        assertThat(pb.getSource()).isEqualTo(PersonalBestSource.MANUAL);
        verify(repo, never()).save(any());
    }

    @Test
    void recompute_manualReplacedByFasterCleanTransitionsToComputed() {
        PersonalBest pb = existing(GameType.MAIN_CROSSWORD, "SATURDAY", 500, PersonalBestSource.MANUAL);
        when(repo.findByUserAndGameTypeAndDayOfWeek(any(), any(), any())).thenReturn(Optional.of(pb));

        PbUpdateOutcome out = service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 400, true);

        PbUpdateOutcome.NewPb npb = (PbUpdateOutcome.NewPb) out;
        assertThat(npb.priorSeconds()).isEqualTo(500);
        assertThat(pb.getBestSeconds()).isEqualTo(400);
        assertThat(pb.getSource()).isEqualTo(PersonalBestSource.COMPUTED);
        verify(repo).save(pb);
    }

    @Test
    void recompute_mainPartitionsByDayOfWeek() {
        // Saturday update should look up the SATURDAY row, not TUESDAY
        when(repo.findByUserAndGameTypeAndDayOfWeek(user, GameType.MAIN_CROSSWORD, "SATURDAY"))
                .thenReturn(Optional.empty());

        service.recompute(user, GameType.MAIN_CROSSWORD, SAT, 600, true);

        verify(repo).findByUserAndGameTypeAndDayOfWeek(user, GameType.MAIN_CROSSWORD, "SATURDAY");
        verify(repo, never()).findByUserAndGameTypeAndDayOfWeek(user, GameType.MAIN_CROSSWORD, "TUESDAY");
    }

    @Test
    void recompute_miniUsesAllDaysSentinel() {
        when(repo.findByUserAndGameTypeAndDayOfWeek(user, GameType.MINI_CROSSWORD, PbDayOfWeek.ALL_DAYS_SENTINEL))
                .thenReturn(Optional.empty());

        PbUpdateOutcome out = service.recompute(user, GameType.MINI_CROSSWORD, TUE, 60, true);

        PbUpdateOutcome.NewPb npb = (PbUpdateOutcome.NewPb) out;
        assertThat(npb.dayOfWeek()).isEmpty();
        ArgumentCaptor<PersonalBest> cap = ArgumentCaptor.forClass(PersonalBest.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getDayOfWeek()).isEqualTo(PbDayOfWeek.ALL_DAYS_SENTINEL);
    }

    @Test
    void seedFromHistory_insertsWhenMissingAndReturnsNoChange() {
        when(repo.findByUserAndGameTypeAndDayOfWeek(any(), any(), any())).thenReturn(Optional.empty());

        PbUpdateOutcome out = service.seedFromHistory(user, GameType.MAIN_CROSSWORD,
                Optional.of(DayOfWeek.SATURDAY), 600, SAT);

        assertThat(out).isSameAs(PbUpdateOutcome.NO_CHANGE);
        verify(repo).save(any());
    }

    @Test
    void seedFromHistory_noOpWhenRowExists() {
        PersonalBest pb = existing(GameType.MAIN_CROSSWORD, "SATURDAY", 500, PersonalBestSource.MANUAL);
        when(repo.findByUserAndGameTypeAndDayOfWeek(any(), any(), any())).thenReturn(Optional.of(pb));

        PbUpdateOutcome out = service.seedFromHistory(user, GameType.MAIN_CROSSWORD,
                Optional.of(DayOfWeek.SATURDAY), 400, SAT);

        assertThat(out).isSameAs(PbUpdateOutcome.NO_CHANGE);
        verify(repo, never()).save(any());
    }

    @Test
    void seedFromHistory_rejectsScopeMismatch() {
        assertThatThrownBy(() -> service.seedFromHistory(user, GameType.MINI_CROSSWORD,
                Optional.of(DayOfWeek.SATURDAY), 60, TUE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.seedFromHistory(user, GameType.MAIN_CROSSWORD,
                Optional.empty(), 600, SAT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
