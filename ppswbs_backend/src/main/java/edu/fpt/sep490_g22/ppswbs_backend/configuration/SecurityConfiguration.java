package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final ObjectProvider<Filter> identityGateFilterProvider;
    private final List<String> allowedOrigins;

    public SecurityConfiguration(
        @Qualifier("identityGateFilter") ObjectProvider<Filter> identityGateFilterProvider,
        @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOriginsStr
    ) {
        this.identityGateFilterProvider = identityGateFilterProvider;
        this.allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    String correlationId = resolveCorrelationId(request);
                    writeError(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "Authentication required", correlationId);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String correlationId = resolveCorrelationId(request);
                    writeError(response, HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Access denied", correlationId);
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/ai-support/messages").permitAll()
                // VNPay calls the signed IPN server-to-server without a Mland bearer token.
                // PaymentIngressService performs checksum, owner, amount and deduplication checks.
                .requestMatchers(HttpMethod.GET, "/api/v1/integrations/payments/vnpay/events").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/workshop-bookings/*/payment-attempts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/workshop-bookings/*/final-bill").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/workshop-bookings/*/final-bill/charges").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            );

        identityGateFilterProvider.ifAvailable(filter -> {
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        });

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public UserDetailsService noopUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Local username/password authentication is not supported");
        };
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return (correlationId != null && !correlationId.isBlank()) ? correlationId.trim() : UUID.randomUUID().toString();
    }

    private void writeError(
        HttpServletResponse response,
        int status,
        String code,
        String message,
        String correlationId
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String json = String.format(
            "{\"code\":\"%s\",\"message\":\"%s\",\"correlationId\":\"%s\",\"timestamp\":\"%s\"}",
            code, message, correlationId, Instant.now()
        );
        response.getWriter().write(json);
    }
}
