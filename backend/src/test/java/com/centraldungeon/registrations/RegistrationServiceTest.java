package com.centraldungeon.registrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.RejectRegistrationRequest;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.CommittedTable;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.ScheduleConflictService;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private RegistrationRejectionRepository rejectionRepository;

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

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                registrationRepository, rejectionRepository, gameTableRepository, masterService, userService, notificationService,
                scheduleConflictService, registrationMapper);
    }

    @Test
    void rejectsApplicationsFromSomeoneWithoutThePlayerRole() {
        GameTable table = persistedTable("table-1", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-1")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("master-only")).thenReturn(new UserAuthSnapshot("master-only", UserStatus.Allowed, Set.of("Master")));

        assertThatThrownBy(() -> registrationService.apply("table-1", "master-only", new CreateRegistrationRequest(null)))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void rejectsApplicationsFromAMasterOfThatSameTable() {
        GameTable table = persistedTable("table-1b", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-1b")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("master-player-1"))
                .thenReturn(new UserAuthSnapshot("master-player-1", UserStatus.Allowed, Set.of("Player", "Master")));
        when(masterService.isMasterOf("table-1b", "master-player-1")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.apply("table-1b", "master-player-1", new CreateRegistrationRequest(null)))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void rejectsApplicationsToATableThatIsNotOpen() {
        GameTable table = persistedTable("table-2", GameTableStatus.Preparation, null);
        when(gameTableRepository.findByIdForUpdate("table-2")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));

        assertThatThrownBy(() -> registrationService.apply("table-2", "player-1", new CreateRegistrationRequest(null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsASecondActiveApplicationForTheSamePair() {
        GameTable table = persistedTable("table-3", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-3")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                        "table-3", "player-1", List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player)))
                .thenReturn(true);

        assertThatThrownBy(() -> registrationService.apply("table-3", "player-1", new CreateRegistrationRequest(null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptsAValidApplication() {
        GameTable table = persistedTable("table-4", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-4")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-1")).thenReturn(persistedUser("player-1"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toResponse(any(TableRegistration.class)))
                .thenReturn(new RegistrationResponse("reg-1", "table-4", "Test table", "player-1", "P1", 8000, "Candidate", null, null, null, null));

        RegistrationResponse response = registrationService.apply("table-4", "player-1", new CreateRegistrationRequest("please"));

        assertThat(response.status()).isEqualTo("Candidate");
    }

    @Test
    void applyNotifiesEveryMasterOfTheTable() {
        GameTable table = persistedTable("table-4b", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-4b")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(userService.getById("player-1")).thenReturn(persistedUser("player-1"));
        when(registrationRepository.save(any(TableRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toResponse(any(TableRegistration.class)))
                .thenReturn(new RegistrationResponse("reg-1b", "table-4b", "Test table", "player-1", "P1", 8000, "Candidate", null, null, null, null));
        com.centraldungeon.tables.Master primary =
                new com.centraldungeon.tables.Master(table, persistedUser("master-1"), com.centraldungeon.tables.MasterType.Primary);
        com.centraldungeon.tables.Master secondary =
                new com.centraldungeon.tables.Master(table, persistedUser("master-2"), com.centraldungeon.tables.MasterType.Secondary);
        when(masterService.findByGameTable("table-4b")).thenReturn(List.of(primary, secondary));

        registrationService.apply("table-4b", "player-1", new CreateRegistrationRequest("please"));

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
        when(registrationMapper.toResponse(registration))
                .thenReturn(new RegistrationResponse("reg-3", "table-7", "Test table", "player-1", "P1", 8000, "Player", null, null, null, null));

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
        when(registrationRepository.countByGameTable_IdAndStatus("table-8", TableRegistrationStatus.Player)).thenReturn(1L);
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc("table-8", TableRegistrationStatus.Candidate))
                .thenReturn(List.of(otherCandidate));
        when(registrationMapper.toResponse(accepted))
                .thenReturn(new RegistrationResponse("reg-4", "table-8", "Test table", "player-1", "P1", 8000, "Player", null, null, null, null));

        registrationService.accept("reg-4", "master-1");

        assertThat(accepted.getStatus()).isEqualTo(TableRegistrationStatus.Player);
        assertThat(otherCandidate.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        verify(rejectionRepository).save(any(RegistrationRejection.class));
        verify(notificationService).notifyRegistrationRejected("player-2", table);
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
        when(registrationMapper.toResponse(registration))
                .thenReturn(new RegistrationResponse("reg-7", "table-10", "Test table", "player-1", "P1", 8000, "Rejected", null, null, null, null));

        registrationService.reject("reg-7", "master-1", new RejectRegistrationRequest("Not a fit"));

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        verify(rejectionRepository).save(any(RegistrationRejection.class));
        verify(notificationService).notifyRegistrationRejected(registration.getUser().getId(), registration.getGameTable());
    }

    /**
     * #197: what a master wrote is their own words and travels verbatim. What the application wrote
     * is a code, so it can be read in whichever language the applicant chose.
     */
    @Test
    void listMineShowsAMasterJustificationVerbatim() {
        TableRegistration registration = persistedRegistration("reg-8", "table-12", TableRegistrationStatus.Rejected, null);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(registrationRepository.findByUser_IdAndStatusNot("player-1", TableRegistrationStatus.Deleted, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(registration)));
        when(registrationMapper.toResponse(registration))
                .thenReturn(new RegistrationResponse("reg-8", "table-12", "Test table", "player-1", "P1", 8000, "Rejected", null, null, null, null));
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
        when(registrationRepository.findByUser_IdAndStatusNot("player-1", TableRegistrationStatus.Deleted, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(registration)));
        when(registrationMapper.toResponse(registration))
                .thenReturn(new RegistrationResponse("reg-9", "table-13", "Test table", "player-1", "P1", 8000, "Rejected", null, null, null, null));
        // rejected_by null is what marks the rejection as the system's own (#34).
        RegistrationRejection rejection = new RegistrationRejection(registration, "TABLE_FULL", null);
        when(rejectionRepository.findByRegistration_IdIn(List.of("reg-9"))).thenReturn(List.of(rejection));

        var result = registrationService.listMine("player-1", pageable);

        assertThat(result.content().get(0).rejectionReasonCode()).isEqualTo("TABLE_FULL");
        assertThat(result.content().get(0).rejectionJustification()).isNull();
    }

    @Test
    void listCandidatesForTableRequiresBeingAMaster() {
        when(masterService.isMasterOf("table-11", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> registrationService.listCandidatesForTable(
                        "table-11", "stranger", org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void applyThrowsWhenTheTableDoesNotExist() {
        when(gameTableRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.apply("missing", "player-1", new CreateRegistrationRequest(null)))
                .isInstanceOf(NotFoundException.class);
    }

    /** R2 (#178): a table where you already play is a real commitment, so applying elsewhere at that hour is refused. */
    @Test
    void refusesAnApplicationThatClashesWithATableTheApplicantAlreadyPlaysAt() {
        GameTable table = persistedTable("table-r2", GameTableStatus.Opened, null);
        when(gameTableRepository.findByIdForUpdate("table-r2")).thenReturn(Optional.of(table));
        when(userService.loadAuthSnapshot("player-1")).thenReturn(new UserAuthSnapshot("player-1", UserStatus.Allowed, Set.of("Player")));
        when(registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(anyString(), anyString(), any())).thenReturn(false);
        when(scheduleConflictService.findClashWith("player-1", table)).thenReturn(new CommittedTable("other", "La cripta"));

        assertThatThrownBy(() -> registrationService.apply("table-r2", "player-1", new CreateRegistrationRequest(null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("La cripta");

        verify(registrationRepository, never()).save(any(TableRegistration.class));
    }

    /** R3 (#178): asked again at accept time, because the candidate may have been taken elsewhere in between. */
    @Test
    void refusesToAcceptACandidateWhoNowPlaysAtAClashingTable() {
        TableRegistration registration = persistedRegistration("reg-r3", "table-r3", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-r3")).thenReturn(Optional.of(registration));
        when(gameTableRepository.findByIdForUpdate("table-r3")).thenReturn(Optional.of(registration.getGameTable()));
        when(masterService.isMasterOf("table-r3", "master-1")).thenReturn(true);
        when(scheduleConflictService.findClashWith("player-1", registration.getGameTable()))
                .thenReturn(new CommittedTable("other", "El faro"));

        assertThatThrownBy(() -> registrationService.accept("reg-r3", "master-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("El faro");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Candidate);
    }

    /**
     * R4 (#178): the other pending applications that now clash are <b>notified</b>, not rejected.
     * Until somebody is accepted there is no commitment, and choosing between them is theirs (#70).
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
        when(registrationMapper.toResponse(any(TableRegistration.class)))
                .thenReturn(new RegistrationResponse("reg-r4", "table-r4", "Test table", "player-1", "P1", 8000, "Player", null, null, null, null));

        registrationService.accept("reg-r4", "master-1");

        verify(notificationService).notifyScheduleConflict("player-1", pendingElsewhere.getGameTable(), acceptedTable.getName());
        assertThat(pendingElsewhere.getStatus()).isEqualTo(TableRegistrationStatus.Candidate);
    }

    /** The way out R4's notice needs to leave open (#178): withdrawing your own pending application. */
    @Test
    void withdrawMarksTheOwnPendingApplicationAsDeleted() {
        TableRegistration registration = persistedRegistration("reg-w1", "table-w1", TableRegistrationStatus.Candidate, null);
        when(registrationRepository.findById("reg-w1")).thenReturn(Optional.of(registration));

        registrationService.withdraw("reg-w1", "player-1");

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Deleted);
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

    private GameTable persistedTable(String id, GameTableStatus status, Integer maxPlayers) {
        GameTable table = new GameTable("Test table", persistedUser("creator-of-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setStatus(status);
        table.setMaxPlayers(maxPlayers);
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
}
