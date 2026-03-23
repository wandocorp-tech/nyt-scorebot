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
}
