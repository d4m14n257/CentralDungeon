package com.centraldungeon.tables;

/**
 * A table somebody is already committed to, named so the clash can be explained instead of merely
 * refused.
 *
 * <p>The name travels with the id because every caller of {@link ScheduleConflictService} ends up
 * writing it into a message a person reads: "clashes with <i>La cripta</i>, Tuesdays at 20:00".
 * Answering with a boolean would force each of them to load the table again just to say which one
 * it was - and principle 2 of frontend-diseno.md 1 asks the interface to say why, not to grey a
 * button out in silence.
 *
 * @param id   the table's identifier
 * @param name the table's title, as it is shown to whoever is being told about the clash
 */
public record CommittedTable(String id, String name) {
}
