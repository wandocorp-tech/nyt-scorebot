package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;

import java.time.LocalDate;
import java.util.OptionalInt;

/**
 * Abstract base for all timed crossword results (Mini, Midi, Main).
 * Concrete subclasses provide the game type via discriminator.
 */
@Getter
@Entity
public abstract class CrosswordResult extends GameResult {

    private String timeString;
    private Integer totalSeconds;

    @Column(name = "crossword_date")
    private LocalDate date;

    protected CrosswordResult() {}

    protected CrosswordResult(String rawContent, String discordAuthor, String comment,
                              String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment);
        this.timeString = timeString;
        this.totalSeconds = totalSeconds;
        this.date = date;
    }

    @Override
    public boolean isSuccess() {
        return true; // Completing a crossword is always a success
    }

    @Override
    public OptionalInt puzzleNumber() {
        return OptionalInt.empty(); // Crosswords don't use puzzle numbers
    }

    @Override
    public LocalDate resultDate() {
        return date;
    }
}
