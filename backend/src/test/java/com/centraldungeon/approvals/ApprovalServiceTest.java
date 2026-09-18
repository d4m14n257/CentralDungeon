package com.centraldungeon.approvals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.centraldungeon.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserRoleService;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Every rule of the single approval mechanism (contrato F3.2, 3), one test each.
 *
 * <p>Two of them are what the slice is actually about and the rest exists so they cannot be broken
 * quietly:
 *
 * <ul>
 *   <li><b>The referenced entity is validated before the insert</b> (#78). There is no foreign key
 *       behind those two columns, so this test is the only thing standing between the application and
 *       a row pointing at nothing. Its twin at resolution time is here too, and so is the sweep's
 *       test, next door in {@code ApprovalOrphanCheckServiceTest} - the price of #78 is three things,
 *       and this file covers two of them.</li>
 *   <li><b>Approving a MasterGrant goes through {@code UserRoleService}</b> and not through a second
 *       path writing the same row. Asserted as a call with the exact arguments, because "the person
 *       ends up with the role" would also pass if the service grew its own way of granting it - which
 *       is the failure fase-3-admin-owner.md 4 names outright.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalServiceTest {

    private static final CurrentUser REQUESTER = new CurrentUser("user-1", Set.of("Player"));

    private static final CurrentUser ADMIN = new CurrentUser("admin-1", Set.of("Admin"));

    private static final String WHY = "Quiero dirigir una mesa de terror";

    private static final String NOTE = "Tiene karma de sobra y dos mesas jugadas";

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private ApprovalEntityResolver entityResolver;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private NotificationService notificationService;

    private final ApprovalMapper approvalMapper = Mappers.getMapper(ApprovalMapper.class);

    private ApprovalService service() {
        return new ApprovalService(
                approvalRequestRepository,
                entityResolver,
                userService,
                userRepository,
                userRoleRepository,
                userRoleService,
                notificationService,
                approvalMapper);
    }

    // ----------------------------------------------------------------- submit

    @Test
    void abreElPedidoPendienteApuntandoAQuienLoPide() {
        User requester = allowedRequester();
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        ApprovalRequestDetailResponse response = service().submit(ApprovalRequestType.TableOpen, WHY, REQUESTER);

        assertThat(response.status()).isEqualTo("Pending");
        assertThat(response.type()).isEqualTo("TableOpen");
        assertThat(response.justification()).isEqualTo(WHY);
        assertThat(response.requestedByName()).isEqualTo(requester.getDiscordUsername());
        // (d) of the contract: TableOpen asks for a table that does not exist yet, and both columns
        // are NOT NULL, so the request points at the person who made it.
        assertThat(response.entityType()).isEqualTo("user");
        assertThat(response.entityId()).isEqualTo("user-1");
        assertThat(response.resolvedAt()).isNull();
        assertThat(response.resolutionNote()).isNull();
        // #100: nothing writes the queue's two columns in F3.2.
        assertThat(response.claimedByName()).isNull();
    }

    /** The whole of #78 at the writing end: the reference is checked, and it is checked <em>first</em>. */
    @Test
    void validaLaEntidadReferenciadaAntesDeInsertar() {
        allowedRequester();
        when(entityResolver.exists("user", "user-1")).thenReturn(false);

        assertThatThrownBy(() -> service().submit(ApprovalRequestType.General, WHY, REQUESTER))
                .isInstanceOf(NotFoundException.class);

        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    void unPedidoNoNotificaANadie() {
        allowedRequester();
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        service().submit(ApprovalRequestType.General, WHY, REQUESTER);

        // #100: los ítems de trabajo de admin no se duplican como notificaciones. La bandeja los
        // muestra; una notificación por pedido sería la copia que #100 evitó.
        verifyNoInteractions(notificationService);
    }

    @Test
    void elSegundoPedidoPendienteDelMismoTipoEsUnConflicto() {
        allowedRequester();
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(approvalRequestRepository.existsByRequestTypeAndRequestedBy_IdAndStatus(
                        ApprovalRequestType.General, "user-1", ApprovalStatus.Pending))
                .thenReturn(true);

        assertThatThrownBy(() -> service().submit(ApprovalRequestType.General, WHY, REQUESTER))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ALREADY_PENDING);

        verify(approvalRequestRepository, never()).save(any());
    }

    /** Un pendiente por tipo, no uno por persona: pedir dos cosas distintas a la vez es legítimo. */
    @Test
    void unPendienteDeOtroTipoNoBloqueaElPedido() {
        allowedRequester();
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(approvalRequestRepository.existsByRequestTypeAndRequestedBy_IdAndStatus(
                        ApprovalRequestType.MasterGrant, "user-1", ApprovalStatus.Pending))
                .thenReturn(true);
        when(approvalRequestRepository.existsByRequestTypeAndRequestedBy_IdAndStatus(
                        ApprovalRequestType.General, "user-1", ApprovalStatus.Pending))
                .thenReturn(false);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        assertThat(service().submit(ApprovalRequestType.General, WHY, REQUESTER).status()).isEqualTo("Pending");
    }

    @Test
    void pedirElRolDeMasterTeniendoloYaEsUnConflicto() {
        allowedRequester();
        when(userRoleRepository.findActiveRoleNames("user-1")).thenReturn(Set.of("Player", "Master"));

        assertThatThrownBy(() -> service().submit(ApprovalRequestType.MasterGrant, WHY, REQUESTER))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.MASTER_ROLE_ALREADY_HELD);

        // Y no como un pedido que un admin tiene que leer para descubrir que no hacía falta.
        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    void pedirElRolDeMasterSinTenerloAbreElPedido() {
        allowedRequester();
        when(userRoleRepository.findActiveRoleNames("user-1")).thenReturn(Set.of("Player"));
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        assertThat(service().submit(ApprovalRequestType.MasterGrant, WHY, REQUESTER).type())
                .isEqualTo("MasterGrant");
    }

    @Test
    void unaCuentaBloqueadaNoAbrePedidos() {
        User blocked = allowedRequester();
        blocked.setStatus(UserStatus.Blocked);

        assertThatThrownBy(() -> service().submit(ApprovalRequestType.General, WHY, REQUESTER))
                .isInstanceOf(ForbiddenActionException.class);

        verify(approvalRequestRepository, never()).save(any());
    }

    // ------------------------------------------------------- approve / reject

    /**
     * The sentence this whole slice is for: «un admin lo aprueba y el rol queda otorgado por el
     * {@code UserRoleService} de F3.1, no por una segunda ruta que haga lo mismo».
     */
    @Test
    void aprobarUnMasterGrantOtorgaElRolPorElUserRoleService() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        service().approve("req-1", NOTE, ADMIN);

        verify(userRoleService).grantRole("user-1", PlatformRole.MASTER, NOTE, ADMIN);
    }

    /** Y el motivo de la resolución es el motivo del otorgamiento: la fila de auditoría no nace vacía. */
    @Test
    void elMotivoDeLaResolucionEsElMotivoDelOtorgamiento() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        service().approve("req-1", NOTE, ADMIN);

        ArgumentCaptor<String> justification = ArgumentCaptor.forClass(String.class);
        verify(userRoleService).grantRole(eq("user-1"), eq(PlatformRole.MASTER), justification.capture(), eq(ADMIN));
        assertThat(justification.getValue()).isEqualTo(NOTE);
    }

    /**
     * Aprobar un {@code TableOpen} no crea la mesa: el pedido no trae nombre, sistema, cupo ni agenda,
     * así que crearla automáticamente no es una opción descartada sino una imposible (#72, #90).
     */
    @Test
    void aprobarUnTableOpenNoCreaLaMesaNiOtorgaNingunRol() {
        ApprovalRequest request = pending(ApprovalRequestType.TableOpen);
        resolvable(request);

        ApprovalRequestDetailResponse response = service().approve("req-1", NOTE, ADMIN);

        assertThat(response.status()).isEqualTo("Approved");
        verifyNoInteractions(userRoleService);
    }

    @Test
    void aprobarUnGeneralNoTieneEfectoMasAlaDeQuedarResuelto() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        resolvable(request);

        assertThat(service().approve("req-1", NOTE, ADMIN).status()).isEqualTo("Approved");
        verifyNoInteractions(userRoleService);
    }

    @Test
    void aprobarSellaQuienResolvioCuandoYPorQue() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        User admin = resolvable(request);

        ApprovalRequestDetailResponse response = service().approve("req-1", NOTE, ADMIN);

        assertThat(response.resolvedByName()).isEqualTo(admin.getDiscordUsername());
        assertThat(response.resolutionNote()).isEqualTo(NOTE);
        assertThat(response.resolvedAt()).isNotNull();
    }

    @Test
    void rechazarSellaLoMismoYNoOtorgaNada() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        ApprovalRequestDetailResponse response = service().reject("req-1", "No todavía", ADMIN);

        assertThat(response.status()).isEqualTo("Rejected");
        assertThat(response.resolutionNote()).isEqualTo("No todavía");
        verifyNoInteractions(userRoleService);
    }

    @Test
    void unPedidoYaResueltoNoSeVuelveAResolver() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        request.resolve(ApprovalStatus.Approved, user("admin-1", "otra-admin"), "ya estaba");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().approve("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ALREADY_RESOLVED);
    }

    @Test
    void rechazarAlgoYaResueltoTampocoSePuede() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        request.resolve(ApprovalStatus.Rejected, user("admin-1", "otra-admin"), "ya estaba");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().reject("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ALREADY_RESOLVED);
    }

    /**
     * La otra mitad de #78. La fila sobrevive a lo que apunta a propósito (#126) — «una solicitud
     * sobre una mesa borrada sigue siendo un hecho» — pero resolverla igual sería actuar sobre un
     * fantasma.
     */
    @Test
    void siLaEntidadReferenciadaDesaparecioNoSeResuelve() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists("user", "user-1")).thenReturn(false);

        assertThatThrownBy(() -> service().approve("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ENTITY_GONE);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.Pending);
        verifyNoInteractions(userRoleService);
    }

    @Test
    void rechazarAlgoQueApuntaAUnFantasmaTampocoSePuede() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists("user", "user-1")).thenReturn(false);

        assertThatThrownBy(() -> service().reject("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ENTITY_GONE);
    }

    @Test
    void resolverAlgoQueNoExisteEs404() {
        when(approvalRequestRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().approve("nope", NOTE, ADMIN)).isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------ notificación

    @Test
    void aprobarNotificaAlSolicitanteYNoALosAdmins() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        service().approve("req-1", NOTE, ADMIN);

        // Al solicitante, con destino a su propio perfil - nunca a /admin/requests, que no puede abrir.
        verify(notificationService)
                .notifyApprovalResolved("user-1", NotificationType.ApprovalRequestApproved, "user", "user-1");
    }

    @Test
    void rechazarNotificaElOtroTipo() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        resolvable(request);

        service().reject("req-1", NOTE, ADMIN);

        verify(notificationService)
                .notifyApprovalResolved("user-1", NotificationType.ApprovalRequestRejected, "user", "user-1");
    }

    // ----------------------------------------------------------------- lectura

    /**
     * La pantalla que provoca el pedido pregunta «¿ya tengo uno de estos abierto?», y tiene que poder
     * preguntarlo de verdad: leer la página uno de todo y deducirlo es cómo el botón vuelve a
     * ofrecerse en cuanto un pendiente viejo queda debajo de veinte resueltos. El filtro entra por
     * {@code ?q=} y no por un parámetro suelto (arquitectura.md 2.5).
     */
    @Test
    void misPedidosAceptanElMismoFiltroQueElListadoAdmin() {
        when(approvalRequestRepository.findAll(ArgumentMatchers.<Specification<ApprovalRequest>>any(), eq(FIRST_PAGE)))
                .thenReturn(new PageImpl<>(List.of()));

        service().listMine("user-1", "/status Pending", FIRST_PAGE);

        verify(approvalRequestRepository)
                .findAll(ArgumentMatchers.<Specification<ApprovalRequest>>any(), eq(FIRST_PAGE));
    }

    /**
     * Que el filtro del solicitante quede forzado y no se pueda ensanchar desde la caja se prueba
     * sobre la specification misma, en {@code ApprovalSearchSpecificationTest}: es donde vive la
     * regla. Acá alcanza con que la lectura pase por ella.
     */
    @Test
    void elDetalleDeUnPedidoQueNoExisteEs404() {
        when(approvalRequestRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getDetail("nope"))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .asInstanceOf(InstanceOfAssertFactories.STRING)
                .isEqualTo("NOT_FOUND");
    }

    // ------------------------------------------------------------------ armado

    /** The requester, Allowed, with no roles and no pending request unless a test says otherwise. */
    private User allowedRequester() {
        User requester = user("user-1", "carla");
        // Stubbed on the LOCKING read, not on a plain load: submit takes the requester's row with
        // PESSIMISTIC_WRITE as the first thing it does, and that is what serializes two submits by one
        // person. Stubbing getById instead would leave these tests green over a service that had
        // dropped the lock (#252).
        when(userRepository.lockById("user-1")).thenReturn(Optional.of(requester));
        when(userRoleRepository.findActiveRoleNames("user-1")).thenReturn(Set.of("Player"));
        when(approvalRequestRepository.existsByRequestTypeAndRequestedBy_IdAndStatus(any(), anyString(), any()))
                .thenReturn(false);
        return requester;
    }

    /** A request that can be resolved: it is found, its reference resolves, and the admin exists. */
    private User resolvable(ApprovalRequest request) {
        User admin = user("admin-1", "damian");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists(request.getEntityType(), request.getEntityId())).thenReturn(true);
        when(userService.getById("admin-1")).thenReturn(admin);
        return admin;
    }

    private static ApprovalRequest pending(ApprovalRequestType type) {
        ApprovalRequest request =
                new ApprovalRequest(type, type.entityType(), "user-1", user("user-1", "carla"), WHY);
        return persisted(request);
    }

    /** JPA stamps the id and createdAt on persist; a unit test has no JPA, so it does it here. */
    private static ApprovalRequest persisted(ApprovalRequest request) {
        ReflectionTestUtils.setField(request, "id", "req-1");
        ReflectionTestUtils.setField(request, "createdAt", java.time.LocalDateTime.now());
        return request;
    }

    private static User user(String id, String discordUsername) {
        User user = new User("discord-" + id, discordUsername);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
