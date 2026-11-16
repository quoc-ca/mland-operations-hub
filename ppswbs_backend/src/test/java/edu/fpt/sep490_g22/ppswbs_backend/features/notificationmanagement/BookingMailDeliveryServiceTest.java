package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryFailed;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationReady;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.DeliveryFailureCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingMailDeliveryServiceTest {

    private static final String REF = "NTF-1";
    private static final String BOOKING = "BK-ABC123";

    private NotificationDeliveryRepository deliveries;
    private WorkshopBookingNotificationFacade bookings;
    private MailAdapter mail;
    private ApplicationEventPublisher eventPublisher;
    private BookingMailDeliveryService service;

    @BeforeEach
    void setUp() {
        deliveries = mock(NotificationDeliveryRepository.class);
        bookings = mock(WorkshopBookingNotificationFacade.class);
        mail = mock(MailAdapter.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new BookingMailDeliveryService(deliveries, bookings, mail, "https://mland.example", eventPublisher);
        when(deliveries.saveAndFlush(any(NotificationDelivery.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static BookingNotificationDeliveryPayload payload(String recipient, String link) {
        return new BookingNotificationDeliveryPayload("CONFIRMED_DELIVERY", recipient, "content",
                List.of("TK-1", "TK-2"), link, "corr");
    }

    private NotificationDelivery savedRow() {
        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void sendsTheTicketsAndTheLinkOnceAndRecordsSent() throws Exception {
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("guest@example.com", "https://app.example/booking#token"));

        service.deliver(REF, BOOKING, "CONFIRMED_DELIVERY");

        ArgumentCaptor<MailMessage> message = ArgumentCaptor.forClass(MailMessage.class);
        verify(mail).send(eq(REF), message.capture());
        assertThat(message.getValue().to()).isEqualTo("guest@example.com");
        assertThat(message.getValue().subject()).contains(BOOKING);
        assertThat(message.getValue().text()).contains("TK-1", "TK-2", "https://app.example/booking#token");
        NotificationDelivery row = savedRow();
        assertThat(row.getStatus()).isEqualTo(NotificationDelivery.SENT);
        assertThat(row.getSentAt()).isNotNull();
        assertThat(row.getAttempts()).isEqualTo(1);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void aHandoffDeliveredTwiceSendsOneEmail() throws Exception {
        when(deliveries.saveAndFlush(any(NotificationDelivery.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.deliver(REF, BOOKING, "CONFIRMED_DELIVERY");

        verify(mail, never()).send(anyString(), any());
        verify(bookings, never()).getBookingNotificationDeliveryPayload(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void withoutARecipientNothingIsSentAndTheNotificationIsSkipped() throws Exception {
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("  ", "https://app.example/booking#token"));

        service.deliver(REF, BOOKING, "CONFIRMED_DELIVERY");

        verify(mail, never()).send(anyString(), any());
        NotificationDelivery row = savedRow();
        assertThat(row.getStatus()).isEqualTo(NotificationDelivery.SKIPPED);
        assertThat(row.getLastError()).isEqualTo("NO_RECIPIENT");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void aKindWithoutABookingEmailIsSkipped() throws Exception {
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("guest@example.com", ""));

        service.deliver(REF, BOOKING, "SOMETHING_ELSE");

        verify(mail, never()).send(anyString(), any());
        assertThat(savedRow().getStatus()).isEqualTo(NotificationDelivery.SKIPPED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void aFailedSendIsKeptForRetryWithoutLeakingTheAddress() throws Exception {
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("guest@example.com", ""));
        doThrow(new IOException("550 guest@example.com rejected")).when(mail).send(anyString(), any());

        service.deliver(REF, BOOKING, "CONFIRMED_DELIVERY");

        NotificationDelivery row = savedRow();
        assertThat(row.getStatus()).isEqualTo(NotificationDelivery.FAILED);
        assertThat(row.getLastError()).isEqualTo("IOException").doesNotContain("guest@example.com");
        // Attempt 1 is transient: must NOT publish failure event
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void aDeliveryProblemNeverEscapesToTheCaller() {
        when(deliveries.saveAndFlush(any(NotificationDelivery.class))).thenThrow(new IllegalStateException("database down"));

        service.deliver(REF, BOOKING, "CONFIRMED_DELIVERY"); // must not throw
    }

    @Test
    void retryTakesAFailedDeliveryOnceAndSendsIt() throws Exception {
        NotificationDelivery failed = new NotificationDelivery();
        failed.setId(7L);
        failed.setNotificationReference(REF);
        failed.setBookingReference(BOOKING);
        failed.setNotificationKind("CONFIRMED_DELIVERY");
        failed.setStatus(NotificationDelivery.SENDING);
        failed.setAttempts(1);
        failed.setCreatedAt(Instant.now());
        failed.setUpdatedAt(Instant.now());
        when(deliveries.findTop50ByStatusAndAttemptsLessThanAndCreatedAtAfterOrderByCreatedAtAsc(
                eq(NotificationDelivery.FAILED), anyInt(), any(Instant.class))).thenReturn(List.of(failed));
        when(deliveries.claimForRetry(eq(7L), any(Instant.class))).thenReturn(1);
        when(deliveries.findById(7L)).thenReturn(Optional.of(failed));
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("guest@example.com", ""));

        service.retryFailed();

        verify(mail, times(1)).send(eq(REF), any());
        assertThat(failed.getStatus()).isEqualTo(NotificationDelivery.SENT);
        assertThat(failed.getAttempts()).isEqualTo(2);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void retryLeavesADeliveryAnotherCallerAlreadyTook() throws Exception {
        NotificationDelivery failed = new NotificationDelivery();
        failed.setId(8L);
        when(deliveries.findTop50ByStatusAndAttemptsLessThanAndCreatedAtAfterOrderByCreatedAtAsc(
                eq(NotificationDelivery.FAILED), anyInt(), any(Instant.class))).thenReturn(List.of(failed));
        when(deliveries.claimForRetry(eq(8L), any(Instant.class))).thenReturn(0);

        service.retryFailed();

        verify(mail, never()).send(anyString(), any());
        verify(deliveries, never()).findById(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void retryExhaustedPublishesBookingNotificationDeliveryFailed() throws Exception {
        NotificationDelivery failed = new NotificationDelivery();
        failed.setId(9L);
        failed.setNotificationReference(REF);
        failed.setBookingReference(BOOKING);
        failed.setNotificationKind("CONFIRMED_DELIVERY");
        failed.setStatus(NotificationDelivery.SENDING);
        failed.setAttempts(1);
        failed.setCreatedAt(Instant.now());
        failed.setUpdatedAt(Instant.now());
        when(deliveries.findTop50ByStatusAndAttemptsLessThanAndCreatedAtAfterOrderByCreatedAtAsc(
                eq(NotificationDelivery.FAILED), anyInt(), any(Instant.class))).thenReturn(List.of(failed));
        when(deliveries.claimForRetry(eq(9L), any(Instant.class))).thenReturn(1);
        when(deliveries.findById(9L)).thenReturn(Optional.of(failed));
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("guest@example.com", ""));
        doThrow(new ConnectException("Connection timed out")).when(mail).send(anyString(), any());

        service.retryFailed();

        assertThat(failed.getStatus()).isEqualTo(NotificationDelivery.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(2);

        ArgumentCaptor<BookingNotificationDeliveryFailed> eventCaptor =
                ArgumentCaptor.forClass(BookingNotificationDeliveryFailed.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        BookingNotificationDeliveryFailed event = eventCaptor.getValue();
        assertThat(event.notificationReference()).isEqualTo(REF);
        assertThat(event.bookingReference()).isEqualTo(BOOKING);
        assertThat(event.notificationKind()).isEqualTo("CONFIRMED_DELIVERY");
        assertThat(event.failureCode()).isEqualTo(DeliveryFailureCode.PROVIDER_UNAVAILABLE);
        assertThat(event.failedAt()).isNotNull();
        assertThat(event.correlationReference()).isEqualTo("corr");
    }

    @Test
    void retryExhaustedWithMissingPayloadPublishesPayloadMissing() throws Exception {
        NotificationDelivery failed = new NotificationDelivery();
        failed.setId(10L);
        failed.setNotificationReference(REF);
        failed.setBookingReference(BOOKING);
        failed.setNotificationKind("CONFIRMED_DELIVERY");
        failed.setStatus(NotificationDelivery.SENDING);
        failed.setAttempts(1);
        failed.setCreatedAt(Instant.now());
        failed.setUpdatedAt(Instant.now());
        when(deliveries.findTop50ByStatusAndAttemptsLessThanAndCreatedAtAfterOrderByCreatedAtAsc(
                eq(NotificationDelivery.FAILED), anyInt(), any(Instant.class))).thenReturn(List.of(failed));
        when(deliveries.claimForRetry(eq(10L), any(Instant.class))).thenReturn(1);
        when(deliveries.findById(10L)).thenReturn(Optional.of(failed));
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(null);

        service.retryFailed();

        assertThat(failed.getStatus()).isEqualTo(NotificationDelivery.FAILED);
        assertThat(failed.getLastError()).isEqualTo("PAYLOAD_MISSING");
        assertThat(failed.getAttempts()).isEqualTo(2);

        ArgumentCaptor<BookingNotificationDeliveryFailed> eventCaptor =
                ArgumentCaptor.forClass(BookingNotificationDeliveryFailed.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        BookingNotificationDeliveryFailed event = eventCaptor.getValue();
        assertThat(event.notificationReference()).isEqualTo(REF);
        assertThat(event.failureCode()).isEqualTo(DeliveryFailureCode.PAYLOAD_MISSING);
    }

    @Test
    void settlementNotificationFailureDoesNotPublishBookingDeliveryFailed() throws Exception {
        NotificationDelivery failed = new NotificationDelivery();
        failed.setId(11L);
        failed.setNotificationReference("SETTLE-NOTIF-99");
        failed.setBookingReference(BOOKING);
        failed.setNotificationKind("SETTLEMENT_REFUND_ACCOUNT_REQUIRED");
        failed.setStatus(NotificationDelivery.SENDING);
        failed.setAttempts(1);
        failed.setCreatedAt(Instant.now());
        failed.setUpdatedAt(Instant.now());
        when(deliveries.findTop50ByStatusAndAttemptsLessThanAndCreatedAtAfterOrderByCreatedAtAsc(
                eq(NotificationDelivery.FAILED), anyInt(), any(Instant.class))).thenReturn(List.of(failed));
        when(deliveries.claimForRetry(eq(11L), any(Instant.class))).thenReturn(1);
        when(deliveries.findById(11L)).thenReturn(Optional.of(failed));

        service.retryFailed();

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void failureCodeMappingCoversAllCategories() {
        // PAYLOAD_MISSING
        assertThat(BookingMailDeliveryService.determineFailureCode(null, "PAYLOAD_MISSING"))
                .isEqualTo(DeliveryFailureCode.PAYLOAD_MISSING);

        // RECIPIENT_INVALID
        assertThat(BookingMailDeliveryService.determineFailureCode(new IOException("550 User unknown"), null))
                .isEqualTo(DeliveryFailureCode.RECIPIENT_INVALID);
        assertThat(BookingMailDeliveryService.determineFailureCode(new RuntimeException("Invalid address syntax"), null))
                .isEqualTo(DeliveryFailureCode.RECIPIENT_INVALID);

        // PROVIDER_REJECTED
        assertThat(BookingMailDeliveryService.determineFailureCode(new IOException("554 Relay Access Denied"), null))
                .isEqualTo(DeliveryFailureCode.PROVIDER_REJECTED);
        assertThat(BookingMailDeliveryService.determineFailureCode(new RuntimeException("535 Authentication credentials invalid"), null))
                .isEqualTo(DeliveryFailureCode.PROVIDER_REJECTED);

        // PROVIDER_UNAVAILABLE
        assertThat(BookingMailDeliveryService.determineFailureCode(new ConnectException("Connection refused"), null))
                .isEqualTo(DeliveryFailureCode.PROVIDER_UNAVAILABLE);
        assertThat(BookingMailDeliveryService.determineFailureCode(null, null))
                .isEqualTo(DeliveryFailureCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void theConsumerHandsTheEventToTheDeliveryService() {
        BookingMailDeliveryService delivery = mock(BookingMailDeliveryService.class);
        BookingNotificationReadyConsumer consumer = new BookingNotificationReadyConsumer(delivery, Runnable::run);

        consumer.onBookingNotificationReady(new BookingNotificationReady(REF, BOOKING, "CONFIRMED_DELIVERY", "corr"));

        verify(delivery).deliver(REF, BOOKING, "CONFIRMED_DELIVERY", "corr");
    }
}
