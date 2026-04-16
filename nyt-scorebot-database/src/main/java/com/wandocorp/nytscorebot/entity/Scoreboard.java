package com.wandocorp.nytscorebot.entity;

import com.wandocorp.nytscorebot.model.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "scoreboard",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"})
)
public class Scoreboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    @Setter
    @Column(nullable = false)
    private boolean finished = false;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "scoreboard_id")
    private List<GameResult> gameResults = new ArrayList<>();

    public Scoreboard(User user, LocalDate date) {
        this.user = user;
        this.date = date;
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    public void addResult(GameResult result) {
        gameResults.add(result);
    }

    public boolean hasResult(GameType gameType) {
        return findResult(gameType) != null;
    }

    // ── Convenience getters (backward-compatible) ────────────────────────────

    public WordleResult getWordleResult() {
        return (WordleResult) findResult(GameType.WORDLE);
    }

    public ConnectionsResult getConnectionsResult() {
        return (ConnectionsResult) findResult(GameType.CONNECTIONS);
    }

    public StrandsResult getStrandsResult() {
        return (StrandsResult) findResult(GameType.STRANDS);
    }

    public CrosswordResult getMiniCrosswordResult() {
        return (CrosswordResult) findResult(GameType.MINI_CROSSWORD);
    }

    public CrosswordResult getMidiCrosswordResult() {
        return (CrosswordResult) findResult(GameType.MIDI_CROSSWORD);
    }

    public MainCrosswordResult getMainCrosswordResult() {
        return (MainCrosswordResult) findResult(GameType.MAIN_CROSSWORD);
    }

    private GameResult findResult(GameType gameType) {
        return gameResults.stream()
                .filter(r -> r.gameType() == gameType)
                .findFirst()
                .orElse(null);
    }
}
