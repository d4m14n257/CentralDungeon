package com.centraldungeon.files.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the owner may change about their own file: what it is called, and whether they are keeping it.
 *
 * <p>Both fields always travel, so the request describes the state the file should end in rather than
 * a delta - the same reasoning as ADR-04 §7. A partial update would mean "null means leave it alone",
 * which makes clearing a value impossible to express and every caller guess.
 *
 * <p><b>No cajón here</b> (ADR-14 §4). A membership is what a file's uses made true and is never revoked,
 * so there is nothing for an edit dialog to correct: the only ways in are to be used in a flow, or to
 * be put there deliberately on an upload that had no flow to observe.
 *
 * <p>Renaming touches nothing but metadata: the content lives under the file's id and has never had
 * anything to do with its name (ADR-13 §6).
 *
 * @param name          the new filename. Metadata only, and it still never reaches the filesystem
 * @param keepInLibrary true to keep the file in the reuse history (ADR-13 §2), which promotes a
 *                      {@code Single-use} to {@code Private} - the "save this for later" of ADR-13 §2, and
 *                      literally what {@code handlePrivateStatus} did in the legacy (M21.2). False
 *                      sends it back to being tied to its context, so the purge of ADR-13 §3 can reclaim it
 */
public record UpdateFileRequest(@NotBlank @Size(max = 256) String name, boolean keepInLibrary) {
}
