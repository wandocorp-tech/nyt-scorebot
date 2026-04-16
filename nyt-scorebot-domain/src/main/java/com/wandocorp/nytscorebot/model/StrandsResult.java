package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;

import java.util.OptionalInt;

@Getter
@Entity
@DiscriminatorValue("STRANDS")
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
    public GameType gameType() {
        return GameType.STRANDS;
    }

    @Override
    public String gameLabel() {
        return BotText.GAME_LABEL_STRANDS;
    }

    @Override
    public boolean isSuccess() {
        return true; // Strands is always a success (you always finish)
    }

    @Override
    public OptionalInt puzzleNumber() {
        return OptionalInt.of(puzzleNumber);
    }

    @Override
    public String toString() {
        return "StrandsResult{puzzle=%d, hintsUsed=%d, comment='%s', author='%s'}"
                .formatted(puzzleNumber, hintsUsed, getComment(), getDiscordAuthor());
    }
}

