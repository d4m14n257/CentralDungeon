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
import com.centraldungeon.users.UserRoleStatus;
import com.centraldungeon.users.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
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
     * <p>The actor comes out holding <strong>exactly one role</strong>, not the requested one on top
     * of whatever they already had. A real account gets Player on first login (#38) and accumulates
     * from there (#37), which is right for the product and wrong for a fixture: an actor who is
     * Master <em>and</em> Player cannot show what a master alone sees, and every crossing between
     * contexts stays invisible. So the roles that were not asked for are revoked on the way in, which
     * also repairs an account that a previous run left with two.
     *
     * @param discordId the identity to log in as. Also used as the display name, since the suite only
     *                  needs someone distinguishable
     * @param asMaster  whether the actor is a Master. Their only role, unless {@code asAdmin} too
     * @param asAdmin   whether the actor is an Admin. Their only role, unless {@code asMaster} too
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
        setRolesExactly(user, wantedRoles(asMaster, asAdmin));

        String accessToken = jwtService.issueAccessToken(user.getId());
        String refreshToken = jwtService.issueRefreshToken(user.getId());
        response.addCookie(refreshCookieFactory.create(refreshToken));
        return new TokenResponse(accessToken, jwtService.accessTokenTtl().toSeconds());
    }

    /** Player when nothing is asked for: the actor with no flags is a plain member of the community. */
    private Set<PlatformRole> wantedRoles(boolean asMaster, boolean asAdmin) {
        Set<PlatformRole> wanted = EnumSet.noneOf(PlatformRole.class);
        if (asMaster) {
            wanted.add(PlatformRole.MASTER);
        }
        if (asAdmin) {
            wanted.add(PlatformRole.ADMIN);
        }
        return wanted.isEmpty() ? EnumSet.of(PlatformRole.PLAYER) : wanted;
    }

    /**
     * Leaves the person holding the wanted roles and nothing else.
     *
     * <p>Revoking marks the row rather than deleting it (#25), and restoring flips that mark back
     * instead of inserting: {@code (user_id, role_id)} is the primary key, so a second row for the
     * same pair is not a thing that can exist. The cache is evicted at the end because otherwise the
     * roles just taken away stay live for the rest of the TTL (#128) - long enough for the reload
     * that follows this call to still read them.
     */
    private void setRolesExactly(User user, Set<PlatformRole> wanted) {
        Set<String> wantedNames = wanted.stream().map(PlatformRole::roleName).collect(Collectors.toSet());
        Set<String> restored = new HashSet<>();

        for (UserRole grant : userRoleRepository.findAllGrants(user.getId())) {
            String name = grant.getRole().getName();
            UserRoleStatus target = wantedNames.contains(name) ? UserRoleStatus.Allowed : UserRoleStatus.Deleted;
            if (grant.getStatus() != target) {
                grant.setStatus(target);
                userRoleRepository.save(grant);
            }
            restored.add(name);
        }
        for (PlatformRole role : wanted) {
            if (!restored.contains(role.roleName())) {
                Role platformRole = roleRepository.findByName(role.roleName())
                        .orElseThrow(() -> new IllegalStateException(role.roleName() + " role is missing - check V2__seed.sql"));
                userRoleRepository.save(new UserRole(user, platformRole));
            }
        }
        userService.evictAuthCache(user.getId());
    }
}
