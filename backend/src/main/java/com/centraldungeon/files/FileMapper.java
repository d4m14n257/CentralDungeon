package com.centraldungeon.files;

import com.centraldungeon.files.dto.AdminFileResponse;
import com.centraldungeon.files.dto.FileResponse;
import com.centraldungeon.files.dto.PublicFileResponse;
import com.centraldungeon.files.dto.SharedFileResponse;
import com.centraldungeon.files.dto.TableFileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Turns file entities into the five response shapes. Wired as a @Bean in
 * common/config/MapperConfig.java, like every other mapper - see that class for why.
 *
 * <p>Five shapes and not one with nullable fields, because they have five different audiences and
 * each is allowed to see a different amount (arquitectura.md 2.3): the owner, an admin, the people
 * running a table, the people playing at one, and anybody picking from what the platform published.
 *
 * <p>The enums leave as strings, which is what the API contract asks for and what the frontend
 * models as a union of literals. Note that {@link FileType} crosses HTTP under its <b>constant
 * name</b> - {@code SingleUse} - and not under the hyphenated value the column holds: that spelling
 * is storage's business and stops at {@link FileTypeConverter}.
 */
@Mapper
public interface FileMapper {

    /**
     * The owner's view of their own file.
     *
     * @param file the entity to describe
     * @return the file as its owner sees it, in the reuse history and after an upload
     */
    @Mapping(target = "fileType", expression = "java(file.getFileType().name())")
    @Mapping(target = "publicAudience", expression = "java(file.getPublicAudience() == null ? null : file.getPublicAudience().name())")
    FileResponse toResponse(StoredFile file);

    /**
     * The /admin/files view.
     *
     * <p>{@code uses} is resolved by the service, not here: it needs a query, and a mapper never
     * touches a repository (arquitectura.md 2.2).
     *
     * @param file the entity to describe
     * @param uses how many tables hold a live link to it (#79)
     * @return the file as an admin sees it, owner and usage count included
     */
    @Mapping(target = "id", source = "file.id")
    @Mapping(target = "name", source = "file.name")
    @Mapping(target = "mimeType", source = "file.mimeType")
    @Mapping(target = "sizeBytes", source = "file.sizeBytes")
    @Mapping(target = "lastUsedAt", source = "file.lastUsedAt")
    @Mapping(target = "createdAt", source = "file.createdAt")
    @Mapping(target = "ownerId", expression = "java(file.getUserCreated().getId())")
    @Mapping(target = "ownerName", expression = "java(file.getUserCreated().getDiscordUsername())")
    @Mapping(target = "status", expression = "java(file.getStatus().name())")
    @Mapping(target = "fileType", expression = "java(file.getFileType().name())")
    @Mapping(target = "publicAudience", expression = "java(file.getPublicAudience() == null ? null : file.getPublicAudience().name())")
    AdminFileResponse toAdminResponse(StoredFile file, long uses);

    /**
     * One row of the master's Archivos tab: the file and the link that put it there.
     *
     * @param file        the attached file
     * @param link        the attachment itself, which is where {@code isPrivate} lives (#79)
     * @param isOwnedByMe whether the actor uploaded the file, resolved by the service from the token
     * @return the attachment as the people running the table see it
     */
    @Mapping(target = "fileId", expression = "java(file.getId())")
    @Mapping(target = "name", source = "file.name")
    @Mapping(target = "mimeType", source = "file.mimeType")
    @Mapping(target = "sizeBytes", source = "file.sizeBytes")
    @Mapping(target = "fileType", expression = "java(file.getFileType().name())")
    @Mapping(target = "tableFileType", expression = "java(link.getTableFileType().name())")
    @Mapping(target = "isPrivate", expression = "java(link.isPrivate())")
    @Mapping(target = "attachedAt", source = "link.createdAt")
    TableFileResponse toTableFileResponse(StoredFile file, TableFile link, boolean isOwnedByMe);

    /**
     * One row of what a candidate or a player sees on a table. Only shared attachments reach this:
     * a private one is absent from the list, never listed and hidden.
     *
     * @param file the attached file
     * @param link the attachment, for what the file is doing on the table
     * @return the attachment as a reader of the table sees it
     */
    @Mapping(target = "fileId", expression = "java(file.getId())")
    @Mapping(target = "name", source = "file.name")
    @Mapping(target = "mimeType", source = "file.mimeType")
    @Mapping(target = "sizeBytes", source = "file.sizeBytes")
    @Mapping(target = "tableFileType", expression = "java(link.getTableFileType().name())")
    SharedFileResponse toSharedResponse(StoredFile file, TableFile link);

    /**
     * One row of what the platform published, for whoever is choosing one to attach (#64, #79).
     *
     * @param file the published file
     * @return the file as the picker offers it
     */
    @Mapping(target = "publicAudience", expression = "java(file.getPublicAudience() == null ? null : file.getPublicAudience().name())")
    PublicFileResponse toPublicResponse(StoredFile file);
}
