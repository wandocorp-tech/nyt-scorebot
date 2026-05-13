-- One-off seed of Main crossword personal bests, supplied manually by the maintainer.
-- Each MERGE is a no-op if the player does not exist (the USING subquery yields 0 rows).
-- For matched rows, pb_seconds is set to LEAST(existing, supplied) so a faster backfilled
-- PB from V7 is never regressed. For unmatched rows, a stub row is inserted with
-- sample_count = 0 and sum_seconds = 0 so the avg cell renders '-' until real
-- submissions accumulate, while the pb cell renders the seeded value.
--
-- day_of_week: 1 = Monday … 7 = Sunday (matches Java DayOfWeek.getValue()).

-- ── Conor ────────────────────────────────────────────────────────────────────
MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(1 AS TINYINT) AS day_of_week, 186 AS pb_seconds
       FROM app_user u WHERE u.name = 'Conor') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(2 AS TINYINT) AS day_of_week, 271 AS pb_seconds
       FROM app_user u WHERE u.name = 'Conor') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(3 AS TINYINT) AS day_of_week, 413 AS pb_seconds
       FROM app_user u WHERE u.name = 'Conor') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(4 AS TINYINT) AS day_of_week, 516 AS pb_seconds
       FROM app_user u WHERE u.name = 'Conor') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(5 AS TINYINT) AS day_of_week, 524 AS pb_seconds
       FROM app_user u WHERE u.name = 'Conor') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(6 AS TINYINT) AS day_of_week, 1231 AS pb_seconds
       FROM app_user u WHERE u.name = 'Conor') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(7 AS TINYINT) AS day_of_week, 1131 AS pb_seconds
       FROM app_user u WHERE u.name = 'Conor') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

-- ── William ──────────────────────────────────────────────────────────────────
MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(1 AS TINYINT) AS day_of_week, 300 AS pb_seconds
       FROM app_user u WHERE u.name = 'William') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(2 AS TINYINT) AS day_of_week, 379 AS pb_seconds
       FROM app_user u WHERE u.name = 'William') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(3 AS TINYINT) AS day_of_week, 534 AS pb_seconds
       FROM app_user u WHERE u.name = 'William') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(4 AS TINYINT) AS day_of_week, 705 AS pb_seconds
       FROM app_user u WHERE u.name = 'William') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(5 AS TINYINT) AS day_of_week, 657 AS pb_seconds
       FROM app_user u WHERE u.name = 'William') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(6 AS TINYINT) AS day_of_week, 948 AS pb_seconds
       FROM app_user u WHERE u.name = 'William') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);

MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, CAST(7 AS TINYINT) AS day_of_week, 1466 AS pb_seconds
       FROM app_user u WHERE u.name = 'William') AS src
ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
WHEN MATCHED THEN UPDATE SET pb_seconds =
    CASE WHEN dst.pb_seconds IS NULL OR src.pb_seconds < dst.pb_seconds
         THEN src.pb_seconds ELSE dst.pb_seconds END
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);
