package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** Applies only the redacted, already verified payment evidence delivered by paymentintegration. */
@Service
public class FinalPaymentService {
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final SettlementFinalPaymentAttemptRepository attempts;
    private final WorkshopBookingSettlementAdapter booking;
    private final SettlementAddonBillRepository addonBills;
    private final SettlementAddonPaymentAttemptRepository addonAttempts;
    private final SettlementCustomerNotifier notifier;

    @Autowired
    public FinalPaymentService(SettlementEvidenceConclusionRepository evidence,
            SettlementFinalBillRepository bills,
            SettlementChargeRepository charges,
            SettlementFinalPaymentAttemptRepository attempts,
            WorkshopBookingSettlementAdapter booking,
            SettlementAddonBillRepository addonBills,
            SettlementAddonPaymentAttemptRepository addonAttempts,
            SettlementCustomerNotifier notifier) {
        this.evidence = evidence;
        this.bills = bills;
        this.charges = charges;
        this.attempts = attempts;
        this.booking = booking;
        this.addonBills = addonBills;
        this.addonAttempts = addonAttempts;
        this.notifier = notifier;
    }

    @Transactional
    public SettlementPaymentEvidenceOutcome apply(ApplyVerifiedSettlementPaymentEvidenceCommand command) {
        var byOperation = evidence.findByOperationReference(command.operationReference().trim());
        if (byOperation.isPresent()) return replay(byOperation.get(), command);
        var byEvidence = evidence.findByEvidenceReference(command.verifiedEvidenceReference().trim());
        if (byEvidence.isPresent()) return replay(byEvidence.get(), command);

        if ("SHOP_ADDON".equals(command.paymentKind())) {
            return applyShopAddon(command);
        }

        SettlementFinalBill bill = bills.findByBookingReferenceForUpdate(command.bookingReference().trim()).orElse(null);
        if (bill == null) return SettlementPaymentEvidenceOutcome.rejected("FINAL_BILL_NOT_FOUND");
        if (!"VND".equals(command.currencyCode()) || !bill.getCurrencyCode().equals(command.currencyCode())) {
            return SettlementPaymentEvidenceOutcome.rejected("CURRENCY_MISMATCH");
        }
        if (command.amountVnd() == null || command.amountVnd() <= 0) {
            return SettlementPaymentEvidenceOutcome.rejected("INVALID_AMOUNT");
        }

        String conclusion;
        if ("FINAL_PAYMENT".equals(command.paymentKind())) {
            if (!"OPEN".equals(bill.getLifecycleStatus())) return SettlementPaymentEvidenceOutcome.rejected("BILL_NOT_OPEN");
            long confirmedCharges = charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(
                            bill.getBillReference(), "CONFIRMED").stream()
                    .mapToLong(charge -> charge.getAmountVnd() == null ? 0L : charge.getAmountVnd()).sum();
            long previousPayments = total(command.bookingReference(), "FINAL_PAYMENT_VERIFIED");
            long outstanding;
            try {
                outstanding = Math.subtractExact(Math.addExact(bill.getRemainingAmountVnd(), confirmedCharges),
                        previousPayments);
            } catch (ArithmeticException overflow) {
                return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
            }
            if (outstanding <= 0 || command.amountVnd() > outstanding) {
                return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
            }

            if (command.amountVnd() == outstanding) {
                FinalBalanceResolutionOutcome resolution = booking.recordFinalBalanceResolved(
                        command.bookingReference().trim(), bill.getBillReference(),
                        command.operationReference().trim(), command.correlationReference().trim());
                if (resolution == null || resolution.status() != BookingOutcomeStatus.ACCEPTED) {
                    return SettlementPaymentEvidenceOutcome.rejected(resolution == null || resolution.reason() == null
                            ? "BOOKING_FINAL_BALANCE_REJECTED" : resolution.reason().name());
                }
            }
            conclusion = "FINAL_PAYMENT_VERIFIED";
        } else {
            long collected;
            try {
                collected = Math.subtractExact(Math.addExact(bill.getAcceptedDepositAmountVnd(),
                                total(command.bookingReference(), "FINAL_PAYMENT_VERIFIED")),
                        total(command.bookingReference(), "REFUND_VERIFIED"));
            } catch (ArithmeticException overflow) {
                return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
            }
            if (collected <= 0 || command.amountVnd() > collected) {
                return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
            }
            conclusion = "REFUND_VERIFIED";
        }

        evidence.saveAndFlush(SettlementEvidenceConclusion.builder()
                .evidenceReference(command.verifiedEvidenceReference().trim())
                .bookingReference(command.bookingReference().trim())
                .providerTransactionReference(command.merchantReference().trim())
                .providerTransactionNumber(command.providerTransactionNumber())
                .originalProviderCreateDate(command.originalProviderCreateDate())
                .conclusion(conclusion)
                .amountVnd(command.amountVnd())
                .currencyCode(command.currencyCode())
                .operationReference(command.operationReference().trim())
                .createdAt(Instant.now()).build());
        if ("FINAL_PAYMENT".equals(command.paymentKind())) {
            if (attempts != null) attempts.findByMerchantReference(command.merchantReference()).ifPresent(attempt -> {
                attempt.setLifecycleStatus("VERIFIED_PAID");
                attempt.setPaymentUrlSnapshot(null);
            });
            // Customer notice for a verified direct-booking payment; refunds are announced when their case resolves.
            notifier.notifyVerified(command.bookingReference().trim(), SettlementCustomerNotifier.FINAL_PAYMENT_VERIFIED,
                    command.amountVnd(), bill.getBillReference(), command.correlationReference().trim());
        }
        return SettlementPaymentEvidenceOutcome.accepted();
    }

