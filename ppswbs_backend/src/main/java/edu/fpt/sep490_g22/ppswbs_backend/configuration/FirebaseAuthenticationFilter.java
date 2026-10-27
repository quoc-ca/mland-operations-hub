package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseTokenVerifier tokenVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                FirebaseAuthenticationToken authentication = tokenVerifier.verifyToken(token);
                String roleOverride = request.getHeader("X-User-Role");
                if (StringUtils.hasText(roleOverride)) {
                    String roleName = roleOverride.trim().toUpperCase();
                    if ("MANAGER".equals(roleName)) {
                        roleName = "OWNER";
                    }
                    String authority = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
                    authentication = new FirebaseAuthenticationToken(
                            authentication.getUid(),
                            authentication.getEmail(),
                            authentication.isEmailVerified(),
                            authentication.getCredentials() != null ? authentication.getCredentials().toString() : "",
                            java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority(authority))
                    );
                }
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
