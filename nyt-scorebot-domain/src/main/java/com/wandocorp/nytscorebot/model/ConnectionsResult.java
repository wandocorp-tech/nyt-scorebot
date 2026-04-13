package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import com.wandocorp.nytscorebot.entity.StringListConverter;
import lombok.Getter;

import java.util.List;
import java.util.OptionalInt;

@Getter
@Embeddable
public class ConnectionsResult extends GameResult {

    private Integer puzzleNumber;
    private Integer mistakes;
    private Boolean completed;

    @Convert(converter = StringListConverter.class)
    private List<String> solveOrder;

    protected ConnectionsResult() {}

    public ConnectionsResult(String rawContent, String discordAuthor, String comment,
                             int puzzleNumber, int mistakes, boolean completed,
                             List<String> solveOrder) {
        super(rawContent, discordAuthor, comment);
        this.puzzleNumber = puzzleNumber;
        this.mistakes = mistakes;
        this.completed = completed;
        this.solveOrder = List.copyOf(solveOrder);
    }

    @Override
    public GameType gameType() {
        return GameType.CONNECTIONS;
    }

    @Override
    public String gameLabel() {
        return BotText.GAME_LABEL_CONNECTIONS;
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
        return "ConnectionsResult{puzzle=%d, mistakes=%d, completed=%b, solveOrder=%s, comment='%s', author='%s'}"
                .formatted(puzzleNumber, mistakes, completed, solveOrder, getComment(), getDiscordAuthor());
    }
}

