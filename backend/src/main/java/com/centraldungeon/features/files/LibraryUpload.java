package com.centraldungeon.features.files;

import com.centraldungeon.features.files.dto.AdminFileResponse;

/**
 * What an admin's upload into the platform's library produced. Internal, like
 * {@link UploadResult}: it never crosses HTTP - {@code AdminFileController} sends the file and turns
 * the flag into the status code.
 *
 * <p>Its own record rather than {@link UploadResult} because what comes back is a different shape:
 * the library row an admin sees, with its owner and its usage count, not somebody's own file.
 *
 * @param file         the file, in the library with the categories asked for - published or not, as the
 *                     upload asked
 * @param deduplicated true when the admin had already uploaded this exact content and their existing
 *                     row came back into the library, rather than a new one being written. The
 *                     controller turns this into 200 rather than 201: nothing was created
 */
public record LibraryUpload(AdminFileResponse file, boolean deduplicated) {
}
