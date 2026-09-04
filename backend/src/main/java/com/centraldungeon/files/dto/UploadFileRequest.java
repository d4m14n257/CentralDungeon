package com.centraldungeon.files.dto;

import com.centraldungeon.files.FileType;
import jakarta.validation.constraints.NotNull;

/**
 * The metadata half of an upload. The bytes and the filename come from the multipart part itself.
 *
 * <p>Only two of the three lifecycles of #68 can be asked for here: {@code Public} is not something
 * an uploader declares, it is something an admin grants afterwards through
 * {@link PublishFileRequest}. Letting this record carry it would be letting anybody publish to the
 * whole platform, which is the same mistake #55 refuses for catalogs.
 *
 * @param fileType whether the uploader is keeping this in their history ({@code Private}) or it is
 *                 tied to the context they are uploading it for ({@code SingleUse}). Validated in the
 *                 service, which is where "not Public" is a rule rather than a format
 */
public record UploadFileRequest(@NotNull FileType fileType) {
}
