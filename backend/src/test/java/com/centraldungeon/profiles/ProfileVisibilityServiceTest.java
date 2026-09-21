package com.centraldungeon.profiles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.settings.SettingKey;
import com.centraldungeon.settings.SettingsService;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterRowStatus;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The five rules of "Visibilidad de perfiles" (modelo-datos.md §5), one case per rule and its
 * negative, plus the two-week window of #44 and the row-status guard of #216.
 */
@ExtendWith(MockitoExtension.class)
class ProfileVisibilityServiceTest {

    @Mock
    private MasterRepository masterRepository;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private GameTableRepository gameTableRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    /**
     * The visibility window, a setting since F3.5 (#44, #141) and a constant of the service before
     * that. Stubbed to the same fourteen days, so every case below keeps asserting #44's rule
     * against #44's number - what moved is where the number comes from.
     */
    @Mock
    private SettingsService settingsService;

    private ProfileVisibilityService service;

    private ProfileVisibilityService service() {
        if (service == null) {
            lenient().when(settingsService.profileVisibilityWindowDays())
                    .thenReturn(SettingKey.PROFILES_VISIBILITY_WINDOW_DAYS.defaultValue());
            service = new ProfileVisibilityService(
                    masterRepository, registrationRepository, gameTableRepository, userRoleRepository, settingsService);
        }
        return service;
    }

    @Test
    void ownProfileIsAlwaysVisible() {
        assertThatCode(() -> service().requireVisible("same-person", "same-person")).doesNotThrowAnyException();
    }

    /** #45: nothing narrows an admin's view. */
    @Test
    void anAdminSeesAnyProfile() {
        when(userRoleRepository.findActiveRoleNames("admin-1")).thenReturn(Set.of("Admin"));

        assertThatCode(() -> service().requireVisible("nobody-related", "admin-1")).doesNotThrowAnyException();
    }

    /** #41a: a master's profile is open to anybody with a session while their table is public. */
    @Test
    void aPlayerSeesTheMasterOfAnOpenedTable() {
        GameTable table = table("table-1", GameTableStatus.Opened, null);
        when(masterRepository.findByUser_Id("master-1")).thenReturn(List.of(masterRow(table, "master-1")));

        assertThatCode(() -> service().requireVisible("master-1", "player-1")).doesNotThrowAnyException();
    }

    /** The negative of #41a: a table nobody outside it could ever look at grants nothing. */
    @Test
    void aPlayerDoesNotSeeTheMasterOfATableStillInDraft() {
        GameTable table = table("table-2", GameTableStatus.Draft, null);
        when(masterRepository.findByUser_Id("master-2")).thenReturn(List.of(masterRow(table, "master-2")));

        assertThatThrownBy(() -> service().requireVisible("master-2", "player-1")).isInstanceOf(NotFoundException.class);
    }

    /** #41b: literal - the master sees the candidate the moment the application arrives, unaccepted. */
    @Test
    void aMasterSeesACandidateWhoAppliedWithoutHavingBeenAccepted() {
        GameTable table = table("table-3", GameTableStatus.Opened, null);
        when(masterRepository.findLiveByUser("master-3", MasterRowStatus.Created)).thenReturn(List.of(masterRow(table, "master-3")));
        when(registrationRepository.findByGameTable_IdInAndUser_IdAndStatusIn(
                        Set.of("table-3"), "candidate-1", List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player)))
                .thenReturn(List.of(registration(table, "candidate-1", TableRegistrationStatus.Candidate)));

        assertThatCode(() -> service().requireVisible("candidate-1", "master-3")).doesNotThrowAnyException();
    }

    /** The negative of #41b: a master sees only the people who actually applied to their tables. */
    @Test
    void aMasterDoesNotSeeAStrangerWhoNeverApplied() {
        GameTable table = table("table-4", GameTableStatus.Opened, null);
        when(masterRepository.findLiveByUser("master-4", MasterRowStatus.Created)).thenReturn(List.of(masterRow(table, "master-4")));

        assertThatThrownBy(() -> service().requireVisible("stranger", "master-4")).isInstanceOf(NotFoundException.class);
    }

    /** #47: two live Players of the same table see each other. */
    @Test
    void twoPlayersOfTheSameTableSeeEachOther() {
        GameTable table = table("table-5", GameTableStatus.InProgress, null);
        when(registrationRepository.findByUser_IdAndStatus("player-a", TableRegistrationStatus.Player))
                .thenReturn(List.of(registration(table, "player-a", TableRegistrationStatus.Player)));
        when(registrationRepository.findByUser_IdAndStatus("player-b", TableRegistrationStatus.Player))
                .thenReturn(List.of(registration(table, "player-b", TableRegistrationStatus.Player)));
        when(gameTableRepository.findAllById(Set.of("table-5"))).thenReturn(List.of(table));

        assertThatCode(() -> service().requireVisible("player-b", "player-a")).doesNotThrowAnyException();
    }

    /** #44: two weeks and a day after closed_at, the link that used to grant visibility no longer does. */
    @Test
    void visibilityIsGoneTwoWeeksAndADayAfterClosedAt() {
        LocalDateTime closedAt = LocalDateTime.now().minusDays(14).minusDays(1);
        GameTable table = table("table-6", GameTableStatus.Finished, closedAt);
        when(masterRepository.findByUser_Id("master-6")).thenReturn(List.of(masterRow(table, "master-6")));

        assertThatThrownBy(() -> service().requireVisible("master-6", "player-1")).isInstanceOf(NotFoundException.class);
    }

    /** #44's companion fact: no special case for Pause, because its closed_at is null while it lives. */
    @Test
    void aPausedTableStaysVisibleEvenSixMonthsIn() {
        GameTable table = table("table-7", GameTableStatus.Pause, null);
        when(masterRepository.findByUser_Id("master-7")).thenReturn(List.of(masterRow(table, "master-7")));

        assertThatCode(() -> service().requireVisible("master-7", "player-1")).doesNotThrowAnyException();
    }

    /** #216: a deleted master row is a record, not a permission - it authorizes nothing here either. */
    @Test
    void aDeletedMasterRowGrantsNoVisibility() {
        GameTable table = table("table-8", GameTableStatus.Opened, null);
        Master deleted = masterRow(table, "master-8");
        deleted.setStatus(MasterRowStatus.Deleted);
        when(masterRepository.findByUser_Id("master-8")).thenReturn(List.of(deleted));

        assertThatThrownBy(() -> service().requireVisible("master-8", "player-1")).isInstanceOf(NotFoundException.class);
    }

    private static GameTable table(String id, GameTableStatus status, LocalDateTime closedAt) {
        GameTable table = new GameTable("Mesa " + id, user("creator-of-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setStatus(status);
        table.setClosedAt(closedAt);
        return table;
    }

    private static Master masterRow(GameTable table, String userId) {
        return new Master(table, user(userId), MasterType.Primary);
    }

    private static TableRegistration registration(GameTable table, String userId, TableRegistrationStatus status) {
        TableRegistration registration = new TableRegistration(table, user(userId), null);
        registration.setStatus(status);
        return registration;
    }

    private static User user(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
