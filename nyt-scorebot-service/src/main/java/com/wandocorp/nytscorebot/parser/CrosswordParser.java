package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.CrosswordType;
import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(3)
public class CrosswordParser implements GameParser {

    private static final Pattern MINI = Pattern.compile(
            "I solved the .+? Mini Crossword in (\\d+:\\d{2})!?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MIDI = Pattern.compile(
            "I solved the .+?[Mm]idi.+? in (\\d+:\\d{2})!?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DAILY = Pattern.compile(
            "I solved the .+? Crossword in (\\d+:\\d{2})!?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NYT_CROSSWORD_URL = Pattern.compile(
            "https://www\\.nytimes\\.com/crosswords/\\S+"
    );

    // Date formats: M/D/YYYY, MM/DD/YYYY, or in URL path /daily/YYYY/M/DD
    private static final Pattern TEXT_DATE = Pattern.compile(
            "(\\d{1,2})/(\\d{1,2})/(\\d{4})"
    );
    private static final Pattern URL_DATE = Pattern.compile(
            "/daily/(\\d{4})/(\\d{1,2})/(\\d{1,2})"
    );

    @Override
    public Optional<GameResult> parse(String content, String discordAuthor) {
        Matcher mini = MINI.matcher(content);
        if (mini.find()) {
            LocalDate date = extractDate(content);
            return Optional.of(build(content, discordAuthor, CrosswordType.MINI, mini.group(1), date, mini));
        }

        Matcher midi = MIDI.matcher(content);
        if (midi.find()) {
            LocalDate date = extractDate(content);
            return Optional.of(build(content, discordAuthor, CrosswordType.MIDI, midi.group(1), date, midi));
        }

        Matcher daily = DAILY.matcher(content);
        if (daily.find()) {
            LocalDate date = extractDate(content);
            return Optional.of(buildMain(content, discordAuthor, daily.group(1), date, daily));
        }

        return Optional.empty();
    }

    private CrosswordResult build(String content, String author, CrosswordType type, String timeString, LocalDate date, Matcher matcher) {
        String comment = extractComment(content, matcher);
        return new CrosswordResult(content, author, comment, type, timeString, parseTimeToSeconds(timeString), date);
    }

    private MainCrosswordResult buildMain(String content, String author, String timeString, LocalDate date, Matcher matcher) {
        String comment = extractComment(content, matcher);
        return new MainCrosswordResult(content, author, comment, timeString, parseTimeToSeconds(timeString), date);
    }

    /** Extract date from text format M/D/YYYY or URL format /daily/YYYY/M/DD. Text format takes priority. */
    private LocalDate extractDate(String content) {
        // Try text format first (M/D/YYYY)
        Matcher textDate = TEXT_DATE.matcher(content);
        if (textDate.find()) {
            int month = Integer.parseInt(textDate.group(1));
            int day = Integer.parseInt(textDate.group(2));
            int year = Integer.parseInt(textDate.group(3));
            return LocalDate.of(year, month, day);
        }

        // Fall back to URL format (/daily/YYYY/M/DD)
        Matcher urlDate = URL_DATE.matcher(content);
        if (urlDate.find()) {
            int year = Integer.parseInt(urlDate.group(1));
            int month = Integer.parseInt(urlDate.group(2));
            int day = Integer.parseInt(urlDate.group(3));
            return LocalDate.of(year, month, day);
        }

        return null;
    }

    private String extractComment(String content, Matcher matcher) {
        int endIdx = matcher.end();
        if (endIdx >= content.length()) return null;
        String remaining = content.substring(endIdx).trim();
        if (remaining.isEmpty()) return null;
        // The NYT share URL is already captured in rawContent; strip it from the comment
        remaining = NYT_CROSSWORD_URL.matcher(remaining).replaceFirst("").trim();
        return remaining.isEmpty() ? null : remaining;
    }

    private int parseTimeToSeconds(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
