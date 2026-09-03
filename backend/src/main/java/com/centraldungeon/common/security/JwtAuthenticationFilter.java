package com.centraldungeon.common.security;

import com.centraldungeon.users.UserAuthSnapshot;
import com.centraldungeon.users.UserService;
import com.centraldungeon.users.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The JWT affirms identity, not authorization (decisiones.md #122): roles and status are
 * re-read from the database (Caffeine-cached, see application.yml) on every request, so a
 * blocked user or a revoked role takes effect without waiting for the token to expire.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** The scheme every authenticated call uses. A header the browser never attaches on its own (#127). */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Validates the token and reads its subject. */
    private final JwtService jwtService;

    /** Reads the roles and status from the database - the whole point of #122. */
    private final UserService userService;

    /**
     * @param jwtService  validates the bearer token
     * @param userService loads the authorization snapshot behind the token's subject
     */
    public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    /**
     * Turns a bearer token into the request's {@code Authentication}, with the authorities the person
     * holds <em>right now</em> rather than the ones their token was minted with (#122).
     *
     * <p>A missing or invalid token is not an error here: the chain continues unauthenticated and
     * whatever the request was aimed at decides. That is what lets the public paths work and what
     * makes {@code RestAuthenticationEntryPoint} the single place a 401 is written.
     *
     * @param request     the incoming request
     * @param response    the response, untouched by this filter
     * @param filterChain the rest of the chain, always continued
     * @throws ServletException if a later filter fails
     * @throws IOException if a later filter fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            String userId = jwtService.verifyAndGetSubject(token, TokenType.ACCESS);
            UserAuthSnapshot snapshot = userService.loadAuthSnapshot(userId);
            if (snapshot.status() != UserStatus.Allowed) {
                return;
            }

            List<GrantedAuthority> authorities = snapshot.roles().stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                    .toList();

            CurrentUser principal = new CurrentUser(snapshot.userId(), snapshot.roles());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidJwtException e) {
            SecurityContextHolder.clearContext();
        }
    }
}
