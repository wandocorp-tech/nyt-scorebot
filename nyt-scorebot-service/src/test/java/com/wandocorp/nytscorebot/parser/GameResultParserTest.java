package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GameResultParserTest {

    private static final String CONTENT = "some message";
    private static final String AUTHOR = "Alice";
    private static final GameResult A_RESULT = new WordleResult(CONTENT, AUTHOR, null, 1, 3, true, false);

    @Test
    void returnsEmptyWhenNoParserMatches() {
        GameParser p1 = mock(GameParser.class);
        GameParser p2 = mock(GameParser.class);
        when(p1.parse(CONTENT, AUTHOR)).thenReturn(Optional.empty());
        when(p2.parse(CONTENT, AUTHOR)).thenReturn(Optional.empty());

        GameResultParser parser = new GameResultParser(List.of(p1, p2));
        assertThat(parser.parse(CONTENT, AUTHOR)).isEmpty();
    }

    @Test
    void returnsResultFromFirstMatchingParser() {
        GameParser p1 = mock(GameParser.class);
        GameParser p2 = mock(GameParser.class);
        when(p1.parse(CONTENT, AUTHOR)).thenReturn(Optional.of(A_RESULT));
        when(p2.parse(CONTENT, AUTHOR)).thenReturn(Optional.of(A_RESULT));

        GameResultParser parser = new GameResultParser(List.of(p1, p2));
        assertThat(parser.parse(CONTENT, AUTHOR)).contains(A_RESULT);
        verify(p1).parse(CONTENT, AUTHOR);
        verify(p2, never()).parse(any(), any());
    }

    @Test
    void skipsNonMatchingParsersAndReturnsSecondMatch() {
        GameParser p1 = mock(GameParser.class);
        GameParser p2 = mock(GameParser.class);
        when(p1.parse(CONTENT, AUTHOR)).thenReturn(Optional.empty());
        when(p2.parse(CONTENT, AUTHOR)).thenReturn(Optional.of(A_RESULT));

        GameResultParser parser = new GameResultParser(List.of(p1, p2));
        assertThat(parser.parse(CONTENT, AUTHOR)).contains(A_RESULT);
        verify(p1).parse(CONTENT, AUTHOR);
        verify(p2).parse(CONTENT, AUTHOR);
    }

    @Test
    void returnsEmptyForEmptyParserList() {
        GameResultParser parser = new GameResultParser(List.of());
        assertThat(parser.parse(CONTENT, AUTHOR)).isEmpty();
    }
}
