package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Booking-group emails use the subject and body rendered by Booking (003.1 FR-040/FR-041). */
class GroupBookingMailComposerTest {

    @Test
    void groupKind_usesRenderedSubjectBodyAndArtifacts() {
        BookingNotificationDeliveryPayload payload = new BookingNotificationDeliveryPayload("GROUP_TICKET_RECIPIENT",
                "friend@example.com", "Xin chào,\n\nbu***@example.com đã gửi bạn một vé <workshop>.",
                List.of("Mã vé: TK-1", "QR: QR-1"), "", "corr", "MLAND – bu***@example.com gửi bạn vé workshop Gói A");

        MailMessage message = BookingMailComposer.compose("GROUP_TICKET_RECIPIENT", "GRP-1", payload, "http://x");

        assertThat(message.to()).isEqualTo("friend@example.com");
        assertThat(message.subject()).isEqualTo("MLAND – bu***@example.com gửi bạn vé workshop Gói A");
        assertThat(message.text()).contains("QR: QR-1").contains("Mã vé: TK-1");
        assertThat(message.html()).contains("&lt;workshop&gt;").doesNotContain("<workshop>");
    }

    @Test
    void groupKind_withoutSubject_isNotMailed() {
        BookingNotificationDeliveryPayload payload = new BookingNotificationDeliveryPayload("GROUP_TICKET_RECIPIENT",
                "friend@example.com", "body", List.of(), "", "corr", null);

        assertThat(BookingMailComposer.compose("GROUP_TICKET_RECIPIENT", "GRP-1", payload, "http://x")).isNull();
    }
}
