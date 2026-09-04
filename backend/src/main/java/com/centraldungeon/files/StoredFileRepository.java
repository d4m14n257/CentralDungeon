package com.centraldungeon.files;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code files}.
 *
 * <p>{@code JpaSpecificationExecutor} is what /admin/files searches through, over
 * {@link FileSearchSpecification} - the same shape the catalogs use for their admin screen (#164).
 */
public interface StoredFileRepository extends JpaRepository<StoredFile, String>, JpaSpecificationExecutor<StoredFile> {

    /**
     * One live file by id. The status is in the {@code WHERE} rather than checked afterwards, so a
     * file that was marked gone is simply not found (#25).
     *
     * @param id     the file
     * @param status the status it has to be in
     * @return the file, or empty when it does not exist or is not in that status
     */
    Optional<StoredFile> findByIdAndStatus(String id, FileStatus status);

    /**
     * Somebody's own files, newest first - the reuse history of #65.
     *
     * <p>The owner is in the {@code WHERE} and comes from the token, never from the URL (#121). This
     * is the query the {@code FilePicker} is built on: reusing what is already uploaded is the point,
     * and it only works if seeing your own history is one round trip.
     *
     * @param userId   the owner, from the token
     * @param status   the status to include - always {@link FileStatus#Current} in practice
     * @param pageable the page and its order, defaulted by the controller (#171, #173)
     * @return their files, page by page
     */
    Page<StoredFile> findByUserCreated_IdAndStatus(String userId, FileStatus status, Pageable pageable);

    /**
     * What the platform published for a given audience (#64).
     *
     * @param fileType       always {@link FileType#Public} - a parameter so the constant stays out of
     *                       the query and the converter handles it (see {@link FileTypeConverter})
     * @param publicAudience who the files are for
     * @param status         the status to include
     * @param pageable       the page and its order
     * @return the published files for that audience, page by page
     */
    Page<StoredFile> findByFileTypeAndPublicAudienceAndStatus(
            FileType fileType, PublicAudience publicAudience, FileStatus status, Pageable pageable);

    /**
     * Everything the platform published, whatever the audience.
     *
     * @param fileType always {@link FileType#Public}
     * @param status   the status to include
     * @param pageable the page and its order
     * @return the published files, page by page
     */
    Page<StoredFile> findByFileTypeAndStatus(FileType fileType, FileStatus status, Pageable pageable);

    /**
     * The same content, already uploaded by the same person - the deduplication of #75.
     *
     * <p><b>Scoped to the owner, and that is the schema's doing, not a preference.</b>
     * {@code uk_files_storage_key} is unique, so two rows cannot share one blob; sharing bytes across
     * users would need a blob table with a reference count, which the baseline does not have. What
     * this does buy is the common case: the same character sheet attached to a second table costs
     * nothing.
     *
     * @param userId      the owner, from the token
     * @param contentHash SHA-256 of what is being uploaded
     * @param status      the status to match - only a live row can be handed back
     * @return the row that already holds this content, or empty
     */
    Optional<StoredFile> findFirstByUserCreated_IdAndContentHashAndStatus(
            String userId, String contentHash, FileStatus status);

    /**
     * The files the retention job of #75 should mark: live, unused for longer than the window, and
     * attached to nothing.
     *
     * <p>Three conditions, each with a reason:
     *
     * <ul>
     *   <li><b>Published files are exempt.</b> They belong to the platform rather than to a person,
     *       and a rules document nobody downloaded for three months is still the rules (#64).
     *   <li><b>{@code lastUsedAt} falls back to {@code createdAt}.</b> A file uploaded and never
     *       touched again has no last use, and treating that as "never used, keep forever" would
     *       exempt exactly the files the purge exists for.
     *   <li><b>A live link keeps a file alive.</b> A table that still shows a map needs its map,
     *       however long ago anybody opened it (#79).
     * </ul>
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param cutoff     the moment before which a last use counts as too old
     * @param publicType {@link FileType#Public}, passed rather than written into the query so the
     *                   attribute converter maps it
     * @param pageable   how many to take in one pass - the job works in batches, not all at once
     * @return the candidates, oldest use first
     */
    @Query("""
            select file from StoredFile file
            where file.status = com.centraldungeon.files.FileStatus.Current
              and file.fileType <> :publicType
              and coalesce(file.lastUsedAt, file.createdAt) < :cutoff
              and not exists (
                  select link from TableFile link
                  where link.id.fileId = file.id
                    and link.status = com.centraldungeon.files.TableFileStatus.Current)
            order by coalesce(file.lastUsedAt, file.createdAt) asc
            """)
    List<StoredFile> findPurgeCandidates(
            @Param("cutoff") LocalDateTime cutoff, @Param("publicType") FileType publicType, Pageable pageable);
}
