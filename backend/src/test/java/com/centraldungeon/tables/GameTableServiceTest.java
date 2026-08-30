package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.CreateGameTableRequest;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.util.List;
import java.util.Optional;
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
    private MasterService masterService;

    @Mock
    private GameTableMapper gameTableMapper;

    @Mock
    private UserService userService;

    private GameTableService gameTableService;

    @BeforeEach
    void setUp() {
        gameTableService = new GameTableService(
                gameTableRepository, tableTypeRepository, tableRegistrationRepository, masterService, gameTableMapper, userService);
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
    void onlyThePrimaryMasterCanOpenATable() {
        GameTable table = persistedTable("table-2", GameTableStatus.Preparation);
        when(gameTableRepository.findById("table-2")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-2", "secondary-1")).thenReturn(false);

        assertThatThrownBy(() -> gameTableService.open("table-2", "secondary-1")).isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void cannotOpenATableThatIsNotInPreparation() {
        GameTable table = persistedTable("table-3", GameTableStatus.Opened);
        when(gameTableRepository.findById("table-3")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-3", "primary-1")).thenReturn(true);

        assertThatThrownBy(() -> gameTableService.open("table-3", "primary-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void openTransitionsPreparationToOpened() {
        GameTable table = persistedTable("table-4", GameTableStatus.Preparation);
        when(gameTableRepository.findById("table-4")).thenReturn(Optional.of(table));
        when(masterService.isPrimaryOf("table-4", "primary-1")).thenReturn(true);
        when(masterService.findByGameTable("table-4")).thenReturn(List.of());
        when(gameTableMapper.toDetail(any(GameTable.class), eqInt(0), eqList()))
                .thenReturn(new GameTableDetailResponse(
                        "table-4", "Test", null, null, null, "Opened", null, 0, null, null, null, List.of(), null));

        gameTableService.open("table-4", "primary-1");

        assertThat(table.getStatus()).isEqualTo(GameTableStatus.Opened);
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
