package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.*;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ScoreboardService {

    private static final Logger log = LoggerFactory.getLogger(ScoreboardService.class);

    private final UserRepository userRepository;
    private final ScoreboardRepository scoreboardRepository;

    public ScoreboardService(UserRepository userRepository, ScoreboardRepository scoreboardRepository) {
        this.userRepository = userRepository;
        this.scoreboardRepository = scoreboardRepository;
    }

    @Transactional
    public void saveResult(String channelId, String personName, String discordUserId, GameResult result) {
        User user = userRepository.findByChannelId(channelId)
                .orElseGet(() -> userRepository.save(new User(channelId, personName, discordUserId)));

        LocalDate date = deriveDate(result);

        Scoreboard scoreboard = scoreboardRepository.findByUserAndDate(user, date)
                .orElseGet(() -> scoreboardRepository.save(new Scoreboard(user, date)));

        applyResult(scoreboard, result);
        scoreboardRepository.save(scoreboard);

        log.info("Saved {} result for {} on {}", result.getClass().getSimpleName(), personName, date);
    }

    private LocalDate deriveDate(GameResult result) {
        if (result instanceof CrosswordResult cw && cw.getDate() != null) {
            return cw.getDate();
        }
        return LocalDate.now();
    }

    private void applyResult(Scoreboard scoreboard, GameResult result) {
        if (result instanceof WordleResult r) {
            scoreboard.setWordleResult(r);
        } else if (result instanceof ConnectionsResult r) {
            scoreboard.setConnectionsResult(r);
        } else if (result instanceof StrandsResult r) {
            scoreboard.setStrandsResult(r);
        } else if (result instanceof CrosswordResult r) {
            switch (r.getType()) {
                case MINI  -> scoreboard.setMiniCrosswordResult(r);
                case MIDI  -> scoreboard.setMidiCrosswordResult(r);
                case DAILY -> scoreboard.setDailyCrosswordResult(r);
            }
        } else {
            log.warn("Unknown GameResult type: {}", result.getClass().getName());
        }
    }
}
