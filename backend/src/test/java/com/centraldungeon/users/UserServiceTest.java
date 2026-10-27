package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterRowStatus;
import com.centraldungeon.users.dto.UpdateUserRequest;
import com.centraldungeon.users.dto.UserDetailResponse;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private MasterRepository masterRepository;

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, userRoleRepository, masterRepository, userMapper);
    }

    @Test
    void refreshesTheUsernameOfAnExistingUser() {
        User existing = persistedUser("user-1", "discord-1", "OldName");
        when(userRepository.findByDiscordId("discord-1")).thenReturn(Optional.of(existing));

        User result = userService.findOrCreateByDiscordId("discord-1", "NewName");

        assertThat(result.getDiscordUsername()).isEqualTo("NewName");
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void createsANewUserWithThePlayerRole() {
        when(userRepository.findByDiscordId("discord-2")).thenReturn(Optional.empty());
        User saved = persistedUser("user-2", "discord-2", "NewPlayer");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        Role playerRole = new Role("Player", null);
        ReflectionTestUtils.setField(playerRole, "id", "role-player");
        when(roleRepository.findByName("Player")).thenReturn(Optional.of(playerRole));

        User result = userService.findOrCreateByDiscordId("discord-2", "NewPlayer");

        assertThat(result.getId()).isEqualTo("user-2");
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void blowsUpLoudlyIfThePlayerRoleIsMissingFromTheSeed() {
        when(userRepository.findByDiscordId("discord-3")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(persistedUser("user-3", "discord-3", "X"));
        when(roleRepository.findByName("Player")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findOrCreateByDiscordId("discord-3", "X")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsNotFoundForAnUnknownUser() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void completesOnboardingBySettingNameAndCountry() {
        User user = persistedUser("user-4", "discord-4", "X");
        when(userRepository.findById("user-4")).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveRoleNames("user-4")).thenReturn(Set.of("Player"));
        when(masterRepository.existsByUser_IdAndStatus("user-4", MasterRowStatus.Created)).thenReturn(false);
        when(userMapper.toDetailResponse(user, Set.of("Player"), false))
                .thenReturn(new UserDetailResponse("user-4", "Onboarded Name", "AR", false, Set.of("Player"), false));

        UserDetailResponse response = userService.completeOnboarding("user-4", new UpdateUserRequest("Onboarded Name", "AR"));

        assertThat(user.getName()).isEqualTo("Onboarded Name");
        assertThat(user.getCountry()).isEqualTo("AR");
        assertThat(response.needsOnboarding()).isFalse();
    }

    @Test
    void completingOnboardingAgainIsRefusedAndLeavesTheProfileUntouched() {
        User user = persistedUser("user-6", "discord-6", "X");
        user.setName("Kept");
        user.setCountry("AR");
        when(userRepository.findById("user-6")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.completeOnboarding("user-6", new UpdateUserRequest("Other", "CL")))
                .isInstanceOfSatisfying(ConflictException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ConflictException.ONBOARDING_ALREADY_COMPLETED));
        assertThat(user.getName()).isEqualTo("Kept");
        assertThat(user.getCountry()).isEqualTo("AR");
    }

    @Test
    void updatesTheProfileOfSomebodyPastOnboarding() {
        User user = persistedUser("user-7", "discord-7", "X");
        user.setName("Old");
        user.setCountry("AR");
        when(userRepository.findById("user-7")).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveRoleNames("user-7")).thenReturn(Set.of("Player"));
        when(masterRepository.existsByUser_IdAndStatus("user-7", MasterRowStatus.Created)).thenReturn(false);
        when(userMapper.toDetailResponse(user, Set.of("Player"), false))
                .thenReturn(new UserDetailResponse("user-7", "New", "CL", false, Set.of("Player"), false));

        userService.updateProfile("user-7", new UpdateUserRequest("New", "CL"));

        assertThat(user.getName()).isEqualTo("New");
        assertThat(user.getCountry()).isEqualTo("CL");
    }

    @Test
    void editingTheProfileBeforeOnboardingIsRefused() {
        User user = persistedUser("user-8", "discord-8", "X");
        when(userRepository.findById("user-8")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile("user-8", new UpdateUserRequest("New", "CL")))
                .isInstanceOfSatisfying(ConflictException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ConflictException.ONBOARDING_PENDING));
        assertThat(user.getName()).isNull();
    }

    @Test
    void detailResponseFlagsAMasterOfAtLeastOneTableEvenWithoutThePlatformRole() {
        User user = persistedUser("user-5", "discord-5", "X");
        when(userRepository.findById("user-5")).thenReturn(Optional.of(user));
        when(userRoleRepository.findActiveRoleNames("user-5")).thenReturn(Set.of("Player"));
        when(masterRepository.existsByUser_IdAndStatus("user-5", MasterRowStatus.Created)).thenReturn(true);
        when(userMapper.toDetailResponse(user, Set.of("Player"), true))
                .thenReturn(new UserDetailResponse("user-5", "X", null, true, Set.of("Player"), true));

        UserDetailResponse response = userService.getDetailResponse("user-5");

        assertThat(response.hasManagedTables()).isTrue();
    }

    private User persistedUser(String id, String discordId, String discordUsername) {
        User user = new User(discordId, discordUsername);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
