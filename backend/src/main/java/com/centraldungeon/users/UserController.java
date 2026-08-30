package com.centraldungeon.users;

import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.UpdateUserRequest;
import com.centraldungeon.users.dto.UserDetailResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** Onboarding is a blocking step the first time: name and country (decisiones.md #134). */
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserDetailResponse completeOnboarding(
            @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return userService.completeOnboarding(currentUser.userId(), request);
    }
}
