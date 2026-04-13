package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.OptionalInt;

@Getter
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

    @Override
    public GameType gameType() {
        return GameType.WORDLE;
    }

    @Override
    public String gameLabel() {
        return BotText.GAME_LABEL_WORDLE;
    }

    @Override
    public boolean isSuccess() {
        return Boolean.TRUE.equals(completed);
    }

    @Override
    public OptionalInt puzzleNumber() {
        return puzzleNumber != null ? OptionalInt.of(puzzleNumber) : OptionalInt.empty();
    }

    @Override
    public String toString() {
        return "WordleResult{puzzle=%d, attempts=%s, completed=%b, hardMode=%b, comment='%s', author='%s'}"
                .formatted(puzzleNumber, completed ? attempts : "X", completed, hardMode, getComment(), getDiscordAuthor());
    }
}

