package com.wandocorp.nytscorebot;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V11 one-off data fix against a database seeded with the corrupted state.
 *
 * <p>{@link FlywayMigrationTest} runs the migrations against an empty schema, where
 * "corrected the data" and "matched zero rows" look identical. That blind spot is why V8, V9
 * and V10 were all recorded by Flyway as successful while changing nothing — they gate on
 * {@code app_user.name = 'William'}, but the persisted name is {@code 'Will'}.
 *
 * <p>Each test migrates to V10, seeds a scenario, then migrates to V11 and asserts the outcome.
 */
class V11DataFixMigrationTest {

    /** discord.channels[0].id — the immutable key V11 gates on. */
    private static final String WILL_CHANNEL_ID = "1358111788146622709";

    private static final int CORRECT_SECONDS = 4225;
    private static final int BOGUS_SECONDS = 70;
    private static final int SEEDED_SUNDAY_PB = 1466;

    private record Fixture(DataSource dataSource, Flyway flyway) {}

    /** Fresh in-memory database migrated as far as V10, with Flyway ready to apply V11. */
    private Fixture migratedToV10() {
        String url = "jdbc:h2:mem:v11test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(url, "sa", "");
        ds.setDriverClassName("org.h2.Driver");

        Flyway toV10 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("10")
                .load();
        toV10.migrate();

        Flyway toV11 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load();

        return new Fixture(ds, toV11);
    }

