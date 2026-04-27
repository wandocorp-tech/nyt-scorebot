package com.wandocorp.nytscorebot.service.scoreboard;

public sealed interface ComparisonOutcome
        permits ComparisonOutcome.Tie, ComparisonOutcome.Nuke, ComparisonOutcome.Win, ComparisonOutcome.WaitingFor {

    record Tie() implements ComparisonOutcome {}

    record Nuke() implements ComparisonOutcome {}

    /** Win where differentialLabel is null means no differential is shown (e.g., one player failed or was disqualified). */
    record Win(String winnerName, String differentialLabel) implements ComparisonOutcome {}

    record WaitingFor(String missingPlayerName) implements ComparisonOutcome {}
}
