package com.centraldungeon.files;

import com.centraldungeon.files.dto.FileResponse;

/**
 * What an upload actually did, so the controller can say so in the status code (#234).
 *
 * <p>Not a DTO and not in {@code dto/}: what crosses HTTP is the {@link FileResponse} inside it plus
 * a 200 or a 201, and the flag stops at {@code FileController}. Adding a {@code deduplicated} field
 * to the response body instead would put a fact about <em>this request</em> onto a record that
 * describes <em>the file</em>, where every other reader of that shape would have to ignore it.
 *
 * <p>It exists because deduplication was invisible. Recognising an upload and handing back the row
 * somebody already had is the cheapest of the three levers of #75, and until now it answered 201
 * exactly like a fresh write - so the person who triggered it had no way to learn that reuse
 * happened, and no reason to expect it next time.
 *
 * @param file         the file, whether it was just written or recognised from an earlier upload
 * @param deduplicated true when the content was already there and the existing row was returned.
 *                     The controller turns this into 200 rather than 201, which is what those two
 *                     codes mean: nothing was created
 */
public record UploadResult(FileResponse file, boolean deduplicated) {
}
