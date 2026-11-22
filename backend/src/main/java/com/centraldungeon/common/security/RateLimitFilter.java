package com.centraldungeon.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refuses a request with {@code 429} once its key went over the limit of the current window.
 *
 * <p>Runs inside the security chain, before any controller, so it writes its own ProblemDetail body,
 * the same way the 401 of the entry point is written. The answer says when to come back, in the
 * {@code Retry-After} header and in {@code errorParams.retryAfterSeconds}, and nothing else: not the
 * limit, not the key, not how many requests were counted.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    /** The code the frontend renders the message from. */
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * One limited door: which requests it covers, its counter, and who each request is counted against.
     *
     * @param name    what the log calls it
     * @param matcher the requests this rule counts
     * @param limiter the counter
     * @param key     who the request is counted against; null leaves the request uncounted
     */
    record Rule(
            String name,
            RequestMatcher matcher,
            FixedWindowLimiter limiter,
            Function<HttpServletRequest, @Nullable String> key) {
    }

    private final String name;
    private final List<Rule> rules;
    private final JsonMapper jsonMapper;

    /**
     * @param name       tells this instance apart from the other one in the same chain
     * @param rules      the doors this instance counts; the first one that matches a request decides
     * @param jsonMapper writes the body, as there is no message converter this early
     */
    RateLimitFilter(String name, List<Rule> rules, JsonMapper jsonMapper) {
        this.name = name;
        this.rules = List.copyOf(rules);
        this.jsonMapper = jsonMapper;
    }

    /**
     * Counts the request against the first rule that covers it, and answers {@code 429} when that rule
     * is over its limit. Requests no rule covers, or without a key, pass untouched.
     *
     * @param request     the incoming request
     * @param response    where the 429 is written, if it comes to that
     * @param filterChain the rest of the chain, continued unless the request is refused
     * @throws ServletException if a later filter fails
     * @throws IOException if a later filter fails or the body cannot be written
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<Rule> rule = rules.stream().filter(r -> r.matcher().matches(request)).findFirst();
        String key = rule.map(r -> r.key().apply(request)).orElse(null);
        if (rule.isEmpty() || key == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<Duration> retryAfter = rule.get().limiter().tryAcquire(key);
        if (retryAfter.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        long seconds = retryAfter.get().toSeconds();
        // Not even a hash of the key: the IPv4 space is small enough to brute-force a hash back to an address.
        log.warn("Rate limit '{}' refused a request; retry in {}s", rule.get().name(), seconds);
        writeTooManyRequests(response, seconds);
    }

    private void writeTooManyRequests(HttpServletResponse response, long seconds) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        problem.setProperty("errorCode", RATE_LIMITED);
        problem.setProperty("errorParams", Map.of("retryAfterSeconds", Long.toString(seconds)));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }

    /**
     * Keyed by this instance's name: there are two of these in the chain, and with the default
     * attribute the second would take the first one's mark as its own and skip every request.
     *
     * @return the request attribute that marks this instance as already run
     */
    @Override
    protected String getAlreadyFilteredAttributeName() {
        return RateLimitFilter.class.getName() + "." + name + ".FILTERED";
    }
}
