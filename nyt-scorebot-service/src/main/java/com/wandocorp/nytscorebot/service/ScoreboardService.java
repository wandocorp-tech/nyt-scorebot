package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.*;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Service
public class ScoreboardService {

    private final UserRepository userRepository;
    private final ScoreboardRepository scoreboardRepository;
    private final PuzzleCalendar puzzleCalendar;
    private final StreakService streakService;

    public List<Scoreboard> getTodayScoreboards() {
        return scoreboardRepository.findAllByDateWithUser(puzzleCalendar.today());
    }

    public boolean areBothPlayersFinishedToday() {
        List<Scoreboard> scoreboards = getTodayScoreboards();
        return scoreboards.size() >= 2 && scoreboards.stream().allMatch(Scoreboard::isFinished);
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
        LocalDate resultDate = result.resultDate() != null ? result.resultDate() : today;

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

        // Update streak within the same transaction
        streakService.updateStreak(user, result);

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
        return result.puzzleNumber()
                .stream()
                .mapToObj(pn -> {
                    int expected = switch (result.gameType()) {
                        case WORDLE -> puzzleCalendar.expectedWordle();
                        case CONNECTIONS -> puzzleCalendar.expectedConnections();
                        case STRANDS -> puzzleCalendar.expectedStrands();
                        default -> pn; // crosswords don't have puzzle numbers
                    };
                    return pn != expected ? SaveOutcome.WRONG_PUZZLE_NUMBER : SaveOutcome.SAVED;
                })
                .findFirst()
                .orElse(SaveOutcome.SAVED);
    }

    private boolean isAlreadySubmitted(Scoreboard scoreboard, GameResult result) {
        GameResult existing = switch (result.gameType()) {
            case WORDLE -> scoreboard.getWordleResult();
            case CONNECTIONS -> scoreboard.getConnectionsResult();
            case STRANDS -> scoreboard.getStrandsResult();
            case MINI_CROSSWORD -> scoreboard.getMiniCrosswordResult();
            case MIDI_CROSSWORD -> scoreboard.getMidiCrosswordResult();
            case MAIN_CROSSWORD -> scoreboard.getMainCrosswordResult();
        };
        return existing != null && existing.getRawContent() != null;
    }

    private void applyResult(Scoreboard scoreboard, GameResult result) {
        switch (result.gameType()) {
            case WORDLE -> scoreboard.setWordleResult((WordleResult) result);
            case CONNECTIONS -> scoreboard.setConnectionsResult((ConnectionsResult) result);
            case STRANDS -> scoreboard.setStrandsResult((StrandsResult) result);
            case MINI_CROSSWORD -> scoreboard.setMiniCrosswordResult((CrosswordResult) result);
            case MIDI_CROSSWORD -> scoreboard.setMidiCrosswordResult((CrosswordResult) result);
            case MAIN_CROSSWORD -> scoreboard.setMainCrosswordResult((MainCrosswordResult) result);
        }
    }

    @Transactional
    public SetFlagOutcome toggleDuo(String discordUserId, LocalDate date) {
        return withMainCrossword(discordUserId, date, main -> {
            boolean newValue = !Boolean.TRUE.equals(main.getDuo());
            main.setDuo(newValue);
            return newValue ? SetFlagOutcome.FLAG_SET : SetFlagOutcome.FLAG_CLEARED;
        });
    }

    @Transactional
    public SetFlagOutcome setLookups(String discordUserId, LocalDate date, int count) {
        if (count < 0) return SetFlagOutcome.INVALID_VALUE;
        return withMainCrossword(discordUserId, date, main -> {
            main.setLookups(count == 0 ? null : count);
            return count == 0 ? SetFlagOutcome.FLAG_CLEARED : SetFlagOutcome.FLAG_SET;
        });
    }

    @Transactional
    public SetFlagOutcome toggleCheck(String discordUserId, LocalDate date) {
        return withMainCrossword(discordUserId, date, main -> {
            boolean newValue = !Boolean.TRUE.equals(main.getCheckUsed());
            main.setCheckUsed(newValue);
            return newValue ? SetFlagOutcome.FLAG_SET : SetFlagOutcome.FLAG_CLEARED;
        });
    }

    private SetFlagOutcome withMainCrossword(String discordUserId, LocalDate date,
                                              Function<MainCrosswordResult, SetFlagOutcome> action) {
        return userRepository.findByUserId(discordUserId)
                .map(user -> scoreboardRepository.findByUserAndDate(user, date)
                        .map(scoreboard -> {
                            MainCrosswordResult main = scoreboard.getMainCrosswordResult();
                            if (main == null || main.getRawContent() == null) {
                                return SetFlagOutcome.NO_MAIN_CROSSWORD;
                            }
                            SetFlagOutcome outcome = action.apply(main);
                            scoreboardRepository.save(scoreboard);
                            return outcome;
                        })
                        .orElse(SetFlagOutcome.NO_SCOREBOARD_FOR_DATE))
                .orElse(SetFlagOutcome.USER_NOT_FOUND);
    }

    private boolean allGamesPresent(Scoreboard scoreboard) {
        return scoreboard.getWordleResult() != null
            && scoreboard.getConnectionsResult() != null
            && scoreboard.getStrandsResult() != null
            && scoreboard.getMiniCrosswordResult() != null
            && scoreboard.getMidiCrosswordResult() != null
            && scoreboard.getMainCrosswordResult() != null;
    }
}
