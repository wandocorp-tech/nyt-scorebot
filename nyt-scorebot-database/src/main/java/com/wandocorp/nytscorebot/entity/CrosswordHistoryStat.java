package com.wandocorp.nytscorebot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-(user, crossword game, weekday) bucket of running aggregates used by the
 * daily scoreboard renderer to display the avg/pb rows.
 *
 * <p>{@code dayOfWeek} is {@code 0} for Mini and Midi (one row per user/game),
 * and {@code 1..7} (Java {@code DayOfWeek.getValue()}) for Main (one row per
 * weekday per user).
 *
 * <p>Maintained incrementally by {@code CrosswordHistoryService}; do not write
 * directly. The unique index on {@code (user_id, game_type, day_of_week)} guards
 * concurrent inserts.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "crossword_history_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_crossword_history_stats_bucket",
                columnNames = {"user_id", "game_type", "day_of_week"}
        )
)
public class CrosswordHistoryStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_type", nullable = false, length = 16)
    private String gameType;

    @Column(name = "day_of_week", nullable = false)
    private byte dayOfWeek;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "sum_seconds", nullable = false)
    private long sumSeconds;

    @Column(name = "pb_seconds")
    private Integer pbSeconds;

    public CrosswordHistoryStat(Long userId, String gameType, byte dayOfWeek,
                                int sampleCount, long sumSeconds, Integer pbSeconds) {
        this.userId = userId;
        this.gameType = gameType;
        this.dayOfWeek = dayOfWeek;
        this.sampleCount = sampleCount;
        this.sumSeconds = sumSeconds;
        this.pbSeconds = pbSeconds;
    }
}
