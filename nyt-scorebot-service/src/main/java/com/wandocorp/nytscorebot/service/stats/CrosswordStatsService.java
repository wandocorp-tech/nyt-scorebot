package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.config.StatsProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import com.wandocorp.nytscorebot.service.scoreboard.MainCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MidiCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MiniCrosswordScoreboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Computes period-aggregated crossword statistics.
 *
 * <p>Win attribution mirrors the {@code crossword-win-streaks} logic:
 * same {@link ComparisonOutcome} semantics from {@link MiniCrosswordScoreboard} /
 * {@link MidiCrosswordScoreboard} / {@link MainCrosswordScoreboard}, plus the same forfeit
 * rule used by {@code WinStreakMidnightJob} for solo submissions.
 */
@RequiredArgsConstructor
@Service
public class CrosswordStatsService {

    private final StatsProperties statsProperties;
    private final ScoreboardRepository scoreboardRepository;
    private final UserRepository userRepository;
    private final MiniCrosswordScoreboard miniScoreboard;
    private final MidiCrosswordScoreboard midiScoreboard;
    private final MainCrosswordScoreboard mainScoreboard;

    /**
     * Compute stats for the given game filter and window.
     *
     * @param filter which games to include
     * @param from   start of the window (inclusive); raised to the anchor if earlier
     * @param to     end of the window (inclusive)
     * @throws StatsWindowBeforeAnchorException if the entire window precedes the anchor date
     */
    public CrosswordStatsReport compute(GameTypeFilter filter, LocalDate from, LocalDate to) {
        LocalDate anchor = statsProperties.getAnchorDate();
        if (anchor != null && from.isBefore(anchor)) {
            if (to.isBefore(anchor)) {
                throw new StatsWindowBeforeAnchorException(anchor);
            }
            from = anchor;
        }

        List<User> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getChannelId))
                .toList();

        if (users.size() < 2) {
            // Cannot compute head-to-head without two registered players.
            return emptyReport(filter, from, to, "Player1", "Player2");
        }

        User user1 = users.get(0);
        User user2 = users.get(1);
        String name1 = user1.getName();
        String name2 = user2.getName();

        List<Scoreboard> allScoreboards = scoreboardRepository.findAllByDateBetweenWithUser(from, to);

        // Group by (user channelId, date) for O(1) lookup
        Map<String, Map<LocalDate, Scoreboard>> byUserDate = new HashMap<>();
        for (Scoreboard sb : allScoreboards) {
            byUserDate
                    .computeIfAbsent(sb.getUser().getChannelId(), k -> new HashMap<>())
                    .put(sb.getDate(), sb);
        }

        // Collect all dates in the window that have at least one scoreboard entry
        java.util.Set<LocalDate> dates = allScoreboards.stream()
                .map(Scoreboard::getDate)
                .collect(Collectors.toSet());

        boolean includeDow = ChronoUnit.DAYS.between(from, to) >= 27;

        List<CrosswordStatsReport.GameStats> games = new ArrayList<>();
        for (GameType gameType : orderedGames(filter)) {
            // Per-player accumulators
            int[] wins1 = {0}, wins2 = {0};
            int[] played1 = {0}, played2 = {0};
            long[] totalSec1 = {0}, totalSec2 = {0};
            int[] bestSec1 = {Integer.MAX_VALUE}, bestSec2 = {Integer.MAX_VALUE};
            LocalDate[] bestDate1 = {null}, bestDate2 = {null};
            int[] excluded1 = {0}, excluded2 = {0};

            // DoW accumulators for Main (keyed by DayOfWeek)
            Map<DayOfWeek, long[]> dowTotal1 = new EnumMap<>(DayOfWeek.class);
            Map<DayOfWeek, int[]> dowCount1 = new EnumMap<>(DayOfWeek.class);
            Map<DayOfWeek, long[]> dowTotal2 = new EnumMap<>(DayOfWeek.class);
            Map<DayOfWeek, int[]> dowCount2 = new EnumMap<>(DayOfWeek.class);

            for (LocalDate date : dates) {
                Scoreboard sb1 = byUserDate.getOrDefault(user1.getChannelId(), Map.of()).get(date);
                Scoreboard sb2 = byUserDate.getOrDefault(user2.getChannelId(), Map.of()).get(date);

                boolean has1 = sb1 != null && sb1.hasResult(gameType);
                boolean has2 = sb2 != null && sb2.hasResult(gameType);

                // Time stats
                if (has1) {
                    CrosswordResult r1 = getResult(gameType, sb1);
                    if (gameType == GameType.MAIN_CROSSWORD && ((MainCrosswordResult) r1).isAssisted()) {
                        excluded1[0]++;
                    } else {
                        int sec1 = r1.getTotalSeconds();
                        played1[0]++;
                        totalSec1[0] += sec1;
                        if (sec1 < bestSec1[0]) {
                            bestSec1[0] = sec1;
                            bestDate1[0] = date;
                        }
                        if (includeDow && gameType == GameType.MAIN_CROSSWORD) {
                            DayOfWeek dow = date.getDayOfWeek();
                            dowTotal1.computeIfAbsent(dow, k -> new long[]{0})[0] += sec1;
                            dowCount1.computeIfAbsent(dow, k -> new int[]{0})[0]++;
                        }
                    }
                }
                if (has2) {
                    CrosswordResult r2 = getResult(gameType, sb2);
                    if (gameType == GameType.MAIN_CROSSWORD && ((MainCrosswordResult) r2).isAssisted()) {
                        excluded2[0]++;
                    } else {
                        int sec2 = r2.getTotalSeconds();
                        played2[0]++;
                        totalSec2[0] += sec2;
                        if (sec2 < bestSec2[0]) {
                            bestSec2[0] = sec2;
                            bestDate2[0] = date;
                        }
                        if (includeDow && gameType == GameType.MAIN_CROSSWORD) {
                            DayOfWeek dow = date.getDayOfWeek();
                            dowTotal2.computeIfAbsent(dow, k -> new long[]{0})[0] += sec2;
                            dowCount2.computeIfAbsent(dow, k -> new int[]{0})[0]++;
                        }
                    }
                }

                // Win attribution
                if (has1 && has2) {
                    ComparisonOutcome outcome = determineOutcome(gameType, sb1, name1, sb2, name2);
                    if (outcome instanceof ComparisonOutcome.Win win) {
                        boolean duoA = isDuo(gameType, sb1);
                        boolean duoB = isDuo(gameType, sb2);
                        boolean winnerIs1 = resolveWinnerIs1(win, user1, user2);
                        boolean winnerUsedDuo = winnerIs1 ? duoA : duoB;
                        if (!winnerUsedDuo) {
                            if (winnerIs1) wins1[0]++;
                            else wins2[0]++;
                        }
                    }
                    // Tie, Nuke, WaitingFor → no win for either
                } else if (has1) {
                    wins1[0]++; // forfeit
                } else if (has2) {
                    wins2[0]++; // forfeit
                }
            }

            CrosswordStatsReport.UserGameStats stats1 = buildUserStats(
                    name1, wins1[0], played1[0], totalSec1[0], bestSec1[0], bestDate1[0], excluded1[0]);
            CrosswordStatsReport.UserGameStats stats2 = buildUserStats(
                    name2, wins2[0], played2[0], totalSec2[0], bestSec2[0], bestDate2[0], excluded2[0]);

            List<CrosswordStatsReport.UserGameStats> sorted = rankPlayers(stats1, stats2);

            Optional<CrosswordStatsReport.DowBlock> dowBlock = Optional.empty();
            if (includeDow && gameType == GameType.MAIN_CROSSWORD) {
                dowBlock = Optional.of(buildDowBlock(
                        name1, dowTotal1, dowCount1,
                        name2, dowTotal2, dowCount2));
            }

            games.add(new CrosswordStatsReport.GameStats(gameType, sorted, dowBlock));
        }

        return new CrosswordStatsReport(filter, from, to, name1, name2, games);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static List<GameType> orderedGames(GameTypeFilter filter) {
        List<GameType> order = List.of(
                GameType.MINI_CROSSWORD, GameType.MIDI_CROSSWORD, GameType.MAIN_CROSSWORD);
        return order.stream().filter(g -> filter.gameTypes().contains(g)).toList();
    }

    private ComparisonOutcome determineOutcome(GameType gameType, Scoreboard sb1, String name1,
                                               Scoreboard sb2, String name2) {
        return switch (gameType) {
            case MINI_CROSSWORD -> miniScoreboard.determineOutcome(sb1, name1, sb2, name2);
            case MIDI_CROSSWORD -> midiScoreboard.determineOutcome(sb1, name1, sb2, name2);
            case MAIN_CROSSWORD -> mainScoreboard.determineOutcome(sb1, name1, sb2, name2);
            default -> new ComparisonOutcome.WaitingFor("");
        };
    }

    private static CrosswordResult getResult(GameType gameType, Scoreboard sb) {
        return switch (gameType) {
            case MINI_CROSSWORD -> sb.getMiniCrosswordResult();
            case MIDI_CROSSWORD -> sb.getMidiCrosswordResult();
            case MAIN_CROSSWORD -> sb.getMainCrosswordResult();
            default -> null;
        };
    }

    private static boolean isDuo(GameType gameType, Scoreboard sb) {
        if (gameType != GameType.MAIN_CROSSWORD) return false;
        MainCrosswordResult r = sb.getMainCrosswordResult();
        return r != null && Boolean.TRUE.equals(r.getDuo());
    }

    /** Mirrors WinStreakService.resolveWinnerIsA — strips the " et al." duo suffix for matching. */
    private static boolean resolveWinnerIs1(ComparisonOutcome.Win win, User user1, User user2) {
        String name = win.winnerName();
        if (name == null) return false;
        String stripped = name.endsWith(" et al.")
                ? name.substring(0, name.length() - " et al.".length())
                : name;
        if (stripped.equals(user1.getName())) return true;
        if (stripped.equals(user2.getName())) return false;
        return true; // defensive default
    }

    private static CrosswordStatsReport.UserGameStats buildUserStats(
            String name, int wins, int played, long totalSec, int bestSec, LocalDate bestDate,
            int excludedAssistedCount) {
        OptionalDouble avg = played > 0 ? OptionalDouble.of((double) totalSec / played) : OptionalDouble.empty();
        OptionalInt best = bestSec < Integer.MAX_VALUE ? OptionalInt.of(bestSec) : OptionalInt.empty();
        return new CrosswordStatsReport.UserGameStats(
                name, wins, played, avg, best, Optional.ofNullable(bestDate), excludedAssistedCount);
    }

    /** Sort by wins descending; break ties by best time ascending (lower is better). */
    private static List<CrosswordStatsReport.UserGameStats> rankPlayers(
            CrosswordStatsReport.UserGameStats s1,
            CrosswordStatsReport.UserGameStats s2) {
        List<CrosswordStatsReport.UserGameStats> list = new ArrayList<>(List.of(s1, s2));
        list.sort((a, b) -> {
            int cmpWins = Integer.compare(b.wins(), a.wins());
            if (cmpWins != 0) return cmpWins;
            int ba = a.bestSeconds().orElse(Integer.MAX_VALUE);
            int bb = b.bestSeconds().orElse(Integer.MAX_VALUE);
            return Integer.compare(ba, bb);
        });
        return list;
    }

    private static CrosswordStatsReport.DowBlock buildDowBlock(
            String name1, Map<DayOfWeek, long[]> totals1, Map<DayOfWeek, int[]> counts1,
            String name2, Map<DayOfWeek, long[]> totals2, Map<DayOfWeek, int[]> counts2) {
        DayOfWeek[] order = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        List<CrosswordStatsReport.DowRow> rows = new ArrayList<>();
        for (DayOfWeek dow : order) {
            Optional<CrosswordStatsReport.DowCell> cell1 = buildDowCell(totals1, counts1, dow);
            Optional<CrosswordStatsReport.DowCell> cell2 = buildDowCell(totals2, counts2, dow);
            rows.add(new CrosswordStatsReport.DowRow(dow, cell1, cell2));
        }
        return new CrosswordStatsReport.DowBlock(rows);
    }

    private static Optional<CrosswordStatsReport.DowCell> buildDowCell(
            Map<DayOfWeek, long[]> totals, Map<DayOfWeek, int[]> counts, DayOfWeek dow) {
        int[] cnt = counts.get(dow);
        if (cnt == null || cnt[0] == 0) return Optional.empty();
        long total = totals.get(dow)[0];
        return Optional.of(new CrosswordStatsReport.DowCell((double) total / cnt[0], cnt[0]));
    }

    private static CrosswordStatsReport emptyReport(GameTypeFilter filter,
                                                     LocalDate from, LocalDate to,
                                                     String name1, String name2) {
        return new CrosswordStatsReport(filter, from, to, name1, name2, List.of());
    }
}
