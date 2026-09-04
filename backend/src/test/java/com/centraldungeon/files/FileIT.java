package com.centraldungeon.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.files.dto.AdminFileResponse;
import com.centraldungeon.files.dto.FileResponse;
import com.centraldungeon.files.dto.LinkTableFileRequest;
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
                pdf("ficha-por-defecto.pdf", "la de la comunidad"), new UploadFileRequest(FileType.Private), admin.getId());
        fileService.publish(defaultSheet.id(), new PublishFileRequest(PublicAudience.Players));

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
        fileService.publish(published.id(), new PublishFileRequest(PublicAudience.Announcements));
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
                fileService.listForAdmin("ficha", List.of(), List.of(), PageRequest.of(0, 10));

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
        return fileService.upload(pdf(filename, content), new UploadFileRequest(FileType.Private), master.getId());
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
