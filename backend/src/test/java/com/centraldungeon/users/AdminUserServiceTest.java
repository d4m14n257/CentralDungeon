package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.AdminUserDetailResponse;
import com.centraldungeon.users.dto.AdminUserSummaryResponse;
import com.centraldungeon.users.dto.UserAdminChangeResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The block, the unblock and the history of {@code /admin/users} (contrato F3.1, 3).
 *
 * <p>The rule that carries the screen is that nobody holding Admin or Owner can be blocked - by
 * anybody, including themselves. Stated about the target rather than as a comparison between actor
 * and target, it covers self-blocking for free; its own test is here anyway, because "for free" is
 * exactly the kind of coverage that stops being true when somebody rewrites the condition.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final CurrentUser ADMIN_ACTOR = new CurrentUser("actor-admin", Set.of("Admin"));

    private static final CurrentUser OWNER_ACTOR = new CurrentUser("actor-owner", Set.of("Owner"));

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserRoleChangeRepository userRoleChangeRepository;

    @Mock
    private UserStatusChangeRepository userStatusChangeRepository;

    @Mock
    private UserService userService;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                userRoleRepository,
                userRoleChangeRepository,
                userStatusChangeRepository,
                userService,
                Mappers.getMapper(UserMapper.class));
    }

    // ---------------------------------------------------------------- rule 1: nobody blocks a peer

    @Test
    void somebodyHoldingAdminCannotBeBlocked() {
        User target = persistedUser("target", UserStatus.Allowed);
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findActiveRoleNames("target")).thenReturn(Set.of("Player", "Admin"));

        assertThatThrownBy(() -> adminUserService.block("target", "se portó mal", OWNER_ACTOR))
                .isInstanceOf(ForbiddenActionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ForbiddenActionException.class))
                .extracting(ForbiddenActionException::getErrorCode)
                .isEqualTo(ForbiddenActionException.CANNOT_BLOCK_PRIVILEGED);

        assertThat(target.getStatus()).isEqualTo(UserStatus.Allowed);
        verify(userStatusChangeRepository, never()).save(any());
        verify(userService, never()).evictAuthCache(any());
    }

    @Test
    void somebodyHoldingOwnerCannotBeBlockedNotEvenByAnOwner() {
        User target = persistedUser("target", UserStatus.Allowed);
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findActiveRoleNames("target")).thenReturn(Set.of("Owner"));

        assertThatThrownBy(() -> adminUserService.block("target", "motivo", OWNER_ACTOR))
                .isInstanceOf(ForbiddenActionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ForbiddenActionException.class))
                .extracting(ForbiddenActionException::getErrorCode)
                .isEqualTo(ForbiddenActionException.CANNOT_BLOCK_PRIVILEGED);
    }

    /** Blocking yourself falls out of the rule above for free - the actor always holds one of the two roles. */
    @Test
    void anAdminCannotBlockThemselves() {
        User self = persistedUser("actor-admin", UserStatus.Allowed);
        when(userService.getById("actor-admin")).thenReturn(self);
        when(userRoleRepository.findActiveRoleNames("actor-admin")).thenReturn(Set.of("Admin"));

        assertThatThrownBy(() -> adminUserService.block("actor-admin", "me arrepentí", ADMIN_ACTOR))
                .isInstanceOf(ForbiddenActionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ForbiddenActionException.class))
                .extracting(ForbiddenActionException::getErrorCode)
                .isEqualTo(ForbiddenActionException.CANNOT_BLOCK_PRIVILEGED);

        assertThat(self.getStatus()).isEqualTo(UserStatus.Allowed);
    }

    // ---------------------------------------------------------------- rule 2 and 3: the transitions

    @Test
    void bloquearUnaCuentaYaBloqueadaEs409() {
        User target = persistedUser("target", UserStatus.Blocked);
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findActiveRoleNames("target")).thenReturn(Set.of("Player"));

        assertThatThrownBy(() -> adminUserService.block("target", "otra vez", ADMIN_ACTOR))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .extracting(ConflictException::getErrorCode)
                .isEqualTo(ConflictException.USER_ALREADY_BLOCKED);

        verify(userStatusChangeRepository, never()).save(any());
    }

    /** F3.1 does not touch Deleted: a deleted account is neither blocked nor unblocked, it stays put. */
    @Test
    void bloquearUnaCuentaDeletedEs409YNoLaMueve() {
        User target = persistedUser("target", UserStatus.Deleted);
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findActiveRoleNames("target")).thenReturn(Set.of());

        assertThatThrownBy(() -> adminUserService.block("target", "x", ADMIN_ACTOR))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .extracting(ConflictException::getErrorCode)
                .isEqualTo(ConflictException.USER_ALREADY_BLOCKED);

        assertThat(target.getStatus()).isEqualTo(UserStatus.Deleted);
    }

    @Test
    void desbloquearUnaCuentaQueNoEstaBloqueadaEs409() {
        User target = persistedUser("target", UserStatus.Allowed);
        when(userService.getById("target")).thenReturn(target);

        assertThatThrownBy(() -> adminUserService.unblock("target", "x", ADMIN_ACTOR))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .extracting(ConflictException::getErrorCode)
                .isEqualTo(ConflictException.USER_NOT_BLOCKED);
    }

    @Test
    void desbloquearUnaCuentaDeletedEs409YNuncaLaPasaAAllowed() {
        User target = persistedUser("target", UserStatus.Deleted);
        when(userService.getById("target")).thenReturn(target);

        assertThatThrownBy(() -> adminUserService.unblock("target", "x", OWNER_ACTOR))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .extracting(ConflictException::getErrorCode)
                .isEqualTo(ConflictException.USER_NOT_BLOCKED);

        assertThat(target.getStatus()).isEqualTo(UserStatus.Deleted);
        verify(userStatusChangeRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- the happy paths, with #84

    @Test
    void blockingKeepsTheDataAndLeavesARowWithTheReason() {
        User target = persistedUser("target", UserStatus.Allowed);
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-admin")).thenReturn(persistedUser("actor-admin", UserStatus.Allowed));
        when(userRoleRepository.findActiveRoleNames("target")).thenReturn(Set.of("Player"));

        AdminUserDetailResponse response = adminUserService.block("target", "spam en tres mesas", ADMIN_ACTOR);

        assertThat(target.getStatus()).isEqualTo(UserStatus.Blocked);
        assertThat(response.status()).isEqualTo("Blocked");
        assertThat(response.roles()).containsExactly("Player");
        // #84: el bloqueo cierra la puerta, no borra nada.
        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).deleteById(any());

        ArgumentCaptor<UserStatusChange> saved = ArgumentCaptor.forClass(UserStatusChange.class);
        verify(userStatusChangeRepository).save(saved.capture());
        assertThat(saved.getValue().getFromStatus()).isEqualTo(UserStatus.Allowed);
        assertThat(saved.getValue().getToStatus()).isEqualTo(UserStatus.Blocked);
        assertThat(saved.getValue().getJustification()).isEqualTo("spam en tres mesas");
        assertThat(saved.getValue().getChangedBy().getId()).isEqualTo("actor-admin");
    }

    /** «Un bloqueo que tarda 60 s en aplicar es un bloqueo que no bloquea» (#128). */
    @Test
    void bloquearInvalidaLaCacheDeAutorizacionDelObjetivo() {
        User target = persistedUser("target", UserStatus.Allowed);
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-admin")).thenReturn(persistedUser("actor-admin", UserStatus.Allowed));
        when(userRoleRepository.findActiveRoleNames("target")).thenReturn(Set.of("Player"));

        adminUserService.block("target", "motivo", ADMIN_ACTOR);

        verify(userService).evictAuthCache("target");
    }

    @Test
    void desbloquearDevuelveLaCuentaAAllowedYDejaSuFila() {
        User target = persistedUser("target", UserStatus.Blocked);
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-owner")).thenReturn(persistedUser("actor-owner", UserStatus.Allowed));
        when(userRoleRepository.findActiveRoleNames("target")).thenReturn(Set.of("Player"));

        AdminUserDetailResponse response = adminUserService.unblock("target", "apeló y tenía razón", OWNER_ACTOR);

        assertThat(target.getStatus()).isEqualTo(UserStatus.Allowed);
        assertThat(response.status()).isEqualTo("Allowed");

        ArgumentCaptor<UserStatusChange> saved = ArgumentCaptor.forClass(UserStatusChange.class);
        verify(userStatusChangeRepository).save(saved.capture());
        assertThat(saved.getValue().getFromStatus()).isEqualTo(UserStatus.Blocked);
        assertThat(saved.getValue().getToStatus()).isEqualTo(UserStatus.Allowed);
        assertThat(saved.getValue().getJustification()).isEqualTo("apeló y tenía razón");
        verify(userService).evictAuthCache("target");
    }

    // ---------------------------------------------------------------- the history panel

    @Test
    void theHistoryMergesBothTablesIntoOneTimeline() {
        User target = persistedUser("target", UserStatus.Blocked);
        User actor = persistedUser("actor-admin", UserStatus.Allowed);
        actor.setName("La Admin");
        when(userService.getById("target")).thenReturn(target);
        when(userRoleChangeRepository.findByUser_IdOrderByCreatedAtAsc("target"))
                .thenReturn(List.of(roleChange("rc-1", target, PlatformRole.MASTER, UserRoleChangeAction.Granted, actor, 10)));
        when(userStatusChangeRepository.findByUser_IdOrderByCreatedAtAsc("target"))
                .thenReturn(List.of(statusChange("sc-1", target, UserStatus.Allowed, UserStatus.Blocked, actor, 11)));

        List<UserAdminChangeResponse> history = adminUserService.history("target");

        assertThat(history).extracting(UserAdminChangeResponse::type).containsExactly("RoleGranted", "StatusChanged");
        assertThat(history.getFirst().role()).isEqualTo("Master");
        assertThat(history.getFirst().fromStatus()).isNull();
        assertThat(history.getLast().fromStatus()).isEqualTo("Allowed");
        assertThat(history.getLast().toStatus()).isEqualTo("Blocked");
        // The resolved name, never the id - the same as TableStatusChangeResponse.
        assertThat(history).allSatisfy(entry -> assertThat(entry.changedByName()).isEqualTo("La Admin"));
    }

    @Test
    void theHistoryOfAUserThatDoesNotExistIs404() {
        when(userService.getById("missing")).thenThrow(new NotFoundException("User not found: missing"));

        assertThatThrownBy(() -> adminUserService.history("missing")).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- the listing

    @Test
    void theListingResolvesTheWholePagesRolesInOneQueryAndInOrder() {
        User first = persistedUser("u1", UserStatus.Blocked);
        User second = persistedUser("u2", UserStatus.Allowed);
        when(userRepository.findAll(any(Specification.class), eq(FIRST_PAGE)))
                .thenReturn(new PageImpl<>(List.of(first, second), FIRST_PAGE, 2));
        when(userRoleRepository.findActiveGrantsByUsers(List.of("u1", "u2")))
                .thenReturn(List.of(
                        new UserRoleGrant("u1", "Admin"), new UserRoleGrant("u1", "Player"), new UserRoleGrant("u2", "Master")));

        PageResponse<AdminUserSummaryResponse> page = adminUserService.search("/status Blocked", FIRST_PAGE);

        // The chips are ordered by PlatformRole, not by whatever order the query returned.
        assertThat(page.content().getFirst().roles()).containsExactly("Player", "Admin");
        assertThat(page.content().getLast().roles()).containsExactly("Master");
        // A blocked account DOES appear here: an admin who cannot find it cannot unblock it.
        assertThat(page.content().getFirst().status()).isEqualTo("Blocked");
    }

    @Test
    void anEmptyPageDoesNotQueryTheRoles() {
        when(userRepository.findAll(any(Specification.class), eq(FIRST_PAGE)))
                .thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

        assertThat(adminUserService.search(null, FIRST_PAGE).content()).isEmpty();

        verify(userRoleRepository, never()).findActiveGrantsByUsers(any());
    }

    // ---------------------------------------------------------------- fixtures

    private static UserRoleChange roleChange(
            String id, User target, PlatformRole role, UserRoleChangeAction action, User actor, int minute) {
        Role entity = new Role(role.roleName(), null);
        ReflectionTestUtils.setField(entity, "id", "role-" + role.name().toLowerCase(Locale.ROOT));
        UserRoleChange change = new UserRoleChange(target, entity, action, actor, "porque si");
        ReflectionTestUtils.setField(change, "id", id);
        ReflectionTestUtils.setField(change, "createdAt", LocalDateTime.of(2026, 1, 1, 12, minute));
        return change;
    }

    private static UserStatusChange statusChange(
            String id, User target, UserStatus from, UserStatus to, User actor, int minute) {
        UserStatusChange change = new UserStatusChange(target, from, to, actor, "porque si");
        ReflectionTestUtils.setField(change, "id", id);
        ReflectionTestUtils.setField(change, "createdAt", LocalDateTime.of(2026, 1, 1, 12, minute));
        return change;
    }

    private static User persistedUser(String id, UserStatus status) {
        User user = new User("discord-" + id, id);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 1, 1, 12, 0));
        user.setStatus(status);
        return user;
    }
}
