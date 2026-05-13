package com.wandocorp.nytscorebot.service.history;

import com.wandocorp.nytscorebot.entity.CrosswordHistoryStat;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.repository.CrosswordHistoryStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrosswordHistoryServiceTest {

    private CrosswordHistoryStatRepository repo;
    private CrosswordHistoryService service;
    private User user;

    @BeforeEach
    void setUp() {
        repo = mock(CrosswordHistoryStatRepository.class);
        service = new CrosswordHistoryService(repo);
        user = mock(User.class);
        when(user.getId()).thenReturn(42L);
    }

    @Test
    void getStatsReturnsEmptyWhenNoRow() {
        when(repo.findByUserIdAndGameTypeAndDayOfWeek(42L, "MINI", (byte) 0)).thenReturn(Optional.empty());

        CrosswordHistoryStats stats = service.getStats(user, CrosswordGame.MINI, Optional.empty());

        assertThat(stats).isEqualTo(CrosswordHistoryStats.EMPTY);
    }

    @Test
    void getStatsComputesRoundedAverageAndPb() {
        CrosswordHistoryStat row = new CrosswordHistoryStat(42L, "MIDI", (byte) 0, 3, 100L, 25);
        when(repo.findByUserIdAndGameTypeAndDayOfWeek(42L, "MIDI", (byte) 0)).thenReturn(Optional.of(row));

        CrosswordHistoryStats stats = service.getStats(user, CrosswordGame.MIDI, Optional.empty());

        // 100/3 = 33.33 → rounded to 33
        assertThat(stats.avgSeconds()).isEqualTo(OptionalInt.of(33));
        assertThat(stats.pbSeconds()).isEqualTo(OptionalInt.of(25));
    }

    @Test
    void getStatsLeavesAvgEmptyWhenSampleCountZero() {
        CrosswordHistoryStat row = new CrosswordHistoryStat(42L, "MAIN", (byte) 4, 0, 0L, 600);
        when(repo.findByUserIdAndGameTypeAndDayOfWeek(42L, "MAIN", (byte) 4)).thenReturn(Optional.of(row));

        CrosswordHistoryStats stats = service.getStats(user, CrosswordGame.MAIN, Optional.of(DayOfWeek.THURSDAY));

        assertThat(stats.avgSeconds()).isEmpty();
        assertThat(stats.pbSeconds()).isEqualTo(OptionalInt.of(600));
    }

    @Test
    void getStatsLeavesPbEmptyWhenColumnNull() {
        CrosswordHistoryStat row = new CrosswordHistoryStat(42L, "MINI", (byte) 0, 2, 60L, null);
        when(repo.findByUserIdAndGameTypeAndDayOfWeek(42L, "MINI", (byte) 0)).thenReturn(Optional.of(row));

        CrosswordHistoryStats stats = service.getStats(user, CrosswordGame.MINI, Optional.empty());

        assertThat(stats.pbSeconds()).isEmpty();
        assertThat(stats.avgSeconds()).isEqualTo(OptionalInt.of(30));
    }

    @Test
    void getStatsRejectsMissingWeekdayForMain() {
        assertThatThrownBy(() -> service.getStats(user, CrosswordGame.MAIN, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getStatsRejectsWeekdayForMini() {
        assertThatThrownBy(() -> service.getStats(user, CrosswordGame.MINI, Optional.of(DayOfWeek.MONDAY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordSubmissionCallsUpsertWithSentinelForMidi() {
        service.recordSubmission(user, CrosswordGame.MIDI, LocalDate.of(2026, 5, 10), 90,
                false, 0, false);

        verify(repo).upsert(eq(42L), eq("MIDI"), eq((byte) 0), eq(90));
    }

    @Test
    void recordSubmissionCallsUpsertWithJavaDayValueForMain() {
        // 2026-05-10 is a Sunday → DayOfWeek.SUNDAY.getValue() == 7
        service.recordSubmission(user, CrosswordGame.MAIN, LocalDate.of(2026, 5, 10), 1200,
                false, 0, false);

        ArgumentCaptor<Byte> dow = ArgumentCaptor.forClass(Byte.class);
        verify(repo).upsert(eq(42L), eq("MAIN"), dow.capture(), eq(1200));
        assertThat(dow.getValue()).isEqualTo((byte) 7);
    }

    @Test
    void recordSubmissionSkipsAssistedMain() {
        service.recordSubmission(user, CrosswordGame.MAIN, LocalDate.of(2026, 5, 10), 1200,
                true, 0, false);
        service.recordSubmission(user, CrosswordGame.MAIN, LocalDate.of(2026, 5, 10), 1200,
                false, 1, false);
        service.recordSubmission(user, CrosswordGame.MAIN, LocalDate.of(2026, 5, 10), 1200,
                false, 0, true);

        verify(repo, never()).upsert(eq(42L), eq("MAIN"), org.mockito.ArgumentMatchers.anyByte(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void recordSubmissionStillRecordsAssistedMiniMidi() {
        // assistance flags only matter for MAIN; MINI/MIDI always record
        service.recordSubmission(user, CrosswordGame.MINI, LocalDate.now(), 30,
                true, 5, true);

        verify(repo).upsert(eq(42L), eq("MINI"), eq((byte) 0), eq(30));
    }

    @Test
    void crosswordGameValuesEnumeratesAllVariants() {
        assertThat(CrosswordGame.values()).containsExactly(
                CrosswordGame.MINI, CrosswordGame.MIDI, CrosswordGame.MAIN);
        assertThat(CrosswordGame.valueOf("MAIN")).isEqualTo(CrosswordGame.MAIN);
    }
}
