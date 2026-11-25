package com.centraldungeon.features.files;

import com.centraldungeon.common.config.StorageProperties;
import com.centraldungeon.features.settings.SettingsService;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.storage.StorageService;
import com.centraldungeon.features.files.dto.AdminFileResponse;
import com.centraldungeon.features.files.dto.AdminFileTableResponse;
import com.centraldungeon.features.files.dto.FileResponse;
import com.centraldungeon.features.files.dto.FileUsageResponse;
import com.centraldungeon.features.files.dto.PublicFileResponse;
import com.centraldungeon.features.files.dto.LibraryUploadRequest;
import com.centraldungeon.features.files.dto.UpdateLibraryCategoryRequest;
import com.centraldungeon.features.files.dto.UpdateFileRequest;
import com.centraldungeon.features.files.dto.UploadFileRequest;
import com.centraldungeon.features.notifications.TableMembership;
import com.centraldungeon.features.registrations.RegistrationFileRepository;
import com.centraldungeon.features.registrations.TableRegistrationRepository;
import com.centraldungeon.features.registrations.TableRegistrationStatus;
import com.centraldungeon.features.tables.MasterService;
import com.centraldungeon.features.tables.TableVisibilityService;
import com.centraldungeon.features.tasks.SubmissionFileRepository;
import com.centraldungeon.features.tasks.TableTask;
import com.centraldungeon.features.tasks.TaskFileRepository;
import com.centraldungeon.features.users.PlatformRole;
import com.centraldungeon.features.users.User;
import com.centraldungeon.features.users.UserRepository;
import com.centraldungeon.features.users.UserRoleRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * link and a file have different owners, different rules and different lifetimes. This class
 * knows nothing about tables except when it has to answer whether somebody may read a file.
 *
 * <p><b>Reading is authorized by membership, never by role</b>. A file is
 * reachable through the tables it is attached to, and whether the reader belongs to any of them is a
 * membership question no {@code @PreAuthorize} can answer. {@code hasRole('MASTER')} would say "runs
 * some table", which is not the question.
 *
 * <p><b>Three levers hold the storage bill down, and none of them is a quota</b>, which would limit the
 * person rather than the volume: the same
 * upload is recognised and reused instead of stored twice, the bytes are compressed underneath
 * {@link StorageService}, and {@link FileRetentionService} lets go of what nobody has touched in
 * months.
 */
@Service
public class FileService {

    /**
     * The code of a library file that is published already: publishing it again, or changing what it
     * is, which masters are choosing it by.
     */
    private static final String ALREADY_PUBLISHED = "FILE_ALREADY_PUBLISHED";

    /** The {@code files} rows. */
    private final StoredFileRepository fileRepository;

    /** The attachments, which is what makes a file reachable to anybody but its owner. */
    private final TableFileRepository tableFileRepository;

    /** The categories a file belongs to. Add-only: nothing here ever takes a membership away. */
    private final FileCategoryLinkRepository categoryRepository;

    /**
     * The blanks a master attached to a request - the third source of uses, and the way a
     * request's blank becomes readable. Another feature's repository, dragged in by the same read rule that
     * dragged in {@code SubmissionFileRepository}: whether somebody may open the form their master
     * asked them to fill is a question about requests, and answering it anywhere else is how this read
     * rule drifted the first time.
     */
    private final TaskFileRepository taskFileRepository;

    /**
     * The other way a file becomes reachable: it was handed in as an answer to a table's task.
     *
     * <p>A repository from another feature, which no other collaborator here is. It is the read rule
     * that drags it in: whether a master may open what their player submitted is a question about
     * submissions, and the alternative - a second download endpoint living in {@code tasks/} with its
     * own copy of the rule - is exactly the drift that already cost us once.
     */
    private final SubmissionFileRepository submissionFileRepository;

    /** Resolves the uploader from the token. */
    private final UserRepository userRepository;

    /** Where the bytes actually live. */
    private final StorageService storageService;

    /** The whitelist and the retention window - deployment configuration, never constants. */
    private final StorageProperties storageProperties;

    /**
     * Where the per-file cap comes from: {@code files.max_file_size_mb} in
     * {@code system_settings}.
     *
     * <p>It used to be {@code app.storage.max-file-size}, beside the whitelist. The two look alike
     * and are not: which MIME types the parser accepts is a decision of whoever deploys the
     * application, and how big a file may be is a decision of whoever runs the community - the per-file cap is one of
     * the values that must be adjustable without a deploy. Read per upload
     * and not captured in a field, so raising the cap applies to the very next upload.
     */
    private final SettingsService settingsService;

    /** Answers membership: a row in {@code masters}, never the platform role. */
    private final MasterService masterService;

    /**
     * Answers the veto: whether the reader stopped being allowed to see the table an
     * attachment hangs off.
     *
     * <p>The <b>same</b> service {@code GET /game-tables/{id}} goes through, deliberately. An earlier version had
     * to fix the mirror image of this once already - a row listed on a screen and a 404 when it was
     * opened - and two copies of "is this person vetoed here" is how that happens again.
     */
    private final TableVisibilityService tableVisibilityService;

    /**
     * The actor's platform roles, for two questions about a <b>role</b> rather than about membership:
     * which categories are theirs to use, and whether they are an admin or the owner, who
     * read the platform's library as a team. The first decides what somebody's own library can hold;
     * the second is the only role that authorizes a read, and only for the library. Every other way
     * of reaching a file is membership.
     */
    private final UserRoleRepository userRoleRepository;

    /**
     * Who belongs to a table, for the request's-blank way a file is reachable: a request's blank has
     * to open for the people the request reaches. Pertenencia again, and again a row rather than a role.
     */
    private final TableRegistrationRepository registrationRepository;

