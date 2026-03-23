package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(1)
public class WordleParser implements GameParser {

    private static final Pattern PATTERN = Pattern.compile(
            "Wordle ([\\d,]+) ([1-6X])/6(\\*)?",
            Pattern.MULTILINE
    );

    private static final Set<Integer> WORDLE_EMOJI = Set.of(
            0x1F7E8, // 🟨 yellow
            0x1F7E9, // 🟩 green
            0x2B1B,  // ⬛ black large square
            0x2B1C   // ⬜ white large square
    );

    @Override
    public Optional<GameResult> parse(String content, String discordAuthor) {
        Matcher m = PATTERN.matcher(content);
        if (!m.find()) return Optional.empty();

        int puzzleNumber = Integer.parseInt(m.group(1).replace(",", ""));
        String attemptStr = m.group(2);
        boolean completed = !attemptStr.equals("X");
        int attempts = completed ? Integer.parseInt(attemptStr) : 0;
        boolean hardMode = m.group(3) != null;
        String comment = extractComment(content, m);

        return Optional.of(new WordleResult(content, discordAuthor, comment, puzzleNumber, attempts, completed, hardMode));
    }

    private String extractComment(String content, Matcher matcher) {
        boolean pastGrid = false;
        List<String> commentLines = new ArrayList<>();
        for (String line : content.substring(matcher.end()).lines().toList()) {
            if (!pastGrid) {
                String stripped = line.strip();
                if (stripped.isEmpty() || isWordleRow(stripped)) continue;
                pastGrid = true;
            }
            commentLines.add(line);
        }
        String comment = String.join("\n", commentLines).trim();
        return comment.isEmpty() ? null : comment;
    }

    private boolean isWordleRow(String line) {
        int[] codepoints = line.codePoints().toArray();
        if (codepoints.length != 5) return false;
        for (int cp : codepoints) {
            if (!WORDLE_EMOJI.contains(cp)) return false;
        }
        return true;
    }
}
