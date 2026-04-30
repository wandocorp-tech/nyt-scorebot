package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.WinStreakService;
import com.wandocorp.nytscorebot.service.WinStreakSummaryBuilder;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finalizes crossword win streaks at midnight in the puzzle timezone.
 *
 * <p>For each crossword game, classifies yesterday's pair as both-submitted,
 * one-submitted, or neither-submitted and applies forfeit rules via
 * {@link WinStreakService#applyForfeit}. After processing, edits the previous
 * day's win streak summary message in place to show the finalized values.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class WinStreakMidnightJob {

    private static final List<GameType> CROSSWORDS = List.of(
            GameType.MINI_CROSSWORD, GameType.MIDI_CROSSWORD, GameType.MAIN_CROSSWORD);

    private final WinStreakService winStreakService;
    private final ScoreboardRepository scoreboardRepository;
    private final UserRepository userRepository;
    private final DiscordChannelProperties channelProperties;
    private final PuzzleCalendar puzzleCalendar;
    private final ResultsChannelService resultsChannelService;
    private final GatewayDiscordClient client;

    /**
     * Runs at 00:00 in the configured puzzle timezone (defaulting to Europe/London).
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "${discord.timezone:Europe/London}")
    public void finalizeYesterday() {
        run();
    }

    /** Visible for testing — performs the rollover synchronously. */
    void run() {
        LocalDate yesterday = puzzleCalendar.today().minusDays(1);

        List<DiscordChannelProperties.ChannelConfig> channels = channelProperties.getChannels();
        if (channels.size() < 2) {
            return;
        }

        Optional<User> u1Opt = userRepository.findByDiscordUserId(channels.get(0).getUserId());
        Optional<User> u2Opt = userRepository.findByDiscordUserId(channels.get(1).getUserId());
        if (u1Opt.isEmpty() || u2Opt.isEmpty()) {
            log.debug("Skipping midnight finalize — players not yet registered");
            return;
        }
        User u1 = u1Opt.get();
        User u2 = u2Opt.get();

        Optional<Scoreboard> sb1 = scoreboardRepository.findByUserAndDate(u1, yesterday);
        Optional<Scoreboard> sb2 = scoreboardRepository.findByUserAndDate(u2, yesterday);

        for (GameType gameType : CROSSWORDS) {
            boolean submitted1 = sb1.map(s -> s.hasResult(gameType)).orElse(false);
            boolean submitted2 = sb2.map(s -> s.hasResult(gameType)).orElse(false);
            winStreakService.applyForfeit(gameType, u1, submitted1, u2, submitted2, yesterday);
        }

        editSummaryIfPresent(u1, channels.get(0).getName(), u2, channels.get(1).getName());
    }

    private void editSummaryIfPresent(User u1, String name1, User u2, String name2) {
        Snowflake messageId = resultsChannelService.getPostedMessageId(
                ResultsChannelService.WIN_STREAK_SUMMARY_SLOT);
        String resultsChannelId = channelProperties.getResultsChannelId();
        if (messageId == null || resultsChannelId == null || resultsChannelId.isBlank()) {
            return;
        }

        Map<User, Map<GameType, Integer>> winStreaks = new HashMap<>();
        winStreaks.put(u1, winStreakService.getStreaks(u1));
        winStreaks.put(u2, winStreakService.getStreaks(u2));
        String content = WinStreakSummaryBuilder.build(u1, name1, u2, name2, winStreaks);

        Snowflake channelSnowflake = Snowflake.of(resultsChannelId);
        client.getMessageById(channelSnowflake, messageId)
                .flatMap(msg -> msg.edit(spec -> spec.setContent(content)))
                .onErrorResume(e -> {
                    log.error("Failed to edit win streak summary at midnight rollover", e);
                    return Mono.empty();
                })
                .subscribe();
    }
}
