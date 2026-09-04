package com.centraldungeon.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.files.dto.LinkTableFileRequest;
import com.centraldungeon.files.dto.SharedFileResponse;
import com.centraldungeon.files.dto.TableFileResponse;
import com.centraldungeon.files.dto.UpdateTableFileRequest;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.users.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The link, and the one property everything here exists to protect: <b>attaching is not copying</b>
 * (#79). Taking a map off one table cannot touch the map, and cannot touch any other table that has
 * it - which is what lets the community publish one default character sheet instead of one per master.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableFileServiceTest {

    @Mock
    private TableFileRepository tableFileRepository;

    @Mock
    private StoredFileRepository fileRepository;

    @Mock
    private FileService fileService;

    @Mock
    private MasterService masterService;

    private final FileMapper fileMapper = org.mapstruct.factory.Mappers.getMapper(FileMapper.class);

    private TableFileService tableFileService() {
        return new TableFileService(tableFileRepository, fileRepository, fileService, masterService, fileMapper);
    }

    // ---------------------------------------------------------------- pertenencia

    /** The role is not the membership (#135): only a row in masters opens this door. */
    @Test
    void refusesEveryOperationToSomebodyWhoDoesNotRunTheTable() {
        when(masterService.isMasterOf("table-1", "outsider-1")).thenReturn(false);
        TableFileService service = tableFileService();

        assertThatThrownBy(() -> service.listForTable("table-1", "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.attach(
                        "table-1", new LinkTableFileRequest("file-1", TableFileType.Preparation, false), "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.detach("table-1", "file-1", "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.update(
                        "table-1", "file-1", new UpdateTableFileRequest(TableFileType.Session, true), "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);

        verify(tableFileRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- attaching

    @Test
    void attachesAFileWithoutCopyingIt() {
        StoredFile file = persistedFile("file-1", persistedUser("master-1"), FileType.Private);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(fileService.requireAttachable("file-1", "master-1")).thenReturn(file);
        when(tableFileRepository.findById(new TableFileId("table-1", "file-1"))).thenReturn(Optional.empty());
        when(tableFileRepository.save(any(TableFile.class))).thenAnswer(returnsArgument());

        TableFileResponse response = tableFileService()
                .attach("table-1", new LinkTableFileRequest("file-1", TableFileType.Preparation, false), "master-1");

        assertThat(response.fileId()).isEqualTo("file-1");
        assertThat(response.isPrivate()).isFalse();
        assertThat(response.isOwnedByMe()).isTrue();
        verify(fileRepository, never()).save(any(StoredFile.class));
    }

    /** #79's point of the whole thing: a master borrows the platform's sheet rather than duplicating it. */
    @Test
    void attachesAPublishedFileTheMasterDidNotUpload() {
        StoredFile published = persistedFile("file-1", persistedUser("admin-1"), FileType.Public);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(fileService.requireAttachable("file-1", "master-1")).thenReturn(published);
        when(tableFileRepository.findById(any())).thenReturn(Optional.empty());
        when(tableFileRepository.save(any(TableFile.class))).thenAnswer(returnsArgument());

        TableFileResponse response = tableFileService()
                .attach("table-1", new LinkTableFileRequest("file-1", TableFileType.Preparation, false), "master-1");

        assertThat(response.fileType()).isEqualTo("Public");
        assertThat(response.isOwnedByMe()).isFalse();
    }

    /**
     * The pair is the primary key, so a detach-and-reattach has to revive the row it marked. Inserting
     * would collide with a key that is still there - the trap TableScheduleStatus documents for the
     * agenda, and the reason a detach marks instead of deleting.
     */
    @Test
    void reattachingRevivesTheRowInsteadOfInsertingASecondOne() {
        StoredFile file = persistedFile("file-1", persistedUser("master-1"), FileType.Private);
        TableFile detached = new TableFile("table-1", "file-1", TableFileType.Preparation, false);
        detached.setStatus(TableFileStatus.Deleted);
        detached.setDeletedAt(LocalDateTime.now());

        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(fileService.requireAttachable("file-1", "master-1")).thenReturn(file);
        when(tableFileRepository.findById(new TableFileId("table-1", "file-1"))).thenReturn(Optional.of(detached));
        when(tableFileRepository.save(any(TableFile.class))).thenAnswer(returnsArgument());

        tableFileService()
                .attach("table-1", new LinkTableFileRequest("file-1", TableFileType.Session, true), "master-1");

        assertThat(detached.getStatus()).isEqualTo(TableFileStatus.Current);
        assertThat(detached.getDeletedAt()).isNull();
        assertThat(detached.getTableFileType()).isEqualTo(TableFileType.Session);
        assertThat(detached.isPrivate()).isTrue();
    }

    // ---------------------------------------------------------------- detaching

    /** The heart of #79: removing the community's sheet from one table cannot remove it from anywhere else. */
    @Test
    void detachingMarksTheLinkAndLeavesTheFileUntouched() {
        StoredFile file = persistedFile("file-1", persistedUser("master-1"), FileType.Private);
        TableFile link = new TableFile("table-1", "file-1", TableFileType.Preparation, false);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(tableFileRepository.findById(new TableFileId("table-1", "file-1"))).thenReturn(Optional.of(link));

        tableFileService().detach("table-1", "file-1", "master-1");

        assertThat(link.getStatus()).isEqualTo(TableFileStatus.Deleted);
        assertThat(link.getDeletedAt()).isNotNull();
        assertThat(file.getStatus()).isEqualTo(FileStatus.Current);
        verify(fileRepository, never()).save(any(StoredFile.class));
        verify(fileRepository, never()).delete(any(StoredFile.class));
    }

    @Test
    void detachingSomethingThatIsNotAttachedIsNotFound() {
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(tableFileRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableFileService().detach("table-1", "file-1", "master-1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- what each side sees

    /** isPrivate is about the link, so the same file reads differently on two tables. */
    @Test
    void updatingSharesOrHidesTheFileOnThisTableOnly() {
        StoredFile file = persistedFile("file-1", persistedUser("master-1"), FileType.Private);
        TableFile link = new TableFile("table-1", "file-1", TableFileType.Preparation, false);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(tableFileRepository.findById(new TableFileId("table-1", "file-1"))).thenReturn(Optional.of(link));
        when(fileRepository.findByIdAndStatus("file-1", FileStatus.Current)).thenReturn(Optional.of(file));

        TableFileResponse response = tableFileService()
                .update("table-1", "file-1", new UpdateTableFileRequest(TableFileType.Session, true), "master-1");

        assertThat(link.isPrivate()).isTrue();
        assertThat(link.getTableFileType()).isEqualTo(TableFileType.Session);
        assertThat(response.isPrivate()).isTrue();
        assertThat(file.getFileType()).isEqualTo(FileType.Private);
    }

    /** A private attachment is absent from a player's view, never listed and locked. */
    @Test
    void whatTheTableSharesLeavesOutThePrivateAttachments() {
        User master = persistedUser("master-1");
        StoredFile shared = persistedFile("file-1", master, FileType.Private);
        StoredFile secret = persistedFile("file-2", master, FileType.Private);
        when(tableFileRepository.findById_GameTableIdAndStatus("table-1", TableFileStatus.Current))
                .thenReturn(List.of(
                        attached("table-1", "file-1", false),
                        attached("table-1", "file-2", true)));
        when(fileRepository.findAllById(List.of("file-1"))).thenReturn(List.of(shared));

        List<SharedFileResponse> files = tableFileService().sharedFilesOf("table-1");

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().fileId()).isEqualTo("file-1");
        assertThat(secret.getId()).isEqualTo("file-2");
    }

    /**
     * A file its owner deleted stops showing on the tables that had it, without any link having to be
     * found and rewritten (#25). The link stays as the record that it was once there.
     */
    @Test
    void aFileItsOwnerDeletedDisappearsFromTheTablesThatShowedIt() {
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(tableFileRepository.findById_GameTableIdAndStatus("table-1", TableFileStatus.Current))
                .thenReturn(List.of(attached("table-1", "file-1", false)));
        when(fileRepository.findAllById(List.of("file-1"))).thenReturn(List.of());

        assertThat(tableFileService().listForTable("table-1", "master-1")).isEmpty();
    }

    @Test
    void listingShowsThePrivateAttachmentsToWhoeverRunsTheTable() {
        StoredFile file = persistedFile("file-1", persistedUser("master-1"), FileType.Private);
        when(masterService.isMasterOf("table-1", "master-1")).thenReturn(true);
        when(tableFileRepository.findById_GameTableIdAndStatus("table-1", TableFileStatus.Current))
                .thenReturn(List.of(attached("table-1", "file-1", true)));
        when(fileRepository.findAllById(List.of("file-1"))).thenReturn(List.of(file));

        List<TableFileResponse> files = tableFileService().listForTable("table-1", "master-1");

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().isPrivate()).isTrue();
    }

    // ---------------------------------------------------------------- fixtures

    private static org.mockito.stubbing.Answer<TableFile> returnsArgument() {
        return invocation -> invocation.getArgument(0);
    }

    private static TableFile attached(String tableId, String fileId, boolean isPrivate) {
        TableFile link = new TableFile(tableId, fileId, TableFileType.Preparation, isPrivate);
        ReflectionTestUtils.setField(link, "createdAt", LocalDateTime.now());
        return link;
    }

    private static User persistedUser(String id) {
        User user = new User("discord-" + id, id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static StoredFile persistedFile(String id, User owner, FileType type) {
        StoredFile file = new StoredFile("mapa.png", "key-" + id, "hash-" + id, "image/png", 10, type, owner);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "createdAt", LocalDateTime.now());
        return file;
    }
}