    /**
     * The files a candidate attached to their own application - the fourth source of uses and
     * the way an application's attachment becomes readable.
     *
     * <p>Another feature's repository, dragged in by the same read rule that dragged in
     * {@code SubmissionFileRepository} and {@code TaskFileRepository}: whether a master may open the
     * sheet a candidate applied with is a question about applications, and answering it anywhere
     * else is how this read rule drifted the first time.
     */
    private final RegistrationFileRepository registrationFileRepository;

    /** Entity to DTO. */
    private final FileMapper fileMapper;

    /**
     * @param fileRepository            the {@code files} rows
     * @param tableFileRepository       the attachments, for resolving who may read a file
     * @param categoryRepository        the categories each file belongs to
     * @param taskFileRepository        the blanks attached to requests, for the same two questions
     * @param submissionFileRepository  the answers a file was handed in with, for the same question
     * @param userRepository            resolves the uploader from the token
     * @param storageService            where the bytes live
     * @param storageProperties         the whitelist and the retention window
     * @param settingsService           the per-file cap, editable without a deploy
     * @param masterService             answers membership
     * @param tableVisibilityService    answers the veto, so a shared attachment stops being readable
     *                                  by somebody the table stopped existing for
     * @param userRoleRepository        the actor's roles, for their own categories
     * @param registrationRepository    who belongs to a table, for reading a request's blank
     * @param registrationFileRepository the files a candidate attached to their application, for the
     *                                   same two questions
     * @param fileMapper                entity to DTO
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
            SettingsService settingsService,
            MasterService masterService,
            TableVisibilityService tableVisibilityService,
            UserRoleRepository userRoleRepository,
            TableRegistrationRepository registrationRepository,
            RegistrationFileRepository registrationFileRepository,
            FileMapper fileMapper) {
        this.fileRepository = fileRepository;
        this.tableFileRepository = tableFileRepository;
        this.categoryRepository = categoryRepository;
        this.taskFileRepository = taskFileRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.settingsService = settingsService;
        this.masterService = masterService;
        this.tableVisibilityService = tableVisibilityService;
        this.userRoleRepository = userRoleRepository;
        this.registrationRepository = registrationRepository;
        this.registrationFileRepository = registrationFileRepository;
        this.fileMapper = fileMapper;
    }

    /**
     * Takes an upload in.
     *
     * <p>The order of the checks is deliberate: <b>the type and the size are settled before a single
     * byte is written</b>, which is the cheapest place to refuse and the one the legacy skipped
     * entirely - {@code multer} ran with no {@code limits} at all.
     *
     * <p>Then comes deduplication: if this person already uploaded this exact content, they get
     * the row they already have. Attaching the same character sheet to a second table costs nothing,
     * which is the point of the whole reuse story.
     *
     * <p>Whether that recognition happened travels back in {@link UploadResult}, because until now
     * it did not travel at all: the response was a 201 with somebody's older row, indistinguishable
     * from a fresh write, so the one cost lever a person can actually feel was invisible to them.
     *
     * @param upload      the multipart part: the bytes and the name the browser sent
     * @param request     which lifecycle the uploader wants for it and what it is
     * @param actorId     the uploader, from the token - never from the URL
     * @return the file, plus whether it was recognised rather than written
     * @throws InvalidRequestException if the part is empty, its type is not on the whitelist, it is
     *                                 over the cap, or the uploader asked for {@code Public} - which
     *                                 is an admin's to grant, not an uploader's to declare
     * @throws NotFoundException       if the token names somebody who is not there
     */
    @Transactional
    public UploadResult upload(MultipartFile upload, UploadFileRequest request, String actorId) {
        // Neither side of the platform's library is something an uploader declares: both are what an
        // admin's own upload produces, through uploadToLibrary.
        if (request.fileType().isInLibrary()) {
            throw new InvalidRequestException(
                    "A file cannot be uploaded into the platform's library from here - that is an admin action",
                    "FILE_CANNOT_SELF_PUBLISH");
        }
        if (upload.isEmpty()) {
            throw new InvalidRequestException("The uploaded file is empty", "FILE_EMPTY");
        }
        requireAllowedMimeType(upload.getContentType());
        requireWithinSizeLimit(upload.getSize());
        requireOwnCategory(request.fileCategory(), actorId);

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
            // A category asked for here is *added*, never swapped: memberships accumulate and are never
            // revoked. Re-uploading the sheet you already had, this time from your library and
            // marked as application material, puts it in that category too without taking it out of the
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
        // Storage stages the bytes and only moves them into place when this transaction commits,
        // so a failure below cannot leave the orphan the legacy left on every failed upload.
        storageService.store(storageKey, content);
        StoredFile saved = fileRepository.save(file);
        // Null in every flow: a file uploaded to attach to a table or to answer a request is
        // classified by the link that follows, not by the upload. It only arrives set from the one
        // screen with no flow to observe - somebody's own library.
        classify(saved.getId(), request.fileCategory());
        return new UploadResult(fileMapper.toResponse(saved, List.of(), List.of()), false);
    }

