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
import com.centraldungeon.tables.MasterService;
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
    private RegistrationMapper registrationMapper;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                registrationRepository, rejectionRepository, gameTableRepository, masterService, userService, notificationService,
                registrationMapper);
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
                .thenReturn(new RegistrationResponse("reg-1", "table-4", "player-1", "P1", 8000, "Candidate", null, null));

        RegistrationResponse response = registrationService.apply("table-4", "player-1", new CreateRegistrationRequest("please"));

        assertThat(response.status()).isEqualTo("Candidate");
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
                .thenReturn(new RegistrationResponse("reg-3", "table-7", "player-1", "P1", 8000, "Player", null, null));

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
                .thenReturn(new RegistrationResponse("reg-4", "table-8", "player-1", "P1", 8000, "Player", null, null));

        registrationService.accept("reg-4", "master-1");

        assertThat(accepted.getStatus()).isEqualTo(TableRegistrationStatus.Player);
        assertThat(otherCandidate.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        verify(rejectionRepository).save(any(RegistrationRejection.class));
        verify(notificationService).notifyRegistrationRejected("player-2", table, "Mesa llena");
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
                .thenReturn(new RegistrationResponse("reg-7", "table-10", "player-1", "P1", 8000, "Rejected", null, null));

        registrationService.reject("reg-7", "master-1", new RejectRegistrationRequest("Not a fit"));

        assertThat(registration.getStatus()).isEqualTo(TableRegistrationStatus.Rejected);
        verify(rejectionRepository).save(any(RegistrationRejection.class));
        verify(notificationService).notifyRegistrationRejected(registration.getUser().getId(), registration.getGameTable(), "Not a fit");
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
