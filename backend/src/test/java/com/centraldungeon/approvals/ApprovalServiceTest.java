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
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.dto.BanRequestResponse;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    /** The pause's effect, approved and rejected alike (#32) - never written in ApprovalService. */
    @Mock
    private com.centraldungeon.tables.GameTableService gameTableService;

    /** The veto's effect (#39) - the one place `Blocked` is written. */
    @Mock
    private com.centraldungeon.registrations.RegistrationService registrationService;

    /** Answers who is a table's Primary, for the one type an admin does not resolve (#39). */
    @Mock
    private com.centraldungeon.tables.MasterService masterService;

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
                gameTableService,
                registrationService,
                masterService,
                approvalMapper);
    }

    // ----------------------------------------------------------------- submit

    @Test
    void itOpensThePendingRequestPointingAtWhoAsked() {
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
        // #100: a request is born unreserved. Only the shared tray writes those two columns.
        assertThat(response.claimedByName()).isNull();
    }

    /** The whole of #78 at the writing end: the reference is checked, and it is checked <em>first</em>. */
    @Test
    void itValidatesTheReferencedEntityBeforeInserting() {
        allowedRequester();
        when(entityResolver.exists("user", "user-1")).thenReturn(false);

        assertThatThrownBy(() -> service().submit(ApprovalRequestType.General, WHY, REQUESTER))
                .isInstanceOf(NotFoundException.class);

        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    void submittingNotifiesNobody() {
        allowedRequester();
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        service().submit(ApprovalRequestType.General, WHY, REQUESTER);

        // #100: admin work items are not duplicated as notifications. The tray shows them; one
        // notification per request would be the copy #100 avoided.
        verifyNoInteractions(notificationService);
    }

    @Test
    void aSecondPendingRequestOfTheSameTypeIsAConflict() {
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

    /** One pending per type, not one per person: asking for two different things at once is legitimate. */
    @Test
    void aPendingRequestOfAnotherTypeDoesNotBlockIt() {
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
    void askingForTheMasterRoleWhileHoldingItIsAConflict() {
        allowedRequester();
        when(userRoleRepository.findActiveRoleNames("user-1")).thenReturn(Set.of("Player", "Master"));

        assertThatThrownBy(() -> service().submit(ApprovalRequestType.MasterGrant, WHY, REQUESTER))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.MASTER_ROLE_ALREADY_HELD);

        // And not as a request an admin has to read to discover it was never needed.
        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    void askingForTheMasterRoleWithoutItOpensTheRequest() {
        allowedRequester();
        when(userRoleRepository.findActiveRoleNames("user-1")).thenReturn(Set.of("Player"));
        when(entityResolver.exists("user", "user-1")).thenReturn(true);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        assertThat(service().submit(ApprovalRequestType.MasterGrant, WHY, REQUESTER).type())
                .isEqualTo("MasterGrant");
    }

    @Test
    void aBlockedAccountOpensNoRequests() {
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
    void approvingAMasterGrantGrantsTheRoleThroughUserRoleService() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        service().approve("req-1", NOTE, ADMIN);

        verify(userRoleService).grantRole("user-1", PlatformRole.MASTER, NOTE, ADMIN);
    }

    /** And the resolution note is the reason the role was granted: the audit row is not born empty. */
    @Test
    void theResolutionNoteIsTheReasonTheRoleWasGranted() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        service().approve("req-1", NOTE, ADMIN);

        ArgumentCaptor<String> justification = ArgumentCaptor.forClass(String.class);
        verify(userRoleService).grantRole(eq("user-1"), eq(PlatformRole.MASTER), justification.capture(), eq(ADMIN));
        assertThat(justification.getValue()).isEqualTo(NOTE);
    }

    /**
     * Aprobar un {@code TableOpen} no crea la mesa: el pedido no trae nombre, sistema, cupo ni agenda,
     * so creating it automatically is not a refused option but an impossible one (#72, #90).
     */
    @Test
    void approvingATableOpenCreatesNoTableAndGrantsNoRole() {
        ApprovalRequest request = pending(ApprovalRequestType.TableOpen);
        resolvable(request);

        ApprovalRequestDetailResponse response = service().approve("req-1", NOTE, ADMIN);

        assertThat(response.status()).isEqualTo("Approved");
        verifyNoInteractions(userRoleService);
    }

    @Test
    void approvingAGeneralHasNoEffectBeyondBeingResolved() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        resolvable(request);

        assertThat(service().approve("req-1", NOTE, ADMIN).status()).isEqualTo("Approved");
        verifyNoInteractions(userRoleService);
    }

    @Test
    void approvingStampsWhoResolvedItWhenAndWhy() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        User admin = resolvable(request);

        ApprovalRequestDetailResponse response = service().approve("req-1", NOTE, ADMIN);

        assertThat(response.resolvedByName()).isEqualTo(admin.getDiscordUsername());
        assertThat(response.resolutionNote()).isEqualTo(NOTE);
        assertThat(response.resolvedAt()).isNotNull();
    }

    @Test
    void rejectingStampsTheSameAndGrantsNothing() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        ApprovalRequestDetailResponse response = service().reject("req-1", "No todavía", ADMIN);

        assertThat(response.status()).isEqualTo("Rejected");
        assertThat(response.resolutionNote()).isEqualTo("No todavía");
        verifyNoInteractions(userRoleService);
    }

    @Test
    void anAlreadyResolvedRequestIsNotResolvedAgain() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        request.resolve(ApprovalStatus.Approved, user("admin-1", "otra-admin"), "ya estaba");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().approve("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ALREADY_RESOLVED);
    }

    @Test
    void rejectingSomethingAlreadyResolvedIsRefusedToo() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        request.resolve(ApprovalStatus.Rejected, user("admin-1", "otra-admin"), "ya estaba");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().reject("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ALREADY_RESOLVED);
    }

    /**
     * The other half of #78. The row outlives what it points at on purpose (#126) — «una solicitud
     * sobre una mesa borrada sigue siendo un hecho» — but resolving it anyway would be acting on a
     * fantasma.
     */
    @Test
    void siLaEntidadReferenciadaDesaparecioNoSeResuelve() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        // Sin reservar, y ahora eso es exactamente el caso real: un pedido libre pasa el chequeo de la
        // tray and reaches the reference check, which is the one this test is looking at.
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
    void rejectingSomethingPointingAtAGhostIsRefusedToo() {
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

    // ------------------------------------------------- la reserva de la bandeja compartida

    /**
     * Case 1 of 3: <b>a request nobody claimed is resolved</b>, and this is the test that exists so
     * nobody tightens the rule again.
     *
     * <p>It was the other way round for a while -«resolver exige tenerlo reservado»- and it left
     * {@code /admin/requests} unusable in the real application: that screen has Approve and Reject and
     * <b>no way to claim anything</b>, so every resolution answered 409 over a reservation it could not
     * offer. Two designs collided - #176 gives requests a screen of their own, §5 of modelo-datos
     * assumed the tray was the only place anything is resolved - and the strict rule made the tray
     * compulsory for a flow that never passed through it.
     */
    @Test
    void aRequestNobodyClaimedIsResolved() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        // Deliberately unclaimed: it is the /admin/requests path, which is the common one.
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists(request.getEntityType(), request.getEntityId())).thenReturn(true);
        when(userService.getById("admin-1")).thenReturn(user("admin-1", "damian"));

        assertThat(service().approve("req-1", NOTE, ADMIN).status()).isEqualTo("Approved");

        verify(userRoleService).grantRole("user-1", PlatformRole.MASTER, NOTE, ADMIN);
    }

    /** Case 2 of 3: the request the actor already holds, which is the path down from the tray. */
    @Test
    void aRequestTheActorAlreadyHeldIsResolved() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        User admin = resolvable(request);
        request.claim(admin, LocalDateTime.now());

        assertThat(service().approve("req-1", NOTE, ADMIN).status()).isEqualTo("Approved");
    }

    /**
     * Case 3 of 3, and the only one refused: <b>somebody else</b> holds it. A stale link, a second tab,
     * a tray that has not refreshed - the three ways of taking work off a colleague's desk, which is
     * exactly what #100 buys.
     *
     * <p>And it is not the rule consistency rests on: two admins resolving the same row unclaimed are
     * serialized by the pessimistic lock (#256) and the second is told «it was already resolved». This
     * one is about courtesy between colleagues, not about correctness.
     */
    @Test
    void aRequestAnotherAdminHoldsCannotBeApproved() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        request.claim(user("admin-2", "otra-admin"), LocalDateTime.now());
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().approve("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        // And it left nothing half done: not the role, not the bell, not the resolved row.
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.Pending);
        verifyNoInteractions(userRoleService);
        verifyNoInteractions(notificationService);
    }

    /** Rejecting is the other half of the same act: a rule holding for only one would be an open door. */
    @Test
    void aRequestAnotherAdminHoldsCannotBeRejected() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        request.claim(user("admin-2", "otra-admin"), LocalDateTime.now());
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().reject("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.ITEM_ALREADY_CLAIMED);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.Pending);
    }

    /**
     * The order of the two 409s is not incidental: when both are true, «somebody already answered
     * this» is the truer sentence. Naming the colleague who claimed a request that no longer needs
     * answering would send the reader to ask them about nothing.
     */
    @Test
    void siYaEstaResueltoLoDiceAntesDeNombrarAQuienLoTiene() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        request.claim(user("admin-2", "otra-admin"), LocalDateTime.now());
        request.resolve(ApprovalStatus.Approved, user("admin-2", "otra-admin"), "ya estaba");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().approve("req-1", NOTE, ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ConflictException.REQUEST_ALREADY_RESOLVED);
    }

    // ------------------------------------------------------------ notification

    @Test
    void approvingNotifiesTheRequesterAndNotTheAdmins() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        service().approve("req-1", NOTE, ADMIN);

        // To the requester, pointed at their own profile - never at /admin/requests, which they cannot open.
        verify(notificationService)
                .notifyApprovalResolved("user-1", NotificationType.ApprovalRequestApproved, "user", "user-1");
    }

    @Test
    void rejectingNotifiesWithTheOtherType() {
        ApprovalRequest request = pending(ApprovalRequestType.General);
        resolvable(request);

        service().reject("req-1", NOTE, ADMIN);

        verify(notificationService)
                .notifyApprovalResolved("user-1", NotificationType.ApprovalRequestRejected, "user", "user-1");
    }

    // ----------------------------------------------------------------- lectura

    /**
     * The screen that provokes the request asks «do I already have one of these open?», and it has to be
     * able to really ask it: reading page one of everything and inferring it is how the button comes back
     * as soon as an old pending one falls below twenty resolved ones. The filter goes through
     * {@code ?q=} and not through a parameter of its own (arquitectura.md 2.5).
     */
    @Test
    void myRequestsAcceptTheSameFilterAsTheAdminListing() {
        when(approvalRequestRepository.findAll(ArgumentMatchers.<Specification<ApprovalRequest>>any(), eq(FIRST_PAGE)))
                .thenReturn(new PageImpl<>(List.of()));

        service().listMine("user-1", "/status Pending", FIRST_PAGE);

        verify(approvalRequestRepository)
                .findAll(ArgumentMatchers.<Specification<ApprovalRequest>>any(), eq(FIRST_PAGE));
    }

    /**
     * That the requester's filter is forced and cannot be widened from the box is proven
     * against the specification itself, in {@code ApprovalSearchSpecificationTest}: that is where the
     * rule. Here it is enough that the read goes through it.
     */
    @Test
    void theDetailOfARequestThatDoesNotExistIs404() {
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

    /**
     * A request that can be resolved: it is found, its reference resolves, and the admin exists.
     *
     * <p><b>Deliberately unreserved</b>, which is the common path and the one {@code /admin/requests}
     * takes: that screen resolves without ever passing through the shared tray. The reservation only
     * matters when <em>another</em> admin holds it, and that is pinned in its own tests above.
     */
    // ------------------------------------- the two types F3.4 brought along with their producer

    /**
     * Approving a {@code TablePause} applies it <b>through the {@code GameTableService}</b> and not by
     * writing the status here - the same clause #42 demands for the role, applied to the lifecycle. And
     * the resolution note is the pause's justification (#32, modelo-datos.md:835).
     */
    @Test
    void approvingATablePauseAppliesItThroughTheGameTableService() {
        ApprovalRequest request = pendingAbout(ApprovalRequestType.TablePause, "table-1");
        resolvable(request);

        service().approve("req-1", NOTE, ADMIN);

        verify(gameTableService).applyApprovedPause("table-1", "admin-1", NOTE);
    }

    /**
     * <b>And rejecting it has an effect, which is what did not exist before.</b> {@code reject} had no
     * {@code switch} because until F3.4 a rejection did nothing; asking for the pause already moved the
     * table, so saying no has to put it back in {@code InProgress} or it is stranded.
     */
    @Test
    void rejectingATablePausePutsTheTableBack() {
        ApprovalRequest request = pendingAbout(ApprovalRequestType.TablePause, "table-1");
        resolvable(request);

        service().reject("req-1", NOTE, ADMIN);

        verify(gameTableService).revertRequestedPause("table-1", "admin-1", NOTE);
    }

    /** The older types still have no effect when rejected: that is what a rejection normally is. */
    @Test
    void rejectingTheOlderTypesStillHasNoEffect() {
        ApprovalRequest request = pending(ApprovalRequestType.MasterGrant);
        resolvable(request);

        service().reject("req-1", NOTE, ADMIN);

        verifyNoInteractions(userRoleService);
        verifyNoInteractions(gameTableService);
    }

    /**
     * <b>A {@code PlayerBan} is resolved by the table's {@code Primary}, and not by an admin</b> (#39
     * over #90). It is the most delicate decision of the contract, and this is what makes it true: the
     * admin passes the route's {@code @PreAuthorize} and is stopped by the rule, which is what an
     * annotation could not express - being <em>this</em> table's {@code Primary} is a row in
     * {@code masters}.
     */
    @Test
    void anAdminCannotResolveAVetoRequest() {
        ApprovalRequest request = pendingAbout(ApprovalRequestType.PlayerBan, "reg-1");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists(request.getEntityType(), "reg-1")).thenReturn(true);
        when(registrationService.tableIdOf("reg-1")).thenReturn("table-1");
        when(masterService.isPrimaryOf("table-1", "admin-1")).thenReturn(false);

        assertThatThrownBy(() -> service().approve("req-1", NOTE, ADMIN))
                .isInstanceOf(ForbiddenActionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ForbiddenActionException.NOT_PRIMARY_MASTER);

        verify(registrationService, never()).applyApprovedBlock(anyString(), anyString(), anyString());
    }

    /**
     * And the {@code Primary} can, with the veto written <b>through the {@code RegistrationService}</b>:
     * there is exactly one place that writes {@code Blocked}, the same way there is exactly one that
     * grants a role.
     */
    @Test
    void thePrimaryResolvesTheVetoRequestAndTheRegistrationServiceWritesTheVeto() {
        ApprovalRequest request = pendingAbout(ApprovalRequestType.PlayerBan, "reg-1");
        CurrentUser primary = new CurrentUser("primary-1", Set.of("Master"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists(request.getEntityType(), "reg-1")).thenReturn(true);
        when(registrationService.tableIdOf("reg-1")).thenReturn("table-1");
        when(masterService.isPrimaryOf("table-1", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(user("primary-1", "primary"));

        service().approve("req-1", NOTE, primary);

        verify(registrationService).applyApprovedBlock("reg-1", "primary-1", NOTE);
    }

    /** Rejecting a veto request moves nothing: nothing had moved when it was asked for. */
    @Test
    void rejectingAVetoRequestDoesNotMoveTheApplication() {
        ApprovalRequest request = pendingAbout(ApprovalRequestType.PlayerBan, "reg-1");
        CurrentUser primary = new CurrentUser("primary-1", Set.of("Master"));
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists(request.getEntityType(), "reg-1")).thenReturn(true);
        when(registrationService.tableIdOf("reg-1")).thenReturn("table-1");
        when(masterService.isPrimaryOf("table-1", "primary-1")).thenReturn(true);
        when(userService.getById("primary-1")).thenReturn(user("primary-1", "primary"));

        service().reject("req-1", NOTE, primary);

        verify(registrationService, never()).applyApprovedBlock(anyString(), anyString(), anyString());
    }

    /**
     * {@code POST /api/v1/requests} only ever opens a request about the person asking, and still takes
     * no {@code entityId} (F3.2 §0d). The two types whose entity is something else have doors of their
     * own on the aggregate they are about; reaching for them here invents no entity.
     *
     * <p><b>400 and not 403</b>, by the rule the project keeps repeating: 403 is who you are, 400 is
     * what you sent. Anybody at all may open a request here - what cannot be opened here is a request
     * <em>of this type</em>, and no change of actor would make it work. The type travels in the body.
     */
    @Test
    void theGenericEndpointDoesNotOpenTheTypesThatAreAboutSomethingElse() {
        allowedRequester();

        assertThatThrownBy(() -> service().submit(ApprovalRequestType.TablePause, WHY, REQUESTER))
                .isInstanceOf(InvalidRequestException.class);
        verify(approvalRequestRepository, never()).save(any());
    }

    /**
     * <b>The veto request listing names who is to be vetoed.</b> It is the hole the contract had:
     * {@code ApprovalRequestSummaryResponse} says who asked and why, which is right for the shared tray
     * - there the entity <em>is</em> the requester - and leaves a {@code Primary} with two open requests
     * on their table telling them apart by the wording of the reason alone. A decision about a person is
     * taken by their name.
     */
    @Test
    void theVetoRequestListingNamesWhoIsToBeVetoed() {
        TableRegistration target = registrationOf("reg-1", user("player-9", "morgana"));
        ApprovalRequest request = pendingAbout(ApprovalRequestType.PlayerBan, "reg-1");
        when(masterService.isMasterOf("table-1", "primary-1")).thenReturn(true);
        when(registrationService.registrationsOf("table-1")).thenReturn(Map.of("reg-1", target));
        when(approvalRequestRepository.findByRequestTypeAndStatusAndEntityIdInOrderByCreatedAtAsc(
                        eq(ApprovalRequestType.PlayerBan), eq(ApprovalStatus.Pending), any()))
                .thenReturn(List.of(request));

        List<BanRequestResponse> pending = service().listBanRequests("table-1", "primary-1");

        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().requestId()).isEqualTo("req-1");
        assertThat(pending.getFirst().registrationId()).isEqualTo("reg-1");
        assertThat(pending.getFirst().targetUserId()).isEqualTo("player-9");
        assertThat(pending.getFirst().targetUserName()).isEqualTo("morgana");
        assertThat(pending.getFirst().requestedByName()).isEqualTo("carla");
        assertThat(pending.getFirst().justification()).isEqualTo(WHY);
    }

    /** Somebody who does not run the table does not read its veto requests (#17, #121, #135). */
    @Test
    void anOutsiderCannotReadATablesVetoRequests() {
        when(masterService.isMasterOf("table-1", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> service().listBanRequests("table-1", "stranger"))
                .isInstanceOf(ForbiddenActionException.class);
    }

    /** A registration as it comes back from the database, with the person it belongs to. */
    private static TableRegistration registrationOf(String id, User applicant) {
        TableRegistration registration = new TableRegistration(null, applicant, null);
        ReflectionTestUtils.setField(registration, "id", id);
        return registration;
    }

    private User resolvable(ApprovalRequest request) {
        User admin = user("admin-1", "damian");
        when(approvalRequestRepository.lockById("req-1")).thenReturn(Optional.of(request));
        when(entityResolver.exists(request.getEntityType(), request.getEntityId())).thenReturn(true);
        when(userService.getById("admin-1")).thenReturn(admin);
        return admin;
    }

    /** A pending request about something other than the requester - the two types F3.4 added. */
    private static ApprovalRequest pendingAbout(ApprovalRequestType type, String entityId) {
        return persisted(new ApprovalRequest(type, type.entityType(), entityId, user("user-1", "carla"), WHY));
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
