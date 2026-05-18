-- Re-application of the V9 one-off data fix for William's 2026-05-17 Main Crossword.
--
-- V9 only matched rows whose stored value was the exact bogus pair
-- (time_string='1:10', total_seconds=70). On databases where the value
-- differed (e.g., a prior manual touch or partial fix), V9 was a no-op
-- and the /stats output for 2026-05-17 still reported the wrong time.
--
-- This migration scopes the update by date + user + game type only,
-- and additionally guards against accidentally overwriting an already
-- correct value (4225s / "1:10:25"). It is idempotent: on databases
-- where V9 succeeded or where the row was manually fixed, the WHERE
-- clause excludes the row and nothing is written.
--
-- The /stats and scoreboard render paths both read `game_result.total_seconds`
-- directly, so updating this row is sufficient to correct the displayed time.
-- The `crossword_history_stats` Sunday bucket for William's MAIN is also
-- rebuilt from `game_result` truth to ensure avg/pb stay in sync.

-- ── Step 1: correct the corrupted scoreboard row ────────────────────────────
UPDATE game_result
SET time_string = '1:10:25',
    total_seconds = 4225
WHERE game_type = 'MAIN_CROSSWORD'
  AND crossword_date = DATE '2026-05-17'
  AND total_seconds <> 4225
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
