package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDate;

@Embeddable
public class CrosswordResult extends GameResult {

    @Enumerated(EnumType.STRING)
    private CrosswordType type;
    private String timeString;
    private Integer totalSeconds;
    private LocalDate crosswordDate;

    protected CrosswordResult() {}

    public CrosswordResult(String rawContent, String discordAuthor, String comment,
                           CrosswordType type, String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment);
        this.type = type;
        this.timeString = timeString;
        this.totalSeconds = totalSeconds;
        this.crosswordDate = date;
    }

    public CrosswordType getType() { return type; }
    public String getTimeString() { return timeString; }
    public int getTotalSeconds() { return totalSeconds; }    public LocalDate getDate() { return crosswordDate; }

    @Override
    public String toString() {
        return "CrosswordResult{type=%s, date=%s, time='%s' (%ds), comment='%s', author='%s'}"
                .formatted(type, crosswordDate, timeString, totalSeconds, getComment(), getDiscordAuthor());
    }
}

