package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.service.SetFlagOutcome;

/**
 * Shared helpers for flag-style slash commands (duo, check, lookups).
 */
final class FlagReplyHelper {

    private FlagReplyHelper() {}

    static String flagReplyFor(String setMsg, String clearedMsg, SetFlagOutcome outcome) {
        return switch (outcome) {
            case FLAG_SET              -> setMsg;
            case FLAG_CLEARED          -> clearedMsg;
            case NO_MAIN_CROSSWORD     -> BotText.MSG_NO_MAIN_CROSSWORD;
            case NO_SCOREBOARD_FOR_DATE -> BotText.MSG_NO_SCOREBOARD_TODAY;
            case USER_NOT_FOUND        -> BotText.MSG_USER_NOT_FOUND;
            case INVALID_VALUE         -> BotText.MSG_INVALID_VALUE;
        };
    }

    static void refreshMainCrossword(String discordUserId,
                                     DiscordChannelProperties channelProperties,
                                     StatusChannelService statusChannelService,
                                     ResultsChannelService resultsChannelService) {
        String playerName = channelProperties.getChannels().stream()
                .filter(c -> c.getUserId().equals(discordUserId))
                .map(DiscordChannelProperties.ChannelConfig::getName)
                .findFirst()
                .orElse(discordUserId);
        String contextMessage = String.format(BotText.STATUS_CONTEXT_FLAG_UPDATED, playerName, BotText.GAME_LABEL_MAIN);
        statusChannelService.refresh(contextMessage);
        resultsChannelService.refreshGame(BotText.GAME_LABEL_MAIN);
    }
}
