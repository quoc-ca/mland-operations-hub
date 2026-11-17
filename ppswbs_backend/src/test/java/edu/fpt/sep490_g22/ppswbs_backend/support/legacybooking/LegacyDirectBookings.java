package edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the rows of a single direct booking for tests of code that still reads them (003.3 booking-wide settlement
 * and the Loyalty single-booking source, kept until the open 003.3 decision of 2026-11-13). The single-booking
 * creation and deposit services were removed; this writes the same rows they wrote: booking, frozen invoice
 * ({@code deposit = ceil(post/2)}), session commitments, and on a verified deposit the payment attempt, the VNPay
 * receipt, CONFIRMED/DEPOSIT_PAID and {@link MlandBookingConfirmedEvent}, which opens the settlement folio.
 */
@TestComponent
public class LegacyDirectBookings {

    private final WorkshopBookingRepository bookings;
    private final BookingInvoiceRepository invoices;
    private final BookingSessionCommitmentRepository commitments;
    private final BookingPaymentAttemptRepository attempts;
    private final BookingPaymentReceiptRepository receipts;
    private final LegacyBookingLoyaltyOutcomes loyaltyOutcomes;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    public LegacyDirectBookings(WorkshopBookingRepository bookings, BookingInvoiceRepository invoices,
                                BookingSessionCommitmentRepository commitments, BookingPaymentAttemptRepository attempts,
                                BookingPaymentReceiptRepository receipts, LegacyBookingLoyaltyOutcomes loyaltyOutcomes,
                                ApplicationEventPublisher events, TransactionTemplate transaction) {
        this.bookings = bookings;
        this.invoices = invoices;
        this.commitments = commitments;
        this.attempts = attempts;
        this.receipts = receipts;
        this.loyaltyOutcomes = loyaltyOutcomes;
        this.events = events;
        this.transaction = transaction;
    }

    /** A PENDING_PAYMENT booking with its frozen invoice and one commitment per session, in session order. */
    public WorkshopBooking pending(String bookingCode, Long packageId, String packageName, String contactEmail,
                                   String contactPhone, Long memberAccountId, int participants,
                                   List<UUID> sessionReferences, long locationId, long pricePerParticipant,
                                   long postPromotionAmount) {
        return transaction.execute(status -> {
            WorkshopBooking booking = bookings.saveAndFlush(WorkshopBooking.builder().bookingCode(bookingCode)
                    .packageId(packageId).packageNameSnapshot(packageName).contactEmail(contactEmail)
                    .contactPhone(contactPhone).memberAccountId(memberAccountId).participantCount(participants)
                    .bookingStatus(BookingStatus.PENDING_PAYMENT).paymentStatus(PaymentStatus.UNPAID)
                    .holdExpiresAt(Instant.now().plusSeconds(600)).build());
            int ordinal = 1;
            for (UUID session : sessionReferences) {
                commitments.save(BookingSessionCommitment.builder().booking(booking)
                        .sessionReference(session.toString()).locationId(locationId).ordinal(ordinal++)
                        .seatCount(participants).build());
            }
            long deposit = Math.floorDiv(postPromotionAmount, 2) + (postPromotionAmount % 2);
            invoices.save(BookingInvoice.builder().booking(booking).invoiceNumber("INV-" + bookingCode)
                    .pricePerParticipant(pricePerParticipant)
                    .packageTotal(Math.multiplyExact((long) participants, pricePerParticipant))
                    .postPromotionAmount(postPromotionAmount).depositAmount(deposit)
                    .remainingAmount(postPromotionAmount - deposit).currency("VND").build());
            return booking;
        });
    }

    /** A PENDING deposit attempt for the invoice's deposit; returns its merchant reference. */
    public String depositAttempt(String bookingCode, String providerCreateDate) {
        String merchantReference = "DEP-" + bookingCode + "-" + UUID.randomUUID().toString().substring(0, 8);
        transaction.executeWithoutResult(status -> {
            WorkshopBooking booking = bookings.findByBookingCode(bookingCode).orElseThrow();
            BookingInvoice invoice = invoices.findByBookingId(booking.getId()).orElseThrow();
            attempts.saveAndFlush(BookingPaymentAttempt.builder().booking(booking).invoice(invoice)
                    .attemptReference(UUID.randomUUID().toString()).merchantReference(merchantReference)
                    .providerCreateDate(providerCreateDate).attemptAmount(invoice.getDepositAmount())
                    .status(PaymentAttemptStatus.PENDING).paymentUrlSnapshot("https://sandbox.example.test/pay")
                    .expiresAt(Instant.now().plusSeconds(600)).createdAt(Instant.now()).updatedAt(Instant.now())
                    .build());
        });
        return merchantReference;
    }

    /**
     * The verified deposit: attempt SUCCESS, a VNPay receipt {@code VNPAY-<txn>}, booking CONFIRMED/DEPOSIT_PAID,
     * Loyalty settled for a Member quote booking, and {@link MlandBookingConfirmedEvent} in the same transaction.
     * A booking already confirmed is replayed: Loyalty is asked again (idempotent) and nothing else changes.
     * Returns false, changing nothing, when the booking is no longer PENDING_PAYMENT or CONFIRMED.
     */
    public boolean confirmDeposit(String bookingCode, String merchantReference, String providerTransaction) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            WorkshopBooking booking = bookings.findLockedByBookingCode(bookingCode).orElseThrow();
            String evidence = "ipn-" + bookingCode;
            if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
                loyaltyOutcomes.settleVerifiedDeposit(booking, evidence);
                return true;
            }
            if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) return false;
            BookingPaymentAttempt attempt = attempts.findByMerchantReference(merchantReference).orElseThrow();
            attempt.setStatus(PaymentAttemptStatus.SUCCESS);
            attempt.setUpdatedAt(Instant.now());
            receipts.save(BookingPaymentReceipt.builder().booking(booking).paymentAttempt(attempt)
                    .providerEventId("VNPAY-" + providerTransaction).providerName("VNPAY")
                    .amountReceived(attempt.getAttemptAmount()).receivedAt(Instant.now())
                    .safePayloadHash("TEST_HASH").build());
            booking.setBookingStatus(BookingStatus.CONFIRMED);
            booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
            booking.setConfirmedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            bookings.saveAndFlush(booking);
            loyaltyOutcomes.settleVerifiedDeposit(booking, evidence);
            events.publishEvent(new MlandBookingConfirmedEvent(booking.getBookingCode(), booking.getConfirmedAt()));
            return true;
        }));
    }

    /** The hold ran out with no verified payment: EXPIRED and the held Member benefits released. */
    public void expireUnpaid(String bookingCode) {
        transaction.executeWithoutResult(status -> {
            WorkshopBooking booking = bookings.findLockedByBookingCode(bookingCode).orElseThrow();
            booking.setHoldExpiresAt(Instant.now().minusSeconds(60));
            booking.setBookingStatus(BookingStatus.EXPIRED);
            bookings.saveAndFlush(booking);
            loyaltyOutcomes.finishConfirmedUnpaidExpiry(booking, "unpaid-expiry-" + bookingCode);
        });
    }

    public WorkshopBooking reload(String bookingCode) {
        return transaction.execute(status -> bookings.findByBookingCode(bookingCode).orElseThrow());
    }
}
