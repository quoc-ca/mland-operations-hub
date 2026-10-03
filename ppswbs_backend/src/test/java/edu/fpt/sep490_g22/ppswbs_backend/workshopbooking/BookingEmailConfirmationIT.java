package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidRequestException;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingEmailConfirmation;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.ConfirmationState;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.ConfirmationTokenState;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.BookingEmailConfirmationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.CapturedBookingMailSender;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BookingEmailConfirmationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkshopBookingService bookingService;

    @Autowired
    private WorkshopBookingRepository bookingRepository;

    @Autowired
    private BookingEmailConfirmationRepository confirmationRepository;

    @Autowired
    private CapturedBookingMailSender mailSender;

    @BeforeEach
    void setUp() {
        mailSender.clear();
    }

    @Test
    void whenGuestCreatesBooking_thenPaymentBlockedUntilConfirmed() {
        WorkshopBooking booking = bookingService.createGuestBooking("guest1@example.com");

        assertEquals(ConfirmationState.EMAIL_CONFIRMATION_PENDING, booking.getConfirmationState());

        // Attempting deposit payment before confirmation throws InvalidRequestException
        assertThrows(InvalidRequestException.class, () -> {
            bookingService.openDepositPayment(booking.getBookingCode());
        });
    }

    @Test
    void whenGuestConfirmsEmailWithin15Min_thenPaymentUnlocked() throws Exception {
        WorkshopBooking booking = bookingService.createGuestBooking("guest2@example.com");
        String rawToken = mailSender.getLastSentToken(booking.getBookingCode());
        assertNotNull(rawToken);

        String body = String.format("{\"confirmationToken\":\"%s\"}", rawToken);

        mockMvc.perform(post("/api/v1/workshop-bookings/{bookingCode}/email-confirmations", booking.getBookingCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        WorkshopBooking updated = bookingRepository.findByBookingCode(booking.getBookingCode()).orElseThrow();
        assertEquals(ConfirmationState.EMAIL_CONFIRMED, updated.getConfirmationState());

        // Deposit payment now succeeds
        assertNotNull(bookingService.openDepositPayment(booking.getBookingCode()));
    }

    @Test
    void whenConfirmationTokenExpires_thenBookingCancelledAndCapacityReleased() {
        WorkshopBooking booking = bookingService.createGuestBooking("guest3@example.com");
        BookingEmailConfirmation confirmation = confirmationRepository.findByBookingId(booking.getId()).orElseThrow();

        // Simulate time passage > 15 minutes
        confirmation.setExpiresAt(Instant.now().minusSeconds(60));
        confirmationRepository.saveAndFlush(confirmation);

        // Run expiry job
        bookingService.expirePendingConfirmations();

        WorkshopBooking cancelled = bookingRepository.findByBookingCode(booking.getBookingCode()).orElseThrow();
        assertEquals(ConfirmationState.CANCELLED_EMAIL_UNCONFIRMED, cancelled.getConfirmationState());

        // Payment attempt throws exception
        assertThrows(InvalidRequestException.class, () -> {
            bookingService.openDepositPayment(booking.getBookingCode());
        });
    }

    @Test
    void whenTokenIsReused_thenSecondAttemptFails() throws Exception {
        WorkshopBooking booking = bookingService.createGuestBooking("guest4@example.com");
        String rawToken = mailSender.getLastSentToken(booking.getBookingCode());

        // First confirmation succeeds
        bookingService.confirmBookingEmail(booking.getBookingCode(), rawToken);

        // Second confirmation attempt fails
        assertThrows(InvalidRequestException.class, () -> {
            bookingService.confirmBookingEmail(booking.getBookingCode(), rawToken);
        });
    }
}
