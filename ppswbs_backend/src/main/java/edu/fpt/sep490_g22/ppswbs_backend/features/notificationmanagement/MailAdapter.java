package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

/** Sends one email, or throws when it could not. */
interface MailAdapter {
    void send(String notificationReference, MailMessage message) throws Exception;
}