    private void exec(DataSource ds, String... sql) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String s : sql) {
                st.execute(s);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed executing setup SQL", e);
        }
    }

    private Integer queryInt(DataSource ds, String sql) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            Object value = rs.getObject(1);
            return value == null ? null : ((Number) value).intValue();
        } catch (Exception e) {
            throw new IllegalStateException("Failed executing query: " + sql, e);
        }
    }

    private String queryString(DataSource ds, String sql) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            throw new IllegalStateException("Failed executing query: " + sql, e);
        }
    }

    /**
     * Seeds the player as the bot actually persists them — name 'Will', not 'William' —
     * plus a scoreboard for 2026-05-17.
     */
    private void seedPlayerAndScoreboard(DataSource ds) {
        exec(ds,
                "INSERT INTO app_user (id, channel_id, name, discord_user_id) "
                        + "VALUES (1, '" + WILL_CHANNEL_ID + "', 'Will', '426857331242958848')",
                "INSERT INTO scoreboard (id, user_id, date, finished) "
                        + "VALUES (1, 1, DATE '2026-05-17', TRUE)");
    }

    private void seedMainCrosswordResult(DataSource ds, String timeString, int totalSeconds) {
        exec(ds, "INSERT INTO game_result "
                + "(game_type, scoreboard_id, time_string, total_seconds, crossword_date, duo, lookups, check_used) "
                + "VALUES ('MAIN_CROSSWORD', 1, '" + timeString + "', " + totalSeconds
                + ", DATE '2026-05-17', FALSE, 0, FALSE)");
    }

    private String pbQuery() {
        return "SELECT pb_seconds FROM crossword_history_stats "
                + "WHERE user_id = 1 AND game_type = 'MAIN' AND day_of_week = 7";
    }

    // ── Step 1: the corrupted row is corrected ───────────────────────────────

    @Test
    void correctsTheCorrupted20260517MainCrosswordRow() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        seedMainCrosswordResult(f.dataSource(), "1:10", BOGUS_SECONDS);

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(),
                "SELECT total_seconds FROM game_result WHERE game_type = 'MAIN_CROSSWORD'"))
                .isEqualTo(CORRECT_SECONDS);
        assertThat(queryString(f.dataSource(),
                "SELECT time_string FROM game_result WHERE game_type = 'MAIN_CROSSWORD'"))
                .isEqualTo("1:10:25");
    }

    @Test
    void isNoOpWhenTheRowIsAlreadyCorrect() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        seedMainCrosswordResult(f.dataSource(), "1:10:25", CORRECT_SECONDS);

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(),
                "SELECT total_seconds FROM game_result WHERE game_type = 'MAIN_CROSSWORD'"))
                .isEqualTo(CORRECT_SECONDS);
        assertThat(queryString(f.dataSource(),
                "SELECT time_string FROM game_result WHERE game_type = 'MAIN_CROSSWORD'"))
                .isEqualTo("1:10:25");
    }

    @Test
    void isNoOpOnADatabaseThatNeverHeldTheBadRow() {
        Fixture f = migratedToV10();

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(), "SELECT COUNT(*) FROM game_result")).isZero();
        assertThat(queryInt(f.dataSource(), "SELECT COUNT(*) FROM crossword_history_stats")).isZero();
    }

    @Test
    void doesNotTouchAnotherPlayersRow() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        exec(f.dataSource(),
                "INSERT INTO app_user (id, channel_id, name, discord_user_id) "
                        + "VALUES (2, '999', 'Conor', 'u2')",
                "INSERT INTO scoreboard (id, user_id, date, finished) "
                        + "VALUES (2, 2, DATE '2026-05-17', TRUE)",
                "INSERT INTO game_result "
                        + "(game_type, scoreboard_id, time_string, total_seconds, crossword_date, duo, lookups, check_used) "
                        + "VALUES ('MAIN_CROSSWORD', 2, '1:10', 70, DATE '2026-05-17', FALSE, 0, FALSE)");

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(),
                "SELECT total_seconds FROM game_result WHERE scoreboard_id = 2"))
                .isEqualTo(BOGUS_SECONDS);
    }

    // ── Step 2: the V8 seeds that never landed are re-applied ────────────────

    @Test
    void reAppliesTheMainCrosswordPbSeedsThatV8Missed() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(), "SELECT COUNT(*) FROM crossword_history_stats "
                + "WHERE user_id = 1 AND game_type = 'MAIN'")).isEqualTo(7);
        assertThat(queryInt(f.dataSource(), pbQuery())).isEqualTo(SEEDED_SUNDAY_PB);
        assertThat(queryInt(f.dataSource(), "SELECT pb_seconds FROM crossword_history_stats "
                + "WHERE user_id = 1 AND game_type = 'MAIN' AND day_of_week = 1")).isEqualTo(300);
    }

    @Test
    void seedNeverRegressesAFasterExistingPb() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        exec(f.dataSource(), "INSERT INTO crossword_history_stats "
                + "(user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds) "
                + "VALUES (1, 'MAIN', 1, 3, 1500, 250)");

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(), "SELECT pb_seconds FROM crossword_history_stats "
                + "WHERE user_id = 1 AND game_type = 'MAIN' AND day_of_week = 1")).isEqualTo(250);
    }

    // ── Step 3: the Sunday bucket is rebuilt without losing the seeded PB ────

    @Test
    void rebuildsSundayBucketFromCorrectedResults() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        seedMainCrosswordResult(f.dataSource(), "1:10", BOGUS_SECONDS);
        exec(f.dataSource(), "INSERT INTO crossword_history_stats "
                + "(user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds) "
                + "VALUES (1, 'MAIN', 7, 1, 70, 70)");

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(), "SELECT sample_count FROM crossword_history_stats "
                + "WHERE user_id = 1 AND game_type = 'MAIN' AND day_of_week = 7")).isEqualTo(1);
        assertThat(queryInt(f.dataSource(), "SELECT CAST(sum_seconds AS INT) FROM crossword_history_stats "
                + "WHERE user_id = 1 AND game_type = 'MAIN' AND day_of_week = 7"))
                .isEqualTo(CORRECT_SECONDS);
    }

    @Test
    void preservesTheSeededSundayPbAgainstASlowerRecordedResult() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        // The only recorded Sunday solve is the corrected 4225s, slower than the seeded 1466s PB.
        seedMainCrosswordResult(f.dataSource(), "1:10", BOGUS_SECONDS);

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(), pbQuery()))
                .as("seeded pre-bot Sunday PB must survive the rebuild")
                .isEqualTo(SEEDED_SUNDAY_PB);
    }

    @Test
    void afasterRecordedSundayResultBecomesTheNewPb() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        seedMainCrosswordResult(f.dataSource(), "1:10", BOGUS_SECONDS);
        exec(f.dataSource(),
                "INSERT INTO scoreboard (id, user_id, date, finished) "
                        + "VALUES (2, 1, DATE '2026-05-10', TRUE)",
                "INSERT INTO game_result "
                        + "(game_type, scoreboard_id, time_string, total_seconds, crossword_date, duo, lookups, check_used) "
                        + "VALUES ('MAIN_CROSSWORD', 2, '20:00', 1200, DATE '2026-05-10', FALSE, 0, FALSE)");

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(), pbQuery())).isEqualTo(1200);
    }

    @Test
    void excludesFlaggedSolvesFromTheRebuild() {
        Fixture f = migratedToV10();
        seedPlayerAndScoreboard(f.dataSource());
        seedMainCrosswordResult(f.dataSource(), "1:10", BOGUS_SECONDS);
        exec(f.dataSource(),
                "INSERT INTO scoreboard (id, user_id, date, finished) "
                        + "VALUES (2, 1, DATE '2026-05-10', TRUE)",
                // A fast Sunday solve, but duo — must not count toward samples or PB.
                "INSERT INTO game_result "
                        + "(game_type, scoreboard_id, time_string, total_seconds, crossword_date, duo, lookups, check_used) "
                        + "VALUES ('MAIN_CROSSWORD', 2, '5:00', 300, DATE '2026-05-10', TRUE, 0, FALSE)");

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(), "SELECT sample_count FROM crossword_history_stats "
                + "WHERE user_id = 1 AND game_type = 'MAIN' AND day_of_week = 7")).isEqualTo(1);
        assertThat(queryInt(f.dataSource(), pbQuery())).isEqualTo(SEEDED_SUNDAY_PB);
    }

    // ── The regression that started it all ───────────────────────────────────

    @Test
    void gateIsNotSensitiveToTheDisplayName() {
        Fixture f = migratedToV10();
        // Deliberately a different display name: V11 must still match on channel_id.
        exec(f.dataSource(),
                "INSERT INTO app_user (id, channel_id, name, discord_user_id) "
                        + "VALUES (1, '" + WILL_CHANNEL_ID + "', 'SomeOtherName', 'u1')",
                "INSERT INTO scoreboard (id, user_id, date, finished) "
                        + "VALUES (1, 1, DATE '2026-05-17', TRUE)");
        seedMainCrosswordResult(f.dataSource(), "1:10", BOGUS_SECONDS);

        f.flyway().migrate();

        assertThat(queryInt(f.dataSource(),
                "SELECT total_seconds FROM game_result WHERE game_type = 'MAIN_CROSSWORD'"))
                .isEqualTo(CORRECT_SECONDS);
    }
}