    /**
     * The categories this person may file something of their own under.
     *
     * <p><b>The personal library and the platform's are two different libraries</b>, and this answers
     * for the first one only. {@code /my/files} is where somebody keeps what is theirs, to have it
     * to hand; {@code /admin/files} is the platform's, where an admin uploads what the community
     * offers and says which categories it belongs to by publishing it. An admin does none of their
     * admin work in their own library, which is why the {@code Admin} role adds nothing here.
     *
     * <p><b>Each side follows the role that fills it</b>, and neither is assumed:
     *
     * <ul>
     *   <li><b>The two player-side categories need the {@code Player} role.</b> Applying to a table and
     *       answering a request are a player's, so somebody who is only a master has no business
     *       filing anything under them - and would never have got one by using the application
     *       either, because those flows are closed to them.
     *   <li><b>The two master-side ones need the {@code Master} role <em>or</em> a live row in
     *       {@code masters}.</b> Not the role alone: somebody an admin assigned to a table has no
     *       {@code Master} role at all and runs that table all the same.
     *   <li><b>Never {@link FileCategory#Announcement}.</b> It is the community's by definition and
     *       lives only in the platform's library.
     * </ul>
     *
     * <p><b>In production nearly everybody gets all four</b>, because every account is created with
     * {@code Player} and a master accumulates rather than replaces. That is not a reason to
     * hardcode it: the rule is what the roles say, and an account that does not hold {@code Player} -
     * one the test profile makes, or one whose role was taken away - has to be told the truth.
     *
     * <p>The list can come back <b>empty</b>, for an account that is neither. That is a real answer
     * and not a failure: their library holds what they were given, and there is no flow of their own
     * to file anything under.
     *
     * @param actorId whose library, from the token
     * @return the categories they may use, in the order the enum declares them. Possibly empty
     */
    @Transactional(readOnly = true)
    public List<FileCategory> personalCategoriesOf(String actorId) {
        // findActiveRoleNames and not findAllGrants: the latter returns every grant the person ever
        // had, revoked ones included, because it exists for the write that changes them. Reading it
        // here counted a Player role that had been taken away, and handed a master the two categories
        // their flows can no longer fill.
        Set<String> roles = userRoleRepository.findActiveRoleNames(actorId);
        boolean plays = roles.contains(PlatformRole.PLAYER.roleName());
        boolean runsTables =
                roles.contains(PlatformRole.MASTER.roleName()) || masterService.runsAnyTable(actorId);
        return Arrays.stream(FileCategory.values())
                .filter(FileCategory::isPersonal)
                .filter(category -> isPlayerSide(category) ? plays : runsTables)
                .toList();
    }

    /** Whether a category is one of the two a player fills by themselves, rather than a master. */
    private static boolean isPlayerSide(FileCategory category) {
        return category == FileCategory.PlayerApplication || category == FileCategory.PlayerSubmission;
    }

    /**
     * Puts a file in a category, if it is not in it already.
     *
     * <p><b>Idempotent and add-only</b>, which is what lets every caller invoke it without first
     * asking whether it needs to: the pair is the primary key, so a master attaching their sheet to a
     * fifth table writes nothing. And nothing anywhere revokes a membership - detaching a file from a
     * table says the file is no longer that table's, never that it was never that table's material.
     *
     * <p>Called by the services that make the links rather than by the links themselves, because a
     * category is a consequence of the domain action and not of the row: {@code TableFileService} knows
     * it is attaching table material, {@code table_files} only knows it has two ids.
     *
     * @param fileId   the file
     * @param category the category, or null to do nothing - which is the normal case on an upload made
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
     * Somebody's own uploads, newest first - the history the {@code FilePicker} offers and what
     * /my/files lists.
     *
     * <p><b>This is the one read that resolves uses</b>, and it is where the classification
     * the owner wants actually lands: a row that says "the sheet on Children of the Void" is recognisable
     * in a way a filename is not, and a row that says nothing is the owner's warning that the retention
     * purge has its eye on that file.
     *
     * <p><b>The search box actually searches now.</b> The frontend has been sending {@code q} on
     * this call since the picker was built and this method had no parameter to receive it, so the
     * "search my files" input filtered nothing at all - which on a history long enough to
     * need searching is the same as not having reuse.
     *
     * @param actorId    whose files, from the token. The owner is in the {@code WHERE}
     * @param query      the search box over their filenames, or null for everything they have
     * @param category   the category to narrow to, or null for everything they have
     * @param pageable   the page and its order, defaulted by the controller
     * @return their live files with their categories and their uses, page by page
     */
    @Transactional(readOnly = true)
    public PageResponse<FileResponse> listMine(
            String actorId, @Nullable String query, @Nullable FileCategory category, Pageable pageable) {
        SearchQuery parsed = SearchQueryParser.parse(query, FileSearchField.wireNames());
        Page<StoredFile> page =
                fileRepository.findAll(FileSearchSpecification.forOwner(actorId, parsed, category), pageable);
        Map<String, List<FileUsageResponse>> usages = usagesByFileId(page.getContent(), actorId);
        Map<String, List<String>> categories = categoriesByFileId(page.getContent());
        return PageResponse.from(page.map(file -> fileMapper.toResponse(
                file, categories.getOrDefault(file.getId(), List.of()), usages.getOrDefault(file.getId(), List.of()))));
    }

