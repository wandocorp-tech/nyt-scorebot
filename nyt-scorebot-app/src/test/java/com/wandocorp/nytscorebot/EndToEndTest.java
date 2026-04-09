package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.MessageChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live end-to-end test — requires a real Discord connection and the E2E channels to exist.
 * <p>
 * Channels configured in application-e2e.properties:
 * - William   : 1488650992009482280
 * - Conor     : 1488651510244966491
 * - Results   : 1488651614482075848
 * - Status    : 1488651639282860133
 * <p>
 * Both player channels use the bot's own user-id (1485298372637102101) so messages
 * the bot posts pass the userId filter and are processed normally.
 * <p>
 * Scenario: William submits 6 games (auto-finishes), sets Main crossword flags,
 * Conor submits 5 games (no Midi), marks finished → scoreboards render,
 * then Conor submits Midi late → Midi board refreshes.
 */
@SpringBootTest
@ActiveProfiles("e2e")
class EndToEndTest {

    // ── Channel IDs ──────────────────────────────────────────────────────────

    private static final Snowflake WILLIAM_CHANNEL = Snowflake.of("1488650992009482280");
    private static final Snowflake CONOR_CHANNEL   = Snowflake.of("1488651510244966491");

    // ── Autowired components ─────────────────────────────────────────────────

    @Autowired private GatewayDiscordClient client;
    @Autowired private UserRepository userRepository;
    @Autowired private ScoreboardRepository scoreboardRepository;
    @Autowired private PuzzleCalendar puzzleCalendar;
    @Autowired private StatusChannelService statusChannelService;
    @Autowired private ResultsChannelService resultsChannelService;

    // ── Test ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E: Full day scenario — two players, flags, and late submission")
    void fullDayScenario() {
        // ── Setup: clear channels and DB ─────────────────────────────────────
        clearChannel(WILLIAM_CHANNEL);
        clearChannel(CONOR_CHANNEL);
        clearChannel(Snowflake.of("1488651639282860133")); // status
        clearChannel(Snowflake.of("1488651614482075848")); // results
        sleep(2);
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

        // Conor: Mini 1:23 (tie with William)
        String conorMini = "I solved the " + dateStr + " New York Times Mini Crossword in 1:23!";

        // Conor: Midi 4:10 (submitted late in Phase 5)
        String conorMidi = "I solved the " + dateStr + " New York Times Midi Crossword in 4:10!";

        // Conor: Main 22:02
        String conorMain = "I solved the " + dayOfWeek + " " + dateStr +
                " New York Times Daily Crossword in 22:02!";

        // ── Phase 1: William submits 6 games → auto-finishes ────────────────

        postTo(WILLIAM_CHANNEL, williamWordle);
        sleep(1);
        postTo(WILLIAM_CHANNEL, williamConnections);
        sleep(1);
        postTo(WILLIAM_CHANNEL, williamStrands);
        sleep(1);
        postTo(WILLIAM_CHANNEL, williamMini);
        sleep(1);
        postTo(WILLIAM_CHANNEL, williamMidi);
        sleep(1);
        postTo(WILLIAM_CHANNEL, williamMain);
        sleep(5);

        User william = userRepository.findByChannelId(WILLIAM_CHANNEL.asString()).orElseThrow();
        Scoreboard williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        assertThat(williamBoard.isFinished()).as("William auto-finished with 6 games").isTrue();
        assertThat(williamBoard.getWordleResult().getAttempts()).isEqualTo(3);
        assertThat(williamBoard.getConnectionsResult().getMistakes()).isEqualTo(0);
        assertThat(williamBoard.getStrandsResult().getHintsUsed()).isEqualTo(1);
        assertThat(williamBoard.getMiniCrosswordResult().getTimeString()).isEqualTo("1:23");
        assertThat(williamBoard.getMidiCrosswordResult().getTimeString()).isEqualTo("3:45");
        assertThat(williamBoard.getMainCrosswordResult().getTimeString()).isEqualTo("15:00");

        // ── Phase 2: William sets Main crossword flags ──────────────────────

        williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        CrosswordResult mainResult = williamBoard.getMainCrosswordResult();
        mainResult.setDuo(true);
        mainResult.setLookups(2);
        mainResult.setCheckUsed(true);
        scoreboardRepository.save(williamBoard);
        statusChannelService.refresh("William set crossword flags");
        sleep(3);

        williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        assertThat(williamBoard.getMainCrosswordResult().getDuo()).isTrue();
        assertThat(williamBoard.getMainCrosswordResult().getLookups()).isEqualTo(2);
        assertThat(williamBoard.getMainCrosswordResult().getCheckUsed()).isTrue();

        // ── Phase 3: Conor submits 5 games (no Midi) ────────────────────────

        postTo(CONOR_CHANNEL, conorWordle);
        sleep(1);
        postTo(CONOR_CHANNEL, conorConnections);
        sleep(1);
        postTo(CONOR_CHANNEL, conorStrands);
        sleep(1);
        postTo(CONOR_CHANNEL, conorMini);
        sleep(1);
        postTo(CONOR_CHANNEL, conorMain);
        sleep(5);

        User conor = userRepository.findByChannelId(CONOR_CHANNEL.asString()).orElseThrow();
        Scoreboard conorBoard = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        assertThat(conorBoard.isFinished()).as("Conor not auto-finished with 5/6 games").isFalse();
        assertThat(conorBoard.getWordleResult().getAttempts()).isEqualTo(4);
        assertThat(conorBoard.getConnectionsResult().getMistakes()).isEqualTo(1);
        assertThat(conorBoard.getStrandsResult().getHintsUsed()).isEqualTo(0);
        assertThat(conorBoard.getMiniCrosswordResult().getTimeString()).isEqualTo("1:23");
        assertThat(conorBoard.getMidiCrosswordResult()).as("Conor has not submitted Midi yet").isNull();
        assertThat(conorBoard.getMainCrosswordResult().getTimeString()).isEqualTo("22:02");

        // ── Phase 4: Mark Conor finished → both finished → scoreboards ──────

        conorBoard = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        conorBoard.setFinished(true);
        scoreboardRepository.save(conorBoard);
        statusChannelService.refresh("Conor marked finished");
        resultsChannelService.refresh();
        sleep(5);

        williamBoard = scoreboardRepository.findByUserAndDate(william, today).orElseThrow();
        conorBoard = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        assertThat(williamBoard.isFinished()).isTrue();
        assertThat(conorBoard.isFinished()).isTrue();

        // ── Phase 5: Conor submits Midi late → boards refresh ───────────────

        postTo(CONOR_CHANNEL, conorMidi);
        sleep(5);

        conorBoard = scoreboardRepository.findByUserAndDate(conor, today).orElseThrow();
        assertThat(conorBoard.getMidiCrosswordResult()).as("Conor now has Midi result").isNotNull();
        assertThat(conorBoard.getMidiCrosswordResult().getTimeString()).isEqualTo("4:10");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void postTo(Snowflake channelId, String content) {
        client.getChannelById(channelId)
                .cast(MessageChannel.class)
                .flatMap(channel -> channel.createMessage(content))
                .block();
    }

    private void clearChannel(Snowflake channelId) {
        client.getChannelById(channelId)
                .cast(MessageChannel.class)
                .flatMapMany(ch -> ch.getMessagesAfter(Snowflake.of(0)))
                .flatMap(msg -> msg.delete().onErrorResume(e -> Mono.empty()))
                .blockLast();
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("E2E test interrupted during wait", e);
        }
    }
}
