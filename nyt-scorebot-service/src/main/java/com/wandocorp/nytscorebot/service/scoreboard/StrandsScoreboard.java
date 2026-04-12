package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Order(3)
public class StrandsScoreboard implements GameComparisonScoreboard {

    private static final Set<Integer> STRANDS_EMOJI_CODEPOINTS = Set.of(
            0x1F535, // 🔵
            0x1F7E1, // 🟡
            0x1F7E0, // 🟠
            0x1F7E2, // 🟢
            0x1F534, // 🔴
            0x1F7E3, // 🟣
            0x1F4A1  // 💡
    );

    @Override
    public String gameType() {
        return BotText.GAME_LABEL_STRANDS;
    }

    @Override
    public boolean hasResult(Scoreboard scoreboard) {
        return scoreboard != null
                && scoreboard.getStrandsResult() != null
                && scoreboard.getStrandsResult().getRawContent() != null;
    }

    @Override
    public String header(Scoreboard scoreboard) {
        return "Strands #" + scoreboard.getStrandsResult().getPuzzleNumber();
    }
    @Override
    public String scoreLabel(Scoreboard scoreboard) {
        return String.valueOf(scoreboard.getStrandsResult().getHintsUsed());
    }

    @Override
    public List<String> emojiGridRows(Scoreboard scoreboard) {
        List<String> rows = new ArrayList<>();
        StringBuilder emojiBuffer = new StringBuilder();
        String rawContent = scoreboard.getStrandsResult().getRawContent();
        
        // Collect all emojis from all lines
        for (String line : rawContent.split("\n")) {
            if (EmojiGridUtils.isEmojiRow(line, STRANDS_EMOJI_CODEPOINTS, -1)) {
                emojiBuffer.append(line);
            }
        }
        
        // Group emojis into rows of 4 each
        int emojisPerRow = maxEmojisPerRow();
        int codePointIndex = 0;
        
        while (codePointIndex < emojiBuffer.length()) {
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < emojisPerRow && codePointIndex < emojiBuffer.length(); j++) {
                int cp = emojiBuffer.codePointAt(codePointIndex);
                row.appendCodePoint(cp);
                codePointIndex += Character.charCount(cp);
            }
            if (!row.isEmpty()) {
                rows.add(row.toString());
            }
        }
        
        return rows;
    }

    @Override
    public ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2) {
        int h1 = s1.getStrandsResult().getHintsUsed();
        int h2 = s2.getStrandsResult().getHintsUsed();

        if (h1 < h2) return new ComparisonOutcome.Win(name1, h2 - h1);
        if (h2 < h1) return new ComparisonOutcome.Win(name2, h1 - h2);
        return new ComparisonOutcome.Tie();
    }

    @Override
    public int leadingSpaces() { return 6; }

    @Override
    public int baseGap() { return 5; }

    @Override
    public int maxEmojisPerRow() { return 4; }

    @Override
    public boolean usesStreakDisplay() { return true; }
}
