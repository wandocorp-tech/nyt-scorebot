package com.wandocorp.nytscorebot.model;

public class StrandsResult extends GameResult {

    private final int puzzleNumber;
    private final int hintsUsed;

    public StrandsResult(String rawContent, String discordAuthor, String comment,
                         int puzzleNumber, int hintsUsed) {
        super(rawContent, discordAuthor, comment);
        this.puzzleNumber = puzzleNumber;
        this.hintsUsed = hintsUsed;
    }

    public int getPuzzleNumber() { return puzzleNumber; }
    public int getHintsUsed() { return hintsUsed; }

    @Override
    public String toString() {
        return "StrandsResult{puzzle=%d, hintsUsed=%d, comment='%s', author='%s'}"
                .formatted(puzzleNumber, hintsUsed, getComment(), getDiscordAuthor());
    }
}
