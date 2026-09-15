package com.centraldungeon.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.AdminUserDetailResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The six rules of {@code grantRole} and the six of {@code revokeRole} (contrato F3.1, 3).
 *
 * <p>Unit and not integration because every one of them is a decision the service makes before it
 * touches anything: who may move which rank, what the exclusion of #169 drags along, and when the
 * last owner makes the whole operation impossible. The only rule that genuinely needs a database is
 * the global "there is always an owner" invariant under concurrency, and that one is
 * {@code UserRoleIT}'s - the same treatment #73 gives the single Primary.
 *
 * <p>The mapper is the real one: the order of the roles in the response is part of the contract, and
 * a mocked mapper would assert nothing about it.
 */
@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    private static final CurrentUser OWNER_ACTOR = new CurrentUser("actor-owner", Set.of("Owner"));

    private static final CurrentUser ADMIN_ACTOR = new CurrentUser("actor-admin", Set.of("Admin"));

    @Mock
    private UserService userService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserRoleChangeRepository userRoleChangeRepository;

    private UserRoleService userRoleService;

    @BeforeEach
    void setUp() {
        userRoleService = new UserRoleService(
                userService, roleRepository, userRoleRepository, userRoleChangeRepository, Mappers.getMapper(UserMapper.class));
    }

    // ---------------------------------------------------------------- rule 1: who moves which rank

    @Test
    void unAdminNoPuedeOtorgarElRolAdmin() {
        assertThatThrownBy(() -> userRoleService.grantRole("target", PlatformRole.ADMIN, "porque si", ADMIN_ACTOR))
                .isInstanceOf(ForbiddenActionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ForbiddenActionException.class))
                .extracting(ForbiddenActionException::getErrorCode)
                .isEqualTo(ForbiddenActionException.ROLE_GRANT_FORBIDDEN);

        // It fails before anything is even read: it is a question of who the actor is, not of state.
        verify(userService, never()).getById(any());
        verify(userRoleChangeRepository, never()).save(any());
    }

    @Test
    void unAdminNoPuedeOtorgarElRolOwner() {
        assertThatThrownBy(() -> userRoleService.grantRole("target", PlatformRole.OWNER, "porque si", ADMIN_ACTOR))
                .isInstanceOf(ForbiddenActionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ForbiddenActionException.class))
                .extracting(ForbiddenActionException::getErrorCode)
                .isEqualTo(ForbiddenActionException.ROLE_GRANT_FORBIDDEN);
    }

    @Test
    void unAdminNoPuedeQuitarElRolAdmin() {
        assertThatThrownBy(() -> userRoleService.revokeRole("target", PlatformRole.ADMIN, "porque si", ADMIN_ACTOR))
                .isInstanceOf(ForbiddenActionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ForbiddenActionException.class))
                .extracting(ForbiddenActionException::getErrorCode)
                .isEqualTo(ForbiddenActionException.ROLE_GRANT_FORBIDDEN);

        verify(userRoleRepository, never()).findAllGrants(any());
    }

    @Test
    void unAdminNoPuedeQuitarElRolOwner() {
        assertThatThrownBy(() -> userRoleService.revokeRole("target", PlatformRole.OWNER, "porque si", ADMIN_ACTOR))
                .isInstanceOf(ForbiddenActionException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ForbiddenActionException.class))
                .extracting(ForbiddenActionException::getErrorCode)
                .isEqualTo(ForbiddenActionException.ROLE_GRANT_FORBIDDEN);
    }

    @Test
    void unAdminSiPuedeOtorgarElRolMaster() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-admin")).thenReturn(persistedUser("actor-admin"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.PLAYER));
        when(roleRepository.findByName("Master")).thenReturn(Optional.of(role(PlatformRole.MASTER)));

        AdminUserDetailResponse response =
                userRoleService.grantRole("target", PlatformRole.MASTER, "dirige bien", ADMIN_ACTOR);

        assertThat(response.roles()).containsExactly("Player", "Master");
        assertThat(savedRoleChanges()).singleElement().satisfies(change -> {
            assertThat(change.getAction()).isEqualTo(UserRoleChangeAction.Granted);
            assertThat(change.getRole().getName()).isEqualTo("Master");
            assertThat(change.getJustification()).isEqualTo("dirige bien");
        });
    }

    @Test
    void unOwnerSiPuedeOtorgarElRolAdmin() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-owner")).thenReturn(persistedUser("actor-owner"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.PLAYER));
        when(roleRepository.findByName("Admin")).thenReturn(Optional.of(role(PlatformRole.ADMIN)));

        AdminUserDetailResponse response = userRoleService.grantRole("target", PlatformRole.ADMIN, "confio", OWNER_ACTOR);

        assertThat(response.roles()).containsExactly("Player", "Admin");
    }

    // ---------------------------------------------------------------- rule 2: the #169 exclusion

    @Test
    void otorgarAdminAUnOwnerLeQuitaOwnerYDejaSuPropiaFilaDeAuditoria() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-owner")).thenReturn(persistedUser("actor-owner"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.PLAYER, PlatformRole.OWNER));
        stubOwnerCount(3);
        when(roleRepository.findByName("Admin")).thenReturn(Optional.of(role(PlatformRole.ADMIN)));

        AdminUserDetailResponse response = userRoleService.grantRole("target", PlatformRole.ADMIN, "baja de rango", OWNER_ACTOR);

        assertThat(response.roles()).containsExactly("Player", "Admin");
        assertThat(savedRoleChanges())
                .extracting(change -> change.getRole().getName() + ":" + change.getAction())
                .containsExactly("Owner:Revoked", "Admin:Granted");
        assertThat(savedRoleChanges()).allSatisfy(change -> assertThat(change.getJustification()).isEqualTo("baja de rango"));
    }

    @Test
    void otorgarOwnerAUnAdminLeQuitaAdminEnLaMismaTransaccion() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-owner")).thenReturn(persistedUser("actor-owner"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.ADMIN));
        when(roleRepository.findByName("Owner")).thenReturn(Optional.of(role(PlatformRole.OWNER)));

        AdminUserDetailResponse response = userRoleService.grantRole("target", PlatformRole.OWNER, "sucesion", OWNER_ACTOR);

        assertThat(response.roles()).containsExactly("Owner");
        assertThat(savedRoleChanges())
                .extracting(change -> change.getRole().getName() + ":" + change.getAction())
                .containsExactly("Admin:Revoked", "Owner:Granted");
    }

    // ---------------------------------------------------------------- rule 3: the last owner

    /** The back door: nobody asked to remove Owner, but #169 would, and it is still the last one. */
    @Test
    void otorgarAdminAlUltimoOwnerSeRechazaEnteroSinEscribirNada() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.OWNER));
        stubOwnerCount(1);

        assertThatThrownBy(() -> userRoleService.grantRole("target", PlatformRole.ADMIN, "baja", OWNER_ACTOR))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .extracting(ConflictException::getErrorCode)
                .isEqualTo(ConflictException.LAST_OWNER);

        // Rejected whole: the Admin grant is not written and then rolled back by an exception.
        verify(userRoleRepository, never()).save(any());
        verify(userRoleChangeRepository, never()).save(any());
        verify(userService, never()).evictAuthCache(any());
    }

    @Test
    void quitarOwnerAlUltimoOwnerSeRechaza() {
        User target = persistedUser("last-owner");
        when(userService.getById("last-owner")).thenReturn(target);
        when(userRoleRepository.findAllGrants("last-owner")).thenReturn(grants(target, PlatformRole.OWNER));
        stubOwnerCount(1);

        assertThatThrownBy(() -> userRoleService.revokeRole("last-owner", PlatformRole.OWNER, "chau", OWNER_ACTOR))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .extracting(ConflictException::getErrorCode)
                .isEqualTo(ConflictException.LAST_OWNER);

        verify(userRoleChangeRepository, never()).save(any());
    }

    @Test
    void quitarOwnerCuandoQuedaOtroOwnerSiFunciona() {
        User target = persistedUser("other-owner");
        List<UserRole> grants = grants(target, PlatformRole.OWNER);
        when(userService.getById("other-owner")).thenReturn(target);
        when(userService.getById("actor-owner")).thenReturn(persistedUser("actor-owner"));
        when(userRoleRepository.findAllGrants("other-owner")).thenReturn(grants);
        stubOwnerCount(2);

        AdminUserDetailResponse response = userRoleService.revokeRole("other-owner", PlatformRole.OWNER, "se va", OWNER_ACTOR);

        assertThat(response.roles()).isEmpty();
        assertThat(grants.getFirst().getStatus()).isEqualTo(UserRoleStatus.Deleted);
        // La fila se marca, no se borra (#25) - y deleted_at deja de nacer muerta.
        assertThat(grants.getFirst().getDeletedAt()).isNotNull();
        assertThat(savedRoleChanges()).singleElement().satisfies(change -> {
            assertThat(change.getAction()).isEqualTo(UserRoleChangeAction.Revoked);
            assertThat(change.getRole().getName()).isEqualTo("Owner");
        });
    }

    /**
     * Una cuenta que no está {@code Allowed} no es uno de los owners que la invariante cuenta: no
     * puede entrar a otorgarle el rol a nadie, así que quitárselo no deja a la plataforma sin nada.
     */
    @Test
    void quitarOwnerAUnaCuentaNoActivaNoCuentaComoElUltimoOwner() {
        User target = persistedUser("deleted-owner");
        target.setStatus(UserStatus.Deleted);
        List<UserRole> grants = grants(target, PlatformRole.OWNER);
        when(userService.getById("deleted-owner")).thenReturn(target);
        when(userService.getById("actor-owner")).thenReturn(persistedUser("actor-owner"));
        when(userRoleRepository.findAllGrants("deleted-owner")).thenReturn(grants);

        userRoleService.revokeRole("deleted-owner", PlatformRole.OWNER, "limpieza", OWNER_ACTOR);

        assertThat(grants.getFirst().getStatus()).isEqualTo(UserRoleStatus.Deleted);
        // Ni siquiera pregunta cuántos owners hay: esta persona no es uno de ellos.
        verify(userRoleRepository, never()).lockActiveHolders(any());
    }

    @Test
    void restaurarUnRolLimpiaLaMarcaDeRevocacion() {
        User target = persistedUser("target");
        List<UserRole> grants = grants(target, PlatformRole.MASTER);
        UserRole revoked = grants.getFirst();
        revoked.revoke();
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-admin")).thenReturn(persistedUser("actor-admin"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants);

        userRoleService.grantRole("target", PlatformRole.MASTER, "vuelve", ADMIN_ACTOR);

        assertThat(revoked.getStatus()).isEqualTo(UserRoleStatus.Allowed);
        assertThat(revoked.getDeletedAt()).isNull();
    }

    // ---------------------------------------------------------------- rule 2 of revoke: stepping down

    @Test
    void unOwnerNoPuedeQuitarseSuPropioOwner() {
        when(userService.getById("actor-owner")).thenReturn(persistedUser("actor-owner"));

        assertThatThrownBy(() -> userRoleService.revokeRole("actor-owner", PlatformRole.OWNER, "me voy", OWNER_ACTOR))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(ConflictException.class))
                .extracting(ConflictException::getErrorCode)
                .isEqualTo(ConflictException.CANNOT_REVOKE_OWN_OWNER);

        // The more specific conflict wins: the platform is never even asked how many owners it has,
        // so an owner with a colleague still gets "you cannot demote yourself" and not LAST_OWNER.
        verify(userRoleRepository, never()).lockActiveHolders(any());
    }

    // ---------------------------------------------------------------- rule 4: idempotence

    @Test
    void otorgarUnRolQueYaTieneNoEscribeFilaDeAuditoria() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.PLAYER));

        AdminUserDetailResponse response = userRoleService.grantRole("target", PlatformRole.PLAYER, "otra vez", ADMIN_ACTOR);

        assertThat(response.roles()).containsExactly("Player");
        verify(userRoleChangeRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
        verify(userService, never()).evictAuthCache(any());
    }

    @Test
    void quitarUnRolYaRevocadoNoEscribeFilaDeAuditoria() {
        User target = persistedUser("target");
        List<UserRole> grants = grants(target, PlatformRole.MASTER);
        grants.getFirst().revoke();
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants);

        AdminUserDetailResponse response = userRoleService.revokeRole("target", PlatformRole.MASTER, "de nuevo", ADMIN_ACTOR);

        assertThat(response.roles()).isEmpty();
        verify(userRoleChangeRepository, never()).save(any());
        verify(userService, never()).evictAuthCache(any());
    }

    @Test
    void quitarUnRolQueNuncaTuvoNoEscribeFilaDeAuditoria() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.PLAYER));

        userRoleService.revokeRole("target", PlatformRole.MASTER, "nunca lo tuvo", ADMIN_ACTOR);

        verify(userRoleChangeRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- rule 5: restore, never insert

    @Test
    void restaurarUnRolRevocadoFlipeaElStatusEnVezDeInsertarOtraFila() {
        User target = persistedUser("target");
        List<UserRole> grants = grants(target, PlatformRole.MASTER);
        UserRole revoked = grants.getFirst();
        revoked.revoke();
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-admin")).thenReturn(persistedUser("actor-admin"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants);

        AdminUserDetailResponse response = userRoleService.grantRole("target", PlatformRole.MASTER, "vuelve", ADMIN_ACTOR);

        assertThat(revoked.getStatus()).isEqualTo(UserRoleStatus.Allowed);
        assertThat(response.roles()).containsExactly("Master");
        // No second row was built: the key of users_roles is (user_id, role_id).
        verify(roleRepository, never()).findByName(any());
        ArgumentCaptor<UserRole> saved = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(revoked);
    }

    // ---------------------------------------------------------------- rule 6: the cache lever

    @Test
    void otorgarUnRolInvalidaLaCacheDeAutorizacionDelObjetivo() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-admin")).thenReturn(persistedUser("actor-admin"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(new ArrayList<>());
        when(roleRepository.findByName("Master")).thenReturn(Optional.of(role(PlatformRole.MASTER)));

        userRoleService.grantRole("target", PlatformRole.MASTER, "dale", ADMIN_ACTOR);

        verify(userService).evictAuthCache("target");
    }

    @Test
    void quitarUnRolInvalidaLaCacheDeAutorizacionDelObjetivo() {
        User target = persistedUser("target");
        when(userService.getById("target")).thenReturn(target);
        when(userService.getById("actor-admin")).thenReturn(persistedUser("actor-admin"));
        when(userRoleRepository.findAllGrants("target")).thenReturn(grants(target, PlatformRole.MASTER));

        userRoleService.revokeRole("target", PlatformRole.MASTER, "chau", ADMIN_ACTOR);

        verify(userService).evictAuthCache("target");
    }

    // ---------------------------------------------------------------- the target has to exist

    @Test
    void otorgarUnRolAUnUsuarioInexistenteEs404() {
        when(userService.getById("missing")).thenThrow(new NotFoundException("User not found: missing"));

        assertThatThrownBy(() -> userRoleService.grantRole("missing", PlatformRole.MASTER, "x", ADMIN_ACTOR))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * La invariante del último owner se decide con una lectura con bloqueo, no con un conteo
     * optimista: el mutex sobre la fila de `roles` primero, y después las filas de owner, frescas.
     * El unitario fija que se consulten las dos y en ese orden; que de verdad serialicen es de
     * {@code UserRoleServiceIT}, contra MySQL real.
     */
    private void stubOwnerCount(int owners) {
        when(roleRepository.lockByName("Owner")).thenReturn(Optional.of(role(PlatformRole.OWNER)));
        List<UserRole> holders = new ArrayList<>();
        for (int i = 0; i < owners; i++) {
            holders.add(new UserRole(persistedUser("owner-" + i), role(PlatformRole.OWNER)));
        }
        when(userRoleRepository.lockActiveHolders("Owner")).thenReturn(holders);
    }

    private List<UserRoleChange> savedRoleChanges() {
        ArgumentCaptor<UserRoleChange> captor = ArgumentCaptor.forClass(UserRoleChange.class);
        verify(userRoleChangeRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static List<UserRole> grants(User user, PlatformRole... roles) {
        List<UserRole> grants = new ArrayList<>();
        for (PlatformRole role : roles) {
            grants.add(new UserRole(user, role(role)));
        }
        return grants;
    }

    private static Role role(PlatformRole role) {
        Role entity = new Role(role.roleName(), null);
        ReflectionTestUtils.setField(entity, "id", "role-" + role.name().toLowerCase(java.util.Locale.ROOT));
        return entity;
    }

    private static User persistedUser(String id) {
        User user = new User("discord-" + id, id);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 1, 1, 12, 0));
        return user;
    }
}
