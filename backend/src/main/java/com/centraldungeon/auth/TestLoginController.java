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
 * (plan-desarrollo.md, E1). Only registered under the "test" profile - absent from the
 * bean graph in dev/prod, so the route 404s there regardless of the permitAll matcher in
 * SecurityConfig covering it.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Profile("test")
public class TestLoginController {

    /** Creates the test user, or reuses them across runs. */
    private final UserService userService;

    /** Resolves the roles the shortcut can grant. */
    private final RoleRepository roleRepository;

    /** Grants them. */
    private final UserRoleRepository userRoleRepository;

    /** Issues the same pair of tokens the real login would. */
    private final JwtService jwtService;

    /** Sets the same refresh cookie the real login would, attributes included. */
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * @param userService          creates or reuses the test user
     * @param roleRepository       resolves the roles to grant
     * @param userRoleRepository   grants them
     * @param jwtService           issues the tokens
     * @param refreshCookieFactory sets the refresh cookie
     */
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

    /**
     * Logs somebody in without going through Discord, so a Playwright spec can set up the actor it
     * needs in one call.
     *
     * <p>Reachable only under the {@code test} profile: without it there is no bean and the path 404s,
     * which is what keeps this from ever being an authentication bypass in dev or prod.
     *
     * <p>It issues the real tokens through the real {@link JwtService} and sets the real cookie -
     * only the Discord handshake is skipped. A shortcut that produced a different kind of session
     * would be testing something the users never do.
     *
     * @param discordId the identity to log in as. Also used as the display name, since the suite only
     *                  needs someone distinguishable
     * @param asMaster  whether to grant the Master role on the way in
     * @param asAdmin   whether to grant the Admin role on the way in
     * @param response  the response the refresh cookie is written onto
     * @return the access token and its lifetime, exactly as a real login would answer
     */
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
