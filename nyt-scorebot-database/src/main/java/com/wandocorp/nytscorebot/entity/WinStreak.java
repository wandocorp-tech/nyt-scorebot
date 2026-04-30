package com.wandocorp.nytscorebot.entity;

import com.wandocorp.nytscorebot.model.GameType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Head-to-head win streak for a single player on a single crossword game type
 * (Mini, Midi, or Main). Distinct from {@link Streak}, which tracks per-player
 * completion of the emoji-based games.
 *
 * <p>Updates use a snapshot pattern so that mid-day flag changes (e.g. {@code /duo})
 * can recompute the day's outcome without losing the previous day's anchor:
 * the first update of a given day copies {@code currentStreak → baseStreak} and
 * {@code computedDate → baseDate} before applying the new outcome. Same-day
 * re-runs reuse the snapshot and recompute against the frozen base.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "win_streak",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_type"})
)
public class WinStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    @Setter
    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Setter
    @Column(name = "base_streak", nullable = false)
    private int baseStreak;

    @Setter
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Setter
    @Column(name = "computed_date", nullable = false)
    private LocalDate computedDate;

    @Setter
    @Column(name = "last_updated_date", nullable = false)
    private LocalDate lastUpdatedDate;

    public WinStreak(User user, GameType gameType, LocalDate today) {
        this.user = user;
        this.gameType = gameType;
        this.currentStreak = 0;
        this.baseStreak = 0;
        this.baseDate = today.minusDays(1);
        this.computedDate = today.minusDays(1);
        this.lastUpdatedDate = today;
    }
}
