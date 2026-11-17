package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.configuration;

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

/** Permits only the customer-facing, read-only Workshop Package discovery routes. */
@Configuration(proxyBeanMethods = false)
public class WorkshopPackageSecurityConfiguration {

    static final RequestMatcher PUBLIC_PACKAGE_READS = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/v1/workshop-packages", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/workshop-packages/*", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/workshop-packages/*/sessions", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/v1/workshop-packages/*/locations/*/add-ons", HttpMethod.GET.name()));

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 11)
    SecurityFilterChain publicWorkshopPackageReadSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(PUBLIC_PACKAGE_READS)
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
