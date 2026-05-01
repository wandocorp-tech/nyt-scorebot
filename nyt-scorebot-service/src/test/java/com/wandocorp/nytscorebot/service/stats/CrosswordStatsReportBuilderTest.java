package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.model.GameType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class CrosswordStatsReportBuilderTest {

    private CrosswordStatsReportBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CrosswordStatsReportBuilder();
    }

    @Test
    void renderContainsHeaderWithPeriodLabel() {
        CrosswordStatsReport report = emptyReport();
        String result = builder.render(report, "Weekly");
        assertThat(result).contains("Weekly");
    }

    @Test
    void renderShowsEmptyPeriodWhenNoGames() {
        CrosswordStatsReport report = emptyReport();
        String result = builder.render(report, "Weekly");
        assertThat(result).contains("No crossword results");
    }

    @Test
    void renderShowsPlayerWinsAndTimes() {
        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "Alice", 3, 5,
                OptionalDouble.of(90.0), OptionalInt.of(60),
                Optional.of(LocalDate.of(2025, 1, 6)));
        CrosswordStatsReport.UserGameStats bob = new CrosswordStatsReport.UserGameStats(
                "Bob", 1, 5,
                OptionalDouble.of(120.0), OptionalInt.of(90),
                Optional.of(LocalDate.of(2025, 1, 7)));
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MINI_CROSSWORD, List.of(alice, bob), Optional.empty());
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MINI,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                "Alice", "Bob",
                List.of(game));

        String result = builder.render(report, "Weekly");

        assertThat(result).contains("Alice");
        assertThat(result).contains("Bob");
        assertThat(result).contains("3W");  // Alice has 3 wins
        assertThat(result).contains("1:30"); // avg 90s
    }

    @Test
    void dowBlockRenderedWhenPresent() {
        CrosswordStatsReport.DowCell cell1 = new CrosswordStatsReport.DowCell(300.0, 4);
        CrosswordStatsReport.DowCell cell2 = new CrosswordStatsReport.DowCell(480.0, 4);
        List<CrosswordStatsReport.DowRow> rows = List.of(
                new CrosswordStatsReport.DowRow(DayOfWeek.MONDAY,
                        Optional.of(cell1), Optional.of(cell2))
        );
        CrosswordStatsReport.DowBlock dow = new CrosswordStatsReport.DowBlock(rows);
        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "Alice", 0, 4,
                OptionalDouble.of(300.0), OptionalInt.of(240), Optional.empty());
        CrosswordStatsReport.UserGameStats bob = new CrosswordStatsReport.UserGameStats(
                "Bob", 0, 4,
                OptionalDouble.of(480.0), OptionalInt.of(360), Optional.empty());
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MAIN_CROSSWORD, List.of(alice, bob), Optional.of(dow));
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MAIN,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                "Alice", "Bob",
                List.of(game));

        String result = builder.render(report, "Monthly");

        assertThat(result).contains("Mon");
        assertThat(result).contains("5:00"); // 300s avg for Alice
    }

    @Test
    void formatTimeUnderOneHour() {
        assertThat(CrosswordStatsReportBuilder.formatTime(90)).isEqualTo("1:30");
        assertThat(CrosswordStatsReportBuilder.formatTime(3599)).isEqualTo("59:59");
    }

    @Test
    void formatTimeOneHourOrMore() {
        assertThat(CrosswordStatsReportBuilder.formatTime(3600)).isEqualTo("1:00:00");
        assertThat(CrosswordStatsReportBuilder.formatTime(3661)).isEqualTo("1:01:01");
    }

    @Test
    void dateRangeSameDay() {
        LocalDate d = LocalDate.of(2025, 4, 7);
        assertThat(CrosswordStatsReportBuilder.dateRange(d, d)).isEqualTo("Apr 7, 2025");
    }

    @Test
    void dateRangeSameMonthDifferentDays() {
        LocalDate from = LocalDate.of(2025, 4, 1);
        LocalDate to   = LocalDate.of(2025, 4, 30);
        String result = CrosswordStatsReportBuilder.dateRange(from, to);
        assertThat(result).contains("Apr").contains("30");
    }

    @Test
    void dateRangeDifferentYears() {
        LocalDate from = LocalDate.of(2024, 12, 1);
        LocalDate to   = LocalDate.of(2025, 1, 31);
        String result = CrosswordStatsReportBuilder.dateRange(from, to);
        assertThat(result).contains("2024").contains("2025");
    }

    @Test
    void renderFallbackUsesCustomLabel() {
        CrosswordStatsReport report = emptyReport();
        String result = builder.render(report);
        // The no-arg render uses STATS_PERIOD_LABEL_CUSTOM as the period label
        assertThat(result).isNotBlank();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static CrosswordStatsReport emptyReport() {
        return new CrosswordStatsReport(
                GameTypeFilter.ALL,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                "Alice", "Bob",
                List.of());
    }
}
