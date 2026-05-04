package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.discord.StatsChannelService;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MidiCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.scoreboard.ScoreboardRenderer;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReport;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsService;
import com.wandocorp.nytscorebot.service.stats.GameTypeFilter;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live end-to-end test — requires a real Discord connection and the E2E channels to exist.
 * <p>
 * Channels configured in application-e2e.properties.
 * Both player channels use the bot's own user-id so messages the bot posts
 * pass the userId filter and are processed normally.
 * <p>
 * Scenario: William submits 6 games (auto-finishes), sets Main crossword flags,
 * Conor submits 5 games (no Midi), sets Main check flag (both aided → draw),
 * marks finished → scoreboards render (Mini = time win, Main = draw),
 * then Conor submits Midi late at the same time as William → Midi board renders Nuke!.
 */
@SpringBootTest
@ActiveProfiles("e2e")
class EndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(EndToEndTest.class);

    // ── Channel IDs (injected from application-e2e.properties) ───────────────

    @Value("${discord.channels[0].id}")   private String williamChannelId;
    @Value("${discord.channels[1].id}")   private String conorChannelId;
    @Value("${discord.statusChannelId}")   private String statusChannelId;
    @Value("${discord.resultsChannelId}")  private String resultsChannelId;
    @Value("${discord.statsChannelId}")    private String statsChannelId;

    // ── Autowired components ─────────────────────────────────────────────────

    @Autowired private GatewayDiscordClient client;
    @Autowired private UserRepository userRepository;
    @Autowired private ScoreboardRepository scoreboardRepository;
    @Autowired private PuzzleCalendar puzzleCalendar;
    @Autowired private StatusChannelService statusChannelService;
    @Autowired private ResultsChannelService resultsChannelService;
    @Autowired private ScoreboardRenderer scoreboardRenderer;
    @Autowired private CrosswordStatsService statsService;
    @Autowired private CrosswordStatsReportBuilder statsReportBuilder;
    @Autowired private StatsChannelService statsChannelService;

    // ── Test ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E: Full day scenario — two players, flags, and late submission")
    void fullDayScenario() throws InterruptedException {
        Snowflake williamChannel = Snowflake.of(williamChannelId);
        Snowflake conorChannel   = Snowflake.of(conorChannelId);

        // ── Setup: clear channels and DB ─────────────────────────────────────
        clearChannel(williamChannel);
        clearChannel(conorChannel);
        clearChannel(Snowflake.of(statusChannelId));
        clearChannel(Snowflake.of(resultsChannelId));
        scoreboardRepository.deleteAll();
        userRepository.deleteAll();

        // ── Build message strings using today's puzzle numbers ───────────────

        LocalDate today = puzzleCalendar.today();
        String dateStr = today.format(DateTimeFormatter.ofPattern("M/d/yyyy"));
        String dayOfWeek = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US);
        int wordle = puzzleCalendar.expectedWordle();
        int connections = puzzleCalendar.expectedConnections();
        int strands = puzzleCalendar.expectedStrands();

        // William: Wordle 3/6 (3 emoji rows)
        String williamWordle = "Wordle " + String.format(Locale.US, "%,d", wordle) +
                " 3/6\n\n🟨⬛⬛⬛⬛\n🟩⬛🟩🟩🟩\n🟩🟩🟩🟩🟩";

        // William: Connections 0 mistakes / perfect (4 emoji rows)
        String williamConnections = "Connections\nPuzzle #" + connections +
                "\n🟩🟩🟩🟩\n🟪🟪🟪🟪\n🟨🟨🟨🟨\n🟦🟦🟦🟦";

        // William: Strands 1 hint (2 emoji rows) — 💡 = hint
        String williamStrands = "Strands #" + strands +
                "\n\"Test Theme\"\n💡🔵🟡🔵\n🔵🔵🔵";

        // William: Mini 1:23
        String williamMini = "I solved the " + dateStr + " New York Times Mini Crossword in 1:23!";

        // William: Midi 3:45
        String williamMidi = "I solved the " + dateStr + " New York Times Midi Crossword in 3:45!";

        // William: Main 15:00
        String williamMain = "I solved the " + dayOfWeek + " " + dateStr +
                " New York Times Daily Crossword in 15:00!";

        // Conor: Wordle 4/6 (4 emoji rows)
        String conorWordle = "Wordle " + String.format(Locale.US, "%,d", wordle) +
                " 4/6\n\n🟩⬛⬛⬛⬛\n🟩🟩⬛⬛⬛\n🟩🟩🟩⬛🟩\n🟩🟩🟩🟩🟩";

        // Conor: Connections 1 mistake (5 emoji rows)
        String conorConnections = "Connections\nPuzzle #" + connections +
                "\n🟩🟩🟩🟩\n🟪🟪🟨🟪\n🟪🟪🟪🟪\n🟨🟨🟨🟨\n🟦🟦🟦🟦";

        // Conor: Strands 0 hints (3 emoji rows) — 🟡 = spangram, not a hint
        String conorStrands = "Strands #" + strands +
                "\n\"Test Theme\"\n🔵🟡🔵\n🔵🔵\n🔵🔵";

        // Conor: Mini 1:42 (William wins time-based)
        String conorMini = "I solved the " + dateStr + " New York Times Mini Crossword in 1:42!";

        // Conor: Midi 3:45 (Nuke! — equal times, both unaided; submitted late in Phase 5)
        String conorMidi = "I solved the " + dateStr + " New York Times Midi Crossword in 3:45!";

        // Conor: Main 22:02
        String conorMain = "I solved the " + dayOfWeek + " " + dateStr +
                " New York Times Daily Crossword in 22:02!";

        // ── Phase 1: William submits 6 games → auto-finishes ────────────────

        postTo(williamChannel, williamWordle);
        Thread.sleep(1000);
        postTo(williamChannel, williamConnections);
        Thread.sleep(1000);
        postTo(williamChannel, williamStrands);
        Thread.sleep(1000);
        postTo(williamChannel, williamMini);
        Thread.sleep(1000);
        postTo(williamChannel, williamMidi);
        Thread.sleep(1000);
        postTo(williamChannel, williamMain);

        Thread.sleep(5000);
        User william = userRepository.findByChannelId(williamChannelId).orElseThrow();
        Scoreboard williamBoardPhase1 = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        assertThat(williamBoardPhase1.isFinished()).as("William auto-finished with 6 games").isTrue();
        assertThat(williamBoardPhase1.getWordleResult().getAttempts()).isEqualTo(3);
        assertThat(williamBoardPhase1.getConnectionsResult().getMistakes()).isEqualTo(0);
        assertThat(williamBoardPhase1.getStrandsResult().getHintsUsed()).isEqualTo(1);
        assertThat(williamBoardPhase1.getMiniCrosswordResult().getTimeString()).isEqualTo("1:23");
        assertThat(williamBoardPhase1.getMidiCrosswordResult().getTimeString()).isEqualTo("3:45");
        assertThat(williamBoardPhase1.getMainCrosswordResult().getTimeString()).isEqualTo("15:00");

        // ── Phase 2: William sets Main crossword flags ──────────────────────

        Scoreboard williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        MainCrosswordResult mainResult = williamBoard.getMainCrosswordResult();
        mainResult.setDuo(true);
        logSlashCommand("William", "/duo");
        Thread.sleep(1000);
        mainResult.setLookups(2);
        logSlashCommand("William", "/lookups 2");
        Thread.sleep(1000);
        mainResult.setCheckUsed(true);
        logSlashCommand("William", "/check");
        Thread.sleep(1000);
        scoreboardRepository.save(williamBoard);
        statusChannelService.refresh("William set crossword flags");
        Thread.sleep(1000);

        williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        assertThat(williamBoard.getMainCrosswordResult().getDuo()).isTrue();
        assertThat(williamBoard.getMainCrosswordResult().getLookups()).isEqualTo(2);
        assertThat(williamBoard.getMainCrosswordResult().getCheckUsed()).isTrue();

        // ── Phase 3: Conor submits 5 games (no Midi) ────────────────────────

        postTo(conorChannel, conorWordle);
        Thread.sleep(1000);
        postTo(conorChannel, conorConnections);
        Thread.sleep(1000);
        postTo(conorChannel, conorStrands);
        Thread.sleep(1000);
        postTo(conorChannel, conorMini);
        Thread.sleep(1000);
        postTo(conorChannel, conorMain);

        Thread.sleep(5000);
        User conor = userRepository.findByChannelId(conorChannelId).orElseThrow();
        Scoreboard conorBoardPhase3 = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        assertThat(conorBoardPhase3.getMainCrosswordResult()).as("Conor Main result persisted").isNotNull();
        assertThat(conorBoardPhase3.isFinished()).as("Conor not auto-finished with 5/6 games").isFalse();
        assertThat(conorBoardPhase3.getWordleResult().getAttempts()).isEqualTo(4);
        assertThat(conorBoardPhase3.getConnectionsResult().getMistakes()).isEqualTo(1);
        assertThat(conorBoardPhase3.getStrandsResult().getHintsUsed()).isEqualTo(0);
        assertThat(conorBoardPhase3.getMiniCrosswordResult().getTimeString()).isEqualTo("1:42");
        assertThat(conorBoardPhase3.getMidiCrosswordResult()).as("Conor has not submitted Midi yet").isNull();
        assertThat(conorBoardPhase3.getMainCrosswordResult().getTimeString()).isEqualTo("22:02");

        // ── Phase 3b: Conor sets Main crossword check flag ──────────────────
        // Both players now have used assistance on Main → outcome is a draw.

        MainCrosswordResult conorMainResult = conorBoardPhase3.getMainCrosswordResult();
        conorMainResult.setCheckUsed(true);
        logSlashCommand("Conor", "/check");
        scoreboardRepository.save(conorBoardPhase3);
        statusChannelService.refresh("Conor set Main check flag");
        Thread.sleep(1000);

        Scoreboard conorAfterFlag = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        assertThat(conorAfterFlag.getMainCrosswordResult().getCheckUsed()).isTrue();

        // ── Phase 4: Mark Conor finished → both finished → scoreboards ──────

        Scoreboard conorBoard = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        conorBoard.setFinished(true);
        scoreboardRepository.save(conorBoard);
        logSlashCommand("Conor", "/finished");
        statusChannelService.refresh("Conor marked finished");
        resultsChannelService.refresh();
        Thread.sleep(1000);

        williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        conorBoard = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        assertThat(williamBoard.isFinished()).isTrue();
        assertThat(conorBoard.isFinished()).isTrue();

        scoreboardRenderer.renderAll(williamBoard, "William", conorBoard, "Conor", null)
                .forEach(this::logScoreboard);

        // ── Phase 5: Conor submits Midi late → blocked by finished lock ─────────

        postTo(conorChannel, conorMidi);

        Thread.sleep(5000);
        Scoreboard conorBoardPhase5 = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        assertThat(conorBoardPhase5.getMidiCrosswordResult()).as("Midi result rejected: scoreboard is finished").isNull();

        williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        scoreboardRenderer.renderAll(williamBoard, "William", conorBoardPhase5, "Conor", null)
                .forEach(this::logScoreboard);

        // ── Phase 6: Insert historical data and invoke /stats for week + month ─

        // Seed 30 days of Mini, Midi, and Main results for both players
        // (excludes today so they fall entirely in the "yesterday and earlier" window)
        seedHistoricalCrosswordData(william, conor, today);
        Thread.sleep(1000);

        // ── Phase 6a: /stats game:all period:week ────────────────────────────
        clearChannel(Snowflake.of(statsChannelId));
        LocalDate weekFrom  = today.minusDays(7);
        LocalDate weekTo    = today.minusDays(1);
        CrosswordStatsReport weekStatsReport = statsService.compute(GameTypeFilter.ALL, weekFrom, weekTo);
        String weekReport   = statsReportBuilder.render(weekStatsReport, "Weekly");
        statsChannelService.post(weekReport).block();
        logEntry("📊 Stats", "/stats game:all period:week", weekReport);
        for (String dow : statsReportBuilder.renderDowBreakdowns(weekStatsReport)) {
            statsChannelService.post(dow).block();
            logEntry("📊 Stats DOW", "/stats game:all period:week", dow);
        }

        assertThat(weekReport).as("Week stats report is non-empty").isNotBlank();
        assertThat(weekReport).contains("Mini").contains("Midi").contains("Main");
        Thread.sleep(2000);

        // ── Phase 6b: /stats game:all period:month ───────────────────────────
        LocalDate monthFrom = today.minusMonths(1).plusDays(1);
        LocalDate monthTo   = today.minusDays(1);
        CrosswordStatsReport monthStatsReport = statsService.compute(GameTypeFilter.ALL, monthFrom, monthTo);
        String monthReport  = statsReportBuilder.render(monthStatsReport, "Monthly");
        statsChannelService.post(monthReport).block();
        logEntry("📊 Stats", "/stats game:all period:month", monthReport);
        for (String dow : statsReportBuilder.renderDowBreakdowns(monthStatsReport)) {
            statsChannelService.post(dow).block();
            logEntry("📊 Stats DOW", "/stats game:all period:month", dow);
        }

        assertThat(monthReport).as("Month stats report is non-empty").isNotBlank();
        assertThat(monthReport).contains("Mini").contains("Midi").contains("Main");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Seeds 30 days of Mini, Midi, and Main crossword results for both players,
     * ending on {@code today.minusDays(1)} so all rows fall within the "week" and
     * "month" stats windows. William consistently beats Conor on Mini; times vary
     * daily to produce non-trivial averages and day-of-week data.
     */
    private void seedHistoricalCrosswordData(User william, User conor, LocalDate today) {
        for (int i = 1; i <= 30; i++) {
            LocalDate date = today.minusDays(i);

            // Vary times slightly day-by-day for realistic averages
            int williamMiniSecs = 60 + (i % 5) * 10;       // 60–100 s
            int conorMiniSecs   = 80 + (i % 7) * 10;       // 80–140 s
            int williamMidiSecs = 200 + (i % 6) * 15;      // 200–275 s
            int conorMidiSecs   = 220 + (i % 6) * 15;      // 220–295 s
            int williamMainSecs = 900 + (i % 8) * 60;      // 15–22 min
            int conorMainSecs   = 1000 + (i % 9) * 60;     // ~17–24 min

            // William
            Scoreboard wb = new Scoreboard(william, date);
            wb.addResult(miniResult(williamMiniSecs, date));
            wb.addResult(midiResult(williamMidiSecs, date));
            wb.addResult(mainResult(williamMainSecs, date));
            wb.setFinished(true);
            scoreboardRepository.save(wb);

            // Conor
            Scoreboard cb = new Scoreboard(conor, date);
            cb.addResult(miniResult(conorMiniSecs, date));
            cb.addResult(midiResult(conorMidiSecs, date));
            cb.addResult(mainResult(conorMainSecs, date));
            cb.setFinished(true);
            scoreboardRepository.save(cb);
        }
    }

    private static MiniCrosswordResult miniResult(int totalSeconds, LocalDate date) {
        return new MiniCrosswordResult("", "", "", formatTime(totalSeconds), totalSeconds, date);
    }

    private static MidiCrosswordResult midiResult(int totalSeconds, LocalDate date) {
        return new MidiCrosswordResult("", "", "", formatTime(totalSeconds), totalSeconds, date);
    }

    private static MainCrosswordResult mainResult(int totalSeconds, LocalDate date) {
        return new MainCrosswordResult("", "", "", formatTime(totalSeconds), totalSeconds, date);
    }

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }


    private void postTo(Snowflake channelId, String content) {
        client.getChannelById(channelId)
                .cast(GuildMessageChannel.class)
                .flatMap(channel -> {
                    logMessage(channel.getName(), content);
                    return channel.createMessage(content);
                })
                .block();
    }

    private void logMessage(String channelName, String content) {
        logEntry("📤 Submit", channelName, content);
    }

    private void logSlashCommand(String playerName, String command) {
        logEntry("⚡ Command", playerName, command);
    }

    /**
     * Writes a scoreboard as a standalone markdown code block in {@code $GITHUB_STEP_SUMMARY}.
     * Fenced code blocks cannot render inside table cells, so scoreboards are written as separate
     * sections. A flag is set so the next {@link #logEntry} call re-emits the table header,
     * keeping subsequent Submit/Command rows in a properly-formed table.
     */
    private void logScoreboard(String gameType, String content) {
        String summaryPath = System.getenv("GITHUB_STEP_SUMMARY");
        if (summaryPath == null || summaryPath.isBlank()) {
            log.info("[📊 Scoreboard] [{}]\n{}", gameType, content);
            return;
        }
        try {
            Path file = Path.of(summaryPath);
            String section = "\n**" + gameType + "**\n\n" + content + "\n";
            Files.writeString(file, section, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            needsTableHeader = true;
        } catch (IOException e) {
            log.warn("Failed to write to GITHUB_STEP_SUMMARY: {}", e.getMessage());
            log.info("[📊 Scoreboard] [{}]\n{}", gameType, content);
        }
    }

    /**
     * Logs a single event row to {@code $GITHUB_STEP_SUMMARY} when running in GitHub Actions
     * (markdown table format); falls back to SLF4J INFO logging when the env var is absent.
     * The table header is written at the start and after any scoreboard sections that break the table.
     */
    private boolean needsTableHeader = true;

    private void logEntry(String type, String actor, String content) {
        String summaryPath = System.getenv("GITHUB_STEP_SUMMARY");
        if (summaryPath == null || summaryPath.isBlank()) {
            log.info("[{}] [{}] >>> {}", type, actor, content);
            return;
        }
        try {
            Path file = Path.of(summaryPath);
            StringBuilder sb = new StringBuilder();
            if (needsTableHeader) {
                sb.append("| Type | Actor | Content |\n| --- | --- | --- |\n");
                needsTableHeader = false;
            }
            sb.append("| ").append(escapeForTable(type))
              .append(" | ").append(escapeForTable(actor))
              .append(" | ").append(escapeForTable(content))
              .append(" |\n");
            Files.writeString(file, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write to GITHUB_STEP_SUMMARY: {}", e.getMessage());
            log.info("[{}] [{}] >>> {}", type, actor, content);
        }
    }

    private String escapeForTable(String s) {
        return s.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>");
    }

    private void clearChannel(Snowflake channelId) {
        client.getChannelById(channelId)
                .cast(MessageChannel.class)
                .flatMapMany(ch -> ch.getMessagesAfter(Snowflake.of(0)))
                .flatMap(msg -> msg.delete().onErrorResume(e -> Mono.empty()))
                .blockLast();
    }
}
