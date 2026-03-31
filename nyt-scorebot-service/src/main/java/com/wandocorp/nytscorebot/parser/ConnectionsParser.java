package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.ConnectionsResult;
import com.wandocorp.nytscorebot.model.GameResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(2)
public class ConnectionsParser implements GameParser {

    private static final Pattern HEADER = Pattern.compile(
            "Connections\\s*\\nPuzzle #(\\d+)",
            Pattern.MULTILINE
    );

    // Codepoints for the four Connections square emojis
    private static final Set<Integer> CONNECTIONS_EMOJI = Set.of(
            0x1F7E8, // 🟨 yellow
            0x1F7E9, // 🟩 green
            0x1F7E6, // 🟦 blue
            0x1F7EA  // 🟪 purple
    );

    @Override
    public Optional<GameResult> parse(String content, String discordAuthor) {
        Matcher header = HEADER.matcher(content);
        if (!header.find()) return Optional.empty();

        int puzzleNumber = Integer.parseInt(header.group(1));

        List<String> solveOrder = new ArrayList<>();
        int totalRows = 0;
        int lastRowEnd = 0;

        for (String line : content.lines().toList()) {
            int[] codepoints = line.strip().codePoints().toArray();
            if (!isConnectionsRow(codepoints)) continue;

            totalRows++;
            lastRowEnd = Math.max(lastRowEnd, content.lastIndexOf(line) + line.length());
            if (isPureRow(codepoints)) {
                solveOrder.add(new String(codepoints, 0, 1));
            }
        }

        int mistakes = totalRows - solveOrder.size();
        boolean completed = solveOrder.size() == 4;
        String comment = extractComment(content, lastRowEnd);

        return Optional.of(new ConnectionsResult(content, discordAuthor, comment, puzzleNumber, mistakes, completed, solveOrder));
    }

    /** Returns true if the codepoint array represents exactly 4 Connections emoji (any combination). */
    private boolean isConnectionsRow(int[] codepoints) {
        if (codepoints.length != 4) return false;
        for (int cp : codepoints) {
            if (!CONNECTIONS_EMOJI.contains(cp)) return false;
        }
        return true;
    }

    /** Returns true if all 4 codepoints are the same (correctly solved group). */
    private boolean isPureRow(int[] codepoints) {
        return codepoints[0] == codepoints[1]
            && codepoints[1] == codepoints[2]
            && codepoints[2] == codepoints[3];
    }

    private String extractComment(String content, int endIdx) {
        if (endIdx >= content.length()) return null;
        String remaining = content.substring(endIdx).trim();
        return remaining.isEmpty() ? null : remaining;
    }
}