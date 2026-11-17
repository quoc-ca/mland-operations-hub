package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;

import java.util.List;

/** Builds the booking email (Vietnamese) from the protected delivery payload. Pure: no I/O, no logging. */
final class BookingMailComposer {

    private BookingMailComposer() {
    }

    /** The email for this notification kind, or null when the kind has no booking email. */
    static MailMessage compose(String kind, String bookingReference, BookingNotificationDeliveryPayload payload,
                               String frontendBaseUrl) {
        if (kind != null && kind.startsWith("GROUP_")) {
            return composeGroup(payload);
        }
        String subject = subject(kind, bookingReference);
        if (subject == null) return null;
        List<String> tickets = payload.renderedTicketArtifacts() == null ? List.of() : payload.renderedTicketArtifacts();
        String link = absolute(payload.bookingAccessLink(), frontendBaseUrl);
        String intro = intro(kind);

        StringBuilder text = new StringBuilder();
        text.append("Xin chào,\n\n").append(intro).append("\n\n");
        text.append("Mã đặt chỗ: ").append(bookingReference).append("\n");
        if (!tickets.isEmpty()) {
            text.append("\nVé của bạn (xuất trình mã vé khi check-in):\n");
            tickets.forEach(ticket -> text.append("  - ").append(ticket).append("\n"));
        }
        if (!link.isEmpty()) {
            text.append("\nXem đặt chỗ và vé của bạn tại liên kết sau (liên kết riêng, đừng chia sẻ cho người khác):\n")
                    .append(link).append("\n");
        } else {
            text.append("\nBạn có thể tra cứu đặt chỗ bằng mã đặt chỗ ở trang Tra cứu của MLAND.\n");
        }
        text.append("\nCảm ơn bạn đã chọn MLAND.\n");

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,sans-serif;color:#1a1a1a;line-height:1.55\">");
        html.append("<p>Xin chào,</p><p>").append(escape(intro)).append("</p>");
        html.append("<p>Mã đặt chỗ: <strong>").append(escape(bookingReference)).append("</strong></p>");
        if (!tickets.isEmpty()) {
            html.append("<p>Vé của bạn (xuất trình mã vé khi check-in):</p><ul>");
            tickets.forEach(ticket -> html.append("<li><strong>").append(escape(ticket)).append("</strong></li>"));
            html.append("</ul>");
        }
        if (!link.isEmpty()) {
            html.append("<p><a href=\"").append(escape(link)).append("\" style=\"display:inline-block;padding:12px 20px;")
                    .append("background:#002967;color:#fff;text-decoration:none\">Xem đặt chỗ và vé</a></p>")
                    .append("<p style=\"color:#666;font-size:13px\">Liên kết này là riêng của bạn, đừng chia sẻ cho người khác.</p>");
        } else {
            html.append("<p>Bạn có thể tra cứu đặt chỗ bằng mã đặt chỗ ở trang Tra cứu của MLAND.</p>");
        }
        html.append("<p>Cảm ơn bạn đã chọn MLAND.</p></div>");
        return new MailMessage(payload.storedRecipient(), subject, text.toString(), html.toString());
    }

    /**
     * Booking-group emails (003.1 FR-040/FR-041): Booking renders the approved subject and body; this only lays
     * them out. Returns null when the snapshot has no subject.
     */
    private static MailMessage composeGroup(BookingNotificationDeliveryPayload payload) {
        if (payload.renderedSubject() == null || payload.renderedSubject().isBlank()) return null;
        String body = payload.renderedBookingContent() == null ? "" : payload.renderedBookingContent();
        List<String> artifacts = payload.renderedTicketArtifacts() == null ? List.of() : payload.renderedTicketArtifacts();
        StringBuilder text = new StringBuilder(body).append("\n");
        artifacts.forEach(item -> text.append("\n").append(item));
        text.append("\n\nCảm ơn bạn đã chọn MLAND.\n");
        StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif;color:#1a1a1a;line-height:1.55\">");
        for (String paragraph : body.split("\n\n")) {
            html.append("<p>").append(escape(paragraph).replace("\n", "<br>")).append("</p>");
        }
        if (!artifacts.isEmpty()) {
            html.append("<ul>");
            artifacts.forEach(item -> html.append("<li><strong>").append(escape(item)).append("</strong></li>"));
            html.append("</ul>");
        }
        html.append("<p>Cảm ơn bạn đã chọn MLAND.</p></div>");
        return new MailMessage(payload.storedRecipient(), payload.renderedSubject(), text.toString(), html.toString());
    }

    /**
     * The booking stores its access link as a path ("/workshop-booking/access#token"). A mail client cannot open a
     * path, so it is joined to the public address of the site; a link that already has an address is kept as it is.
     */
    static String absolute(String link, String frontendBaseUrl) {
        if (link == null || link.isBlank()) return "";
        String trimmed = link.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.isEmpty()) return "";
        return base + (trimmed.startsWith("/") ? "" : "/") + trimmed;
    }

    private static String subject(String kind, String bookingReference) {
        if (kind == null) return null;
        return switch (kind) {
            case "CONFIRMED_DELIVERY", "CONFIRMATION_DELIVERY" -> "MLAND – Xác nhận đặt chỗ workshop " + bookingReference;
            case "CONFIRMATION_RESEND" -> "MLAND – Gửi lại vé workshop " + bookingReference;
            case "MAGIC_LINK_ROTATION" -> "MLAND – Liên kết truy cập đặt chỗ " + bookingReference + " đã được làm mới";
            default -> null;
        };
    }

    private static String intro(String kind) {
        return switch (kind) {
            case "CONFIRMATION_RESEND" -> "Chúng tôi gửi lại vé và thông tin đặt chỗ workshop của bạn theo yêu cầu.";
            case "MAGIC_LINK_ROTATION" -> "Liên kết truy cập đặt chỗ của bạn đã được làm mới. Hãy dùng liên kết mới bên dưới.";
            default -> "Đặt chỗ workshop của bạn đã được xác nhận. Dưới đây là vé và liên kết xem đặt chỗ.";
        };
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
