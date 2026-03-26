package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.ConnectionsResult;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.StrandsResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import com.wandocorp.nytscorebot.listener.MessageListener;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.MarkFinishedOutcome;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live smoke tests — require a real Discord connection and the test channels to exist in the server.
 * <p>
 * Channels configured in application-test.properties:
 * - Player One (Smoke Test) : 1358128555384377360  ← configured, bot posts here
 * - Player Two (Smoke Test) : 1485602399182913586  ← configured, bot posts here
 * - ignored-channel         : 1485602440287092806  ← NOT configured, used for 5.4
 * <p>
 * Both configured channels use the bot's own user-id (1485298372637102101) so that messages
 * the bot posts to those channels pass the userId filter and are processed normally.
 * <p>
 * Run with: mvn test
 */
@SpringBootTest
@ActiveProfiles("test")
class SmokeTest {

    // Real sample data — captured from the NYT games.
    // The Wordle grid has no trailing comment; the Crossword has "No lookups" as a comment.
    private static final String WORDLE_SAMPLE =
            "Wordle 1,738 3/6\n\n🟨⬛⬛⬛⬛\n🟩⬛🟩🟩🟩\n🟩🟩🟩🟩🟩";

    private static final String CROSSWORD_SAMPLE =
            """
                https://www.nytimes.com/crosswords/game/by-id/23772
                I solved the Saturday 3/21/2026 New York Times Daily Crossword in 22:02!
        
                No lookups""";

    // Configured channels — messages posted here ARE processed
    private static final Snowflake PLAYER_1_CHANNEL = Snowflake.of("1358128555384377360");
    private static final Snowflake PLAYER_2_CHANNEL = Snowflake.of("1485602399182913586");

    // Not in discord.channels config — messages posted here are silently ignored
    private static final Snowflake IGNORED_CHANNEL = Snowflake.of("1485602440287092806");

    @Autowired
    private GatewayDiscordClient client;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoreboardRepository scoreboardRepository;

    @Autowired
    private MessageListener messageListener;

    @Autowired
    private ScoreboardService scoreboardService;