    private SettlementPaymentEvidenceOutcome applyShopAddon(ApplyVerifiedSettlementPaymentEvidenceCommand command) {
        SettlementAddonPaymentAttempt attempt = addonAttempts.findByMerchantReference(command.merchantReference().trim()).orElse(null);
        if (attempt == null) {
            return SettlementPaymentEvidenceOutcome.rejected("ATTEMPT_NOT_FOUND");
        }
        SettlementAddonBill bill = addonBills.findByBillReferenceForUpdate(attempt.getBillReference()).orElse(null);
        if (bill == null) {
            return SettlementPaymentEvidenceOutcome.rejected("ADDON_BILL_NOT_FOUND");
        }
        if (!"VND".equals(command.currencyCode()) || !bill.getCurrencyCode().equals(command.currencyCode())) {
            return SettlementPaymentEvidenceOutcome.rejected("CURRENCY_MISMATCH");
        }
        // The provider must have collected exactly what this attempt asked for.
        if (!Objects.equals(attempt.getAmountVnd(), command.amountVnd())) {
            return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
        }
        if ("VERIFIED_PAID".equals(attempt.getLifecycleStatus())) {
            return SettlementPaymentEvidenceOutcome.accepted(); // the same verified payment delivered again
        }
        boolean alreadyPaid = "PAID".equals(bill.getLifecycleStatus());
        if (!alreadyPaid && !"OPEN".equals(bill.getLifecycleStatus())) {
            return SettlementPaymentEvidenceOutcome.rejected("BILL_NOT_OPEN");
        }

        // A verified payment for a bill that another attempt already settled is real money collected twice.
        // It is recorded like any other SHOP_ADDON payment so a Manager can refund it; the bill stays PAID.
        if (!alreadyPaid) {
            bill.setLifecycleStatus("PAID");
            addonBills.saveAndFlush(bill);
        }

        attempt.setLifecycleStatus("VERIFIED_PAID");
        attempt.setPaymentUrlSnapshot(null);
        addonAttempts.saveAndFlush(attempt);

        evidence.saveAndFlush(SettlementEvidenceConclusion.builder()
                .evidenceReference(command.verifiedEvidenceReference().trim())
                .bookingReference(bill.getChannelBookingReference().trim())
                .providerTransactionReference(command.merchantReference().trim())
                .providerTransactionNumber(command.providerTransactionNumber())
                .originalProviderCreateDate(command.originalProviderCreateDate())
                .conclusion("SHOP_ADDON_VERIFIED")
                .amountVnd(command.amountVnd())
                .currencyCode(command.currencyCode())
                .operationReference(command.operationReference().trim())
                .createdAt(Instant.now()).build());

        return SettlementPaymentEvidenceOutcome.accepted();
    }

    private long total(String bookingReference, String conclusion) {
        return evidence.findByBookingReferenceAndConclusion(bookingReference, conclusion).stream()
                .mapToLong(item -> item.getAmountVnd() == null ? 0L : item.getAmountVnd()).sum();
    }

    private SettlementPaymentEvidenceOutcome replay(SettlementEvidenceConclusion existing,
                                                    ApplyVerifiedSettlementPaymentEvidenceCommand command) {
        boolean same = Objects.equals(existing.getBookingReference(), command.bookingReference())
                && Objects.equals(existing.getEvidenceReference(), command.verifiedEvidenceReference())
                && Objects.equals(existing.getProviderTransactionReference(), command.merchantReference())
                && (existing.getProviderTransactionNumber() == null
                    || Objects.equals(existing.getProviderTransactionNumber(), command.providerTransactionNumber()))
                && (existing.getOriginalProviderCreateDate() == null
                    || Objects.equals(existing.getOriginalProviderCreateDate(), command.originalProviderCreateDate()))
                && Objects.equals(existing.getAmountVnd(), command.amountVnd())
                && Objects.equals(existing.getCurrencyCode(), command.currencyCode())
                && Objects.equals(existing.getConclusion(), command.paymentKind() + "_VERIFIED");
        return same ? SettlementPaymentEvidenceOutcome.accepted()
                : SettlementPaymentEvidenceOutcome.rejected("IDEMPOTENCY_CONFLICT");
    }
}
