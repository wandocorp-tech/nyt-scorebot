package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.model.GameType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
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
        assertThat(result).contains("Weekly").contains("Stats");
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
        assertThat(result).contains("3")    // Alice has 3 wins
                          .contains("1:30"); // avg 90s
    }

    @Test
    void renderDoesNotIncludeDowData() {
        CrosswordStatsReport.DowCell cell1 = new CrosswordStatsReport.DowCell(300.0, 4);
        CrosswordStatsReport.DowBlock dow = new CrosswordStatsReport.DowBlock(List.of(
                new CrosswordStatsReport.DowRow(DayOfWeek.MONDAY, Optional.of(cell1), Optional.empty())
        ));
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MINI_CROSSWORD,
                List.of(new CrosswordStatsReport.UserGameStats("Alice", 0, 1,
                        OptionalDouble.of(300.0), OptionalInt.of(300), Optional.empty())),
                Optional.of(dow));
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MINI, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                "Alice", "Bob", List.of(game));

        String mainResult = builder.render(report, "Monthly");

        assertThat(mainResult).doesNotContain("DOW");
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

        List<String> dowMessages = builder.renderDowBreakdowns(report);

        assertThat(dowMessages).hasSize(1);
        String dowMessage = dowMessages.get(0);
        assertThat(dowMessage).contains("Mon");
        assertThat(dowMessage).contains("5:00"); // 300s avg for Alice
        assertThat(dowMessage).contains("8:00"); // 480s avg for Bob
    }

    @Test
    void renderDowBreakdowns_emptyWhenNoData() {
        CrosswordStatsReport report = emptyReport();
        assertThat(builder.renderDowBreakdowns(report)).isEmpty();
    }

    @Test
    void renderDowBreakdowns_emptyWhenGameHasNoDow() {
        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "Alice", 1, 1, OptionalDouble.of(60.0), OptionalInt.of(60), Optional.empty());
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MINI_CROSSWORD, List.of(alice), Optional.empty());
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MINI, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7),
                "Alice", "Bob", List.of(game));

        assertThat(builder.renderDowBreakdowns(report)).isEmpty();
    }

    @Test
    void mainReportLinesFitMaxWidth() {
        CrosswordStatsReport report = fullReport();
        String result = builder.render(report, "Weekly");
        assertAllLinesFitMaxWidth(result);
    }

    @Test
    void dowBreakdownLinesFitMaxWidth() {
        CrosswordStatsReport report = fullReport();
        List<String> dowMessages = builder.renderDowBreakdowns(report);
        for (String msg : dowMessages) {
            assertAllLinesFitMaxWidth(msg);
        }
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
        assertThat(result).contains(BotText.STATS_PERIOD_LABEL_CUSTOM);
    }

    // ── Assisted-excluded footnote (Group 9.6) ────────────────────────────────

    @Test
    void mainRendersAssistedExcludedFootnote() {
        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "Alice", 5, 10,
                OptionalDouble.of(600.0), OptionalInt.of(480),
                Optional.of(LocalDate.of(2025, 1, 6)), 2);
        CrosswordStatsReport.UserGameStats bob = new CrosswordStatsReport.UserGameStats(
                "Bob", 3, 8,
                OptionalDouble.of(720.0), OptionalInt.of(600),
                Optional.of(LocalDate.of(2025, 1, 7)), 0);
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MAIN_CROSSWORD, List.of(alice, bob), Optional.empty());
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MAIN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                "Alice", "Bob", List.of(game));

        String result = builder.render(report, "Monthly");

        assertThat(result).contains(String.format(BotText.STATS_FOOTNOTE_ASSISTED_EXCLUDED, 2));
        // Bob has 0 — no footnote for him.
        assertThat(result).doesNotContain(String.format(BotText.STATS_FOOTNOTE_ASSISTED_EXCLUDED, 0));
    }

    @Test
    void mainOmitsFootnoteWhenNoAssistedExclusions() {
        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "Alice", 5, 10,
                OptionalDouble.of(600.0), OptionalInt.of(480),
                Optional.of(LocalDate.of(2025, 1, 6)), 0);
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MAIN_CROSSWORD, List.of(alice), Optional.empty());
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MAIN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                "Alice", "Bob", List.of(game));

        String result = builder.render(report, "Monthly");

        assertThat(result).doesNotContain("assisted excluded");
    }

    @Test
    void miniNeverRendersAssistedExcludedFootnote() {
        // Even if the count is non-zero on a Mini stats row (defensive — service won't produce this),
        // the builder must not render the footnote for non-Main games.
        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "Alice", 1, 1,
                OptionalDouble.of(60.0), OptionalInt.of(60),
                Optional.empty(), 5);
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MINI_CROSSWORD, List.of(alice), Optional.empty());
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MINI, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7),
                "Alice", "Bob", List.of(game));

        String result = builder.render(report, "Weekly");
        assertThat(result).doesNotContain("assisted excluded");
    }

    @Test
    void midiNeverRendersAssistedExcludedFootnote() {
        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "Alice", 1, 1,
                OptionalDouble.of(180.0), OptionalInt.of(180),
                Optional.empty(), 3);
        CrosswordStatsReport.GameStats game = new CrosswordStatsReport.GameStats(
                GameType.MIDI_CROSSWORD, List.of(alice), Optional.empty());
        CrosswordStatsReport report = new CrosswordStatsReport(
                GameTypeFilter.MIDI, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7),
                "Alice", "Bob", List.of(game));

        String result = builder.render(report, "Weekly");
        assertThat(result).doesNotContain("assisted excluded");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static CrosswordStatsReport emptyReport() {
        return new CrosswordStatsReport(
                GameTypeFilter.ALL,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                "Alice", "Bob",
                List.of());
    }

    /**
     * A report with three games, all three having DOW data and one player with a missing value,
     * designed to exercise column-width computation and max-line-width safety.
     */
    private static CrosswordStatsReport fullReport() {
        CrosswordStatsReport.DowCell cell = new CrosswordStatsReport.DowCell(330.0, 3);
        CrosswordStatsReport.DowBlock dow = new CrosswordStatsReport.DowBlock(List.of(
                new CrosswordStatsReport.DowRow(DayOfWeek.MONDAY, Optional.of(cell), Optional.empty()),
                new CrosswordStatsReport.DowRow(DayOfWeek.TUESDAY, Optional.empty(), Optional.of(cell))
        ));

        CrosswordStatsReport.UserGameStats alice = new CrosswordStatsReport.UserGameStats(
                "William", 5, 10,
                OptionalDouble.of(90.0), OptionalInt.of(60), Optional.empty());
        CrosswordStatsReport.UserGameStats bob = new CrosswordStatsReport.UserGameStats(
                "Conor", 3, 10,
                OptionalDouble.of(120.0), OptionalInt.of(90), Optional.empty());

        CrosswordStatsReport.GameStats mini = new CrosswordStatsReport.GameStats(
                GameType.MINI_CROSSWORD, List.of(alice, bob), Optional.of(dow));
        CrosswordStatsReport.GameStats midi = new CrosswordStatsReport.GameStats(
                GameType.MIDI_CROSSWORD, List.of(alice, bob), Optional.of(dow));
        CrosswordStatsReport.GameStats main = new CrosswordStatsReport.GameStats(
                GameType.MAIN_CROSSWORD, List.of(alice, bob), Optional.of(dow));

        return new CrosswordStatsReport(
                GameTypeFilter.ALL,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                "William", "Conor",
                List.of(mini, midi, main));
    }

    private static void assertAllLinesFitMaxWidth(String codeBlock) {
        String inner = codeBlock
                .replace(BotText.STATUS_CODE_BLOCK_OPEN, "")
                .replace(BotText.STATUS_CODE_BLOCK_CLOSE, "");
        Arrays.stream(inner.split("\n"))
              .filter(line -> !line.isBlank())
              .forEach(line ->
                  assertThat(line.length())
                      .as("Line too wide: [%s] (%d chars)", line, line.length())
                      .isLessThanOrEqualTo(BotText.MAX_LINE_WIDTH));
    }
}
