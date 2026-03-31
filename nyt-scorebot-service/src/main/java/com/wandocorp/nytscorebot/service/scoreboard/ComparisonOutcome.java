package com.wandocorp.nytscorebot.service.scoreboard;

public sealed interface ComparisonOutcome
        permits ComparisonOutcome.Tie, ComparisonOutcome.Win, ComparisonOutcome.WaitingFor {

    record Tie() implements ComparisonOutcome {}

    /** Win where differential is null means one player failed (no differential shown) */
    record Win(String winnerName, Integer differential) implements ComparisonOutcome {}

    record WaitingFor(String missingPlayerName) implements ComparisonOutcome {}
}
