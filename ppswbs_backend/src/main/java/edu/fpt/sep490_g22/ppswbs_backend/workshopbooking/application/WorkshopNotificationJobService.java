package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopNotificationJobService {

    private final NotificationIntentRepository notificationIntentRepository;
    private final WorkshopBookingRepository bookingRepository;
    private final WorkshopPackageRepository packageRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final BookingTicketRepository ticketRepository;
    
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Scheduled(fixedDelay = 15000) // Run every 15 seconds
    @Transactional
    public void processPendingNotifications() {
        List<NotificationIntent> pendingIntents = notificationIntentRepository.findByStatusInAndNextRetryAtBefore(
                List.of("PENDING", "RETRY"), Instant.now());

        if (pendingIntents.isEmpty()) {
            return;
        }

        log.info("Found {} pending notification intents", pendingIntents.size());

        for (NotificationIntent intent : pendingIntents) {
            try {
                if ("CONFIRMATION".equals(intent.getIntentType())) {
                    sendConfirmationEmail(intent);
                } else {
                    log.warn("Unknown intent type: {}", intent.getIntentType());
                }
                intent.setStatus("SENT");
                intent.setLastAttemptedAt(Instant.now());
            } catch (Exception e) {
                log.error("Failed to send notification for intent {}", intent.getId(), e);
                intent.setAttemptCount(intent.getAttemptCount() + 1);
                intent.setLastAttemptedAt(Instant.now());
                if (intent.getAttemptCount() >= 3) {
                    intent.setStatus("FAILED");
                } else {
                    intent.setStatus("RETRY");
                    // Exponential backoff
                    intent.setNextRetryAt(Instant.now().plusSeconds(60L * intent.getAttemptCount()));
                }
            }
            notificationIntentRepository.save(intent);
        }
    }

    private void sendConfirmationEmail(NotificationIntent intent) throws Exception {
        WorkshopBooking booking = bookingRepository.findById(intent.getBookingId())
                .orElseThrow(() -> new Exception("Booking not found"));
        
        WorkshopPackage pkg = packageRepository.findById(booking.getPackageId()).orElse(null);
        WorkshopSession session = sessionRepository.findById(booking.getSessionId()).orElse(null);
        BookingTicket ticket = ticketRepository.findByBookingIdAndRevokedFalse(booking.getId()).orElse(null);

        if (pkg == null || session == null || ticket == null) {
            throw new Exception("Missing booking details or ticket");
        }

        Context context = new Context();
        context.setVariable("bookingCode", booking.getBookingCode());
        context.setVariable("packageName", pkg.getName());
        context.setVariable("sessionDate", DATE_FORMATTER.format(session.getSessionDate().atStartOfDay().atZone(ZoneId.of("UTC"))));
        context.setVariable("location", session.getLocation());
        context.setVariable("participantCount", booking.getParticipantCount());
        context.setVariable("qrCodeData", ticket.getQrValue()); // Assuming frontend or email client will render this, or we could generate an image

        String htmlContent = templateEngine.process("email/booking-confirmation", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(intent.getRecipientEmailSnapshot());
        helper.setSubject("Xác nhận Đặt chỗ Workshop - " + pkg.getName());
        helper.setText(htmlContent, true); // true = isHtml

        mailSender.send(message);
        log.info("Sent confirmation email to {}", intent.getRecipientEmailSnapshot());
    }
}
