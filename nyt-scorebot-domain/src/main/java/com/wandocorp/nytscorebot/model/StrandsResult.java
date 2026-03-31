package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;

@Getter
@Embeddable
public class StrandsResult extends GameResult {

    private int puzzleNumber;
    private int hintsUsed;

    protected StrandsResult() {}

    public StrandsResult(String rawContent, String discordAuthor, String comment,
                         int puzzleNumber, int hintsUsed) {
        super(rawContent, discordAuthor, comment);
        this.puzzleNumber = puzzleNumber;
        this.hintsUsed = hintsUsed;
    }

    @Override
    public String toString() {
        return "StrandsResult{puzzle=%d, hintsUsed=%d, comment='%s', author='%s'}"
                .formatted(puzzleNumber, hintsUsed, getComment(), getDiscordAuthor());
    }
}

