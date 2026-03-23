package com.wandocorp.nytscorebot.repository;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ScoreboardRepository extends JpaRepository<Scoreboard, Long> {
    Optional<Scoreboard> findByUserAndDate(User user, LocalDate date);
}
