package com.centraldungeon.features.files.dto;

import org.jspecify.annotations.Nullable;

/**
 * One place a file is being used, as its owner sees it in their own list.
 *
 * <p>It is what turns the reuse history from a list of filenames into something a person
 * recognises: "the sheet I use on Children of the Void" rather than "sheet-thalia-n5.pdf". It is also how
 * the owner can tell what the retention purge would reclaim - a file with no uses at all is the one
 * about to go quiet.
 *
 * <p><b>A use is not a category, though it names one.</b> A category says the file has belonged to a flow
 * and is never revoked; this says it is linked somewhere right now, and vanishes when the link does.
 *
 * <p>The enum leaves as a string, like every other one on this boundary.
 *
 * @param category    the category this use puts the file in, as a string
 * @param contextId   the table the use belongs to, so the screen can link to it
 * @param contextName the table's name, which is what the reader actually recognises
 * @param membership  how the reader is joined to that table - {@code Master} (a live row in
 *                    {@code masters}), {@code Player} (an accepted registration) or null for neither.
 *                    The screen cannot guess it: directing a table is not deduced from the role,
 *                    and the view each one opens is a different screen. Master wins when both
 *                    hold, and null means the name has nowhere to link to
 */
public record FileUsageResponse(String category, String contextId, String contextName, @Nullable String membership) {
}
