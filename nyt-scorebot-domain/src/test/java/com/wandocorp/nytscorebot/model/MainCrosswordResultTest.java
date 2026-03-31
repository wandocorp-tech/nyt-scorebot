package com.wandocorp.nytscorebot.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MainCrosswordResultTest {

    @Test
    void flagFieldsDefaultToNull() {
        MainCrosswordResult result = new MainCrosswordResult("raw", "author", null,
                "15:00", 900, LocalDate.of(2026, 3, 31));
        assertThat(result.getDuo()).isNull();
        assertThat(result.getLookups()).isNull();
        assertThat(result.getCheckUsed()).isNull();
    }

    @Test
    void flagFieldsAreSettable() {
        MainCrosswordResult result = new MainCrosswordResult("raw", "author", null,
                "15:00", 900, LocalDate.of(2026, 3, 31));
        result.setDuo(true);
        result.setLookups(3);
        result.setCheckUsed(true);
        assertThat(result.getDuo()).isTrue();
        assertThat(result.getLookups()).isEqualTo(3);
        assertThat(result.getCheckUsed()).isTrue();
    }

    @Test
    void fromWrapsExistingCrosswordResult() {
        CrosswordResult source = new CrosswordResult("raw", "author", "comment",
                CrosswordType.MAIN, "20:00", 1200, LocalDate.of(2026, 3, 31));
        MainCrosswordResult wrapped = MainCrosswordResult.from(source);

        assertThat(wrapped.getRawContent()).isEqualTo("raw");
        assertThat(wrapped.getDiscordAuthor()).isEqualTo("author");
        assertThat(wrapped.getComment()).isEqualTo("comment");
        assertThat(wrapped.getType()).isEqualTo(CrosswordType.MAIN);
        assertThat(wrapped.getTimeString()).isEqualTo("20:00");
        assertThat(wrapped.getTotalSeconds()).isEqualTo(1200);
        assertThat(wrapped.getDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(wrapped.getDuo()).isNull();
        assertThat(wrapped.getLookups()).isNull();
        assertThat(wrapped.getCheckUsed()).isNull();
    }

    @Test
    void toStringIncludesFlags() {
        MainCrosswordResult result = new MainCrosswordResult("raw", "author", null,
                "15:00", 900, LocalDate.of(2026, 3, 31));
        result.setDuo(true);
        result.setLookups(2);
        assertThat(result.toString()).contains("duo=true");
        assertThat(result.toString()).contains("lookups=2");
        assertThat(result.toString()).contains("checkUsed=null");
    }
}
