package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.*;
import com.wandocorp.nytscorebot.repository.PersonalBestRepository;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.scoreboard.CrosswordPbLookup;
import com.wandocorp.nytscorebot.service.scoreboard.CrosswordPbStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Service
public class ScoreboardService {

    private final UserRepository userRepository;
    private final ScoreboardRepository scoreboardRepository;
    private final PuzzleCalendar puzzleCalendar;
    private final StreakService streakService;
    private final PersonalBestService personalBestService;
    private final PersonalBestRepository personalBestRepository;

    public List<Scoreboard> getTodayScoreboards() {
        return scoreboardRepository.findAllByDateWithUser(puzzleCalendar.today());
    }

    public List<Scoreboard> getScoreboardsForDate(LocalDate date) {
        return scoreboardRepository.findAllByDateWithUser(date);
    }

    public boolean areBothPlayersFinishedToday() {
        List<Scoreboard> scoreboards = getTodayScoreboards();
        return scoreboards.size() >= 2 && scoreboards.stream().allMatch(Scoreboard::isFinished);
    }

    @Transactional
    public MarkFinishedOutcome markFinished(String discordUserId, LocalDate date) {
        return userRepository.findByDiscordUserId(discordUserId)
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
    public SaveResult saveResult(String channelId, String personName, String discordUserId, GameResult result) {
        SaveOutcome validation = validate(result);
        if (validation != SaveOutcome.SAVED) {
            log.info("Rejected {} result for {}: {}", result.getClass().getSimpleName(), personName, validation);
            return SaveResult.of(validation);
        }

        LocalDate resultDate = resolveDate(result);
        User user = findOrCreateUser(channelId, personName, discordUserId);
        Scoreboard scoreboard = findOrCreateScoreboard(user, resultDate);

        if (scoreboard.isFinished()) {
            log.info("Rejected {} result for {} on {}: scoreboard already finished",
                    result.getClass().getSimpleName(), personName, resultDate);
            return SaveResult.of(SaveOutcome.ALREADY_FINISHED);
        }

        if (isAlreadySubmitted(scoreboard, result)) {
            log.info("Duplicate {} result for {} on {}", result.getClass().getSimpleName(), personName, resultDate);
            return SaveResult.of(SaveOutcome.ALREADY_SUBMITTED);
        }

        applyResult(scoreboard, result);
        scoreboardRepository.save(scoreboard);
        streakService.updateStreak(user, result);
        autoFinishIfComplete(scoreboard, personName, resultDate);

        PbUpdateOutcome pb = recomputePbIfCrossword(user, result, resultDate);

        log.info("Saved {} result for {} on {}", result.getClass().getSimpleName(), personName, resultDate);
        return SaveResult.saved(pb);
    }

    private PbUpdateOutcome recomputePbIfCrossword(User user, GameResult result, LocalDate resultDate) {
        return switch (result.gameType()) {
            case MINI_CROSSWORD, MIDI_CROSSWORD -> {
                Integer secs = ((CrosswordResult) result).getTotalSeconds();
                yield secs == null
                        ? PbUpdateOutcome.NO_CHANGE
                        : personalBestService.recompute(user, result.gameType(), resultDate, secs, true);
            }
            case MAIN_CROSSWORD -> {
                MainCrosswordResult main = (MainCrosswordResult) result;
                Integer secs = main.getTotalSeconds();
                yield secs == null
                        ? PbUpdateOutcome.NO_CHANGE
                        : personalBestService.recompute(user, GameType.MAIN_CROSSWORD, resultDate, secs, !main.isAssisted());
            }
            default -> PbUpdateOutcome.NO_CHANGE;
        };
    }

    private LocalDate resolveDate(GameResult result) {
        LocalDate embedded = result.resultDate();
        return embedded != null ? embedded : puzzleCalendar.today();
    }

    private User findOrCreateUser(String channelId, String personName, String discordUserId) {
        return userRepository.findByChannelId(channelId)
                .orElseGet(() -> userRepository.save(new User(channelId, personName, discordUserId)));
    }

    private Scoreboard findOrCreateScoreboard(User user, LocalDate date) {
        return scoreboardRepository.findByUserAndDate(user, date)
                .orElseGet(() -> scoreboardRepository.save(new Scoreboard(user, date)));
    }

    private void autoFinishIfComplete(Scoreboard scoreboard, String personName, LocalDate date) {
        if (allGamesPresent(scoreboard)) {
            scoreboard.setFinished(true);
            scoreboardRepository.save(scoreboard);
            log.info("Auto-marked scoreboard finished for {} on {} (all 6 games present)", personName, date);
        }
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
        return scoreboard.hasResult(result.gameType());
    }

    private void applyResult(Scoreboard scoreboard, GameResult result) {
        scoreboard.addResult(result);
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
        return userRepository.findByDiscordUserId(discordUserId)
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
        return scoreboard.getGameResults().size() >= GameType.values().length;
    }

    /**
     * Builds a {@link CrosswordPbLookup} that resolves prior-clean averages and current PBs
     * for the two named players against the given date. The lookup pre-fetches per-player /
     * per-(game, dow) data once and serves all subsequent queries from an in-memory map.
     *
     * <p>Average is the arithmetic mean of clean total-seconds for that player + game (+ dow
     * for Main) on dates strictly before {@code date}. Mini and Midi have no DoW partition.
     * Returns an {@link CrosswordPbLookup#EMPTY} fallback when either name has no user row.
     */
    public CrosswordPbLookup buildCrosswordPbLookup(String name1, String name2, LocalDate date) {
        Map<String, Map<GameType, CrosswordPbStats>> data = new HashMap<>();
        for (String name : List.of(name1, name2)) {
            if (name == null) continue;
            Optional<User> userOpt = userRepository.findAll().stream()
                    .filter(u -> name.equals(u.getName())).findFirst();
            if (userOpt.isEmpty()) continue;
            User user = userOpt.get();
            Map<GameType, CrosswordPbStats> perGame = new HashMap<>();
            perGame.put(GameType.MINI_CROSSWORD, statsFor(user, GameType.MINI_CROSSWORD, null, date));
            perGame.put(GameType.MIDI_CROSSWORD, statsFor(user, GameType.MIDI_CROSSWORD, null, date));
            perGame.put(GameType.MAIN_CROSSWORD, statsFor(user, GameType.MAIN_CROSSWORD, date.getDayOfWeek(), date));
            data.put(name, perGame);
        }
        return (playerName, gameType) -> data.getOrDefault(playerName, Map.of())
                .getOrDefault(gameType, CrosswordPbStats.EMPTY);
    }

    private CrosswordPbStats statsFor(User user, GameType gameType, java.time.DayOfWeek dow, LocalDate date) {
        List<Integer> cleanSecs = (gameType == GameType.MAIN_CROSSWORD)
                ? scoreboardRepository.findCleanMainSecondsBeforeDate(user, dow, date)
                : scoreboardRepository.findCleanSecondsBeforeDate(user, gameType, date);
        OptionalInt avg = cleanSecs.isEmpty()
                ? OptionalInt.empty()
                : OptionalInt.of((int) Math.round(cleanSecs.stream().mapToInt(Integer::intValue).average().orElse(0)));
        String dowKey = (dow == null) ? PbDayOfWeek.ALL_DAYS_SENTINEL : dow.name();
        OptionalInt pb = personalBestRepository
                .findByUserAndGameTypeAndDayOfWeek(user, gameType, dowKey)
                .map(p -> OptionalInt.of(p.getBestSeconds()))
                .orElse(OptionalInt.empty());
        return new CrosswordPbStats(avg, pb);
    }
}
