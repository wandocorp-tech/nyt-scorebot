package com.wandocorp.nytscorebot.parser;

import com.wandocorp.nytscorebot.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for all four game parsers.
 * No Spring context is required — parsers are instantiated directly.
 *
 * Each parser is exercised with three real samples covering a mix of outcomes
 * (different attempt counts, comments, failures, game types, etc.).
 * Assertions cover every parsed field including rawContent and comment extraction.
 */
class ParserTest {

    private static final String AUTHOR = "Test Player";

    // ── Wordle ────────────────────────────────────────────────────────────────

    private final WordleParser wordleParser = new WordleParser();

    /** 5 attempts, no comment */
    private static final String WORDLE_1 =
            "Wordle 1,738 5/6\n\n⬛⬛⬛🟨⬛\n⬛⬛⬛🟨🟨\n🟨⬛🟩⬛⬛\n⬛🟨🟩⬛🟨\n🟩🟩🟩🟩🟩";

    /** 4 attempts with a user comment on the line after the grid */
    private static final String WORDLE_2 =
            "Wordle 1,737 4/6\n\n⬛🟨⬛🟨⬛\n🟨⬛🟨⬛⬛\n🟨⬛⬛🟨🟨\n🟩🟩🟩🟩🟩\nthat was hard";

    /** Failed (X/6) — 6 attempts, none solved */
    private static final String WORDLE_3 =
            "Wordle 1,497 X/6\n\n⬛⬛🟨⬛🟨\n⬛🟨⬛🟨⬛\n⬛🟩⬛🟩🟩\n⬛🟩⬛🟩🟩\n⬛🟩⬛🟩🟩\n🟨🟩⬛🟩🟩";

    @Test
    void wordle5Attempts() {
        Optional<GameResult> result = wordleParser.parse(WORDLE_1, AUTHOR);
        assertThat(result).isPresent();
        WordleResult wr = (WordleResult) result.get();
        assertThat(wr.getPuzzleNumber()).isEqualTo(1738);
        assertThat(wr.getAttempts()).isEqualTo(5);
        assertThat(wr.isCompleted()).isTrue();
        assertThat(wr.isHardMode()).isFalse();
        assertThat(wr.getRawContent()).isEqualTo(WORDLE_1);
        assertThat(wr.getComment()).isNull();
    }

    @Test
    void wordle4AttemptsWithComment() {
        Optional<GameResult> result = wordleParser.parse(WORDLE_2, AUTHOR);
        assertThat(result).isPresent();
        WordleResult wr = (WordleResult) result.get();
        assertThat(wr.getPuzzleNumber()).isEqualTo(1737);
        assertThat(wr.getAttempts()).isEqualTo(4);
        assertThat(wr.isCompleted()).isTrue();
        assertThat(wr.isHardMode()).isFalse();
        assertThat(wr.getRawContent()).isEqualTo(WORDLE_2);
        assertThat(wr.getComment()).isEqualTo("that was hard");
    }

    @Test
    void wordleFailed() {
        Optional<GameResult> result = wordleParser.parse(WORDLE_3, AUTHOR);
        assertThat(result).isPresent();
        WordleResult wr = (WordleResult) result.get();
        assertThat(wr.getPuzzleNumber()).isEqualTo(1497);
        assertThat(wr.getAttempts()).isEqualTo(0); // X = failed, stored as 0
        assertThat(wr.isCompleted()).isFalse();
        assertThat(wr.isHardMode()).isFalse();
        assertThat(wr.getRawContent()).isEqualTo(WORDLE_3);
        assertThat(wr.getComment()).isNull();
    }

    // ── Connections ───────────────────────────────────────────────────────────

    private final ConnectionsParser connectionsParser = new ConnectionsParser();

    /**
     * #961 — 2 groups solved, 4 mistakes (failed game).
     * Row 3 and row 6 are identical ("🟦🟦🟩🟦"), which verifies the lastIndexOf fix
     * that prevents duplicate rows from producing a non-null comment.
     */
    private static final String CONNECTIONS_1 =
            "Connections\nPuzzle #961\n🟨🟨🟨🟨\n🟦🟦🟩🟪\n🟦🟦🟩🟦\n🟪🟪🟪🟪\n🟦🟩🟩🟩\n🟦🟦🟩🟦";

