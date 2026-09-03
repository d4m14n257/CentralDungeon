package com.centraldungeon.users;

import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.UpdateUserRequest;
import com.centraldungeon.users.dto.UserDetailResponse;
import com.centraldungeon.users.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * People. Deliberately small: reading the caller's own profile, the picker that admins use to assign
 * masters, and the onboarding step.
 *
 * <p>There is no "get user by id" here. Public profiles and their visibility window (#41, #44, #47)
 * are F2, and a user directory is not opened wider than the screens that need it (#165).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /** The only collaborator: a controller never reaches a repository (regla dura 1). */
    private final UserService userService;

    /**
     * @param userService the service that owns people, roles and the auth snapshot
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * The caller's own profile. The whole frontend shell is built from this: the contexts the
     * switcher offers, whether onboarding still blocks, the avatar and the karma.
     *
     * @param currentUser the actor, from the token. There is no id parameter, so this can only ever
     *                    answer about the caller (#121)
     * @return 200 with their profile
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserDetailResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return userService.getDetailResponse(currentUser.userId());
    }

    /**
     * User picker of /admin/tables (assigning masters). Admin-only for now: the only screen that
     * needs it is an admin one, and a user directory is not something to open wider than the
     * current consumer needs. When a Primary master gets to add a Secondary from /master/tables/:id,
     * widening this is an explicit decision, not a side effect (decisiones.md #165).
     *
     * <p>The query syntax lives in SearchQueryParser; the fields it accepts, in UserSearchField.
     *
     * @param query    the search box, or null to list everyone allowed. An empty query is not an
     *                 error: it opens the picker with a first page instead of a blank slate
     * @param pageable page, size and sort; ten at a time, which is a picker's worth
     * @return 200 with one page of people who can be picked
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<UserSummaryResponse> search(
            @RequestParam(name = "q", required = false) @Nullable String query,
            @PageableDefault(size = 10, sort = {"discordUsername", "id"}) Pageable pageable) {
        return userService.search(query, pageable);
    }

    /**
     * Onboarding is a blocking step the first time: name and country (decisiones.md #134).
     *
     * @param request     the name and country they chose
     * @param currentUser the actor, from the token. This endpoint can only edit the caller's own
     *                    profile - there is no id to point somewhere else (#121)
     * @return 200 with their profile, now past onboarding
     */
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserDetailResponse completeOnboarding(
            @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return userService.completeOnboarding(currentUser.userId(), request);
    }
}
