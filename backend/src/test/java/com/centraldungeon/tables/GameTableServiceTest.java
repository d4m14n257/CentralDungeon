package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.AssignMastersRequest;
import com.centraldungeon.tables.dto.ChangeTableStatusRequest;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private GameTableService gameTableService;

    @BeforeEach
    void setUp() {
        gameTableService = new GameTableService(
                gameTableRepository, tableTypeRepository, tableRegistrationRepository, tableStatusChangeRepository, masterService,
                gameTableMapper, userService);
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
        when(gameTableMapper.toDetail(any(GameTable.class), eqInt(0), eqList()))
                .thenReturn(new GameTableDetailResponse(
                        "table-1", "Test", null, null, null, "Preparation", null, 0, null, null, null, List.of(), null));

        CreateGameTableRequest request = new CreateGameTableRequest("Test", null, null, null, null, null, null, null);
        GameTableDetailResponse response = gameTableService.create(request, "creator-1");

        assertThat(response.id()).isEqualTo("table-1");
        org.mockito.Mockito.verify(masterService).createPrimary(any(GameTable.class), org.mockito.ArgumentMatchers.eq(creator));
    }

    @Test
    void rejectsCreationWithAnUnknownTableType() {
        when(userService.getById("creator-1")).thenReturn(persistedUser("creator-1"));
        when(tableTypeRepository.findById("missing-type")).thenReturn(Optional.empty());

        CreateGameTableRequest request = new CreateGameTableRequest("Test", null, null, "missing-type", null, null, null, null);

        assertThatThrownBy(() -> gameTableService.create(request, "creator-1")).isInstanceOf(NotFoundException.class);
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
        when(gameTableMapper.toDetail(any(GameTable.class), eqInt(0), eqList()))
                .thenReturn(new GameTableDetailResponse(
                        "table-4", "Test", null, null, null, "Opened", null, 0, null, null, null, List.of(), null));

        gameTableService.approve("table-4", "admin-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Opened);
        org.mockito.Mockito.verify(tableStatusChangeRepository).save(any(TableStatusChange.class));
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
        when(gameTableMapper.toDetail(any(GameTable.class), eqInt(0), eqList()))
                .thenReturn(new GameTableDetailResponse(
                        "table-unassigned", "Test", null, null, null, "Opened", null, 0, null, null, null, List.of(), null));

        gameTableService.assignInitialMasters(
                "table-unassigned", new AssignMastersRequest("primary-1", List.of()), "admin-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Opened);
        org.mockito.Mockito.verify(masterService).assignInitialMasters(table, "primary-1", List.of());
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
        when(gameTableMapper.toDetail(any(GameTable.class), eqInt(0), eqList()))
                .thenReturn(new GameTableDetailResponse(
                        "table-6b", "Test", null, null, null, "Canceled", null, 0, null, null, null, List.of(), null));

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

    /** La línea de #175: lo que ya fue público se cancela, no se borra. */
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

    /** Una mesa borrada no existe para nadie: 404, no 403 (#25). */
    @Test
    void aDeletedTableIsNotFound() {
        GameTable table = persistedTable("table-7e", GameTableStatus.Deleted);
        when(gameTableRepository.findById("table-7e")).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> gameTableService.getDetail("table-7e")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listExcludesTablesTheActorMasters() {
        GameTable table = persistedTable("table-4b", GameTableStatus.Opened);
        User primaryUser = persistedUser("someone-else");
        Master primary = new Master(table, primaryUser, MasterType.Primary);
        Pageable pageable = PageRequest.of(0, 20);
        when(gameTableRepository.findByStatusInAndNotMasteredByActor(
                        List.of(GameTableStatus.Opened, GameTableStatus.InProgress), "player-1", pageable))
                .thenReturn(new PageImpl<>(List.of(table)));
        when(masterService.findByGameTable("table-4b")).thenReturn(List.of(primary));
        MasterSummaryResponse primarySummary = new MasterSummaryResponse("someone-else", "Someone Else", 8000, "Primary");
        when(gameTableMapper.toMasterSummary(primary)).thenReturn(primarySummary);
        GameTableSummaryResponse summary =
                new GameTableSummaryResponse("table-4b", "Test", "Opened", null, null, 0, primarySummary);
        when(gameTableMapper.toSummary(table, 0, primarySummary)).thenReturn(summary);

        var result = gameTableService.list(pageable, "player-1");

        assertThat(result.content()).containsExactly(summary);
        org.mockito.Mockito.verify(gameTableRepository)
                .findByStatusInAndNotMasteredByActor(List.of(GameTableStatus.Opened, GameTableStatus.InProgress), "player-1", pageable);
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
                new GameTableSummaryResponse("table-5", "Test", "Preparation", null, null, 0, primarySummary);
        when(gameTableMapper.toSummary(table, 0, primarySummary)).thenReturn(summary);

        var result = gameTableService.listManaged("master-1", pageable);

        assertThat(result.content()).containsExactly(summary);
    }

    @Test
    void getManagedDetailRejectsSomeoneWhoIsNotAMasterOfThatTable() {
        GameTable table = persistedTable("table-6", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-6")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-6", "outsider-1")).thenReturn(false);

        assertThatThrownBy(() -> gameTableService.getManagedDetail("table-6", "outsider-1")).isInstanceOf(ForbiddenActionException.class);

        org.mockito.Mockito.verify(gameTableMapper, org.mockito.Mockito.never())
                .toDetail(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void getManagedDetailReturnsTheTableForItsOwnMaster() {
        GameTable table = persistedTable("table-7", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-7")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-7", "master-1")).thenReturn(true);
        when(masterService.findByGameTable("table-7")).thenReturn(List.of());
        when(gameTableMapper.toDetail(any(GameTable.class), eqInt(0), eqList()))
                .thenReturn(new GameTableDetailResponse(
                        "table-7", "Test", null, null, null, "Opened", null, 0, null, null, null, List.of(), null));

        GameTableDetailResponse response = gameTableService.getManagedDetail("table-7", "master-1");

        assertThat(response.id()).isEqualTo("table-7");
    }

    @Test
    void getEntityByIdThrowsWhenMissing() {
        when(gameTableRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameTableService.getEntityById("missing")).isInstanceOf(NotFoundException.class);
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

    private int eqInt(int value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    private List<MasterSummaryResponse> eqList() {
        return org.mockito.ArgumentMatchers.eq(List.of());
    }
}
