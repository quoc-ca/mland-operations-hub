package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for Workshop Operations endpoints.
 * Enforces origin allowlist, deny-by-default, and denies credentialed CORS
 * per Constitution and T010 specifications.
 */
@Configuration
public class WorkshopOperationsCorsConfiguration {

    @Value("${workshop.operations.cors.allowed-origins:${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}}")
    private String allowedOriginsConfig;

    @Bean
    public FilterRegistrationBean<CorsFilter> workshopOperationsCorsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        // Deny credentialed CORS by default per Constitution and T010
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/workshop-locations/**", configuration);
        source.registerCorsConfiguration("/api/v1/workshop-branches/**", configuration);
        source.registerCorsConfiguration("/api/v1/workshop-slots/**", configuration);
        source.registerCorsConfiguration("/api/v1/workshop-sessions/**", configuration);
        source.registerCorsConfiguration("/api/v1/workshop-checkin/**", configuration);
        source.registerCorsConfiguration("/api/v1/workshop-test/**", configuration);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns(
            "/api/v1/workshop-locations/*",
            "/api/v1/workshop-branches/*",
            "/api/v1/workshop-slots/*",
            "/api/v1/workshop-sessions/*",
            "/api/v1/workshop-checkin/*",
            "/api/v1/workshop-test/*"
        );
        return bean;
    }
}
