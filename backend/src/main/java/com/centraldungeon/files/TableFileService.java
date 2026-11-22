package com.centraldungeon.files;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.files.dto.LinkTableFileRequest;
import com.centraldungeon.files.dto.SharedFileResponse;
import com.centraldungeon.files.dto.TableFileResponse;
import com.centraldungeon.files.dto.UpdateTableFileRequest;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.TableVisibilityService;
import com.centraldungeon.tasks.TaskFile;
import com.centraldungeon.tasks.TaskFileRepository;
import com.centraldungeon.tasks.TaskFileStatus;
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
 * <p><b>This links, it never copies</b>, and every method here is written so that stays true.
 * Detaching a file leaves the file alone; editing the file changes what every table using it shows.
 * That is the whole reason a master can attach the community's default character sheet rather than
 * uploading their own copy of it, and it is why "remove from this table" and "delete this file" are
 * two different operations living in two different classes.
 *
 * <p><b>Every mutation answers to membership and not to a role</b>: running
 * <em>this</em> table, a row in {@code masters}, is what authorizes touching its files. {@code hasRole('MASTER')} says "runs
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

    /** Resolves and authorizes the file being attached, and stamps its last use. */
    private final FileService fileService;

    /** Answers whether the actor runs the table: a row in {@code masters}, never the platform role. */
    private final MasterService masterService;

    /** Entity to DTO. */
    private final FileMapper fileMapper;

    /** The blank forms a table's requests attach, which leave with the table when it is deleted. */
    private final TaskFileRepository taskFileRepository;

    /** Whether a table is there for the actor, asked before a refusal could confirm it. */
    private final TableVisibilityService tableVisibilityService;

    /**
     * @param tableFileRepository the {@code table_files} rows
     * @param fileRepository      the files each link points at
     * @param fileService         resolves and authorizes the file being attached
     * @param masterService       answers whether the actor runs the table
     * @param fileMapper          entity to DTO
     * @param taskFileRepository  the {@code task_files} rows of the table's requests
     * @param tableVisibilityService whether the table is there for the actor, before being refused on it
     */
    public TableFileService(
            TableFileRepository tableFileRepository,
            StoredFileRepository fileRepository,
            FileService fileService,
            MasterService masterService,
            FileMapper fileMapper,
            TaskFileRepository taskFileRepository,
            TableVisibilityService tableVisibilityService) {
        this.tableFileRepository = tableFileRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.masterService = masterService;
        this.fileMapper = fileMapper;
        this.taskFileRepository = taskFileRepository;
        this.tableVisibilityService = tableVisibilityService;
    }

    /**
     * Detaches every file a deleted table held: its own attachments and the blank forms of its requests.
     *
     * <p>Part of deleting the table, in its transaction and with its instant. A link that stayed live
     * would keep reporting the table as a use of the file - to its owner, who would see a table that is
     * gone, and to the purge, which would never reach a file only that table held. The rows are marked,
     * never dropped, like every other link.
     *
     * @param gameTableId the table being deleted
     * @param deletedAt   the table's own deletion instant, stamped on every link it takes with it
     */
    @Transactional
    public void detachAllOfDeletedTable(String gameTableId, LocalDateTime deletedAt) {
        for (TableFile link : tableFileRepository.findById_GameTableIdAndStatus(gameTableId, TableFileStatus.Current)) {
            link.setStatus(TableFileStatus.Deleted);
            link.setDeletedAt(deletedAt);
        }
        for (TaskFile link : taskFileRepository.findLiveByGameTableId(gameTableId)) {
            link.setStatus(TaskFileStatus.Detached);
            link.setDeletedAt(deletedAt);
        }
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
     * @throws NotFoundException        if the table does not exist, or the actor cannot see it - the
     *                                  same answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but neither runs it nor is an admin
     */
    @Transactional(readOnly = true)
    public List<TableFileResponse> listForTable(String gameTableId, String actorId) {
        tableVisibilityService.requireExisting(gameTableId);
        // Read by the table's masters and by any admin; its writes stay the masters' own.
        if (!masterService.canOversee(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("Only a master of this table or an admin can view its files");
        }
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
     * @throws ForbiddenActionException if the actor sees the table but does not run it, or the file is
     *                                  somebody else's and is not published
     * @throws NotFoundException        if the table does not exist or the actor cannot see it - the
     *                                  same answer as a table that does not exist - or if the file is
     *                                  not there or was marked gone
     */
    @Transactional
    public TableFileResponse attach(String gameTableId, LinkTableFileRequest request, String actorId) {
        requireMasterOf(gameTableId, actorId);
        StoredFile file = fileService.requireAttachable(request.fileId(), actorId);
        // The link is what classifies the file, not the upload (ADR-14 §4): attaching it to a table is
        // what makes it table material, and the membership stands even if it is detached later.
        fileService.classify(file.getId(), FileCategory.TableMaterial);

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
     * belongs to the link, which is exactly what {@code table_files} exists to make possible.
     *
     * @param gameTableId the table
     * @param fileId      the attached file
     * @param request     what it should be for, and whether it stays with the masters
     * @param actorId     the actor, from the token
     * @return the attachment after the change
     * @throws ForbiddenActionException if the actor sees the table but does not run it
     * @throws NotFoundException        if the table does not exist or the actor cannot see it - the
     *                                  same answer as a table that does not exist - or if the file is
     *                                  not attached to it
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
     * <p><b>The file survives, in full</b>. It stays in its owner's history, it stays attached to
     * every other table that has it, and if the platform published it, it stays published. What ends
     * is this one attachment. Removing the community's default sheet from one table cannot be allowed
     * to remove it from the community.
     *
     * @param gameTableId the table
     * @param fileId      the file to take off
     * @param actorId     the actor, from the token
     * @throws ForbiddenActionException if the actor sees the table but does not run it
     * @throws NotFoundException        if the table does not exist or the actor cannot see it - the
     *                                  same answer as a table that does not exist - or if the file is
     *                                  not attached to it
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
     * down to answering 404 to somebody vetoed (ADR-09 §4). Checking again here would be a second copy of a
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
     * be found and rewritten (ADR-03 §7).
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
     * The membership gate: a row in {@code masters}, not the {@code Master} role.
     *
     * @throws NotFoundException        if the table does not exist or the actor cannot see it - the
     *                                  same answer as a table that does not exist
     * @throws ForbiddenActionException if the actor sees the table but does not run it
     */
    private void requireMasterOf(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            // Said only about a table they may see: to anybody else it is the same 404 as an id nobody used.
            tableVisibilityService.requireVisible(gameTableId, actorId);
            throw new ForbiddenActionException("User " + actorId + " does not run table " + gameTableId);
        }
    }
}
