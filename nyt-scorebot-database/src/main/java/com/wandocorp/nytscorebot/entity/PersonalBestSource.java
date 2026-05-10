package com.wandocorp.nytscorebot.entity;

/**
 * Provenance of a {@link PersonalBest} row.
 *
 * <ul>
 *   <li>{@link #MANUAL} — hand-seeded via SQL by the operator. Preserved by the
 *       recompute path until a strictly faster <em>clean</em> result beats it.</li>
 *   <li>{@link #COMPUTED} — set/updated by the bot from a clean live save or by the
 *       initial-launch backfill runner.</li>
 * </ul>
 */
public enum PersonalBestSource {
    MANUAL,
    COMPUTED
}
