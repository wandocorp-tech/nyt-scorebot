package com.wandocorp.nytscorebot.model;

public abstract class GameResult {

    private final String rawContent;
    private final String discordAuthor;
    private final String comment;

    protected GameResult(String rawContent, String discordAuthor, String comment) {
        this.rawContent = rawContent;
        this.discordAuthor = discordAuthor;
        this.comment = comment;
    }

    public String getRawContent() {
        return rawContent;
    }

    public String getDiscordAuthor() {
        return discordAuthor;
    }

    public String getComment() {
        return comment;
    }
}
