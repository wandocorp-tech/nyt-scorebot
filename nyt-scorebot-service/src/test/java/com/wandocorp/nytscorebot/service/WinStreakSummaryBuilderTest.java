package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WinStreakSummaryBuilderTest {

    private final User alice = new User("c1", "Alice", "u1");
    private final User bob = new User("c2", "Bob", "u2");

    @Test
    void rendersHeaderAndCodeBlock() {
        String out = WinStreakSummaryBuilder.build(alice, "Alice", bob, "Bob",
                Map.of(alice, Map.of(), bob, Map.of()));

        assertThat(out).startsWith(BotText.WIN_STREAK_HEADER);
        assertThat(out).contains(BotText.STATUS_CODE_BLOCK_OPEN);
        assertThat(out).endsWith(BotText.STATUS_CODE_BLOCK_CLOSE);
    }

    @Test
    void includesAllThreeCrosswordRows() {
        String out = WinStreakSummaryBuilder.build(alice, "Alice", bob, "Bob",
                Map.of(alice, Map.of(), bob, Map.of()));
        assertThat(out).contains(GameType.MINI_CROSSWORD.label())
                .contains(GameType.MIDI_CROSSWORD.label())
                .contains(GameType.MAIN_CROSSWORD.label());
    }

    @Test
    void zeroValuesRenderWithoutFire() {
        assertThat(WinStreakSummaryBuilder.renderValue(0)).isEqualTo("0");
        assertThat(WinStreakSummaryBuilder.renderValue(2)).isEqualTo("2");
    }

    @Test
    void streakAtThresholdGetsFireEmoji() {
        String rendered = WinStreakSummaryBuilder.renderValue(BotText.WIN_STREAK_FIRE_THRESHOLD);
        assertThat(rendered).contains(BotText.WIN_STREAK_FIRE_EMOJI);
        assertThat(rendered).startsWith(String.valueOf(BotText.WIN_STREAK_FIRE_THRESHOLD));
    }

    @Test
    void renderedLengthCountsFireAsTwoWide() {
        // "5 🔥" — 1 digit, 1 space, 2-wide emoji = 4
        assertThat(WinStreakSummaryBuilder.renderedLength("5 " + BotText.WIN_STREAK_FIRE_EMOJI)).isEqualTo(4);
    }

    @Test
    void mixedStreaksAppearInOutput() {
        String out = WinStreakSummaryBuilder.build(alice, "Alice", bob, "Bob",
                Map.of(
                        alice, Map.of(GameType.MINI_CROSSWORD, 2, GameType.MAIN_CROSSWORD, BotText.WIN_STREAK_FIRE_THRESHOLD),
                        bob, Map.of(GameType.MIDI_CROSSWORD, 1)));
        assertThat(out).contains("Alice").contains("Bob").contains(BotText.WIN_STREAK_FIRE_EMOJI);
    }

    @Test
    void missingPlayerEntriesTreatedAsZero() {
        // Pass empty map → should not throw and should still render
        String out = WinStreakSummaryBuilder.build(alice, "Alice", bob, "Bob", Map.of());
        assertThat(out).contains("Alice").contains("Bob").contains("0");
    }
}
