package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationReady;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private BookingMailDeliveryService service;

    @BeforeEach
    void setUp() {
        deliveries = mock(NotificationDeliveryRepository.class);
        bookings = mock(WorkshopBookingNotificationFacade.class);
        mail = mock(MailAdapter.class);
        service = new BookingMailDeliveryService(deliveries, bookings, mail, "https://mland.example");
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
    }

    @Test
    void aHandoffDeliveredTwiceSendsOneEmail() throws Exception {
        when(deliveries.saveAndFlush(any(NotificationDelivery.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.deliver(REF, BOOKING, "CONFIRMED_DELIVERY");

        verify(mail, never()).send(anyString(), any());
        verify(bookings, never()).getBookingNotificationDeliveryPayload(any());
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
    }

    @Test
    void aKindWithoutABookingEmailIsSkipped() throws Exception {
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("guest@example.com", ""));

        service.deliver(REF, BOOKING, "SOMETHING_ELSE");

        verify(mail, never()).send(anyString(), any());
        assertThat(savedRow().getStatus()).isEqualTo(NotificationDelivery.SKIPPED);
    }

    @Test
    void aFailedSendIsKeptForRetryWithoutLeakingTheAddress() throws Exception {
        when(bookings.getBookingNotificationDeliveryPayload(any(BookingNotificationDeliveryPayloadQuery.class)))
                .thenReturn(payload("guest@example.com", ""));
        org.mockito.Mockito.doThrow(new IOException("550 guest@example.com rejected")).when(mail).send(anyString(), any());

        service.deliver(REF, BOOKING, "CONFIRMED_DELIVERY");

        NotificationDelivery row = savedRow();
        assertThat(row.getStatus()).isEqualTo(NotificationDelivery.FAILED);
        assertThat(row.getLastError()).isEqualTo("IOException").doesNotContain("guest@example.com");
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
    }

    @Test
    void theConsumerHandsTheEventToTheDeliveryService() {
        BookingMailDeliveryService delivery = mock(BookingMailDeliveryService.class);
        BookingNotificationReadyConsumer consumer = new BookingNotificationReadyConsumer(delivery, Runnable::run);

        consumer.onBookingNotificationReady(new BookingNotificationReady(REF, BOOKING, "CONFIRMED_DELIVERY", "corr"));

        verify(delivery).deliver(REF, BOOKING, "CONFIRMED_DELIVERY");
    }
}
