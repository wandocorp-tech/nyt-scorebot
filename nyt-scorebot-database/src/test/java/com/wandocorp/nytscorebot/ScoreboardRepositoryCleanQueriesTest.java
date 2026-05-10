package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MidiCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class ScoreboardRepositoryCleanQueriesTest {

    @SpringBootApplication
    static class TestConfig {}

    @Autowired private UserRepository userRepository;
    @Autowired private ScoreboardRepository scoreboardRepository;

    private User newUser(String channelId, String name) {
        return userRepository.save(new User(channelId, name, channelId + "-discord"));
    }

    private Scoreboard saveMain(User user, LocalDate date, int seconds, Boolean duo, Integer lookups, Boolean check) {
        Scoreboard sb = new Scoreboard(user, date);
        MainCrosswordResult r = new MainCrosswordResult("raw", user.getName(), null, formatTime(seconds), seconds, date);
        r.setDuo(duo);
        r.setLookups(lookups);
        r.setCheckUsed(check);
        sb.addResult(r);
        return scoreboardRepository.save(sb);
    }

    private Scoreboard saveMini(User user, LocalDate date, int seconds) {
        Scoreboard sb = new Scoreboard(user, date);
        sb.addResult(new MiniCrosswordResult("raw", user.getName(), null, formatTime(seconds), seconds, date));
        return scoreboardRepository.save(sb);
    }

    private Scoreboard saveMidi(User user, LocalDate date, int seconds) {
        Scoreboard sb = new Scoreboard(user, date);
        sb.addResult(new MidiCrosswordResult("raw", user.getName(), null, formatTime(seconds), seconds, date));
        return scoreboardRepository.save(sb);
    }

    private static String formatTime(int s) {
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Test
    void mainEmptyHistoryReturnsEmpty() {
        User u = newUser("ch1", "P1");
        assertThat(scoreboardRepository.findCleanMainSecondsBeforeDate(u, DayOfWeek.SATURDAY, LocalDate.of(2026, 5, 10)))
                .isEmpty();
    }

    @Test
    void mainTodayExcluded() {
        User u = newUser("ch2", "P2");
        LocalDate today = LocalDate.of(2026, 5, 10);
        saveMain(u, today, 1000, null, null, null);
        assertThat(scoreboardRepository.findCleanMainSecondsBeforeDate(u, DayOfWeek.SUNDAY, today)).isEmpty();
    }

    @Test
    void mainDayOfWeekFilterMatches() {
        User u = newUser("ch3", "P3");
        LocalDate today = LocalDate.of(2026, 5, 10);
        saveMain(u, LocalDate.of(2026, 5, 4), 300, null, null, null); // Mon
        saveMain(u, LocalDate.of(2026, 5, 5), 400, null, null, null); // Tue
        saveMain(u, LocalDate.of(2026, 5, 9), 900, null, null, null); // Sat

        assertThat(scoreboardRepository.findCleanMainSecondsBeforeDate(u, DayOfWeek.MONDAY, today))
                .containsExactly(300);
        assertThat(scoreboardRepository.findCleanMainSecondsBeforeDate(u, DayOfWeek.SATURDAY, today))
                .containsExactly(900);
    }

    @Test
    void mainAssistedExcluded() {
        User u = newUser("ch4", "P4");
        LocalDate today = LocalDate.of(2026, 5, 10);
        saveMain(u, LocalDate.of(2026, 4, 27), 300, null, null, null);  // clean
        saveMain(u, LocalDate.of(2026, 5, 4),  250, true, null, null);  // duo
        saveMain(u, LocalDate.of(2026, 4, 20), 280, null, 2, null);     // lookups
        saveMain(u, LocalDate.of(2026, 4, 13), 290, null, null, true);  // check

        List<Integer> result = scoreboardRepository.findCleanMainSecondsBeforeDate(u, DayOfWeek.MONDAY, today);
        assertThat(result).containsExactly(300);
    }

    @Test
    void mainCleanFlagsZeroOrFalseTreatedAsClean() {
        User u = newUser("ch5", "P5");
        LocalDate today = LocalDate.of(2026, 5, 10);
        saveMain(u, LocalDate.of(2026, 5, 4), 333, false, 0, false);
        assertThat(scoreboardRepository.findCleanMainSecondsBeforeDate(u, DayOfWeek.MONDAY, today))
                .containsExactly(333);
    }

    @Test
    void miniReturnsAllBeforeToday() {
        User u = newUser("ch6", "P6");
        LocalDate today = LocalDate.of(2026, 5, 10);
        saveMini(u, LocalDate.of(2026, 5, 4), 60);
        saveMini(u, LocalDate.of(2026, 5, 5), 75);
        saveMini(u, today, 90);

        assertThat(scoreboardRepository.findCleanSecondsBeforeDate(u, GameType.MINI_CROSSWORD, today))
                .containsExactlyInAnyOrder(60, 75);
    }

    @Test
    void midiReturnsAllBeforeToday() {
        User u = newUser("ch7", "P7");
        LocalDate today = LocalDate.of(2026, 5, 10);
        saveMidi(u, LocalDate.of(2026, 5, 4), 200);
        saveMidi(u, LocalDate.of(2026, 5, 5), 240);

        assertThat(scoreboardRepository.findCleanSecondsBeforeDate(u, GameType.MIDI_CROSSWORD, today))
                .containsExactlyInAnyOrder(200, 240);
    }

    @Test
    void cleanSecondsRejectsMain() {
        User u = newUser("ch8", "P8");
        assertThatThrownBy(() -> scoreboardRepository.findCleanSecondsBeforeDate(u, GameType.MAIN_CROSSWORD, LocalDate.of(2026, 5, 10)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only supports Mini/Midi");
    }
}
