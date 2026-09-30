package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

public interface BookingMailSender {
    void sendConfirmationEmail(String toEmail, String bookingCode, String rawToken);
}