    /** #963 — all 4 groups solved with 1 mistake, with a user comment */
    private static final String CONNECTIONS_2 =
            "Connections\nPuzzle #963\n🟩🟩🟩🟩\n🟨🟨🟨🟨\n🟪🟦🟦🟦\n🟦🟦🟦🟦\n🟪🟪🟪🟪\nnailed it";

    /** #962 — perfect solve, 0 mistakes */
    private static final String CONNECTIONS_3 =
            "Connections\nPuzzle #962\n🟦🟦🟦🟦\n🟨🟨🟨🟨\n🟩🟩🟩🟩\n🟪🟪🟪🟪";

    @Test
    void connectionsNotCompleted() {
        Optional<GameResult> result = connectionsParser.parse(CONNECTIONS_1, AUTHOR);
        assertThat(result).isPresent();
        ConnectionsResult cr = (ConnectionsResult) result.get();
        assertThat(cr.getPuzzleNumber()).isEqualTo(961);
        assertThat(cr.getMistakes()).isEqualTo(4);
        assertThat(cr.isCompleted()).isFalse();
        assertThat(cr.getSolveOrder()).containsExactly("🟨", "🟪");
        assertThat(cr.getRawContent()).isEqualTo(CONNECTIONS_1);
        assertThat(cr.getComment()).isNull();
    }

    @Test
    void connectionsCompletedWith1Mistake() {
        Optional<GameResult> result = connectionsParser.parse(CONNECTIONS_2, AUTHOR);
        assertThat(result).isPresent();
        ConnectionsResult cr = (ConnectionsResult) result.get();
        assertThat(cr.getPuzzleNumber()).isEqualTo(963);
        assertThat(cr.getMistakes()).isEqualTo(1);
        assertThat(cr.isCompleted()).isTrue();
        assertThat(cr.getSolveOrder()).containsExactly("🟩", "🟨", "🟦", "🟪");
        assertThat(cr.getRawContent()).isEqualTo(CONNECTIONS_2);
        assertThat(cr.getComment()).isEqualTo("nailed it");
    }

    @Test
    void connectionsPerfectSolve() {
        Optional<GameResult> result = connectionsParser.parse(CONNECTIONS_3, AUTHOR);
        assertThat(result).isPresent();
        ConnectionsResult cr = (ConnectionsResult) result.get();
        assertThat(cr.getPuzzleNumber()).isEqualTo(962);
        assertThat(cr.getMistakes()).isEqualTo(0);
        assertThat(cr.isCompleted()).isTrue();
        assertThat(cr.getSolveOrder()).containsExactly("🟦", "🟨", "🟩", "🟪");
        assertThat(cr.getRawContent()).isEqualTo(CONNECTIONS_3);
        assertThat(cr.getComment()).isNull();
    }

    // ── Strands ───────────────────────────────────────────────────────────────

    private final StrandsParser strandsParser = new StrandsParser();

    /** #700 — 1 hint used (💡 in grid), no user comment */
    private static final String STRANDS_1 =
            "NYT Strands #700\n\"It's a gift\"\n💡🔵🔵🟡🔵🔵🔵🔵";

    /** #698 — no hints, user comment "that sucked" after the grid */
    private static final String STRANDS_2 =
            "NYT Strands #698\n\"We're not lost ...\"\n🟡🔵🔵🔵🔵🔵🔵\nthat sucked";

    /** #695 — no hints, no comment */
    private static final String STRANDS_3 =
            "NYT Strands #695\n\"Canine classics\"\n🔵🔵🔵🟡🔵🔵🔵🔵🔵";

    @Test
    void strandsWithHint() {
        Optional<GameResult> result = strandsParser.parse(STRANDS_1, AUTHOR);
        assertThat(result).isPresent();
        StrandsResult sr = (StrandsResult) result.get();
        assertThat(sr.getPuzzleNumber()).isEqualTo(700);
        assertThat(sr.getHintsUsed()).isEqualTo(1);
        assertThat(sr.getRawContent()).isEqualTo(STRANDS_1);
        assertThat(sr.getComment()).isNull();
    }