    /**
     * Which categories each of the given files belongs to, in one query for the whole page.
     *
     * @param files the files to resolve categories for
     * @return the categories per file id, as strings; a file in none is absent
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
     * What the platform published, optionally narrowed to one audience.
     *
     * <p><b>The audience narrows the list; it does not guard the file.</b> That is the intent
     * of the filter - it exists so "a document written for masters showed up in front of a player" stops
     * happening on screen - and treating it as authorization would mean a player handed a
     * link to the community's own rules gets a 403 from a document that is, by name, published.
     *
     * <p><b>The category is how this list is asked for now</b>, and it replaced the old
     * audience filter outright. A flow already says who a document is for - {@code PlayerApplication} is a
     * player's by construction, {@code TableMaterial} a master's - so keeping an audience beside it
     * would be two axes deciding one thing, with the second one free to drift out of agreement.
     *
     * <p>It is still a narrowing and not a guard, which is the part of the old audience filter
     * worth keeping: somebody handed a link to a published document gets the document.
     *
     * @param category the category to narrow to, or null for everything published
     * @param pageable the page and its order
     * @return the published files, page by page
     */
    @Transactional(readOnly = true)
    public PageResponse<PublicFileResponse> listPublic(@Nullable FileCategory category, Pageable pageable) {
        Page<StoredFile> page = fileRepository.findAll(FileSearchSpecification.forPublic(category), pageable);
        Map<String, List<String>> categories = categoriesByFileId(page.getContent());
        return PageResponse.from(page.map(
                file -> fileMapper.toPublicResponse(file, categories.getOrDefault(file.getId(), List.of()))));
    }

    /**
     * Hands the bytes over, and records that the file was used.
     *
     * <p>Not {@code readOnly}: every download stamps {@code lastUsedAt}, which is what keeps a file
     * somebody actually opens from being reclaimed by the purge. A read that writes is unusual
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
     * with what the file is called. Keeping it is the "save this for later" action - what
     * {@code handlePrivateStatus} did in the legacy - and it is what puts the file in the
     * reuse history.
     *
     * @param fileId  the file
     * @param request the name it should end up with, and whether to keep it
     * @param actorId the actor, from the token
     * @return the file after the change
     * @throws NotFoundException        if the file is not there
     * @throws ForbiddenActionException if it is somebody else's, or it is in the platform's library -
     *                                  published or not, it is the platform's and only an admin
     *                                  changes it
     */
    @Transactional
    public FileResponse update(String fileId, UpdateFileRequest request, String actorId) {
        StoredFile file = requireOwned(fileId, actorId);
        if (file.getFileType().isInLibrary()) {
            throw new ForbiddenActionException("File " + fileId + " is in the platform's library; only an admin can change it");
        }
        file.setName(request.name());
        file.setFileType(request.keepInLibrary() ? FileType.Private : FileType.SingleUse);
        // No category here. Membership is what a file's uses made true and is never revoked, so
        // there is nothing for an edit dialog to correct - the only way in is to be used, or to be
        // put there deliberately when there is no flow to observe.
        return fileMapper.toResponse(file, List.of(), List.of());
    }

    /**
     * The owner letting go of their own file.
     *
     * <p><b>Marks, never erases</b>. Freeing the bytes is a deliberate maintenance operation the
     * platform owner runs from the administration menu, and that is still to be built. Tables that still hold
     * a link keep it: what disappears is the file from its owner's history, not the map from
     * somebody's table - which is the same asymmetry seen from the other direction.
     *
     * @param fileId  the file
     * @param actorId the owner, from the token
     * @throws NotFoundException        if the file is not there
     * @throws ForbiddenActionException if it is somebody else's, or it is in the platform's library
     */
    @Transactional
    public void delete(String fileId, String actorId) {
        StoredFile file = requireOwned(fileId, actorId);
        if (file.getFileType().isInLibrary()) {
            throw new ForbiddenActionException("File " + fileId + " is in the platform's library; only an admin can remove it");
        }
        markDeleted(file);
    }

    /**
     * /admin/files: the platform's library - published, unpublished and hidden - searchable, with the
     * tables that show one file is shared, not copied.
     *
     * <p><b>Only the library</b>, and that is a predicate in the query rather than a filter the screen
     * may drop. Somebody's private upload - the sheet a player applied with, what they handed in - is
     * theirs and not the platform's, and seeing or removing it is not this screen's job: that belongs
     * to acting as that person, not to administering the library.
     *
     * <p>The tables of the whole page come from one query rather than one per row, the shape
     * /admin/catalogs uses for its counts.
     *
     * @param query      the search box, in the admin search language. Null or blank matches everything
     * @param statuses   the statuses to keep, or empty for all of them
     * @param category   the category to keep, or null for all of them
     * @param pageable   the page and its order
     * @return the library's files, page by page
     * @throws InvalidRequestException if the category is one nothing can be published into - asking the
     *                                 platform's library for a player's answers is asking for a shelf
     *                                 it does not have
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminFileResponse> listForAdmin(
            @Nullable String query,
            List<FileStatus> statuses,
            @Nullable FileCategory category,
            Pageable pageable) {
        if (category != null) {
            requirePublishable(List.of(category));
        }
        SearchQuery parsed = SearchQueryParser.parse(query, FileSearchField.wireNames());
        Page<StoredFile> page = fileRepository.findAll(
                FileSearchSpecification.forAdmin(parsed, statuses, category), pageable);
        Map<String, List<AdminFileTableResponse>> tables = tablesByFileId(page.getContent());
        Map<String, List<String>> categories = categoriesByFileId(page.getContent());
        return PageResponse.from(page.map(file -> fileMapper.toAdminResponse(
                file,
                tables.getOrDefault(file.getId(), List.of()),
                publishableOnly(categories.getOrDefault(file.getId(), List.of())))));
    }

    /**
     * An admin uploading a file into the platform's library, published now or left for later.
     *
     * <p><b>What it is, always; whether it goes out, a choice.</b> The category is required, so nothing
     * sits in the library without saying which flow it is for - the state of a file with no declared flow stays gone.
     * Publishing is not required any more: a published file is attached by tables straight away and
     * hiding it later does not take it off them, so going out on the same click as the upload
     * has to be something the admin asked for.
     *
     * <p>Deduplication applies as on any upload: content this admin already uploaded comes back
     * as the row they had, moved into the library with the categories <em>added</em>. <b>A re-upload
     * never hides</b>: a file already published stays published even if this upload asked not to
     * publish, because taking it out of the pickers is hiding, which is its own action.
     *
     * @param upload  the multipart part: the bytes and the name the browser sent
     * @param request the categories it is offered in, at least one, and whether to publish it now
     * @param actorId the admin uploading, from the token. They stay its uploader
     * @return the file, plus whether it was recognised rather than written
     * @throws InvalidRequestException if a category is one nobody may publish into, or the part is
     *                                 empty, off the whitelist or over the cap
     * @throws NotFoundException       if the token names somebody who is not there
     */
    @Transactional
    public LibraryUpload uploadToLibrary(MultipartFile upload, LibraryUploadRequest request, String actorId) {
        // Before the bytes: a refused category should cost nothing, not a stored file rolled back.
        requirePublishable(request.categories());
        UploadResult stored = upload(upload, new UploadFileRequest(FileType.Private, null), actorId);
        String fileId = stored.file().id();
        StoredFile file = requireLive(fileId);
        if (request.publish()) {
            markPublished(file);
        } else if (file.getFileType() != FileType.Public) {
            file.setFileType(FileType.Library);
        }
        for (FileCategory category : request.categories()) {
            classify(fileId, category);
        }
        return new LibraryUpload(toLibraryResponse(file), stored.deduplicated());
    }

