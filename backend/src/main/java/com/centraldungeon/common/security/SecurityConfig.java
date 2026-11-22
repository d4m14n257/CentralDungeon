package com.centraldungeon.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Only what is transversal lives here - stateless-as-possible, CORS, what is public. The permission
 * itself is declared on each concrete controller method, never in a route list here (ADR-05 §4).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    /**
     * An authorized-client store that keeps nothing. The application never calls Discord on the
     * person's behalf after the login, so there is no reason to hold their Discord token at all.
     */
    private static final OAuth2AuthorizedClientRepository DISCARDING_AUTHORIZED_CLIENTS = new OAuth2AuthorizedClientRepository() {
        @Override
        public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
                String clientRegistrationId, Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(
                OAuth2AuthorizedClient authorizedClient, Authentication principal, HttpServletRequest request,
                HttpServletResponse response) {
            // Deliberately dropped.
        }

        @Override
        public void removeAuthorizedClient(
                String clientRegistrationId, Authentication principal, HttpServletRequest request,
                HttpServletResponse response) {
            // Nothing was ever kept.
        }
    };

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DiscordOAuth2UserService discordOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RateLimitFilters rateLimitFilters;

    /**
     * @param corsConfigurationSource   the per-profile CORS policy
     * @param jwtAuthenticationFilter   turns a bearer token into the request's authorities
     * @param discordOAuth2UserService  the guild-membership gate of the login (ADR-05 §1)
     * @param oAuth2LoginSuccessHandler sets the refresh cookie and redirects on success
     * @param oAuth2LoginFailureHandler redirects with the reason, and the invite when it applies
     * @param restAuthenticationEntryPoint answers 401 with a ProblemDetail instead of a redirect
     * @param rateLimitFilters          the per-IP and per-user limits on the doors anybody can repeat
     */
    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DiscordOAuth2UserService discordOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RateLimitFilters rateLimitFilters) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.discordOAuth2UserService = discordOAuth2UserService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.oAuth2LoginFailureHandler = oAuth2LoginFailureHandler;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.rateLimitFilters = rateLimitFilters;
    }

    /**
     * The application's filter chain.
     *
     * <p>Only the transversal decisions are here - CORS, what is public, where CSRF applies, how the
     * session behaves. <b>Not one endpoint's permission</b>: those are declared on each concrete
     * controller method (ADR-05 §4), because the previous attempt kept them in a route list where five of
     * six paths were written without a leading slash, matched nothing, and let any authenticated user
     * through to the admin endpoints.
     *
     * @param http the builder Spring Security hands in
     * @return the configured chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // The two endpoints the refresh cookie authenticates. A cookie travels on its own, so a page on
        // another site could otherwise force a rotation or a logout; everything else takes a bearer header,
        // which the browser never attaches by itself.
        RequestMatcher cookieAuthenticated = new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, REFRESH_PATH),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, LOGOUT_PATH));

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.spa().requireCsrfProtectionMatcher(cookieAuthenticated))
                // IF_REQUIRED, not fully stateless: oauth2Login needs a short-lived session to
                // carry the authorization request across the redirect to Discord and back.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // But the security context never lives in that session. Kept there, a JSESSIONID alone
                // would authenticate every later request without a token and without re-reading the
                // account's status and roles - a blocked account or a revoked role would keep working
                // until the session expired. Identity comes from the bearer token on every request.
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/**",
                                REFRESH_PATH,
                                LOGOUT_PATH,
                                // Only mapped to a handler under the "test" profile (TestLoginController
                                // and TestDiscordController) - 404s in dev/prod regardless of being
                                // permitAll here.
                                "/api/v1/auth/test-login",
                                // Only exist under the "test" profile; without a bean the paths 404 anyway.
                                "/api/v1/test-data/**",
                                "/test-discord/**",
                                "/api/v1/health",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(restAuthenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        // Discord's token is used during the callback to read the profile and the guilds,
                        // and then dropped. The default repository would keep it in memory, per person,
                        // for as long as the process runs.
                        .authorizedClientRepository(DISCARDING_AUTHORIZED_CLIENTS)
                        .userInfoEndpoint(userInfo -> userInfo.userService(discordOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                // Per IP before anything reads a credential, so even a refused login or refresh is counted;
                // per user only once the bearer token said who is asking.
                .addFilterBefore(rateLimitFilters.byIp(), CsrfFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilters.byUser(), JwtAuthenticationFilter.class);

        return http.build();
    }
}
