package com.centraldungeon.features.registrations;

import com.centraldungeon.features.approvals.ApprovalService;
import com.centraldungeon.features.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.features.registrations.dto.BanRequestResponse;
import com.centraldungeon.features.registrations.dto.BlockRegistrationRequest;
import com.centraldungeon.features.registrations.dto.RegistrationResponse;
import com.centraldungeon.features.registrations.dto.UnblockRegistrationRequest;
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
 * The veto, from the table's side: a master asking for one, the masters reading what is pending, and
 * lifting a veto.
 *
 * <p><b>Nobody vetoes here.</b> A veto is decided by an admin, who approves the request through the
 * shared approval endpoints ({@code /admin/requests/{id}/approve}); what a master does from this table
 * is ask, with a reason.
 *
 * <p><b>Its own controller and not more methods on {@link RegistrationController}.</b> That one is
 * about the intake - applying, accepting, turning down, withdrawing - and this is about barring
 * somebody from a table, which is a different act with its own error codes.
 *
 * <p><b>Every route hangs off the table</b>, unlike {@code /registrations/{id}/accept}: the
 * authorization is about <em>this</em> table, so the table belongs in the path where it can be read -
 * and the service checks that the application is the table's anyway.
 *
 * <p>Every method is {@code isAuthenticated()} and carries no role. Membership of the table decides,
 * and only a service can see it: a master an admin assigned may never have been given the
 * {@code Master} role at all.
 */
@RestController
@RequestMapping("/api/v1/game-tables/{tableId}")
public class RegistrationBanController {

    /** Where a veto is lifted. */
    private final RegistrationService registrationService;

    /** Where a veto is asked for and where the pending requests are read. */
    private final ApprovalService approvalService;

    /**
     * @param registrationService the service that lifts a veto
     * @param approvalService     the service that owns the veto requests
     */
    public RegistrationBanController(RegistrationService registrationService, ApprovalService approvalService) {
        this.registrationService = registrationService;
        this.approvalService = approvalService;
    }

    /**
     * Lifting a veto, by the table's {@code Primary} or by an admin.
     *
     * <p>The person goes back to being a {@code Player}. If the table has no seat left they come back
     * as a rejected application, so they see the table again and can apply once a seat frees.
     *
     * @param tableId        the table
     * @param registrationId the application
     * @param request        why the veto is being lifted, required
     * @param currentUser    the actor, from the token; the service checks they are the Primary or an
     *                       admin
     * @return 200 with the application, back to {@code Player} or rejected for lack of a seat. 403
     *         {@code NOT_PRIMARY_MASTER}, 404 when the application does not belong to this table, 409
     *         {@code REGISTRATION_NOT_BLOCKED} when there was no veto to lift
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
     * A master of the table asking the admins to veto somebody.
     *
     * <p>Nothing changes for the person while the request waits: they keep playing and seeing the table
     * until an admin approves it.
     *
     * @param tableId        the table
     * @param registrationId the application the veto is being asked for
     * @param request        the reason, required - the admin deciding reads exactly this
     * @param currentUser    the actor, from the token; the service checks they run the table
     * @return 200 with the request as it was opened. 403 when the actor does not run the table, 404 when
     *         the application is not this table's, 409 {@code REQUEST_ALREADY_PENDING} when a veto on
     *         this person is already waiting, or {@code REGISTRATION_ALREADY_BLOCKED} when they cannot be
     *         vetoed any more
     */
    @PostMapping("/registrations/{registrationId}/request-block")
    @PreAuthorize("isAuthenticated()")
    public ApprovalRequestDetailResponse requestBlock(
            @PathVariable String tableId,
            @PathVariable String registrationId,
            @Valid @RequestBody BlockRegistrationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.submitPlayerBan(tableId, registrationId, request.justification(), currentUser);
    }

    /**
     * The veto requests waiting on this table, for the masters who asked and for the admins who decide.
     *
     * <p>A list and not a page: it is bounded by how many people are at one table. Each line
     * <b>names the person it is about</b>, which is what {@link BanRequestResponse} exists for.
     *
     * @param tableId     the table
     * @param currentUser the actor, from the token; the service checks they run the table or are an admin
     * @return 200 with the pending veto requests, oldest first. 403 when the actor neither runs it nor is
     *         an admin
     */
    @GetMapping("/ban-requests")
    @PreAuthorize("isAuthenticated()")
    public List<BanRequestResponse> listBanRequests(
            @PathVariable String tableId, @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.listBanRequests(tableId, currentUser.userId());
    }
}
