package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Slf4j
public class DefaultFirebaseTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public FirebaseAuthenticationToken verifyToken(String idToken) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            boolean emailVerified = decodedToken.isEmailVerified();

            return new FirebaseAuthenticationToken(
                    uid,
                    email,
                    emailVerified,
                    idToken,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER"))
            );
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            log.warn("Firebase ID Token verification failed: {}", e.getMessage());
            throw new InvalidTokenException("Invalid or expired Firebase ID token");
        }
    }
}
