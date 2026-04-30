package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.WinStreakService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class WinStreakMidnightJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 24);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private WinStreakService winStreakService;
    private ScoreboardRepository scoreboardRepository;
    private UserRepository userRepository;
    private DiscordChannelProperties channelProperties;
    private ResultsChannelService resultsChannelService;
    private GatewayDiscordClient client;
    private WinStreakMidnightJob job;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        winStreakService = mock(WinStreakService.class);
        scoreboardRepository = mock(ScoreboardRepository.class);
        userRepository = mock(UserRepository.class);
        channelProperties = new DiscordChannelProperties();
        resultsChannelService = mock(ResultsChannelService.class);
        client = mock(GatewayDiscordClient.class);
        PuzzleCalendar calendar = mock(PuzzleCalendar.class);
        when(calendar.today()).thenReturn(TODAY);

        ChannelConfig c1 = new ChannelConfig();
        c1.setId("111"); c1.setName("Alice"); c1.setUserId("u1");
        ChannelConfig c2 = new ChannelConfig();
        c2.setId("222"); c2.setName("Bob"); c2.setUserId("u2");
        channelProperties.setChannels(List.of(c1, c2));
        channelProperties.setResultsChannelId("999");

        alice = new User("c1", "Alice", "u1");
        bob = new User("c2", "Bob", "u2");
        when(userRepository.findByDiscordUserId("u1")).thenReturn(Optional.of(alice));
        when(userRepository.findByDiscordUserId("u2")).thenReturn(Optional.of(bob));
        when(winStreakService.getStreaks(any())).thenReturn(Map.of());

        job = new WinStreakMidnightJob(winStreakService, scoreboardRepository, userRepository,
                channelProperties, calendar, resultsChannelService, client);
    }

    @Test
    void noOpWhenChannelsMisconfigured() {
        channelProperties.setChannels(List.of());
        job.run();
        verifyNoInteractions(winStreakService);
    }

    @Test
    void noOpWhenUsersNotRegistered() {
        when(userRepository.findByDiscordUserId(any())).thenReturn(Optional.empty());
        job.run();
        verifyNoInteractions(winStreakService);
    }

    @Test
    void appliesForfeitForEachCrosswordWithSubmissionFlags() {
        Scoreboard sbAlice = new Scoreboard(alice, YESTERDAY);
        sbAlice.addResult(new MiniCrosswordResult("raw", "u1", null, "0:30", 30, YESTERDAY));
        sbAlice.addResult(new MainCrosswordResult("raw", "u1", null, "15:00", 900, YESTERDAY));
        // Bob has no scoreboard at all
        when(scoreboardRepository.findByUserAndDate(alice, YESTERDAY)).thenReturn(Optional.of(sbAlice));
        when(scoreboardRepository.findByUserAndDate(bob, YESTERDAY)).thenReturn(Optional.empty());

        job.run();

        verify(winStreakService).applyForfeit(eq(GameType.MINI_CROSSWORD), eq(alice), eq(true), eq(bob), eq(false), eq(YESTERDAY));
        verify(winStreakService).applyForfeit(eq(GameType.MIDI_CROSSWORD), eq(alice), eq(false), eq(bob), eq(false), eq(YESTERDAY));
        verify(winStreakService).applyForfeit(eq(GameType.MAIN_CROSSWORD), eq(alice), eq(true), eq(bob), eq(false), eq(YESTERDAY));
    }

    @Test
    void editsExistingSummaryMessageWhenIdPresent() {
        when(scoreboardRepository.findByUserAndDate(any(), eq(YESTERDAY))).thenReturn(Optional.empty());
        Snowflake msgId = Snowflake.of("777");
        when(resultsChannelService.getPostedMessageId(ResultsChannelService.WIN_STREAK_SUMMARY_SLOT))
                .thenReturn(msgId);
        Message msg = mock(Message.class);
        when(client.getMessageById(any(), any())).thenReturn(Mono.just(msg));
        when(msg.edit(any(java.util.function.Consumer.class))).thenReturn(Mono.just(msg));

        job.run();

        verify(client, times(1)).getMessageById(Snowflake.of("999"), msgId);
    }

    @Test
    void skipsEditWhenNoExistingSummaryMessage() {
        when(scoreboardRepository.findByUserAndDate(any(), eq(YESTERDAY))).thenReturn(Optional.empty());
        when(resultsChannelService.getPostedMessageId(any())).thenReturn(null);

        job.run();

        verify(client, never()).getMessageById(any(), any());
    }
}
