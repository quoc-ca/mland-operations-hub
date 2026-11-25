package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Hands a customer-facing verified settlement outcome to Feature 007. The recipient is the contact email stored on the
 * direct booking; without one the notification is skipped. Runs inside the transaction that accepted the outcome, so the
 * handoff and the outcome commit together; a skipped or failed recipient lookup never affects the outcome.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementCustomerNotifier {
    static final String FINAL_PAYMENT_VERIFIED = "FINAL_PAYMENT_VERIFIED";
    static final String REFUND_VERIFIED = "REFUND_VERIFIED";
    static final String REFUND_ACCOUNT_REMINDER = "REFUND_ACCOUNT_REMINDER";

    private final SettlementNotificationHandoffService handoffs;
    private final WorkshopBookingSettlementAdapter booking;

    /** Reminds the payer that Mland still needs their account before a bank refund can move (FR-040). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyRefundAccountNeeded(String bookingReference, long amountVnd, String refundReference,
                                            String correlationReference) {
        return notifyVerified(bookingReference, REFUND_ACCOUNT_REMINDER, amountVnd, refundReference,
                correlationReference);
    }

    static final String REFUND_ACCOUNT_REQUEST = "REFUND_ACCOUNT_REQUEST";
    /** Frontend path that reads the token from the fragment, so it never reaches server or referrer logs. */
    static final String REFUND_ACCOUNT_PATH = "/refund-account#";

    /**
     * Template 7 (approved 2026-11-12): asks the payer for the refund account with a fresh 7-day link; a reminder on
     * day 3 or 7 uses the same content with "(nhắc lại)". The link is stored only in the email snapshot until it
     * expires, never in events, audit or logs.
     *
     * @return the notification reference, or null when the booking has no contact email
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyRefundAccountRequest(SettlementRefundRequest request, String rawLinkToken,
                                             java.time.Instant linkExpiresAt, boolean reminder,
                                             String correlationReference) {
        String recipient;
        try {
            recipient = booking.notificationRecipient(request.getBookingReference());
        } catch (RuntimeException failure) {
            log.warn("Refund-account email skipped, recipient lookup failed: refund={} exceptionType={}",
                    request.getRequestReference(), failure.getClass().getSimpleName());
            return null;
        }
        if (recipient == null || recipient.isBlank()) {
            log.info("Refund-account email skipped, booking has no contact email: refund={}",
                    request.getRequestReference());
            return null;
        }
        String amount = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(request.getAmountVnd())
                + " VND";
        String order = request.getGroupReference() != null ? request.getGroupReference() : request.getBookingReference();
        String subject = "MLAND – Cung cấp tài khoản để nhận hoàn " + amount + " (đơn " + order + ")"
                + (reminder ? " (nhắc lại)" : "");
        String target = request.getBookingReference().equals(order) ? "đơn " + order : "vé " + request.getBookingReference();
        String body = "MLAND sẽ hoàn " + amount + " cho " + target + " với lý do: " + reasonLabel(request.getReason())
                + ". Vui lòng nhập ngân hàng, số tài khoản và tên chủ tài khoản tại liên kết dưới đây trong 7 ngày. "
                + "Vui lòng nhập tài khoản của chính người đã thanh toán và kiểm tra lại ngân hàng, số tài khoản, tên chủ tài khoản trước khi gửi.\n\n"
                + "Bạn cũng có thể cung cấp tại quầy MLAND.";
        return handoffs.create(request.getRequestReference(), REFUND_ACCOUNT_REQUEST, correlationReference, recipient,
                body, subject, REFUND_ACCOUNT_PATH + rawLinkToken, linkExpiresAt);
    }

    /**
     * T159 (FR-012a): the account-link token is cleared from every stored email of this refund request once the link
     * is used or revoked (resend, return to PENDING_ACCOUNT, key replacement, counter entry, closing). The rest of the
     * payload stays for its 30 days. Nothing here logs or publishes the token.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scrubRefundAccountLink(String requestReference) {
        handoffs.scrubActionLinks(requestReference, REFUND_ACCOUNT_REQUEST);
    }

    /** T159: the sweep for links that expired without being used or revoked. */
    @Transactional
    public int scrubExpiredRefundAccountLinks(java.time.Instant now) {
        return handoffs.scrubExpiredActionLinks(REFUND_ACCOUNT_REQUEST, now);
    }

    private static String reasonLabel(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason reason) {
        if (reason == null) return "hoàn tiền";
        return switch (reason) {
            case MLAND_CANCELLATION -> "MLAND hủy buổi workshop";
            case LATE_PAYMENT_NOT_RECONFIRMED -> "thanh toán đến sau thời hạn giữ chỗ và không thể xác nhận chỗ";
            case OVERCHARGE_AFTER_FINALIZED -> "điều chỉnh khoản thu sau khi chốt hóa đơn";
            case EXCESS_OR_DUPLICATE_MONEY -> "thanh toán thừa hoặc trùng";
        };
    }

    /** @return the notification reference, or null when no notification was created. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyVerified(String bookingReference, String kind, long amountVnd, String settlementReference,
                                 String correlationReference) {
        String recipient;
        try {
            recipient = booking.notificationRecipient(bookingReference);
        } catch (RuntimeException failure) {
            log.warn("Settlement notification skipped, recipient lookup failed: settlementReference={} exceptionType={}",
                    settlementReference, failure.getClass().getSimpleName());
            return null;
        }
        if (recipient == null || recipient.isBlank()) {
            log.info("Settlement notification skipped, booking has no contact email: settlementReference={}",
                    settlementReference);
            return null;
        }
        return handoffs.create(settlementReference, kind, correlationReference, recipient, render(kind, amountVnd, bookingReference));
    }

    private static String render(String kind, long amountVnd, String bookingReference) {
        String amount = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amountVnd) + " VND";
        if (REFUND_ACCOUNT_REMINDER.equals(kind)) {
            return "Mland cần số tài khoản để hoàn " + amount + " cho đơn " + bookingReference
                    + ". Vui lòng mở lại liên kết trong email trước đó để điền.";
        }
        return REFUND_VERIFIED.equals(kind)
                ? "Mland đã hoàn " + amount + " cho đơn " + bookingReference + ". Số tiền sẽ về phương thức thanh toán ban đầu theo thời gian của ngân hàng."
                : "Mland đã nhận thanh toán " + amount + " cho đơn " + bookingReference + ". Cảm ơn bạn.";
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyMlandRemedyOffer(String ticketCode, String cancelledSession, String replacementSession,
                                         java.time.Instant deadline, String correlationReference) {
        String recipient;
        try {
            recipient = booking.notificationRecipient(ticketCode);
        } catch (RuntimeException failure) {
            log.warn("Mland remedy offer email skipped, recipient lookup failed: ticketCode={} ({})",
                    ticketCode, failure.getClass().getSimpleName());
            return null;
        }
        if (recipient == null || recipient.isBlank()) {
            return null;
        }
        String subject = "MLAND – Buổi workshop bị hủy, vui lòng chọn ca thay thế hoặc hoàn tiền";
        // Template 9 (T112): every valid equivalent session is listed and the buyer chooses ONE of them.
        String body = "Rất tiếc, buổi workshop của vé " + ticketCode + " bị hủy. Chúng tôi đề xuất các ca thay thế "
                + "(chọn một ca, ca phải bắt đầu ít nhất 24 giờ sau khi bạn trả lời):\n"
                + replacementSession + "\n"
                + "Vui lòng trả lời trước " + deadline + " để nhận ca thay thế hoặc chọn hoàn tiền.\n"
                + "Nếu không trả lời trước hạn hoặc không còn ca nào chọn được, MLAND sẽ hoàn 100% số tiền đã thanh toán "
                + "cho vé này. Điểm thưởng đã dùng và lượt voucher sẽ được trả lại.";
        return handoffs.create(ticketCode, "MLAND_REMEDY_OFFER", correlationReference, recipient, body, subject,
                null, null);
    }

    /** Template 9 without a replacement (T112): no equivalent session exists, so the 100% bank refund opens at once. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyMlandDirectRefund(String ticketCode, String cancelledSession, String correlationReference) {
        String recipient;
        try {
            recipient = booking.notificationRecipient(ticketCode);
        } catch (RuntimeException failure) {
            log.warn("Mland direct refund email skipped, recipient lookup failed: ticketCode={} ({})",
                    ticketCode, failure.getClass().getSimpleName());
            return null;
        }
        if (recipient == null || recipient.isBlank()) {
            return null;
        }
        String subject = "MLAND – Buổi workshop bị hủy, MLAND hoàn tiền vé " + ticketCode;
        String body = "Rất tiếc, buổi workshop của vé " + ticketCode + " bị hủy và hiện chưa có ca thay thế phù hợp. "
                + "MLAND sẽ hoàn 100% số tiền đã thanh toán cho vé này bằng chuyển khoản ngân hàng. "
                + "Điểm thưởng đã dùng và lượt voucher sẽ được trả lại.";
        return handoffs.create(ticketCode, "MLAND_DIRECT_REFUND", correlationReference, recipient, body, subject,
                null, null);
    }

    /** T112, FR-043: tells the ticket's recipient (if any) the session was cancelled; only the buyer chooses. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyMlandRemedyRecipientOf(String ticketCode, String correlationReference) {
        String recipient;
        try {
            recipient = booking.ticketRecipientEmail(ticketCode);
        } catch (RuntimeException failure) {
            log.warn("Mland remedy recipient email skipped, recipient lookup failed: ticketCode={} ({})",
                    ticketCode, failure.getClass().getSimpleName());
            return null;
        }
        return notifyMlandRemedyRecipient(ticketCode, recipient, correlationReference);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyMlandRemedyRecipient(String ticketCode, String recipientEmail, String correlationReference) {
        if (recipientEmail == null || recipientEmail.isBlank()) return null;
        String subject = "MLAND – Thông báo buổi workshop bị hủy";
        String body = "Buổi workshop của vé " + ticketCode + " đã bị hủy. Người mua vé sẽ chọn ca thay thế hoặc nhận hoàn tiền.";
        return handoffs.create(ticketCode, "MLAND_REMEDY_RECIPIENT", correlationReference, recipientEmail.trim(),
                body, subject, null, null);
    }

    static final String TICKET_CHANGED = "TICKET_CHANGED";
    static final String TICKET_CHANGE_REJECTED = "TICKET_CHANGE_REJECTED";
    static final String TICKET_CHANGE_EXPIRED = "TICKET_CHANGE_EXPIRED";
    static final String RECIPIENT_SUFFIX = "_RECIPIENT";
    /** Public lookup page (no token in it), so the link needs no expiry. */
    static final String TICKET_LOOKUP_PATH = "/workshop-booking/lookup";

    /**
     * Template 10 (approved 2026-11-12, owner decision 2026-11-18): "Đã đổi lịch" to the buyer's contact email and to the
     * ticket's own recipient, if any. The ticket code and QR do not change (003.2 FR-010z), so no ticket email is sent.
     * One handoff per email, keyed by the change reference. Never throws: a failed lookup skips that email.
     *
     * @return the buyer notification reference, or null when the order has no contact email
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyTicketChanged(String ticketCode, String changeReference, String oldSchedule,
                                      String newSchedule, String locationName, long priceIncreaseVnd,
                                      String correlationReference) {
        String where = locationName == null || locationName.isBlank() ? "" : " tại " + locationName.trim();
        String head = "Vé " + ticketCode + " đã chuyển từ " + oldSchedule + " sang " + newSchedule + where + ".";
        String tail = " Đây là lần đổi duy nhất của vé này. Mã vé và mã QR không đổi, bạn tiếp tục dùng QR đã có để check-in."
                + " Bạn có thể xem lại vé tại liên kết bên dưới.";
        String increase = priceIncreaseVnd > 0
                ? " Do lịch mới nằm ngoài phạm vi voucher, số tiền cần thanh toán thêm là " + vnd(priceIncreaseVnd)
                + ", sẽ thu khi checkout."
                : "";
        String subject = "MLAND – Đã đổi lịch vé " + ticketCode;
        return sendTicketChangeNotices(ticketCode, changeReference, TICKET_CHANGED, subject,
                head + increase + tail, head + tail, correlationReference);
    }

    /** A queued change was refused: the ticket keeps its current schedule and QR (owner decision 2026-11-18). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyTicketChangeRejected(String ticketCode, String requestReference, String currentSchedule,
                                             String requestedSchedule, String locationName,
                                             String correlationReference) {
        return sendUnchangedNotice(ticketCode, requestReference, TICKET_CHANGE_REJECTED, "không được chấp nhận",
                currentSchedule, requestedSchedule, locationName, correlationReference);
    }

    /** A queued change ran out of time without a decision: the ticket keeps its current schedule and QR. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String notifyTicketChangeExpired(String ticketCode, String requestReference, String currentSchedule,
                                            String requestedSchedule, String locationName,
                                            String correlationReference) {
        return sendUnchangedNotice(ticketCode, requestReference, TICKET_CHANGE_EXPIRED, "đã hết hạn xử lý",
                currentSchedule, requestedSchedule, locationName, correlationReference);
    }

    private String sendUnchangedNotice(String ticketCode, String requestReference, String kind, String outcome,
                                       String currentSchedule, String requestedSchedule, String locationName,
                                       String correlationReference) {
        String where = locationName == null || locationName.isBlank() ? "" : " tại " + locationName.trim();
        String body = "Yêu cầu đổi lịch vé " + ticketCode + " sang " + requestedSchedule + where + " " + outcome + ". "
                + "Vé giữ nguyên lịch cũ: " + currentSchedule + ". Mã QR hiện tại vẫn dùng được. "
                + "Nếu cần hỗ trợ, vui lòng liên hệ quầy MLAND hoặc xem lại vé tại liên kết bên dưới.";
        String subject = "MLAND – Yêu cầu đổi lịch vé " + ticketCode + " " + outcome;
        return sendTicketChangeNotices(ticketCode, requestReference, kind, subject, body, body, correlationReference);
    }

    /** Buyer first, then the ticket's own recipient (null when none or the same address as the buyer). */
    private String sendTicketChangeNotices(String ticketCode, String reference, String kind, String subject,
                                           String buyerBody, String recipientBody, String correlationReference) {
        String buyerRef = null;
        String buyer = null;
        try {
            buyer = booking.notificationRecipient(ticketCode);
        } catch (RuntimeException failure) {
            log.warn("Ticket change email skipped for the buyer, recipient lookup failed: ticketCode={} kind={} ({})",
                    ticketCode, kind, failure.getClass().getSimpleName());
        }
        if (buyer != null && !buyer.isBlank()) {
            buyerRef = handoffs.create(reference, kind, correlationReference, buyer.trim(), buyerBody, subject,
                    TICKET_LOOKUP_PATH, null);
        } else {
            log.info("Ticket change email skipped, no buyer contact email: ticketCode={} kind={}", ticketCode, kind);
        }
        String recipient = null;
        try {
            recipient = booking.ticketRecipientEmail(ticketCode);
        } catch (RuntimeException failure) {
            log.warn("Ticket change email skipped for the ticket recipient, lookup failed: ticketCode={} kind={} ({})",
                    ticketCode, kind, failure.getClass().getSimpleName());
        }
        if (recipient != null && !recipient.isBlank()
                && (buyer == null || !recipient.trim().equalsIgnoreCase(buyer.trim()))) {
            handoffs.create(reference, kind + RECIPIENT_SUFFIX, correlationReference, recipient.trim(), recipientBody,
                    subject, TICKET_LOOKUP_PATH, null);
        }
        return buyerRef;
    }

    private static String vnd(long amountVnd) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amountVnd) + " VND";
    }
}
