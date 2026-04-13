package com.wandocorp.nytscorebot.repository;

import com.wandocorp.nytscorebot.entity.Streak;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StreakRepository extends JpaRepository<Streak, Long> {

    Optional<Streak> findByUserAndGameType(User user, GameType gameType);

    List<Streak> findAllByUser(User user);
}
