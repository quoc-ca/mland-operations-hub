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
                + "Vui lòng nhập tài khoản của chính người đã thanh toán và ghi đúng tên chủ tài khoản như ngân hàng hiển thị. MLAND đối chiếu tên trước khi chuyển; nếu không khớp, chúng tôi sẽ dừng lại và liên hệ với bạn.\n\n"
                + "Bạn cũng có thể cung cấp tại quầy MLAND.";
        return handoffs.create(request.getRequestReference(), REFUND_ACCOUNT_REQUEST, correlationReference, recipient,
                body, subject, REFUND_ACCOUNT_PATH + rawLinkToken, linkExpiresAt);
    }

    private static String reasonLabel(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason reason) {
        if (reason == null) return "hoàn tiền";
        return switch (reason) {
            case MLAND_CANCELLATION -> "MLAND hủy buổi workshop";
            case LATE_PAYMENT_NOT_RECONFIRMED -> "thanh toán đến sau thời hạn giữ chỗ và không thể xác nhận chỗ";
            case OVERCHARGE_AFTER_FINALIZED -> "điều chỉnh khoản thu sau khi chốt hóa đơn";
            case EXCESS_OR_DUPLICATE_MONEY -> "thanh toán thừa hoặc trùng";
            case OTA_ADDON_OVERCHARGE -> "điều chỉnh khoản dịch vụ thêm";
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
}
