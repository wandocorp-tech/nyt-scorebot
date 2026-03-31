package com.wandocorp.nytscorebot.model;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

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
}

