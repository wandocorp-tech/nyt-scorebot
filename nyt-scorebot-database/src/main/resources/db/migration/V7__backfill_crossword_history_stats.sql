-- Backfill crossword_history_stats from existing game_result rows (V4-normalised schema).
-- Mini/Midi: one row per user; day_of_week sentinel = 0; every successful result counts.
-- Main: one row per (user, weekday); only "clean" results count
--       (no check used, no lookups, no duo).
--
-- NOTE: H2's DAY_OF_WEEK() is locale-sensitive. EXTRACT(ISO_DAY_OF_WEEK FROM d) is
-- guaranteed to return Monday=1..Sunday=7 to match Java's DayOfWeek.getValue().

INSERT INTO crossword_history_stats (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
SELECT s.user_id,
       'MINI',
       0,
       COUNT(*),
       COALESCE(SUM(gr.total_seconds), 0),
       MIN(gr.total_seconds)
FROM game_result gr
JOIN scoreboard s ON s.id = gr.scoreboard_id
WHERE gr.game_type = 'MINI_CROSSWORD'
  AND gr.total_seconds IS NOT NULL
GROUP BY s.user_id;

INSERT INTO crossword_history_stats (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
SELECT s.user_id,
       'MIDI',
       0,
       COUNT(*),
       COALESCE(SUM(gr.total_seconds), 0),
       MIN(gr.total_seconds)
FROM game_result gr
JOIN scoreboard s ON s.id = gr.scoreboard_id
WHERE gr.game_type = 'MIDI_CROSSWORD'
  AND gr.total_seconds IS NOT NULL
GROUP BY s.user_id;

INSERT INTO crossword_history_stats (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
SELECT s.user_id,
       'MAIN',
       CAST(EXTRACT(ISO_DAY_OF_WEEK FROM gr.crossword_date) AS TINYINT),
       COUNT(*),
       COALESCE(SUM(gr.total_seconds), 0),
       MIN(gr.total_seconds)
FROM game_result gr
JOIN scoreboard s ON s.id = gr.scoreboard_id
WHERE gr.game_type = 'MAIN_CROSSWORD'
  AND gr.total_seconds IS NOT NULL
  AND gr.crossword_date IS NOT NULL
  AND (gr.check_used IS NULL OR gr.check_used = FALSE)
  AND (gr.lookups IS NULL OR gr.lookups = 0)
  AND (gr.duo IS NULL OR gr.duo = FALSE)
GROUP BY s.user_id, EXTRACT(ISO_DAY_OF_WEEK FROM gr.crossword_date);
