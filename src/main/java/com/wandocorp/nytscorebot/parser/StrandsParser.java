package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.StrandsResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(4)
public class StrandsParser implements GameParser {

    private static final Pattern HEADER = Pattern.compile("NYT Strands #(\\d+)");
    private static final String HINT_BULB = "💡";

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
        int endIdx = matcher.end();
        if (endIdx >= content.length()) return null;
        String remaining = content.substring(endIdx).trim();
        return remaining.isEmpty() ? null : remaining;
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
