package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StatusMessageBuilder;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class StatusChannelService {

    /** How long the ephemeral notification message lives before being deleted. */
    static final Duration NOTIFICATION_TTL = Duration.ofSeconds(10);
    /** Max attempts for the ephemeral-delete retry on transient Discord errors (rate limits, etc.). */
    private static final int DELETE_RETRY_ATTEMPTS = 3;
    private static final Duration DELETE_RETRY_BACKOFF = Duration.ofSeconds(2);

    private final GatewayDiscordClient client;
    private final DiscordChannelProperties channelProperties;
    private final ScoreboardService scoreboardService;
    private final MessageSlotWriter slotWriter;
    private final Scheduler scheduler;
    private final AtomicReference<Snowflake> lastMessageId = new AtomicReference<>();
    /**
     * Serialises consecutive board writes so a second {@link #refresh(String)} arriving before
     * the first has recorded its message id reuses (and edits) the same persistent board
     * instead of posting a duplicate.
     */
    private final AtomicReference<Mono<Snowflake>> boardChain = new AtomicReference<>();

    @Autowired
    public StatusChannelService(GatewayDiscordClient client,
                                DiscordChannelProperties channelProperties,
                                ScoreboardService scoreboardService,
                                MessageSlotWriter slotWriter) {
        this(client, channelProperties, scoreboardService, slotWriter, Schedulers.parallel());
    }

    /** Visible-for-tests constructor allowing scheduler override. */
    StatusChannelService(GatewayDiscordClient client,
                         DiscordChannelProperties channelProperties,
                         ScoreboardService scoreboardService,
                         MessageSlotWriter slotWriter,
                         Scheduler scheduler) {
        this.client = client;
        this.channelProperties = channelProperties;
        this.scoreboardService = scoreboardService;
        this.slotWriter = slotWriter;
        this.scheduler = scheduler;
    }

    public void refresh(String contextMessage) {
        String statusChannelId = channelProperties.getStatusChannelId();
        if (statusChannelId == null || statusChannelId.isBlank()) return;

        Snowflake channelSnowflake = Snowflake.of(statusChannelId);
        String table = buildStatusTable();

        writeBoard(channelSnowflake, table);
        sendEphemeralNotification(channelSnowflake, contextMessage);
    }

    /** Resets the persistent status board to the all-pending base state. Called by midnight job. */
    public void resetForNewDay() {
        String statusChannelId = channelProperties.getStatusChannelId();
        if (statusChannelId == null || statusChannelId.isBlank()) return;

        Snowflake existing = lastMessageId.get();
        if (existing == null) return; // No board to reset

        Snowflake channelSnowflake = Snowflake.of(statusChannelId);
        String emptyTable = renderTable(List.of());
        writeBoard(channelSnowflake, emptyTable);
    }

    private void writeBoard(Snowflake channelSnowflake, String content) {
        Mono<Snowflake> chained = boardChain.updateAndGet(prev -> {
            Mono<Snowflake> prevId = (prev == null)
                    ? Mono.justOrEmpty(lastMessageId.get())
                    : prev.onErrorResume(e -> Mono.empty());
            return prevId
                    .flatMap(id -> slotWriter.editOrPost(channelSnowflake, id, content))
                    .switchIfEmpty(Mono.defer(() -> slotWriter.editOrPost(channelSnowflake, null, content)))
                    .cache();
        });
        chained.subscribe(
                lastMessageId::set,
                error -> log.error("Error writing status board", error));
    }

    private void sendEphemeralNotification(Snowflake channelSnowflake, String contextMessage) {
        if (contextMessage == null || contextMessage.isBlank()) return;

        client.getChannelById(channelSnowflake)
                .cast(MessageChannel.class)
                .flatMap(ch -> ch.createMessage(contextMessage))
                .flatMap(msg -> Mono.delay(NOTIFICATION_TTL, scheduler).then(deleteMessage(msg)))
                .subscribe(
                        v -> {},
                        error -> log.warn("Error posting/deleting ephemeral notification", error));
    }

    private Mono<Void> deleteMessage(Message msg) {
        return msg.delete()
                .retryWhen(Retry.backoff(DELETE_RETRY_ATTEMPTS, DELETE_RETRY_BACKOFF)
                        .scheduler(scheduler)
                        .doBeforeRetry(rs -> log.debug(
                                "Retrying delete of ephemeral notification {} (attempt #{}): {}",
                                msg.getId().asString(), rs.totalRetries() + 1, rs.failure().toString())))
                .onErrorResume(e -> {
                    log.warn("Failed to delete ephemeral notification {} after {} retries",
                            msg.getId().asString(), DELETE_RETRY_ATTEMPTS, e);
                    return Mono.empty();
                });
    }

    String buildStatusTable() {
        List<Scoreboard> scoreboards = scoreboardService.getTodayScoreboards();
        return renderTable(scoreboards);
    }

    private String renderTable(List<Scoreboard> scoreboards) {
        List<String> playerNames = channelProperties.getChannels().stream()
                .map(DiscordChannelProperties.ChannelConfig::getName)
                .sorted()
                .toList();
        return new StatusMessageBuilder(scoreboards, playerNames).build();
    }

    /** Visible for testing: returns the currently-tracked persistent board message id. */
    Snowflake getLastMessageId() {
        return lastMessageId.get();
    }

    /** Visible for testing: pre-populates the tracked board id. */
    void setLastMessageId(Snowflake id) {
        lastMessageId.set(id);
    }
}
