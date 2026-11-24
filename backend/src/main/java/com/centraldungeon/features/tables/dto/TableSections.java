package com.centraldungeon.features.tables.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The four optional sections of rich text a master can add to a table, each shown under its own
 * heading on the detail. The same shape travels in both directions: in the create and edit requests,
 * and in the detail the table answers with.
 *
 * <p>Every section is sanitized before it is stored and again before it is shown, like the
 * description. A section left null - or written empty - is one the master did not write, and the
 * detail does not show it.
 *
 * @param characterCreation how characters are made for this table: level, method, allowed sources
 * @param whatToExpect      what a session is like, so a player can tell whether it is for them
 * @param equipment         what a player needs to have at hand: dice, a microphone, a character sheet
 * @param safety            the safety tools used and the content warnings, in the master's words; the
 *                          filterable part of this lives in the table's tags
 */
public record TableSections(
        @Size(max = MAX_TEXT_LENGTH) @Nullable String characterCreation,
        @Size(max = MAX_TEXT_LENGTH) @Nullable String whatToExpect,
        @Size(max = MAX_TEXT_LENGTH) @Nullable String equipment,
        @Size(max = MAX_TEXT_LENGTH) @Nullable String safety) {

    /**
     * The longest any rich text of a table may be, in characters of its HTML, the same bound the form
     * enforces. Without it a master could store megabytes that are sanitized again and sent to every
     * reader of the table.
     */
    public static final int MAX_TEXT_LENGTH = 10_000;

    /** No section written: what a request that leaves them out means. */
    public static final TableSections EMPTY = new TableSections(null, null, null, null);
}
