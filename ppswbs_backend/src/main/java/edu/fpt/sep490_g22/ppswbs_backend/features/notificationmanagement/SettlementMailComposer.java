package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationDeliveryPayload;

/**
 * Builds a Settlement customer email (Vietnamese) from the protected payload. Settlement renders the body and, for
 * the refund-account request, the subject and the private link path. Pure: no I/O, no logging.
 */
final class SettlementMailComposer {

    private SettlementMailComposer() {
    }

    /** The email, or null when the kind has no subject to send under. */
    static MailMessage compose(String settlementReference, SettlementNotificationDeliveryPayload payload,
                               String frontendBaseUrl) {
        String subject = payload.renderedSubject() != null && !payload.renderedSubject().isBlank()
                ? payload.renderedSubject() : subject(payload.notificationKind());
        if (subject == null) return null;
        String body = payload.renderedSettlementContent() == null ? "" : payload.renderedSettlementContent();
        String link = BookingMailComposer.absolute(payload.actionLink(), frontendBaseUrl);
        boolean accountLink = "REFUND_ACCOUNT_REQUEST".equals(payload.notificationKind());

        StringBuilder text = new StringBuilder("Xin chào,\n\n").append(body).append("\n");
        if (!link.isEmpty()) {
            text.append("\n").append(accountLink ? "Nhập tài khoản nhận hoàn" : "Mở liên kết").append(":\n")
                    .append(link).append("\nLiên kết này là riêng của bạn, đừng chia sẻ cho người khác.\n");
        }
        text.append("\nCảm ơn bạn đã chọn MLAND.\n");

        StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif;color:#1a1a1a;line-height:1.55\">")
                .append("<p>Xin chào,</p>");
        for (String paragraph : body.split("\n\n")) {
            html.append("<p>").append(escape(paragraph).replace("\n", "<br>")).append("</p>");
        }
        if (!link.isEmpty()) {
            html.append("<p><a href=\"").append(escape(link)).append("\" style=\"display:inline-block;padding:12px 20px;")
                    .append("background:#002967;color:#fff;text-decoration:none\">")
                    .append(accountLink ? "Nhập tài khoản nhận hoàn" : "Mở liên kết").append("</a></p>")
                    .append("<p style=\"color:#666;font-size:13px\">Liên kết này là riêng của bạn, đừng chia sẻ cho người khác.</p>");
        }
        html.append("<p>Cảm ơn bạn đã chọn MLAND.</p></div>");
        return new MailMessage(payload.storedRecipient(), subject, text.toString(), html.toString());
    }

    private static String subject(String kind) {
        if (kind == null) return null;
        return switch (kind) {
            case "FINAL_PAYMENT_VERIFIED" -> "MLAND – Đã nhận thanh toán";
            case "REFUND_VERIFIED" -> "MLAND – Đã hoàn tiền";
            case "REFUND_ACCOUNT_REMINDER" -> "MLAND – Cung cấp tài khoản để nhận hoàn (nhắc lại)";
            default -> null;
        };
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