    @BeforeEach
    void cleanDb() throws InterruptedException {
        // Let any in-flight events from the previous test settle before clearing the DB.
        Thread.sleep(1_000);
        scoreboardRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── 5.2 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5.2: Message on configured channel creates a User and a Scoreboard in H2")
    void configuredChannelMessagePersistsUserAndScoreboard() {
        postTo(PLAYER_1_CHANNEL, WORDLE_SAMPLE);
        sleep(5);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(scoreboardRepository.count()).isEqualTo(1);

        User user = userRepository.findAll().get(0);
        assertThat(user.getChannelId()).isEqualTo(PLAYER_1_CHANNEL.asString());
        assertThat(user.getName()).isEqualTo("Player One (Smoke Test)");

        Scoreboard scoreboard = scoreboardRepository.findAll().get(0);
        assertThat(scoreboard.getDate()).isEqualTo(LocalDate.now());

        WordleResult wr = scoreboard.getWordleResult();
        assertThat(wr).isNotNull();
        assertThat(wr.getPuzzleNumber()).isEqualTo(1738);
        assertThat(wr.getAttempts()).isEqualTo(3);
        assertThat(wr.isCompleted()).isTrue();
        assertThat(wr.isHardMode()).isFalse();
        assertThat(wr.getRawContent()).isEqualTo(WORDLE_SAMPLE);
        assertThat(wr.getComment()).isNull();
    }

    // ── 5.3 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5.3: Second message from same channel reuses the existing User and adds a new Scoreboard")
    void secondMessageReusesUserAndAddsScoreboard() {
        postTo(PLAYER_1_CHANNEL, WORDLE_SAMPLE);
        sleep(5);

        // Daily Crossword dated 3/21/2026 — CrosswordParser extracts that date, so
        // ScoreboardService creates a second Scoreboard record for a different LocalDate.
        postTo(PLAYER_1_CHANNEL, CROSSWORD_SAMPLE);
        sleep(5);

        List<User> users = userRepository.findAll();
        assertThat(users).as("same User reused — not a second User").hasSize(1);
        assertThat(users.get(0).getChannelId()).isEqualTo(PLAYER_1_CHANNEL.asString());

        List<Scoreboard> scoreboards = scoreboardRepository.findAll();
        assertThat(scoreboards).as("one Scoreboard per date").hasSize(2);

        Scoreboard wordleBoard = scoreboards.stream()
                .filter(s -> s.getDate().equals(LocalDate.now()))
                .findFirst().orElseThrow();
        assertThat(wordleBoard.getWordleResult()).isNotNull();

        Scoreboard crosswordBoard = scoreboards.stream()
                .filter(s -> s.getDate().equals(LocalDate.of(2026, 3, 21)))
                .findFirst().orElseThrow();
        CrosswordResult cr = crosswordBoard.getDailyCrosswordResult();
        assertThat(cr).isNotNull();
        assertThat(cr.getTimeString()).isEqualTo("22:02");
        assertThat(cr.getRawContent()).isEqualTo(CROSSWORD_SAMPLE);
        assertThat(cr.getComment()).isEqualTo("No lookups");
    }

    // ── 5.4 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5.4: Message on unconfigured channel is silently ignored")
    void unconfiguredChannelIsIgnored() {
        assertThat(messageListener.isChannelMonitored(IGNORED_CHANNEL))
                .as("ignored-channel must not be in the monitored set")
                .isFalse();

        // Post a valid game message to the ignored channel — the bot receives the Discord event
        // but its channel filter rejects it, so nothing should be persisted.
        postTo(IGNORED_CHANNEL, WORDLE_SAMPLE);
        sleep(5);

        assertThat(userRepository.count()).as("no User created for unconfigured channel").isZero();
        assertThat(scoreboardRepository.count()).as("no Scoreboard created for unconfigured channel").isZero();
    }

    // ── Multi-channel ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Multi-channel: Player Two channel creates its own User and Scoreboard independently")
    void playerTwoChannelPersistsIndependently() {
        postTo(PLAYER_1_CHANNEL, WORDLE_SAMPLE);
        postTo(PLAYER_2_CHANNEL, WORDLE_SAMPLE);
        sleep(5);

        assertThat(userRepository.count()).as("one User per channel").isEqualTo(2);
        assertThat(scoreboardRepository.count()).as("one Scoreboard per channel").isEqualTo(2);

        User player1 = userRepository.findByChannelId(PLAYER_1_CHANNEL.asString()).orElseThrow();
        assertThat(player1.getName()).isEqualTo("Player One (Smoke Test)");
        assertThat(scoreboardRepository.findByUserAndDate(player1, LocalDate.now())).isPresent();

        User player2 = userRepository.findByChannelId(PLAYER_2_CHANNEL.asString()).orElseThrow();
        assertThat(player2.getName()).isEqualTo("Player Two (Smoke Test)");
        assertThat(scoreboardRepository.findByUserAndDate(player2, LocalDate.now())).isPresent();
    }

    // ── All-games ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All-games: One of each game type in a single day lands on one Scoreboard row")
    void allGameTypesLandOnSingleScoreboard() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy"));

        String connections = "Connections\nPuzzle #1016\n🟩🟩🟩🟩\n🟪🟪🟪🟪\n🟨🟨🟨🟨\n🟦🟦🟦🟦";
        String strands = "Strands #750\n\"In pieces\"\n🔵🔵🟡🔵\n🔵🔵🔵";
        String mini = "I solved the " + today + " New York Times Mini Crossword in 1:23!";
        String midi = "I solved the " + today + " New York Times Midi Crossword in 3:45!";
        String daily = "I solved the " + today + " New York Times Daily Crossword in 15:00!";

        postTo(PLAYER_1_CHANNEL, WORDLE_SAMPLE);
        postTo(PLAYER_1_CHANNEL, connections);
        postTo(PLAYER_1_CHANNEL, strands);
        postTo(PLAYER_1_CHANNEL, mini);
        postTo(PLAYER_1_CHANNEL, midi);
        postTo(PLAYER_1_CHANNEL, daily);
        sleep(10);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(scoreboardRepository.count()).as("all same-day results on one Scoreboard").isEqualTo(1);

        Scoreboard scoreboard = scoreboardRepository.findAll().get(0);
        assertThat(scoreboard.getDate()).isEqualTo(LocalDate.now());

        WordleResult wr = scoreboard.getWordleResult();
        assertThat(wr).isNotNull();
        assertThat(wr.getPuzzleNumber()).isEqualTo(1738);
        assertThat(wr.getAttempts()).isEqualTo(3);

        ConnectionsResult cr = scoreboard.getConnectionsResult();
        assertThat(cr).isNotNull();
        assertThat(cr.getPuzzleNumber()).isEqualTo(1016);
        assertThat(cr.getMistakes()).isEqualTo(0);

        StrandsResult sr = scoreboard.getStrandsResult();
        assertThat(sr).isNotNull();
        assertThat(sr.getPuzzleNumber()).isEqualTo(750);
        assertThat(sr.getHintsUsed()).isEqualTo(0);

        CrosswordResult miniResult = scoreboard.getMiniCrosswordResult();
        assertThat(miniResult).isNotNull();
        assertThat(miniResult.getTimeString()).isEqualTo("1:23");

        CrosswordResult midiResult = scoreboard.getMidiCrosswordResult();
        assertThat(midiResult).isNotNull();
        assertThat(midiResult.getTimeString()).isEqualTo("3:45");

        CrosswordResult dailyResult = scoreboard.getDailyCrosswordResult();
        assertThat(dailyResult).isNotNull();
        assertThat(dailyResult.getTimeString()).isEqualTo("15:00");
    }

