package com.wandocorp.nytscorebot.entity;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StringListConverterTest {

    private final StringListConverter converter = new StringListConverter();

    // ── convertToDatabaseColumn ───────────────────────────────────────────────

    @Test
    void nullListConvertsToEmptyString() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("");
    }

    @Test
    void emptyListConvertsToEmptyString() {
        assertThat(converter.convertToDatabaseColumn(Collections.emptyList())).isEqualTo("");
    }

    @Test
    void singleItemConvertsToPlainString() {
        assertThat(converter.convertToDatabaseColumn(List.of("🟩"))).isEqualTo("🟩");
    }

    @Test
    void multipleItemsJoinedWithComma() {
        assertThat(converter.convertToDatabaseColumn(List.of("🟩", "🟨", "🟦", "🟪")))
                .isEqualTo("🟩,🟨,🟦,🟪");
    }

    // ── convertToEntityAttribute ──────────────────────────────────────────────

    @Test
    void nullStringConvertsToEmptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void blankStringConvertsToEmptyList() {
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void emptyStringConvertsToEmptyList() {
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    void commaSeparatedStringConvertsToList() {
        assertThat(converter.convertToEntityAttribute("🟩,🟨,🟦,🟪"))
                .containsExactly("🟩", "🟨", "🟦", "🟪");
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    void roundTripPreservesOrder() {
        List<String> original = List.of("🟩", "🟨", "🟦", "🟪");
        String db = converter.convertToDatabaseColumn(original);
        assertThat(converter.convertToEntityAttribute(db)).isEqualTo(original);
    }

    // ── delimiter collision ──────────────────────────────────────────────────

    @Test
    void itemContainingDelimiterCorruptsRoundTrip() {
        // Documents the known limitation: items containing commas will be split
        // incorrectly on read-back, producing more elements than the original list.
        List<String> original = List.of("a,b", "c");
        String db = converter.convertToDatabaseColumn(original);
        assertThat(db).isEqualTo("a,b,c");
        List<String> restored = converter.convertToEntityAttribute(db);
        assertThat(restored).as("delimiter collision produces extra elements")
                .hasSize(3)
                .containsExactly("a", "b", "c");
    }

    @Test
    void emojiItemsNeverCollideWithCommaDelimiter() {
        // In practice, stored values are emoji codepoints (🟩🟨🟦🟪) which never
        // contain commas, so the delimiter choice is safe for this domain.
        List<String> emojis = List.of("🟩🟩🟩🟩", "🟪🟪🟪🟪", "🟨🟨🟨🟨", "🟦🟦🟦🟦");
        String db = converter.convertToDatabaseColumn(emojis);
        assertThat(converter.convertToEntityAttribute(db)).isEqualTo(emojis);
    }
}
