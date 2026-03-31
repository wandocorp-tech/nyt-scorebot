package com.wandocorp.nytscorebot.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Embeddable
public class CrosswordResult extends GameResult {

    @Enumerated(EnumType.STRING)
    private CrosswordType type;
    private String timeString;
    private int totalSeconds;
    private LocalDate date;

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
        return "CrosswordResult{type=%s, date=%s, time='%s' (%ds), comment='%s', author='%s'}"
                .formatted(type, date, timeString, totalSeconds, getComment(), getDiscordAuthor());
    }
}

