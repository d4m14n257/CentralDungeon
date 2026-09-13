package com.centraldungeon.registrations.dto;

/**
 * A file attached to an application, as whoever may read that application sees it: the applicant
 * themselves, or a master of the table they applied to.
 *
 * <p>Its own record rather than one of {@code files.dto}'s: none has this exact shape without
 * carrying a field this reader has no business seeing. {@code SharedFileResponse} and
 * {@code TableFileResponse} carry {@code tableFileType}, which an application has no such thing as;
 * {@code FileResponse} and {@code PublicFileResponse} carry {@code categories} and other
 * owner-or-admin fields nobody reading somebody else's application should get. {@code tasks}'
 * {@code SubmittedFileResponse} has the exact shape, but {@code registrations} may not depend on
 * {@code tasks} (arquitectura.md 2.1) - so this is that same shape, declared again in its own
 * feature rather than borrowed from one it cannot import.
 *
 * @param fileId    the file's identifier, which is what the download endpoint takes
 * @param name      the original filename, which is what the reader recognises it by (#80)
 * @param mimeType  the declared MIME type, so the screen can show the right icon
 * @param sizeBytes the size as it was uploaded, before compression (#75)
 */
public record RegistrationFileResponse(String fileId, String name, String mimeType, long sizeBytes) {
}
