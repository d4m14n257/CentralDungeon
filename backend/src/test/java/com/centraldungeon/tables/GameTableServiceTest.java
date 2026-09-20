package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.catalogs.TableCatalogService;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.tables.dto.AddMasterRequest;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.files.TableFileService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AssignMastersRequest;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.tables.dto.ChangeTableStatusRequest;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableHistoryResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.tables.dto.UpdateGameTableRequest;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GameTableServiceTest {

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private TableTypeRepository tableTypeRepository;

    @Mock
    private TableRegistrationRepository tableRegistrationRepository;

    @Mock
    private TableStatusChangeRepository tableStatusChangeRepository;

    @Mock
    private MasterService masterService;

    @Mock
    private GameTableMapper gameTableMapper;

    @Mock
    private UserService userService;

    @Mock
    private TableScheduleService tableScheduleService;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    @Mock
    private TableCatalogService tableCatalogService;

    @Mock
    private TableSessionService tableSessionService;

    @Mock
    private TableFileService tableFileService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private GameTableSearchResolver gameTableSearchResolver;

    private GameTableService gameTableService;

    @BeforeEach
    void setUp() {
        gameTableService = new GameTableService(
                gameTableRepository, tableTypeRepository, tableRegistrationRepository, tableStatusChangeRepository, masterService,
                gameTableMapper, userService, tableScheduleService, scheduleConflictService, tableCatalogService,
                tableSessionService, tableFileService, new RichTextSanitizer(), notificationService,
                gameTableSearchResolver,
                // The real one over the same mocked repositories: every read of a concrete table in
                // these tests goes through it, and a mock would hide whether it does (#25, #29).
                new TableVisibilityService(gameTableRepository, tableRegistrationRepository));
    }

    @Test
    void createsATableAndItsPrimaryMaster() {
        User creator = persistedUser("creator-1");
        when(userService.getById("creator-1")).thenReturn(creator);
        when(gameTableRepository.save(any(GameTable.class))).thenAnswer(invocation -> {
            GameTable table = invocation.getArgument(0);
            ReflectionTestUtils.setField(table, "id", "table-1");
            return table;
        });
        when(masterService.findByGameTable("table-1")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-1", "Test", null, null, null, null, null, "Preparation", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        CreateGameTableRequest request = runnableRequest(null);
        GameTableDetailResponse response = gameTableService.create(request, "creator-1");

        assertThat(response.id()).isEqualTo("table-1");
        org.mockito.Mockito.verify(masterService).createPrimary(any(GameTable.class), org.mockito.ArgumentMatchers.eq(creator));
    }

    @Test
    void rejectsCreationWithAnUnknownTableType() {
        when(userService.getById("creator-1")).thenReturn(persistedUser("creator-1"));
        when(tableTypeRepository.findById("missing-type")).thenReturn(Optional.empty());

        CreateGameTableRequest request = runnableRequest("missing-type");

        assertThatThrownBy(() -> gameTableService.create(request, "creator-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("una mesa sin sistema no se crea: hay que decir qué se juega (#226)")
    void rejectsCreationWithoutASystem() {
        CreateGameTableRequest request = new CreateGameTableRequest(
                "Test", null, null, null, null, List.of(), List.of("tag-1"), List.of("platform-1"), null, null, null,
                List.of(new TableScheduleEntry(Weekday.Friday, LocalTime.of(20, 0), LocalTime.of(3, 0))));

        assertThatThrownBy(() -> gameTableService.create(request, "creator-1"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getErrorCode())
                .isEqualTo("TABLE_NEEDS_SYSTEM");
        // Nothing is written when the draft does not hold together: the check runs before the save.
        verify(gameTableRepository, never()).save(any(GameTable.class));
    }

    @Test
    @DisplayName("una mesa sin plataforma no se crea: hay que decir dónde se juega (#226)")
    void rejectsCreationWithoutAPlatform() {
        CreateGameTableRequest request = new CreateGameTableRequest(
                "Test", null, null, null, null, List.of("system-1"), List.of("tag-1"), null, null, null, null,
                List.of(new TableScheduleEntry(Weekday.Friday, LocalTime.of(20, 0), LocalTime.of(3, 0))));

        assertThatThrownBy(() -> gameTableService.create(request, "creator-1"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getErrorCode())
                .isEqualTo("TABLE_NEEDS_PLATFORM");
        verify(gameTableRepository, never()).save(any(GameTable.class));
    }

    @Test
    @DisplayName("una mesa sin agenda no se crea: hay que decir cuándo se juega (#226, lo que #196 dejó anotado)")
    void rejectsCreationWithoutASchedule() {
        CreateGameTableRequest request = new CreateGameTableRequest(
                "Test", null, null, null, null, List.of("system-1"), List.of("tag-1"), List.of("platform-1"), null, null, null, List.of());

        assertThatThrownBy(() -> gameTableService.create(request, "creator-1"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getErrorCode())
                .isEqualTo("TABLE_NEEDS_SCHEDULE");
        verify(gameTableRepository, never()).save(any(GameTable.class));
    }

    @Test
    @DisplayName("una mesa sin tags no se crea: hay que poder encontrarla por tema (#229, corrige #226)")
    void rejectsCreationWithoutATag() {
        CreateGameTableRequest request = new CreateGameTableRequest(
                "Test", null, null, null, null, List.of("system-1"), List.of(), List.of("platform-1"), null, null, null,
                List.of(new TableScheduleEntry(Weekday.Friday, LocalTime.of(20, 0), LocalTime.of(3, 0))));

        assertThatThrownBy(() -> gameTableService.create(request, "creator-1"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getErrorCode())
                .isEqualTo("TABLE_NEEDS_TAG");
        verify(gameTableRepository, never()).save(any(GameTable.class));
    }

    /** A draft that carries the three things #226 requires, so a test about something else gets past them. */
    private static CreateGameTableRequest runnableRequest(@Nullable String tableTypeId) {
        return new CreateGameTableRequest(
                "Test", null, null, null, tableTypeId, List.of("system-1"), List.of("tag-1"), List.of("platform-1"), null, null, null,
                List.of(new TableScheduleEntry(Weekday.Friday, LocalTime.of(20, 0), LocalTime.of(3, 0))));
    }

    @Test
    void cannotApproveATableThatIsNotInPreparation() {
        GameTable table = persistedTable("table-3", GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-3")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> gameTableService.approve("table-3", "admin-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void approveTransitionsPreparationToOpenedAndRecordsHistory() {
        GameTable table = persistedTable("table-4", GameTableStatus.Preparation);
        User admin = persistedUser("admin-1");
        when(gameTableRepository.findByIdForUpdate("table-4")).thenReturn(Optional.of(table));
        when(userService.getById("admin-1")).thenReturn(admin);
        when(masterService.findByGameTable("table-4")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-4", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.approve("table-4", "admin-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Opened);
        org.mockito.Mockito.verify(tableStatusChangeRepository).save(any(TableStatusChange.class));
    }

    /**
     * The tray's rule, case 1 of 3: <b>a table nobody claimed is approved</b>.
     *
     * <p>It was the other way round for a while and it broke the real application: with «resolver exige
     * tenerlo reservado», every resolution from a screen with no claim button answered 409 over a
     * reservation the screen could not offer. What #100 buys is «si lo toma uno, baja para todos», so an
     * item nobody owns is not the situation to prevent: resolving it is an implicit claim. This test is
     * the one that fails if somebody tightens the rule again.
     */
    @Test
    void approvesATableNobodyClaimed() {
        GameTable table = persistedTable("table-free", GameTableStatus.Preparation);
        when(gameTableRepository.findByIdForUpdate("table-free")).thenReturn(Optional.of(table));
        when(userService.getById("admin-1")).thenReturn(persistedUser("admin-1"));
        when(masterService.findByGameTable("table-free")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-free", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.approve("table-free", "admin-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Opened);
    }

    /** Case 2 of 3: the table the actor already holds, which is the path down from the tray. */
    @Test
    void approvesATableTheActorAlreadyClaimed() {
        GameTable table = persistedTable("table-mine", GameTableStatus.Preparation);
        User admin = persistedUser("admin-1");
        claimedBy(table, admin);
        when(gameTableRepository.findByIdForUpdate("table-mine")).thenReturn(Optional.of(table));
        when(userService.getById("admin-1")).thenReturn(admin);
        when(masterService.findByGameTable("table-mine")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-mine", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.approve("table-mine", "admin-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Opened);
    }

    /**
     * Case 3 of 3, and the only one refused: the table <b>another</b> admin holds. A stale link, a second
     * tab, a tray that has not refreshed - the three ways of taking work off a colleague's desk.
     */
    @Test
    void cannotApproveATableAnotherAdminClaimed() {
        GameTable table = persistedTable("table-taken", GameTableStatus.Preparation);
        claimedBy(table, persistedUser("admin-2"));
        when(gameTableRepository.findByIdForUpdate("table-taken")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> gameTableService.approve("table-taken", "admin-1"))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        // And it left nothing half done: not the transition, not the history row, not the bell.
        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Preparation);
        verify(tableStatusChangeRepository, never()).save(any(TableStatusChange.class));
        verify(notificationService, never()).notifyReviewOutcome(any(), any(), any());
    }

    /** Requesting changes is the other half of the same act, and the rule holds for both alike. */
    @Test
    void cannotRequestChangesOnATableAnotherAdminClaimed() {
        GameTable table = persistedTable("table-taken-2", GameTableStatus.Preparation);
        claimedBy(table, persistedUser("admin-2"));
        when(gameTableRepository.findByIdForUpdate("table-taken-2")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> gameTableService.requestChanges(
                        "table-taken-2", "admin-1", new ChangeTableStatusRequest("Falta la agenda")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Preparation);
    }

    /**
     * The order of the two 409s: if the table is not even under review, that is what has to be said.
     * Naming the colleague who claimed a table that no longer needs reviewing would send the reader to
     * ask them about nothing.
     */
    @Test
    void aTableOutOfReviewSaysSoBeforeNamingWhoeverHoldsIt() {
        GameTable table = persistedTable("table-opened", GameTableStatus.Opened);
        claimedBy(table, persistedUser("admin-2"));
        when(gameTableRepository.findByIdForUpdate("table-opened")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> gameTableService.approve("table-opened", "admin-1"))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isNotEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);
    }

    @Test
    void onlyThePrimaryMasterCanStartATable() {
        GameTable table = persistedTable("table-2", GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-2")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-2", "secondary-1")).thenReturn(false);

        assertThatThrownBy(() -> gameTableService.start("table-2", "secondary-1")).isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void onlyThePrimaryMasterCanFinishATable() {
        GameTable table = persistedTable("table-2b", GameTableStatus.InProgress);
        when(gameTableRepository.findByIdForUpdate("table-2b")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-2b", "secondary-1")).thenReturn(false);

        assertThatThrownBy(() -> gameTableService.finish("table-2b", "secondary-1")).isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void assignInitialMastersOpensAnUnassignedTable() {
        GameTable table = persistedTable("table-unassigned", GameTableStatus.Unassigned);
        User admin = persistedUser("admin-1");
        when(gameTableRepository.findByIdForUpdate("table-unassigned")).thenReturn(Optional.of(table));
        when(userService.getById("admin-1")).thenReturn(admin);
        when(masterService.findByGameTable("table-unassigned")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-unassigned", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.assignInitialMasters(
                "table-unassigned", new AssignMastersRequest("primary-1", List.of()), "admin-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Opened);
        org.mockito.Mockito.verify(masterService).assignInitialMasters(table, "primary-1", List.of());
    }

    /**
     * #244: the one assignment nobody asked for. An admin creates the table and hands it over, so
     * without the bell the new master finds out by stumbling on a table they had never seen.
     */
    @Test
    void tellsEveryMasterItJustAssignedThatTheyNowRunTheTable() {
        GameTable table = persistedTable("table-handed-over", GameTableStatus.Unassigned);
        when(gameTableRepository.findByIdForUpdate("table-handed-over")).thenReturn(Optional.of(table));
        when(userService.getById("admin-1")).thenReturn(persistedUser("admin-1"));
        when(masterService.findByGameTable("table-handed-over")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-handed-over", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.assignInitialMasters(
                "table-handed-over", new AssignMastersRequest("primary-1", List.of("second-1")), "admin-1");

        // The co-master too: being added to somebody else's table is news for whoever is added (#135).
        org.mockito.Mockito.verify(notificationService).notifyMasterAssigned("primary-1", table);
        org.mockito.Mockito.verify(notificationService).notifyMasterAssigned("second-1", table);
    }

    /**
     * #244: review is a wait whose other side the master cannot see. The table sits there and nothing
     * on their screen changes until an admin acts, so the bell is the only thing that says it came out.
     */
    @Test
    void tellsTheMastersWhenTheirTableComesOutOfReviewApproved() {
        GameTable table = persistedTable("table-approved", GameTableStatus.Preparation);
        when(gameTableRepository.findByIdForUpdate("table-approved")).thenReturn(Optional.of(table));
        when(masterService.findByGameTable("table-approved"))
                .thenReturn(List.of(new Master(table, persistedUser("master-1"), MasterType.Primary)));
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-approved", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.approve("table-approved", "admin-1");

        org.mockito.Mockito.verify(notificationService)
                .notifyReviewOutcome("master-1", table, NotificationType.TableApproved);
    }

    @Test
    void tellsTheMastersWhenTheirTableIsSentBackForChanges() {
        GameTable table = persistedTable("table-bounced", GameTableStatus.Preparation);
        when(gameTableRepository.findByIdForUpdate("table-bounced")).thenReturn(Optional.of(table));
        when(masterService.findByGameTable("table-bounced"))
                .thenReturn(List.of(new Master(table, persistedUser("master-1"), MasterType.Primary)));
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-bounced", "Test", null, null, null, null, null, "ChangesRequested", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.requestChanges("table-bounced", "admin-1", new ChangeTableStatusRequest("Falta la agenda"));

        // The reason is not in the notification: it is mandatory on the transition and kept in the
        // status history, which is where the master reads it whole (#197).
        org.mockito.Mockito.verify(notificationService)
                .notifyReviewOutcome("master-1", table, NotificationType.TableChangesRequested);
    }

    /** Being promoted is not news: they already ran the table and are watching the screen that did it. */
    @Test
    void doesNotAnnounceAPromotionOfSomebodyWhoAlreadyRanTheTable() {
        GameTable table = persistedTable("table-promote", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-promote")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-promote", "second-1")).thenReturn(true);
        when(masterService.findByGameTable("table-promote")).thenReturn(List.of());

        gameTableService.addOrPromoteMaster("table-promote", "primary-1", new AddMasterRequest("second-1", MasterType.Primary));

        org.mockito.Mockito.verify(notificationService, org.mockito.Mockito.never())
                .notifyMasterAssigned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cannotAssignMastersToATableThatIsNotUnassigned() {
        GameTable table = persistedTable("table-5b", GameTableStatus.Preparation);
        when(gameTableRepository.findByIdForUpdate("table-5b")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> gameTableService.assignInitialMasters(
                        "table-5b", new AssignMastersRequest("primary-1", List.of()), "admin-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCanCancelATableEvenWithoutBeingItsMaster() {
        GameTable table = persistedTable("table-6b", GameTableStatus.Opened);
        User admin = persistedUser("admin-1");
        when(gameTableRepository.findByIdForUpdate("table-6b")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-6b", "admin-1")).thenReturn(false);
        when(userService.loadAuthSnapshot("admin-1")).thenReturn(new UserAuthSnapshot("admin-1", UserStatus.Allowed, Set.of("Admin")));
        when(userService.getById("admin-1")).thenReturn(admin);
        when(masterService.findByGameTable("table-6b")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-6b", "Test", null, null, null, null, null, "Canceled", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.cancel("table-6b", "admin-1", new ChangeTableStatusRequest("No hay suficientes jugadores"));

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Canceled);
    }

    @Test
    void someoneWhoIsNeitherPrimaryNorAdminCannotCancel() {
        GameTable table = persistedTable("table-6c", GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-6c")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-6c", "secondary-1")).thenReturn(false);
        when(userService.loadAuthSnapshot("secondary-1")).thenReturn(new UserAuthSnapshot("secondary-1", UserStatus.Allowed, Set.of("Master")));

        assertThatThrownBy(() -> gameTableService.cancel("table-6c", "secondary-1", new ChangeTableStatusRequest("motivo")))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void cannotCancelATableThatIsAlreadyFinished() {
        GameTable table = persistedTable("table-6d", GameTableStatus.Finished);
        when(gameTableRepository.findByIdForUpdate("table-6d")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-6d", "primary-1")).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.cancel("table-6d", "primary-1", new ChangeTableStatusRequest("motivo")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletesADraftNobodyEverSaw() {
        GameTable table = persistedTable("table-7a", GameTableStatus.Preparation);
        User primary = persistedUser("primary-1");
        when(gameTableRepository.findByIdForUpdate("table-7a")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-7a", "primary-1")).thenReturn(true);
        when(tableRegistrationRepository.existsByGameTable_IdAndStatusIn(eq("table-7a"), any())).thenReturn(false);
        when(tableRegistrationRepository.findByGameTable_Id("table-7a")).thenReturn(List.of());
        when(userService.getById("primary-1")).thenReturn(primary);

        gameTableService.delete("table-7a", "primary-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Deleted);
        assertThat(table.getDeletedAt()).isNotNull();
        verify(masterService).softDeleteAllOfTable(eq("table-7a"), any());
    }

    /** The line #175 draws: what was once public is cancelled, never deleted. */
    @Test
    void cannotDeleteATableThatWasAlreadyPublic() {
        GameTable table = persistedTable("table-7b", GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-7b")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-7b", "primary-1")).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.delete("table-7b", "primary-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotDeleteATableThatSomeoneAlreadyAppliedTo() {
        GameTable table = persistedTable("table-7c", GameTableStatus.ChangesRequested);
        when(gameTableRepository.findByIdForUpdate("table-7c")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-7c", "primary-1")).thenReturn(true);
        when(tableRegistrationRepository.existsByGameTable_IdAndStatusIn(eq("table-7c"), any())).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.delete("table-7c", "primary-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void someoneWhoIsNeitherPrimaryNorAdminCannotDelete() {
        GameTable table = persistedTable("table-7d", GameTableStatus.Preparation);
        when(gameTableRepository.findByIdForUpdate("table-7d")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-7d", "secondary-1")).thenReturn(false);
        when(userService.loadAuthSnapshot("secondary-1")).thenReturn(new UserAuthSnapshot("secondary-1", UserStatus.Allowed, Set.of("Master")));

        assertThatThrownBy(() -> gameTableService.delete("table-7d", "secondary-1")).isInstanceOf(ForbiddenActionException.class);
    }

    /** A deleted table exists for nobody: 404 and not 403 (#25). */
    @Test
    void aDeletedTableIsNotFound() {
        GameTable table = persistedTable("table-7e", GameTableStatus.Deleted);
        when(gameTableRepository.findById("table-7e")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> gameTableService.getDetail("table-7e", "player-1")).isInstanceOf(NotFoundException.class);
    }

    // ---------------------- group B: the reads that close themselves when Player -> Blocked happens

    /**
     * <b>A group B regression test.</b> {@code /my/tables} was not touched by F3.4, and the
     * <b>only</b> thing protecting it is that it filters on {@code status = Player}: moving somebody to
     * {@code Blocked} takes them out of that filter and the table leaves their listing. This test is
     * what would speak up if that filter were ever loosened to «any live registration».
     *
     * <p>And {@code PauseRequested} does count as live: F3.4 added it to {@link
     * GameTableService#LIVE_MINE_STATUSES} when it gave the state a producer - a table whose master has
     * asked for a pause is still being played, and making it vanish while an admin thinks it over would
     * be worse than the orphan the slice came to close.
     */
    @Test
    void myTablesFilterByPlayerAndByLiveStatusIncludingPauseRequested() {
        ArgumentCaptor<List<GameTableStatus>> statuses = ArgumentCaptor.captor();
        when(tableRegistrationRepository.findByUser_IdAndStatusAndGameTable_StatusIn(
                        eq("player-1"), eq(TableRegistrationStatus.Player), statuses.capture(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        gameTableService.listMine("player-1", PageRequest.of(0, 20));

        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(
                        GameTableStatus.Opened, GameTableStatus.InProgress,
                        GameTableStatus.PauseRequested, GameTableStatus.Pause);
    }

    /** The same for the history: {@code Player} and nothing else is what closes it. */
    @Test
    void myHistoryAlsoFiltersByPlayer() {
        when(tableRegistrationRepository.findByUser_IdAndStatusAndGameTable_StatusIn(
                        eq("player-1"), eq(TableRegistrationStatus.Player), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(tableSessionService.summarizeByTables(any(), eq("player-1"))).thenReturn(java.util.Map.of());

        assertThat(gameTableService.listMineHistory("player-1", PageRequest.of(0, 20)).content()).isEmpty();

        verify(tableRegistrationRepository)
                .findByUser_IdAndStatusAndGameTable_StatusIn(
                        eq("player-1"), eq(TableRegistrationStatus.Player), any(), any());
    }

    // ------------------------------------- the veto in the detail (read paths 2, 3 and 4 of F3.4)

    /**
     * <b>Read path 2.</b> The detail of a table the actor is vetoed on answers {@code 404},
     * <b>never</b> {@code 403} (#29). A 403 confirms what the 404 denies.
     */
    @Test
    void theDetailOfAVetoedTableIs404AndNeverA403() {
        GameTable table = persistedTable("table-veto-1", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-veto-1")).thenReturn(Optional.of(table));
        when(tableRegistrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-veto-1", "vetado", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(true);

        assertThatThrownBy(() -> gameTableService.getDetail("table-veto-1", "vetado"))
                .isInstanceOf(NotFoundException.class)
                .isNotInstanceOf(ForbiddenActionException.class);
    }

    /**
     * <b>Read paths 3 and 4.</b> The public sessions and the shared files travel <em>inside</em> the
     * detail, so they inherit its answer - and what is pinned here is that the inheritance is real:
     * with the reader vetoed, those two reads <b>do not happen</b>. If they ever stopped travelling
     * inside the detail, two more checks would be needed, and this test is what would say so.
     */
    @Test
    void theSessionsAndFilesAreNotEvenReadWhenTheReaderIsVetoed() {
        GameTable table = persistedTable("table-veto-2", GameTableStatus.InProgress);
        when(gameTableRepository.findById("table-veto-2")).thenReturn(Optional.of(table));
        when(tableRegistrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-veto-2", "vetado", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(true);

        assertThatThrownBy(() -> gameTableService.getDetail("table-veto-2", "vetado"))
                .isInstanceOf(NotFoundException.class);

        verify(tableSessionService, never()).findPublicSessions(any());
        verify(tableFileService, never()).sharedFilesOf(anyString());
    }

    /** And somebody who is not vetoed still sees the whole table, sessions and files included. */
    @Test
    void somebodyNotVetoedStillSeesTheWholeDetail() {
        GameTable table = persistedTable("table-veto-3", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-veto-3")).thenReturn(Optional.of(table));
        when(tableRegistrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-veto-3", "player-1", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(false);
        when(masterService.findByGameTable("table-veto-3")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-veto-3", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        assertThat(gameTableService.getDetail("table-veto-3", "player-1")).isNotNull();

        verify(tableSessionService).findPublicSessions(table);
        verify(tableFileService).sharedFilesOf("table-veto-3");
    }

    // ------------------------------------------------------------- the requested pause (#32)

    /**
     * {@code PauseRequested} stops being the orphan F1.7 surveyed: this is its producer. <b>Any
     * master</b> may ask and not only the {@code Primary} - asking is not deciding, and a co-master who
     * cannot run next week's session is exactly the person with a reason to ask.
     */
    @Test
    void aMasterAsksForThePauseAndTheTableLandsInPauseRequested() {
        GameTable table = persistedTable("table-pause-1", GameTableStatus.InProgress);
        when(gameTableRepository.findByIdForUpdate("table-pause-1")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-pause-1", "secondary-1")).thenReturn(true);
        when(userService.getById("secondary-1")).thenReturn(persistedUser("secondary-1"));
        when(masterService.findByGameTable("table-pause-1")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-pause-1", "Test", null, null, null, null, null, "PauseRequested", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.markPauseRequested("table-pause-1", "secondary-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.PauseRequested);
        verify(tableStatusChangeRepository).save(any(TableStatusChange.class));
    }

    /** Somebody who does not run the table cannot ask for anything about it (#17, #121, #135). */
    @Test
    void anOutsiderCannotAskForThePause() {
        GameTable table = persistedTable("table-pause-2", GameTableStatus.InProgress);
        when(gameTableRepository.findByIdForUpdate("table-pause-2")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-pause-2", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> gameTableService.markPauseRequested("table-pause-2", "stranger"))
                .isInstanceOf(ForbiddenActionException.class);
        assertThat(table.getStatus()).isEqualTo(GameTableStatus.InProgress);
    }

    /** Asking twice is a 409 with a code of its own: it is about the table, not about who asked. */
    @Test
    void askingToPauseATableAlreadyWaitingIs409WithItsOwnCode() {
        GameTable table = persistedTable("table-pause-3", GameTableStatus.PauseRequested);
        when(gameTableRepository.findByIdForUpdate("table-pause-3")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-pause-3", "master-1")).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.markPauseRequested("table-pause-3", "master-1"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConflictException.PAUSE_ALREADY_REQUESTED);
    }

    /**
     * Approving it takes the table to {@code Pause} and <b>the resolution note is the
     * justification</b> (#32, modelo-datos.md:835): the admin already wrote why, and asking them for a
     * second reason would leave two answers to one question.
     */
    @Test
    void approvingThePauseAppliesItWithTheResolutionNoteAsTheJustification() {
        GameTable table = persistedTable("table-pause-4", GameTableStatus.PauseRequested);
        when(gameTableRepository.findByIdForUpdate("table-pause-4")).thenReturn(Optional.of(table));
        when(userService.getById("admin-1")).thenReturn(persistedUser("admin-1"));
        when(masterService.findByGameTable("table-pause-4")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-pause-4", "Test", null, null, null, null, null, "Pause", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.applyApprovedPause("table-pause-4", "admin-1", "The master is moving house");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Pause);
        ArgumentCaptor<TableStatusChange> change = ArgumentCaptor.forClass(TableStatusChange.class);
        verify(tableStatusChangeRepository).save(change.capture());
        assertThat(change.getValue().getJustification()).isEqualTo("The master is moving house");
        assertThat(change.getValue().getFromStatus()).isEqualTo(GameTableStatus.PauseRequested);
    }

    /**
     * <b>And rejecting it has an effect</b>, which is what is new: until F3.4 no rejection did
     * anything. Asking for the pause already moved the table, so saying no has to move it back -
     * otherwise it is stranded in {@code PauseRequested} with no door out.
     */
    @Test
    void rejectingThePausePutsTheTableBackInProgress() {
        GameTable table = persistedTable("table-pause-5", GameTableStatus.PauseRequested);
        when(gameTableRepository.findByIdForUpdate("table-pause-5")).thenReturn(Optional.of(table));
        when(userService.getById("admin-1")).thenReturn(persistedUser("admin-1"));
        when(masterService.findByGameTable("table-pause-5")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-pause-5", "Test", null, null, null, null, null, "InProgress", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.revertRequestedPause("table-pause-5", "admin-1", "The table has only just started");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.InProgress);
        ArgumentCaptor<TableStatusChange> change = ArgumentCaptor.forClass(TableStatusChange.class);
        verify(tableStatusChangeRepository).save(change.capture());
        assertThat(change.getValue().getToStatus()).isEqualTo(GameTableStatus.InProgress);
        assertThat(change.getValue().getJustification()).isEqualTo("The table has only just started");
    }

    /**
     * The explorer's visibility rules - public statuses only, and never a table the actor runs
     * (#154) - moved into {@code GameTableSearchSpecification} when the search box arrived, so what a
     * unit test can still assert here is the mapping. The predicate itself is covered by
     * {@code GameTableSearchIT} against real MySQL, which is the only place a Criteria predicate can
     * be proven to mean what it says.
     */
    @Test
    void listMapsEveryTableTheExplorerQueryReturns() {
        GameTable table = persistedTable("table-4b", GameTableStatus.Opened);
        User primaryUser = persistedUser("someone-else");
        Master primary = new Master(table, primaryUser, MasterType.Primary);
        Pageable pageable = PageRequest.of(0, 20);
        when(gameTableSearchResolver.resolveCatalogTerms(any())).thenReturn(Map.of());
        when(gameTableRepository.findAll(ArgumentMatchers.<Specification<GameTable>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(table)));
        when(masterService.findByGameTable("table-4b")).thenReturn(List.of(primary));
        MasterSummaryResponse primarySummary = new MasterSummaryResponse("someone-else", "Someone Else", 8000, "Primary");
        when(gameTableMapper.toMasterSummary(primary)).thenReturn(primarySummary);
        GameTableSummaryResponse summary =
                new GameTableSummaryResponse("table-4b", "Test", "Opened", null, null, null, 0, List.of(), false, primarySummary);
        when(gameTableMapper.toSummary(table, 0, primarySummary, List.of(), false)).thenReturn(summary);

        var result = gameTableService.list(null, pageable, "player-1");

        assertThat(result.content()).containsExactly(summary);
    }

    /**
     * The resolution of #246 happens before the query is built, and this is what says so: the service
     * hands the resolver a parsed query, not the raw string, and the criterion arrives as a term of
     * the field it was written with.
     */
    @Test
    void listResolvesCatalogCriteriaBeforeQueryingTheDatabase() {
        Pageable pageable = PageRequest.of(0, 20);
        when(gameTableSearchResolver.resolveCatalogTerms(any())).thenReturn(Map.of());
        when(gameTableRepository.findAll(ArgumentMatchers.<Specification<GameTable>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        gameTableService.list("/table_tag horror", pageable, "player-1");

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(gameTableSearchResolver).resolveCatalogTerms(captor.capture());
        assertThat(captor.getValue().terms()).singleElement().satisfies(term -> {
            assertThat(term.field()).isEqualTo("table_tag");
            assertThat(term.values()).containsExactly("horror");
        });
    }

    /** An empty box is not a criterion: the query the resolver gets has nothing in it to resolve. */
    @Test
    void listWithAnEmptySearchBoxResolvesNothing() {
        Pageable pageable = PageRequest.of(0, 20);
        when(gameTableSearchResolver.resolveCatalogTerms(any())).thenReturn(Map.of());
        when(gameTableRepository.findAll(ArgumentMatchers.<Specification<GameTable>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        gameTableService.list("   ", pageable, "player-1");

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(gameTableSearchResolver).resolveCatalogTerms(captor.capture());
        assertThat(captor.getValue().isEmpty()).isTrue();
    }

    @Test
    void listManagedMapsEveryTableTheRepositoryReturnsForThatMaster() {
        GameTable table = persistedTable("table-5", GameTableStatus.Preparation);
        User primaryUser = persistedUser("primary-1");
        Master primary = new Master(table, primaryUser, MasterType.Primary);
        Pageable pageable = PageRequest.of(0, 20);
        when(gameTableRepository.findByMasterUserId("master-1", pageable)).thenReturn(new PageImpl<>(List.of(table)));
        when(masterService.findByGameTable("table-5")).thenReturn(List.of(primary));
        MasterSummaryResponse primarySummary = new MasterSummaryResponse("primary-1", "Primary One", 8000, "Primary");
        when(gameTableMapper.toMasterSummary(primary)).thenReturn(primarySummary);
        GameTableSummaryResponse summary =
                new GameTableSummaryResponse("table-5", "Test", "Preparation", null, null, null, 0, List.of(), false, primarySummary);
        when(gameTableMapper.toSummary(table, 0, primarySummary, List.of(), false)).thenReturn(summary);

        var result = gameTableService.listManaged("master-1", pageable);

        assertThat(result.content()).containsExactly(summary);
    }

    // ---------------------------------------------------------------- /my/tables and its history (#133a)

    /**
     * #133a: the registration alone never says whether a run is over - it stays {@code Player}
     * forever once accepted. What decides "still mine to see here" is the table's own status, and
     * this fakes the repository just enough to prove the service asks for the right ones: a table
     * that ended is filtered out of /my/tables even though the registration on it never changed.
     */
    @Test
    @DisplayName("/mine no devuelve una mesa Finished ni una Canceled (#133a)")
    void listMineExcludesFinishedAndCanceledTables() {
        GameTable opened = persistedTable("table-live", GameTableStatus.Opened);
        GameTable finished = persistedTable("table-finished", GameTableStatus.Finished);
        GameTable canceled = persistedTable("table-canceled", GameTableStatus.Canceled);
        Pageable pageable = PageRequest.of(0, 20);
        stubStatusFilteredRegistrations(pageable, opened, finished, canceled);
        stubPrimarylessSummary(opened);

        var result = gameTableService.listMine("player-1", pageable);

        assertThat(result.content()).extracting(GameTableSummaryResponse::id).containsExactly("table-live");
    }

    /** #32: a paused table is frozen, not over - it stays among "mine" and never moves to the history. */
    @Test
    @DisplayName("/mine sí devuelve una mesa en Pause: está viva, solo congelada (#32)")
    void listMineIncludesPausedTables() {
        GameTable paused = persistedTable("table-paused", GameTableStatus.Pause);
        Pageable pageable = PageRequest.of(0, 20);
        stubStatusFilteredRegistrations(pageable, paused);
        stubPrimarylessSummary(paused);

        var result = gameTableService.listMine("player-1", pageable);

        assertThat(result.content()).extracting(GameTableSummaryResponse::id).containsExactly("table-paused");
    }

    /** The mirror of the two tests above: the history shows exactly the two endings, nothing else. */
    @Test
    @DisplayName("el historial devuelve Finished y Canceled, y nada más (#133a)")
    void listMineHistoryReturnsOnlyTheTwoEndings() {
        GameTable opened = persistedTable("table-still-live", GameTableStatus.Opened);
        GameTable finished = persistedTable("table-done", GameTableStatus.Finished);
        GameTable canceled = persistedTable("table-called-off", GameTableStatus.Canceled);
        Pageable pageable = PageRequest.of(0, 20);
        stubStatusFilteredRegistrations(pageable, opened, finished, canceled);
        when(tableSessionService.summarizeByTables(any(), eq("player-1"))).thenReturn(Map.of());
        when(gameTableMapper.toHistory(any(GameTable.class), any())).thenAnswer(invocation -> {
            GameTable table = invocation.getArgument(0);
            return new GameTableHistoryResponse(
                    table.getId(), table.getName(), table.getStatus().name(), null, null, null,
                    new AttendanceSummaryResponse(0, 0, 0, 0));
        });

        var result = gameTableService.listMineHistory("player-1", pageable);

        assertThat(result.content()).extracting(GameTableHistoryResponse::id)
                .containsExactlyInAnyOrder("table-done", "table-called-off");
    }

    /**
     * #133a, #137: the attendance travels with each table's card, resolved for the whole page in one
     * grouped read - {@link TableSessionService#summarizeByTables} and not one {@code summarize}
     * call per row - and {@code Unknown} stays out of the denominator the same way #137 already
     * keeps it out everywhere else.
     */
    @Test
    @DisplayName("el historial trae la asistencia de cada mesa en una sola consulta agrupada, sin Unknown en el denominador")
    void listMineHistoryCarriesEachTablesOwnAttendanceFromOneGroupedRead() {
        GameTable tableOne = persistedTable("table-h1", GameTableStatus.Finished);
        GameTable tableTwo = persistedTable("table-h2", GameTableStatus.Canceled);
        Pageable pageable = PageRequest.of(0, 20);
        stubStatusFilteredRegistrations(pageable, tableOne, tableTwo);
        AttendanceSummaryResponse tableOneAttendance = new AttendanceSummaryResponse(2, 0, 1, 3);
        AttendanceSummaryResponse tableTwoAttendance = new AttendanceSummaryResponse(0, 0, 0, 0);
        when(tableSessionService.summarizeByTables(eq(List.of("table-h1", "table-h2")), eq("player-1")))
                .thenReturn(Map.of("table-h1", tableOneAttendance, "table-h2", tableTwoAttendance));
        when(gameTableMapper.toHistory(tableOne, tableOneAttendance)).thenReturn(new GameTableHistoryResponse(
                "table-h1", "Test", "Finished", null, null, null, tableOneAttendance));
        when(gameTableMapper.toHistory(tableTwo, tableTwoAttendance)).thenReturn(new GameTableHistoryResponse(
                "table-h2", "Test", "Canceled", null, null, null, tableTwoAttendance));

        var result = gameTableService.listMineHistory("player-1", pageable);

        // One call for the whole page, not one per table - the N+1 the contract calls out by name.
        verify(tableSessionService).summarizeByTables(any(), eq("player-1"));
        assertThat(result.content()).extracting(GameTableHistoryResponse::attendance)
                .containsExactlyInAnyOrder(tableOneAttendance, tableTwoAttendance);
        // Table two never had a row recorded (all Unknown) and reads as zero, not as absent.
        assertThat(result.content()).filteredOn(response -> response.id().equals("table-h2"))
                .singleElement()
                .extracting(response -> response.attendance().registered())
                .isEqualTo(0);
    }

    /**
     * A row that never became {@code Player} is a candidate the master has not accepted, and it
     * belongs in neither listing (#28, #133a) - the repository is asked for {@code Player} on both
     * calls, never {@code Candidate}.
     */
    @Test
    @DisplayName("un actor que fue Candidate y nunca Player no aparece en /mine ni en su historial")
    void aCandidateWhoNeverBecamePlayerAppearsInNeitherListing() {
        Pageable pageable = PageRequest.of(0, 20);
        when(tableRegistrationRepository.findByUser_IdAndStatusAndGameTable_StatusIn(
                        eq("player-1"), eq(TableRegistrationStatus.Player), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        gameTableService.listMine("player-1", pageable);
        gameTableService.listMineHistory("player-1", pageable);

        verify(tableRegistrationRepository, org.mockito.Mockito.times(2))
                .findByUser_IdAndStatusAndGameTable_StatusIn(eq("player-1"), eq(TableRegistrationStatus.Player), any(), eq(pageable));
        verify(tableRegistrationRepository, never())
                .findByUser_IdAndStatusAndGameTable_StatusIn(eq("player-1"), eq(TableRegistrationStatus.Candidate), any(), any());
    }

    /**
     * Fakes the derived query {@code findByUser_IdAndStatusAndGameTable_StatusIn} enough to prove the
     * *service* asks for the right set of table statuses: one Player registration per given table,
     * and the fake filters by whatever status collection the service actually passed - the real
     * derived query itself is proven against MySQL by {@code GameTableHistoryIT}.
     */
    private void stubStatusFilteredRegistrations(Pageable pageable, GameTable... tables) {
        List<TableRegistration> registrations = new java.util.ArrayList<>();
        for (GameTable table : tables) {
            TableRegistration registration = new TableRegistration(table, persistedUser("player-1"), null);
            registration.setStatus(TableRegistrationStatus.Player);
            registrations.add(registration);
        }
        when(tableRegistrationRepository.findByUser_IdAndStatusAndGameTable_StatusIn(
                        eq("player-1"), eq(TableRegistrationStatus.Player), any(), eq(pageable)))
                .thenAnswer(invocation -> {
                    java.util.Collection<GameTableStatus> statuses = invocation.getArgument(2);
                    List<TableRegistration> matching = registrations.stream()
                            .filter(registration -> statuses.contains(registration.getGameTable().getStatus()))
                            .toList();
                    return new PageImpl<>(matching);
                });
    }

    /** /my/tables needs a Primary to map a summary at all; this wires the minimum for one table. */
    private void stubPrimarylessSummary(GameTable table) {
        Master primary = new Master(table, persistedUser("primary-of-" + table.getId()), MasterType.Primary);
        when(masterService.findByGameTable(table.getId())).thenReturn(List.of(primary));
        MasterSummaryResponse primarySummary = new MasterSummaryResponse("primary-of-" + table.getId(), "Primary", 8000, "Primary");
        when(gameTableMapper.toMasterSummary(primary)).thenReturn(primarySummary);
        GameTableSummaryResponse summary =
                new GameTableSummaryResponse(table.getId(), "Test", table.getStatus().name(), null, null, null, 0, List.of(), false, primarySummary);
        when(gameTableMapper.toSummary(eq(table), org.mockito.ArgumentMatchers.anyInt(), eq(primarySummary), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(summary);
    }

    @Test
    void getManagedDetailRejectsSomeoneWhoIsNotAMasterOfThatTable() {
        GameTable table = persistedTable("table-6", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-6")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-6", "outsider-1")).thenReturn(false);

        assertThatThrownBy(() -> gameTableService.getManagedDetail("table-6", "outsider-1")).isInstanceOf(ForbiddenActionException.class);

        org.mockito.Mockito.verify(gameTableMapper, org.mockito.Mockito.never())
                .toDetail(
                        any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void getManagedDetailReturnsTheTableForItsOwnMaster() {
        GameTable table = persistedTable("table-7", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-7")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-7", "master-1")).thenReturn(true);
        when(masterService.findByGameTable("table-7")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-7", "Test", null, null, null, null, null, "Opened", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        GameTableDetailResponse response = gameTableService.getManagedDetail("table-7", "master-1");

        assertThat(response.id()).isEqualTo("table-7");
    }

    @Test
    void getEntityByIdThrowsWhenMissing() {
        when(gameTableRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameTableService.getEntityById("missing")).isInstanceOf(NotFoundException.class);
    }

    /** #180: closing a table is what starts the two-week profile window of #44, so it gets a date. */
    @Test
    void finishSealsClosedAt() {
        GameTable table = persistedTable("table-close-1", GameTableStatus.InProgress);
        when(gameTableRepository.findByIdForUpdate("table-close-1")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-close-1", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(persistedUser("primary-1"));
        when(masterService.findByGameTable("table-close-1")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-close-1", "Test", null, null, null, null, null, "Finished", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.finish("table-close-1", "primary-1");

        assertThat(table.getClosedAt()).isNotNull();
    }

    @Test
    void cancelSealsClosedAtToo() {
        GameTable table = persistedTable("table-close-2", GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-close-2")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-close-2", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(persistedUser("primary-1"));
        when(masterService.findByGameTable("table-close-2")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-close-2", "Test", null, null, null, null, null, "Canceled", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.cancel("table-close-2", "primary-1", new ChangeTableStatusRequest("se cae"));

        assertThat(table.getClosedAt()).isNotNull();
    }

    /** A table closes once (#44, #180): a later transition never moves the date. */
    @Test
    void closedAtIsNotOverwrittenOnceItIsSet() {
        GameTable table = persistedTable("table-close-3", GameTableStatus.InProgress);
        LocalDateTime original = LocalDateTime.of(2026, 1, 1, 12, 0);
        table.setClosedAt(original);
        when(gameTableRepository.findByIdForUpdate("table-close-3")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-close-3", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(persistedUser("primary-1"));
        when(masterService.findByGameTable("table-close-3")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-close-3", "Test", null, null, null, null, null, "Finished", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.finish("table-close-3", "primary-1");

        assertThat(table.getClosedAt()).isEqualTo(original);
    }

    @Test
    void updateRewritesTheDraftAndSanitizesItsRichText() {
        GameTable table = persistedTable("table-edit-1", GameTableStatus.Draft);
        when(gameTableRepository.findByIdForUpdate("table-edit-1")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-edit-1", "master-1")).thenReturn(true);
        when(masterService.findByGameTable("table-edit-1")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-edit-1", "Nuevo", null, null, null, null, null, "Preparation", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.update(
                "table-edit-1",
                new UpdateGameTableRequest(
                        "Nuevo", "<p>Hola</p><script>alert(1)</script>", null, null, null, List.of("system-1"), List.of("tag-1"), List.of("platform-1"),
                        null, null, 5, List.of(new TableScheduleEntry(Weekday.Friday, LocalTime.of(20, 0), LocalTime.of(3, 0)))),
                "master-1");

        assertThat(table.getName()).isEqualTo("Nuevo");
        assertThat(table.getDescription()).isEqualTo("<p>Hola</p>");
        assertThat(table.getMaxPlayers()).isEqualTo(5);
    }

    @Test
    void someoneWhoDoesNotRunTheTableCannotEditIt() {
        GameTable table = persistedTable("table-edit-2", GameTableStatus.Draft);
        when(gameTableRepository.findByIdForUpdate("table-edit-2")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-edit-2", "outsider-1")).thenReturn(false);

        assertThatThrownBy(() -> gameTableService.update("table-edit-2", updateRequest(), "outsider-1"))
                .isInstanceOf(ForbiddenActionException.class);
    }

    /** Past Preparation people applied on the strength of what the table says (#27). */
    @Test
    void anOpenedTableIsNoLongerItsMastersToRewrite() {
        GameTable table = persistedTable("table-edit-3", GameTableStatus.Opened);
        when(gameTableRepository.findByIdForUpdate("table-edit-3")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-edit-3", "master-1")).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.update("table-edit-3", updateRequest(), "master-1"))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * #245: a table sent to review is being read by somebody else, and moving it while they read is
     * how a reviewer approves something that no longer exists.
     */
    @Test
    @DisplayName("una mesa enviada a revisión ya no la edita su master")
    void aTableAwaitingReviewIsNoLongerItsMastersToRewrite() {
        GameTable table = persistedTable("table-in-review", GameTableStatus.Preparation);
        when(gameTableRepository.findByIdForUpdate("table-in-review")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-in-review", "master-1")).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.update("table-in-review", updateRequest(), "master-1"))
                .isInstanceOf(ConflictException.class);
    }

    /** Sending it is the act that makes the table exist for anybody else (#245). */
    @Test
    @DisplayName("enviar a revisión mueve el borrador a Preparation")
    void submittingADraftSendsItToReview() {
        GameTable table = persistedTable("table-draft", GameTableStatus.Draft);
        when(gameTableRepository.findByIdForUpdate("table-draft")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-draft", "master-1")).thenReturn(true);
        when(masterService.findByGameTable("table-draft")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-draft", "Test", null, null, null, null, null, "Preparation", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));

        gameTableService.submitForReview("table-draft", "master-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Preparation);
    }

    /** Only once: what is already in the queue cannot be filed again. */
    @Test
    void cannotSendATableThatIsNotADraftToReview() {
        GameTable table = persistedTable("table-sent", GameTableStatus.Preparation);
        when(gameTableRepository.findByIdForUpdate("table-sent")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-sent", "master-1")).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.submitForReview("table-sent", "master-1"))
                .isInstanceOf(ConflictException.class);
    }

    /** Each slot carries its own length, so one table can run two different ones (#228). */
    @Test
    @DisplayName("la agenda viaja entera y cada franja con su propia duración")
    void updateSendsEachSlotWithItsOwnDuration() {
        GameTable table = persistedTable("table-edit-4", GameTableStatus.Draft);
        when(gameTableRepository.findByIdForUpdate("table-edit-4")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-edit-4", "master-1")).thenReturn(true);
        when(masterService.findByGameTable("table-edit-4")).thenReturn(List.of());
        when(anyDetailMapping())
                .thenReturn(new GameTableDetailResponse(
                        "table-edit-4", "Test", null, null, null, null, null, "Preparation", null, 0, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, false));
        List<TableScheduleEntry> agenda = List.of(
                new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0), LocalTime.of(3, 0)),
                new TableScheduleEntry(Weekday.Saturday, LocalTime.of(15, 0), LocalTime.of(6, 0)));

        gameTableService.update(
                "table-edit-4",
                new UpdateGameTableRequest(
                        "Test", null, null, null, null, List.of("system-1"), List.of("tag-1"), List.of("platform-1"), null, null, null, agenda),
                "master-1");

        // The agenda travels whole, each slot with its own length (#228): three hours midweek and six
        // on a Saturday is one table, and it used to be a table that had to lie about one of the two.
        verify(tableScheduleService).replace(table, agenda, "master-1");
    }

    /**
     * A bare request, on purpose: the two tests that use it are refused before the draft is looked at
     * - permission and status come first (#226) - and that ordering is part of what they assert.
     */
    private UpdateGameTableRequest updateRequest() {
        return new UpdateGameTableRequest("Test", null, null, null, null, null, null, null, null, null, null, null);
    }

    private GameTable persistedTable(String id, GameTableStatus status) {
        GameTable table = new GameTable("Test table", persistedUser("creator-of-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setStatus(status);
        return table;
    }

    private User persistedUser(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /**
     * Puts the table in an admin's hands. In production this is written by {@code AdminQueueService}
     * under the table's row lock; here it is the arrangement of the three tests that are about who
     * holds what. Resolving does <b>not</b> require it - see {@link #approvesATableNobodyClaimed}.
     */
    private static GameTable claimedBy(GameTable table, User admin) {
        table.claim(admin, LocalDateTime.now());
        return table;
    }

    /**
     * toDetail now takes the agenda, the three catalogs and the sanitized rich text as well, and none
     * of those are what any test here is about: stubbing them one matcher at a time would put ten
     * any() calls in every arrangement and hide the line that matters.
     */
    private GameTableDetailResponse anyDetailMapping() {
        return gameTableMapper.toDetail(
                any(GameTable.class),
                org.mockito.ArgumentMatchers.anyInt(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }
}
