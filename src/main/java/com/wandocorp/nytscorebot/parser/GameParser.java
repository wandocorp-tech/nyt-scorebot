package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.GameResult;

import java.util.Optional;

public interface GameParser {

    /**
     * Attempts to recognise and parse a Discord message as a specific NYT game result.
     *
     * @param content       raw message text
     * @param discordAuthor username of the message author
     * @return the parsed result, or empty if this parser does not recognise the message
     */
    Optional<GameResult> parse(String content, String discordAuthor);
}
