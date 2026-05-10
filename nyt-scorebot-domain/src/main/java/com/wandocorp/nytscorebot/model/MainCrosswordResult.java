package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Main/Daily crossword result with additional flag fields (duo, lookups, checkUsed).
 * Now properly extends CrosswordResult since the Hibernate 6 embeddable-inheritance
 * issue does not apply to @Entity/@Inheritance(SINGLE_TABLE).
 */
@Getter
@Entity
@DiscriminatorValue("MAIN_CROSSWORD")
public class MainCrosswordResult extends CrosswordResult {

    @Setter private Boolean duo;
    @Setter private Integer lookups;
    @Setter private Boolean checkUsed;

    protected MainCrosswordResult() {}

    public MainCrosswordResult(String rawContent, String discordAuthor, String comment,
                                String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment, timeString, totalSeconds, date);
    }

    @Override
    public GameType gameType() {
        return GameType.MAIN_CROSSWORD;
    }

    /**
     * A result is "assisted" when any of duo, lookups, or check was used.
     * Centralised here so all consumers (stats, personal-best, avg queries) agree.
     */
    public boolean isAssisted() {
        return Boolean.TRUE.equals(duo)
                || Boolean.TRUE.equals(checkUsed)
                || (lookups != null && lookups > 0);
    }

    @Override
    public String gameLabel() {
        return BotText.GAME_LABEL_MAIN;
    }

    @Override
    public String toString() {
        return "MainCrosswordResult{date=%s, time='%s' (%ds), duo=%s, lookups=%s, checkUsed=%s, comment='%s', author='%s'}"
                .formatted(getDate(), getTimeString(), getTotalSeconds(), duo, lookups, checkUsed, getComment(), getDiscordAuthor());
    }
}
