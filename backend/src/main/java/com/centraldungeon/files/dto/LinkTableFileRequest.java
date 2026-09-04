package com.centraldungeon.files.dto;

import com.centraldungeon.files.TableFileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Attaching an existing file to a table.
 *
 * <p><b>There is no upload here, and that is the design</b>: uploading is {@code POST /files} and
 * attaching is this. Splitting them is what makes "upload a new one" and "reuse one I already have"
 * end in the same call, which is exactly the choice the {@code FilePicker} of #65 offers - and reuse
 * is the cost lever of the whole fase, so it cannot be the path with the extra step.
 *
 * @param fileId        the file to attach. Either the actor's own or one the platform published
 *                      (#79) - never somebody else's private file, which the service refuses
 * @param tableFileType what the file is doing on the table: prepared beforehand, or produced at a
 *                      session
 * @param isPrivate     true to keep it to the people running the table. About this attachment only:
 *                      the same file can be shared on one table and private on another
 */
public record LinkTableFileRequest(@NotBlank String fileId, @NotNull TableFileType tableFileType, boolean isPrivate) {
}
