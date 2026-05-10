package com.wandocorp.nytscorebot.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MainCrosswordResultTest {

    private MainCrosswordResult newResult() {
        return new MainCrosswordResult("raw", "author", "comment", "5:00", 300, LocalDate.of(2026, 5, 10));
    }

    @Test
    void isAssistedFalseWhenAllFlagsUnset() {
        assertThat(newResult().isAssisted()).isFalse();
    }

    @Test
    void isAssistedFalseWhenFlagsExplicitlyClean() {
        MainCrosswordResult r = newResult();
        r.setDuo(false);
        r.setCheckUsed(false);
        r.setLookups(0);
        assertThat(r.isAssisted()).isFalse();
    }

    @Test
    void isAssistedTrueWhenDuoTrue() {
        MainCrosswordResult r = newResult();
        r.setDuo(true);
        assertThat(r.isAssisted()).isTrue();
    }

    @Test
    void isAssistedTrueWhenCheckUsedTrue() {
        MainCrosswordResult r = newResult();
        r.setCheckUsed(true);
        assertThat(r.isAssisted()).isTrue();
    }

    @Test
    void isAssistedTrueWhenLookupsPositive() {
        MainCrosswordResult r = newResult();
        r.setLookups(1);
        assertThat(r.isAssisted()).isTrue();
    }

    @Test
    void isAssistedFalseWhenLookupsZero() {
        MainCrosswordResult r = newResult();
        r.setLookups(0);
        assertThat(r.isAssisted()).isFalse();
    }

    @Test
    void isAssistedTrueWhenAllFlagsSet() {
        MainCrosswordResult r = newResult();
        r.setDuo(true);
        r.setCheckUsed(true);
        r.setLookups(3);
        assertThat(r.isAssisted()).isTrue();
    }
}
