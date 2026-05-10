package com.wandocorp.nytscorebot.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PbDayOfWeekTest {

    @Test
    void encodeWeekdayUsesEnumName() {
        assertThat(PbDayOfWeek.encode(Optional.of(DayOfWeek.SATURDAY))).isEqualTo("SATURDAY");
    }

    @Test
    void encodeEmptyUsesSentinel() {
        assertThat(PbDayOfWeek.encode(Optional.empty())).isEqualTo(PbDayOfWeek.ALL_DAYS_SENTINEL);
    }

    @Test
    void decodeWeekdayRoundTrips() {
        assertThat(PbDayOfWeek.decode("MONDAY")).contains(DayOfWeek.MONDAY);
    }

    @Test
    void decodeSentinelReturnsEmpty() {
        assertThat(PbDayOfWeek.decode(PbDayOfWeek.ALL_DAYS_SENTINEL)).isEmpty();
    }

    @Test
    void decodeNullThrows() {
        assertThatThrownBy(() -> PbDayOfWeek.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeUnknownThrows() {
        assertThatThrownBy(() -> PbDayOfWeek.decode("MAYBEDAY"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
