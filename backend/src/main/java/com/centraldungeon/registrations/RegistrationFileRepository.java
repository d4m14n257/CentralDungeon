package com.centraldungeon.registrations;

import com.centraldungeon.files.FileUsage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the {@code registration_files} bridge table - a candidate's character sheet (#60 uso 2). */
public interface RegistrationFileRepository extends JpaRepository<RegistrationFile, RegistrationFileId> {

    /**
     * The live attachments of a set of applications, already joined to their files - a candidate
     * queue of twenty is one query and not twenty plus twenty.
     *
     * <p>No filter on the application's own status: a rejected application still shows what it was
     * submitted with on {@code /my/applications}, and only a withdrawn one is excluded - by never
     * appearing in the page the caller built this list from. This is a listing query, not the read
     * rule of #247.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param registrationIds the applications
     * @return one row per live attachment whose file is still there, in no particular order; the
     *         service groups them by application
     */
    @Query("""
            select new com.centraldungeon.registrations.RegistrationFileRow(
                link.id.registrationId, file.id, file.name, file.mimeType, file.sizeBytes)
            from RegistrationFile link, com.centraldungeon.files.StoredFile file
            where link.id.registrationId in :registrationIds
              and link.status = com.centraldungeon.registrations.RegistrationFileStatus.Current
              and file.id = link.id.fileId
              and file.status = com.centraldungeon.files.FileStatus.Current
            """)
    List<RegistrationFileRow> findAttachmentsByRegistrationIds(@Param("registrationIds") Collection<String> registrationIds);

    /**
     * Which tables can reach a file through an application that attached it.
     *
     * <p>The seventh way a file becomes readable, and the sister of the fifth
     * ({@code SubmissionFileRepository.findTableIdsBySubmittedFileId}, #211): a master has to be able
     * to open the sheet a candidate applied with, and without this they would see the row on the
     * application and get a 404 opening it.
     *
     * <p><b>Critical (#247): only a live application counts.</b> {@code Candidate} and {@code Player}
     * are alive; {@code Rejected} and {@code Deleted} (withdrawn) are not. A withdrawn application
     * keeps its rows - #247 forbids touching them on withdrawal - but the master who ran that table
     * stops being able to open through this door once it is gone. Without this filter, withdrawing
     * would immunize the file against the purge of #75 forever and in silence, because it would
     * always look reachable to somebody.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param fileId the file somebody is trying to open
     * @return the ids of the tables whose live applications attach it. Empty when nothing live does
     */
    @Query("""
            select registration.gameTable.id
            from RegistrationFile link, TableRegistration registration
            where link.id.fileId = :fileId
              and link.status = com.centraldungeon.registrations.RegistrationFileStatus.Current
              and registration.id = link.id.registrationId
              and registration.status in (
                  com.centraldungeon.registrations.TableRegistrationStatus.Candidate,
                  com.centraldungeon.registrations.TableRegistrationStatus.Player)
            """)
    List<String> findTableIdsByApplicationFileId(@Param("fileId") String fileId);

    /**
     * The same reachability as {@link #findTableIdsByApplicationFileId}, but for a whole page of
     * files and carrying the table's name - the fourth source of the uses an owner sees on their own
     * list (#232).
     *
     * <p>It answers with {@link FileUsage}, a type of the {@code files} feature, which is the one
     * import that direction. The alternative is a second projection here that {@code FileService}
     * would immediately convert into the first, and the read rule already crosses this way for the
     * same reason (#206): whether an application's file is reachable is a question about
     * applications, and splitting it across two features is what let the two answers drift apart
     * once already.
     *
     * <p><b>The same liveness filter as {@link #findTableIdsByApplicationFileId}, and for the same
     * reason (#247):</b> a withdrawn application's file is a use that no longer counts. It is the
     * uses of this file that decide whether the purge of #75 may reclaim it, and a withdrawn
     * application is not a reason to keep it around.
     *
     * <p>Named parameters only, never positional (#124).
     *
     * @param fileIds the files to resolve uses for
     * @return one row per live attachment on a live application, absent for a file never applied with
     */
    @Query("""
            select new com.centraldungeon.files.FileUsage(
                link.id.fileId,
                com.centraldungeon.files.FileCategory.PlayerApplication,
                registration.gameTable.id,
                registration.gameTable.name)
            from RegistrationFile link, TableRegistration registration
            where link.id.fileId in :fileIds
              and link.status = com.centraldungeon.registrations.RegistrationFileStatus.Current
              and registration.id = link.id.registrationId
              and registration.status in (
                  com.centraldungeon.registrations.TableRegistrationStatus.Candidate,
                  com.centraldungeon.registrations.TableRegistrationStatus.Player)
            """)
    List<FileUsage> findUsagesByFileIds(@Param("fileIds") Collection<String> fileIds);
}