    // ── /finished ────────────────────────────────────────────────────────────

    /**
     * The bot Discord user ID used in all configured smoke-test channels.
     * Matches discord.channels[*].user-id in application-test.properties.
     */
    private static final String BOT_USER_ID = "1485298372637102101";

    @Test
    @DisplayName("/finished: marks Player One's scoreboard as finished and persists the change")
    void finishedCommandMarksScoreboardFinished() {
        postTo(PLAYER_1_CHANNEL, WORDLE_SAMPLE);
        sleep(5);

        assertThat(scoreboardRepository.count()).isEqualTo(1);
        Scoreboard before = scoreboardRepository.findAll().get(0);
        assertThat(before.isFinished()).isFalse();

        MarkFinishedOutcome outcome = scoreboardService.markFinished(BOT_USER_ID, LocalDate.now());
        assertThat(outcome).isEqualTo(MarkFinishedOutcome.MARKED_FINISHED);

        Scoreboard after = scoreboardRepository.findAll().get(0);
        assertThat(after.isFinished()).isTrue();
    }

    @Test
    @DisplayName("/finished: returns NO_SCOREBOARD_FOR_DATE when user has no results today")
    void finishedCommandReturnsNoScoreboardWhenNoneExists() {
        // Ensure the user record exists by posting a result first, then delete scoreboards only
        postTo(PLAYER_1_CHANNEL, WORDLE_SAMPLE);
        sleep(5);
        scoreboardRepository.deleteAll();

        MarkFinishedOutcome outcome = scoreboardService.markFinished(BOT_USER_ID, LocalDate.now());
        assertThat(outcome).isEqualTo(MarkFinishedOutcome.NO_SCOREBOARD_FOR_DATE);
    }

    @Test
    @DisplayName("/finished: returns USER_NOT_FOUND for an untracked Discord user ID")
    void finishedCommandReturnsUserNotFoundForUnknownUser() {
        MarkFinishedOutcome outcome = scoreboardService.markFinished("unknown-discord-id", LocalDate.now());
        assertThat(outcome).isEqualTo(MarkFinishedOutcome.USER_NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void postTo(Snowflake channelId, String content) {
        client.getChannelById(channelId)
                .cast(MessageChannel.class)
                .flatMap(channel -> channel.createMessage(content))
                .block();
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Smoke test interrupted during wait", e);
        }
    }
}
