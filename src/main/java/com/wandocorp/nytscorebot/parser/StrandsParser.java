package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.StrandsResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(4)
public class StrandsParser implements GameParser {

    private static final Pattern HEADER = Pattern.compile("NYT Strands #(\\d+)");
    private static final String HINT_BULB = "💡";

    // Emoji used in the Strands result grid: coloured circles + hint bulb
    private static final Set<Integer> STRANDS_EMOJI = Set.of(
            0x1F535, // 🔵 blue circle
            0x1F7E1, // 🟡 yellow circle
            0x1F7E0, // 🟠 orange circle
            0x1F7E2, // 🟢 green circle
            0x1F534, // 🔴 red circle
            0x1F7E3, // 🟣 purple circle
            0x1F4A1  // 💡 light bulb
    );

    @Override
    public Optional<GameResult> parse(String content, String discordAuthor) {
        Matcher m = HEADER.matcher(content);
        if (!m.find()) return Optional.empty();

        int puzzleNumber = Integer.parseInt(m.group(1));
        int hintsUsed = countOccurrences(content, HINT_BULB);
        String comment = extractComment(content, m);

        return Optional.of(new StrandsResult(content, discordAuthor, comment, puzzleNumber, hintsUsed));
    }

    private String extractComment(String content, Matcher matcher) {
        // After the header: skip the theme line (enclosed in quotes) and all emoji grid rows.
        // The first non-empty, non-grid line after at least one grid row has been seen is the
        // start of any user-added comment.
        boolean seenGridRow = false;
        boolean pastGrid = false;
        List<String> commentLines = new ArrayList<>();

        for (String line : content.substring(matcher.end()).lines().toList()) {
            if (pastGrid) {
                commentLines.add(line);
                continue;
            }
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("\"") || isStrandsRow(stripped)) {
                if (isStrandsRow(stripped)) seenGridRow = true;
            } else if (seenGridRow) {
                pastGrid = true;
                commentLines.add(line);
            }
            // else: preamble before grid starts — skip
        }

        String comment = String.join("\n", commentLines).trim();
        return comment.isEmpty() ? null : comment;
    }

    private boolean isStrandsRow(String line) {
        int[] codepoints = line.codePoints().toArray();
        if (codepoints.length == 0) return false;
        for (int cp : codepoints) {
            if (!STRANDS_EMOJI.contains(cp)) return false;
        }
        return true;
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
