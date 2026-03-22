package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(1)
public class WordleParser implements GameParser {

    private static final Pattern PATTERN = Pattern.compile(
            "Wordle ([\\d,]+) ([1-6X])/6(\\*)?",
            Pattern.MULTILINE
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
        int endIdx = matcher.end();
        if (endIdx >= content.length()) return null;
        String remaining = content.substring(endIdx).trim();
        return remaining.isEmpty() ? null : remaining;
    }
}
