package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {

    private final String uid;
    private final String email;
    private final boolean emailVerified;
    private final String credentials;

    public FirebaseAuthenticationToken(String uid, String email, boolean emailVerified, String credentials,
                                       Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.uid = uid;
        this.email = email;
        this.emailVerified = emailVerified;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return uid;
    }
}