    @Test
    void strandsNoHintWithComment() {
        Optional<GameResult> result = strandsParser.parse(STRANDS_2, AUTHOR);
        assertThat(result).isPresent();
        StrandsResult sr = (StrandsResult) result.get();
        assertThat(sr.getPuzzleNumber()).isEqualTo(698);
        assertThat(sr.getHintsUsed()).isEqualTo(0);
        assertThat(sr.getRawContent()).isEqualTo(STRANDS_2);
        assertThat(sr.getComment()).isEqualTo("that sucked");
    }

    @Test
    void strandsNoHintNoComment() {
        Optional<GameResult> result = strandsParser.parse(STRANDS_3, AUTHOR);
        assertThat(result).isPresent();
        StrandsResult sr = (StrandsResult) result.get();
        assertThat(sr.getPuzzleNumber()).isEqualTo(695);
        assertThat(sr.getHintsUsed()).isEqualTo(0);
        assertThat(sr.getRawContent()).isEqualTo(STRANDS_3);
        assertThat(sr.getComment()).isNull();
    }

    // ── Crossword ─────────────────────────────────────────────────────────────

    private final CrosswordParser crosswordParser = new CrosswordParser();

    /** Daily — explicit calendar date in message text; URL becomes the comment */
    private static final String CROSSWORD_DAILY =
            "I solved the Monday 3/23/2026 New York Times Daily Crossword in 6:25! " +
            "https://www.nytimes.com/crosswords/game/by-id/23781";

    /** Midi — no date extracted; URL becomes the comment */
    private static final String CROSSWORD_MIDI =
            "I solved the 3/23/2026 New York Times Midi Crossword in 2:13! " +
            "https://www.nytimes.com/crosswords/game/midi";

    /** Mini — URL on same line followed by a user comment on the next line */
    private static final String CROSSWORD_MINI =
            "I solved the 3/23/2026 New York Times Mini Crossword in 0:24! " +
            "https://www.nytimes.com/crosswords/game/mini\nthat was a fun one";

    @Test
    void crosswordDaily() {
        Optional<GameResult> result = crosswordParser.parse(CROSSWORD_DAILY, AUTHOR);
        assertThat(result).isPresent();
        CrosswordResult cr = (CrosswordResult) result.get();
        assertThat(cr.getType()).isEqualTo(CrosswordType.DAILY);
        assertThat(cr.getTimeString()).isEqualTo("6:25");
        assertThat(cr.getTotalSeconds()).isEqualTo(6 * 60 + 25);
        assertThat(cr.getDate()).isEqualTo(LocalDate.of(2026, 3, 23));
        assertThat(cr.getRawContent()).isEqualTo(CROSSWORD_DAILY);
        assertThat(cr.getComment()).isNull();
    }

    @Test
    void crosswordMidi() {
        Optional<GameResult> result = crosswordParser.parse(CROSSWORD_MIDI, AUTHOR);
        assertThat(result).isPresent();
        CrosswordResult cr = (CrosswordResult) result.get();
        assertThat(cr.getType()).isEqualTo(CrosswordType.MIDI);
        assertThat(cr.getTimeString()).isEqualTo("2:13");
        assertThat(cr.getTotalSeconds()).isEqualTo(2 * 60 + 13);
        assertThat(cr.getDate()).isNull();
        assertThat(cr.getRawContent()).isEqualTo(CROSSWORD_MIDI);
        assertThat(cr.getComment()).isNull();
    }

    @Test
    void crosswordMini() {
        Optional<GameResult> result = crosswordParser.parse(CROSSWORD_MINI, AUTHOR);
        assertThat(result).isPresent();
        CrosswordResult cr = (CrosswordResult) result.get();
        assertThat(cr.getType()).isEqualTo(CrosswordType.MINI);
        assertThat(cr.getTimeString()).isEqualTo("0:24");
        assertThat(cr.getTotalSeconds()).isEqualTo(24);
        assertThat(cr.getDate()).isNull();
        assertThat(cr.getRawContent()).isEqualTo(CROSSWORD_MINI);
        assertThat(cr.getComment()).isEqualTo("that was a fun one");
    }
}
