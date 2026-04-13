package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Main/Daily crossword result with additional flag fields (duo, lookups, checkUsed).
 */
@Getter
@Embeddable
public class MainCrosswordResult extends CrosswordResult {

    @Setter private Boolean duo;
    @Setter private Integer lookups;
    @Setter private Boolean checkUsed;

    protected MainCrosswordResult() {}

    public MainCrosswordResult(String rawContent, String discordAuthor, String comment,
                               String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment, CrosswordType.MAIN, timeString, totalSeconds, date);
    }

    @Override
    public String toString() {
        return "MainCrosswordResult{type=%s, date=%s, time='%s' (%ds), duo=%s, lookups=%s, checkUsed=%s, comment='%s', author='%s'}"
                .formatted(getType(), getDate(), getTimeString(), getTotalSeconds(), duo, lookups, checkUsed, getComment(), getDiscordAuthor());
    }
}
