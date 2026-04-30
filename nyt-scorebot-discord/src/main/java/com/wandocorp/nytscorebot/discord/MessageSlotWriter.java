package com.wandocorp.nytscorebot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Edit-or-post helper for long-lived bot messages (scoreboards, win-streak summary,
 * status board). When an {@code existingId} is supplied, attempts to edit that message
 * in place; on failure (or when no id is supplied), falls back to posting a fresh
 * message. Returns the resulting message's id so callers can update their slot tracking.
 *
 * <p>The returned {@link Mono} is cold — callers are responsible for subscribing
 * (typically fire-and-forget with error logging).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSlotWriter {

    private final GatewayDiscordClient client;

    /**
     * Edits the message identified by {@code existingId} with {@code content} when
     * {@code existingId != null}. If the edit fails (e.g. the message no longer
     * exists) or no id is supplied, posts a new message and returns its id.
     */
    public Mono<Snowflake> editOrPost(Snowflake channelId, @Nullable Snowflake existingId, String content) {
        Mono<Snowflake> editAttempt = existingId == null
                ? Mono.empty()
                : client.getMessageById(channelId, existingId)
                        .flatMap(msg -> msg.edit(spec -> spec.setContent(content)))
                        .map(Message::getId)
                        .onErrorResume(e -> {
                            log.warn("Edit failed for message {} in channel {}, falling back to fresh post: {}",
                                    existingId.asString(), channelId.asString(), e.toString());
                            return Mono.empty();
                        });

        return editAttempt.switchIfEmpty(Mono.defer(() -> postFresh(channelId, content)));
    }

    private Mono<Snowflake> postFresh(Snowflake channelId, String content) {
        return client.getChannelById(channelId)
                .cast(MessageChannel.class)
                .flatMap(ch -> ch.createMessage(content))
                .map(Message::getId);
    }
}
