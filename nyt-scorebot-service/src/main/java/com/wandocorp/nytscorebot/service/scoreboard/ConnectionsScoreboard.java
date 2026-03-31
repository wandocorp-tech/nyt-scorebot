package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.ConnectionsResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Order(2)
public class ConnectionsScoreboard implements GameComparisonScoreboard {

    private static final Set<Integer> CONNECTIONS_EMOJI_CODEPOINTS = Set.of(
            0x1F7E8, // 🟨
            0x1F7E9, // 🟩
            0x1F7E6, // 🟦
            0x1F7EA  // 🟪
    );

    @Override
    public String gameType() {
        return BotText.GAME_LABEL_CONNECTIONS;
    }

    @Override
    public boolean hasResult(Scoreboard scoreboard) {
        return scoreboard != null
                && scoreboard.getConnectionsResult() != null
                && scoreboard.getConnectionsResult().getRawContent() != null;
    }

    @Override
    public String header(Scoreboard scoreboard) {
        return "Connections #" + scoreboard.getConnectionsResult().getPuzzleNumber();
    }

    @Override
    public String scoreLabel(Scoreboard scoreboard) {
        ConnectionsResult r = scoreboard.getConnectionsResult();
        return r.isCompleted() ? String.valueOf(r.getMistakes()) : "X";
    }

    @Override
    public List<String> emojiGridRows(Scoreboard scoreboard) {
        List<String> rows = new ArrayList<>();
        String rawContent = scoreboard.getConnectionsResult().getRawContent();
        for (String line : rawContent.split("\n")) {
            if (EmojiGridUtils.isEmojiRow(line, CONNECTIONS_EMOJI_CODEPOINTS, 4)) {
                rows.add(line);
            }
        }
        return rows;
    }

    @Override
    public ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2) {
        ConnectionsResult r1 = s1.getConnectionsResult();
        ConnectionsResult r2 = s2.getConnectionsResult();

        if (!r1.isCompleted() && !r2.isCompleted()) {
            return new ComparisonOutcome.Tie();
        }
        if (!r1.isCompleted()) {
            return new ComparisonOutcome.Win(name2, null);
        }
        if (!r2.isCompleted()) {
            return new ComparisonOutcome.Win(name1, null);
        }
        boolean perfect1 = r1.getMistakes() == 0;
        boolean perfect2 = r2.getMistakes() == 0;
        if (perfect1 && !perfect2) return new ComparisonOutcome.Win(name1, null);
        if (perfect2 && !perfect1) return new ComparisonOutcome.Win(name2, null);
        return new ComparisonOutcome.Tie();
    }

    @Override
    public int leadingSpaces() { return 6; }

    @Override
    public int baseGap() { return 5; }

    @Override
    public int maxEmojisPerRow() { return 4; }
}
