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
import java.util.List;

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

    public List<Scoreboard> getTodayScoreboards() {
        return scoreboardRepository.findAllByDateWithUser(puzzleCalendar.today());
    }

    @Transactional
    public MarkFinishedOutcome markFinished(String discordUserId, LocalDate date) {
        return userRepository.findByUserId(discordUserId)
                .map(user -> scoreboardRepository.findByUserAndDate(user, date)
                        .map(scoreboard -> {
                            if (scoreboard.isFinished()) {
                                return MarkFinishedOutcome.ALREADY_FINISHED;
                            }
                            scoreboard.setFinished(true);
                            scoreboardRepository.save(scoreboard);
                            log.info("Marked scoreboard finished for user {} on {}", discordUserId, date);
                            return MarkFinishedOutcome.MARKED_FINISHED;
                        })
                        .orElse(MarkFinishedOutcome.NO_SCOREBOARD_FOR_DATE))
                .orElse(MarkFinishedOutcome.USER_NOT_FOUND);
    }

    @Transactional
    public SaveOutcome saveResult(String channelId, String personName, String discordUserId, GameResult result) {
        LocalDate today = puzzleCalendar.today();

        // Validate puzzle number (numbered games only)
        SaveOutcome validation = validate(result);
        if (validation != SaveOutcome.SAVED) {
            log.info("Rejected {} result for {}: {}", result.getClass().getSimpleName(), personName, validation);
            return validation;
        }

        // Crosswords are stored against their own embedded date; all other results use today
        LocalDate resultDate = (result instanceof CrosswordResult r && r.getDate() != null)
                ? r.getDate() : today;

        User user = userRepository.findByChannelId(channelId)
                .orElseGet(() -> userRepository.save(new User(channelId, personName, discordUserId)));

        Scoreboard scoreboard = scoreboardRepository.findByUserAndDate(user, resultDate)
                .orElseGet(() -> scoreboardRepository.save(new Scoreboard(user, resultDate)));

        // Check for duplicate submission
        if (isAlreadySubmitted(scoreboard, result)) {
            log.info("Duplicate {} result for {} on {}", result.getClass().getSimpleName(), personName, today);
            return SaveOutcome.ALREADY_SUBMITTED;
        }

        applyResult(scoreboard, result);
        scoreboardRepository.save(scoreboard);

        // Auto-set finished flag if all 6 games are now present
        if (allGamesPresent(scoreboard)) {
            scoreboard.setFinished(true);
            scoreboardRepository.save(scoreboard);
            log.info("Auto-marked scoreboard finished for {} on {} (all 6 games present)", personName, resultDate);
        }

        log.info("Saved {} result for {} on {}", result.getClass().getSimpleName(), personName, today);
        return SaveOutcome.SAVED;
    }

    private SaveOutcome validate(GameResult result) {
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
        }
        return SaveOutcome.SAVED;
    }

    private boolean isAlreadySubmitted(Scoreboard scoreboard, GameResult result) {
        GameResult existing;
        if (result instanceof WordleResult) {
            existing = scoreboard.getWordleResult();
        } else if (result instanceof ConnectionsResult) {
            existing = scoreboard.getConnectionsResult();
        } else if (result instanceof StrandsResult) {
            existing = scoreboard.getStrandsResult();
        } else if (result instanceof CrosswordResult r) {
            existing = switch (r.getType()) {
                case MINI  -> scoreboard.getMiniCrosswordResult();
                case MIDI  -> scoreboard.getMidiCrosswordResult();
                case MAIN  -> scoreboard.getDailyCrosswordResult();
            };
        } else {
            return false;
        }
        return existing != null && existing.getRawContent() != null;
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
                case MAIN  -> scoreboard.setDailyCrosswordResult(r);
            }
        }
    }

    private boolean allGamesPresent(Scoreboard scoreboard) {
        return scoreboard.getWordleResult() != null
            && scoreboard.getConnectionsResult() != null
            && scoreboard.getStrandsResult() != null
            && scoreboard.getMiniCrosswordResult() != null
            && scoreboard.getMidiCrosswordResult() != null
            && scoreboard.getDailyCrosswordResult() != null;
    }
}
