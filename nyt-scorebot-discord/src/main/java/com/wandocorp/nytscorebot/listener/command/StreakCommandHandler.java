package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.service.StreakService;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class StreakCommandHandler implements SlashCommandHandler {

    private final StreakService streakService;
    private final UserRepository userRepository;
    private final DiscordChannelProperties channelProperties;
    private final ResultsChannelService resultsChannelService;

    @Override
    public String commandName() {
        return BotText.CMD_STREAK;
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /streak from Discord user {}", discordUserId);

        String game = event.getOption(BotText.CMD_STREAK_GAME_OPTION)
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");

        long streakValue = event.getOption(BotText.CMD_STREAK_VALUE_OPTION)
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .orElse(0L);

        int streakInt;
        try {
            streakInt = Math.toIntExact(streakValue);
        } catch (ArithmeticException e) {
            return event.reply().withEphemeral(true)
                    .withContent(BotText.MSG_INVALID_VALUE);
        }
        if (streakInt < 0) {
            return event.reply().withEphemeral(true)
                    .withContent(BotText.MSG_INVALID_VALUE);
        }

        Optional<User> userOpt = channelProperties.getChannels().stream()
                .filter(c -> discordUserId.equals(c.getUserId()))
                .findFirst()
                .flatMap(c -> userRepository.findByChannelId(c.getId()))
                .or(() -> userRepository.findByDiscordUserId(discordUserId));
        if (userOpt.isEmpty()) {
            return event.reply().withEphemeral(true)
                    .withContent(BotText.MSG_USER_NOT_FOUND);
        }

        streakService.setStreak(userOpt.get(), GameType.fromLabel(game), streakInt);
        resultsChannelService.refresh();

        String reply = String.format(BotText.MSG_STREAK_SET, game, streakValue);
        return event.reply().withEphemeral(true).withContent(reply);
    }
}
