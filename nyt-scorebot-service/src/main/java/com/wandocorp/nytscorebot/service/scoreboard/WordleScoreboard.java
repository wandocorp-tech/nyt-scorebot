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
        WordleResult r1 = s1.getWordleResult();
        WordleResult r2 = s2.getWordleResult();

        if (!r1.isCompleted() && !r2.isCompleted()) {
            return new ComparisonOutcome.Tie();
        }
        if (!r1.isCompleted()) {
            return new ComparisonOutcome.Win(name2, null);
        }
        if (!r2.isCompleted()) {
            return new ComparisonOutcome.Win(name1, null);
        }
        int a1 = r1.getAttempts();
        int a2 = r2.getAttempts();
        if (a1 == a2) return new ComparisonOutcome.Tie();
        if (a1 < a2) return new ComparisonOutcome.Win(name1, a2 - a1);
        return new ComparisonOutcome.Win(name2, a1 - a2);
    }

    @Override
    public int leadingSpaces() { return 4; }

    @Override
    public int baseGap() { return 5; }

    @Override
    public int maxEmojisPerRow() { return 5; }
}
