package com.centraldungeon.files;

import com.centraldungeon.files.dto.AdminFileResponse;

/**
 * What an admin's upload into the platform's library produced (#278). Internal, like
 * {@link UploadResult}: it never crosses HTTP - {@code AdminFileController} sends the file and turns
 * the flag into the status code.
 *
 * <p>Its own record rather than {@link UploadResult} because what comes back is a different shape:
 * the library row an admin sees, with its owner and its usage count, not somebody's own file.
 *
 * @param file         the file, already published into the cajones asked for
 * @param deduplicated true when the admin had already uploaded this exact content and their existing
 *                     row came back published, rather than a new one being written (#75, #234). The
 *                     controller turns this into 200 rather than 201: nothing was created
 */
public record PublishedUpload(AdminFileResponse file, boolean deduplicated) {
}
