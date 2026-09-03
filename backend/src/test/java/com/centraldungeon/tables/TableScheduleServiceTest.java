package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.users.User;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Replacing an agenda: the two refusals it can raise, and the notice it sends instead of a third. */
@ExtendWith(MockitoExtension.class)
class TableScheduleServiceTest {

    @Mock
    private TableScheduleRepository scheduleRepository;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    @Mock
    private TableRegistrationRepository registrationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TableScheduleService tableScheduleService;

    @Test
    void savesTheSlotsTheMasterAsked() {
        GameTable table = table("table-1", LocalTime.of(3, 0));
        when(scheduleRepository.findById_GameTableId("table-1")).thenReturn(List.of());

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00"), entry(Weekday.Friday, "21:00")), "master-1");

        verify(scheduleRepository, org.mockito.Mockito.times(2)).save(any(TableSchedule.class));
    }

    /** Two slots of the same table that overlap each other: malformed input, not a conflict (#178). */
    @Test
    void refusesAnAgendaThatOverlapsItself() {
        GameTable table = table("table-2", LocalTime.of(3, 0));
        when(scheduleConflictService.hasSelfOverlap(any())).thenReturn(true);

        assertThatThrownBy(() -> tableScheduleService.replace(
                        table, List.of(entry(Weekday.Tuesday, "20:00"), entry(Weekday.Tuesday, "22:00")), "master-1"))
                .isInstanceOf(InvalidRequestException.class);

        verify(scheduleRepository, never()).save(any());
    }

    /** R1: nothing is written when the agenda collides with something the master already runs. */
    @Test
    void refusesAnAgendaThatCollidesWithAnotherTableTheMasterIsCommittedTo() {
        GameTable table = table("table-3", LocalTime.of(3, 0));
        when(scheduleConflictService.findClash(eq("master-1"), eq("table-3"), any())).thenReturn(new CommittedTable("other", "La cripta"));

        assertThatThrownBy(() -> tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("La cripta");

        verify(scheduleRepository, never()).save(any());
    }

    /**
     * #197: the refusal travels as a code plus the name, not as a finished sentence. The message is
     * for a log; what the master reads is rendered by the frontend in their language.
     */
    @Test
    void carriesTheClashingTableNameAsAParameterAndNotOnlyInsideTheMessage() {
        GameTable table = table("table-3b", LocalTime.of(3, 0));
        when(scheduleConflictService.findClash(eq("master-1"), eq("table-3b"), any()))
                .thenReturn(new CommittedTable("other", "La cripta"));

        assertThatThrownBy(() -> tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1"))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ConflictException.SCHEDULE_CONFLICT);
                    assertThat(exception.getErrorParams()).containsEntry(ConflictException.PARAM_OTHER_TABLE_NAME, "La cripta");
                });
    }

    /** An Unassigned table has no master yet, so R1 has nobody to compare against (#72, #178). */
    @Test
    void skipsTheOwnerCheckWhenTheTableHasNoMasterYet() {
        GameTable table = table("table-4", LocalTime.of(3, 0));
        when(scheduleRepository.findById_GameTableId("table-4")).thenReturn(List.of());

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), null);

        verify(scheduleConflictService, never()).findClash(anyString(), any(), any());
        verify(scheduleRepository).save(any(TableSchedule.class));
    }

    /** Rows are marked, never dropped, so putting a slot back is an update and not a collision. */
    @Test
    void revivesASlotThatWasRemovedInsteadOfInsertingItAgain() {
        GameTable table = table("table-5", LocalTime.of(3, 0));
        TableSchedule removed = new TableSchedule("table-5", Weekday.Tuesday, LocalTime.of(20, 0));
        removed.setStatus(TableScheduleStatus.Deleted);
        when(scheduleRepository.findById_GameTableId("table-5")).thenReturn(List.of(removed));

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1");

        assertThat(removed.getStatus()).isEqualTo(TableScheduleStatus.Created);
        assertThat(removed.getDeletedAt()).isNull();
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void marksTheSlotsThatLeftTheAgenda() {
        GameTable table = table("table-6", LocalTime.of(3, 0));
        TableSchedule dropped = new TableSchedule("table-6", Weekday.Monday, LocalTime.of(19, 0));
        when(scheduleRepository.findById_GameTableId("table-6")).thenReturn(List.of(dropped));

        tableScheduleService.replace(table, List.of(), "master-1");

        assertThat(dropped.getStatus()).isEqualTo(TableScheduleStatus.Deleted);
        assertThat(dropped.getDeletedAt()).isNotNull();
    }

    /** Moving an agenda under people who are already in warns them; it never expels anybody (#70, #178). */
    @Test
    void warnsThePeopleTheNewAgendaNowClashesFor() {
        GameTable table = table("table-7", LocalTime.of(3, 0));
        when(scheduleRepository.findById_GameTableId("table-7")).thenReturn(List.of());
        TableRegistration player = registration(table, "player-1", TableRegistrationStatus.Player);
        when(registrationRepository.findByGameTable_Id("table-7")).thenReturn(List.of(player));
        when(scheduleConflictService.findClashWith("player-1", table)).thenReturn(new CommittedTable("other", "El faro"));

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1");

        verify(notificationService).notifyScheduleConflict("player-1", table, "El faro");
        assertThat(player.getStatus()).isEqualTo(TableRegistrationStatus.Player);
    }

    @Test
    void doesNotWarnSomebodyWhoseOtherTablesStillFit() {
        GameTable table = table("table-8", LocalTime.of(3, 0));
        when(scheduleRepository.findById_GameTableId("table-8")).thenReturn(List.of());
        when(registrationRepository.findByGameTable_Id("table-8"))
                .thenReturn(List.of(registration(table, "player-2", TableRegistrationStatus.Player)));
        when(scheduleConflictService.findClashWith("player-2", table)).thenReturn(null);

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1");

        verify(notificationService, never()).notifyScheduleConflict(anyString(), any(), anyString());
    }

    /** The column is part of the primary key, so 20:00:00 and 20:00:45 have to be one slot. */
    @Test
    void dropsTheSecondsAndCollapsesDuplicateSlots() {
        GameTable table = table("table-9", LocalTime.of(3, 0));
        when(scheduleRepository.findById_GameTableId("table-9")).thenReturn(List.of());

        tableScheduleService.replace(
                table,
                List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0, 45)), entry(Weekday.Tuesday, "20:00")),
                "master-1");

        verify(scheduleRepository, org.mockito.Mockito.times(1)).save(any(TableSchedule.class));
    }

    private static TableScheduleEntry entry(Weekday weekday, String hourtime) {
        return new TableScheduleEntry(weekday, LocalTime.parse(hourtime));
    }

    private static GameTable table(String id, LocalTime duration) {
        GameTable table = new GameTable("Table " + id, user("creator-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        table.setDuration(duration);
        return table;
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
