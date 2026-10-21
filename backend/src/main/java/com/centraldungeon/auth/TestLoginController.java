package com.centraldungeon.auth;

import com.centraldungeon.auth.dto.TokenResponse;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.RefreshCookieFactory;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.context.annotation.Profile;
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

    /** Sets the actor's roles exactly, the part that writes through repositories (rule 1). */
    private final TestDataService testDataService;

    /** Issues the same pair of tokens the real login would. */
    private final JwtService jwtService;

    /** Sets the same refresh cookie the real login would, attributes included. */
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * @param userService          creates or reuses the test user
     * @param testDataService      sets the actor's roles exactly
     * @param jwtService           issues the tokens
     * @param refreshCookieFactory sets the refresh cookie
     */
    public TestLoginController(
            UserService userService,
            TestDataService testDataService,
            JwtService jwtService,
            RefreshCookieFactory refreshCookieFactory) {
        this.userService = userService;
        this.testDataService = testDataService;
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
     * of whatever they already had. A real account gets Player on first login (ADR-05 §1) and accumulates
     * from there (ADR-06 §1), which is right for the product and wrong for a fixture: an actor who is
     * Master <em>and</em> Player cannot show what a master alone sees, and every crossing between
     * contexts stays invisible. So the roles that were not asked for are revoked on the way in, which
     * also repairs an account that a previous run left with two.
     *
     * @param discordId the identity to log in as. Also used as the display name, since the suite only
     *                  needs someone distinguishable
     * @param asMaster  whether the actor is a Master. Their only role, unless {@code asAdmin} too
     * @param asAdmin   whether the actor is an Admin. Their only role, unless {@code asMaster} too
     * @param asOwner   whether the actor is the platform Owner. Never together with {@code asAdmin}:
     *                  the two are one rank at two scopes and never coexist (ADR-06 §1), so Owner wins
     * @param response  the response the refresh cookie is written onto
     * @return the access token and its lifetime, exactly as a real login would answer
     */
    @PostMapping("/test-login")
    public TokenResponse testLogin(
            @RequestParam String discordId,
            @RequestParam(defaultValue = "false") boolean asMaster,
            @RequestParam(defaultValue = "false") boolean asAdmin,
            @RequestParam(defaultValue = "false") boolean asOwner,
            HttpServletResponse response) {
        User user = userService.findOrCreateByDiscordId(discordId, discordId);
        testDataService.setRolesExactly(user, wantedRoles(asMaster, asAdmin, asOwner));

        String accessToken = jwtService.issueAccessToken(user.getId());
        String refreshToken = jwtService.issueRefreshToken(user.getId());
        response.addCookie(refreshCookieFactory.create(refreshToken));
        return new TokenResponse(accessToken, jwtService.accessTokenTtl().toSeconds());
    }

    /**
     * Player when nothing is asked for: the actor with no flags is a plain member of the community.
     *
     * <p><b>Owner excludes Admin, here as everywhere</b> (ADR-06 §1). The two are the same rank at two
     * scopes, and a fixture that handed out both would be the one place in the application where they
     * coexist - which is exactly the state F3.1's rules assume cannot happen.
     */
    private Set<PlatformRole> wantedRoles(boolean asMaster, boolean asAdmin, boolean asOwner) {
        Set<PlatformRole> wanted = EnumSet.noneOf(PlatformRole.class);
        if (asMaster) {
            wanted.add(PlatformRole.MASTER);
        }
        if (asOwner) {
            wanted.add(PlatformRole.OWNER);
        } else if (asAdmin) {
            wanted.add(PlatformRole.ADMIN);
        }
        return wanted.isEmpty() ? EnumSet.of(PlatformRole.PLAYER) : wanted;
    }
}
