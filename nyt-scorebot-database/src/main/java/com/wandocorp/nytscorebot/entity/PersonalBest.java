package com.wandocorp.nytscorebot.entity;

import com.wandocorp.nytscorebot.model.GameType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Per-user personal best for a crossword game type, optionally partitioned by day-of-week.
 *
 * <p>Composite uniqueness on {@code (user_id, game_type, day_of_week)} ensures one row per
 * scope. {@code day_of_week} is non-null and uses the
 * {@link com.wandocorp.nytscorebot.model.PbDayOfWeek#ALL_DAYS_SENTINEL} value for
 * Mini/Midi (which are not partitioned by weekday) — this avoids H2/Postgres treating
 * NULL as distinct in unique constraints.
 *
 * <p>Mutation is intentionally minimal: only {@code bestSeconds}, {@code bestDate},
 * and {@code source} are settable. The scope columns ({@code user}, {@code gameType},
 * {@code dayOfWeek}) are immutable after construction.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "personal_best",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_type", "day_of_week"})
)
public class PersonalBest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    @Column(name = "day_of_week", nullable = false)
    private String dayOfWeek;

    @Setter
    @Column(name = "best_seconds", nullable = false)
    private int bestSeconds;

    @Setter
    @Column(name = "best_date")
    private LocalDate bestDate;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private PersonalBestSource source;

    public PersonalBest(User user, GameType gameType, String dayOfWeek,
                        int bestSeconds, LocalDate bestDate, PersonalBestSource source) {
        this.user = user;
        this.gameType = gameType;
        this.dayOfWeek = dayOfWeek;
        this.bestSeconds = bestSeconds;
        this.bestDate = bestDate;
        this.source = source;
    }
}
