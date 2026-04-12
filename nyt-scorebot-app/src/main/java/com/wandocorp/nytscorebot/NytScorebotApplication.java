package com.wandocorp.nytscorebot;

import discord4j.core.GatewayDiscordClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NytScorebotApplication {

    public static void main(String[] args) {
        SpringApplication.run(NytScorebotApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "discord.block-until-disconnect", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner blockUntilDisconnect(GatewayDiscordClient client) {
        return args -> client.onDisconnect().block();
    }
}