    /**
     * Publishing a file of the library that is not published: never published yet, or hidden.
     *
     * <p>From here on masters see it in the picker and can attach it. It needs a category first -
     * which the upload always asks for, so the refusal is for a file whose category was somehow lost.
     *
     * @param fileId the file to publish
     * @return the file after publishing
     * @throws NotFoundException       if the file is not there, was marked gone, or is not in the library
     * @throws ConflictException       if it is already published - a state, not a refusal of the admin,
     *                                 with the code {@code FILE_ALREADY_PUBLISHED}
     * @throws InvalidRequestException if it has no category to be offered in
     */
    @Transactional
    public AdminFileResponse publish(String fileId) {
        StoredFile file = requireInLibrary(fileId);
        if (file.getFileType() == FileType.Public) {
            throw new ConflictException("File " + fileId + " is already published", ALREADY_PUBLISHED);
        }
        if (publishableOnly(categoriesOf(fileId)).isEmpty()) {
            throw new InvalidRequestException(
                    "File " + fileId + " says no flow it is for", "FILE_NEEDS_CATEGORY", Map.of("fileId", fileId));
        }
        markPublished(file);
        return toLibraryResponse(file);
    }

    /**
     * Hiding a published file: it stays in the library, offered to nobody.
     *
     * <p><b>It no longer leaves the library.</b> It used to go back to its uploader as a {@code
     * Private}, and when the uploader was an admin with no library of their own the file vanished for
     * everybody. Now it is {@code Library} again, with its category and its {@code publishedAt}, and
     * publishing it again is one click. Tables that already attached it keep it - hiding is not a
     * delete.
     *
     * @param fileId the file to hide
     * @return the file after hiding
     * @throws NotFoundException if the file is not there, was marked gone, or is not in the library
     * @throws ConflictException if it is not published, with the code {@code FILE_NOT_PUBLISHED}
     */
    @Transactional
    public AdminFileResponse unpublish(String fileId) {
        StoredFile file = requireInLibrary(fileId);
        if (file.getFileType() != FileType.Public) {
            throw new ConflictException("File " + fileId + " is not published", "FILE_NOT_PUBLISHED");
        }
        file.setFileType(FileType.Library);
        // The categories stay: hiding says the platform no longer offers the file, not what it is.
        return toLibraryResponse(file);
    }

    /**
     * Changing what a file of the library is, while it is not published.
     *
     * <p><b>The one place a membership is removed</b>, and only a declared one: the library's categories
     * are what an admin said the file is for, and correcting that before anybody is offered it is
     * correcting a declaration. A player-side membership the file carries from its history is not the
     * library's and stays. A published file is refused - masters are choosing it under the
     * category it has now, so it is hidden first.
     *
     * @param fileId  the file
     * @param request what it is now
     * @return the file after the change
     * @throws NotFoundException       if the file is not there, was marked gone, or is not in the library
     * @throws ConflictException       if it is published, with the code {@code FILE_ALREADY_PUBLISHED}
     * @throws InvalidRequestException if the category is one nothing can be published into
     */
    @Transactional
    public AdminFileResponse changeLibraryCategory(String fileId, UpdateLibraryCategoryRequest request) {
        requirePublishable(List.of(request.category()));
        StoredFile file = requireInLibrary(fileId);
        if (file.getFileType() == FileType.Public) {
            throw new ConflictException(
                    "File " + fileId + " is published; hide it before changing what it is", ALREADY_PUBLISHED);
        }
        categoryRepository.deleteByFileIdAndCategories(fileId, PUBLISHABLE_CATEGORIES);
        classify(fileId, request.category());
        return toLibraryResponse(file);
    }

    /**
     * An admin removing a file from the platform's library, published or not.
     *
     * <p><b>Only a file of the library</b>. Somebody's private file is theirs: an admin reaches
     * it by acting as that person, never from the library, so from here it is simply not there. Still
     * a mark and not an erase.
     *
     * @param fileId the file
     * @throws NotFoundException if the file is not there, was already marked gone, or is not in the library
     */
    @Transactional
    public void deleteAsAdmin(String fileId) {
        markDeleted(requireInLibrary(fileId));
    }

    /** The categories a file of the library can be offered in, as a set for the one delete that uses it. */
    private static final List<FileCategory> PUBLISHABLE_CATEGORIES =
            Arrays.stream(FileCategory.values()).filter(FileCategory::isPublishable).toList();

