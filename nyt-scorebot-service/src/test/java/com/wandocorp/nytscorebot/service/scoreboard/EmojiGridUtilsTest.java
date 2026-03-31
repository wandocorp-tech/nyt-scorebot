package com.wandocorp.nytscorebot.service.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmojiGridUtilsTest {

    private static final Set<Integer> WORDLE = Set.of(0x1F7E8, 0x1F7E9, 0x2B1B, 0x2B1C);
    private static final Set<Integer> STRANDS = Set.of(0x1F535, 0x1F7E1, 0x1F7E0, 0x1F7E2, 0x1F534, 0x1F7E3, 0x1F4A1);

    @Test
    void blankLineReturnsFalse() {
        assertThat(EmojiGridUtils.isEmojiRow("", WORDLE, 5)).isFalse();
        assertThat(EmojiGridUtils.isEmojiRow("   ", WORDLE, 5)).isFalse();
        assertThat(EmojiGridUtils.isEmojiRow(null, WORDLE, 5)).isFalse();
    }

    @Test
    void correctCountReturnsTrue() {
        assertThat(EmojiGridUtils.isEmojiRow("🟩🟩🟩🟩🟩", WORDLE, 5)).isTrue();
    }

    @Test
    void wrongCountReturnsFalse() {
        assertThat(EmojiGridUtils.isEmojiRow("🟩🟩🟩🟩", WORDLE, 5)).isFalse();
    }

    @Test
    void disallowedCodepointReturnsFalse() {
        assertThat(EmojiGridUtils.isEmojiRow("🟩🟩🟩🟩❌", WORDLE, 5)).isFalse();
    }

    @Test
    void variableLengthAcceptsAnyPositiveCount() {
        assertThat(EmojiGridUtils.isEmojiRow("🔵🟡", STRANDS, -1)).isTrue();
        assertThat(EmojiGridUtils.isEmojiRow("🔵🟡🟢🔴🟣💡", STRANDS, -1)).isTrue();
    }

    @Test
    void variableLengthRejectsEmpty() {
        assertThat(EmojiGridUtils.isEmojiRow("", STRANDS, -1)).isFalse();
    }
}
