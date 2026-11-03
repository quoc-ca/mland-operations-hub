package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class TokenVerificationService {

    @Value("${firebase.emulator.host:}")
    private String emulatorHost;

    public VerifiedIdentity verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token is missing or blank");
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                // If FirebaseApp is not initialized (e.g. in test or emulator), handle safely
                log.warn("FirebaseApp has not been initialized. Attempting default instance.");
            }

            FirebaseAuth auth = FirebaseAuth.getInstance();
            // checkRevoked=true is mandatory per Constitution Principle II
            FirebaseToken decodedToken = auth.verifyIdToken(token, true);

            Instant authTime = decodedToken.getClaims().get("auth_time") != null
                ? Instant.ofEpochSecond(((Number) decodedToken.getClaims().get("auth_time")).longValue())
                : Instant.now();

            return new VerifiedIdentity(
                decodedToken.getUid(),
                decodedToken.getEmail(),
                decodedToken.isEmailVerified(),
                authTime
            );

        } catch (FirebaseAuthException e) {
            String errorCode = e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "";
            log.warn("Firebase token verification failed with error code: {}", errorCode);

            if ("REVOKED_ID_TOKEN".equalsIgnoreCase(errorCode)) {
                throw new TokenRevokedException("Firebase token has been revoked");
            } else if ("EXPIRED_ID_TOKEN".equalsIgnoreCase(errorCode)) {
                throw new TokenExpiredException("Firebase token has expired");
            } else {
                throw new InvalidTokenException("Invalid Firebase token: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            if (e instanceof TokenRevokedException tre) throw tre;
            if (e instanceof TokenExpiredException tee) throw tee;
            if (e instanceof InvalidTokenException ite) throw ite;
            throw new InvalidTokenException("Unexpected error during token verification", e);
        }
    }
}
