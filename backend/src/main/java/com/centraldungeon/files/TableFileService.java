package com.centraldungeon.files;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.files.dto.LinkTableFileRequest;
import com.centraldungeon.files.dto.SharedFileResponse;
import com.centraldungeon.files.dto.TableFileResponse;
import com.centraldungeon.files.dto.UpdateTableFileRequest;
import com.centraldungeon.tables.MasterService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a table has attached: putting a file on it, changing who sees it, and taking it off again.
 *
 * <p><b>This links, it never copies</b> (#79), and every method here is written so that stays true.
 * Detaching a file leaves the file alone; editing the file changes what every table using it shows.
 * That is the whole reason a master can attach the community's default character sheet rather than
 * uploading their own copy of it, and it is why "remove from this table" and "delete this file" are
 * two different operations living in two different classes.
 *
 * <p><b>Every mutation answers to pertenencia and not to a role</b> (#17, #121, #135): running
 * <em>this</em> table is what authorizes touching its files. {@code hasRole('MASTER')} says "runs
 * some table", which is a different sentence, and no {@code @PreAuthorize} can tell them apart.
 *
 * <p>Lives in {@code files/} rather than {@code tables/}, the same way {@code TableCatalogService}
 * lives in {@code catalogs/}: the link belongs to the domain it is a link <em>of</em>.
 */
@Service
public class TableFileService {

    /** The {@code table_files} rows. */
    private final TableFileRepository tableFileRepository;

    /** The files themselves, for the metadata each link is shown with. */
    private final StoredFileRepository fileRepository;

    /** Resolves and authorizes the file being attached, and stamps its last use (#75). */
    private final FileService fileService;

    /** Answers pertenencia: a row in {@code masters}, never the platform role (#135). */
    private final MasterService masterService;

    /** Entity to DTO. */
    private final FileMapper fileMapper;

    /**
     * @param tableFileRepository the {@code table_files} rows
     * @param fileRepository      the files each link points at
     * @param fileService         resolves and authorizes the file being attached (#79)
     * @param masterService       answers pertenencia (#17, #121, #135)
     * @param fileMapper          entity to DTO
     */
    public TableFileService(
            TableFileRepository tableFileRepository,
            StoredFileRepository fileRepository,
            FileService fileService,
            MasterService masterService,
            FileMapper fileMapper) {
        this.tableFileRepository = tableFileRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.masterService = masterService;
        this.fileMapper = fileMapper;
    }

    /**
     * Everything attached to a table, as the people running it see it - private notes included.
     *
     * <p>A list and not a page: it is bounded by what one table's masters chose to attach and read as
     * one shelf, the same criterion the calendar and the status history use.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return its attachments, oldest first - the order they were put there, which is the order
     *         somebody who assembled them remembers
     * @throws ForbiddenActionException if the actor does not run the table
     */
    @Transactional(readOnly = true)
    public List<TableFileResponse> listForTable(String gameTableId, String actorId) {
        requireMasterOf(gameTableId, actorId);
        List<TableFile> links = tableFileRepository.findById_GameTableIdAndStatus(gameTableId, TableFileStatus.Current);
        Map<String, StoredFile> files = filesOf(links);
        return links.stream()
                .filter(link -> files.containsKey(link.getId().fileId()))
                .sorted(Comparator.comparing(TableFile::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(link -> {
                    StoredFile file = files.get(link.getId().fileId());
                    return fileMapper.toTableFileResponse(file, link, file.getUserCreated().getId().equals(actorId));
                })
                .toList();
    }

    /**
     * Attaches a file to a table.
     *
     * <p><b>Re-attaching something that was taken off revives its row rather than inserting a second
     * one.</b> The pair {@code (table, file)} is the primary key, so a master who removes a map and
     * puts it back a minute later would otherwise collide with the row they just removed - the same
     * trap {@code TableScheduleStatus} documents for the agenda, and the reason a detach marks
     * instead of deleting.
     *
     * @param gameTableId the table
     * @param request     the file to attach and how
     * @param actorId     the actor, from the token
     * @return the attachment
     * @throws ForbiddenActionException if the actor does not run the table, or the file is somebody
     *                                  else's and is not published (#79)
     * @throws NotFoundException        if the file is not there or was marked gone
     */
    @Transactional
    public TableFileResponse attach(String gameTableId, LinkTableFileRequest request, String actorId) {
        requireMasterOf(gameTableId, actorId);
        StoredFile file = fileService.requireAttachable(request.fileId(), actorId);

        TableFile link = tableFileRepository.findById(new TableFileId(gameTableId, file.getId()))
                .orElseGet(() -> new TableFile(gameTableId, file.getId(), request.tableFileType(), request.isPrivate()));
        link.setTableFileType(request.tableFileType());
        link.setPrivate(request.isPrivate());
        link.setStatus(TableFileStatus.Current);
        link.setDeletedAt(null);

        TableFile saved = tableFileRepository.save(link);
        return fileMapper.toTableFileResponse(file, saved, file.getUserCreated().getId().equals(actorId));
    }

    /**
     * Changes what an attachment is for, or who sees it - without touching the file.
     *
     * <p>Sharing a map on this table says nothing about the same map on another one: {@code isPrivate}
     * belongs to the link, which is exactly what {@code table_files} exists to make possible (#79).
     *
     * @param gameTableId the table
     * @param fileId      the attached file
     * @param request     what it should be for, and whether it stays with the masters
     * @param actorId     the actor, from the token
     * @return the attachment after the change
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws NotFoundException        if the file is not attached to it
     */
    @Transactional
    public TableFileResponse update(
            String gameTableId, String fileId, UpdateTableFileRequest request, String actorId) {
        requireMasterOf(gameTableId, actorId);
        TableFile link = requireAttached(gameTableId, fileId);
        link.setTableFileType(request.tableFileType());
        link.setPrivate(request.isPrivate());

        StoredFile file = requireFile(fileId);
        return fileMapper.toTableFileResponse(file, link, file.getUserCreated().getId().equals(actorId));
    }

    /**
     * Takes a file off a table.
     *
     * <p><b>The file survives, in full</b> (#79). It stays in its owner's history, it stays attached to
     * every other table that has it, and if the platform published it, it stays published. What ends
     * is this one attachment. Removing the community's default sheet from one table cannot be allowed
     * to remove it from the community.
     *
     * @param gameTableId the table
     * @param fileId      the file to take off
     * @param actorId     the actor, from the token
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws NotFoundException        if the file is not attached to it
     */
    @Transactional
    public void detach(String gameTableId, String fileId, String actorId) {
        requireMasterOf(gameTableId, actorId);
        TableFile link = requireAttached(gameTableId, fileId);
        link.setStatus(TableFileStatus.Deleted);
        link.setDeletedAt(LocalDateTime.now());
    }

    /**
     * What a table shares with its candidates and players, for the table's own detail.
     *
     * <p><b>No authorization here, on purpose.</b> This travels inside
     * {@code GameTableDetailResponse}, and that read has already decided who may see the table -
     * down to answering 404 to somebody vetoed (#29). Checking again here would be a second copy of a
     * rule that can only drift out of step with the first. Same decision F1.3 took for the calendar.
     *
     * <p>Private attachments are simply absent. Not listed and locked: absent. A player has no reason
     * to know their master keeps notes, and a row they cannot open is worse than no row.
     *
     * @param gameTableId the table
     * @return its shared files, oldest first. Empty when it shares none
     */
    @Transactional(readOnly = true)
    public List<SharedFileResponse> sharedFilesOf(String gameTableId) {
        List<TableFile> links = tableFileRepository.findById_GameTableIdAndStatus(gameTableId, TableFileStatus.Current)
                .stream()
                .filter(link -> !link.isPrivate())
                .toList();
        Map<String, StoredFile> files = filesOf(links);
        return links.stream()
                .filter(link -> files.containsKey(link.getId().fileId()))
                .sorted(Comparator.comparing(TableFile::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(link -> fileMapper.toSharedResponse(files.get(link.getId().fileId()), link))
                .toList();
    }

    /**
     * Loads the files a set of links points at, in one round trip.
     *
     * <p>Files marked gone are left out of the map, which is what makes every caller skip them: an
     * owner deleting a file removes it from the tables that showed it, without those links having to
     * be found and rewritten (#25).
     */
    private Map<String, StoredFile> filesOf(List<TableFile> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        List<String> fileIds = links.stream().map(link -> link.getId().fileId()).toList();
        return fileRepository.findAllById(fileIds).stream()
                .filter(file -> file.getStatus() == FileStatus.Current)
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
    }

    /** The live attachment, or 404. */
    private TableFile requireAttached(String gameTableId, String fileId) {
        return tableFileRepository.findById(new TableFileId(gameTableId, fileId))
                .filter(link -> link.getStatus() == TableFileStatus.Current)
                .orElseThrow(() -> new NotFoundException("File " + fileId + " is not attached to table " + gameTableId));
    }

    /** The live file behind a link, or 404. */
    private StoredFile requireFile(String fileId) {
        return fileRepository.findByIdAndStatus(fileId, FileStatus.Current)
                .orElseThrow(() -> new NotFoundException("File " + fileId + " not found"));
    }

    /**
     * The membership gate (#17, #121, #135): a row in {@code masters}, not the {@code Master} role.
     *
     * @throws ForbiddenActionException if the actor does not run this table
     */
    private void requireMasterOf(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("User " + actorId + " does not run table " + gameTableId);
        }
    }
}
