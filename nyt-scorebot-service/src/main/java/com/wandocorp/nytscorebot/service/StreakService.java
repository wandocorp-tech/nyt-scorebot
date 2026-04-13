package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Streak;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.repository.StreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class StreakService {

    private final StreakRepository streakRepository;
    private final PuzzleCalendar puzzleCalendar;

    @Transactional
    public void updateStreak(User user, GameResult result) {
        GameType gameType = resolveGameType(result);
        if (gameType == null) return;

        boolean success = isSuccess(result);
        LocalDate today = puzzleCalendar.today();

        var existingStreak = streakRepository.findByUserAndGameType(user, gameType);
        if (existingStreak.isEmpty()) {
            int initialValue = success ? 1 : 0;
            Streak streak = new Streak(user, gameType, initialValue, today);
            streakRepository.save(streak);
            return;
        }

        Streak streak = existingStreak.get();
        long daysSinceLastUpdate = ChronoUnit.DAYS.between(streak.getLastUpdatedDate(), today);

        if (daysSinceLastUpdate <= 0) {
            // Already updated today — no-op
            return;
        } else if (daysSinceLastUpdate == 1) {
            // Consecutive day
            if (success) {
                streak.setCurrentStreak(streak.getCurrentStreak() + 1);
            } else {
                streak.setCurrentStreak(0);
            }
        } else {
            // Gap detected — reset first, then apply
            if (success) {
                streak.setCurrentStreak(1);
            } else {
                streak.setCurrentStreak(0);
            }
        }

        streak.setLastUpdatedDate(today);
        streakRepository.save(streak);
    }

    @Transactional
    public void setStreak(User user, GameType gameType, int value) {
        LocalDate today = puzzleCalendar.today();

        Streak streak = streakRepository.findByUserAndGameType(user, gameType)
                .orElseGet(() -> new Streak(user, gameType, 0, today));

        streak.setCurrentStreak(value);
        streak.setLastUpdatedDate(today);
        streakRepository.save(streak);
    }

    public Map<GameType, Integer> getStreaks(User user) {
        return streakRepository.findAllByUser(user).stream()
                .collect(Collectors.toMap(Streak::getGameType, Streak::getCurrentStreak));
    }

    public int getStreak(User user, GameType gameType) {
        return streakRepository.findByUserAndGameType(user, gameType)
                .map(Streak::getCurrentStreak)
                .orElse(0);
    }

    static GameType resolveGameType(GameResult result) {
        return switch (result.gameType()) {
            case WORDLE -> GameType.WORDLE;
            case CONNECTIONS -> GameType.CONNECTIONS;
            case STRANDS -> GameType.STRANDS;
            default -> null; // Crosswords are not streak-tracked
        };
    }

    static boolean isSuccess(GameResult result) {
        return result.isSuccess();
    }
}
