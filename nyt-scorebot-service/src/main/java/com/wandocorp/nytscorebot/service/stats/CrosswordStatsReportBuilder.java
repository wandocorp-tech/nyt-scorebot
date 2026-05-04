package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.model.GameType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link CrosswordStatsReport} as Discord-compatible code-block string(s).
 *
 * <p>The main report (header + per-game summary tables) is returned by
 * {@link #render(CrosswordStatsReport, String)}. Day-of-week breakdowns are returned
 * separately by {@link #renderDowBreakdowns}, one code block per game that has DOW data.
 *
 * <p>Time values are formatted as {@code m:ss} for sub-hour and {@code h:mm:ss} for ≥ 1 hour.
 * Average times are rounded to the nearest second.
 */
@Component
public class CrosswordStatsReportBuilder {

    private static final String SEP = "-".repeat(BotText.MAX_LINE_WIDTH);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy");

    /**
     * Renders the main stats report: header + per-game summary tables.
     *
     * <p>Day-of-week breakdowns are available separately via {@link #renderDowBreakdowns}.
     */
    public String render(CrosswordStatsReport report, String periodLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append(BotText.STATUS_CODE_BLOCK_OPEN);
        sb.append(" ").append(periodLabel).append(" Stats\n");
        sb.append(" ").append(dateRange(report.from(), report.to())).append("\n");
        sb.append("\n");
        if (report.games().isEmpty()) {
            sb.append(SEP).append("\n");
            sb.append(" ").append(BotText.STATS_EMPTY_PERIOD).append("\n");
        } else {
            List<CrosswordStatsReport.GameStats> games = report.games();
            for (int i = 0; i < games.size(); i++) {
                if (i > 0) sb.append("\n");
                appendGameSummary(sb, games.get(i));
            }
        }

        sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
        return sb.toString();
    }

    /** Fallback overload — uses the custom period label. */
    public String render(CrosswordStatsReport report) {
        return render(report, BotText.STATS_PERIOD_LABEL_CUSTOM);
    }

    /**
     * Returns a list of DOW breakdown messages, one code block per game that has DOW data.
     * Returns an empty list if no games have DOW data.
     */
    public List<String> renderDowBreakdowns(CrosswordStatsReport report) {
        List<String> result = new ArrayList<>();
        for (CrosswordStatsReport.GameStats game : report.games()) {
            game.dowBlock().ifPresent(dow -> {
                StringBuilder sb = new StringBuilder();
                sb.append(BotText.STATUS_CODE_BLOCK_OPEN);
                appendDowTable(sb, gameLabel(game.gameType()), dow,
                        report.player1Name(), report.player2Name());
                sb.append(BotText.STATUS_CODE_BLOCK_CLOSE);
                result.add(sb.toString());
            });
        }
        return result;
    }

    // ── Per-game summary table ────────────────────────────────────────────────

    private static void appendGameSummary(StringBuilder sb, CrosswordStatsReport.GameStats game) {
        sb.append(" ").append(gameLabel(game.gameType())).append("\n");
        sb.append(SEP).append("\n");

        int nameCol = "Player".length();
        int winCol  = "Win".length();
        int avgCol  = "Avg".length();
        int bestCol = "Best".length();

        for (CrosswordStatsReport.UserGameStats p : game.players()) {
            nameCol = Math.max(nameCol, p.playerName().length());
            winCol  = Math.max(winCol,  String.valueOf(p.wins()).length());
            if (p.avgSeconds().isPresent()) {
                avgCol = Math.max(avgCol, formatTime((int) Math.round(p.avgSeconds().getAsDouble())).length());
            }
            if (p.bestSeconds().isPresent()) {
                bestCol = Math.max(bestCol, formatTime(p.bestSeconds().getAsInt()).length());
            }
        }

        // Header
        sb.append(" ").append(rpad("Player", nameCol)).append(" |")
          .append(" ").append(rpad("Win", winCol)).append(" |")
          .append(" ").append(rpad("Avg", avgCol)).append(" |")
          .append(" Best\n");

        // Separator
        sb.append("-".repeat(BotText.MAX_LINE_WIDTH)).append("\n");

        // Data rows
        for (CrosswordStatsReport.UserGameStats p : game.players()) {
            String avg  = p.avgSeconds().isPresent()
                    ? formatTime((int) Math.round(p.avgSeconds().getAsDouble())) : "-";
            String best = p.bestSeconds().isPresent()
                    ? formatTime(p.bestSeconds().getAsInt()) : "-";
            sb.append(" ").append(rpad(p.playerName(), nameCol)).append(" |")
              .append(" ").append(rpad(String.valueOf(p.wins()), winCol)).append(" |")
              .append(" ").append(rpad(avg, avgCol)).append(" |")
              .append(" ").append(best).append("\n");
        }
    }

    // ── Day-of-week table ─────────────────────────────────────────────────────

    private static void appendDowTable(StringBuilder sb, String gameLabel,
                                        CrosswordStatsReport.DowBlock dow,
                                        String player1Name, String player2Name) {
        sb.append(" ").append(gameLabel).append(" DOW\n");

        int dayCol   = 3;
        int cell1Col = player1Name.length();
        int cell2Col = player2Name.length();

        for (CrosswordStatsReport.DowRow row : dow.rows()) {
            if (row.player1Cell().isPresent()) {
                cell1Col = Math.max(cell1Col, formatDowCell(row.player1Cell().get()).length());
            }
            if (row.player2Cell().isPresent()) {
                cell2Col = Math.max(cell2Col, formatDowCell(row.player2Cell().get()).length());
            }
        }

        // Header
        sb.append(" ").append(rpad("Day", dayCol)).append(" |")
          .append(" ").append(rpad(player1Name, cell1Col)).append(" |")
          .append(" ").append(player2Name).append("\n");

        // Separator
        sb.append("-".repeat(BotText.MAX_LINE_WIDTH)).append("\n");

        // Rows
        for (CrosswordStatsReport.DowRow row : dow.rows()) {
            String cell1 = row.player1Cell().map(CrosswordStatsReportBuilder::formatDowCell).orElse("-");
            String cell2 = row.player2Cell().map(CrosswordStatsReportBuilder::formatDowCell).orElse("-");
            sb.append(" ").append(rpad(dowLabel(row.dayOfWeek()), dayCol)).append(" |")
              .append(" ").append(rpad(cell1, cell1Col)).append(" |")
              .append(" ").append(cell2).append("\n");
        }

        sb.append(SEP).append("\n");
    }

    private static String formatDowCell(CrosswordStatsReport.DowCell cell) {
        return formatTime((int) Math.round(cell.avgSeconds())) + " (" + cell.count() + ")";
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
                return from.format(DAY_MONTH) + "-" + to.format(DateTimeFormatter.ofPattern("d, yyyy"));
            }
            return from.format(DAY_MONTH) + " - " + to.format(DAY_MONTH_YEAR);
        }
        return from.format(DAY_MONTH_YEAR) + " - " + to.format(DAY_MONTH_YEAR);
    }

    private static String rpad(String s, int target) {
        int spaces = target - s.length();
        return spaces > 0 ? s + " ".repeat(spaces) : s;
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
            case MONDAY    -> BotText.STATS_DOW_MON;
            case TUESDAY   -> BotText.STATS_DOW_TUE;
            case WEDNESDAY -> BotText.STATS_DOW_WED;
            case THURSDAY  -> BotText.STATS_DOW_THU;
            case FRIDAY    -> BotText.STATS_DOW_FRI;
            case SATURDAY  -> BotText.STATS_DOW_SAT;
            case SUNDAY    -> BotText.STATS_DOW_SUN;
        };
    }
}
