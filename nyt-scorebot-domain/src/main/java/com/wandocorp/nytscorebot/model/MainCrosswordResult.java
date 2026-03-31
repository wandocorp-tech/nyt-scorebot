package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

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

    /** Wraps a parsed CrosswordResult (MAIN type) into a MainCrosswordResult with null flags. */
    public static MainCrosswordResult from(CrosswordResult result) {
        return new MainCrosswordResult(
                result.getRawContent(), result.getDiscordAuthor(), result.getComment(),
                result.getTimeString(), result.getTotalSeconds(), result.getDate());
    }

    @Override
    public String toString() {
        return "MainCrosswordResult{date=%s, time='%s' (%ds), duo=%s, lookups=%s, checkUsed=%s, comment='%s', author='%s'}"
                .formatted(getDate(), getTimeString(), getTotalSeconds(), duo, lookups, checkUsed, getComment(), getDiscordAuthor());
    }
}
