package com.centraldungeon.matrix;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Cells where the code and the expected matrix disagree and somebody still has to decide which of the
 * two is wrong.
 *
 * <p>This is a strict expected failure. The cell shows {@code [pending decision]} in its name and passes
 * only while the observed status stays the one written here. If it changes in any direction, including
 * towards what the matrix expects, the cell fails and asks for the entry to be removed, so a list like
 * this one cannot quietly go stale.
 */
final class KnownDisagreements {

    /**
     * One disagreeing cell.
     *
     * @param row      the row's name, as {@link MatrixRow#name()} prints it
     * @param actor    the column
     * @param observed the status the code answers today
     * @param reason   what disagrees with what, in a sentence
     */
    record Entry(String row, MatrixActor actor, int observed, String reason) {
    }

    /** Every open disagreement. Empty until a run finds one and it gets triaged. */
    static final List<Entry> ENTRIES = List.of();

    private KnownDisagreements() {
    }

    /**
     * @param row   the row's name
     * @param actor the column
     * @return the entry for that cell, or null when the cell has to meet its expectation
     */
    static @Nullable Entry find(String row, MatrixActor actor) {
        return ENTRIES.stream()
                .filter(entry -> entry.row().equals(row) && entry.actor() == actor)
                .findFirst()
                .orElse(null);
    }
}
