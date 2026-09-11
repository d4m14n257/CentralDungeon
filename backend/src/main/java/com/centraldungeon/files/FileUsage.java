package com.centraldungeon.files;

/**
 * One place a file is being used, right now. Internal projection, like {@link FileUsageCount}: it
 * never crosses HTTP - {@code FileUsageResponse} is what does.
 *
 * <p><b>Not the same question as {@link FileCategory}, though it carries one.</b> A cajón says the
 * file <em>has belonged</em> to a flow and is never revoked; a usage says it <em>is linked</em>
 * somewhere at this moment, and disappears when the link does. Detaching a file from a table removes
 * a usage and leaves the cajón standing - which is exactly the difference #233 turns on.
 *
 * <p>It carries the cajón rather than a kind of its own because there would be nothing to tell them
 * apart: a link to a table is a {@code TableMaterial} use, and a second enum saying so would be the
 * same list under another name.
 *
 * <p>Resolved in one grouped query per source and never one per row - a page of twenty files costs
 * three round trips, not sixty.
 *
 * @param fileId      the file being used
 * @param category    the cajón this use puts it in
 * @param contextId   the table the use belongs to. For a submission or a request it is still a
 *                    table: the one whose task is involved
 * @param contextName how to name that context on screen - the table's name, so the owner recognises
 *                    the use without following a link
 */
public record FileUsage(String fileId, FileCategory category, String contextId, String contextName) {
}
