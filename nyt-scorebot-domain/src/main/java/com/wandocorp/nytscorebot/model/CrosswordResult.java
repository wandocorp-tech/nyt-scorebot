package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.time.LocalDate;
import java.util.OptionalInt;

/**
 * Base crossword result for timed crosswords (Mini, Midi).
 * Also used as the storage type for all crossword embeddings.
 * For Main crossword with flag fields, see {@link MainCrosswordResult}.
 */
@Getter
@Embeddable
public class CrosswordResult extends GameResult {

    @Enumerated(EnumType.STRING)
    private CrosswordType type;
    private String timeString;
    private Integer totalSeconds;
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
    public GameType gameType() {
        return switch (type) {
            case MINI -> GameType.MINI_CROSSWORD;
            case MIDI -> GameType.MIDI_CROSSWORD;
            case MAIN -> GameType.MAIN_CROSSWORD;
        };
    }

    @Override
    public String gameLabel() {
        return switch (type) {
            case MINI -> BotText.GAME_LABEL_MINI;
            case MIDI -> BotText.GAME_LABEL_MIDI;
            case MAIN -> BotText.GAME_LABEL_MAIN;
        };
    }

    @Override
    public boolean isSuccess() {
        return true; // Completing a crossword is always a success
    }

    @Override
    public OptionalInt puzzleNumber() {
        return OptionalInt.empty(); // Crosswords don't use puzzle numbers
    }

    @Override
    public LocalDate resultDate() {
        return date;
    }

    @Override
    public String toString() {
        return "CrosswordResult{type=%s, date=%s, time='%s' (%ds), comment='%s', author='%s'}"
                .formatted(type, date, timeString, totalSeconds, getComment(), getDiscordAuthor());
    }
}

