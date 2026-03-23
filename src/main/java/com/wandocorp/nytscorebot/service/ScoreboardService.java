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
    private final PuzzleCalendar puzzleCalendar;

    public ScoreboardService(UserRepository userRepository,
                             ScoreboardRepository scoreboardRepository,
                             PuzzleCalendar puzzleCalendar) {
        this.userRepository = userRepository;
        this.scoreboardRepository = scoreboardRepository;
        this.puzzleCalendar = puzzleCalendar;
    }

    @Transactional
    public SaveOutcome saveResult(String channelId, String personName, String discordUserId, GameResult result) {
        LocalDate today = puzzleCalendar.today();

        // Validate puzzle number / date
        SaveOutcome validation = validate(result, today);
        if (validation != SaveOutcome.SAVED) {
            log.info("Rejected {} result for {}: {}", result.getClass().getSimpleName(), personName, validation);
            return validation;
        }

        User user = userRepository.findByChannelId(channelId)
                .orElseGet(() -> userRepository.save(new User(channelId, personName, discordUserId)));

        Scoreboard scoreboard = scoreboardRepository.findByUserAndDate(user, today)
                .orElseGet(() -> scoreboardRepository.save(new Scoreboard(user, today)));

        // Check for duplicate submission
        if (isAlreadySubmitted(scoreboard, result)) {
            log.info("Duplicate {} result for {} on {}", result.getClass().getSimpleName(), personName, today);
            return SaveOutcome.ALREADY_SUBMITTED;
        }

        applyResult(scoreboard, result);
        scoreboardRepository.save(scoreboard);

        log.info("Saved {} result for {} on {}", result.getClass().getSimpleName(), personName, today);
        return SaveOutcome.SAVED;
    }

    private SaveOutcome validate(GameResult result, LocalDate today) {
        if (result instanceof WordleResult r) {
            if (r.getPuzzleNumber() != puzzleCalendar.expectedWordle()) {
                return SaveOutcome.WRONG_PUZZLE_NUMBER;
            }
        } else if (result instanceof ConnectionsResult r) {
            if (r.getPuzzleNumber() != puzzleCalendar.expectedConnections()) {
                return SaveOutcome.WRONG_PUZZLE_NUMBER;
            }
        } else if (result instanceof StrandsResult r) {
            if (r.getPuzzleNumber() != puzzleCalendar.expectedStrands()) {
                return SaveOutcome.WRONG_PUZZLE_NUMBER;
            }
        } else if (result instanceof CrosswordResult r) {
            if (r.getDate() != null && !r.getDate().equals(today)) {
                return SaveOutcome.WRONG_DATE;
            }
        }
        return SaveOutcome.SAVED;
    }

    private boolean isAlreadySubmitted(Scoreboard scoreboard, GameResult result) {
        if (result instanceof WordleResult) {
            return scoreboard.getWordleResult() != null;
        } else if (result instanceof ConnectionsResult) {
            return scoreboard.getConnectionsResult() != null;
        } else if (result instanceof StrandsResult) {
            return scoreboard.getStrandsResult() != null;
        } else if (result instanceof CrosswordResult r) {
            return switch (r.getType()) {
                case MINI  -> scoreboard.getMiniCrosswordResult() != null;
                case MIDI  -> scoreboard.getMidiCrosswordResult() != null;
                case DAILY -> scoreboard.getDailyCrosswordResult() != null;
            };
        }
        return false;
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
        }
    }
}
