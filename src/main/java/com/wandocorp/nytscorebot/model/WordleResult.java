package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class WordleResult extends GameResult {

    private Integer puzzleNumber;
    private Integer attempts;
    private Boolean completed;
    private Boolean hardMode;

    protected WordleResult() {}

    public WordleResult(String rawContent, String discordAuthor, String comment,
                        int puzzleNumber, int attempts, boolean completed, boolean hardMode) {
        super(rawContent, discordAuthor, comment);
        this.puzzleNumber = puzzleNumber;
        this.attempts = attempts;
        this.completed = completed;
        this.hardMode = hardMode;
    }

    public int getPuzzleNumber() { return puzzleNumber; }
    public int getAttempts() { return attempts; }
    public boolean isCompleted() { return completed; }
    public boolean isHardMode() { return hardMode; }

    @Override
    public String toString() {
        return "WordleResult{puzzle=%d, attempts=%s, completed=%b, hardMode=%b, comment='%s', author='%s'}"
                .formatted(puzzleNumber, completed ? attempts : "X", completed, hardMode, getComment(), getDiscordAuthor());
    }
}

