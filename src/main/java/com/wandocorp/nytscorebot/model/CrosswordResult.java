package com.wandocorp.nytscorebot.model;

import java.time.LocalDate;

public class CrosswordResult extends GameResult {

    private final CrosswordType type;
    private final String timeString;
    private final int totalSeconds;
    private final LocalDate date;

    public CrosswordResult(String rawContent, String discordAuthor, String comment,
                           CrosswordType type, String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment);
        this.type = type;
        this.timeString = timeString;
        this.totalSeconds = totalSeconds;
        this.date = date;
    }

    public CrosswordType getType() { return type; }
    public String getTimeString() { return timeString; }
    public int getTotalSeconds() { return totalSeconds; }
    public LocalDate getDate() { return date; }

    @Override
    public String toString() {
        return "CrosswordResult{type=%s, date=%s, time='%s' (%ds), comment='%s', author='%s'}"
                .formatted(type, date, timeString, totalSeconds, getComment(), getDiscordAuthor());
    }
}
