package com.centraldungeon.registrations;

/**
 * One file attached to one application, already joined to the file it points at. Internal
 * projection: it never crosses HTTP - {@code RegistrationFileResponse} is what does.
 *
 * <p>It exists so a page of applications costs one query instead of one plus one per row: the link
 * table alone would give ids, and turning those into names would be a second round trip per
 * application. Same shape and same reason as {@code TaskFileRow}.
 *
 * @param registrationId the application the file is attached to, which is what the service groups by
 * @param fileId          the file
 * @param name            the original filename. Metadata only (#80)
 * @param mimeType        the declared MIME type
 * @param sizeBytes       the size as it was uploaded, before compression (#75)
 */
public record RegistrationFileRow(String registrationId, String fileId, String name, String mimeType, long sizeBytes) {
}
