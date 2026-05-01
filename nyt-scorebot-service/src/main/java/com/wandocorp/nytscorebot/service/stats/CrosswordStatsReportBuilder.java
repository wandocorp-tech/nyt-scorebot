package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.model.GameType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Renders a {@link CrosswordStatsReport} as a Discord-compatible code-block string.
 *
 * <p>Time values are formatted as {@code m:ss} for sub-hour and {@code h:mm:ss} for ≥ 1 hour.
 * Average times are rounded to the nearest second.
 */
@Component
public class CrosswordStatsReportBuilder {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy");

    /**
     * Render the report as a single Discord message (code-block wrapped).
     */
    public String render(CrosswordStatsReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);

        String header = String.format(BotText.STATS_REPORT_HEADER,
                periodLabel(report.filter()), dateRange(report.from(), report.to()));
        sb.append(header).append("\n");

        if (report.games().isEmpty()) {
            sb.append("\n").append(BotText.STATS_EMPTY_PERIOD).append("\n");
        } else {
            for (CrosswordStatsReport.GameStats game : report.games()) {
                sb.append("\n");
                sb.append(String.format(BotText.STATS_GAME_SECTION_HEADER, gameLabel(game.gameType()))).append("\n");
                renderPlayers(sb, game.players());

                game.dowBlock().ifPresent(dow -> {
                    sb.append("\n");
                    sb.append(String.format(BotText.STATS_DOW_SECTION_HEADER, gameLabel(game.gameType()))).append("\n");
                    renderDow(sb, dow, report.player1Name(), report.player2Name());
                });
            }
        }

        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    // ── Player rows ───────────────────────────────────────────────────────────

    private static void renderPlayers(StringBuilder sb,
                                      List<CrosswordStatsReport.UserGameStats> players) {
        for (int i = 0; i < players.size(); i++) {
            CrosswordStatsReport.UserGameStats p = players.get(i);
            String rank = rankEmoji(i + 1);
            String avg = p.avgSeconds().isPresent()
                    ? formatTime((int) Math.round(p.avgSeconds().getAsDouble()))
                    : "—";
            String best = p.bestSeconds().isPresent()
                    ? formatTime(p.bestSeconds().getAsInt()) + bestDateSuffix(p.bestDate())
                    : "—";
            sb.append(String.format("  %s %-12s %2dW  avg %s  best %s%n",
                    rank, p.playerName(), p.wins(), avg, best));
        }
    }

    private static String bestDateSuffix(Optional<LocalDate> bestDate) {
        return bestDate.map(d -> " (" + d.format(DAY_MONTH) + ")").orElse("");
    }

    // ── Day-of-week rows ──────────────────────────────────────────────────────

    private static void renderDow(StringBuilder sb, CrosswordStatsReport.DowBlock dow,
                                   String name1, String name2) {
        for (CrosswordStatsReport.DowRow row : dow.rows()) {
            String label = dowLabel(row.dayOfWeek());
            String cell1 = formatDowCell(row.player1Cell(), name1);
            String cell2 = formatDowCell(row.player2Cell(), name2);
            sb.append(String.format("  %s   %s   %s%n", label, cell1, cell2));
        }
    }

    private static String formatDowCell(Optional<CrosswordStatsReport.DowCell> cellOpt,
                                         String playerName) {
        return cellOpt
                .map(c -> String.format("%s %s (%d)",
                        playerName, formatTime((int) Math.round(c.avgSeconds())), c.count()))
                .orElse(playerName + " —");
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    static String formatTime(int totalSeconds) {
        if (totalSeconds >= 3600) {
            int h = totalSeconds / 3600;
            int m = (totalSeconds % 3600) / 60;
            int s = totalSeconds % 60;
            return String.format("%d:%02d:%02d", h, m, s);
        }
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    static String dateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return from.format(DAY_MONTH_YEAR);
        }
        if (from.getYear() == to.getYear()) {
            if (from.getMonth() == to.getMonth()) {
                // Same month: "Apr 24–30, 2026"
                return from.format(DAY_MONTH) + "\u2013"
                        + to.format(DateTimeFormatter.ofPattern("d, yyyy"));
            }
            // Same year, different months: "Apr 24 – May 7, 2026"
            return from.format(DAY_MONTH) + " \u2013 " + to.format(DAY_MONTH_YEAR);
        }
        // Different years
        return from.format(DAY_MONTH_YEAR) + " \u2013 " + to.format(DAY_MONTH_YEAR);
    }

    private static String periodLabel(GameTypeFilter filter) {
        return switch (filter) {
            // The scheduled jobs always use ALL; label is set by the caller at the report level.
            // For slash-command, the period label is part of the report header constant;
            // we derive a sensible default from the filter here.
            default -> BotText.STATS_PERIOD_LABEL_CUSTOM;
        };
    }

    // Overload used by callers that know the period label explicitly (e.g. scheduled jobs).
    public String render(CrosswordStatsReport report, String periodLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);

        String header = String.format(BotText.STATS_REPORT_HEADER,
                periodLabel, dateRange(report.from(), report.to()));
        sb.append(header).append("\n");

        if (report.games().isEmpty()) {
            sb.append("\n").append(BotText.STATS_EMPTY_PERIOD).append("\n");
        } else {
            for (CrosswordStatsReport.GameStats game : report.games()) {
                sb.append("\n");
                sb.append(String.format(BotText.STATS_GAME_SECTION_HEADER, gameLabel(game.gameType()))).append("\n");
                renderPlayers(sb, game.players());

                game.dowBlock().ifPresent(dow -> {
                    sb.append("\n");
                    sb.append(String.format(BotText.STATS_DOW_SECTION_HEADER, gameLabel(game.gameType()))).append("\n");
                    renderDow(sb, dow, report.player1Name(), report.player2Name());
                });
            }
        }

        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    private static String rankEmoji(int rank) {
        return switch (rank) {
            case 1 -> BotText.STATS_RANK_1;
            case 2 -> BotText.STATS_RANK_2;
            case 3 -> BotText.STATS_RANK_3;
            default -> String.valueOf(rank) + ".";
        };
    }

    private static String gameLabel(GameType gameType) {
        return switch (gameType) {
            case MINI_CROSSWORD -> BotText.GAME_LABEL_MINI;
            case MIDI_CROSSWORD -> BotText.GAME_LABEL_MIDI;
            case MAIN_CROSSWORD -> BotText.GAME_LABEL_MAIN;
            default -> gameType.name();
        };
    }

    private static String dowLabel(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> BotText.STATS_DOW_MON;
            case TUESDAY -> BotText.STATS_DOW_TUE;
            case WEDNESDAY -> BotText.STATS_DOW_WED;
            case THURSDAY -> BotText.STATS_DOW_THU;
            case FRIDAY -> BotText.STATS_DOW_FRI;
            case SATURDAY -> BotText.STATS_DOW_SAT;
            case SUNDAY -> BotText.STATS_DOW_SUN;
        };
    }
}
