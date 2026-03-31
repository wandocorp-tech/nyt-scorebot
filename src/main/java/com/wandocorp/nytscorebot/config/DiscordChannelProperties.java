package com.wandocorp.nytscorebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import jakarta.annotation.PostConstruct;
import java.util.List;

@ConfigurationProperties(prefix = "discord")
public class DiscordChannelProperties {

    private List<ChannelConfig> channels = List.of();
    private String statusChannelId;

    @PostConstruct
    public void validate() {
        Assert.notEmpty(channels, "At least one discord.channels entry must be configured");
        channels.forEach(c -> {
            Assert.hasText(c.getId(), "Each discord.channels entry must have an 'id'");
            Assert.hasText(c.getName(), "Each discord.channels entry must have a 'name'");
            Assert.hasText(c.getUserId(), "Each discord.channels entry must have a 'user-id'");
        });
    }

    public List<ChannelConfig> getChannels() {
        return channels;
    }

    public void setChannels(List<ChannelConfig> channels) {
        this.channels = channels;
    }

    public String getStatusChannelId() { return statusChannelId; }
    public void setStatusChannelId(String statusChannelId) { this.statusChannelId = statusChannelId; }

    private String resultsChannelId;

    public String getResultsChannelId() { return resultsChannelId; }
    public void setResultsChannelId(String resultsChannelId) { this.resultsChannelId = resultsChannelId; }

    public static class ChannelConfig {
        private String id;
        private String name;
        private String userId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
}
