package com.centraldungeon.files.dto;

import com.centraldungeon.files.FileCategory;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Publishing a file for the whole platform, into the cajones it is offered in (#233).
 *
 * <p>Only an admin sends this, and the list cannot be empty. That is M24.1's fix carried across from
 * the audience it replaced: the legacy returned every public file everywhere, so a document written
 * for masters turned up in front of a player. A published file has to say which flow it is for, and
 * refusing to publish without one is what keeps that from happening by omission.
 *
 * <p><b>Plural, and that is the point of the whole redesign</b> (#233). The community's blank sheet is
 * asked for while a table recruits <em>and</em> once it is running, so it is published into
 * {@code TableMaterial} and {@code MasterRequest} at once - one file, one blob, two rows. The column
 * this replaced would have forced a choice and quietly broken the other flow.
 *
 * <p>The two player-side cajones are refused by the service: they hold what individual people
 * answered with, and a blank offered to everybody is not an answer.
 *
 * @param categories the cajones the file is offered in. At least one, all of them publishable
 */
public record PublishFileRequest(@NotEmpty List<FileCategory> categories) {
}
