package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMailComposerTest {

    private static final String BASE = "https://mland.example";

    private static BookingNotificationDeliveryPayload payload(String link, List<String> tickets) {
        return new BookingNotificationDeliveryPayload("CONFIRMED_DELIVERY", "guest@example.com", "content", tickets, link, "corr");
    }

    private static MailMessage compose(String kind, String link, List<String> tickets) {
        return BookingMailComposer.compose(kind, "BK-1", payload(link, tickets), BASE);
    }

    @Test
    void confirmationHasTheBookingCodeTheTicketsAndTheBookingLink() {
        MailMessage message = compose("CONFIRMED_DELIVERY", "https://app.example/booking#abc", List.of("TK-1", "TK-2"));

        assertThat(message.to()).isEqualTo("guest@example.com");
        assertThat(message.subject()).isEqualTo("MLAND – Xác nhận đặt chỗ workshop BK-1");
        assertThat(message.text()).contains("BK-1", "TK-1", "TK-2", "https://app.example/booking#abc");
        assertThat(message.html()).contains("href=\"https://app.example/booking#abc\"", "<strong>TK-1</strong>");
    }

    @Test
    void theStoredPathBecomesAFullAddressSoTheMailClientCanOpenIt() {
        MailMessage message = compose("CONFIRMED_DELIVERY", "/workshop-booking/access#token-1", List.of("TK-1"));

        assertThat(message.text()).contains("https://mland.example/workshop-booking/access#token-1");
        assertThat(message.html()).contains("href=\"https://mland.example/workshop-booking/access#token-1\"");
        assertThat(message.text()).doesNotContain("http:///");
    }

    @Test
    void absoluteJoinsPathsAndKeepsFullAddresses() {
        assertThat(BookingMailComposer.absolute("/a#t", "https://x.example/")).isEqualTo("https://x.example/a#t");
        assertThat(BookingMailComposer.absolute("a#t", "https://x.example")).isEqualTo("https://x.example/a#t");
        assertThat(BookingMailComposer.absolute("https://y.example/a#t", "https://x.example")).isEqualTo("https://y.example/a#t");
        assertThat(BookingMailComposer.absolute("/a#t", "")).isEmpty();
        assertThat(BookingMailComposer.absolute("/a#t", null)).isEmpty();
        assertThat(BookingMailComposer.absolute("  ", "https://x.example")).isEmpty();
        assertThat(BookingMailComposer.absolute(null, "https://x.example")).isEmpty();
    }

    @Test
    void withoutALinkTheEmailPointsToTheLookupPageInstead() {
        MailMessage message = compose("CONFIRMED_DELIVERY", "", List.of("TK-1"));

        assertThat(message.text()).contains("trang Tra cứu").doesNotContain("http");
        assertThat(message.html()).doesNotContain("href=");
    }

    @Test
    void valuesAreEscapedInTheHtmlPart() {
        MailMessage message = compose("CONFIRMED_DELIVERY", "https://app.example/?a=1&b=\"2\"", List.of("<b>TK</b>"));

        assertThat(message.html()).contains("&lt;b&gt;TK&lt;/b&gt;", "a=1&amp;b=&quot;2&quot;").doesNotContain("<b>TK</b>");
    }

    @Test
    void resendAndLinkRotationHaveTheirOwnSubject() {
        assertThat(compose("CONFIRMATION_RESEND", "", List.of()).subject()).isEqualTo("MLAND – Gửi lại vé workshop BK-1");
        assertThat(compose("MAGIC_LINK_ROTATION", "", List.of()).subject()).contains("đã được làm mới");
    }

    @Test
    void anUnknownKindHasNoEmail() {
        assertThat(compose("SOMETHING_ELSE", "", List.of())).isNull();
        assertThat(compose(null, "", List.of())).isNull();
    }
}
