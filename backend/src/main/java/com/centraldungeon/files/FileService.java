package com.centraldungeon.files;

import com.centraldungeon.common.config.StorageProperties;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.storage.StorageService;
import com.centraldungeon.files.dto.AdminFileResponse;
import com.centraldungeon.files.dto.FileResponse;
import com.centraldungeon.files.dto.FileUsageResponse;
import com.centraldungeon.files.dto.PublicFileResponse;
import com.centraldungeon.files.dto.PublishFileRequest;
import com.centraldungeon.files.dto.UpdateFileRequest;
import com.centraldungeon.files.dto.UploadFileRequest;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tasks.SubmissionFileRepository;
import com.centraldungeon.tasks.TaskFileRepository;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRoleRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The file itself: uploading it, finding it again, handing it over, and letting go of it.
 *
 * <p><b>Attaching a file to a table is not here</b> - that is {@link TableFileService}, because a
 * link and a file have different owners, different rules and different lifetimes (#79). This class
 * knows nothing about tables except when it has to answer whether somebody may read a file.
 *
 * <p><b>Reading is authorized by pertenencia, never by role</b> (#17, #121, #135). A file is
 * reachable through the tables it is attached to, and whether the reader belongs to any of them is a
 * membership question no {@code @PreAuthorize} can answer. {@code hasRole('MASTER')} would say "runs
 * some table", which is not the question.
 *
 * <p><b>Three levers hold the storage bill down, and none of them is a quota</b> (#75, which repealed
 * the per-user quota of #61 for being a limit on the person rather than on the volume): the same
 * upload is recognised and reused instead of stored twice, the bytes are compressed underneath
 * {@link StorageService}, and {@link FileRetentionService} lets go of what nobody has touched in
 * months.
 */
@Service
public class FileService {

    /**
     * The registrations a request's blank opens for: the people it reaches (#63).
     *
     * <p>Both, and not only {@code Player}: a request written for candidates is how a master asks
     * for a sheet <em>before</em> accepting somebody, which is the whole point of asking at that
     * moment. {@code Rejected} is absent - a request stops reaching you when you stop belonging.
     */
    private static final List<TableRegistrationStatus> REQUEST_READERS =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    /** The {@code files} rows. */
    private final StoredFileRepository fileRepository;

    /** The attachments, which is what makes a file reachable to anybody but its owner (#79). */
    private final TableFileRepository tableFileRepository;

    /** The cajones a file belongs to (#233). Add-only: nothing here ever takes a membership away. */
    private final FileCategoryLinkRepository categoryRepository;

    /**
     * The blanks a master attached to a request (#63) - the third source of uses, and the sixth way
     * a file becomes readable. Another feature's repository, dragged in by the same read rule that
     * dragged in {@code SubmissionFileRepository}: whether somebody may open the form their master
     * asked them to fill is a question about requests, and answering it anywhere else is how #206
     * drifted the first time.
     */
    private final TaskFileRepository taskFileRepository;

    /**
     * The other way a file becomes reachable: it was handed in as an answer to a table's task (#63).
     *
     * <p>A repository from another feature, which no other collaborator here is. It is the read rule
     * that drags it in: whether a master may open what their player submitted is a question about
     * submissions, and the alternative - a second download endpoint living in {@code tasks/} with its
     * own copy of the rule - is exactly the drift #206 already cost us once.
     */
    private final SubmissionFileRepository submissionFileRepository;

    /** Resolves the uploader from the token. */
    private final UserRepository userRepository;

    /** Where the bytes actually live (#15). */
    private final StorageService storageService;

    /** The cap, the whitelist and the retention window - all configuration, never constants. */
    private final StorageProperties storageProperties;

    /** Answers pertenencia: a row in {@code masters}, never the platform role (#135). */
    private final MasterService masterService;

    /**
     * The actor's platform roles, for deciding which cajones are theirs to use (#237).
     *
     * <p>The one place in this class that asks about a <b>role</b> rather than about pertenencia, and
     * it is not an authorization: it decides what somebody's own library can hold, not what they may
     * reach. Reaching is still membership, always (#17, #121, #135).
     */
    private final UserRoleRepository userRoleRepository;

    /**
     * Who belongs to a table, for the sixth way a file is reachable: a request's blank has to open
     * for the people the request reaches. Pertenencia again, and again a row rather than a role
     * (#17, #121, #135).
     */
    private final TableRegistrationRepository registrationRepository;

    /** Entity to DTO. */
    private final FileMapper fileMapper;

    /**
     * @param fileRepository           the {@code files} rows
     * @param tableFileRepository      the attachments, for resolving who may read a file
     * @param categoryRepository       the cajones each file belongs to (#233)
     * @param taskFileRepository       the blanks attached to requests, for the same two questions
     * @param submissionFileRepository the answers a file was handed in with, for the same question
     * @param userRepository           resolves the uploader from the token
     * @param storageService           where the bytes live (#15)
     * @param storageProperties        the cap, the whitelist and the retention window
     * @param masterService            answers pertenencia (#17, #121, #135)
     * @param userRoleRepository       the actor's roles, for their own cajones (#237)
     * @param registrationRepository   who belongs to a table, for reading a request's blank
     * @param fileMapper               entity to DTO
     */
    public FileService(
            StoredFileRepository fileRepository,
            TableFileRepository tableFileRepository,
            FileCategoryLinkRepository categoryRepository,
            TaskFileRepository taskFileRepository,
            SubmissionFileRepository submissionFileRepository,
            UserRepository userRepository,
            StorageService storageService,
            StorageProperties storageProperties,
            MasterService masterService,
            UserRoleRepository userRoleRepository,
            TableRegistrationRepository registrationRepository,
            FileMapper fileMapper) {
        this.fileRepository = fileRepository;
        this.tableFileRepository = tableFileRepository;
        this.categoryRepository = categoryRepository;
        this.taskFileRepository = taskFileRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.masterService = masterService;
        this.userRoleRepository = userRoleRepository;
        this.registrationRepository = registrationRepository;
        this.fileMapper = fileMapper;
    }

    /**
     * Takes an upload in.
     *
     * <p>The order of the checks is deliberate: <b>the type and the size are settled before a single
     * byte is written</b>, which is the cheapest place to refuse and the one the legacy skipped
     * entirely - {@code multer} ran with no {@code limits} at all (M21.3).
     *
     * <p>Then comes deduplication (#75): if this person already uploaded this exact content, they get
     * the row they already have. Attaching the same character sheet to a second table costs nothing,
     * which is the point of the whole reuse story.
     *
     * <p>Whether that recognition happened travels back in {@link UploadResult}, because until #234
     * it did not travel at all: the response was a 201 with somebody's older row, indistinguishable
     * from a fresh write, so the one lever of #75 a person can actually feel was invisible to them.
     *
     * @param upload      the multipart part: the bytes and the name the browser sent
     * @param request     which lifecycle the uploader wants for it (#68) and what it is (#233)
     * @param actorId     the uploader, from the token - never from the URL (#121)
     * @return the file, plus whether it was recognised rather than written (#234)
     * @throws InvalidRequestException if the part is empty, its type is not on the whitelist, it is
     *                                 over the cap, or the uploader asked for {@code Public} - which
     *                                 is an admin's to grant, not an uploader's to declare (#55, #64)
     * @throws NotFoundException       if the token names somebody who is not there
     */
    @Transactional
    public UploadResult upload(MultipartFile upload, UploadFileRequest request, String actorId) {
        if (request.fileType() == FileType.Public) {
            throw new InvalidRequestException(
                    "A file cannot be uploaded as Public - publishing is an admin action (#64)", "FILE_CANNOT_SELF_PUBLISH");
        }
        if (upload.isEmpty()) {
            throw new InvalidRequestException("The uploaded file is empty", "FILE_EMPTY");
        }
        requireAllowedMimeType(upload.getContentType());
        requireWithinSizeLimit(upload.getSize());
        requireOwnCajon(request.fileCategory(), actorId);

        byte[] content = readContent(upload);
        requireWithinSizeLimit(content.length);

        User owner = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User " + actorId + " not found"));
        String contentHash = sha256(content);

        Optional<StoredFile> alreadyUploaded =
                fileRepository.findFirstByUserCreated_IdAndContentHashAndStatus(actorId, contentHash, FileStatus.Current);
        if (alreadyUploaded.isPresent()) {
            StoredFile existing = alreadyUploaded.get();
            existing.setLastUsedAt(LocalDateTime.now());
            // A cajón asked for here is *added*, never swapped: memberships accumulate and are never
            // revoked (#233). Re-uploading the sheet you already had, this time from your library and
            // marked as application material, puts it in that cajón too without taking it out of the
            // ones its earlier uses put it in.
            classify(existing.getId(), request.fileCategory());
            return new UploadResult(fileMapper.toResponse(existing, List.of(), List.of()), true);
        }

        String storageKey = IdGenerator.newId();
        StoredFile file = new StoredFile(
                originalName(upload),
                storageKey,
                contentHash,
                upload.getContentType(),
                content.length,
                request.fileType(),
                owner);
        // Storage stages the bytes and only moves them into place when this transaction commits
        // (M26.2), so a failure below cannot leave the orphan the legacy left on every failed upload.
        storageService.store(storageKey, content);
        StoredFile saved = fileRepository.save(file);
        // Null in every flow: a file uploaded to attach to a table or to answer a request is
        // classified by the link that follows, not by the upload. It only arrives set from the one
        // screen with no flow to observe - somebody's own library (#233).
        classify(saved.getId(), request.fileCategory());
        return new UploadResult(fileMapper.toResponse(saved, List.of(), List.of()), false);
    }

    /**
     * The cajones this person may file something of their own under (#237).
     *
     * <p><b>The personal library and the platform's are two different libraries</b>, and this answers
     * for the first one only. {@code /my/files} is where somebody keeps what is theirs, to have it
     * to hand; {@code /admin/files} is the platform's, where an admin uploads what the community
     * offers and says which cajones it belongs to by publishing it. An admin does none of their
     * admin work in their own library, which is why the {@code Admin} role adds nothing here.
     *
     * <p>Three rules, and each one is a different kind of answer:
     *
     * <ul>
     *   <li><b>The two player-side cajones, for everybody.</b> Every account is created with
     *       {@code Player} (#38), so there is nobody who cannot apply to a table or answer a
     *       request - an admin included, because an admin is also a person who plays.
     *   <li><b>The two master-side ones, for whoever runs tables.</b> Not the role alone: a row in
     *       {@code masters} is what authorizes running a table, and somebody an admin assigned has
     *       no {@code Master} role at all (#135). Either answer opens them.
     *   <li><b>Never {@link FileCategory#Announcement}.</b> It is the community's by definition and
     *       lives only in the platform's library.
     * </ul>
     *
     * @param actorId whose library, from the token (#121)
     * @return the cajones they may use, in the order the enum declares them
     */
    @Transactional(readOnly = true)
    public List<FileCategory> personalCategoriesOf(String actorId) {
        boolean runsTables = userRoleRepository.findAllGrants(actorId).stream()
                        .anyMatch(grant -> PlatformRole.MASTER.roleName().equals(grant.getRole().getName()))
                || masterService.runsAnyTable(actorId);
        return Arrays.stream(FileCategory.values())
                .filter(FileCategory::isPersonal)
                .filter(category -> runsTables || isPlayerSide(category))
                .toList();
    }

    /** Whether a cajón is one of the two a plain member of the community fills by themselves. */
    private static boolean isPlayerSide(FileCategory category) {
        return category == FileCategory.PlayerApplication || category == FileCategory.PlayerSubmission;
    }

    /**
     * Puts a file in a cajón, if it is not in it already (#233).
     *
     * <p><b>Idempotent and add-only</b>, which is what lets every caller invoke it without first
     * asking whether it needs to: the pair is the primary key, so a master attaching their sheet to a
     * fifth table writes nothing. And nothing anywhere revokes a membership - detaching a file from a
     * table says the file is no longer that table's, never that it was never that table's material.
     *
     * <p>Called by the services that make the links rather than by the links themselves, because a
     * cajón is a consequence of the domain action and not of the row: {@code TableFileService} knows
     * it is attaching table material, {@code table_files} only knows it has two ids.
     *
     * @param fileId   the file
     * @param category the cajón, or null to do nothing - which is the normal case on an upload made
     *                 from inside a flow, where the link that follows classifies it
     */
    @Transactional
    public void classify(String fileId, @Nullable FileCategory category) {
        if (category == null) {
            return;
        }
        FileCategoryLinkId key = new FileCategoryLinkId(fileId, category);
        if (!categoryRepository.existsById(key)) {
            categoryRepository.save(new FileCategoryLink(fileId, category));
        }
    }

    /**
     * Somebody's own uploads, newest first - the history the {@code FilePicker} offers (#65) and what
     * /my/files lists.
     *
     * <p><b>This is the one read that resolves uses</b> (#232), and it is where the classification
     * the owner wants actually lands: a row that says "the sheet on Hijos del Vacío" is recognisable
     * in a way a filename is not, and a row that says nothing is the owner's warning that the purge
     * of #75 has its eye on that file.
     *
     * <p><b>The search box actually searches now.</b> The frontend has been sending {@code q} on
     * this call since the picker was built and this method had no parameter to receive it, so the
     * "buscar entre mis archivos" input filtered nothing at all - which on a history long enough to
     * need searching is the same as not having reuse (#65).
     *
     * @param actorId    whose files, from the token. The owner is in the {@code WHERE} (#121)
     * @param query      the search box over their filenames, or null for everything they have
     * @param category   the cajón to narrow to (#233), or null for everything they have
     * @param pageable   the page and its order, defaulted by the controller (#171, #173)
     * @return their live files with their cajones and their uses, page by page
     */
    @Transactional(readOnly = true)
    public PageResponse<FileResponse> listMine(
            String actorId, @Nullable String query, @Nullable FileCategory category, Pageable pageable) {
        SearchQuery parsed = SearchQueryParser.parse(query, FileSearchField.wireNames());
        Page<StoredFile> page =
                fileRepository.findAll(FileSearchSpecification.forOwner(actorId, parsed, category), pageable);
        Map<String, List<FileUsageResponse>> usages = usagesByFileId(page.getContent());
        Map<String, List<String>> cajones = categoriesByFileId(page.getContent());
        return PageResponse.from(page.map(file -> fileMapper.toResponse(
                file, cajones.getOrDefault(file.getId(), List.of()), usages.getOrDefault(file.getId(), List.of()))));
    }

    /**
     * Which cajones each of the given files belongs to, in one query for the whole page (#233).
     *
     * @param files the files to resolve cajones for
     * @return the cajones per file id, as strings; a file in none is absent
     */
    Map<String, List<String>> categoriesByFileId(List<StoredFile> files) {
        if (files.isEmpty()) {
            return Map.of();
        }
        List<String> ids = files.stream().map(StoredFile::getId).toList();
        return categoryRepository.findByFileIds(ids).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getId().fileId(),
                        Collectors.mapping(link -> link.getId().category().name(), Collectors.toList())));
    }

    /**
     * What the platform published, optionally narrowed to one audience (#64).
     *
     * <p><b>The audience narrows the list; it does not guard the file.</b> That is the reading #64
     * asks for - it exists so "a document written for masters showed up in front of a player" stops
     * happening on screen (M24.1) - and treating it as authorization would mean a player handed a
     * link to the community's own rules gets a 403 from a document that is, by name, published.
     *
     * <p><b>The cajón is how this list is asked for now</b> (#233), and it replaced the audience of
     * #64 outright. A flow already says who a document is for - {@code PlayerApplication} is a
     * player's by construction, {@code TableMaterial} a master's - so keeping an audience beside it
     * would be two axes deciding one thing, with the second one free to drift out of agreement.
     *
     * <p>It is still a narrowing and not a guard, which is the reading #64 asked for and the part
     * worth keeping: somebody handed a link to a published document gets the document.
     *
     * @param category the cajón to narrow to, or null for everything published
     * @param pageable the page and its order
     * @return the published files, page by page
     */
    @Transactional(readOnly = true)
    public PageResponse<PublicFileResponse> listPublic(@Nullable FileCategory category, Pageable pageable) {
        Page<StoredFile> page = fileRepository.findAll(FileSearchSpecification.forPublic(category), pageable);
        Map<String, List<String>> cajones = categoriesByFileId(page.getContent());
        return PageResponse.from(page.map(
                file -> fileMapper.toPublicResponse(file, cajones.getOrDefault(file.getId(), List.of()))));
    }

    /**
     * One file's metadata, for whoever is allowed to reach it.
     *
     * @param fileId  the file
     * @param actorId the reader, from the token
     * @return the file
     * @throws NotFoundException if it does not exist, was marked gone, or the reader has no way to
     *                           reach it - the three answer the same, so an id nobody should have
     *                           learns nothing from asking (#9, #29)
     */
    @Transactional(readOnly = true)
    public FileResponse findById(String fileId, String actorId) {
        // No cajones and no uses resolved: this answers "what is this file", and the reader is often
        // somebody with no business knowing which other tables hold it (#232).
        return fileMapper.toResponse(requireReadable(fileId, actorId), List.of(), List.of());
    }

    /**
     * Hands the bytes over, and records that the file was used.
     *
     * <p>Not {@code readOnly}: every download stamps {@code lastUsedAt}, which is what keeps a file
     * somebody actually opens from being reclaimed by the purge (#75). A read that writes is unusual
     * enough to say out loud.
     *
     * @param fileId  the file
     * @param actorId the reader, from the token
     * @return the content plus the name and type the browser needs to save it
     * @throws NotFoundException if the file is not there or the reader cannot reach it
     */
    @Transactional
    public FileDownload download(String fileId, String actorId) {
        StoredFile file = requireReadable(fileId, actorId);
        file.setLastUsedAt(LocalDateTime.now());
        return new FileDownload(file.getName(), file.getMimeType(), storageService.read(file.getStorageKey()));
    }

    /**
     * Renaming a file and deciding whether to keep it.
     *
     * <p>Both are metadata: the content never moves, because the key it lives under has nothing to do
     * with what the file is called (#80). Keeping it is the "save this for later" of #68 - what
     * {@code handlePrivateStatus} did in the legacy (M21.2) - and it is what puts the file in the
     * reuse history of #65.
     *
     * @param fileId  the file
     * @param request the name it should end up with, and whether to keep it
     * @param actorId the actor, from the token
     * @return the file after the change
     * @throws NotFoundException        if the file is not there
     * @throws ForbiddenActionException if it is somebody else's, or the platform published it - a
     *                                  published file is the platform's and only an admin unpublishes
     */
    @Transactional
    public FileResponse update(String fileId, UpdateFileRequest request, String actorId) {
        StoredFile file = requireOwned(fileId, actorId);
        if (file.getFileType() == FileType.Public) {
            throw new ForbiddenActionException("File " + fileId + " is published; only an admin can change it (#64)");
        }
        file.setName(request.name());
        file.setFileType(request.keepInLibrary() ? FileType.Private : FileType.SingleUse);
        // No cajón here. Membership is what a file's uses made true and is never revoked (#233), so
        // there is nothing for an edit dialog to correct - the only way in is to be used, or to be
        // put there deliberately when there is no flow to observe.
        return fileMapper.toResponse(file, List.of(), List.of());
    }

    /**
     * The owner letting go of their own file.
     *
     * <p><b>Marks, never erases</b> (#25). Freeing the bytes is a deliberate maintenance operation the
     * platform owner runs from the administration menu, and that is F5 (#66). Tables that still hold
     * a link keep it: what disappears is the file from its owner's history, not the map from
     * somebody's table - which is the same asymmetry #79 describes from the other direction.
     *
     * @param fileId  the file
     * @param actorId the owner, from the token
     * @throws NotFoundException        if the file is not there
     * @throws ForbiddenActionException if it is somebody else's, or the platform published it
     */
    @Transactional
    public void delete(String fileId, String actorId) {
        StoredFile file = requireOwned(fileId, actorId);
        if (file.getFileType() == FileType.Public) {
            throw new ForbiddenActionException("File " + fileId + " is published; only an admin can remove it (#64)");
        }
        markDeleted(file);
    }

    /**
     * /admin/files: everything, searchable, with the usage count that makes #79 visible.
     *
     * <p>The count comes from one grouped query for the whole page rather than one per row, the same
     * shape /admin/catalogs uses.
     *
     * @param query      the search box, in the language of #164. Null or blank matches everything
     * @param statuses   the statuses to keep, or empty for all of them
     * @param fileTypes  the lifecycles to keep (#68), or empty for all of them
     * @param category   the cajón to keep (#233), or null for all of them
     * @param pageable   the page and its order
     * @return the files an admin sees, page by page
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminFileResponse> listForAdmin(
            @Nullable String query,
            List<FileStatus> statuses,
            List<FileType> fileTypes,
            @Nullable FileCategory category,
            Pageable pageable) {
        SearchQuery parsed = SearchQueryParser.parse(query, FileSearchField.wireNames());
        Page<StoredFile> page = fileRepository.findAll(
                FileSearchSpecification.forAdmin(parsed, statuses, fileTypes, category), pageable);
        Map<String, Long> uses = usesByFileId(page.getContent());
        Map<String, List<String>> cajones = categoriesByFileId(page.getContent());
        return PageResponse.from(page.map(file -> fileMapper.toAdminResponse(
                file, uses.getOrDefault(file.getId(), 0L), cajones.getOrDefault(file.getId(), List.of()))));
    }

    /**
     * An admin publishing a file for the whole platform, with its audience (#64).
     *
     * <p>This is what makes #79 possible: once the community's default character sheet is published,
     * every master attaches <em>that</em> file instead of uploading a copy, and correcting it corrects
     * it everywhere at once.
     *
     * <p>Publishing does not change whose file it is. The uploader stays the uploader - what changes
     * is what the file is for, which is why they can no longer rename or delete it.
     *
     * <p><b>It takes cajones, plural</b> (#233), and that is the case the relation exists for: the
     * community's blank sheet is asked for both while a table recruits and once it is running, so it
     * is published into {@code TableMaterial} and {@code MasterRequest} at once - one file, one blob,
     * two rows. A column would have forced a choice and broken the other flow.
     *
     * @param fileId  the file to publish
     * @param request the cajones it is offered in (#233)
     * @return the file after publishing
     * @throws NotFoundException       if the file is not there or was marked gone
     * @throws InvalidRequestException if a cajón is one nobody may publish into - the two player-side
     *                                 ones hold what individual people answered with, and a blank
     *                                 offered to everybody is not an answer
     */
    @Transactional
    public AdminFileResponse publish(String fileId, PublishFileRequest request) {
        StoredFile file = requireLive(fileId);
        for (FileCategory category : request.categories()) {
            if (!category.isPublishable()) {
                throw new InvalidRequestException(
                        "Cannot publish into " + category + ": it holds what individual people answered with",
                        "FILE_CATEGORY_NOT_PUBLISHABLE",
                        Map.of("category", category.name()));
            }
        }
        file.setFileType(FileType.Public);
        file.setLastUsedAt(LocalDateTime.now());
        for (FileCategory category : request.categories()) {
            classify(fileId, category);
        }
        return fileMapper.toAdminResponse(file, usesOf(fileId), categoriesOf(fileId));
    }

    /** The cajones one file belongs to, as strings. */
    private List<String> categoriesOf(String fileId) {
        return categoryRepository.findByFileIds(List.of(fileId)).stream()
                .map(link -> link.getId().category().name())
                .toList();
    }

    /**
     * Taking a file back out of the platform's published set.
     *
     * <p>It returns to its uploader as something they are keeping, not as a {@code Single-use}: it has
     * been public and attached to tables, and dropping it straight into the purge's path would be a
     * surprise rather than a decision. Tables that already attached it keep it - unpublishing is not
     * a delete (#79).
     *
     * @param fileId the file to unpublish
     * @return the file after unpublishing
     * @throws NotFoundException        if the file is not there or was marked gone
     * @throws ForbiddenActionException if it was not published in the first place
     */
    @Transactional
    public AdminFileResponse unpublish(String fileId) {
        StoredFile file = requireLive(fileId);
        if (file.getFileType() != FileType.Public) {
            throw new ForbiddenActionException("File " + fileId + " is not published");
        }
        file.setFileType(FileType.Private);
        // The cajones stay. Unpublishing says the platform no longer offers the file, not that it was
        // never a blank for those flows - and every table that already attached it keeps it (#79,
        // #233).
        return fileMapper.toAdminResponse(file, usesOf(fileId), categoriesOf(fileId));
    }

    /**
     * An admin removing any file, including one somebody else uploaded.
     *
     * <p>Still a mark and not an erase (#25, #66) - an admin has more reach than an owner, not a
     * different kind of delete.
     *
     * @param fileId the file
     * @throws NotFoundException if the file is not there or was already marked gone
     */
    @Transactional
    public void deleteAsAdmin(String fileId) {
        markDeleted(requireLive(fileId));
    }

    /**
     * Resolves a file somebody is about to attach to a table, checking they are allowed to.
     *
     * <p>Called by {@link TableFileService}, which is why it hands back the entity: it stays inside
     * the backend and never crosses HTTP (arquitectura.md 2.2). Two files qualify, and only two - the
     * actor's own, and one the platform published (#79). Somebody else's private upload never does,
     * which is the difference between reusing what the community offers and reading a stranger's
     * character sheet.
     *
     * @param fileId  the file about to be attached
     * @param actorId the actor, from the token
     * @return the file
     * @throws NotFoundException        if it is not there or was marked gone
     * @throws ForbiddenActionException if it belongs to somebody else and is not published
     */
    @Transactional
    public StoredFile requireAttachable(String fileId, String actorId) {
        StoredFile file = requireLive(fileId);
        boolean isOwn = file.getUserCreated().getId().equals(actorId);
        if (!isOwn && file.getFileType() != FileType.Public) {
            throw new ForbiddenActionException("File " + fileId + " belongs to somebody else and is not published");
        }
        file.setLastUsedAt(LocalDateTime.now());
        return file;
    }

    /**
     * Where each of the given files is being used, in <b>two</b> queries for the whole page (#232).
     *
     * <p>Three and not one: the sources are different joins over different bridge tables, and a
     * {@code union} in JPQL buys nothing but a query neither Hibernate nor a reader can follow. Three
     * is still a constant - what matters is that it is not one per row, which is what a naive
     * "resolve the uses of this file" helper called from a loop would have been.
     *
     * <p>A fourth source, {@code registration_files}, joins them in F2. Its table is in the baseline
     * DDL and nothing maps it yet, so a player's application files are simply not among the uses a
     * file reports today.
     *
     * @param files the files to resolve uses for
     * @return the uses per file id; a file nothing points at is absent, which reads as unused
     */
    Map<String, List<FileUsageResponse>> usagesByFileId(List<StoredFile> files) {
        if (files.isEmpty()) {
            return Map.of();
        }
        List<String> ids = files.stream().map(StoredFile::getId).toList();
        return Stream.of(
                        tableFileRepository.findUsagesByFileIds(ids),
                        taskFileRepository.findUsagesByFileIds(ids),
                        submissionFileRepository.findUsagesByFileIds(ids))
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        FileUsage::fileId, Collectors.mapping(fileMapper::toUsageResponse, Collectors.toList())));
    }

    /**
     * How many tables hold a live link to each of the given files, in one grouped query.
     *
     * @param files the files to count for
     * @return the count per file id; a file nothing points at is absent, which reads as zero
     */
    Map<String, Long> usesByFileId(List<StoredFile> files) {
        if (files.isEmpty()) {
            return Map.of();
        }
        List<String> ids = files.stream().map(StoredFile::getId).toList();
        return tableFileRepository.countUsesByFileIds(ids).stream()
                .collect(Collectors.toMap(FileUsageCount::fileId, FileUsageCount::uses));
    }

    /** How many tables hold a live link to one file. */
    private long usesOf(String fileId) {
        return tableFileRepository.countUsesByFileIds(List.of(fileId)).stream()
                .findFirst()
                .map(FileUsageCount::uses)
                .orElse(0L);
    }

    /** Marks a file gone and stamps when. The bytes stay until F5 (#25, #66). */
    private void markDeleted(StoredFile file) {
        file.setStatus(FileStatus.Deleted);
        file.setDeletedAt(LocalDateTime.now());
    }

    /** A live file by id, or 404. */
    private StoredFile requireLive(String fileId) {
        return fileRepository.findByIdAndStatus(fileId, FileStatus.Current)
                .orElseThrow(() -> new NotFoundException("File " + fileId + " not found"));
    }

    /** A live file the actor uploaded, or 404 if it is not there and 403 if it is not theirs. */
    private StoredFile requireOwned(String fileId, String actorId) {
        StoredFile file = requireLive(fileId);
        if (!file.getUserCreated().getId().equals(actorId)) {
            throw new ForbiddenActionException("File " + fileId + " belongs to somebody else");
        }
        return file;
    }

    /**
     * The read rule, in one place, and the reason this class has to know about tables at all.
     *
     * <p>Five ways a file is reachable, in the order they are cheapest to answer:
     *
     * <ol>
     *   <li><b>It is yours.</b> Whatever else is true about it.
     *   <li><b>The platform published it</b> (#64). The audience decides where it is <em>listed</em>,
     *       not who may open it - see {@link #listPublic}.
     *   <li><b>You run a table it is attached to</b> (#135). Private or shared: a master sees
     *       everything on their own table, and this is pertenencia and not the {@code Master} role.
     *   <li><b>A table shares it.</b> Not "a table you play at" - <b>any</b> table that shares it.
     *   <li><b>It was handed in to a task of a table you run</b> (#63, #76). A master asks for a
     *       character sheet and has to be able to open the one that arrives; without this they would
     *       see the row on the answer and get a 404 opening it, which is the exact mismatch #206 had
     *       to fix for the files a table shares. It is the master's, and nobody else's: the submitter
     *       already reaches it through the first way.
     * </ol>
     *
     * <p><b>The fourth one is deliberately as wide as the table itself, and no wider.</b> A shared
     * attachment already travels inside {@code GameTableDetailResponse}, which anybody who may see
     * the table receives - so requiring membership here would list a file to somebody and then refuse
     * to hand it over, which is the worst of both. It is also the case the files exist for: what a
     * master attaches to their open table is the character sheet and the house rules, and the person
     * who most needs to read those is somebody still deciding whether to apply (#60 uso 1). A private
     * attachment is the master's own notes and never gets here.
     *
     * <p>⚠️ <b>When the veto lands (F3) it has to be honoured here.</b> Today
     * {@code TableRegistrationStatus} has no {@code Blocked} value at all, so there is nobody to
     * exclude; once there is, somebody vetoed on a table must stop reaching what it shares, the same
     * way the table itself answers them 404 (#29, #39).
     *
     * <p>Anything else answers 404 rather than 403: confirming that a file exists to somebody with no
     * business knowing so is the kind of leak an opaque id is only defence in depth against (#9, #29).
     */
    private StoredFile requireReadable(String fileId, String actorId) {
        StoredFile file = requireLive(fileId);
        if (file.getUserCreated().getId().equals(actorId) || file.getFileType() == FileType.Public) {
            return file;
        }
        List<TableFile> links = tableFileRepository.findById_FileIdAndStatus(fileId, TableFileStatus.Current);
        for (TableFile link : links) {
            if (!link.isPrivate() || masterService.isMasterOf(link.getId().gameTableId(), actorId)) {
                return file;
            }
        }
        // The fifth way, last because it is the one that costs a join: what somebody handed in to a
        // task is readable by the people running the table that asked for it (#63, #76).
        for (String gameTableId : submissionFileRepository.findTableIdsBySubmittedFileId(fileId)) {
            if (masterService.isMasterOf(gameTableId, actorId)) {
                return file;
            }
        }
        // The sixth: the blank a master attached to a request has to be readable by everybody the
        // request reaches, or "fill in this form" arrives with a form nobody can open. Wider than the
        // fifth on purpose - that one is the master reading an answer, this one is the table reading
        // the question - so membership of the table is what it asks for, not running it.
        for (String gameTableId : taskFileRepository.findTableIdsByRequestedFileId(fileId)) {
            if (masterService.isMasterOf(gameTableId, actorId)
                    || registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                            gameTableId, actorId, REQUEST_READERS)) {
                return file;
            }
        }
        throw new NotFoundException("File " + fileId + " not found");
    }

    /**
     * Refuses a cajón this person has no business filing something of their own under (#237).
     *
     * <p>It is a rule of the domain and not of the screen, which is why it is here: the frontend
     * offers only what {@link #personalCategoriesOf} allows, but a request that names another cajón
     * is a request, and the authority over it is the service (#121).
     *
     * <p>Null passes, and that is the normal case: a file uploaded from inside a flow declares
     * nothing, because the link that follows is what classifies it (#233).
     *
     * @throws InvalidRequestException naming the cajón, so the frontend can say which one
     */
    private void requireOwnCajon(@Nullable FileCategory category, String actorId) {
        if (category != null && !personalCategoriesOf(actorId).contains(category)) {
            throw new InvalidRequestException(
                    "Cannot file a personal upload under " + category,
                    "FILE_CATEGORY_NOT_YOURS",
                    Map.of("category", category.name()));
        }
    }

    /**
     * The whitelist check (M21.4), before anything is written.
     *
     * @throws InvalidRequestException with the type that was refused, so the frontend can name it
     */
    private void requireAllowedMimeType(@Nullable String mimeType) {
        if (mimeType == null || !storageProperties.allowedMimeTypes().contains(mimeType)) {
            throw new InvalidRequestException(
                    "MIME type " + mimeType + " is not accepted",
                    "FILE_TYPE_NOT_ALLOWED",
                    Map.of("mimeType", String.valueOf(mimeType)));
        }
    }

    /**
     * The per-file cap - the one piece of #61 that #75 kept.
     *
     * <p>The limit travels as a number of bytes and not as "2 MB": the sentence is the frontend's to
     * write, in the reader's language and its own units (#197).
     *
     * @throws InvalidRequestException carrying the limit, because "too large" without a number tells
     *                                 nobody what to do next
     */
    private void requireWithinSizeLimit(long sizeBytes) {
        long limit = storageProperties.maxFileSize().toBytes();
        if (sizeBytes > limit) {
            throw new InvalidRequestException(
                    "File of " + sizeBytes + " bytes is over the " + limit + " byte limit",
                    "FILE_TOO_LARGE",
                    Map.of("maxBytes", String.valueOf(limit), "sizeBytes", String.valueOf(sizeBytes)));
        }
    }

    /**
     * The filename as the browser sent it, or a fallback when it sent none.
     *
     * <p>It is stored verbatim, traversal sequences and all, because it is <b>metadata</b> and nothing
     * else: the download header echoes it and the filesystem never sees it (#80). Sanitizing it here
     * would only make the value shown on screen disagree with the file the person uploaded, while the
     * property that matters - that no user-supplied text becomes a path - is held by the storage key
     * being a generated id.
     */
    private static String originalName(MultipartFile upload) {
        String name = upload.getOriginalFilename();
        return name == null || name.isBlank() ? "file" : name;
    }

    /** Reads the part into memory. Safe because the cap was already applied - 2 MB, not a stream. */
    private static byte[] readContent(MultipartFile upload) {
        try {
            return upload.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read the uploaded file", exception);
        }
    }

    /** SHA-256 of the content, lowercase hex - the 64 characters {@code files.content_hash} holds. */
    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required of every JVM", exception);
        }
    }
}
