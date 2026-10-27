package com.centraldungeon.tables;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.notifications.TableMembership;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.users.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Who hears that a table changed, and which notification each of them gets. */
@ExtendWith(MockitoExtension.class)
class TableChangeNotifierTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private MasterService masterService;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @InjectMocks
    private TableChangeNotifier notifier;

    @Test
    @DisplayName("when a master acts, the actor is left out and the co-masters and players are told")
    void aMasterActingTellsTheCoMastersAndThePlayersButNotThemselves() {
        GameTable table = table("t1", GameTableStatus.InProgress);
        masters(table, master(table, "primary-1", MasterType.Primary), master(table, "co-1", MasterType.Secondary));
        players(table, "player-1", "player-2");

        notifier.announceTransition(table, NotificationType.TableCanceled, "primary-1", null);

        verify(notificationService, never()).notifyTableChanged(eq("primary-1"), any(), any(), any());
        // A master who acts is a live master of the table, so the co-master hears the plain type.
        verify(notificationService).notifyTableChanged("co-1", table, NotificationType.TableCanceled, TableMembership.Master);
        verify(notificationService).notifyTableChanged("player-1", table, NotificationType.TableCanceled, TableMembership.Player);
        verify(notificationService).notifyTableChanged("player-2", table, NotificationType.TableCanceled, TableMembership.Player);
    }

    @Test
    @DisplayName("an admin who does not run the table gets the masters the ByAdmin variant and the players the plain one")
    void anAdminActingOnSomebodyElsesTableGivesTheMastersTheByAdminVariant() {
        GameTable table = table("t2", GameTableStatus.InProgress);
        masters(table, master(table, "primary-1", MasterType.Primary), master(table, "co-1", MasterType.Secondary));
        players(table, "player-1");

        notifier.announceTransition(table, NotificationType.TablePaused, "admin-1", null);

        verify(notificationService).notifyTableChanged("primary-1", table, NotificationType.TablePausedByAdmin, TableMembership.Master);
        verify(notificationService).notifyTableChanged("co-1", table, NotificationType.TablePausedByAdmin, TableMembership.Master);
        verify(notificationService).notifyTableChanged("player-1", table, NotificationType.TablePaused, TableMembership.Player);
    }

    @Test
    @DisplayName("an admin who also runs the table is the actor: the other masters hear the plain type")
    void anAdminWhoRunsTheTableGivesTheCoMastersThePlainVariant() {
        GameTable table = table("t3", GameTableStatus.Pause);
        masters(table, master(table, "admin-1", MasterType.Primary), master(table, "co-1", MasterType.Secondary));
        players(table);

        notifier.announceTransition(table, NotificationType.TableResumed, "admin-1", null);

        verify(notificationService).notifyTableChanged("co-1", table, NotificationType.TableResumed, TableMembership.Master);
        verify(notificationService, never()).notifyTableChanged(eq("admin-1"), any(), any(), any());
    }

    @Test
    @DisplayName("an approved pause does not tell the requester again, but tells their co-masters and the players")
    void anApprovedPauseSkipsTheRequester() {
        GameTable table = table("t4", GameTableStatus.Pause);
        masters(table, master(table, "primary-1", MasterType.Primary), master(table, "co-1", MasterType.Secondary));
        players(table, "player-1");

        notifier.announceTransition(table, NotificationType.TablePaused, "admin-1", "co-1");

        verify(notificationService, never()).notifyTableChanged(eq("co-1"), any(), any(), any());
        verify(notificationService).notifyTableChanged("primary-1", table, NotificationType.TablePausedByAdmin, TableMembership.Master);
        verify(notificationService).notifyTableChanged("player-1", table, NotificationType.TablePaused, TableMembership.Player);
    }

    @Test
    @DisplayName("finishing has no ByAdmin variant: the masters hear the plain type whoever acted")
    void finishingKeepsThePlainTypeForTheMasters() {
        GameTable table = table("t5", GameTableStatus.Finished);
        masters(table, master(table, "primary-1", MasterType.Primary), master(table, "co-1", MasterType.Secondary));
        players(table, "player-1");

        notifier.announceTransition(table, NotificationType.TableFinished, "primary-1", null);

        verify(notificationService).notifyTableChanged("co-1", table, NotificationType.TableFinished, TableMembership.Master);
        verify(notificationService).notifyTableChanged("player-1", table, NotificationType.TableFinished, TableMembership.Player);
    }

    @Test
    @DisplayName("somebody who is both a master and a player of the table is told once")
    void aPersonIsToldOnce() {
        GameTable table = table("t6", GameTableStatus.InProgress);
        masters(table, master(table, "co-1", MasterType.Secondary));
        players(table, "co-1");

        notifier.announceTransition(table, NotificationType.TableCanceled, "admin-1", null);

        verify(notificationService).notifyTableChanged(eq("co-1"), eq(table), any(), any());
        verify(notificationService, never()).notifyTableChanged(eq("co-1"), eq(table), any(), eq(TableMembership.Player));
    }

    @Test
    @DisplayName("an agenda change on a public table tells masters and players, minus the actor")
    void aScheduleChangeOnAPublicTableTellsEveryoneButTheActor() {
        GameTable table = table("t7", GameTableStatus.Opened);
        masters(table, master(table, "primary-1", MasterType.Primary), master(table, "co-1", MasterType.Secondary));
        players(table, "player-1");

        notifier.announceScheduleChanged(table, "primary-1");

        verify(notificationService).notifyTableChanged("co-1", table, NotificationType.TableScheduleChanged, TableMembership.Master);
        verify(notificationService).notifyTableChanged("player-1", table, NotificationType.TableScheduleChanged, TableMembership.Player);
        verify(notificationService, never()).notifyTableChanged(eq("primary-1"), any(), any(), any());
    }

    @Test
    @DisplayName("an agenda change on a table that never went public tells nobody")
    void aScheduleChangeOnADraftTellsNobody() {
        notifier.announceScheduleChanged(table("t8", GameTableStatus.Draft), "primary-1");

        verify(notificationService, never()).notifyTableChanged(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("an admin edit gives the masters the list of changes and the players the agenda notice only when it moved")
    void anAdminEditTellsTheMastersWhatChangedAndThePlayersOnlyAboutTheAgenda() {
        GameTable table = table("t9", GameTableStatus.Opened);
        masters(table, master(table, "primary-1", MasterType.Primary));
        players(table, "player-1");

        notifier.announceEdit(table, "admin-1", List.of("name", "schedule"));

        verify(notificationService).notifyTableEditedByAdmin("primary-1", table, List.of("name", "schedule"));
        verify(notificationService).notifyTableChanged("player-1", table, NotificationType.TableScheduleChanged, TableMembership.Player);
        // One notice for the masters, not a second ScheduleChanged on top of the edit.
        verify(notificationService, never()).notifyTableChanged(eq("primary-1"), any(), any(), any());
    }

    @Test
    @DisplayName("an admin edit that did not touch the agenda does not bother the players")
    void anAdminEditWithoutTheAgendaLeavesThePlayersAlone() {
        GameTable table = table("t10", GameTableStatus.Opened);
        masters(table, master(table, "primary-1", MasterType.Primary));

        notifier.announceEdit(table, "admin-1", List.of("description"));

        verify(notificationService).notifyTableEditedByAdmin("primary-1", table, List.of("description"));
        verify(registrationRepository, never()).findByGameTable_IdAndStatusOrderByCreatedAtAsc(anyString(), any());
    }

    @Test
    @DisplayName("an admin edit that changed nothing sends nothing")
    void anEditThatChangedNothingSendsNothing() {
        notifier.announceEdit(table("t11", GameTableStatus.Opened), "admin-1", List.of());

        verify(notificationService, never()).notifyTableEditedByAdmin(anyString(), any(), any());
        verify(notificationService, never()).notifyTableChanged(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("removing a table tells its masters unless the admin was one of them")
    void removingATableTellsTheMastersItHad() {
        GameTable table = table("t12", GameTableStatus.Deleted);
        List<Master> masters = List.of(master(table, "primary-1", MasterType.Primary));

        notifier.announceDeleted(table, "admin-1", masters);
        notifier.announceDeleted(table, "primary-1", masters);

        verify(notificationService, org.mockito.Mockito.times(1)).notifyTableDeleted("primary-1", table);
    }

    private void masters(GameTable table, Master... masters) {
        when(masterService.findByGameTable(table.getId())).thenReturn(List.of(masters));
    }

    private void players(GameTable table, String... userIds) {
        List<TableRegistration> registrations = java.util.Arrays.stream(userIds)
                .map(id -> new TableRegistration(table, user(id), null))
                .toList();
        when(registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(table.getId(), TableRegistrationStatus.Player))
                .thenReturn(registrations);
    }

    private static GameTable table(String id, GameTableStatus status) {
        GameTable table = new GameTable("Table " + id, user("creator-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setStatus(status);
        return table;
    }

    private static Master master(GameTable table, String userId, MasterType type) {
        return new Master(table, user(userId), type);
    }

    private static User user(String id) {
        User user = new User("discord-" + id, "name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