    /** A live file of the platform's library, published or not - anything else is not there. */
    private StoredFile requireInLibrary(String fileId) {
        StoredFile file = requireLive(fileId);
        if (!file.getFileType().isInLibrary()) {
            throw new NotFoundException("File " + fileId + " is not in the platform's library");
        }
        return file;
    }

    /** Publishes a file, stamping the first publication once and never again. */
    private static void markPublished(StoredFile file) {
        LocalDateTime now = LocalDateTime.now();
        file.setFileType(FileType.Public);
        file.setLastUsedAt(now);
        if (file.getPublishedAt() == null) {
            file.setPublishedAt(now);
        }
    }

    /** One file of the library as /admin/files shows it. */
    private AdminFileResponse toLibraryResponse(StoredFile file) {
        return fileMapper.toAdminResponse(
                file, tablesOf(file.getId()), publishableOnly(categoriesOf(file.getId())));
    }

    /**
     * Refuses the categories nothing can be published into.
     *
     * <p>The two player-side ones hold what individual people answered with; a blank offered to the
     * whole community is not an answer, and it belongs in the master-side category the request came from.
     */
    private static void requirePublishable(List<FileCategory> categories) {
        for (FileCategory category : categories) {
            if (!category.isPublishable()) {
                throw new InvalidRequestException(
                        "Cannot publish into " + category + ": it holds what individual people answered with",
                        "FILE_CATEGORY_NOT_PUBLISHABLE",
                        Map.of("category", category.name()));
            }
        }
    }

    /**
     * The categories the platform's library speaks of.
     *
     * <p>A file of the library can still carry a player-side membership from before it got there -
     * memberships are never revoked - but that is its history as somebody's answer, not a
     * shelf the library offers it on, so the admin's screen does not show it.
     */
    private static List<String> publishableOnly(List<String> categories) {
        return categories.stream()
                .filter(category -> FileCategory.valueOf(category).isPublishable())
                .toList();
    }

    /** The categories one file belongs to, as strings. */
    private List<String> categoriesOf(String fileId) {
        return categoryRepository.findByFileIds(List.of(fileId)).stream()
                .map(link -> link.getId().category().name())
                .toList();
    }

    /**
     * Resolves a file somebody is about to attach to a table, checking they are allowed to.
     *
     * <p>Called by {@link TableFileService}, which is why it hands back the entity: it stays inside
     * the backend and never crosses HTTP. Two files qualify, and only two - the actor's own, and one
     * the platform published. Somebody else's private upload never does, which is the difference
     * between reusing what the community offers and reading a stranger's character sheet.
     *
     * <p>A refused file the actor may not even open answers as a file that does not exist, so the
     * refusal never confirms it is there; only one they can open and is not theirs to attach is a 403.
     *
     * @param fileId  the file about to be attached
     * @param actorId the actor, from the token
     * @return the file
     * @throws NotFoundException        if it is not there, was marked gone, or the actor may not open it
     * @throws ForbiddenActionException if the actor may open it but it belongs to somebody else and is
     *                                  not published
     */
    @Transactional
    public StoredFile requireAttachable(String fileId, String actorId) {
        StoredFile file = requireReadable(fileId, actorId);
        boolean isOwn = file.getUserCreated().getId().equals(actorId);
        if (!isOwn && file.getFileType() != FileType.Public) {
            throw new ForbiddenActionException("File " + fileId + " belongs to somebody else and is not published");
        }
        file.setLastUsedAt(LocalDateTime.now());
        return file;
    }

    /**
     * Where each of the given files is being used, in four queries for the whole page.
     *
     * <p>Four and not one: the sources are different joins over different bridge tables, and a
     * {@code union} in JPQL buys nothing but a query neither Hibernate nor a reader can follow. Four
     * is still a constant - what matters is that it is not one per row, which is what a naive
     * "resolve the uses of this file" helper called from a loop would have been.
     *
     * <p>The fourth source, {@code registration_files}, joins them too: a candidate's application
     * counts as a use only while the application is still alive ({@code Candidate} or
     * {@code Player}) - a withdrawn or rejected one does not, which is
     * {@code RegistrationFileRepository.findUsagesByFileIds}'s own filter, and the whole point of that query.
     *
     * <p>Each use also says how the reader is joined to its table, in two more queries for
     * the whole page and not one per use: the tables they run (a live row in {@code masters}) and, among
     * the tables the page names, the ones they play at. Master wins when both hold; neither leaves it
     * null.
     *
     * @param files   the files to resolve uses for
     * @param actorId the reader, from the token
     * @return the uses per file id; a file nothing points at is absent, which reads as unused
     */
    Map<String, List<FileUsageResponse>> usagesByFileId(List<StoredFile> files, String actorId) {
        if (files.isEmpty()) {
            return Map.of();
        }
        List<String> ids = files.stream().map(StoredFile::getId).toList();
        List<FileUsage> usages = Stream.of(
                        tableFileRepository.findUsagesByFileIds(ids),
                        taskFileRepository.findUsagesByFileIds(ids),
                        submissionFileRepository.findUsagesByFileIds(ids),
                        registrationFileRepository.findUsagesByFileIds(ids))
                .flatMap(List::stream)
                .toList();
        Map<String, TableMembership> membership = membershipOf(actorId, usages);
        return usages.stream()
                .collect(Collectors.groupingBy(
                        FileUsage::fileId,
                        Collectors.mapping(
                                usage -> fileMapper.toUsageResponse(
                                        usage,
                                        Optional.ofNullable(membership.get(usage.contextId()))
                                                .map(TableMembership::name)
                                                .orElse(null)),
                                Collectors.toList())));
    }

