package com.centraldungeon.common.security;

import com.centraldungeon.common.config.RateLimitProperties;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.InstantSource;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two rate-limit filters of the security chain, built from the configured limits.
 *
 * <p>Two because the keys are ready at different moments. The doors anybody can reach without a
 * session - the login and the refresh - count per client IP and run before authentication; the writes
 * a signed-in person repeats - uploading, applying to a table, proposing a catalog value, searching for
 * co-masters - count per user and run after the bearer
 * token resolved who the actor is. The user is always the one from the token, never one named in the URL.
 *
 * <p>The client IP is {@code getRemoteAddr()}, narrowed to its /64 prefix when it is IPv6 (see
 * {@link #ipKey}): behind the production proxy, the servlet container
 * already replaced it with the address the proxy reports, and only for that proxy. The forwarding
 * headers are never read here.
 *
 * <p>Not filters registered as beans on purpose: Spring Boot would also mount a filter bean on the
 * servlet container, outside the security chain, and it would count every request twice.
 */
@Component
public class RateLimitFilters {

    private final RateLimitFilter byIp;
    private final RateLimitFilter byUser;

    /**
     * @param properties the limits, from {@code app.rate-limit.*}
     * @param jsonMapper the application's mapper, to write the 429 body
     */
    public RateLimitFilters(RateLimitProperties properties, JsonMapper jsonMapper) {
        InstantSource clock = InstantSource.system();
        this.byIp = new RateLimitFilter("byIp", List.of(
                new RateLimitFilter.Rule("login",
                        new OrRequestMatcher(
                                PathPatternRequestMatcher.pathPattern("/oauth2/authorization/**"),
                                PathPatternRequestMatcher.pathPattern("/login/oauth2/code/**")),
                        new FixedWindowLimiter(properties.login(), clock),
                        request -> ipKey(request.getRemoteAddr())),
                new RateLimitFilter.Rule("refresh",
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
                        new FixedWindowLimiter(properties.refresh(), clock),
                        request -> ipKey(request.getRemoteAddr()))),
                jsonMapper);
        this.byUser = new RateLimitFilter("byUser", List.of(
                new RateLimitFilter.Rule("upload",
                        new OrRequestMatcher(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/files"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/admin/files")),
                        new FixedWindowLimiter(properties.upload(), clock),
                        request -> currentUserId()),
                new RateLimitFilter.Rule("registration",
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/game-tables/*/registrations"),
                        new FixedWindowLimiter(properties.registration(), clock),
                        request -> currentUserId()),
                new RateLimitFilter.Rule("catalogProposal",
                        new OrRequestMatcher(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/tags"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/systems"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/platforms")),
                        new FixedWindowLimiter(properties.catalogProposal(), clock),
                        request -> currentUserId()),
                new RateLimitFilter.Rule("masterCandidates",
                        PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/game-tables/*/master-candidates"),
                        new FixedWindowLimiter(properties.masterCandidates(), clock),
                        request -> currentUserId())),
                jsonMapper);
    }

    /**
     * The counter key of a client address: IPv4 by its whole address, IPv6 by its /64 prefix.
     *
     * <p>One IPv6 subscriber is handed a whole /64, so counting each full address would let a single
     * client rotate through billions of them. An IPv4-mapped IPv6 address counts as the IPv4 it carries.
     * Only literals are parsed - a literal never reaches the resolver - and anything that is not one is
     * kept as it came.
     *
     * @param remoteAddr the address the servlet container reports
     * @return the key the address is counted under
     */
    static String ipKey(String remoteAddr) {
        if (remoteAddr.indexOf(':') < 0) {
            return remoteAddr;
        }
        try {
            // The zone id (fe80::1%en0) names a local interface, which the JVM may not know: it is not part of the prefix.
            InetAddress address = InetAddress.getByName(remoteAddr.replaceFirst("%.*$", ""));
            if (address instanceof Inet4Address) {
                return address.getHostAddress();
            }
            byte[] bytes = address.getAddress();
            return "%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64".formatted(
                    bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
        } catch (UnknownHostException e) {
            return remoteAddr;
        }
    }

    /**
     * The filter for the doors reachable without a session, keyed by client IP. Goes before authentication.
     *
     * @return the per-IP filter
     */
    public RateLimitFilter byIp() {
        return byIp;
    }

    /**
     * The filter for the writes of a signed-in person, keyed by the actor. Goes after the bearer token
     * was read; an unauthenticated request is not counted and gets its 401 further on.
     *
     * @return the per-user filter
     */
    public RateLimitFilter byUser() {
        return byUser;
    }

    private static @Nullable String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CurrentUser user ? user.userId() : null;
    }
}
