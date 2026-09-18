package com.centraldungeon.approvals;

import com.centraldungeon.approvals.dto.ApprovalRequestDetailResponse;
import com.centraldungeon.approvals.dto.ApprovalRequestSummaryResponse;
import com.centraldungeon.approvals.dto.SubmitApprovalRequestRequest;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /requests}: the asking end. Two audiences means two controllers, and this is the one with
 * <b>no role at all</b> - anybody logged in can ask for something, which is what makes the mechanism
 * of #42 a door rather than a privilege.
 *
 * <p>Every endpoint here is scoped to the actor from the token. There is no user id in any path and
 * none in any body, so there is no way to ask on somebody else's behalf (#121, arquitectura.md 2.6) -
 * and the polymorphic reference has no foreign key that would catch it if there were (#78).
 *
 * <p>The form that produces one of these lives on the screen that provokes it - the profile, the
 * explorer, the help page - and never on a "make a request" screen
 * (docs/fase-3-admin-owner.md 4).
 */
@RestController
@RequestMapping("/api/v1/requests")
public class ApprovalRequestController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final ApprovalService approvalService;

    /**
     * @param approvalService the service that owns every rule of this mechanism
     */
    public ApprovalRequestController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * Asks for something.
     *
     * @param request     what is being asked for, and why. No entity id: the request is about the
     *                    actor, who comes from the token
     * @param currentUser the actor, from the token (#121)
     * @return 201 with the request and a {@code Location} header. 400 when the reason is blank or the
     *         type is not one of the three, 409 when the same request is already open or when the
     *         Master role is already held
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApprovalRequestDetailResponse> submit(
            @Valid @RequestBody SubmitApprovalRequestRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        ApprovalRequestDetailResponse created =
                approvalService.submit(request.type(), request.justification(), currentUser);
        // The Location points at the admin route, which is the only one that reads a request by id.
        // It is the resource's canonical address even though this caller cannot open it: a 201 whose
        // Location is invented, or absent, is worse than one pointing where the thing actually lives.
        return ResponseEntity.created(URI.create("/api/v1/admin/requests/" + created.id())).body(created);
    }

    /**
     * The actor's own requests, newest first.
     *
     * <p><b>This endpoint is why a request does not vanish.</b> Without it somebody sends a
     * justification into a hole: the screen that provoked the request has no way to say "you already
     * asked, it is pending since Tuesday" and offers the button again, where its only possible answer
     * is a 409. A button that can only fail is a button that should not be drawn (principio 2 de
     * frontend-diseno.md 1).
     *
     * <p>Which is also why it takes a {@code ?q=}: reading page one of everything and inferring the
     * answer from it is how the button comes back anyway, the moment an old pending request is pushed
     * off the page by newer resolved ones. {@code ?q=/status Pending /and /request_type MasterGrant}
     * asks the question directly.
     *
     * @param q           the search box, the same language {@code /admin/requests} speaks:
     *                    {@code /status}, {@code /request_type} and {@code /requested_by}, with bare
     *                    text over the justification. Null or blank lists all of theirs. <b>It cannot
     *                    widen the result past the actor</b> - that filter is not part of the
     *                    language and is applied in the service (#121)
     * @param pageable    page and size; newest first, with a tie-break by id (#171)
     * @param currentUser the actor, from the token
     * @return 200 with one page of their requests
     */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ApprovalRequestSummaryResponse> listMine(
            @RequestParam(required = false) @Nullable String q,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return approvalService.listMine(currentUser.userId(), q, pageable);
    }
}
