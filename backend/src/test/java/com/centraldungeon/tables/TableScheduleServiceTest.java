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

    @Mock
    private MasterService masterService;

    @InjectMocks
    private TableScheduleService tableScheduleService;

    @Test
    void savesTheSlotsTheMasterAsked() {
        GameTable table = table("table-1");
        when(scheduleRepository.findById_GameTableId("table-1")).thenReturn(List.of());

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00"), entry(Weekday.Friday, "21:00")), "master-1");

        verify(scheduleRepository, org.mockito.Mockito.times(2)).save(any(TableSchedule.class));
    }

    /** Two slots of the same table that overlap each other: malformed input, not a conflict (ADR-08 §2). */
    @Test
    void refusesAnAgendaThatOverlapsItself() {
        GameTable table = table("table-2");
        when(scheduleConflictService.hasSelfOverlap(any())).thenReturn(true);

        assertThatThrownBy(() -> tableScheduleService.replace(
                        table, List.of(entry(Weekday.Tuesday, "20:00"), entry(Weekday.Tuesday, "22:00")), "master-1"))
                .isInstanceOf(InvalidRequestException.class);

        verify(scheduleRepository, never()).save(any());
    }

    /** R1: nothing is written when the agenda collides with something the master already runs. */
    @Test
    void refusesAnAgendaThatCollidesWithAnotherTableTheMasterIsCommittedTo() {
        GameTable table = table("table-3");
        when(masterService.findByGameTable("table-3")).thenReturn(List.of(master(table, "master-1", MasterType.Primary)));
        when(scheduleConflictService.findClash(eq("master-1"), eq("table-3"), any())).thenReturn(new CommittedTable("other", "La cripta"));

        assertThatThrownBy(() -> tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("La cripta");

        verify(scheduleRepository, never()).save(any());
    }

    /**
     * ADR-23 §3: the refusal travels as a code plus the name, not as a finished sentence. The message is
     * for a log; what the master reads is rendered by the frontend in their language.
     */
    @Test
    void carriesTheClashingTableNameAsAParameterAndNotOnlyInsideTheMessage() {
        GameTable table = table("table-3b");
        when(masterService.findByGameTable("table-3b")).thenReturn(List.of(master(table, "master-1", MasterType.Primary)));
        when(scheduleConflictService.findClash(eq("master-1"), eq("table-3b"), any()))
                .thenReturn(new CommittedTable("other", "La cripta"));

        assertThatThrownBy(() -> tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1"))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ConflictException.SCHEDULE_CONFLICT);
                    assertThat(exception.getErrorParams()).containsEntry(ConflictException.PARAM_OTHER_TABLE_NAME, "La cripta");
                });
    }

    /** An Unassigned table has no master yet, so R1 has nobody to compare against (ADR-06 §7, ADR-08 §2). */
    @Test
    void skipsTheOwnerCheckWhenTheTableHasNoMasterYet() {
        GameTable table = table("table-4");
        when(scheduleRepository.findById_GameTableId("table-4")).thenReturn(List.of());

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), null);

        verify(scheduleConflictService, never()).findClash(anyString(), any(), any());
        verify(scheduleRepository).save(any(TableSchedule.class));
    }

    /**
     * The masters run the table together (ADR-08 §2): a co-master's week is measured as well, and the
     * refusal names them, because "where you are already committed" is false for the one editing.
     */
    @Test
    void refusesAnAgendaThatCollidesWithACoMastersOtherTable() {
        GameTable table = table("table-3c");
        when(masterService.findByGameTable("table-3c")).thenReturn(List.of(
                master(table, "master-1", MasterType.Primary), master(table, "co-master-1", MasterType.Secondary)));
        when(scheduleConflictService.findClash(eq("master-1"), eq("table-3c"), any())).thenReturn(null);
        when(scheduleConflictService.findClash(eq("co-master-1"), eq("table-3c"), any()))
                .thenReturn(new CommittedTable("other", "La cripta"));

        assertThatThrownBy(() -> tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1"))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ConflictException.MASTER_SCHEDULE_CONFLICT);
                    assertThat(exception.getErrorParams())
                            .containsEntry(ConflictException.PARAM_OTHER_TABLE_NAME, "La cripta")
                            .containsEntry(ConflictException.PARAM_MASTER_NAME, "name-co-master-1");
                });

        verify(scheduleRepository, never()).save(any());
    }

    /** Rows are marked, never dropped, so putting a slot back is an update and not a collision. */
    @Test
    void revivesASlotThatWasRemovedInsteadOfInsertingItAgain() {
        GameTable table = table("table-5");
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
        GameTable table = table("table-6");
        TableSchedule dropped = new TableSchedule("table-6", Weekday.Monday, LocalTime.of(19, 0));
        when(scheduleRepository.findById_GameTableId("table-6")).thenReturn(List.of(dropped));

        tableScheduleService.replace(table, List.of(), "master-1");

        assertThat(dropped.getStatus()).isEqualTo(TableScheduleStatus.Deleted);
        assertThat(dropped.getDeletedAt()).isNotNull();
    }

    /** Moving an agenda under candidates warns them; it never expels anybody (ADR-15 §2, ADR-08 §2). */
    @Test
    void warnsTheCandidatesTheNewAgendaNowClashesFor() {
        GameTable table = table("table-7");
        when(scheduleRepository.findById_GameTableId("table-7")).thenReturn(List.of());
        TableRegistration candidate = registration(table, "candidate-1", TableRegistrationStatus.Candidate);
        when(registrationRepository.findByGameTable_Id("table-7")).thenReturn(List.of(candidate));
        when(scheduleConflictService.findClashWith("candidate-1", table)).thenReturn(new CommittedTable("other", "El faro"));

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1");

        verify(notificationService).notifyScheduleConflict("candidate-1", table, "El faro");
        assertThat(candidate.getStatus()).isEqualTo(TableRegistrationStatus.Candidate);
    }

    /**
     * A player has nothing to withdraw on their applications screen, and already hears the agenda
     * changed through its own notice, so the clash warning is for candidates only.
     */
    @Test
    void doesNotSendTheClashWarningToAPlayer() {
        GameTable table = table("table-7b");
        when(scheduleRepository.findById_GameTableId("table-7b")).thenReturn(List.of());
        when(registrationRepository.findByGameTable_Id("table-7b"))
                .thenReturn(List.of(registration(table, "player-1", TableRegistrationStatus.Player)));

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1");

        verify(scheduleConflictService, never()).findClashWith(anyString(), any());
        verify(notificationService, never()).notifyScheduleConflict(anyString(), any(), anyString());
    }

    @Test
    void reportsAChangeWhenASlotIsAdded() {
        GameTable table = table("table-10");
        when(scheduleRepository.findById_GameTableIdAndStatus("table-10", TableScheduleStatus.Created))
                .thenReturn(List.of(slot("table-10", Weekday.Tuesday, "20:00", "03:00")));
        when(scheduleRepository.findById_GameTableId("table-10"))
                .thenReturn(List.of(slot("table-10", Weekday.Tuesday, "20:00", "03:00")));

        boolean changed = tableScheduleService.replace(
                table, List.of(entry(Weekday.Tuesday, "20:00"), entry(Weekday.Friday, "21:00")), "master-1");

        assertThat(changed).isTrue();
    }

    @Test
    void reportsAChangeWhenASlotIsRemoved() {
        GameTable table = table("table-11");
        when(scheduleRepository.findById_GameTableIdAndStatus("table-11", TableScheduleStatus.Created))
                .thenReturn(List.of(slot("table-11", Weekday.Tuesday, "20:00", "03:00"), slot("table-11", Weekday.Friday, "21:00", "03:00")));
        when(scheduleRepository.findById_GameTableId("table-11"))
                .thenReturn(List.of(slot("table-11", Weekday.Tuesday, "20:00", "03:00"), slot("table-11", Weekday.Friday, "21:00", "03:00")));

        assertThat(tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1")).isTrue();
    }

    @Test
    void reportsAChangeWhenOnlyTheDurationOfASlotChanges() {
        GameTable table = table("table-12");
        when(scheduleRepository.findById_GameTableIdAndStatus("table-12", TableScheduleStatus.Created))
                .thenReturn(List.of(slot("table-12", Weekday.Tuesday, "20:00", "03:00")));
        when(scheduleRepository.findById_GameTableId("table-12"))
                .thenReturn(List.of(slot("table-12", Weekday.Tuesday, "20:00", "03:00")));

        assertThat(tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00", "04:00")), "master-1")).isTrue();
    }

    /** Sending back the week it already has is not news for anybody, whatever order or seconds it arrives with. */
    @Test
    void reportsNoChangeWhenTheSameWeekIsSentBack() {
        GameTable table = table("table-13");
        when(scheduleRepository.findById_GameTableIdAndStatus("table-13", TableScheduleStatus.Created))
                .thenReturn(List.of(slot("table-13", Weekday.Tuesday, "20:00", "03:00"), slot("table-13", Weekday.Friday, "21:00", "03:00")));
        when(scheduleRepository.findById_GameTableId("table-13"))
                .thenReturn(List.of(slot("table-13", Weekday.Tuesday, "20:00", "03:00"), slot("table-13", Weekday.Friday, "21:00", "03:00")));

        boolean changed = tableScheduleService.replace(
                table,
                List.of(entry(Weekday.Friday, "21:00"), new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0, 30), LocalTime.of(3, 0))),
                "master-1");

        assertThat(changed).isFalse();
    }

    @Test
    void doesNotWarnSomebodyWhoseOtherTablesStillFit() {
        GameTable table = table("table-8");
        when(scheduleRepository.findById_GameTableId("table-8")).thenReturn(List.of());
        when(registrationRepository.findByGameTable_Id("table-8"))
                .thenReturn(List.of(registration(table, "player-2", TableRegistrationStatus.Candidate)));
        when(scheduleConflictService.findClashWith("player-2", table)).thenReturn(null);

        tableScheduleService.replace(table, List.of(entry(Weekday.Tuesday, "20:00")), "master-1");

        verify(notificationService, never()).notifyScheduleConflict(anyString(), any(), anyString());
    }

    /** The column is part of the primary key, so 20:00:00 and 20:00:45 have to be one slot. */
    @Test
    void dropsTheSecondsAndCollapsesDuplicateSlots() {
        GameTable table = table("table-9");
        when(scheduleRepository.findById_GameTableId("table-9")).thenReturn(List.of());

        tableScheduleService.replace(
                table,
                List.of(new TableScheduleEntry(Weekday.Tuesday, LocalTime.of(20, 0, 45), LocalTime.of(3, 0)), entry(Weekday.Tuesday, "20:00")),
                "master-1");

        verify(scheduleRepository, org.mockito.Mockito.times(1)).save(any(TableSchedule.class));
    }

    /** A slot that lasts three hours, which is the length these tests are not about. */
    private static TableScheduleEntry entry(Weekday weekday, String hourtime) {
        return entry(weekday, hourtime, "03:00");
    }

    /**
     * A slot with the length it is meant to claim (ADR-08 §1). The duration belongs to the slot now, so
     * it is here and no longer on the table.
     */
    private static TableScheduleEntry entry(Weekday weekday, String hourtime, String duration) {
        return new TableScheduleEntry(weekday, LocalTime.parse(hourtime), LocalTime.parse(duration));
    }

    private static TableSchedule slot(String tableId, Weekday weekday, String hourtime, String duration) {
        return new TableSchedule(tableId, weekday, LocalTime.parse(hourtime), LocalTime.parse(duration));
    }

    private static GameTable table(String id) {
        GameTable table = new GameTable("Table " + id, user("creator-" + id));
        ReflectionTestUtils.setField(table, "id", id);
        return table;
    }

    private static TableRegistration registration(GameTable table, String userId, TableRegistrationStatus status) {
        TableRegistration registration = new TableRegistration(table, user(userId), null);
        registration.setStatus(status);
        return registration;
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
