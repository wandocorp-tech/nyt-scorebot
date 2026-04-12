package com.wandocorp.nytscorebot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "streak",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_type"})
)
public class Streak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "game_type", nullable = false)
    private String gameType;

    @Setter
    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Setter
    @Column(name = "last_updated_date", nullable = false)
    private LocalDate lastUpdatedDate;

    public Streak(User user, String gameType, int currentStreak, LocalDate lastUpdatedDate) {
        this.user = user;
        this.gameType = gameType;
        this.currentStreak = currentStreak;
        this.lastUpdatedDate = lastUpdatedDate;
    }
}
