package com.centraldungeon.common.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Origin restricted per profile (arquitectura.md 2.6) - never "*". */
@Configuration
public class CorsConfig {

    /** The single frontend origin allowed to call the API, set per profile. */
    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    /**
     * The CORS policy the security filter chain installs.
     *
     * <p>{@code allowCredentials} is on because the refresh call authenticates with a cookie, and
     * that is exactly why the origin can never be {@code "*"} - the two are incompatible, and a
     * wildcard here would let any site drive a logged-in browser's refresh.
     *
     * @return a source that applies the same policy to every path
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
