package com.wandocorp.nytscorebot.repository;

import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.entity.WinStreak;
import com.wandocorp.nytscorebot.model.GameType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WinStreakRepository extends JpaRepository<WinStreak, Long> {

    Optional<WinStreak> findByUserAndGameType(User user, GameType gameType);

    List<WinStreak> findAllByUser(User user);
}
