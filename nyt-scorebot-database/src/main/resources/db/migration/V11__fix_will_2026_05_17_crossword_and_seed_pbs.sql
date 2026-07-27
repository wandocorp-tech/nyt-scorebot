-- Supersedes the V8 seed and the V9 / V10 correction attempts for the player configured on
-- discord.channels[0] (display name "Will", channel 1358111788146622709).
--
-- Why V8, V9 and V10 all did nothing
-- ----------------------------------
-- All three gate on `app_user.name = 'William'`. That column is written by
-- ScoreboardService.findOrCreateUser from the `discord.channels[0].name` property, whose
-- configured value is 'Will'. Every one of those migrations therefore matched zero rows.
-- Flyway records a zero-row UPDATE as SUCCESS, and FlywayMigrationTest runs against an empty
-- schema where "corrected the data" and "matched nothing" are indistinguishable, so the
-- failures were silent.
--
-- V8, V9 and V10 are left byte-for-byte unchanged: they are already applied in production and
-- editing them would fail Flyway's checksum validation and prevent the application starting.
--
-- This migration is gated on `channel_id`, which is NOT NULL, UNIQUE, and is the identity key
-- ScoreboardService already uses to resolve a user. It is not derived from a display name and
-- so cannot be invalidated by a rename.
--
-- What it does
-- ------------
--   1. Corrects the 2026-05-17 Main Crossword row. The share text ended "in 1:10:25!" but the
--      CrosswordParser regex only matched MM:SS at the time, so it was stored as '1:10' (70s)
--      instead of 4225s.
--   2. Re-applies the V8 Main Crossword personal-best seeds that never landed.
--   3. Rebuilds the MAIN / Sunday history bucket from `game_result` truth, without regressing
--      the seeded PB.
--
-- Every step is idempotent and is a no-op on a database that never held the bad data.

-- ── Step 1: correct the corrupted 2026-05-17 Main Crossword row ──────────────
UPDATE game_result
SET time_string = '1:10:25',
    total_seconds = 4225
WHERE game_type = 'MAIN_CROSSWORD'
  AND crossword_date = DATE '2026-05-17'
  AND (total_seconds IS NULL OR total_seconds <> 4225)
  AND scoreboard_id IN (
      SELECT s.id
      FROM scoreboard s
      JOIN app_user u ON u.id = s.user_id
      WHERE u.channel_id = '1358111788146622709'
  );

-- ── Step 2: re-apply the V8 Main Crossword PB seeds ──────────────────────────
-- pb_seconds is only ever lowered, so a genuinely faster recorded PB is never regressed.
-- Unmatched buckets are inserted with sample_count/sum_seconds = 0 so the avg cell renders
-- '-' until real submissions accumulate, while the pb cell shows the seeded value.
MERGE INTO crossword_history_stats AS dst
USING (
    SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(d.dow AS TINYINT) AS day_of_week, d.pb AS pb_seconds
    FROM app_user u
    CROSS JOIN (
        SELECT 1 AS dow,  300 AS pb UNION ALL
        SELECT 2,         379       UNION ALL
        SELECT 3,         534       UNION ALL
        SELECT 4,         705       UNION ALL
        SELECT 5,         657       UNION ALL
        SELECT 6,         948       UNION ALL
        SELECT 7,        1466
    ) d
    WHERE u.channel_id = '1358111788146622709'
) AS src
ON (dst.user_id = src.user_id
    AND dst.game_type = src.game_type
    AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

-- ── Step 3: rebuild the MAIN / Sunday bucket from game_result truth ──────────
-- sample_count and sum_seconds are derived solely from qualifying rows (clean solves only —
-- no check, no lookups, no duo — matching CrosswordHistoryService.recordSubmission).
--
-- pb_seconds takes the *lesser* of the rebuilt minimum and the existing value. V9 and V10 both
-- assigned MIN(total_seconds) outright, which would have discarded the seeded 1466s Sunday PB:
-- that solve predates the bot and has no game_result row, so a bare recompute loses it.
MERGE INTO crossword_history_stats AS dst
USING (
    SELECT u.id AS user_id,
           'MAIN' AS game_type,
           CAST(7 AS TINYINT) AS day_of_week,
           COUNT(gr.id) AS sample_count,
           COALESCE(SUM(gr.total_seconds), 0) AS sum_seconds,
           MIN(gr.total_seconds) AS pb_seconds
    FROM app_user u
    JOIN scoreboard s ON s.user_id = u.id
    JOIN game_result gr ON gr.scoreboard_id = s.id
    WHERE u.channel_id = '1358111788146622709'
      AND gr.game_type = 'MAIN_CROSSWORD'
      AND COALESCE(gr.check_used, FALSE) = FALSE
      AND COALESCE(gr.lookups, 0) = 0
      AND COALESCE(gr.duo, FALSE) = FALSE
      AND EXTRACT(ISO_DAY_OF_WEEK FROM gr.crossword_date) = 7
    GROUP BY u.id
    HAVING COUNT(gr.id) > 0
) AS src
ON (dst.user_id = src.user_id
    AND dst.game_type = src.game_type
    AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET
    sample_count = src.sample_count,
    sum_seconds = src.sum_seconds,
    pb_seconds =
        CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
             THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week,
                               src.sample_count, src.sum_seconds, src.pb_seconds);
