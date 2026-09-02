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

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

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
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public PageResponse<UserSummaryResponse> search(
            @RequestParam(name = "q", required = false) @Nullable String query,
            @PageableDefault(size = 10, sort = {"discordUsername", "id"}) Pageable pageable) {
        return userService.search(query, pageable);
    }

    /** Onboarding is a blocking step the first time: name and country (decisiones.md #134). */
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserDetailResponse completeOnboarding(
            @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return userService.completeOnboarding(currentUser.userId(), request);
    }
}
