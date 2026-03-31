package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import com.wandocorp.nytscorebot.entity.StringListConverter;
import lombok.Getter;

import java.util.List;

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
    public String toString() {
        return "ConnectionsResult{puzzle=%d, mistakes=%d, completed=%b, solveOrder=%s, comment='%s', author='%s'}"
                .formatted(puzzleNumber, mistakes, completed, solveOrder, getComment(), getDiscordAuthor());
    }
}

