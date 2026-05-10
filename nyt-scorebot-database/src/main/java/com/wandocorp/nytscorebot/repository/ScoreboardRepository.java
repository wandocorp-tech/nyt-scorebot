package com.wandocorp.nytscorebot.repository;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface ScoreboardRepository extends JpaRepository<Scoreboard, Long> {
    Optional<Scoreboard> findByUserAndDate(User user, LocalDate date);

    @Query("SELECT s FROM Scoreboard s JOIN FETCH s.user WHERE s.date = :date")
    List<Scoreboard> findAllByDateWithUser(@Param("date") LocalDate date);

    @Query("SELECT s FROM Scoreboard s JOIN FETCH s.user WHERE s.date BETWEEN :from AND :to")
    List<Scoreboard> findAllByDateBetweenWithUser(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT MIN(s.date) FROM Scoreboard s")
    Optional<LocalDate> findEarliestDate();

    @Query("SELECT s FROM Scoreboard s WHERE s.user = :user AND s.date < :today")
    List<Scoreboard> findByUserAndDateBefore(@Param("user") User user, @Param("today") LocalDate today);

    /**
     * Returns total-seconds values from clean Main crossword results for the user, strictly
     * before {@code today}, matching the given day-of-week. "Clean" means the result is not
     * assisted ({@link MainCrosswordResult#isAssisted()} returns {@code false}).
     */
    default List<Integer> findCleanMainSecondsBeforeDate(User user, DayOfWeek dow, LocalDate today) {
        return findByUserAndDateBefore(user, today).stream()
                .filter(s -> s.getDate().getDayOfWeek() == dow)
                .map(Scoreboard::getMainCrosswordResult)
                .filter(Objects::nonNull)
                .filter(r -> !r.isAssisted())
                .map(MainCrosswordResult::getTotalSeconds)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Returns total-seconds values from Mini or Midi crossword results for the user, strictly
     * before {@code today}. Mini and Midi have no assistance flags, so all completed results
     * are clean.
     *
     * @throws IllegalArgumentException if {@code gameType} is not Mini or Midi.
     */
    default List<Integer> findCleanSecondsBeforeDate(User user, GameType gameType, LocalDate today) {
        if (gameType != GameType.MINI_CROSSWORD && gameType != GameType.MIDI_CROSSWORD) {
            throw new IllegalArgumentException(
                    "findCleanSecondsBeforeDate only supports Mini/Midi; use findCleanMainSecondsBeforeDate for Main. Got: " + gameType);
        }
        return findByUserAndDateBefore(user, today).stream()
                .map(s -> selectCrossword(s, gameType))
                .filter(Objects::nonNull)
                .map(CrosswordResult::getTotalSeconds)
                .filter(Objects::nonNull)
                .toList();
    }

    private static CrosswordResult selectCrossword(Scoreboard s, GameType gameType) {
        return switch (gameType) {
            case MINI_CROSSWORD -> s.getMiniCrosswordResult();
            case MIDI_CROSSWORD -> s.getMidiCrosswordResult();
            default -> null; // unreachable; guarded above
        };
    }
}

