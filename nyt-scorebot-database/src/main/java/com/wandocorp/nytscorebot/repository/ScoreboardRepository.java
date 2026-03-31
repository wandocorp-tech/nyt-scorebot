package com.wandocorp.nytscorebot.repository;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScoreboardRepository extends JpaRepository<Scoreboard, Long> {
    Optional<Scoreboard> findByUserAndDate(User user, LocalDate date);

    @Query("SELECT s FROM Scoreboard s JOIN FETCH s.user WHERE s.date = :date")
    List<Scoreboard> findAllByDateWithUser(@Param("date") LocalDate date);
}
