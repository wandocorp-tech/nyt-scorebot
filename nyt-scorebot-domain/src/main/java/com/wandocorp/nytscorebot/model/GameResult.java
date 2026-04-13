package com.wandocorp.nytscorebot.model;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.util.OptionalInt;

@Getter
@MappedSuperclass
public abstract class GameResult {

    private String rawContent;
    private String discordAuthor;
    private String comment;

    protected GameResult() {}

    protected GameResult(String rawContent, String discordAuthor, String comment) {
        this.rawContent = rawContent;
        this.discordAuthor = discordAuthor;
        this.comment = comment;
    }

    public abstract GameType gameType();

    public abstract String gameLabel();

    public abstract boolean isSuccess();

    /** Returns the puzzle number if this game type uses numbered puzzles, empty otherwise. */
    public abstract OptionalInt puzzleNumber();
}

