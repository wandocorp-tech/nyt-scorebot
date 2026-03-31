package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.StrandsResult;
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
        return "STRANDS";
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
        String rawContent = scoreboard.getStrandsResult().getRawContent();
        for (String line : rawContent.split("\n")) {
            if (isStrandsEmojiRow(line)) {
                rows.add(line);
            }
        }
        return rows;
    }

    private boolean isStrandsEmojiRow(String line) {
        if (line.isBlank()) return false;
        int i = 0;
        int count = 0;
        while (i < line.length()) {
            int cp = line.codePointAt(i);
            if (!STRANDS_EMOJI_CODEPOINTS.contains(cp)) return false;
            count++;
            i += Character.charCount(cp);
        }
        return count > 0;
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
}
