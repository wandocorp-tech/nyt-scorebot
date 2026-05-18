-- One-off data fix for William's 2026-05-17 Main Crossword submission.
--
-- The CrosswordParser regex previously only matched MM:SS, so the share text
-- "...New York Times Daily Crossword in 1:10:25!" was parsed as "1:10" (70s)
-- instead of 4,225s. That corrupted two tables:
--   1. The Main Crossword row in `game_result` for William on 2026-05-17.
--   2. William's MAIN / Sunday (day_of_week=7) bucket in `crossword_history_stats`,
--      where sample_count/sum_seconds gained a bogus 70s sample and pb_seconds
--      was overwritten to 70s.
--
-- Step 1 patches the game_result row in place. The WHERE clause is gated on the
-- exact bogus values (70 / "1:10" / 2026-05-17 / user=William), so this migration
-- is idempotent and a no-op on databases that never recorded the bad row
-- (including the in-memory DB used by FlywayMigrationTest).
--
-- Step 2 rebuilds William's MAIN / Sunday bucket from `game_result` truth
-- (clean solves only — no check, no lookups, no duo — matching
-- CrosswordHistoryService.recordSubmission). Only this single bucket is touched.

-- ── Step 1: correct the corrupted scoreboard row ────────────────────────────
UPDATE game_result
SET time_string = '1:10:25',
    total_seconds = 4225
WHERE game_type = 'MAIN_CROSSWORD'
  AND time_string = '1:10'
  AND total_seconds = 70
  AND crossword_date = DATE '2026-05-17'
  AND scoreboard_id IN (
      SELECT s.id
      FROM scoreboard s
      JOIN app_user u ON u.id = s.user_id
      WHERE u.name = 'William'
        AND s.date = DATE '2026-05-17'
  );

-- ── Step 2: rebuild William's MAIN / Sunday history bucket ──────────────────
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
    WHERE u.name = 'William'
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
    pb_seconds = src.pb_seconds;
