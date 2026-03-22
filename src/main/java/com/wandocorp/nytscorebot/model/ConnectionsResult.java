package com.wandocorp.nytscorebot.model;

import java.util.List;

public class ConnectionsResult extends GameResult {

    private final int puzzleNumber;

    /** Number of incorrect guesses (total rows − correctly solved rows). */
    private final int mistakes;

    /** True when all 4 groups were found. */
    private final boolean completed;

    /**
     * The color emoji for each correctly solved group, in the order solved.
     * E.g. ["🟨", "🟦", "🟩", "🟪"]
     */
    private final List<String> solveOrder;

    public ConnectionsResult(String rawContent, String discordAuthor, String comment,
                             int puzzleNumber, int mistakes, boolean completed,
                             List<String> solveOrder) {
        super(rawContent, discordAuthor, comment);
        this.puzzleNumber = puzzleNumber;
        this.mistakes = mistakes;
        this.completed = completed;
        this.solveOrder = List.copyOf(solveOrder);
    }

    public int getPuzzleNumber() { return puzzleNumber; }
    public int getMistakes() { return mistakes; }
    public boolean isCompleted() { return completed; }
    public List<String> getSolveOrder() { return solveOrder; }

    @Override
    public String toString() {
        return "ConnectionsResult{puzzle=%d, mistakes=%d, completed=%b, solveOrder=%s, comment='%s', author='%s'}"
                .formatted(puzzleNumber, mistakes, completed, solveOrder, getComment(), getDiscordAuthor());
    }
}
