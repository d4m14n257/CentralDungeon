package com.centraldungeon.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.config.StorageProperties;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.storage.StorageService;
import com.centraldungeon.files.dto.PublishFileRequest;
import com.centraldungeon.files.dto.UpdateFileRequest;
import com.centraldungeon.files.dto.UploadFileRequest;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

/**
 * The file's own rules: what gets in, what gets stored once, and who is allowed to read it back.
 *
 * <p>Two of these cases are about holes the legacy actually had rather than features being added -
 * the filename that became a path (M21.5) and the upload with no limit of any kind (M21.3). They are
 * written as tests so the fix cannot quietly regress.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    @Mock
    private StoredFileRepository fileRepository;

    @Mock
    private TableFileRepository tableFileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private MasterService masterService;

    private final FileMapper fileMapper = org.mapstruct.factory.Mappers.getMapper(FileMapper.class);

    private final StorageProperties storageProperties = new StorageProperties(
            "target/test-storage", DataSize.ofMegabytes(2), Set.of("application/pdf", "image/png"), Duration.ofDays(90));

    private FileService fileService() {
        return new FileService(
                fileRepository,
                tableFileRepository,
                userRepository,
                storageService,
                storageProperties,
                masterService,
                fileMapper);
    }

    // ---------------------------------------------------------------- upload

    @Test
    void storesTheContentUnderAGeneratedKeyAndNeverUnderTheFilename() {
        User owner = persistedUser("player-1");
        when(userRepository.findById("player-1")).thenReturn(Optional.of(owner));
        when(fileRepository.save(any(StoredFile.class))).thenAnswer(saveWithId("file-1"));

        fileService().upload(pdf("ficha.pdf", "hoja"), new UploadFileRequest(FileType.Private), "player-1");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(key.capture(), any());
        assertThat(key.getValue()).doesNotContain("ficha").doesNotContain(".pdf");

        ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
        verify(fileRepository).save(saved.capture());
        assertThat(saved.getValue().getStorageKey()).isEqualTo(key.getValue());
        assertThat(saved.getValue().getName()).isEqualTo("ficha.pdf");
    }

    /**
     * The legacy's path traversal (M21.5), from the other side: the name is kept verbatim because it
     * is only ever metadata, and what reaches storage is a key that cannot contain it (#80).
     */
    @Test
    void aTraversalFilenameIsKeptAsMetadataAndNeverReachesStorage() {
        User owner = persistedUser("player-1");
        when(userRepository.findById("player-1")).thenReturn(Optional.of(owner));
        when(fileRepository.save(any(StoredFile.class))).thenAnswer(saveWithId("file-1"));

        fileService().upload(pdf("../../etc/passwd", "x"), new UploadFileRequest(FileType.Private), "player-1");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(key.capture(), any());
        assertThat(key.getValue()).doesNotContain("..").doesNotContain("/");

        ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
        verify(fileRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("../../etc/passwd");
    }

    @Test
    void refusesAMimeTypeThatIsNotOnTheWhitelist() {
        MockMultipartFile executable =
                new MockMultipartFile("file", "cheat.exe", "application/x-msdownload", "MZ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fileService().upload(executable, new UploadFileRequest(FileType.Private), "player-1"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(thrown -> assertThat(((InvalidRequestException) thrown).getErrorCode())
                        .isEqualTo("FILE_TYPE_NOT_ALLOWED"));

        verify(storageService, never()).store(anyString(), any());
    }

    /** The cap carries its number, because "too large" without one tells nobody what to do (#197). */
    @Test
    void refusesAFileOverTheCapAndSaysWhatTheCapIs() {
        MockMultipartFile huge = new MockMultipartFile("file", "mapa.png", "image/png", new byte[3 * 1024 * 1024]);

        assertThatThrownBy(() -> fileService().upload(huge, new UploadFileRequest(FileType.Private), "player-1"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(thrown -> {
                    InvalidRequestException failure = (InvalidRequestException) thrown;
                    assertThat(failure.getErrorCode()).isEqualTo("FILE_TOO_LARGE");
                    assertThat(failure.getErrorParams()).containsEntry("maxBytes", String.valueOf(2 * 1024 * 1024));
                });

        verify(storageService, never()).store(anyString(), any());
    }

    @Test
    void refusesAnUploaderWhoDeclaresTheFilePublic() {
        assertThatThrownBy(() ->
                        fileService().upload(pdf("reglas.pdf", "x"), new UploadFileRequest(FileType.Public), "player-1"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(thrown -> assertThat(((InvalidRequestException) thrown).getErrorCode())
                        .isEqualTo("FILE_CANNOT_SELF_PUBLISH"));
    }

    /** #75's first lever: the same sheet uploaded twice is one row and one blob. */
    @Test
    void recognisesContentTheSamePersonAlreadyUploadedAndDoesNotStoreItTwice() {
        User owner = persistedUser("player-1");
        StoredFile existing = persistedFile("file-1", owner, FileType.Private);
        when(userRepository.findById("player-1")).thenReturn(Optional.of(owner));
        when(fileRepository.findFirstByUserCreated_IdAndContentHashAndStatus(
                        eq("player-1"), anyString(), eq(FileStatus.Current)))
                .thenReturn(Optional.of(existing));

        var response = fileService().upload(pdf("otra-copia.pdf", "hoja"), new UploadFileRequest(FileType.Private), "player-1");

        assertThat(response.id()).isEqualTo("file-1");
        verify(storageService, never()).store(anyString(), any());
        verify(fileRepository, never()).save(any(StoredFile.class));
        assertThat(existing.getLastUsedAt()).isNotNull();
    }

    /** Dedup is scoped to the owner: the schema's unique storage key forbids sharing a blob. */
    @Test
    void doesNotHandSomebodyElseTheFileThatMatchesTheirUpload() {
        User owner = persistedUser("player-2");
        when(userRepository.findById("player-2")).thenReturn(Optional.of(owner));
        when(fileRepository.findFirstByUserCreated_IdAndContentHashAndStatus(
                        eq("player-2"), anyString(), eq(FileStatus.Current)))
                .thenReturn(Optional.empty());
        when(fileRepository.save(any(StoredFile.class))).thenAnswer(saveWithId("file-2"));

        fileService().upload(pdf("ficha.pdf", "hoja"), new UploadFileRequest(FileType.Private), "player-2");

        verify(storageService).store(anyString(), any());
    }

    // ---------------------------------------------------------------- reading

    @Test
    void theOwnerCanAlwaysDownloadTheirOwnFile() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.SingleUse);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));
        when(storageService.read(file.getStorageKey())).thenReturn("hoja".getBytes(StandardCharsets.UTF_8));

        FileDownload download = fileService().download("file-1", "player-1");

        assertThat(download.name()).isEqualTo("ficha.pdf");
        assertThat(download.content()).asString().isEqualTo("hoja");
        assertThat(file.getLastUsedAt()).isNotNull();
    }

    /** A master reads everything on their own table - and it is the row in masters that says so (#135). */
    @Test
    void whoeverRunsTheTableReadsEvenAPrivateAttachment() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.SingleUse);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));
        when(tableFileRepository.findById_FileIdAndStatus("file-1", TableFileStatus.Current))
                .thenReturn(List.of(new TableFile("table-1", "file-1", TableFileType.Preparation, true)));
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(storageService.read(anyString())).thenReturn(new byte[] {1});

        assertThat(fileService().download("file-1", "master-1")).isNotNull();
    }

    /**
     * What a table shares is as reachable as the table: the same file already travels inside the
     * table's own detail, so refusing the download would list it and then refuse to hand it over. A
     * private attachment is the master's notes and stops here.
     */
    @Test
    void whatATableSharesIsReadableAndWhatItKeepsPrivateIsNot() {
        StoredFile file = persistedFile("file-1", persistedUser("master-1"), FileType.SingleUse);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));
        when(masterService.isMasterOf(anyString(), eq("reader-9"))).thenReturn(false);
        when(storageService.read(anyString())).thenReturn(new byte[] {1});

        when(tableFileRepository.findById_FileIdAndStatus("file-1", TableFileStatus.Current))
                .thenReturn(List.of(new TableFile("table-1", "file-1", TableFileType.Preparation, false)));
        assertThat(fileService().download("file-1", "reader-9")).isNotNull();

        when(tableFileRepository.findById_FileIdAndStatus("file-1", TableFileStatus.Current))
                .thenReturn(List.of(new TableFile("table-1", "file-1", TableFileType.Preparation, true)));
        assertThatThrownBy(() -> fileService().download("file-1", "reader-9")).isInstanceOf(NotFoundException.class);
    }

    /** A published file is published: the audience decides where it is listed, not who may open it. */
    @Test
    void anybodyCanDownloadAPublishedFile() {
        StoredFile file = persistedFile("file-1", persistedUser("admin-1"), FileType.Public);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));
        when(storageService.read(anyString())).thenReturn(new byte[] {1});

        assertThat(fileService().download("file-1", "stranger-1")).isNotNull();
    }

    /** 404 and not 403: confirming a file exists to somebody with no business knowing is the leak. */
    @Test
    void aStrangerGetsNotFoundRatherThanForbidden() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.SingleUse);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));
        when(tableFileRepository.findById_FileIdAndStatus("file-1", TableFileStatus.Current)).thenReturn(List.of());

        assertThatThrownBy(() -> fileService().download("file-1", "stranger-1")).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- keeping and letting go

    @Test
    void promotingASingleUseFilePutsItInTheReuseHistory() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.SingleUse);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        fileService().update("file-1", new UpdateFileRequest("Mi ficha.pdf", true), "player-1");

        assertThat(file.getFileType()).isEqualTo(FileType.Private);
        assertThat(file.getName()).isEqualTo("Mi ficha.pdf");
    }

    @Test
    void renamingNeverTouchesTheContent() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.Private);
        String keyBefore = file.getStorageKey();
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        fileService().update("file-1", new UpdateFileRequest("otro nombre.pdf", true), "player-1");

        assertThat(file.getStorageKey()).isEqualTo(keyBefore);
        verify(storageService, never()).store(anyString(), any());
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void refusesToLetSomebodyElseRenameOrDeleteAFile() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.Private);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService().update("file-1", new UpdateFileRequest("mio.pdf", true), "player-2"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> fileService().delete("file-1", "player-2"))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void refusesToLetTheUploaderTouchAFileThePlatformPublished() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.Public);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService().delete("file-1", "player-1"))
                .isInstanceOf(ForbiddenActionException.class);
    }

    /** Deleting marks; the bytes wait for the platform owner, which is F5 (#25, #66). */
    @Test
    void deletingMarksTheRowAndLeavesTheBytesAlone() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.Private);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        fileService().delete("file-1", "player-1");

        assertThat(file.getStatus()).isEqualTo(FileStatus.Deleted);
        assertThat(file.getDeletedAt()).isNotNull();
        verify(storageService, never()).delete(anyString());
    }

    // ---------------------------------------------------------------- publishing

    @Test
    void publishingKeepsTheUploaderAndRecordsTheAudience() {
        User uploader = persistedUser("admin-1");
        StoredFile file = persistedFile("file-1", uploader, FileType.Private);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));
        when(tableFileRepository.countUsesByFileIds(List.of("file-1"))).thenReturn(List.of());

        var response = fileService().publish("file-1", new PublishFileRequest(PublicAudience.Players));

        assertThat(file.getFileType()).isEqualTo(FileType.Public);
        assertThat(file.getPublicAudience()).isEqualTo(PublicAudience.Players);
        assertThat(response.ownerId()).isEqualTo("admin-1");
    }

    @Test
    void unpublishingReturnsTheFileToItsOwnerAsSomethingTheyKeep() {
        StoredFile file = persistedFile("file-1", persistedUser("admin-1"), FileType.Public);
        file.setPublicAudience(PublicAudience.Masters);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));
        when(tableFileRepository.countUsesByFileIds(List.of("file-1"))).thenReturn(List.of());

        fileService().unpublish("file-1");

        assertThat(file.getFileType()).isEqualTo(FileType.Private);
        assertThat(file.getPublicAudience()).isNull();
    }

    @Test
    void refusesToUnpublishSomethingThatWasNeverPublished() {
        StoredFile file = persistedFile("file-1", persistedUser("admin-1"), FileType.Private);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService().unpublish("file-1")).isInstanceOf(ForbiddenActionException.class);
    }

    // ---------------------------------------------------------------- attaching

    @Test
    void aMasterMayAttachAPublishedFileTheyDidNotUpload() {
        StoredFile file = persistedFile("file-1", persistedUser("admin-1"), FileType.Public);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        assertThat(fileService().requireAttachable("file-1", "master-1")).isSameAs(file);
        assertThat(file.getLastUsedAt()).isNotNull();
    }

    @Test
    void refusesToAttachSomebodyElsesPrivateFile() {
        StoredFile file = persistedFile("file-1", persistedUser("player-1"), FileType.Private);
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService().requireAttachable("file-1", "master-1"))
                .isInstanceOf(ForbiddenActionException.class);
    }

    // ---------------------------------------------------------------- fixtures

    private static org.mockito.stubbing.Answer<StoredFile> saveWithId(String id) {
        return invocation -> {
            StoredFile file = invocation.getArgument(0);
            ReflectionTestUtils.setField(file, "id", id);
            ReflectionTestUtils.setField(file, "createdAt", java.time.LocalDateTime.now());
            return file;
        };
    }

    private static MockMultipartFile pdf(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    private static User persistedUser(String id) {
        User user = new User("discord-" + id, id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static StoredFile persistedFile(String id, User owner, FileType type) {
        StoredFile file = new StoredFile("ficha.pdf", "key-" + id, "hash-" + id, "application/pdf", 10, type, owner);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "createdAt", java.time.LocalDateTime.now());
        return file;
    }
}
