package com.centraldungeon.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.files.dto.AdminFileResponse;
import com.centraldungeon.files.dto.FileResponse;
import com.centraldungeon.files.dto.FileUsageResponse;
import com.centraldungeon.files.dto.LinkTableFileRequest;
import com.centraldungeon.files.dto.PublicFileResponse;
import com.centraldungeon.files.dto.PublishFileRequest;
import com.centraldungeon.files.dto.SharedFileResponse;
import com.centraldungeon.files.dto.TableFileResponse;
import com.centraldungeon.files.dto.UploadFileRequest;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The half of the file story only a real database and a real disk can answer.
 *
 * <p><b>The headline case is the one #79 exists for</b>: one upload, two tables, <em>one</em> row in
 * {@code files}. A unit test can only assert that {@code save} was not called twice; this counts the
 * rows. It also exercises the composite key of {@code table_files}, which is what makes detaching and
 * re-attaching a revive rather than a duplicate-key failure, and the purge query of #75, whose
 * {@code not exists} and {@code coalesce} are exactly the parts a mock cannot check.
 *
 * <p>Storage points at a temporary directory rather than the dev root, so a run never leaves blobs in
 * the working tree. Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection}: see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FileIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private FileService fileService;

    @Autowired
    private TableFileService tableFileService;

    @Autowired
    private FileRetentionService fileRetentionService;

    @Autowired
    private StoredFileRepository fileRepository;

    @Autowired
    private TableFileRepository tableFileRepository;

    @Autowired
    private FileCategoryLinkRepository categoryRepository;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterService masterService;

    @Autowired
    private UserRepository userRepository;

    private User master;
    private GameTable firstTable;
    private GameTable secondTable;

    @BeforeEach
    void setUp() {
        master = userRepository.save(new User(randomDiscordId(), "File Master"));
        firstTable = gameTableRepository.save(new GameTable("Mesa de los martes", master));
        secondTable = gameTableRepository.save(new GameTable("Mesa de los jueves", master));
        masterService.createPrimary(firstTable, master);
        masterService.createPrimary(secondTable, master);
    }

    /**
     * The criterion of fase-1-master.md §4, end to end: a master uploads a character sheet, attaches
     * it to two tables, and there is one file.
     */
    @Test
    void oneUploadAttachedToTwoTablesIsStillOneFile() {
        FileResponse sheet = upload("ficha.pdf", "hoja de personaje");

        attach(firstTable, sheet.id(), false);
        attach(secondTable, sheet.id(), false);

        assertThat(fileRepository.findById(sheet.id())).isPresent();
        assertThat(tableFileRepository.countUsesByFileIds(List.of(sheet.id())))
                .singleElement()
                .extracting(FileUsageCount::uses)
                .isEqualTo(2L);
    }

    /**
     * The uses an owner sees, resolved against a real database (#232).
     *
     * <p>This is the case a mock cannot check. Both queries are constructor expressions carrying an
     * <b>enum literal</b> - {@code FileCategory.TableMaterial} written into the JPQL itself - joined across
     * a composite key, and whether Hibernate accepts that shape is not something a stubbed repository
     * has an opinion about. The unit test asserts the merge; this asserts the SQL exists.
     */
    @Test
    void theOwnersOwnListNamesEveryTableThatUsesEachFile() {
        FileResponse sheet = upload("ficha.pdf", "hoja de personaje");
        attach(firstTable, sheet.id(), false);
        attach(secondTable, sheet.id(), true);

        PageResponse<FileResponse> mine = fileService.listMine(master.getId(), null, null, PageRequest.of(0, 20));

        assertThat(mine.content())
                .filteredOn(file -> file.id().equals(sheet.id()))
                .singleElement()
                .extracting(FileResponse::usages, list(FileUsageResponse.class))
                .extracting(FileUsageResponse::category, FileUsageResponse::contextName)
                .containsExactlyInAnyOrder(
                        tuple("TableMaterial", "Mesa de los martes"), tuple("TableMaterial", "Mesa de los jueves"));
    }

    /** A file nothing points at reports no uses, which is what the purge of #75 is about to notice. */
    @Test
    void aFileNoTableHoldsReportsNoUses() {
        FileResponse loose = upload("suelto.pdf", "nada lo usa");

        PageResponse<FileResponse> mine = fileService.listMine(master.getId(), null, null, PageRequest.of(0, 20));

        assertThat(mine.content())
                .filteredOn(file -> file.id().equals(loose.id()))
                .singleElement()
                .extracting(FileResponse::usages, list(FileUsageResponse.class))
                .isEmpty();
    }

    /**
     * The picker's search box actually narrows the history now.
     *
     * <p>The frontend had been sending {@code q} on this call all along and the endpoint had no
     * parameter to receive it, so "buscar entre mis archivos" returned everything regardless of what
     * was typed - and a reuse history you cannot search is one nobody reuses from (#65).
     */
    @Test
    void theOwnersListIsNarrowedByWhatTheyTyped() {
        upload("ficha-thalia.pdf", "la elfa");
        upload("mapa-pantano.pdf", "el pantano");

        PageResponse<FileResponse> searched = fileService.listMine(master.getId(), "thalia", null, PageRequest.of(0, 20));

        assertThat(searched.content()).extracting(FileResponse::name).containsExactly("ficha-thalia.pdf");
    }

    /**
     * <b>The link classifies the file, and the membership is never revoked</b> (#233).
     *
     * <p>The whole design in one case: nobody declared a cajón on the upload, attaching the file to a
     * table put it in one, and detaching it left the cajón standing while the <em>use</em> went away.
     * That difference is the reason a cajón is stored and a use is derived - a mock cannot tell them
     * apart because both are just rows to it.
     */
    @Test
    void attachingClassifiesTheFileAndDetachingNeverUnclassifiesIt() {
        FileResponse map = upload("pantano.pdf", "el mapa");
        // Nothing declared anything: an upload from inside a flow carries no cajón at all.
        assertThat(cajonesOf(map.id())).isEmpty();

        attach(firstTable, map.id(), false);
        assertThat(cajonesOf(map.id())).containsExactly("TableMaterial");

        tableFileService.detach(firstTable.getId(), map.id(), master.getId());
        assertThat(cajonesOf(map.id())).containsExactly("TableMaterial");
        assertThat(fileService.listMine(master.getId(), null, null, PageRequest.of(0, 20)).content())
                .filteredOn(file -> file.id().equals(map.id()))
                .singleElement()
                .extracting(FileResponse::usages, list(FileUsageResponse.class))
                .isEmpty();
    }

    /**
     * The same file in two cajones, which is what the column could never express (#233).
     *
     * <p>A sheet attached to a table and later handed in to that table's request belongs to both
     * flows, and deduplication (#75) guarantees it is one row - so one row has to hold two answers.
     */
    @Test
    void aFileUsedInTwoFlowsBelongsToTwoCajones() {
        FileResponse sheet = upload("ficha.pdf", "la elfa");
        attach(firstTable, sheet.id(), false);
        fileService.classify(sheet.id(), FileCategory.PlayerSubmission);

        assertThat(cajonesOf(sheet.id())).containsExactlyInAnyOrder("TableMaterial", "PlayerSubmission");
    }

    /**
     * The cajón as a search command, not only as a filter (#239).
     *
     * <p>It is the one field of the search language that is not a column: membership is a row per
     * cajón, so the criterion resolves to an {@code exists} rather than a {@code LIKE}. Whether
     * Hibernate builds that subquery from inside the term folding is not something a mock can say.
     */
    @Test
    void theSearchBoxNarrowsByCajonWithItsOwnCommand() {
        FileResponse attached = upload("ficha.pdf", "la elfa");
        upload("suelto.pdf", "nada lo usa");
        attach(firstTable, attached.id(), false);

        PageResponse<FileResponse> material =
                fileService.listMine(master.getId(), "/file_categories TableMaterial", null, PageRequest.of(0, 20));
        PageResponse<FileResponse> asSubmission =
                fileService.listMine(master.getId(), "/file_categories PlayerSubmission", null, PageRequest.of(0, 20));

        assertThat(material.content()).extracting(FileResponse::name).containsExactly("ficha.pdf");
        // A cajón nothing of theirs is in finds nothing, rather than everything.
        assertThat(asSubmission.content()).isEmpty();
    }

    /** A value that names no cajón finds nothing, the same way a typo does in any other field. */
    @Test
    void aCajonThatDoesNotExistFindsNothing() {
        upload("ficha.pdf", "la elfa");

        PageResponse<FileResponse> none =
                fileService.listMine(master.getId(), "/file_categories NoSuchCajon", null, PageRequest.of(0, 20));

        assertThat(none.content()).isEmpty();
    }

    /** Somebody's own library, narrowed to one cajón, through the {@code exists} subquery (#233). */
    @Test
    void theOwnersListIsNarrowedByCajon() {
        FileResponse attached = upload("ficha.pdf", "la elfa");
        upload("suelto.pdf", "nada lo usa");
        attach(firstTable, attached.id(), false);

        PageResponse<FileResponse> material =
                fileService.listMine(master.getId(), null, FileCategory.TableMaterial, PageRequest.of(0, 20));

        assertThat(material.content()).extracting(FileResponse::name).containsExactly("ficha.pdf");
    }

    /**
     * Uploading the same content twice answers 200's worth of "already there", not a second row
     * (#234, #75).
     */
    @Test
    void theSecondUploadOfTheSameContentIsRecognisedRatherThanStored() {
        UploadResult first =
                fileService.upload(pdf("ficha.pdf", "idéntico"), new UploadFileRequest(FileType.Private, null), master.getId());
        UploadResult second =
                fileService.upload(pdf("copia.pdf", "idéntico"), new UploadFileRequest(FileType.Private, null), master.getId());

        assertThat(first.deduplicated()).isFalse();
        assertThat(second.deduplicated()).isTrue();
        assertThat(second.file().id()).isEqualTo(first.file().id());
        // The name of the first upload is what survives: the row was recognised, not rewritten.
        assertThat(second.file().name()).isEqualTo("ficha.pdf");
    }

    /** The published set narrows by category, which is what makes it browsable (#233, #79). */
    @Test
    void thePublishedSetCanBeNarrowedToOneKindOfDocument() {
        User admin = userRepository.save(new User(randomDiscordId(), "Library Admin"));
        FileResponse sheet = fileService
                .upload(pdf("ficha-comunidad.pdf", "la de todos"), new UploadFileRequest(FileType.Private, null), admin.getId())
                .file();
        FileResponse rules = fileService
                .upload(pdf("reglamento.pdf", "las normas"), new UploadFileRequest(FileType.Private, null), admin.getId())
                .file();
        // The sheet goes into both master-side cajones at once: it is asked for while the table
        // recruits *and* once it is running. That is the case a single column could not express.
        fileService.publish(sheet.id(), new PublishFileRequest(List.of(FileCategory.TableMaterial, FileCategory.MasterRequest)));
        fileService.publish(rules.id(), new PublishFileRequest(List.of(FileCategory.Announcement)));

        PageResponse<PublicFileResponse> forRequests =
                fileService.listPublic(FileCategory.MasterRequest, PageRequest.of(0, 20));
        PageResponse<PublicFileResponse> forTables =
                fileService.listPublic(FileCategory.TableMaterial, PageRequest.of(0, 20));

        // One file, two cajones, and it shows up in both without being stored twice.
        assertThat(forRequests.content()).extracting(PublicFileResponse::id).containsExactly(sheet.id());
        assertThat(forTables.content()).extracting(PublicFileResponse::id).containsExactly(sheet.id());
        assertThat(fileRepository.findById(sheet.id())).isPresent();
    }

    /**
     * The two player-side cajones refuse to be published into (#233).
     *
     * <p>They hold what individual people answered with. A blank offered to the whole community is
     * not an answer - it belongs in the master-side cajón the request was written from.
     */
    @Test
    void refusesToPublishIntoACajonThatHoldsPeoplesAnswers() {
        FileResponse file = upload("ficha.pdf", "la de todos");

        assertThatThrownBy(() ->
                        fileService.publish(file.id(), new PublishFileRequest(List.of(FileCategory.PlayerApplication))))
                .isInstanceOf(InvalidRequestException.class);
    }

    /** Taking a file off one table leaves the file, and leaves the other table's copy of it (#79). */
    @Test
    void detachingFromOneTableLeavesTheFileAndTheOtherTable() {
        FileResponse sheet = upload("ficha.pdf", "hoja de personaje");
        attach(firstTable, sheet.id(), false);
        attach(secondTable, sheet.id(), false);

        tableFileService.detach(firstTable.getId(), sheet.id(), master.getId());

        assertThat(fileRepository.findByIdAndStatus(sheet.id(), FileStatus.Current)).isPresent();
        assertThat(tableFileService.listForTable(firstTable.getId(), master.getId())).isEmpty();
        assertThat(tableFileService.listForTable(secondTable.getId(), master.getId()))
                .singleElement()
                .extracting(TableFileResponse::fileId)
                .isEqualTo(sheet.id());
    }

    /**
     * Against the real composite primary key: re-attaching what was detached has to update the row
     * that is still there, not insert a second one with the same key.
     */
    @Test
    void reattachingAfterDetachingDoesNotCollideOnTheCompositeKey() {
        FileResponse sheet = upload("ficha.pdf", "hoja");
        attach(firstTable, sheet.id(), false);
        tableFileService.detach(firstTable.getId(), sheet.id(), master.getId());

        attach(firstTable, sheet.id(), true);

        assertThat(tableFileRepository.findById_GameTableId(firstTable.getId())).hasSize(1);
        assertThat(tableFileService.listForTable(firstTable.getId(), master.getId()))
                .singleElement()
                .extracting(TableFileResponse::isPrivate)
                .isEqualTo(true);
    }

    /** #75 on real rows: the same bytes from the same person are recognised, not stored again. */
    @Test
    void uploadingTheSameContentTwiceWritesOneRow() {
        FileResponse first = upload("ficha.pdf", "exactamente lo mismo");
        FileResponse second = upload("copia-de-ficha.pdf", "exactamente lo mismo");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(fileRepository.findByUserCreated_IdAndStatus(master.getId(), FileStatus.Current, PageRequest.of(0, 10)))
                .hasSize(1);
    }

    /** The bytes survive a round trip through gzip and the staging move (#75, M26.2). */
    @Test
    void theContentComesBackExactlyAsItWentIn() {
        FileResponse sheet = upload("ficha.pdf", "contenido con acentos: ñáé");

        FileDownload download = fileService.download(sheet.id(), master.getId());

        assertThat(new String(download.content(), StandardCharsets.UTF_8)).isEqualTo("contenido con acentos: ñáé");
        assertThat(download.name()).isEqualTo("ficha.pdf");
    }

    /** A published file is attachable by a master who did not upload it, and is not copied (#79). */
    @Test
    void aPublishedFileIsAttachedRatherThanCopied() {
        User admin = userRepository.save(new User(randomDiscordId(), "File Admin"));
        FileResponse defaultSheet = fileService.upload(
                        pdf("ficha-por-defecto.pdf", "la de la comunidad"),
                        new UploadFileRequest(FileType.Private, null),
                        admin.getId())
                .file();
        fileService.publish(defaultSheet.id(), new PublishFileRequest(List.of(FileCategory.TableMaterial)));

        attach(firstTable, defaultSheet.id(), false);

        List<SharedFileResponse> shared = tableFileService.sharedFilesOf(firstTable.getId());
        assertThat(shared).singleElement().extracting(SharedFileResponse::fileId).isEqualTo(defaultSheet.id());
        assertThat(fileRepository.findById(defaultSheet.id()))
                .get()
                .extracting(file -> file.getUserCreated().getId())
                .isEqualTo(admin.getId());
    }

    /** What a player sees: the shared attachments and nothing else. */
    @Test
    void whatTheTableSharesExcludesThePrivateAttachments() {
        FileResponse shared = upload("mapa.pdf", "el mapa");
        FileResponse notes = upload("notas.pdf", "mis notas");
        attach(firstTable, shared.id(), false);
        attach(firstTable, notes.id(), true);

        assertThat(tableFileService.sharedFilesOf(firstTable.getId()))
                .singleElement()
                .extracting(SharedFileResponse::fileId)
                .isEqualTo(shared.id());
    }

    /**
     * The purge query of #75 against real SQL: its {@code not exists} over {@code table_files} and its
     * {@code coalesce} of the two dates are the parts no mock can check.
     */
    @Test
    void thePurgeSkipsAttachedFilesAndPublishedOnesAndTakesTheRest() {
        FileResponse stale = upload("viejo.pdf", "nadie lo abre");
        FileResponse attached = upload("en-uso.pdf", "una mesa lo muestra");
        FileResponse published = upload("reglas.pdf", "las reglas");
        attach(firstTable, attached.id(), false);
        fileService.publish(published.id(), new PublishFileRequest(List.of(FileCategory.Announcement)));
        ageOut(stale.id(), attached.id(), published.id());

        fileRetentionService.markUnusedFiles();

        assertThat(statusOf(stale.id())).isEqualTo(FileStatus.Deleted);
        assertThat(statusOf(attached.id())).isEqualTo(FileStatus.Current);
        assertThat(statusOf(published.id())).isEqualTo(FileStatus.Current);
    }

    /**
     * The purge through the <b>scheduled entry point</b>, which is the only way to catch what a unit
     * test structurally cannot: a self-invocation that bypasses the proxy leaves the whole pass
     * without a transaction, the entities come back detached, and marking them writes to nothing. The
     * job would log a count and change zero rows, silently. So this calls the method the scheduler
     * calls, on the real bean, and then asks the database.
     */
    @Test
    void theScheduledPassActuallyWritesItsMarksToTheDatabase() {
        FileResponse stale = upload("olvidado.pdf", "nadie lo abre nunca");
        ageOut(stale.id());

        fileRetentionService.purgeUnusedFiles();

        assertThat(statusOf(stale.id())).isEqualTo(FileStatus.Deleted);
        assertThat(fileRepository.findById(stale.id()).orElseThrow().getDeletedAt()).isNotNull();
    }

    /** /admin/files shows the usage count, which is what makes #79 visible instead of merely true. */
    @Test
    void theAdminListingCountsHowManyTablesUseEachFile() {
        FileResponse sheet = upload("ficha.pdf", "hoja");
        attach(firstTable, sheet.id(), false);
        attach(secondTable, sheet.id(), false);

        PageResponse<AdminFileResponse> page =
                fileService.listForAdmin("ficha", List.of(), List.of(), null, PageRequest.of(0, 10));

        assertThat(page.content())
                .filteredOn(file -> file.id().equals(sheet.id()))
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.uses()).isEqualTo(2L);
                    assertThat(file.ownerName()).isEqualTo("File Master");
                });
    }

    // ---------------------------------------------------------------- fixtures

    private FileResponse upload(String filename, String content) {
        return fileService.upload(pdf(filename, content), new UploadFileRequest(FileType.Private, null), master.getId()).file();
    }

    /** The cajones one file belongs to, straight out of the bridge table. */
    private List<String> cajonesOf(String fileId) {
        return categoryRepository.findByFileIds(List.of(fileId)).stream()
                .map(link -> link.getId().category().name())
                .toList();
    }

    private void attach(GameTable table, String fileId, boolean isPrivate) {
        tableFileService.attach(
                table.getId(), new LinkTableFileRequest(fileId, TableFileType.Preparation, isPrivate), master.getId());
    }

    /** Backdates the files so the retention window has something to find, without waiting 90 days. */
    private void ageOut(String... fileIds) {
        LocalDateTime longAgo = LocalDateTime.now().minusDays(200);
        for (String fileId : fileIds) {
            StoredFile file = fileRepository.findById(fileId).orElseThrow();
            file.setLastUsedAt(longAgo);
            fileRepository.save(file);
        }
    }

    private FileStatus statusOf(String fileId) {
        return fileRepository.findById(fileId).orElseThrow().getStatus();
    }

    private static MockMultipartFile pdf(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
