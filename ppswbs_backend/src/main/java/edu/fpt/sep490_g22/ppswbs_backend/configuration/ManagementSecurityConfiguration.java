package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lets the metrics scraper read the actuator endpoints that are exposed ({@code prometheus} only, see
 * {@code management.endpoints.web.exposure.include}) without a Mland bearer. Safe because they are served on a
 * separate management port bound to loopback by default ({@code management.server.address}); on the public API
 * port the actuator path is not mapped, so the main chain's {@code denyAll()} and the 404 still apply there.
 * The endpoints carry no personal data.
 */
@Configuration
public class ManagementSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
