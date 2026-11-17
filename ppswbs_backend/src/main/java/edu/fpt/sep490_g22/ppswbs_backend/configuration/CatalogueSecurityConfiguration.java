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
 * Route-scoped chain of FE-02 (T007): the public catalogue reads need no identity (interfaces "security").
 *
 * <p>Only {@code GET} of {@code /api/v1/products}, {@code /api/v1/products/{id}},
 * {@code /api/v1/product-categories} and {@code /api/v1/product-media/{id}} match. This chain runs before the
 * shared chain of 001 and does not add 001's {@code IdentityGateFilter}, so a guest is not asked for a bearer.
 * Everything else — including every {@code /api/v1/catalogue-management/**} route — stays on the shared chain
 * (verified bearer, otherwise 401; unknown routes denied). CORS uses the shared origin-based configuration;
 * no state changes here, so CSRF does not apply.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogueSecurityConfiguration {

    static final RequestMatcher PUBLIC_CATALOGUE = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/v1/products", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/products/*", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/product-categories", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/product-media/*", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/product-selling-locations", HttpMethod.GET.name()));

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    SecurityFilterChain publicCatalogueSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(PUBLIC_CATALOGUE)
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
