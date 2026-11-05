package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Route-scoped chain of FE-06 (T007): the public promotion cards need no identity (a Guest may read them, FR-001).
 *
 * <p>Only {@code GET /api/v1/promotions} and {@code GET /api/v1/promotions/{id}} match. This chain runs before the
 * shared chain of 001 and does not add 001's identity gate, so a Guest is not asked for a bearer. Everything else,
 * including every Manager and Member route, stays on the shared chain: verified bearer, otherwise 401; unknown routes
 * denied. CORS uses the shared origin-based configuration (no wildcard); these routes change no state, so CSRF does
 * not apply. A public card is not a quote and not a guarantee that a voucher can be used.
 */
@Configuration(proxyBeanMethods = false)
public class LoyaltySecurityConfiguration {

    static final RequestMatcher PUBLIC_PROMOTIONS = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/v1/promotions", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/promotions/*", HttpMethod.GET.name()));

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    SecurityFilterChain publicPromotionsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(PUBLIC_PROMOTIONS)
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
