package com.centraldungeon.approvals;

import com.centraldungeon.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.approvals.dto.ApprovalRequestSummaryResponse;
import com.centraldungeon.approvals.dto.ResolveApprovalRequestRequest;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /admin/requests}: the answering end. A separate controller from
 * {@link ApprovalRequestController} because they are two audiences, not two verbs on one resource -
 * one is open to anybody logged in and this one is not.
 *
 * <p><b>Admin and Owner are enumerated on every method.</b> There is no {@code RoleHierarchy} in this
 * project: an owner can do everything an admin can by being listed, never by inheriting (#37, #89,
 * #123), and the annotation goes on the concrete method rather than on the class, an interface or a
 * list of paths in {@code SecurityConfig}. The matrix of fase-3-admin-owner.md 3 does not separate
 * the two ranks here - answering a request is something both do - so the guard is the whole of the
 * authorization story for this screen, which is why it must not be wrong: {@code hasRole('ADMIN')}
 * would lock the owner out and nobody would notice in development.
 *
 * <p><b>The claim endpoints are not here.</b> {@code POST}/{@code DELETE .../claim} belong to the
 * shared queue of F3.3 (#100). F3.2 leaves the two columns and no behaviour.
 */
@RestController
@RequestMapping("/api/v1/admin/requests")
public class AdminApprovalRequestController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final ApprovalService approvalService;

    /**
     * @param approvalService the service that owns every rule of this mechanism
     */
    public AdminApprovalRequestController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * The listing.
     *
     * @param q        the search box, in the language of #164: bare text matches the justification or
     *                 the requester's name, and {@code /request_type}, {@code /status} and
     *                 {@code /requested_by} narrow it. Null or blank lists every request in every
     *                 state - the screen's {@code Pending} default is the frontend's initial
     *                 {@code ?q=}, not a filter hidden in here
     * @param pageable page, size and sort; oldest first, with a tie-break by id (#171, #173)
     * @return 200 with one page of requests
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<ApprovalRequestSummaryResponse> list(
            @RequestParam(required = false) @Nullable String q,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}) Pageable pageable) {
        return approvalService.search(q, pageable);
    }

    /**
     * One request, as the detail opens it.
     *
     * @param id the request
     * @return 200 with its detail. 404 when no request has that id
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ApprovalRequestDetailResponse getDetail(@PathVariable String id) {
        return approvalService.getDetail(id);
    }

    /**
     * Says yes.
     *
     * <p>A POST with a body and not a PUT on a sub-resource, because the reason is mandatory and this
     * is an action rather than an assignment. The sub-route is one kebab-case verb, the shape every
     * action in this application already has - {@code /grant-role}, {@code /request-changes},
     * {@code /assign-masters}, {@code /publish}.
     *
     * @param id          the request to approve
     * @param request     why
     * @param currentUser the actor, from the token (#121)
     * @return 200 with the request afterwards. 400 when the note is blank, 404 when it does not
     *         exist, 409 when it was already resolved or when what it points at is gone
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ApprovalRequestDetailResponse approve(
            @PathVariable String id,
            @Valid @RequestBody ResolveApprovalRequestRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.approve(id, request.resolutionNote(), currentUser);
    }

    /**
     * Says no, with a reason - which is as mandatory here as it is on an approval (#42).
     *
     * @param id          the request to reject
     * @param request     why
     * @param currentUser the actor, from the token (#121)
     * @return 200 with the request afterwards. 400 when the note is blank, 404 when it does not
     *         exist, 409 when it was already resolved or when what it points at is gone
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ApprovalRequestDetailResponse reject(
            @PathVariable String id,
            @Valid @RequestBody ResolveApprovalRequestRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.reject(id, request.resolutionNote(), currentUser);
    }
}
