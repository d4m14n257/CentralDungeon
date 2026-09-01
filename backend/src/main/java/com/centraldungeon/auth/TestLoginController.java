package com.centraldungeon.auth;

import com.centraldungeon.auth.dto.TokenResponse;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.RefreshCookieFactory;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for the Discord OAuth2 dance in e2e runs, where there is no real Discord app yet
 * (plan-desarrollo.md E1 Bloque 8). Only registered under the "test" profile - absent from the
 * bean graph in dev/prod, so the route 404s there regardless of the permitAll matcher in
 * SecurityConfig covering it.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Profile("test")
public class TestLoginController {

    private final UserService userService;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtService jwtService;
    private final RefreshCookieFactory refreshCookieFactory;

    public TestLoginController(
            UserService userService,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            JwtService jwtService,
            RefreshCookieFactory refreshCookieFactory) {
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.jwtService = jwtService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/test-login")
    @Transactional
    public TokenResponse testLogin(
            @RequestParam String discordId,
            @RequestParam(defaultValue = "false") boolean asMaster,
            @RequestParam(defaultValue = "false") boolean asAdmin,
            HttpServletResponse response) {
        User user = userService.findOrCreateByDiscordId(discordId, discordId);
        if (asMaster) {
            grantRoleIfMissing(user, PlatformRole.MASTER);
        }
        if (asAdmin) {
            grantRoleIfMissing(user, PlatformRole.ADMIN);
        }

        String accessToken = jwtService.issueAccessToken(user.getId());
        String refreshToken = jwtService.issueRefreshToken(user.getId());
        response.addCookie(refreshCookieFactory.create(refreshToken));
        return new TokenResponse(accessToken, jwtService.accessTokenTtl().toSeconds());
    }

    private void grantRoleIfMissing(User user, PlatformRole role) {
        if (userRoleRepository.findActiveRoleNames(user.getId()).contains(role.roleName())) {
            return;
        }
        Role platformRole = roleRepository.findByName(role.roleName())
                .orElseThrow(() -> new IllegalStateException(role.roleName() + " role is missing - check V2__seed.sql"));
        userRoleRepository.save(new UserRole(user, platformRole));
    }
}
