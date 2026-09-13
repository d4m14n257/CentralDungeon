package com.centraldungeon.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.profiles.dto.ProfileResponse;
import com.centraldungeon.tables.TableSessionService;
import com.centraldungeon.tables.dto.AttendanceSummaryResponse;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserService;
import java.util.Set;
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

    private ProfileService service;

    private ProfileService service() {
        if (service == null) {
            service = new ProfileService(userService, userRoleRepository, tableSessionService, visibilityService);
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

    private static User user(String id, String name, String country) {
        User user = new User("discord-" + id, "discord-name-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        user.setName(name);
        user.setCountry(country);
        return user;
    }
}
