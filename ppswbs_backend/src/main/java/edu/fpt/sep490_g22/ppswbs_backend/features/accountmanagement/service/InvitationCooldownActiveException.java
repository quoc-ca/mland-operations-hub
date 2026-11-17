package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class InvitationCooldownActiveException extends RuntimeException {
    public InvitationCooldownActiveException(String message) {
        super(message);
    }
}
