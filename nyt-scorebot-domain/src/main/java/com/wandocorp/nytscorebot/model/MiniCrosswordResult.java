package com.wandocorp.nytscorebot.model;

import com.wandocorp.nytscorebot.BotText;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@DiscriminatorValue("MINI_CROSSWORD")
public class MiniCrosswordResult extends CrosswordResult {

    protected MiniCrosswordResult() {}

    public MiniCrosswordResult(String rawContent, String discordAuthor, String comment,
                               String timeString, int totalSeconds, LocalDate date) {
        super(rawContent, discordAuthor, comment, timeString, totalSeconds, date);
    }

    @Override
    public GameType gameType() {
        return GameType.MINI_CROSSWORD;
    }

    @Override
    public String gameLabel() {
        return BotText.GAME_LABEL_MINI;
    }

    @Override
    public String toString() {
        return "MiniCrosswordResult{date=%s, time='%s' (%ds), comment='%s', author='%s'}"
                .formatted(getDate(), getTimeString(), getTotalSeconds(), getComment(), getDiscordAuthor());
    }
}
