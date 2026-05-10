package com.wandocorp.nytscorebot.repository;

import com.wandocorp.nytscorebot.entity.PersonalBest;
import com.wandocorp.nytscorebot.entity.PersonalBestSource;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalBestRepository extends JpaRepository<PersonalBest, Long> {

    Optional<PersonalBest> findByUserAndGameTypeAndDayOfWeek(User user, GameType gameType, String dayOfWeek);

    boolean existsBySource(PersonalBestSource source);
}
