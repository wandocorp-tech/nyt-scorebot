package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Order(1)
public class WordleScoreboard implements GameComparisonScoreboard {

    private static final Set<Integer> WORDLE_EMOJI_CODEPOINTS = Set.of(
            0x1F7E8, // 🟨
            0x1F7E9, // 🟩
            0x2B1B,  // ⬛
            0x2B1C   // ⬜
    );

    @Override
    public String gameType() {
        return BotText.GAME_LABEL_WORDLE;
    }

    @Override
    public boolean hasResult(Scoreboard scoreboard) {
        return scoreboard != null
                && scoreboard.getWordleResult() != null
                && scoreboard.getWordleResult().getRawContent() != null;
    }

    @Override
    public String header(Scoreboard scoreboard) {
        return "Wordle #" + scoreboard.getWordleResult().getPuzzleNumber();
    }

    @Override
    public String scoreLabel(Scoreboard scoreboard) {
        WordleResult r = scoreboard.getWordleResult();
        return r.isCompleted() ? String.valueOf(r.getAttempts()) : "X";
    }

    @Override
    public List<String> emojiGridRows(Scoreboard scoreboard) {
        List<String> rows = new ArrayList<>();
        String rawContent = scoreboard.getWordleResult().getRawContent();
        for (String line : rawContent.split("\n")) {
            if (EmojiGridUtils.isEmojiRow(line, WORDLE_EMOJI_CODEPOINTS, 5)) {
                rows.add(line);
            }
        }
        return rows;
    }

    @Override
    public ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2) {
        // Outcome is never rendered when usesStreakDisplay() == true, so return a dummy Tie.
        return new ComparisonOutcome.Tie();
    }

    @Override
    public int leadingSpaces() { return 4; }

    @Override
    public int baseGap() { return 5; }

    @Override
    public int maxEmojisPerRow() { return 5; }

    @Override
    public boolean usesStreakDisplay() { return true; }
}