    /** How the actor is joined to each table the given uses name; a table they are not joined to is absent. */
    private Map<String, TableMembership> membershipOf(String actorId, List<FileUsage> usages) {
        Set<String> tableIds = usages.stream().map(FileUsage::contextId).collect(Collectors.toSet());
        if (tableIds.isEmpty()) {
            return Map.of();
        }
        Map<String, TableMembership> result = new HashMap<>();
        registrationRepository
                .findTableIdsWithRegistration(actorId, tableIds, List.of(TableRegistrationStatus.Player))
                .forEach(tableId -> result.put(tableId, TableMembership.Player));
        masterService.findLiveByUser(actorId).forEach(master -> {
            if (tableIds.contains(master.getGameTable().getId())) {
                result.put(master.getGameTable().getId(), TableMembership.Master);
            }
        });
        return result;
    }

    /**
     * The tables that hold a live link to each of the given files, named, in one query.
     *
     * @param files the files to resolve tables for
     * @return the tables per file id; a file nothing points at is absent, which reads as none
     */
    Map<String, List<AdminFileTableResponse>> tablesByFileId(List<StoredFile> files) {
        if (files.isEmpty()) {
            return Map.of();
        }
        List<String> ids = files.stream().map(StoredFile::getId).toList();
        return tableFileRepository.findUsagesByFileIds(ids).stream()
                .collect(Collectors.groupingBy(
                        FileUsage::fileId,
                        Collectors.mapping(fileMapper::toAdminTableResponse, Collectors.toList())));
    }

    /** The tables that hold a live link to one file. */
    private List<AdminFileTableResponse> tablesOf(String fileId) {
        return tableFileRepository.findUsagesByFileIds(List.of(fileId)).stream()
                .map(fileMapper::toAdminTableResponse)
                .toList();
    }

    /** Marks a file gone and stamps when. The bytes stay until the physical purge exists. */
    private void markDeleted(StoredFile file) {
        file.setStatus(FileStatus.Deleted);
        file.setDeletedAt(LocalDateTime.now());
    }

    /** A live file by id, or 404. */
    private StoredFile requireLive(String fileId) {
        return fileRepository.findByIdAndStatus(fileId, FileStatus.Current)
                .orElseThrow(() -> new NotFoundException("File " + fileId + " not found"));
    }

    /**
     * A live file the actor uploaded. 404 if it is not there or they may not open it, the same answer
     * for both; 403 only for a file they may open and is not theirs.
     */
    private StoredFile requireOwned(String fileId, String actorId) {
        StoredFile file = requireReadable(fileId, actorId);
        if (!file.getUserCreated().getId().equals(actorId)) {
            throw new ForbiddenActionException("File " + fileId + " belongs to somebody else");
        }
        return file;
    }

    /**
     * The read rule, in one place, and the reason this class has to know about tables at all.
     *
     * <p>These are the ways a file is reachable, in the order they are cheapest to answer:
     *
     * <ol>
     *   <li><b>It is yours.</b> Whatever else is true about it.
     *   <li><b>The platform published it.</b> The category decides where it is <em>listed</em>, not who
     *       may open it - see {@link #listPublic}.
     *   <li><b>It is in the platform's library and you are an admin or the owner.</b> Published or
     *       not, hidden or not, and whoever uploaded it: the library is the admins' as a team, every
     *       one of them sees it listed on {@code /admin/files}, and a row they could not open would be
     *       listed and then refused.
     *   <li><b>You run a table it is attached to.</b> Private or shared: a master sees everything on
     *       their own table, and that is the row in {@code masters}, not the {@code Master} role.
     *   <li><b>A table shares it and you may see that table.</b> Not "a table you play at" -
     *       <b>any</b> table that shares it and that you may see.
     *   <li><b>It was handed in to a request of a table you run.</b> A master asks for a character
     *       sheet and has to be able to open the one that arrives, or they would see the row on the
     *       answer and get a 404 opening it. It is the master's, and nobody else's: the submitter
     *       already reaches it through the first way.
     *   <li><b>It is the blank a master attached to a request that reaches you</b>, and the request's
     *       audience decides who that is - see {@link #reaches}. A closed request still counts: closing
     *       ends what it takes in, not what it asked, and its blank stays part of the table's history.
     *   <li><b>It was attached to an application to a table you run.</b> The sister of the answer
     *       to a request: a
     *       master has to be able to open the character sheet a candidate applied with. Only a
     *       <b>live</b> application counts - {@code RegistrationFileRepository.findTableIdsByApplicationFileId}
     *       excludes a withdrawn or rejected one, or withdrawing would leave the file readable by a
     *       master who has nothing left to do with it.
     * </ol>
     *
     * <p><b>The admin reaches whatever the master of the table reaches</b>: running a table, an answer
     * to a request, a request's blank and an application all ask {@code MasterService.canOversee},
     * which is "runs it, or holds the admin rank". Outside the library, only through a table - somebody's private file that no table
     * links is still theirs alone, which is why the admin library does not list it either.
     *
     * <p><b>A shared attachment is deliberately as wide as the table itself, and no wider.</b> A shared
     * attachment already travels inside {@code GameTableDetailResponse}, which anybody who may see
     * the table receives - so requiring membership here would list a file to somebody and then refuse
     * to hand it over. It is also the case the files exist for: what a master attaches to their open
     * table is the character sheet and the house rules, and the person who most needs to read those
     * is somebody still deciding whether to apply. A private attachment is the master's own notes and
     * never gets here. Asking {@code TableVisibilityService.canSee} rather than a copy of its rule
     * keeps out the vetoed, everybody outside a table nobody was shown yet, and everybody once the
     * table is deleted - the same answer {@code GET /game-tables/{id}} gives.
     *
     * <p>Anything else answers 404 rather than 403: confirming that a file exists to somebody with no
     * business knowing so is the kind of leak an opaque id is only defence in depth against.
     */
    private StoredFile requireReadable(String fileId, String actorId) {
        StoredFile file = requireLive(fileId);
        if (file.getUserCreated().getId().equals(actorId) || file.getFileType() == FileType.Public) {
            return file;
        }
        if (file.getFileType().isInLibrary() && isAdminOrOwner(actorId)) {
            return file;
        }
        List<TableFile> links = tableFileRepository.findById_FileIdAndStatus(fileId, TableFileStatus.Current);
        for (TableFile link : links) {
            String linkedTableId = link.getId().gameTableId();
            // Running the table, first because it is the widest: a master sees everything on their own
            // table, private or shared, and is never the subject of a veto there. So does an admin,
            // on every table - in this and in the three loops below.
            if (masterService.canOversee(linkedTableId, actorId)) {
                return file;
            }
            // A shared attachment is read by whoever may see the table - not by a vetoed
            // person, and not by anyone outside a table nobody was shown yet.
            if (!link.isPrivate() && tableVisibilityService.canSee(linkedTableId, actorId)) {
                return file;
            }
        }
        // An answer to a request, after the links because it costs a join: what somebody handed in to a request is
        // readable by the people running the table that asked for it.
        for (String gameTableId : submissionFileRepository.findTableIdsBySubmittedFileId(fileId)) {
            if (masterService.canOversee(gameTableId, actorId)) {
                return file;
            }
        }
        // A request's blank: it has to be readable by everybody the request reaches, or "fill in this
        // form" arrives with a form nobody can open. Wider than an answer on purpose - that one is the master
        // reading an answer, this one is the table reading the question - but only as wide as the request:
        // its audience decides, not the table.
        for (TableTask task : taskFileRepository.findTasksByRequestedFileId(fileId)) {
            if (masterService.canOversee(task.getGameTable().getId(), actorId) || reaches(task, actorId)) {
                return file;
            }
        }
        // An application's attachment: what a candidate attached to their application is readable by the people
        // running the table they applied to. The repository already excludes a withdrawn or rejected
        // application, so this loop never sees a table whose master should not reach it any more.
        for (String gameTableId : registrationFileRepository.findTableIdsByApplicationFileId(fileId)) {
            if (masterService.canOversee(gameTableId, actorId)) {
                return file;
            }
        }
        throw new NotFoundException("File " + fileId + " not found");
    }

