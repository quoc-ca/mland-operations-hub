package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
