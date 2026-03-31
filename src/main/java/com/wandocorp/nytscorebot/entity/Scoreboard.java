package com.wandocorp.nytscorebot.entity;

import com.wandocorp.nytscorebot.model.*;
import jakarta.persistence.*;

import java.time.LocalDate;

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

    @Column(nullable = false)
    private boolean finished = false;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rawContent",    column = @Column(name = "wordle_raw_content")),
        @AttributeOverride(name = "discordAuthor", column = @Column(name = "wordle_discord_author")),
        @AttributeOverride(name = "comment",       column = @Column(name = "wordle_comment")),
        @AttributeOverride(name = "puzzleNumber",  column = @Column(name = "wordle_puzzle_number")),
        @AttributeOverride(name = "attempts",      column = @Column(name = "wordle_attempts")),
        @AttributeOverride(name = "completed",     column = @Column(name = "wordle_completed")),
        @AttributeOverride(name = "hardMode",      column = @Column(name = "wordle_hard_mode"))
    })
    private WordleResult wordleResult;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rawContent",    column = @Column(name = "connections_raw_content")),
        @AttributeOverride(name = "discordAuthor", column = @Column(name = "connections_discord_author")),
        @AttributeOverride(name = "comment",       column = @Column(name = "connections_comment")),
        @AttributeOverride(name = "puzzleNumber",  column = @Column(name = "connections_puzzle_number")),
        @AttributeOverride(name = "mistakes",      column = @Column(name = "connections_mistakes")),
        @AttributeOverride(name = "completed",     column = @Column(name = "connections_completed")),
        @AttributeOverride(name = "solveOrder",    column = @Column(name = "connections_solve_order"))
    })
    private ConnectionsResult connectionsResult;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rawContent",       column = @Column(name = "strands_raw_content")),
        @AttributeOverride(name = "discordAuthor",    column = @Column(name = "strands_discord_author")),
        @AttributeOverride(name = "comment",          column = @Column(name = "strands_comment")),
        @AttributeOverride(name = "puzzleNumber",     column = @Column(name = "strands_puzzle_number")),
        @AttributeOverride(name = "hintsUsed",        column = @Column(name = "strands_hints_used"))
    })
    private StrandsResult strandsResult;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rawContent",    column = @Column(name = "crossword_mini_raw_content")),
        @AttributeOverride(name = "discordAuthor", column = @Column(name = "crossword_mini_discord_author")),
        @AttributeOverride(name = "comment",       column = @Column(name = "crossword_mini_comment")),
        @AttributeOverride(name = "type",          column = @Column(name = "crossword_mini_type")),
        @AttributeOverride(name = "timeString",    column = @Column(name = "crossword_mini_time_string")),
        @AttributeOverride(name = "totalSeconds",  column = @Column(name = "crossword_mini_total_seconds")),
        @AttributeOverride(name = "crosswordDate", column = @Column(name = "crossword_mini_date"))
    })
    private CrosswordResult miniCrosswordResult;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rawContent",    column = @Column(name = "crossword_midi_raw_content")),
        @AttributeOverride(name = "discordAuthor", column = @Column(name = "crossword_midi_discord_author")),
        @AttributeOverride(name = "comment",       column = @Column(name = "crossword_midi_comment")),
        @AttributeOverride(name = "type",          column = @Column(name = "crossword_midi_type")),
        @AttributeOverride(name = "timeString",    column = @Column(name = "crossword_midi_time_string")),
        @AttributeOverride(name = "totalSeconds",  column = @Column(name = "crossword_midi_total_seconds")),
        @AttributeOverride(name = "crosswordDate", column = @Column(name = "crossword_midi_date"))
    })
    private CrosswordResult midiCrosswordResult;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "rawContent",    column = @Column(name = "crossword_daily_raw_content")),
        @AttributeOverride(name = "discordAuthor", column = @Column(name = "crossword_daily_discord_author")),
        @AttributeOverride(name = "comment",       column = @Column(name = "crossword_daily_comment")),
        @AttributeOverride(name = "type",          column = @Column(name = "crossword_daily_type")),
        @AttributeOverride(name = "timeString",    column = @Column(name = "crossword_daily_time_string")),
        @AttributeOverride(name = "totalSeconds",  column = @Column(name = "crossword_daily_total_seconds")),
        @AttributeOverride(name = "crosswordDate", column = @Column(name = "crossword_daily_date"))
    })
    private CrosswordResult dailyCrosswordResult;

    protected Scoreboard() {}

    public Scoreboard(User user, LocalDate date) {
        this.user = user;
        this.date = date;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public LocalDate getDate() { return date; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public WordleResult getWordleResult() { return wordleResult; }
    public void setWordleResult(WordleResult wordleResult) { this.wordleResult = wordleResult; }

    public ConnectionsResult getConnectionsResult() { return connectionsResult; }
    public void setConnectionsResult(ConnectionsResult connectionsResult) { this.connectionsResult = connectionsResult; }

    public StrandsResult getStrandsResult() { return strandsResult; }
    public void setStrandsResult(StrandsResult strandsResult) { this.strandsResult = strandsResult; }

    public CrosswordResult getMiniCrosswordResult() { return miniCrosswordResult; }
    public void setMiniCrosswordResult(CrosswordResult miniCrosswordResult) { this.miniCrosswordResult = miniCrosswordResult; }

    public CrosswordResult getMidiCrosswordResult() { return midiCrosswordResult; }
    public void setMidiCrosswordResult(CrosswordResult midiCrosswordResult) { this.midiCrosswordResult = midiCrosswordResult; }

    public CrosswordResult getDailyCrosswordResult() { return dailyCrosswordResult; }
    public void setDailyCrosswordResult(CrosswordResult dailyCrosswordResult) { this.dailyCrosswordResult = dailyCrosswordResult; }
}
