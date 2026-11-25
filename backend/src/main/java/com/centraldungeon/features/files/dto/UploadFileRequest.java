package com.centraldungeon.features.files.dto;

import com.centraldungeon.features.files.FileCategory;
import com.centraldungeon.features.files.FileType;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * The metadata half of an upload. The bytes and the filename come from the multipart part itself.
 *
 * <p>Only two of the three lifecycles can be asked for here: {@code Public} is not something
 * an uploader declares, it is what an admin's upload into the platform's library produces, through
 * {@link PublishFileRequest}. Letting this record carry it would be letting anybody publish to
 * the whole platform, which is the same mistake the catalogs refuse.
 *
 * @param fileType     whether the uploader is keeping this in their history ({@code Private}) or it
 *                     is tied to the context they are uploading it for ({@code SingleUse}). Validated
 *                     in the service, which is where "not Public" is a rule rather than a format
 * @param fileCategory the category to put it in, or <b>null</b> - which is the normal case. A
 *                     file uploaded to attach to a table or to answer a request is classified by the
 *                     link that follows, because the flow knows what the uploader cannot be asked to
 *                     declare. It only arrives set from the one screen with no flow to observe:
 *                     somebody's own library
 */
public record UploadFileRequest(@NotNull FileType fileType, @Nullable FileCategory fileCategory) {
}
