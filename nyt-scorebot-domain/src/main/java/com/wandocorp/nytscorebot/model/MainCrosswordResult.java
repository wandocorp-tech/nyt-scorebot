package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.OptionalInt;

/**
 * Main/Daily crossword result. Standalone embeddable (does not extend
 * CrosswordResult) to avoid Hibernate 6 embeddable-inheritance issues
 * that silently drop inherited columns from the schema.
 */
@Getter
@Embeddable
public class MainCrosswordResult extends GameResult {

    @Enumerated(EnumType.STRING)
    private CrosswordType type;
    private String timeString;
    private Integer totalSeconds;
    private LocalDate date;

    @Setter private Boolean duo;
    @Setter private Integer lookups;
    @Setter private Boolean checkUsed;

    protected MainCrosswordResult() {}

    public MainCrosswordResult(String rawContent, String discordAuthor, String comment,
                               String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment);
        this.type = CrosswordType.MAIN;
        this.timeString = timeString;
        this.totalSeconds = totalSeconds;
        this.date = date;
    }

    @Override
    public GameType gameType() {
        return GameType.MAIN_CROSSWORD;
    }

    @Override
    public String gameLabel() {
        return BotText.GAME_LABEL_MAIN;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public OptionalInt puzzleNumber() {
        return OptionalInt.empty();
    }

    @Override
    public LocalDate resultDate() {
        return date;
    }

    @Override
    public String toString() {
        return "MainCrosswordResult{type=%s, date=%s, time='%s' (%ds), duo=%s, lookups=%s, checkUsed=%s, comment='%s', author='%s'}"
                .formatted(type, date, timeString, totalSeconds, duo, lookups, checkUsed, getComment(), getDiscordAuthor());
    }
}
