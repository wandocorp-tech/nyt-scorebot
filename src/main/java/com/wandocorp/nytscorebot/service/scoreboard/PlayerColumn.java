package com.wandocorp.nytscorebot.service.scoreboard;

import java.util.List;

public record PlayerColumn(String playerName, String scoreLabel, List<String> gridRows) {}
