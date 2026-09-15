package com.centraldungeon.users;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.AdminUserDetailResponse;
import com.centraldungeon.users.dto.AdminUserSummaryResponse;
import com.centraldungeon.users.dto.BlockUserRequest;
import com.centraldungeon.users.dto.GrantRoleRequest;
import com.centraldungeon.users.dto.RevokeRoleRequest;
import com.centraldungeon.users.dto.UnblockUserRequest;
import com.centraldungeon.users.dto.UserAdminChangeResponse;
import jakarta.validation.Valid;
import java.util.List;
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
 * {@code /admin/users}: the people of the platform, seen by whoever administers them.
 *
 * <p>Admin and Owner are enumerated explicitly on every method. There is no {@code RoleHierarchy} in
 * this project: an Owner can do everything an Admin can by being listed, not by inheriting (#37,
 * #89, #123). And the annotation is where the guard <em>stops</em> - the line between the two roles
 * is a rule about what is being granted, so it lives in {@link UserRoleService} and in
 * {@link AdminUserService}, not up here (fase-3-admin-owner.md 4).
 *
 * <p>The actor always comes from {@code @AuthenticationPrincipal}, never from the path or the body
 * (arquitectura.md 2.6). The {@code {id}} in these routes is the <b>target</b>, and it is the only
 * controller in the application where the two are different people on purpose.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    /** Search, detail, block, unblock and history. */
    private final AdminUserService adminUserService;

    /** Grants and revocations - the half an admin cannot always perform. */
    private final UserRoleService userRoleService;

    /**
     * @param adminUserService the service behind the listing, the block and the history
     * @param userRoleService  the service that owns who may move which role
     */
    public AdminUserController(AdminUserService adminUserService, UserRoleService userRoleService) {
        this.adminUserService = adminUserService;
        this.userRoleService = userRoleService;
    }

    /**
     * The listing. Sees blocked and deleted accounts, which {@code GET /users/search} deliberately
     * does not.
     *
     * @param q        the search box, in the language of #164: bare text matches either name, and
     *                 {@code /discord_name}, {@code /user_name}, {@code /role} and {@code /status}
     *                 narrow it. Null or blank lists everybody
     * @param pageable page, size and sort; by Discord handle, with a tie-break by id (#171, #173)
     * @return 200 with one page of people
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<AdminUserSummaryResponse> list(
            @RequestParam(required = false) @Nullable String q,
            @PageableDefault(size = 20, sort = {"discordUsername", "id"}) Pageable pageable) {
        return adminUserService.search(q, pageable);
    }

    /**
     * One person's detail, as the dialog opens it.
     *
     * @param id the person to read
     * @return 200 with their detail. 404 when nobody has that id
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminUserDetailResponse getDetail(@PathVariable String id) {
        return adminUserService.getDetail(id);
    }

    /**
     * Gives somebody a role.
     *
     * <p>A POST with a body and not a PUT on a sub-resource, because the reason is mandatory and the
     * operation is an action rather than an assignment. The sub-route is one kebab-case verb, which
     * is the shape every action in this application already has - {@code /approve},
     * {@code /request-changes}, {@code /assign-masters}, {@code /publish}.
     *
     * @param id          the person the role is given to
     * @param request     which role, and why
     * @param currentUser the actor, from the token (#121)
     * @return 200 with the person afterwards. 403 when an admin reaches for Admin or Owner, 404 when
     *         the person does not exist, 409 when the exclusion of #169 would leave no owner
     */
    @PostMapping("/{id}/grant-role")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminUserDetailResponse grantRole(
            @PathVariable String id,
            @Valid @RequestBody GrantRoleRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return userRoleService.grantRole(id, request.role(), request.justification(), currentUser);
    }

    /**
     * Takes a role away. The grant is marked, never deleted (#25).
     *
     * @param id          the person the role is taken from
     * @param request     which role, and why
     * @param currentUser the actor, from the token (#121)
     * @return 200 with the person afterwards. 403 when an admin reaches for Admin or Owner, 404 when
     *         the person does not exist, 409 when an owner tries to step down or is the last one
     */
    @PostMapping("/{id}/revoke-role")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminUserDetailResponse revokeRole(
            @PathVariable String id,
            @Valid @RequestBody RevokeRoleRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return userRoleService.revokeRole(id, request.role(), request.justification(), currentUser);
    }

    /**
     * Blocks an account (#84). Their data is kept; only the door closes.
     *
     * @param id          the account to block
     * @param request     why
     * @param currentUser the actor, from the token (#121)
     * @return 200 with the account afterwards. 403 when the target holds Admin or Owner, 404 when
     *         they do not exist, 409 when the account is not Allowed
     */
    @PostMapping("/{id}/block")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminUserDetailResponse block(
            @PathVariable String id,
            @Valid @RequestBody BlockUserRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return adminUserService.block(id, request.justification(), currentUser);
    }

    /**
     * Lets a blocked account back in.
     *
     * @param id          the account to unblock
     * @param request     why
     * @param currentUser the actor, from the token (#121)
     * @return 200 with the account afterwards. 404 when they do not exist, 409 when the account is
     *         not Blocked
     */
    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public AdminUserDetailResponse unblock(
            @PathVariable String id,
            @Valid @RequestBody UnblockUserRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return adminUserService.unblock(id, request.justification(), currentUser);
    }

    /**
     * Everything that was ever done to this account, as one timeline, oldest first.
     *
     * @param id the person
     * @return 200 with their whole history. Not paginated: it is a handful of rows read as a
     *         sequence. 404 when the person does not exist
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public List<UserAdminChangeResponse> getHistory(@PathVariable String id) {
        return adminUserService.history(id);
    }
}
