package com.centraldungeon.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Every unauthenticated API call gets a ProblemDetail body (arquitectura.md 2.5), never a redirect to a login page. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Writes the body by hand: this runs inside the filter chain, before any message converter. */
    private final JsonMapper jsonMapper;

    /**
     * @param jsonMapper the application's configured Jackson 3 mapper
     */
    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Answers 401 with a ProblemDetail body.
     *
     * <p>The detail is deliberately the same whatever went wrong - no token, expired, wrong type.
     * Telling the caller which check failed only helps someone probing.
     *
     * @param request       the unauthenticated request
     * @param response      the response to write the 401 into
     * @param authException what Spring Security rejected it with. Logged, never echoed
     * @throws IOException if the response body cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
        problem.setProperty("errorCode", "UNAUTHORIZED");

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
