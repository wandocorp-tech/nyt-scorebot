package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class StrandsResult extends GameResult {

    private Integer puzzleNumber;
    private Integer hintsUsed;
    private Integer spangramPosition;

    protected StrandsResult() {}

    public StrandsResult(String rawContent, String discordAuthor, String comment,
                         int puzzleNumber, int hintsUsed, int spangramPosition) {
        super(rawContent, discordAuthor, comment);
        this.puzzleNumber = puzzleNumber;
        this.hintsUsed = hintsUsed;
        this.spangramPosition = spangramPosition;
    }

    public int getPuzzleNumber() { return puzzleNumber; }
    public int getHintsUsed() { return hintsUsed; }
    public int getSpangramPosition() { return spangramPosition; }

    @Override
    public String toString() {
        return "StrandsResult{puzzle=%d, hintsUsed=%d, spangramPosition=%d, comment='%s', author='%s'}"
                .formatted(puzzleNumber, hintsUsed, spangramPosition, getComment(), getDiscordAuthor());
    }
}

