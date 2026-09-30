package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@Getter
public class CapturedBookingMailSender implements BookingMailSender {

    private final ConcurrentHashMap<String, String> sentTokens = new ConcurrentHashMap<>();

    @Override
    public void sendConfirmationEmail(String toEmail, String bookingCode, String rawToken) {
        log.info("CAPTURED MAIL: Sent booking confirmation email to: {}, bookingCode: {}, rawToken: [REDACTED_IN_LOG]", toEmail, bookingCode);
        sentTokens.put(bookingCode, rawToken);
    }

    public String getLastSentToken(String bookingCode) {
        return sentTokens.get(bookingCode);
    }

    public void clear() {
        sentTokens.clear();
    }
}
