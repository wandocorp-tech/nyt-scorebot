package com.wandocorp.nytscorebot.repository;

import com.wandocorp.nytscorebot.entity.CrosswordHistoryStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CrosswordHistoryStatRepository extends JpaRepository<CrosswordHistoryStat, Long> {

    Optional<CrosswordHistoryStat> findByUserIdAndGameTypeAndDayOfWeek(Long userId, String gameType, byte dayOfWeek);

    /**
     * Atomic upsert of one bucket: bumps {@code sample_count} by 1, adds {@code totalSeconds}
     * to {@code sum_seconds}, and lowers {@code pb_seconds} when the new value beats it
     * (or sets it for the first time when {@code pb_seconds IS NULL}).
     *
     * <p>Native H2 {@code MERGE INTO ... USING ...} chosen for portability with V7/V8 and
     * for atomicity under concurrent submissions.
     */
    @Modifying
    @Query(value = """
            MERGE INTO crossword_history_stats AS dst
            USING (SELECT CAST(:userId AS BIGINT) AS user_id,
                          CAST(:gameType AS VARCHAR) AS game_type,
                          CAST(:dayOfWeek AS TINYINT) AS day_of_week,
                          CAST(:totalSeconds AS INTEGER) AS new_seconds) AS src
            ON (dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week)
            WHEN MATCHED THEN UPDATE SET
                sample_count = dst.sample_count + 1,
                sum_seconds = dst.sum_seconds + src.new_seconds,
                pb_seconds = CASE WHEN dst.pb_seconds IS NULL OR src.new_seconds < dst.pb_seconds
                                  THEN src.new_seconds ELSE dst.pb_seconds END
            WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                                   VALUES (src.user_id, src.game_type, src.day_of_week, 1, src.new_seconds, src.new_seconds)
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("gameType") String gameType,
                @Param("dayOfWeek") byte dayOfWeek,
                @Param("totalSeconds") int totalSeconds);
}
