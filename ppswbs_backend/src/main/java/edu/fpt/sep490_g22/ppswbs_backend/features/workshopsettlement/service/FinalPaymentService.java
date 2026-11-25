package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** Applies only the redacted, already verified payment evidence delivered by paymentintegration. */
@Service
public class FinalPaymentService {

    /** Signed VNPay evidence that settled a ticket-folio VNPAY collection; counted through the collection only. */
    public static final String FOLIO_VNPAY_VERIFIED = "FOLIO_VNPAY_VERIFIED";
    /**
     * Signed VNPay money that settled no pending folio part (T110/T121, FR-034): collected and owed back through an
     * {@code EXCESS_OR_DUPLICATE_MONEY} bank refund; counted by the refund cap, never by a balance.
     */
    public static final String FOLIO_VNPAY_EXCESS = "FOLIO_VNPAY_EXCESS";
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementFinalBillRepository bills;
    private final SettlementCustomerNotifier notifier;
    private final SettlementFolioCollectionRepository collections;
    private final FolioCollectionService folioCollections;

    @Autowired
    public FinalPaymentService(SettlementEvidenceConclusionRepository evidence,
            SettlementFinalBillRepository bills,
            SettlementCustomerNotifier notifier,
            SettlementFolioCollectionRepository collections,
            FolioCollectionService folioCollections) {
        this.evidence = evidence;
        this.bills = bills;
        this.notifier = notifier;
        this.collections = collections;
        this.folioCollections = folioCollections;
    }

    @Transactional
    public SettlementPaymentEvidenceOutcome apply(ApplyVerifiedSettlementPaymentEvidenceCommand command) {
        var byOperation = evidence.findByOperationReference(command.operationReference().trim());
        if (byOperation.isPresent()) return replay(byOperation.get(), command);
        var byEvidence = evidence.findByEvidenceReference(command.verifiedEvidenceReference().trim());
        if (byEvidence.isPresent()) return replay(byEvidence.get(), command);

        SettlementFinalBill bill = bills.findByBookingReferenceForUpdate(command.bookingReference().trim()).orElse(null);
        if (bill == null) return SettlementPaymentEvidenceOutcome.rejected("FINAL_BILL_NOT_FOUND");
        if (!"VND".equals(command.currencyCode()) || !bill.getCurrencyCode().equals(command.currencyCode())) {
            return SettlementPaymentEvidenceOutcome.rejected("CURRENCY_MISMATCH");
        }
        if (command.amountVnd() == null || command.amountVnd() <= 0) {
            return SettlementPaymentEvidenceOutcome.rejected("INVALID_AMOUNT");
        }

        String conclusion;
        String unappliedCause = null;
        if ("FINAL_PAYMENT".equals(command.paymentKind())) {
            // FR-049: a ticket folio's checkout payment is a VNPAY folio collection, confirmed only from signed
            // evidence. T121: the legacy booking-wide final-payment branch is gone, so FINAL_PAYMENT money settles
            // only a pending VNPAY part of an OPEN folio; anything else is owed back in full (T110, FR-034).
            String merchant = command.merchantReference().trim();
            SettlementFolioCollection folioCollection = collections
                    .findByMethodAndExternalReference(FolioCollectionMethod.VNPAY, merchant).orElse(null);
            if (folioCollection == null || !bill.getBookingReference().equals(folioCollection.getBookingReference())) {
                unappliedCause = "NO_MATCHING_VNPAY_COLLECTION";
            } else if (!"OPEN".equals(bill.getLifecycleStatus())) {
                unappliedCause = "FOLIO_NOT_OPEN";
            } else if (folioCollection.getStatus() == FolioCollectionStatus.CONFIRMED
                    && alreadyConfirmedBy(bill.getBookingReference(), merchant, command.providerTransactionNumber())) {
                return SettlementPaymentEvidenceOutcome.accepted(); // the same VNPay payment under another reference
            } else if (!folioCollection.isPending()) {
                unappliedCause = "COLLECTION_" + folioCollection.getStatus().name();
            }
            if (unappliedCause == null) {
                try {
                    folioCollections.confirmGatewayCollection(merchant, command.amountVnd(),
                            command.correlationReference().trim());
                } catch (WorkshopSettlementValidationException | WorkshopSettlementConflictException ex) {
                    if ("COLLECTION_AMOUNT_MISMATCH".equals(ex.getMessage())) {
                        return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
                    }
                    return SettlementPaymentEvidenceOutcome.rejected(ex.getMessage());
                }
                // FR-008 per ticket (BE-DESIGN-0006, decided 2026-11-14): no conclusion here. If this collection
                // clears the balance, confirmGatewayCollection finalizes the folio and FolioFinalizationService
                // sends 003.1 recordTicketFinalBalanceResolved in the same transaction.
                // FR-034/FR-049: this money is counted once, through the CONFIRMED collection. The evidence row keeps
                // its own conclusion so balance and refund sums that read FINAL_PAYMENT_VERIFIED never add it again.
                conclusion = FOLIO_VNPAY_VERIFIED;
            } else {
                // T110: kept as collected money of this folio (the refund cap counts it) but never reduces a balance.
                conclusion = FOLIO_VNPAY_EXCESS;
            }
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
                // T186 (FR-060): the EXCESS row keeps the branch of the moment the evidence is recorded.
                .branchId(FOLIO_VNPAY_EXCESS.equals(conclusion)
                        ? folioCollections.branchOfTicket(command.bookingReference().trim()) : null)
                .currencyCode(command.currencyCode())
                .operationReference(command.operationReference().trim())
                .createdAt(Instant.now()).build());
        if (unappliedCause != null) {
            folioCollections.routeUnappliedGatewayMoney(bill, command.merchantReference().trim(),
                    command.verifiedEvidenceReference().trim(), command.amountVnd(), unappliedCause,
                    command.correlationReference().trim());
        } else if ("FINAL_PAYMENT".equals(command.paymentKind())) {
            // Customer notice for a verified checkout payment; refunds are announced when their case resolves.
            notifier.notifyVerified(command.bookingReference().trim(), SettlementCustomerNotifier.FINAL_PAYMENT_VERIFIED,
                    command.amountVnd(), bill.getBillReference(), command.correlationReference().trim());
        }
        return SettlementPaymentEvidenceOutcome.accepted();
    }

    /** A confirmed part already has evidence of this very VNPay transaction, delivered under another reference. */
    private boolean alreadyConfirmedBy(String bookingReference, String merchantReference,
                                       String providerTransactionNumber) {
        if (providerTransactionNumber == null || providerTransactionNumber.isBlank()) {
            return false;
        }
        return evidence.findByBookingReferenceAndConclusion(bookingReference, FOLIO_VNPAY_VERIFIED).stream()
                .anyMatch(row -> merchantReference.equals(row.getProviderTransactionReference())
                        && providerTransactionNumber.equals(row.getProviderTransactionNumber()));
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
                && (Objects.equals(existing.getConclusion(), command.paymentKind() + "_VERIFIED")
                    || ("FINAL_PAYMENT".equals(command.paymentKind())
                        && (FOLIO_VNPAY_VERIFIED.equals(existing.getConclusion())
                            || FOLIO_VNPAY_EXCESS.equals(existing.getConclusion()))));
        return same ? SettlementPaymentEvidenceOutcome.accepted()
                : SettlementPaymentEvidenceOutcome.rejected("IDEMPOTENCY_CONFLICT");
    }
}
