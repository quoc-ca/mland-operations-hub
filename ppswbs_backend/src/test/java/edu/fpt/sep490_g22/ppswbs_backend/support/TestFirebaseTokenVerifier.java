package edu.fpt.sep490_g22.ppswbs_backend.support;

import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidTokenException;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.FirebaseAuthenticationToken;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.FirebaseTokenVerifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@Primary
@Profile("test")
public class TestFirebaseTokenVerifier implements FirebaseTokenVerifier {

    private final Map<String, TokenInfo> mockTokens = new HashMap<>();

    public void registerToken(String token, String uid, String email, boolean emailVerified) {
        mockTokens.put(token, new TokenInfo(uid, email, emailVerified));
    }

    public void clearTokens() {
        mockTokens.clear();
    }

    @Override
    public FirebaseAuthenticationToken verifyToken(String idToken) {
        if (mockTokens.containsKey(idToken)) {
            TokenInfo info = mockTokens.get(idToken);
            return new FirebaseAuthenticationToken(
                    info.uid,
                    info.email,
                    info.emailVerified,
                    idToken,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER"))
            );
        }

        if (idToken != null && idToken.startsWith("valid-token-")) {
            String uid = idToken.substring("valid-token-".length());
            return new FirebaseAuthenticationToken(
                    uid,
                    uid + "@example.com",
                    true,
                    idToken,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER"))
            );
        }

        throw new InvalidTokenException("Invalid or expired test token");
    }

    public static class TokenInfo {
        public String uid;
        public String email;
        public boolean emailVerified;

        public TokenInfo(String uid, String email, boolean emailVerified) {
            this.uid = uid;
            this.email = email;
            this.emailVerified = emailVerified;
        }
    }
}
