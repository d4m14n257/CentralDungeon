package com.centraldungeon.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.profiles.dto.ProfileResponse;
import com.centraldungeon.profiles.dto.UserTableResponse;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterRowStatus;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.tables.TableSessionService;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Assembling a profile once its visibility has already been decided: the three sources it reads, and
 * that neither {@code karma} nor {@code comments} is among them (#248).
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private TableSessionService tableSessionService;

    @Mock
    private ProfileVisibilityService visibilityService;

    @Mock
    private MasterRepository masterRepository;

    @Mock
    private TableRegistrationRepository registrationRepository;

    private ProfileService service;

    private ProfileService service() {
        if (service == null) {
            service = new ProfileService(
                    userService, userRoleRepository, tableSessionService, visibilityService, masterRepository, registrationRepository);
        }
        return service;
    }

    @Test
    void assemblesTheProfileFromNameCountryRolesAndAggregateAttendance() {
        User target = user("target-1", "Elara", "AR");
        when(userService.getById("target-1")).thenReturn(target);
        when(userRoleRepository.findActiveRoleNames("target-1")).thenReturn(Set.of("Player", "Master"));
        AttendanceSummaryResponse attendance = new AttendanceSummaryResponse(5, 1, 2, 8);
        when(tableSessionService.summarizeAll("target-1")).thenReturn(attendance);

        ProfileResponse profile = service().getProfile("target-1", "actor-1");

        assertThat(profile.id()).isEqualTo("target-1");
        assertThat(profile.name()).isEqualTo("Elara");
        assertThat(profile.country()).isEqualTo("AR");
        assertThat(profile.roles()).containsExactlyInAnyOrder("Player", "Master");
        assertThat(profile.attendance()).isEqualTo(attendance);
    }

    /** What a master reads first on a candidate's card, in place of karma, which is F5 (#248, #284). */
    @Test
    void carriesTheDiscordHandleAndHowManyTablesTheyPlayedToTheEnd() {
        when(userService.getById("target-3")).thenReturn(user("target-3", "Cora", "CL"));
        when(userRoleRepository.findActiveRoleNames("target-3")).thenReturn(Set.of("Player"));
        when(tableSessionService.summarizeAll("target-3")).thenReturn(new AttendanceSummaryResponse(0, 0, 0, 0));
        when(registrationRepository.countByUser_IdAndStatusAndGameTable_Status(
                        "target-3", TableRegistrationStatus.Player, GameTableStatus.Finished))
                .thenReturn(4L);

        ProfileResponse profile = service().getProfile("target-3", "actor-1");

        assertThat(profile.discordUsername()).isEqualTo("discord-name-target-3");
        assertThat(profile.finishedTables()).isEqualTo(4L);
    }

    /**
     * An admin's view of a person's tables (#284): the ones they run and the ones they applied to,
     * newest table first, each saying what the person is to it - and nothing removed (#25).
     */
    @Test
    void listsTheTablesAPersonRunsAndPlaysAtNewestFirstWithoutRemovedOnes() {
        User person = user("target-4", "Dorian", "PE");
        when(userService.getById("target-4")).thenReturn(person);
        GameTable runs = table("table-old", GameTableStatus.InProgress, LocalDateTime.of(2026, 1, 1, 0, 0));
        GameTable plays = table("table-new", GameTableStatus.Finished, LocalDateTime.of(2026, 6, 1, 0, 0));
        GameTable removed = table("table-gone", GameTableStatus.Deleted, LocalDateTime.of(2026, 7, 1, 0, 0));
        when(masterRepository.findLiveByUser("target-4", MasterRowStatus.Created))
                .thenReturn(List.of(new Master(runs, person, MasterType.Primary)));
        TableRegistration played = new TableRegistration(plays, person, null);
        played.setStatus(TableRegistrationStatus.Player);
        TableRegistration gone = new TableRegistration(removed, person, null);
        gone.setStatus(TableRegistrationStatus.Player);
        when(registrationRepository.findWithTableByUserAndStatusIn(org.mockito.ArgumentMatchers.eq("target-4"), any()))
                .thenReturn(List.of(played, gone));

        PageResponse<UserTableResponse> page = service().listTablesForAdmin("target-4", PageRequest.of(0, 20));

        assertThat(page.content()).extracting(UserTableResponse::tableId).containsExactly("table-new", "table-old");
        assertThat(page.content()).extracting(UserTableResponse::relation).containsExactly("Player", "Primary");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    /** Paged in memory, like the shared tray (#100): the second page starts where the first ended. */
    @Test
    void pagesThePersonsTablesInMemory() {
        User person = user("target-5", "Esme", "MX");
        when(userService.getById("target-5")).thenReturn(person);
        List<Master> rows = List.of(
                new Master(table("t-1", GameTableStatus.Opened, LocalDateTime.of(2026, 3, 1, 0, 0)), person, MasterType.Primary),
                new Master(table("t-2", GameTableStatus.Opened, LocalDateTime.of(2026, 2, 1, 0, 0)), person, MasterType.Secondary),
                new Master(table("t-3", GameTableStatus.Opened, LocalDateTime.of(2026, 1, 1, 0, 0)), person, MasterType.Primary));
        when(masterRepository.findLiveByUser("target-5", MasterRowStatus.Created)).thenReturn(rows);
        when(registrationRepository.findWithTableByUserAndStatusIn(org.mockito.ArgumentMatchers.eq("target-5"), any()))
                .thenReturn(List.of());

        PageResponse<UserTableResponse> second = service().listTablesForAdmin("target-5", PageRequest.of(1, 2));

        assertThat(second.content()).extracting(UserTableResponse::tableId).containsExactly("t-3");
        assertThat(second.totalPages()).isEqualTo(2);
    }

    /** The target's existence is checked before its visibility - both answer identically either way (#249). */
    @Test
    void propagatesNotFoundWhenTheTargetDoesNotExist() {
        when(userService.getById("ghost")).thenThrow(new NotFoundException("User not found: ghost"));

        assertThatThrownBy(() -> service().getProfile("ghost", "actor-1")).isInstanceOf(NotFoundException.class);

        verify(visibilityService, never()).requireVisible(any(), any());
    }

    @Test
    void propagatesNotFoundWhenTheActorMayNotSeeTheProfile() {
        User target = user("target-2", "Bram", "UY");
        when(userService.getById("target-2")).thenReturn(target);
        doThrow(new NotFoundException("User not found: target-2")).when(visibilityService).requireVisible("target-2", "stranger");

        assertThatThrownBy(() -> service().getProfile("target-2", "stranger")).isInstanceOf(NotFoundException.class);
    }

    private static GameTable table(String id, GameTableStatus status, LocalDateTime createdAt) {
        GameTable table = new GameTable("Table " + id, user("creator-of-" + id, "Creator", "AR"));
        ReflectionTestUtils.setField(table, "id", id);
        ReflectionTestUtils.setField(table, "createdAt", createdAt);
        table.setStatus(status);
        return table;
    }

    private static User user(String id, String name, String country) {
        User user = new User("discord-" + id, "discord-name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        user.setName(name);
        user.setCountry(country);
        return user;
    }
}
