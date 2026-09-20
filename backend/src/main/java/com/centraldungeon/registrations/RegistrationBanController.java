package com.centraldungeon.registrations;

import com.centraldungeon.approvals.ApprovalService;
import com.centraldungeon.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.approvals.dto.ResolveApprovalRequestRequest;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.registrations.dto.BanRequestResponse;
import com.centraldungeon.registrations.dto.BlockRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.registrations.dto.UnblockRegistrationRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The veto, from both ends (#39): the {@code Primary} who applies and lifts it, and the
 * {@code Secondary} who asks for one.
 *
 * <p><b>Its own controller and not six more methods on {@link RegistrationController}.</b> That one
 * is about the intake - applying, accepting, turning down, withdrawing - and this is about barring
 * somebody from a table, which is a different act with a different actor and its own error codes. It
 * is also the surface most likely to grow an appeal in a later phase.
 *
 * <p><b>Every route hangs off the table and not off the registration alone</b>, unlike
 * {@code /registrations/{id}/accept}. The authorization is «are you the {@code Primary} of
 * <em>this</em> table», so the table belongs in the path where it can be read (§2.6, #121) - and the
 * service checks it anyway, because a path that cannot express the wrong thing is not the same as a
 * rule being enforced.
 *
 * <p>Every method is {@code isAuthenticated()} and carries no role. Pertenencia decides, and only a
 * service can see it (#17, #121, #135): a {@code Primary} an admin assigned may never have been
 * given the {@code Master} role at all (#72).
 */
@RestController
@RequestMapping("/api/v1/game-tables/{tableId}")
public class RegistrationBanController {

    /** Where {@code Blocked} is written and lifted - the direct half of the veto. */
    private final RegistrationService registrationService;

    /** The one mechanism behind a co-master's request, and behind resolving one (#42, #39). */
    private final ApprovalService approvalService;

    /**
     * @param registrationService the service that owns the veto itself
     * @param approvalService     the service that owns the request a {@code Secondary} opens
     */
    public RegistrationBanController(RegistrationService registrationService, ApprovalService approvalService) {
        this.registrationService = registrationService;
        this.approvalService = approvalService;
    }

    /**
     * The {@code Primary} vetoing somebody from their table (#39).
     *
     * <p>It goes through {@code ApprovalService} rather than straight to {@code RegistrationService}
     * for one reason: any veto a co-master had <em>asked</em> for on this person has just been
     * answered in fact, and those rows have to be resolved in the same transaction or they sit
     * {@code Pending} for ever. The veto itself is still written in exactly one place.
     *
     * @param tableId        the table, which is what the authorization is about
     * @param registrationId the application to veto
     * @param request        the reason, required - it is what makes the veto reversible in practice
     * @param currentUser    the actor, from the token; the service checks they are the Primary
     * @return 200 with the application, now Blocked, carrying who vetoed it and when. 403
     *         {@code NOT_PRIMARY_MASTER} for a {@code Secondary} - it is who you are, not what you
     *         sent - 404 when the application does not belong to this table, and 409
     *         {@code REGISTRATION_ALREADY_BLOCKED} when it already was
     */
    @PostMapping("/registrations/{registrationId}/block")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse block(
            @PathVariable String tableId,
            @PathVariable String registrationId,
            @Valid @RequestBody BlockRegistrationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.blockDirectly(tableId, registrationId, request.justification(), currentUser);
    }

    /**
     * The {@code Primary} lifting a veto (#39).
     *
     * <p>The person goes back to where they were, read from the trail - {@code Candidate} if that is
     * what they were when it happened, and not a blanket {@code Player}.
     *
     * @param tableId        the table
     * @param registrationId the application
     * @param request        why the veto is being lifted, required
     * @param currentUser    the actor, from the token; the service checks they are the Primary
     * @return 200 with the application, back where it was. 403 {@code NOT_PRIMARY_MASTER}, 404 when
     *         the application does not belong to this table, 409 {@code REGISTRATION_NOT_BLOCKED}
     *         when there was no veto to lift
     */
    @PostMapping("/registrations/{registrationId}/unblock")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse unblock(
            @PathVariable String tableId,
            @PathVariable String registrationId,
            @Valid @RequestBody UnblockRegistrationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return registrationService.unblock(tableId, registrationId, currentUser.userId(), request.justification());
    }

    /**
     * A {@code Secondary} asking the {@code Primary} for a veto (#39).
     *
     * <p><b>A second endpoint rather than the first one behaving differently.</b> The screen already
     * knows which of the two the reader is and says, before the button is pressed, that what it sends
     * is a request (fase-3-admin-owner.md §4) - not afterwards. One endpoint that sometimes vetoes
     * and sometimes asks would have to answer with a record half of whose fields are null, which is
     * what R3 forbids; here the two answers are two types, and each is complete.
     *
     * <p>It takes the same body as {@link #block}: what a master writes is the reason either way.
     * What differs is who may call it and what comes back.
     *
     * @param tableId        the table
     * @param registrationId the application the veto is being asked for
     * @param request        the reason, required - the {@code Primary} reads exactly this
     * @param currentUser    the actor, from the token; the service checks they run the table
     * @return 200 with the request as it was opened. 403 when the actor does not run the table, 409
     *         when they already have one pending or the person cannot be vetoed any more
     */
    @PostMapping("/registrations/{registrationId}/request-block")
    @PreAuthorize("isAuthenticated()")
    public ApprovalRequestDetailResponse requestBlock(
            @PathVariable String tableId,
            @PathVariable String registrationId,
            @Valid @RequestBody BlockRegistrationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.submitPlayerBan(registrationId, request.justification(), currentUser);
    }

    /**
     * The veto requests waiting on this table, for the masters who run it.
     *
     * <p><b>It is not in {@code /admin/queue} and that is the decision, not an omission</b> (#39 over
     * #90): a veto between a co-master and a player of <em>this</em> table is decided by whoever runs
     * it. So the pending ones need a home, and this is it.
     *
     * <p>Readable by any master - the {@code Secondary} who asked has to see that they did - and
     * resolvable only by the {@code Primary}.
     *
     * <p>A list and not a page: it is bounded by how many people are at one table.
     *
     * <p>Each line <b>names the person it is about</b>, which is what {@link BanRequestResponse}
     * exists for and what the shared approval summary could not say.
     *
     * @param tableId     the table
     * @param currentUser the actor, from the token; the service checks they run the table
     * @return 200 with the pending veto requests, oldest first. 403 when the actor does not run it
     */
    @GetMapping("/ban-requests")
    @PreAuthorize("isAuthenticated()")
    public List<BanRequestResponse> listBanRequests(
            @PathVariable String tableId, @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.listBanRequests(tableId, currentUser.userId());
    }

    /**
     * The {@code Primary} granting a co-master's veto request (#39).
     *
     * <p>It goes through the same {@code ApprovalService.approve} an admin uses, which is why there
     * is no second way to write {@code Blocked}: the resolution is bookkept once, and the effect is
     * applied by {@code RegistrationService}. What decides that a {@code Primary} rather than an
     * admin may call it is the request's <b>type</b>, checked in the service (fase-3-admin-owner.md
     * §4: «el otorgamiento es la regla, no la puerta»).
     *
     * <p>The answer is the application, not the request: the screen that has this button is the
     * roster, and what re-renders is the row.
     *
     * @param tableId     the table
     * @param requestId   the request to grant
     * @param request     the resolution note, required in both directions (#42). It becomes the
     *                    veto's own reason
     * @param currentUser the actor, from the token; the service checks they are the Primary
     * @return 200 with the application, now Blocked. 403 {@code NOT_PRIMARY_MASTER}, 409
     *         {@code REQUEST_ALREADY_RESOLVED}
     */
    @PostMapping("/ban-requests/{requestId}/approve")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse approveBanRequest(
            @PathVariable String tableId,
            @PathVariable String requestId,
            @Valid @RequestBody ResolveApprovalRequestRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.approveBanRequest(tableId, requestId, request.resolutionNote(), currentUser);
    }

    /**
     * The {@code Primary} refusing a co-master's veto request (#39).
     *
     * <p>Nothing moves: the application was never touched when the request was opened, so refusing it
     * leaves the person exactly where they are. The reason is mandatory all the same (#42) - the
     * co-master who asked is the one who reads it.
     *
     * @param tableId     the table
     * @param requestId   the request to refuse
     * @param request     the resolution note, required
     * @param currentUser the actor, from the token; the service checks they are the Primary
     * @return 200 with the request, now Rejected. 403 {@code NOT_PRIMARY_MASTER}
     */
    @PostMapping("/ban-requests/{requestId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApprovalRequestDetailResponse rejectBanRequest(
            @PathVariable String tableId,
            @PathVariable String requestId,
            @Valid @RequestBody ResolveApprovalRequestRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.rejectBanRequest(tableId, requestId, request.resolutionNote(), currentUser);
    }
}
