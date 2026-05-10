package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.entity.PersonalBestSource;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.repository.PersonalBestRepository;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.service.PersonalBestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One-shot backfill that seeds {@code personal_best} rows from existing scoreboard history.
 *
 * <p>Skipped on every restart after the first run by checking
 * {@link PersonalBestRepository#existsBySource(PersonalBestSource)} for {@code COMPUTED}.
 * Manual rows seeded via SQL are ignored by this guard, so the backfill will still run if
 * only manual rows exist — and it will not overwrite them, since
 * {@link PersonalBestService#seedFromHistory} is no-op on existing rows.
 *
 * <p>Does NOT trigger any Discord messages; uses {@code seedFromHistory}, not {@code recompute}.
 */
@Component
public class PersonalBestBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersonalBestBackfillRunner.class);

    private final ScoreboardRepository scoreboardRepository;
    private final PersonalBestRepository personalBestRepository;
    private final PersonalBestService personalBestService;

    public PersonalBestBackfillRunner(ScoreboardRepository scoreboardRepository,
                                      PersonalBestRepository personalBestRepository,
                                      PersonalBestService personalBestService) {
        this.scoreboardRepository = scoreboardRepository;
        this.personalBestRepository = personalBestRepository;
        this.personalBestService = personalBestService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (personalBestRepository.existsBySource(PersonalBestSource.COMPUTED)) {
            log.debug("Personal-best backfill skipped — computed rows already present.");
            return;
        }

        List<Scoreboard> all = scoreboardRepository.findAll();
        if (all.isEmpty()) {
            log.info("Personal-best backfill: no scoreboard history; nothing to do.");
            return;
        }

        // (user, gameType, dow-or-null) -> (minSeconds, date)
        Map<ScopeKey, BestEntry> mins = new HashMap<>();

        for (Scoreboard s : all) {
            User user = s.getUser();
            // MAIN — DoW partitioned, only clean results
            MainCrosswordResult main = s.getMainCrosswordResult();
            if (main != null && !main.isAssisted() && main.getTotalSeconds() != null) {
                accumulate(mins, new ScopeKey(user.getId(), GameType.MAIN_CROSSWORD,
                                s.getDate().getDayOfWeek()),
                        user, main.getTotalSeconds(), s);
            }
            if (s.getMiniCrosswordResult() != null && s.getMiniCrosswordResult().getTotalSeconds() != null) {
                accumulate(mins, new ScopeKey(user.getId(), GameType.MINI_CROSSWORD, null),
                        user, s.getMiniCrosswordResult().getTotalSeconds(), s);
            }
            if (s.getMidiCrosswordResult() != null && s.getMidiCrosswordResult().getTotalSeconds() != null) {
                accumulate(mins, new ScopeKey(user.getId(), GameType.MIDI_CROSSWORD, null),
                        user, s.getMidiCrosswordResult().getTotalSeconds(), s);
            }
        }

        int seeded = 0;
        for (var entry : mins.entrySet()) {
            ScopeKey k = entry.getKey();
            BestEntry b = entry.getValue();
            personalBestService.seedFromHistory(b.user, k.gameType,
                    Optional.ofNullable(k.dayOfWeek), b.seconds, b.date);
            seeded++;
        }
        log.info("Personal-best backfill: seeded {} row(s) from {} scoreboard record(s).",
                seeded, all.size());
    }

    private static void accumulate(Map<ScopeKey, BestEntry> mins, ScopeKey key,
                                    User user, int seconds, Scoreboard s) {
        BestEntry cur = mins.get(key);
        if (cur == null || seconds < cur.seconds) {
            mins.put(key, new BestEntry(user, seconds, s.getDate()));
        }
    }

    private record ScopeKey(Long userId, GameType gameType, DayOfWeek dayOfWeek) {}

    private record BestEntry(User user, int seconds, java.time.LocalDate date) {}
}
