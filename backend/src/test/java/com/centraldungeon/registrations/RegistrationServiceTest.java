package com.centraldungeon.registrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.approvals.ApprovalStatus;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.registrations.dto.TablePlayerResponse;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.files.FileCategory;
import com.centraldungeon.files.FileService;
import com.centraldungeon.files.FileType;
import com.centraldungeon.files.StoredFile;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.CommittedTable;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.TableVisibilityService;
import com.centraldungeon.tables.ScheduleConflictService;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private RegistrationRejectionRepository rejectionRepository;

    /** The veto's trail (ADR-09 §5): every block and every lifting leaves a row. */
    @Mock
    private RegistrationStatusChangeRepository statusChangeRepository;

    @Mock
    private RegistrationFileRepository registrationFileRepository;

    @Mock
    private FileService fileService;

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private MasterService masterService;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    @Mock
    private RegistrationMapper registrationMapper;

    /** The veto requests the roster names next to each veto. */
    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    private RegistrationService registrationService;

    /** Every table a fixture built, so the lock can hand back the one a test's application belongs to. */
    private final Map<String, GameTable> tablesById = new HashMap<>();

    @BeforeEach
    void setUp() {
        // The writes resolve an application's table from a projection and lock it before reading the
        // application; both answer from whatever the test already stubbed, and a test's own stub wins.
        lenient().when(registrationRepository.findTablesByRegistrationIds(any())).thenAnswer(call -> {
            Collection<String> ids = call.getArgument(0);
            return ids.stream()
                    .flatMap(id -> registrationRepository.findById(id).stream())
                    .map(registration -> new RegistrationTable(registration.getId(), registration.getGameTable().getId()))
                    .toList();
        });
        lenient().when(gameTableRepository.findByIdForUpdate(anyString()))
                .thenAnswer(call -> Optional.ofNullable(tablesById.get(call.<String>getArgument(0))));
        registrationService = new RegistrationService(
                registrationRepository, rejectionRepository, registrationFileRepository, fileService, gameTableRepository, masterService,
                userService, notificationService, scheduleConflictService, statusChangeRepository,
                // The real gate over the same mocked repositories: applying goes through it now, and
                // that is exactly what stops a vetoed person re-applying (ADR-09 §4).
                new TableVisibilityService(gameTableRepository, registrationRepository, masterService), registrationMapper,
                approvalRequestRepository);
    }

    @Test
    void rejectsApplicationsFromSomeoneWithoutThePlayerRole() {
        GameTable table = persistedTable("table-1", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-1")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("master-only")).thenReturn(new UserAuthSnapshot("master-only", UserStatus.Allowed, Set.of("Master")));

        assertThatThrownBy(() -> registrationService.apply("table-1", "master-only", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void rejectsApplicationsFromAMasterOfThatSameTable() {
        GameTable table = persistedTable("table-1b", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-1b")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-1b")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("master-player-1"))
                .thenReturn(new UserAuthSnapshot("master-player-1", UserStatus.Allowed, Set.of("Player", "Master")));
        when(masterService.isMasterOf("table-1b", "master-player-1")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.apply("table-1b", "master-player-1", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void rejectsApplicationsToATableThatIsNotOpen() {
        GameTable table = persistedTable("table-2", GameTableStatus.Finished, null);
        when(gameTableRepository.findByIdForUpdate("table-2")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-2")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));

        assertThatThrownBy(() -> registrationService.apply("table-2", "player-1", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsASecondActiveApplicationForTheSamePair() {
        GameTable table = persistedTable("table-3", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-3")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-3")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        // The veto gate asks the same method with [Blocked] before anything else, so the stubbing
        // has to say what that one answers too (ADR-09 §4).
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-3", "player-1", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(false);
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-3", "player-1", List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player)))
                .thenReturn(true);

        assertThatThrownBy(() -> registrationService.apply("table-3", "player-1", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptsAValidApplication() {
        GameTable table = persistedTable("table-4", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-4")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-4")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-1")).thenReturn(persistedUser("player-1"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toResponse(any(TableRegistration.class), any()))
                .thenReturn(new RegistrationResponse("reg-1", "table-4", "Test table", "player-1", "P1", "Candidate", null, null, null, null, List.of(), null, null, null));

        RegistrationResponse response = registrationService.apply("table-4", "player-1", new CreateRegistrationRequest("please", List.of()));

        assertThat(response.status()).isEqualTo("Candidate");
    }

    /** A table already in progress takes applications while its masters are looking for players - and only then. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aTableInProgressTakesApplicationsOnlyWhileLookingForPlayers(boolean seeking) {
        GameTable table = persistedTable("table-seek", GameTableStatus.InProgress, null);
        table.setSeekingPlayers(seeking);
        when(gameTableRepository.findByIdForUpdate("table-seek")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-seek")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        lenient().when(userService.getById("player-1")).thenReturn(persistedUser("player-1"));
        lenient().when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        if (seeking) {
            registrationService.apply("table-seek", "player-1", new CreateRegistrationRequest(null, List.of()));
            verify(registrationRepository).save(any(TableRegistration.class));
        } else {
            assertThatThrownBy(() -> registrationService.apply("table-seek", "player-1", new CreateRegistrationRequest(null, List.of())))
                    .isInstanceOf(ConflictException.class);
            verify(registrationRepository, never()).save(any(TableRegistration.class));
        }
    }

    @Test
    void applyNotifiesEveryMasterOfTheTable() {
        GameTable table = persistedTable("table-4b", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-4b")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-4b")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-1")).thenReturn(persistedUser("player-1"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toResponse(any(TableRegistration.class), any()))
                .thenReturn(new RegistrationResponse("reg-1b", "table-4b", "Test table", "player-1", "P1", "Candidate", null, null, null, null, List.of(), null, null, null));
        com.centraldungeon.tables.Master primary =
                new com.centraldungeon.tables.Master(table, persistedUser("master-1"), com.centraldungeon.tables.MasterType.Primary);
        com.centraldungeon.tables.Master secondary =
                new com.centraldungeon.tables.Master(table, persistedUser("master-2"), com.centraldungeon.tables.MasterType.Secondary);
        when(masterService.findByGameTable("table-4b")).thenReturn(List.of(primary, secondary));

        registrationService.apply("table-4b", "player-1", new CreateRegistrationRequest("please", List.of()));

        verify(notificationService).notifyNewCandidate("master-1", table, "name-player-1");
        verify(notificationService).notifyNewCandidate("master-2", table, "name-player-1");
    }

    @Test
    void onlyAMasterOfTheTableCanAcceptACandidate() {
        TableRegistration registration = persistedRegistration("reg-1", "table-5", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-1")).thenReturn(Optional.of(registration));
        when(gameTableRepository.findByIdForUpdate("table-5")).thenReturn(Optional.of(registration.getGameTable()));
        when(masterService.isMasterOf("table-5", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> registrationService.accept("reg-1", "stranger")).isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void cannotAcceptARegistrationThatIsNotAPendingCandidate() {
        TableRegistration registration = persistedRegistration("reg-2", "table-6", TableRegistrationStatus.Rejected, null);
        when(registrationRepository.findById("reg-2")).thenReturn(Optional.of(registration));
        when(gameTableRepository.findByIdForUpdate("table-6")).thenReturn(Optional.of(registration.getGameTable()));
        when(masterService.isMasterOf("table-6", "master-1")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.accept("reg-2", "master-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptingWithRoomLeftDoesNotAutoRejectAnyone() {
        TableRegistration registration = persistedRegistration("reg-3", "table-7", TableRegistrationStatus.Candidate, 5);
        when(registrationRepository.findById("reg-3")).thenReturn(Optional.of(registration));
        when(gameTableRepository.findByIdForUpdate("table-7")).thenReturn(Optional.of(registration.getGameTable()));
        when(masterService.isMasterOf("table-7", "master-1")).thenReturn(true);
        when(registrationRepository.countByGameTable_IdAndStatus("table-7", TableRegistrationStatus.Player)).thenReturn(1L);
        when(registrationMapper.toResponse(eq(registration), any()))
                .thenReturn(new RegistrationResponse("reg-3", "table-7", "Test table", "player-1", "P1", "Player", null, null, null, null, List.of(), null, null, null));

        registrationService.accept("reg-3", "master-1");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Player);
        verify(registrationRepository, never()).findByGameTable_IdAndStatusOrderByCreatedAtAsc(anyString(), any());
    }

    @Test
    void acceptingTheLastSeatAutoRejectsTheRemainingCandidatesAsTableIsFull() {
        TableRegistration accepted = persistedRegistration("reg-4", "table-8", TableRegistrationStatus.Candidate, 1);
        GameTable table = accepted.getGameTable();
        TableRegistration otherCandidate = new TableRegistration(table, persistedUser("player-2"), null);
        ReflectionTestUtils.setField(otherCandidate, "id", "reg-5");

        when(registrationRepository.findById("reg-4")).thenReturn(Optional.of(accepted));
        when(gameTableRepository.findByIdForUpdate("table-8")).thenReturn(Optional.of(table));
        when(masterService.isMasterOf("table-8", "master-1")).thenReturn(true);
        // A seat left when asked before accepting, the table full once the candidate takes it.
        when(registrationRepository.countByGameTable_IdAndStatus("table-8", TableRegistrationStatus.Player)).thenReturn(0L, 1L);
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("table-8", TableRegistrationStatus.Candidate))
                .thenReturn(List.of(otherCandidate));
        when(registrationMapper.toResponse(eq(accepted), any()))
                .thenReturn(new RegistrationResponse("reg-4", "table-8", "Test table", "player-1", "P1", "Player", null, null, null, null, List.of(), null, null, null));

        registrationService.accept("reg-4", "master-1");

        assertThat(accepted.getStatus()).isEqualTo(TableRegistrationStatus.Player);
        assertThat(otherCandidate.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        verify(rejectionRepository).save(any(RegistrationRejection.class));
        verify(notificationService).notifyRegistrationRejected("player-2", table);
    }

    /** Applying is subject to the seats left: a full table refuses the newcomer with the same code its rejections carry. */
    @Test
    void applyingToAFullTableIsRefusedAsTableFull() {
        GameTable table = persistedTable("table-full-a", GameTableStatus.Opened, 2);
        when(gameTableRepository.findById("table-full-a")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.countByGameTable_IdAndStatus("table-full-a", TableRegistrationStatus.Player)).thenReturn(2L);

        assertThatThrownBy(() -> registrationService.apply("table-full-a", "player-1", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.getErrorCode()).isEqualTo(ConflictException.TABLE_FULL));
        verify(registrationRepository, never()).save(any(TableRegistration.class));
    }

    /** Accepting is the other door: a candidate who got in before the table filled cannot push it past its seats. */
    @Test
    void acceptingAtAFullTableIsRefusedAsTableFull() {
        TableRegistration registration = persistedRegistration("reg-full", "table-full-b", TableRegistrationStatus.Candidate, 1);
        when(registrationRepository.findById("reg-full")).thenReturn(Optional.of(registration));
        when(masterService.isMasterOf("table-full-b", "master-1")).thenReturn(true);
        when(registrationRepository.countByGameTable_IdAndStatus("table-full-b", TableRegistrationStatus.Player)).thenReturn(1L);

        assertThatThrownBy(() -> registrationService.accept("reg-full", "master-1"))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.getErrorCode()).isEqualTo(ConflictException.TABLE_FULL));
        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Candidate);
    }

    /** A deleted table does not exist for a write either: 404, never a conflict that confirms it is there. */
    @Test
    void aWriteOnADeletedTableIsNotFound() {
        TableRegistration registration = persistedRegistration("reg-gone", "table-gone", TableRegistrationStatus.Candidate, null);
        registration.getGameTable().setStatus(GameTableStatus.Deleted);
        when(registrationRepository.findById("reg-gone")).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> registrationService.accept("reg-gone", "master-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyAMasterCanRejectACandidate() {
        TableRegistration registration = persistedRegistration("reg-6", "table-9", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-6")).thenReturn(Optional.of(registration));
        when(masterService.isMasterOf("table-9", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> registrationService.reject("reg-6", "stranger", new RejectRegistrationRequest("no")))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void rejectingRecordsTheJustificationAndNotifies() {
        TableRegistration registration = persistedRegistration("reg-7", "table-10", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-7")).thenReturn(Optional.of(registration));
        when(masterService.isMasterOf("table-10", "master-1")).thenReturn(true);
        when(userService.getById("master-1")).thenReturn(persistedUser("master-1"));
        when(registrationMapper.toResponse(eq(registration), any()))
                .thenReturn(new RegistrationResponse("reg-7", "table-10", "Test table", "player-1", "P1", "Rejected", null, null, null, null, List.of(), null, null, null));

        registrationService.reject("reg-7", "master-1", new RejectRegistrationRequest("Not a fit"));

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        verify(rejectionRepository).save(any(RegistrationRejection.class));
        verify(notificationService).notifyRegistrationRejected(registration.getUser().getId(), registration.getGameTable());
    }

    /**
     * ADR-23 §3: what a master wrote is their own words and travels verbatim. What the application wrote
     * is a code, so it can be read in whichever language the applicant chose.
     */
    @Test
    void listMineShowsAMasterJustificationVerbatim() {
        TableRegistration registration = persistedRegistration("reg-8", "table-12", TableRegistrationStatus.Rejected, null);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(registrationRepository.findByUser_IdAndStatusNotIn(
                        "player-1",
                        List.of(TableRegistrationStatus.Deleted, TableRegistrationStatus.Blocked),
                        pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(registration)));
        when(registrationMapper.toResponse(eq(registration), any()))
                .thenReturn(new RegistrationResponse("reg-8", "table-12", "Test table", "player-1", "P1", "Rejected", null, null, null, null, List.of(), null, null, null));
        RegistrationRejection rejection = new RegistrationRejection(registration, "No encaja con el tono", persistedUser("master-9"));
        when(rejectionRepository.findByRegistration_IdIn(List.of("reg-8"))).thenReturn(List.of(rejection));

        var result = registrationService.listMine("player-1", pageable);

        assertThat(result.content().get(0).rejectionJustification()).isEqualTo("No encaja con el tono");
        assertThat(result.content().get(0).rejectionReasonCode()).isNull();
    }

    @Test
    void listMineReportsAnAutomaticRejectionAsACodeAndNotAsText() {
        TableRegistration registration = persistedRegistration("reg-9", "table-13", TableRegistrationStatus.Rejected, null);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(registrationRepository.findByUser_IdAndStatusNotIn(
                        "player-1",
                        List.of(TableRegistrationStatus.Deleted, TableRegistrationStatus.Blocked),
                        pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(registration)));
        when(registrationMapper.toResponse(eq(registration), any()))
                .thenReturn(new RegistrationResponse("reg-9", "table-13", "Test table", "player-1", "P1", "Rejected", null, null, null, null, List.of(), null, null, null));
        // rejected_by null is what marks the rejection as the system's own (ADR-09 §3).
        RegistrationRejection rejection = new RegistrationRejection(registration, "TABLE_FULL", null);
        when(rejectionRepository.findByRegistration_IdIn(List.of("reg-9"))).thenReturn(List.of(rejection));

        var result = registrationService.listMine("player-1", pageable);

        assertThat(result.content().get(0).rejectionReasonCode()).isEqualTo("TABLE_FULL");
        assertThat(result.content().get(0).rejectionJustification()).isNull();
    }

    @Test
    void myRejectionShowsAMasterJustificationAndTheDate() {
        TableRegistration registration = persistedRegistration("reg-r1", "table-r1", TableRegistrationStatus.Rejected, null);
        when(registrationRepository.findFirstByGameTable_IdAndUser_IdOrderByCreatedAtDescIdDesc("table-r1", "player-1"))
                .thenReturn(Optional.of(registration));
        RegistrationRejection rejection = rejectionAt(registration, "No encaja con el tono", persistedUser("master-9"));
        when(rejectionRepository.findByRegistration_Id("reg-r1")).thenReturn(Optional.of(rejection));

        var mine = registrationService.findMyRejection("table-r1", "player-1");

        assertThat(mine).isNotNull();
        assertThat(mine.justification()).isEqualTo("No encaja con el tono");
        assertThat(mine.reasonCode()).isNull();
        assertThat(mine.rejectedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 12, 0));
    }

    @Test
    void myRejectionReportsTheAutomaticOneAsACode() {
        TableRegistration registration = persistedRegistration("reg-r2", "table-r2", TableRegistrationStatus.Rejected, null);
        when(registrationRepository.findFirstByGameTable_IdAndUser_IdOrderByCreatedAtDescIdDesc("table-r2", "player-1"))
                .thenReturn(Optional.of(registration));
        when(rejectionRepository.findByRegistration_Id("reg-r2"))
                .thenReturn(Optional.of(rejectionAt(registration, "TABLE_FULL", null)));

        var mine = registrationService.findMyRejection("table-r2", "player-1");

        assertThat(mine).isNotNull();
        assertThat(mine.reasonCode()).isEqualTo("TABLE_FULL");
        assertThat(mine.justification()).isNull();
    }

    @Test
    void myRejectionIsNullOnceTheActorAppliedAgain() {
        TableRegistration reapplied = persistedRegistration("reg-r3", "table-r3", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findFirstByGameTable_IdAndUser_IdOrderByCreatedAtDescIdDesc("table-r3", "player-1"))
                .thenReturn(Optional.of(reapplied));

        assertThat(registrationService.findMyRejection("table-r3", "player-1")).isNull();
        verifyNoInteractions(rejectionRepository);
    }

    @Test
    void myRejectionIsNullForSomebodyWhoNeverApplied() {
        when(registrationRepository.findFirstByGameTable_IdAndUser_IdOrderByCreatedAtDescIdDesc("table-r4", "player-1"))
                .thenReturn(Optional.empty());

        assertThat(registrationService.findMyRejection("table-r4", "player-1")).isNull();
        verifyNoInteractions(rejectionRepository);
    }

    /** Somebody else's rejection never shows: the lookup is keyed by the reader, so a stranger gets nothing. */
    @Test
    void myRejectionNeverShowsAnotherPersonsRejection() {
        when(registrationRepository.findFirstByGameTable_IdAndUser_IdOrderByCreatedAtDescIdDesc("table-r5", "stranger"))
                .thenReturn(Optional.empty());

        assertThat(registrationService.findMyRejection("table-r5", "stranger")).isNull();
        verify(registrationRepository).findFirstByGameTable_IdAndUser_IdOrderByCreatedAtDescIdDesc("table-r5", "stranger");
        verifyNoInteractions(rejectionRepository);
    }

    @Test
    void listCandidatesForTableRequiresBeingAMaster() {
        when(masterService.canOversee("table-11", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> registrationService.listCandidatesForTable(
                        "table-11", "stranger", org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void applyThrowsWhenTheTableDoesNotExist() {
        // The locking read is what answers here, because it is the first statement of the transaction
        // and has to be (ADR-06 §2). It gives the same 404 the single gate would have - a missing table and
        // a vetoed one are indistinguishable on purpose (ADR-03 §7, ADR-09 §4).
        when(gameTableRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.apply("missing", "player-1", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOf(NotFoundException.class);
    }

    /** R2 (ADR-08 §2): a table where you already play is a real commitment, so applying elsewhere at that hour is refused. */
    @Test
    void refusesAnApplicationThatClashesWithATableTheApplicantAlreadyPlaysAt() {
        GameTable table = persistedTable("table-r2", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-r2")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-r2")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(scheduleConflictService.findClashWith("player-1", table)).thenReturn(new CommittedTable("other", "La cripta"));

        assertThatThrownBy(() -> registrationService.apply("table-r2", "player-1", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("La cripta");

        verify(registrationRepository, never()).save(any(TableRegistration.class));
    }

    /** R3 (ADR-08 §2): asked again at accept time, because the candidate may have been taken elsewhere in between. */
    @Test
    void refusesToAcceptACandidateWhoNowPlaysAtAClashingTable() {
        TableRegistration registration = persistedRegistration("reg-r3", "table-r3", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-r3")).thenReturn(Optional.of(registration));
        when(gameTableRepository.findByIdForUpdate("table-r3")).thenReturn(Optional.of(registration.getGameTable()));
        when(masterService.isMasterOf("table-r3", "master-1")).thenReturn(true);
        when(scheduleConflictService.findClashWith("player-1", registration.getGameTable()))
                .thenReturn(new CommittedTable("other", "El faro"));

        // The code and the parameter, not the sentence: what the master reads is written by the
        // frontend in their language (ADR-23 §3). This assertion used to look for the table's name inside
        // the message, which is how the one Spanish sentence left in the backend went unnoticed.
        assertThatThrownBy(() -> registrationService.accept("reg-r3", "master-1"))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .satisfies(exception -> {
                    // Its own code: the sentence is about a third person, so the applicant's wording
                    // ("where you are already committed") would be plainly false here.
                    assertThat(exception.getErrorCode()).isEqualTo(ConflictException.CANDIDATE_SCHEDULE_CONFLICT);
                    assertThat(exception.getErrorParams()).containsEntry(ConflictException.PARAM_OTHER_TABLE_NAME, "El faro");
                });

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Candidate);
    }

    /**
     * R4 (ADR-08 §2): the other pending applications that now clash are <b>notified</b>, not rejected.
     * Until somebody is accepted there is no commitment, and choosing between them is theirs (ADR-15 §2).
     */
    @Test
    void acceptingSomebodyWarnsThemAboutTheirOtherApplicationsThatNowClash() {
        TableRegistration accepted = persistedRegistration("reg-r4", "table-r4", TableRegistrationStatus.Candidate, null);
        GameTable acceptedTable = accepted.getGameTable();
        TableRegistration pendingElsewhere = persistedRegistration("reg-r4b", "table-other", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-r4")).thenReturn(Optional.of(accepted));
        when(gameTableRepository.findByIdForUpdate("table-r4")).thenReturn(Optional.of(acceptedTable));
        when(masterService.isMasterOf("table-r4", "master-1")).thenReturn(true);
        when(registrationRepository.findByUser_IdAndStatus("player-1", TableRegistrationStatus.Candidate))
                .thenReturn(List.of(accepted, pendingElsewhere));
        when(scheduleConflictService.overlap(acceptedTable, pendingElsewhere.getGameTable())).thenReturn(true);
        when(registrationMapper.toResponse(any(TableRegistration.class), any()))
                .thenReturn(new RegistrationResponse("reg-r4", "table-r4", "Test table", "player-1", "P1", "Player", null, null, null, null, List.of(), null, null, null));

        registrationService.accept("reg-r4", "master-1");

        verify(notificationService).notifyScheduleConflict("player-1", pendingElsewhere.getGameTable(), acceptedTable.getName());
        assertThat(pendingElsewhere.getStatus()).isEqualTo(TableRegistrationStatus.Candidate);
    }

    /** The way out R4's notice needs to leave open (ADR-08 §2): withdrawing your own pending application. */
    @Test
    void withdrawMarksTheOwnPendingApplicationAsDeleted() {
        TableRegistration registration = persistedRegistration("reg-w1", "table-w1", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-w1")).thenReturn(Optional.of(registration));

        registrationService.withdraw("reg-w1", "player-1");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Deleted);
        assertThat(registration.getDeletedAt()).isNotNull();
    }

    @Test
    void cannotWithdrawSomebodyElsesApplication() {
        TableRegistration registration = persistedRegistration("reg-w2", "table-w2", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-w2")).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> registrationService.withdraw("reg-w2", "someone-else")).isInstanceOf(ForbiddenActionException.class);
    }

    /** Once accepted there is a table counting on you: leaving is a conversation, not a button. */
    @Test
    void cannotWithdrawAnApplicationThatWasAlreadyAccepted() {
        TableRegistration registration = persistedRegistration("reg-w3", "table-w3", TableRegistrationStatus.Player, null);
        when(registrationRepository.findById("reg-w3")).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> registrationService.withdraw("reg-w3", "player-1")).isInstanceOf(ConflictException.class);
    }

    /**
     * ADR-09 §2, deliberate: withdrawing a pending application never touches {@code registration_files}.
     * The row is the record that a sheet was sent, and withdrawing does not undo that it was - only
     * the read side stops counting it as in use.
     */
    @Test
    void withdrawingNeverTouchesRegistrationFiles() {
        TableRegistration registration = persistedRegistration("reg-w4", "table-w4", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-w4")).thenReturn(Optional.of(registration));

        registrationService.withdraw("reg-w4", "player-1");

        verifyNoInteractions(registrationFileRepository);
    }

    // ---------------------------------------------------------------- files attached to an application (ADR-13 §1 uso 2)

    /** The same gate as attaching to a table or an answer: the applicant's own, or one published (ADR-14 §1). */
    @Test
    void attachingOwnFileLinksItAndClassifiesItAsPlayerApplication() {
        GameTable table = persistedTable("table-f1", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-f1")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-f1")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-1")).thenReturn(persistedUser("player-1"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(persistRegistration("reg-f1"));
        StoredFile file = persistedFile("file-1", persistedUser("player-1"));
        when(fileService.requireAttachable("file-1", "player-1")).thenReturn(file);
        when(registrationMapper.toResponse(any(TableRegistration.class), any()))
                .thenReturn(new RegistrationResponse(
                        "reg-f1", "table-f1", "Test table", "player-1", "P1", "Candidate", null, null, null, null, List.of(), null, null, null));

        registrationService.apply("table-f1", "player-1", new CreateRegistrationRequest(null, List.of("file-1")));

        verify(fileService).classify("file-1", FileCategory.PlayerApplication);
        verify(registrationFileRepository).save(any(RegistrationFile.class));
    }

    /**
     * From this service's point of view, a published file goes through the exact same door as the
     * applicant's own: {@code FileService.requireAttachable} is where "own or published" is decided
     * (ADR-14 §1), and it is mocked here on purpose - that gate has its own test in {@code FileServiceTest}.
     */
    @Test
    void attachingAPublishedFileLinksItTheSameWay() {
        GameTable table = persistedTable("table-f2", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-f2")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-f2")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-2")).thenReturn(new UserAuthSnapshot("player-2", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-2")).thenReturn(persistedUser("player-2"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(persistRegistration("reg-f2"));
        StoredFile published = persistedFile("file-published", persistedUser("some-admin"));
        ReflectionTestUtils.setField(published, "fileType", FileType.Public);
        when(fileService.requireAttachable("file-published", "player-2")).thenReturn(published);
        when(registrationMapper.toResponse(any(TableRegistration.class), any()))
                .thenReturn(new RegistrationResponse(
                        "reg-f2", "table-f2", "Test table", "player-2", "P2", "Candidate", null, null, null, null, List.of(), null, null, null));

        registrationService.apply("table-f2", "player-2", new CreateRegistrationRequest(null, List.of("file-published")));

        verify(fileService).classify("file-published", FileCategory.PlayerApplication);
        verify(registrationFileRepository).save(any(RegistrationFile.class));
    }

    /** Somebody else's private upload never gets attached (ADR-14 §1) - 403, and nothing is linked. */
    @Test
    void refusesAFileThatIsSomebodyElsesAndNotPublished() {
        GameTable table = persistedTable("table-f3", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-f3")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-f3")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-3")).thenReturn(new UserAuthSnapshot("player-3", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-3")).thenReturn(persistedUser("player-3"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(persistRegistration("reg-f3"));
        when(fileService.requireAttachable("file-of-somebody-else", "player-3"))
                .thenThrow(new ForbiddenActionException("File belongs to somebody else"));

        assertThatThrownBy(() -> registrationService.apply(
                        "table-f3", "player-3", new CreateRegistrationRequest(null, List.of("file-of-somebody-else"))))
                .isInstanceOf(ForbiddenActionException.class);
        verify(registrationFileRepository, never()).save(any());
    }

    /** Attaching is optional (ADR-13 §1 uso 2): an empty list is a normal application. */
    @Test
    void applyingWithNoFilesIsValid() {
        GameTable table = persistedTable("table-f4", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-f4")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-f4")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-4")).thenReturn(new UserAuthSnapshot("player-4", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-4")).thenReturn(persistedUser("player-4"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(persistRegistration("reg-f4"));
        when(registrationMapper.toResponse(any(TableRegistration.class), any()))
                .thenReturn(new RegistrationResponse(
                        "reg-f4", "table-f4", "Test table", "player-4", "P4", "Candidate", null, null, null, null, List.of(), null, null, null));

        RegistrationResponse response =
                registrationService.apply("table-f4", "player-4", new CreateRegistrationRequest(null, List.of()));

        assertThat(response.status()).isEqualTo("Candidate");
        verify(fileService, never()).requireAttachable(anyString(), anyString());
        verify(registrationFileRepository, never()).save(any());
    }

    private static org.mockito.stubbing.Answer<TableRegistration> persistRegistration(String id) {
        return invocation -> {
            TableRegistration registration = invocation.getArgument(0);
            ReflectionTestUtils.setField(registration, "id", id);
            ReflectionTestUtils.setField(registration, "createdAt", LocalDateTime.now());
            return registration;
        };
    }

    private static StoredFile persistedFile(String id, User owner) {
        StoredFile file = new StoredFile("ficha.pdf", "key-" + id, "hash-" + id, "application/pdf", 120, FileType.Private, owner);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "createdAt", LocalDateTime.now());
        return file;
    }

    // ------------------------------------------------------------------ the veto

    /** An admin's approval vetoes: the row moves to {@code Blocked}, and the trail keeps the reason. */
    @Test
    void anApprovedVetoBlocksAndTheTrailKeepsTheReason() {
        TableRegistration registration = persistedRegistration("reg-b1", "table-b1", TableRegistrationStatus.Player, null);
        when(registrationRepository.findById("reg-b1")).thenReturn(Optional.of(registration));
        when(userService.getById("admin-1")).thenReturn(persistedUser("admin-1"));
        when(registrationMapper.toResponse(eq(registration), any()))
                .thenReturn(new RegistrationResponse("reg-b1", "table-b1", "Test table", "player-1", "P1",
                        "Blocked", null, null, null, null, List.of(), null, null, null));

        RegistrationResponse response =
                registrationService.applyApprovedBlock("reg-b1", "admin-1", "Missed three sessions without a word");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Blocked);
        ArgumentCaptor<RegistrationStatusChange> change = ArgumentCaptor.forClass(RegistrationStatusChange.class);
        verify(statusChangeRepository).save(change.capture());
        assertThat(change.getValue().getFromStatus()).isEqualTo(TableRegistrationStatus.Player);
        assertThat(change.getValue().getToStatus()).isEqualTo(TableRegistrationStatus.Blocked);
        assertThat(change.getValue().getJustification()).isEqualTo("Missed three sessions without a word");
        // And the master sees who vetoed and why: a veto that vanishes from the screen is not reversible.
        assertThat(response.blockedByName()).isEqualTo("name-admin-1");
        assertThat(response.blockJustification()).isEqualTo("Missed three sessions without a word");
    }

    /** Vetoing somebody already vetoed is a 409 with its own code - two approvals landing at once. */
    @Test
    void vetoingSomebodyAlreadyVetoedIs409WithItsOwnCode() {
        TableRegistration registration = persistedRegistration("reg-b3", "table-b3", TableRegistrationStatus.Blocked, null);
        when(registrationRepository.findById("reg-b3")).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> registrationService.applyApprovedBlock("reg-b3", "admin-1", "again"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConflictException.REGISTRATION_ALREADY_BLOCKED);
    }

    /** Only a player can be vetoed: approving a veto on an application that is not one is a 409 and moves nothing. */
    @ParameterizedTest
    @EnumSource(value = TableRegistrationStatus.class, names = {"Candidate", "Rejected", "Deleted"})
    void approvingAVetoOnSomebodyWhoIsNoLongerAPlayerIs409(TableRegistrationStatus status) {
        TableRegistration registration = persistedRegistration("reg-nb", "table-nb", status, null);
        when(registrationRepository.findById("reg-nb")).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> registrationService.applyApprovedBlock("reg-nb", "admin-1", "too late"))
                .isInstanceOf(ConflictException.class);

        assertThat(registration.getStatus()).isEqualTo(status);
        verify(statusChangeRepository, never()).save(any());
    }

    /** Asking for a veto is refused the same way, before anything is written: a candidate is rejected, not vetoed. */
    @ParameterizedTest
    @EnumSource(value = TableRegistrationStatus.class, names = {"Candidate", "Rejected", "Deleted"})
    void aVetoCanOnlyBeAskedForAPlayer(TableRegistrationStatus status) {
        when(registrationRepository.findById("reg-nb2"))
                .thenReturn(Optional.of(persistedRegistration("reg-nb2", "table-nb2", status, null)));

        assertThatThrownBy(() -> registrationService.requireBlockableOn("table-nb2", "reg-nb2"))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isNotEqualTo(ConflictException.REGISTRATION_ALREADY_BLOCKED);
    }

    /** The «already vetoed» code is only for somebody who is. */
    @Test
    void askingForAVetoOnSomebodyAlreadyVetoedKeepsItsOwnCode() {
        when(registrationRepository.findById("reg-nb4"))
                .thenReturn(Optional.of(persistedRegistration("reg-nb4", "table-nb4", TableRegistrationStatus.Blocked, null)));

        assertThatThrownBy(() -> registrationService.requireBlockableOn("table-nb4", "reg-nb4"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConflictException.REGISTRATION_ALREADY_BLOCKED);
    }

    @Test
    void aPlayerCanBeAskedAVetoFor() {
        when(registrationRepository.findById("reg-nb3"))
                .thenReturn(Optional.of(persistedRegistration("reg-nb3", "table-nb3", TableRegistrationStatus.Player, null)));

        registrationService.requireBlockableOn("table-nb3", "reg-nb3");
    }

    /**
     * <b>Lifting the veto puts the person back where they were, read from the trail</b>. Only players are
     * vetoed now, but a veto an older version wrote against a {@code Candidate} still lifts back to
     * {@code Candidate}.
     */
    @Test
    void liftingTheVetoPutsThePersonBackWhereTheyWere() {
        TableRegistration registration = persistedRegistration("reg-b4", "table-b4", TableRegistrationStatus.Blocked, null);
        when(registrationRepository.findById("reg-b4")).thenReturn(Optional.of(registration));
        when(masterService.isPrimaryOf("table-b4", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(persistedUser("primary-1"));
        vetoedFrom(registration, TableRegistrationStatus.Candidate);
        when(registrationMapper.toResponse(eq(registration), any()))
                .thenReturn(new RegistrationResponse("reg-b4", "table-b4", "Test table", "player-1", "P1",
                        "Candidate", null, null, null, null, List.of(), null, null, null));

        registrationService.unblock("table-b4", "reg-b4", "primary-1", "We talked it through and it is settled");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Candidate);
        ArgumentCaptor<RegistrationStatusChange> change = ArgumentCaptor.forClass(RegistrationStatusChange.class);
        verify(statusChangeRepository).save(change.capture());
        assertThat(change.getValue().getToStatus()).isEqualTo(TableRegistrationStatus.Candidate);
        assertThat(change.getValue().getJustification()).isEqualTo("We talked it through and it is settled");
    }

    /** An admin decided the veto, so an admin can lift it too. */
    @Test
    void anAdminCanLiftAVeto() {
        TableRegistration registration = persistedRegistration("reg-b7", "table-b7", TableRegistrationStatus.Blocked, null);
        when(registrationRepository.findById("reg-b7")).thenReturn(Optional.of(registration));
        when(userService.loadAuthSnapshot("admin-1")).thenReturn(new UserAuthSnapshot("admin-1", UserStatus.Allowed, Set.of("Admin")));
        when(userService.getById("admin-1")).thenReturn(persistedUser("admin-1"));
        vetoedFrom(registration, TableRegistrationStatus.Player);

        registrationService.unblock("table-b7", "reg-b7", "admin-1", "Settled with the table");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Player);
    }

    /** A co-master asks for vetoes but does not lift them: 403 with its own code, and nothing moves. */
    @Test
    void aSecondaryCannotLiftAVetoAndGets403WithItsOwnCode() {
        TableRegistration registration = persistedRegistration("reg-b2", "table-b2", TableRegistrationStatus.Blocked, null);
        when(userService.loadAuthSnapshot("secondary-1"))
                .thenReturn(new UserAuthSnapshot("secondary-1", UserStatus.Allowed, Set.of("Master")));

        assertThatThrownBy(() -> registrationService.unblock("table-b2", "reg-b2", "secondary-1", "just because"))
                .isInstanceOf(ForbiddenActionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ForbiddenActionException.NOT_PRIMARY_MASTER);

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Blocked);
        verify(statusChangeRepository, never()).save(any());
    }

    /**
     * Who is asked before which row: somebody who does not run the table gets the same 403 for an
     * application of another table as for one of this table, so a 404 cannot tell them which belong here.
     */
    @Test
    void liftingAVetoChecksWhoIsAskingBeforeLookingTheApplicationUp() {
        persistedRegistration("reg-here", "table-b9", TableRegistrationStatus.Blocked, null);
        when(userService.loadAuthSnapshot("outsider-1"))
                .thenReturn(new UserAuthSnapshot("outsider-1", UserStatus.Allowed, Set.of("Master")));

        assertThatThrownBy(() -> registrationService.unblock("table-b9", "reg-elsewhere", "outsider-1", "just because"))
                .isInstanceOf(ForbiddenActionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ForbiddenActionException.NOT_PRIMARY_MASTER);

        InOrder order = inOrder(gameTableRepository, masterService);
        order.verify(gameTableRepository).findByIdForUpdate("table-b9");
        order.verify(masterService).isPrimaryOf("table-b9", "outsider-1");
        verify(registrationRepository, never()).findById(anyString());
    }

    /**
     * <b>A person whose seat was taken while vetoed comes back as a rejected application</b>, with the
     * system's reason: lifting the veto never fails for lack of room and never pushes the table over its
     * cap. The same holds whether they were a player or a candidate.
     */
    @ParameterizedTest
    @EnumSource(value = TableRegistrationStatus.class, names = {"Player", "Candidate"})
    void liftingTheVetoAtAFullTableLeavesARejectionForLackOfSeats(TableRegistrationStatus before) {
        TableRegistration registration = persistedRegistration("reg-b6", "table-b6", TableRegistrationStatus.Blocked, 1);
        when(registrationRepository.findById("reg-b6")).thenReturn(Optional.of(registration));
        when(masterService.isPrimaryOf("table-b6", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(persistedUser("primary-1"));
        vetoedFrom(registration, before);
        when(registrationRepository.countByGameTable_IdAndStatus("table-b6", TableRegistrationStatus.Player)).thenReturn(1L);

        registrationService.unblock("table-b6", "reg-b6", "primary-1", "The veto was a mistake");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        ArgumentCaptor<RegistrationRejection> rejection = ArgumentCaptor.forClass(RegistrationRejection.class);
        verify(rejectionRepository).save(rejection.capture());
        assertThat(rejection.getValue().getDescription()).isEqualTo("TABLE_FULL");
        assertThat(rejection.getValue().getRejectedBy()).isNull();
        ArgumentCaptor<RegistrationStatusChange> change = ArgumentCaptor.forClass(RegistrationStatusChange.class);
        verify(statusChangeRepository).save(change.capture());
        assertThat(change.getValue().getToStatus()).isEqualTo(TableRegistrationStatus.Rejected);
    }

    /** A player coming back into the last seat closes the table to the rest, exactly as accepting would. */
    @Test
    void liftingTheVetoThatFillsTheLastSeatTurnsDownTheRemainingCandidates() {
        TableRegistration registration = persistedRegistration("reg-b8", "table-b8", TableRegistrationStatus.Blocked, 1);
        GameTable table = registration.getGameTable();
        table.setStatus(GameTableStatus.InProgress);
        table.setSeekingPlayers(true);
        TableRegistration waiting = new TableRegistration(table, persistedUser("player-2"), null);
        ReflectionTestUtils.setField(waiting, "id", "reg-b9");
        when(registrationRepository.findById("reg-b8")).thenReturn(Optional.of(registration));
        when(masterService.isPrimaryOf("table-b8", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(persistedUser("primary-1"));
        vetoedFrom(registration, TableRegistrationStatus.Player);
        when(registrationRepository.countByGameTable_IdAndStatus("table-b8", TableRegistrationStatus.Player)).thenReturn(0L, 1L);
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("table-b8", TableRegistrationStatus.Candidate))
                .thenReturn(List.of(waiting));

        registrationService.unblock("table-b8", "reg-b8", "primary-1", "Back in");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Player);
        assertThat(waiting.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        assertThat(table.isSeekingPlayers()).isFalse();
    }

    /** Lifting a veto that is not there is a 409 with its own code. */
    @Test
    void liftingAVetoThatIsNotThereIs409WithItsOwnCode() {
        TableRegistration registration = persistedRegistration("reg-b5", "table-b5", TableRegistrationStatus.Player, null);
        when(registrationRepository.findById("reg-b5")).thenReturn(Optional.of(registration));
        when(masterService.isPrimaryOf("table-b5", "primary-1")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.unblock("table-b5", "reg-b5", "primary-1", "just in case"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConflictException.REGISTRATION_NOT_BLOCKED);
    }

    /** The trail row of the veto being lifted, saying what the person was before it. */
    private void vetoedFrom(TableRegistration registration, TableRegistrationStatus before) {
        when(statusChangeRepository.findFirstByRegistration_IdAndToStatusOrderByCreatedAtDesc(
                        registration.getId(), TableRegistrationStatus.Blocked))
                .thenReturn(Optional.of(new RegistrationStatusChange(
                        registration, before, TableRegistrationStatus.Blocked, persistedUser("admin-1"), "vetoed")));
    }

    /**
     * <b>Applying to the same table again is impossible without any rule of its own.</b> It is the
     * proof the single point works: nobody had to remember this endpoint - it goes through
     * {@code requireVisible} like everything else and answers 404 (ADR-09 §4).
     */
    @Test
    void aVetoedPersonCannotApplyAgainAndGets404() {
        GameTable table = persistedTable("table-b6", GameTableStatus.Opened, null);
        // The table is there and its lock is taken: what refuses is the veto, not a 404 for a missing
        // table. Both stubs have to be here or the test would pass for the wrong reason.
        when(gameTableRepository.findByIdForUpdate("table-b6")).thenReturn(Optional.of(table));
        when(gameTableRepository.findById("table-b6")).thenReturn(Optional.of(table));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-b6", "vetado", List.of(TableRegistrationStatus.Blocked)))
                .thenReturn(true);

        assertThatThrownBy(() -> registrationService.apply(
                        "table-b6", "vetado", new CreateRegistrationRequest(null, List.of())))
                .isInstanceOf(NotFoundException.class)
                .isNotInstanceOf(ForbiddenActionException.class);

        verify(registrationRepository, never()).save(any());
    }

    // -------------------------------------- the side leaks the contract decided about (§1.C)

    /**
     * <b>{@code /registrations/mine} excludes {@code Blocked} just like {@code Deleted}.</b> ADR-09 §4 says
     * the vetoed person «no ve esa mesa», and this row carries {@code gameTableName}: leaving it in
     * would be the one crack through which they keep seeing it, with the status beside it explaining
     * why.
     */
    @Test
    void myApplicationsDoNotShowTheVetoedOnes() {
        Pageable pageable = PageRequest.of(0, 20);
        when(registrationRepository.findByUser_IdAndStatusNotIn(
                        "player-1",
                        List.of(TableRegistrationStatus.Deleted, TableRegistrationStatus.Blocked),
                        pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        assertThat(registrationService.listMine("player-1", pageable).content()).isEmpty();
    }

    /**
     * <b>And the vetoed row does stay visible on the master's side</b>, with who vetoed it, when and
     * why. It is what makes the veto reversible in practice: the button that lifts it has to live
     * somewhere.
     */
    @Test
    void theMasterStillSeesTheVetoedRowWithWhoVetoedItAndWhy() {
        GameTable table = persistedTable("table-b7", GameTableStatus.InProgress, null);
        TableRegistration player = new TableRegistration(table, persistedUser("player-1"), null);
        ReflectionTestUtils.setField(player, "id", "reg-ok");
        player.setStatus(TableRegistrationStatus.Player);
        TableRegistration blocked = new TableRegistration(table, persistedUser("player-2"), null);
        ReflectionTestUtils.setField(blocked, "id", "reg-veto");
        blocked.setStatus(TableRegistrationStatus.Blocked);

        when(masterService.canOversee("table-b7", "master-1")).thenReturn(true);
        when(registrationRepository.findByGameTable_IdAndStatusInOrderByCreatedAtAsc(
                        "table-b7", List.of(TableRegistrationStatus.Player, TableRegistrationStatus.Blocked)))
                .thenReturn(List.of(player, blocked));
        when(statusChangeRepository.findByRegistration_IdInAndToStatusOrderByCreatedAtAsc(
                        List.of("reg-veto"), TableRegistrationStatus.Blocked))
                .thenReturn(List.of(persistedChange(
                        blocked, TableRegistrationStatus.Player, TableRegistrationStatus.Blocked, "Missed a session without a word")));

        List<TablePlayerResponse> roster = registrationService.listPlayersForTable("table-b7", "master-1");

        assertThat(roster).extracting(TablePlayerResponse::status).containsExactly("Player", "Blocked");
        assertThat(roster.get(1).registrationId()).isEqualTo("reg-veto");
        assertThat(roster.get(1).blockedByName()).isEqualTo("name-primary-1");
        assertThat(roster.get(1).blockJustification()).isEqualTo("Missed a session without a word");
        assertThat(roster.get(1).blockedAt()).isNotNull();
        // A veto with no approved request behind it carries only the decision's side.
        assertThat(roster.get(1).banRequestedByName()).isNull();
        assertThat(roster.get(1).banRequestJustification()).isNull();
        // And a row that is not vetoed invents nothing.
        assertThat(roster.get(0).blockedByName()).isNull();
    }

    /**
     * <b>A veto that went through a request shows both reasons</b>: the admin's note that decided it
     * and, from the latest approved request, the master who asked and why.
     */
    @Test
    void theVetoedRowCarriesTheMastersRequestNextToTheAdminsDecision() {
        GameTable table = persistedTable("table-b8", GameTableStatus.InProgress, null);
        TableRegistration player = new TableRegistration(table, persistedUser("player-1"), null);
        ReflectionTestUtils.setField(player, "id", "reg-ok");
        player.setStatus(TableRegistrationStatus.Player);
        TableRegistration blocked = new TableRegistration(table, persistedUser("player-2"), null);
        ReflectionTestUtils.setField(blocked, "id", "reg-veto");
        blocked.setStatus(TableRegistrationStatus.Blocked);

        when(masterService.canOversee("table-b8", "master-1")).thenReturn(true);
        when(registrationRepository.findByGameTable_IdAndStatusInOrderByCreatedAtAsc(
                        "table-b8", List.of(TableRegistrationStatus.Player, TableRegistrationStatus.Blocked)))
                .thenReturn(List.of(player, blocked));
        when(statusChangeRepository.findByRegistration_IdInAndToStatusOrderByCreatedAtAsc(
                        List.of("reg-veto"), TableRegistrationStatus.Blocked))
                .thenReturn(List.of(persistedChange(
                        blocked, TableRegistrationStatus.Player, TableRegistrationStatus.Blocked, "Confirmed by the admin")));
        // Oldest first: the earlier approved request is superseded by the later one.
        when(approvalRequestRepository.findByRequestTypeAndStatusAndEntityIdInOrderByCreatedAtAsc(
                        ApprovalRequestType.PlayerBan, ApprovalStatus.Approved, List.of("reg-veto")))
                .thenReturn(List.of(
                        new ApprovalRequest(ApprovalRequestType.PlayerBan, "table_registration", "reg-veto",
                                persistedUser("old-master"), "An old reason"),
                        new ApprovalRequest(ApprovalRequestType.PlayerBan, "table_registration", "reg-veto",
                                persistedUser("secondary-1"), "Harassed another player")));

        List<TablePlayerResponse> roster = registrationService.listPlayersForTable("table-b8", "master-1");

        assertThat(roster.get(1).blockJustification()).isEqualTo("Confirmed by the admin");
        assertThat(roster.get(1).banRequestedByName()).isEqualTo("name-secondary-1");
        assertThat(roster.get(1).banRequestJustification()).isEqualTo("Harassed another player");
        assertThat(roster.get(0).banRequestedByName()).isNull();
        assertThat(roster.get(0).banRequestJustification()).isNull();
    }

    /** A trail row as it comes back from the database: with its id and its instant already stamped. */
    private RegistrationStatusChange persistedChange(
            TableRegistration registration,
            TableRegistrationStatus from,
            TableRegistrationStatus to,
            String justification) {
        RegistrationStatusChange change =
                new RegistrationStatusChange(registration, from, to, persistedUser("primary-1"), justification);
        ReflectionTestUtils.setField(change, "id", "rsc-" + registration.getId());
        ReflectionTestUtils.setField(change, "createdAt", java.time.LocalDateTime.now());
        return change;
    }

    private GameTable persistedTable(String id, GameTableStatus status, Integer maxPlayers) {
        GameTable table = new GameTable("Test table", persistedUser("creator-of-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setStatus(status);
        table.setMaxPlayers(maxPlayers);
        tablesById.put(id, table);
        return table;
    }

    private User persistedUser(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private TableRegistration persistedRegistration(String id, String tableId, TableRegistrationStatus status, Integer maxPlayers) {
        GameTable table = persistedTable(tableId, GameTableStatus.Opened, maxPlayers);
        TableRegistration registration = new TableRegistration(table, persistedUser("player-1"), null);
        ReflectionTestUtils.setField(registration, "id", id);
        registration.setStatus(status);
        return registration;
    }

    private static RegistrationRejection rejectionAt(TableRegistration registration, String description, User rejectedBy) {
        RegistrationRejection rejection = new RegistrationRejection(registration, description, rejectedBy);
        ReflectionTestUtils.setField(rejection, "rejectedAt", LocalDateTime.of(2026, 10, 1, 12, 0));
        return rejection;
    }
}
