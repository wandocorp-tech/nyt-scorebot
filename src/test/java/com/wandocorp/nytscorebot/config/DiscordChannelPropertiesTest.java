package com.wandocorp.nytscorebot.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DiscordChannelPropertiesTest {

    private DiscordChannelProperties buildWithChannels(List<DiscordChannelProperties.ChannelConfig> channels) {
        DiscordChannelProperties props = new DiscordChannelProperties();
        props.setChannels(channels);
        return props;
    }

    private DiscordChannelProperties.ChannelConfig channel(String id, String name, String userId) {
        DiscordChannelProperties.ChannelConfig c = new DiscordChannelProperties.ChannelConfig();
        c.setId(id);
        c.setName(name);
        c.setUserId(userId);
        return c;
    }

    @Test
    void validConfigurationPassesValidation() {
        DiscordChannelProperties props = buildWithChannels(
                List.of(channel("123", "Alice", "456")));
        assertThatNoException().isThrownBy(props::validate);
    }

    @Test
    void emptyChannelListFailsValidation() {
        DiscordChannelProperties props = buildWithChannels(List.of());
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one discord.channels entry must be configured");
    }

    @Test
    void missingIdFailsValidation() {
        DiscordChannelProperties props = buildWithChannels(
                List.of(channel("", "Alice", "456")));
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have an 'id'");
    }

    @Test
    void missingNameFailsValidation() {
        DiscordChannelProperties props = buildWithChannels(
                List.of(channel("123", "", "456")));
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have a 'name'");
    }

    @Test
    void missingUserIdFailsValidation() {
        DiscordChannelProperties props = buildWithChannels(
                List.of(channel("123", "Alice", "")));
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have a 'user-id'");
    }

    @Test
    void channelConfigGettersReturnSetValues() {
        DiscordChannelProperties.ChannelConfig c = channel("id1", "Bob", "uid1");
        org.assertj.core.api.Assertions.assertThat(c.getId()).isEqualTo("id1");
        org.assertj.core.api.Assertions.assertThat(c.getName()).isEqualTo("Bob");
        org.assertj.core.api.Assertions.assertThat(c.getUserId()).isEqualTo("uid1");
    }

    @Test
    void channelsGetterReturnsSetList() {
        List<DiscordChannelProperties.ChannelConfig> channels = List.of(channel("1", "A", "u1"));
        DiscordChannelProperties props = buildWithChannels(channels);
        org.assertj.core.api.Assertions.assertThat(props.getChannels()).isEqualTo(channels);
    }
}
