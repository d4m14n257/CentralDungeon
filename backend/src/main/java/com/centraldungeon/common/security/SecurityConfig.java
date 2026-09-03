package com.centraldungeon.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Only what is transversal lives here - stateless-as-possible, CORS, what is public. The permission
 * itself is declared on each concrete controller method, never in a route list here (decisiones.md #123).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DiscordOAuth2UserService discordOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    /**
     * @param corsConfigurationSource   the per-profile CORS policy
     * @param jwtAuthenticationFilter   turns a bearer token into the request's authorities
     * @param discordOAuth2UserService  the guild-membership gate of the login (#38)
     * @param oAuth2LoginSuccessHandler sets the refresh cookie and redirects on success
     * @param oAuth2LoginFailureHandler redirects with the reason, and the invite when it applies
     * @param restAuthenticationEntryPoint answers 401 with a ProblemDetail instead of a redirect
     */
    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DiscordOAuth2UserService discordOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.discordOAuth2UserService = discordOAuth2UserService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.oAuth2LoginFailureHandler = oAuth2LoginFailureHandler;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    }

    /**
     * The application's filter chain.
     *
     * <p>Only the transversal decisions are here - CORS, what is public, where CSRF applies, how the
     * session behaves. <b>Not one endpoint's permission</b>: those are declared on each concrete
     * controller method (#123), because the previous attempt kept them in a route list where five of
     * six paths were written without a leading slash, matched nothing, and let any authenticated user
     * through to the admin endpoints.
     *
     * @param http the builder Spring Security hands in
     * @return the configured chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RequestMatcher refreshOnly = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, REFRESH_PATH);

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.spa().requireCsrfProtectionMatcher(refreshOnly))
                // IF_REQUIRED, not fully stateless: oauth2Login needs a short-lived session to
                // carry the authorization request across the redirect to Discord and back. Every
                // other endpoint authenticates with the JWT bearer token and never touches it.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/**",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
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
                        .userInfoEndpoint(userInfo -> userInfo.userService(discordOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
