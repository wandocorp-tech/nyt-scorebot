package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Order(6)
public class MidiCrosswordScoreboard implements GameComparisonScoreboard {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy");

    @Override
    public String gameType() {
        return BotText.GAME_LABEL_MIDI;
    }

    @Override
    public boolean hasResult(Scoreboard scoreboard) {
        return scoreboard != null
                && scoreboard.getMidiCrosswordResult() != null
                && scoreboard.getMidiCrosswordResult().getRawContent() != null;
    }

    @Override
    public String header(Scoreboard scoreboard) {
        CrosswordResult r = scoreboard.getMidiCrosswordResult();
        String dateStr = r.getDate() != null ? r.getDate().format(DATE_FMT) : "?";
        return String.format(BotText.SCOREBOARD_MIDI_HEADER, dateStr);
    }

    @Override
    public String scoreLabel(Scoreboard scoreboard) {
        return scoreboard.getMidiCrosswordResult().getTimeString();
    }

    @Override
    public List<String> emojiGridRows(Scoreboard scoreboard) {
        return List.of();
    }

    @Override
    public ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2) {
        int t1 = s1.getMidiCrosswordResult().getTotalSeconds();
        int t2 = s2.getMidiCrosswordResult().getTotalSeconds();
        if (t1 == t2) return new ComparisonOutcome.Tie();
        if (t1 < t2) return new ComparisonOutcome.Win(name1, t2 - t1);
        return new ComparisonOutcome.Win(name2, t1 - t2);
    }

    @Override public int leadingSpaces() { return 0; }
    @Override public int baseGap() { return 0; }
    @Override public int maxEmojisPerRow() { return 0; }
    @Override public boolean usesScoreLabelRow() { return true; }
}
