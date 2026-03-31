package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Embeddable
public class CrosswordResult extends GameResult {

    @Enumerated(EnumType.STRING)
    private CrosswordType type;
    private String timeString;
    private Integer totalSeconds;
    private LocalDate date;

    // Main crossword flag fields — null for Mini/Midi
    @Setter private Boolean duo;
    @Setter private Integer lookups;
    @Setter private Boolean checkUsed;

    protected CrosswordResult() {}

    public CrosswordResult(String rawContent, String discordAuthor, String comment,
                           CrosswordType type, String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment);
        this.type = type;
        this.timeString = timeString;
        this.totalSeconds = totalSeconds;
        this.date = date;
    }

    @Override
    public String toString() {
        if (type == CrosswordType.MAIN) {
            return "CrosswordResult{type=%s, date=%s, time='%s' (%ds), duo=%s, lookups=%s, checkUsed=%s, comment='%s', author='%s'}"
                    .formatted(type, date, timeString, totalSeconds, duo, lookups, checkUsed, getComment(), getDiscordAuthor());
        }
        return "CrosswordResult{type=%s, date=%s, time='%s' (%ds), comment='%s', author='%s'}"
                .formatted(type, date, timeString, totalSeconds, getComment(), getDiscordAuthor());
    }
}

