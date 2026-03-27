package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.WordleResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusMessageBuilderTest {

    private static final String NAME_ALICE = "Alice";
    private static final String NAME_BOB = "Bob";
    private static final String CONTEXT = "Alice submitted Wordle";

    // ── build() ────────────────────────────────────────────────────────────────

    @Test
    void buildContainsContextMessage() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        assertThat(table).startsWith(CONTEXT);
    }

    @Test
    void buildHasTwoBlankLinesBetweenContextAndCodeBlock() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        // context line + \n\n\n + ``` → three newlines between context and code block
        assertThat(table).contains(CONTEXT + "\n\n\n```");
    }

    @Test
    void buildContainsPlayerNames() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        assertThat(table).contains(NAME_ALICE);
        assertThat(table).contains(NAME_BOB);
    }

    @Test
    void buildIsWrappedInCodeBlock() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        assertThat(table).contains("```");
    }

    @Test
    void buildContainsFullGameNames() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        assertThat(table).contains(BotText.GAME_LABEL_WORDLE);
        assertThat(table).contains(BotText.GAME_LABEL_CONNECTIONS);
        assertThat(table).contains(BotText.GAME_LABEL_STRANDS);
        assertThat(table).contains(BotText.GAME_LABEL_MINI);
        assertThat(table).contains(BotText.GAME_LABEL_MIDI);
        assertThat(table).contains(BotText.GAME_LABEL_MAIN);
    }

    @Test
    void buildHeaderContainsNoTrafficLightEmoji() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        String headerLine = table.lines()
                .filter(l -> l.contains(NAME_ALICE) || l.contains(NAME_BOB))
                .findFirst().orElse("");
        assertThat(headerLine).doesNotContain(BotText.RED);
        assertThat(headerLine).doesNotContain(BotText.ORANGE);
        assertThat(headerLine).doesNotContain(BotText.GREEN);
        assertThat(headerLine).doesNotContain(BotText.YELLOW);
    }

    @Test
    void buildGamesAreRows() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        // Filter to table rows only (contain " |") to avoid matching the context message
        assertThat(table.lines().filter(l -> l.contains(" |") && l.contains("Wordle")).count()).isEqualTo(1);
        assertThat(table.lines().filter(l -> l.contains(" |") && l.contains("Connections")).count()).isEqualTo(1);
    }

    @Test
    void buildShowsSubmittedEmojiForSubmittedResult() {
        User user = mock(User.class);
        when(user.getName()).thenReturn(NAME_ALICE);

        Scoreboard s = mock(Scoreboard.class);
        when(s.getUser()).thenReturn(user);
        WordleResult wordleResult = new WordleResult("raw", NAME_ALICE, null, 100, 3, true, false);
        when(s.getWordleResult()).thenReturn(wordleResult);
        when(s.getConnectionsResult()).thenReturn(null);
        when(s.getStrandsResult()).thenReturn(null);
        when(s.getMiniCrosswordResult()).thenReturn(null);
        when(s.getMidiCrosswordResult()).thenReturn(null);
        when(s.getDailyCrosswordResult()).thenReturn(null);
        when(s.isFinished()).thenReturn(false);

        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(s), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        assertThat(table).contains(BotText.SUBMITTED);
        assertThat(table).contains(BotText.PENDING);
    }

    @Test
    void buildShowsPendingEmojiForNoSubmission() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        assertThat(table).contains(BotText.PENDING);
    }

    @Test
    void buildContainsDoneFooterRow() {
        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        assertThat(table).contains(BotText.STATUS_FOOTER_DONE_LABEL);
    }

    @Test
    void buildShowsFinishedCheckmarkInFooter() {
        User user = mock(User.class);
        when(user.getName()).thenReturn(NAME_ALICE);

        Scoreboard s = mock(Scoreboard.class);
        when(s.getUser()).thenReturn(user);
        when(s.isFinished()).thenReturn(true);
        when(s.getWordleResult()).thenReturn(null);
        when(s.getConnectionsResult()).thenReturn(null);
        when(s.getStrandsResult()).thenReturn(null);
        when(s.getMiniCrosswordResult()).thenReturn(null);
        when(s.getMidiCrosswordResult()).thenReturn(null);
        when(s.getDailyCrosswordResult()).thenReturn(null);

        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(s), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        String footerLine = table.lines()
                .filter(l -> l.contains(BotText.STATUS_FOOTER_DONE_LABEL))
                .findFirst().orElse("");
        assertThat(footerLine).contains(BotText.CHECK_MARK);
    }

    @Test
    void buildShowsUnfinishedCrossmarkInFooter() {
        User user = mock(User.class);
        when(user.getName()).thenReturn(NAME_ALICE);

        Scoreboard s = mock(Scoreboard.class);
        when(s.getUser()).thenReturn(user);
        when(s.isFinished()).thenReturn(false);
        when(s.getWordleResult()).thenReturn(null);
        when(s.getConnectionsResult()).thenReturn(null);
        when(s.getStrandsResult()).thenReturn(null);
        when(s.getMiniCrosswordResult()).thenReturn(null);
        when(s.getMidiCrosswordResult()).thenReturn(null);
        when(s.getDailyCrosswordResult()).thenReturn(null);

        StatusMessageBuilder builder = new StatusMessageBuilder(List.of(s), List.of(NAME_ALICE, NAME_BOB), CONTEXT);
        String table = builder.build();
        String footerLine = table.lines()
                .filter(l -> l.contains(BotText.STATUS_FOOTER_DONE_LABEL))
                .findFirst().orElse("");
        assertThat(footerLine).contains(BotText.CROSS_MARK);
    }

    // ── renderedLength ────────────────────────────────────────────────────────

    @Test
    void renderedLengthOfPlainTextEqualsStringLength() {
        assertThat(StatusMessageBuilder.renderedLength("Hello")).isEqualTo(5);
        assertThat(StatusMessageBuilder.renderedLength("Connections")).isEqualTo(11);
        assertThat(StatusMessageBuilder.renderedLength("")).isEqualTo(0);
    }

    @Test
    void renderedLengthOfCircleEmojiEqualsStringLength() {
        // Circle emoji are supplementary (U+1F7Ex), Java length 2, also renders 2-wide — no correction
        assertThat(StatusMessageBuilder.renderedLength(BotText.GREEN)).isEqualTo(2);
        assertThat(StatusMessageBuilder.renderedLength(BotText.RED)).isEqualTo(2);
    }

    @Test
    void renderedLengthCountsCheckmarkAsTwo() {
        // ✅ is U+2705, Java length 1, but renders 2-wide in Discord monospace
        assertThat(StatusMessageBuilder.renderedLength(BotText.SUBMITTED)).isEqualTo(2);
    }

    @Test
    void renderedLengthCountsHourglassAsTwo() {
        // ⏳ is U+23F3, Java length 1, renders 2-wide
        assertThat(StatusMessageBuilder.renderedLength(BotText.PENDING)).isEqualTo(2);
    }

    @Test
    void renderedLengthCountsCheckMarkAsTwo() {
        // ✅ is U+2705, Java length 1, renders 2-wide
        assertThat(StatusMessageBuilder.renderedLength(BotText.CHECK_MARK)).isEqualTo(2);
    }

    @Test
    void renderedLengthCountsCrossmarkAsTwo() {
        // ❌ is U+274C, Java length 1, renders 2-wide
        assertThat(StatusMessageBuilder.renderedLength(BotText.CROSS_MARK)).isEqualTo(2);
    }

    @Test
    void renderedLengthOfEmojiPlusTextIsCorrect() {
        // "✅ done" → rendered = 2 + 5 = 7
        assertThat(StatusMessageBuilder.renderedLength(BotText.SUBMITTED + " done")).isEqualTo(7);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Scoreboard makeScoreboard(boolean finished) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(NAME_ALICE);
        Scoreboard s = mock(Scoreboard.class);
        when(s.getUser()).thenReturn(user);
        when(s.isFinished()).thenReturn(finished);
        when(s.getWordleResult()).thenReturn(null);
        when(s.getConnectionsResult()).thenReturn(null);
        when(s.getStrandsResult()).thenReturn(null);
        when(s.getMiniCrosswordResult()).thenReturn(null);
        when(s.getMidiCrosswordResult()).thenReturn(null);
        when(s.getDailyCrosswordResult()).thenReturn(null);
        return s;
    }
}
