package edu.fpt.sep490_g22.ppswbs_backend.configuration;

public interface FirebaseTokenVerifier {
    FirebaseAuthenticationToken verifyToken(String idToken);
}
