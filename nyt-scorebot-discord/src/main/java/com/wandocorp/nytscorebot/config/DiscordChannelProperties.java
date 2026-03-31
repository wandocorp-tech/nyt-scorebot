package com.wandocorp.nytscorebot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "discord")
public class DiscordChannelProperties {

    private List<ChannelConfig> channels = List.of();
    private String statusChannelId;
    private String resultsChannelId;

    @PostConstruct
    public void validate() {
        Assert.notEmpty(channels, "At least one discord.channels entry must be configured");
        channels.forEach(c -> {
            Assert.hasText(c.getId(), "Each discord.channels entry must have an 'id'");
            Assert.hasText(c.getName(), "Each discord.channels entry must have a 'name'");
            Assert.hasText(c.getUserId(), "Each discord.channels entry must have a 'user-id'");
        });
    }

    @Data
    public static class ChannelConfig {
        private String id;
        private String name;
        private String userId;
    }
}
