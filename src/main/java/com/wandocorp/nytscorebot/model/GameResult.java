package com.wandocorp.nytscorebot.model;

import jakarta.persistence.MappedSuperclass;

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

    public String getRawContent() { return rawContent; }
    public String getDiscordAuthor() { return discordAuthor; }
    public String getComment() { return comment; }
}

