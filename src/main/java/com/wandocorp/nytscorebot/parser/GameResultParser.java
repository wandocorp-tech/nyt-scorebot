package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.GameResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GameResultParser {

    private final List<GameParser> parsers;

    public GameResultParser(List<GameParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * Tries each registered {@link GameParser} in order and returns the first match.
     *
     * @param content       raw message text
     * @param discordAuthor username of the message author
     * @return the parsed result, or empty if no parser recognises the message
     */
    public Optional<GameResult> parse(String content, String discordAuthor) {
        return parsers.stream()
                .map(p -> p.parse(content, discordAuthor))
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(o -> o);
    }
}

