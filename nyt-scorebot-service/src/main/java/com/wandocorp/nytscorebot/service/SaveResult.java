package com.wandocorp.nytscorebot.service;

/**
 * Wrapping result of {@link ScoreboardService#saveResult}.
 *
 * <p>Carries the existing {@link SaveOutcome} (success or failure mode) plus a
 * {@link PbUpdateOutcome} indicating whether a personal best was set as a side
 * effect of a successful crossword save. For non-crossword saves and for any
 * non-{@code SAVED} outcome, {@link #pb()} is {@link PbUpdateOutcome#NO_CHANGE}.
 */
public record SaveResult(SaveOutcome outcome, PbUpdateOutcome pb) {

    public static SaveResult of(SaveOutcome outcome) {
        return new SaveResult(outcome, PbUpdateOutcome.NO_CHANGE);
    }

    public static SaveResult saved(PbUpdateOutcome pb) {
        return new SaveResult(SaveOutcome.SAVED, pb);
    }
}
