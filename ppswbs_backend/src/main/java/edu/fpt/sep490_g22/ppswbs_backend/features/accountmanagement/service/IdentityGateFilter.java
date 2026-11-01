package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component("identityGateFilter")
@RequiredArgsConstructor
@Slf4j
public class IdentityGateFilter extends OncePerRequestFilter {

    @Bean
    public FilterRegistrationBean<IdentityGateFilter> identityGateFilterRegistration(IdentityGateFilter filter) {
        FilterRegistrationBean<IdentityGateFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private final TokenVerificationService tokenVerificationService;
    private final AccountFacade accountFacade;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod())
                && "/api/v1/integrations/payments/vnpay/events".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(request.getMethod())
                && path.matches("/api/v1/workshop-bookings/[^/]+/payment-attempts")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/workshop-bookings".equals(path)
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            return true;
        }
        // Guest booking lookup is explicitly public and uses booking code plus
        // one contact proof (email or phone) instead of a Bearer token.
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/booking-lookups".equals(path)) {
            return true;
        }
        boolean customerBillRoute = ("GET".equalsIgnoreCase(request.getMethod())
                && path.matches("/api/v1/workshop-bookings/[^/]+/final-bill"))
                || ("POST".equalsIgnoreCase(request.getMethod())
                && path.matches("/api/v1/workshop-bookings/[^/]+/final-bill/charges"));
        if (customerBillRoute && request.getHeader(HttpHeaders.AUTHORIZATION) == null) return true;
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        response.setHeader("X-Correlation-Id", correlationId);

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean isAiSupportMessage = "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/ai-support/messages".equals(request.getRequestURI());

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (isAiSupportMessage && authHeader == null) {
                // Anonymous Guest access allowed for Basic AI Support
                filterChain.doFilter(request, response);
                return;
            }
            writeError(response, HttpStatus.UNAUTHORIZED, "MISSING_BEARER_TOKEN", "Bearer authentication token is required", correlationId);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "MISSING_BEARER_TOKEN", "Bearer authentication token is empty", correlationId);
            return;
        }

        VerifiedIdentity identity;
        try {
            identity = tokenVerificationService.verify(token);
        } catch (TokenExpiredException ex) {
            writeError(response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", ex.getMessage(), correlationId);
            return;
        } catch (TokenRevokedException ex) {
            writeError(response, HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", ex.getMessage(), correlationId);
            return;
        } catch (InvalidTokenException ex) {
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", ex.getMessage(), correlationId);
            return;
        } catch (Exception ex) {
            log.error("Identity verification failure correlationId={}: {}", correlationId, ex.getMessage(), ex);
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Identity provider is temporarily unavailable", correlationId);
            return;
        }

        Optional<CurrentAccountActor> actorOpt = accountFacade.resolveActor(identity.uid(), identity.emailVerified());
        if (actorOpt.isPresent()) {
            CurrentAccountActor actor = actorOpt.get();
            if (actor.status() == ActorStatus.SUSPENDED) {
                writeError(response, HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Account has been suspended", correlationId);
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                actor,
                token,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name()))
            );
            authentication.setDetails(identity.toFacade());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute("currentAccountActor", actor);
            request.setAttribute("verifiedIdentity", identity.toFacade());
        } else {
            String path = request.getRequestURI();
            String method = request.getMethod();
            if ("/api/v1/accounts/me".equals(path) && "PUT".equalsIgnoreCase(method)) {
                // Allow bootstrap for new identity
                CurrentAccountActor bootstrapActor = new CurrentAccountActor(
                    null,
                    ActorRole.MEMBER,
                    ActorStatus.ACTIVE,
                    identity.emailVerified(),
                    List.of()
                );
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    bootstrapActor,
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                );
                authentication.setDetails(identity.toFacade());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("currentAccountActor", bootstrapActor);
                request.setAttribute("verifiedIdentity", identity.toFacade());
            } else {
                writeError(response, HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND", "Account not found for verified identity. Please bootstrap account first.", correlationId);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(
        HttpServletResponse response,
        HttpStatus status,
        String code,
        String message,
        String correlationId
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.builder()
            .code(code)
            .message(message)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String header = request.getHeader("X-Correlation-Id");
        return (header != null && !header.isBlank()) ? header.trim() : UUID.randomUUID().toString();
    }
}
