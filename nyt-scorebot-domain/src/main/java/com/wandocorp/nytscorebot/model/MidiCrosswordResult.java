package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@DiscriminatorValue("MIDI_CROSSWORD")
public class MidiCrosswordResult extends CrosswordResult {

    protected MidiCrosswordResult() {}

    public MidiCrosswordResult(String rawContent, String discordAuthor, String comment,
                               String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment, timeString, totalSeconds, date);
    }

    @Override
    public GameType gameType() {
        return GameType.MIDI_CROSSWORD;
    }

    @Override
    public String gameLabel() {
        return BotText.GAME_LABEL_MIDI;
    }

    @Override
    public String toString() {
        return "MidiCrosswordResult{date=%s, time='%s' (%ds), comment='%s', author='%s'}"
                .formatted(getDate(), getTimeString(), getTotalSeconds(), getComment(), getDiscordAuthor());
    }
}