    /** Whether this person holds the admin or the owner rank, read from the database and not the token. */
    private boolean isAdminOrOwner(String actorId) {
        Set<String> roles = userRoleRepository.findActiveRoleNames(actorId);
        return roles.contains(PlatformRole.ADMIN.roleName()) || roles.contains(PlatformRole.OWNER.roleName());
    }

    /**
     * Whether a request is addressed to this person, which is who may open its blank.
     *
     * <p>A request to the candidates is read by anybody who may see the table, applied or not: the
     * list of a table's requests already shows it to them, and somebody about to apply is exactly who
     * needs to read what they will be asked for. The vetoed and everybody outside a table nobody was
     * shown yet do not see the table, so they do not read it either. A request to the players is a
     * player's alone, and one handed to a single player is that player's and nobody else's at the
     * table: a blank made for one person can be their own material.
     */
    private boolean reaches(TableTask task, String actorId) {
        String gameTableId = task.getGameTable().getId();
        return switch (task.getAudience()) {
            case Candidates -> tableVisibilityService.canSee(gameTableId, actorId);
            case Players -> plays(gameTableId, actorId);
            case Single -> {
                User target = task.getTargetUser();
                yield target != null && target.getId().equals(actorId) && plays(gameTableId, actorId);
            }
        };
    }

    /** Whether this person plays at the table: a live {@code Player} registration, which a vetoed one no longer is. */
    private boolean plays(String gameTableId, String actorId) {
        return registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                gameTableId, actorId, List.of(TableRegistrationStatus.Player));
    }

    /**
     * Refuses a category this person has no business filing something of their own under.
     *
     * <p>It is a rule of the domain and not of the screen, which is why it is here: the frontend
     * offers only what {@link #personalCategoriesOf} allows, but a request that names another category
     * is a request, and the authority over it is the service.
     *
     * <p>Null passes, and that is the normal case: a file uploaded from inside a flow declares
     * nothing, because the link that follows is what classifies it.
     *
     * @throws InvalidRequestException naming the category, so the frontend can say which one
     */
    private void requireOwnCategory(@Nullable FileCategory category, String actorId) {
        if (category != null && !personalCategoriesOf(actorId).contains(category)) {
            throw new InvalidRequestException(
                    "Cannot file a personal upload under " + category,
                    "FILE_CATEGORY_NOT_YOURS",
                    Map.of("category", category.name()));
        }
    }

    /**
     * The whitelist check, before anything is written.
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
     * The per-file cap - the one piece of the old per-user quota that was kept.
     *
     * <p>The limit travels as a number of bytes and not as "2 MB": the sentence is the frontend's to
     * write, in the reader's language and its own units.
     *
     * @throws InvalidRequestException carrying the limit, because "too large" without a number tells
     *                                 nobody what to do next
     */
    private void requireWithinSizeLimit(long sizeBytes) {
        long limit = settingsService.maxFileSizeBytes();
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
     * else: the download header echoes it and the filesystem never sees it. Sanitizing it here
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
