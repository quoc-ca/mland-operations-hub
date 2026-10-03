package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorizedEntryPoint()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/member-auth/**",
                    "/workshop-bookings/**",
                    "/policies/**",
                    "/api/v1/policies/current",
                    "/api/v1/workshop-bookings/*/email-confirmations",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/favicon.ico"
                ).permitAll()
                .requestMatchers("/api/v1/members/me/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INVALID_TOKEN")
                    .message("Authentication token is missing, expired, or invalid")
                    .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                    .timestamp(Instant.now())
                    .build();
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }
}
