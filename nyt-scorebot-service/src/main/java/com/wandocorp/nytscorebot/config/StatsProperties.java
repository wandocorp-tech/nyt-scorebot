package com.wandocorp.nytscorebot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@Data
@ConfigurationProperties(prefix = "stats")
public class StatsProperties {

    private LocalDate anchorDate;

    public boolean isEnabled() {
        return anchorDate != null;
    }
}
