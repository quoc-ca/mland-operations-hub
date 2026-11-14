package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Records and verifies the collections of one folio (003.3 T094, FR-031..FR-034).
 *
 * <p>Rules this service owns:
 * <ul>
 *   <li>a collection belongs to exactly one folio, which must be OPEN, and the folio row is locked while the
 *       balance is read and assigned, so the same balance is never assigned twice;</li>
 *   <li>only a CONFIRMED collection reduces the balance; a PENDING_VERIFICATION one reserves its part;</li>
 *   <li>cash is confirmed by the Staff who took it, recording what was handed over and returned, and only
 *       the applied amount reaches the folio; a direct transfer is confirmed only by a Manager who has seen
 *       the money in the shop account; VNPay is confirmed only from signed gateway evidence, never by a
 *       person, and never under 5,000 VND;</li>
 *   <li>the same bank transaction or cash receipt cannot be recorded twice;</li>
 *   <li>a pending VNPay part must be reconciled before another method is used for it; closing the QR is not
 *       proof of failure;</li>
 *   <li>money that arrives above the balance is kept in full and flagged as excess owed back, never waived,
 *       hidden, or allowed to finalize twice.</li>
 * </ul>
 *
 * <p>Confirming a collection that clears the balance finalizes the folio, which is how FR-035's
 * "Chờ xác minh thanh toán" resolves itself once the part is proven.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolioCollectionService {

    /** The VNPay floor of a single collection (FR-033). Cash and transfer have no floor of their own. */
    public static final long VNPAY_MINIMUM_VND = 5_000;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SettlementFinalBillRepository bills;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementCashDayCloseRepository cashCloses;
    private final SettlementBusinessAuditRepository audits;
    private final FolioBalanceService balances;
    private final FolioFinalizationService finalization;

    public record CollectionOutcome(String collectionReference, FolioCollectionMethod method, long amountVnd,
                                    long excessVnd, FolioCollectionStatus status, long balanceDue, long pendingVnd,
                                    String folioStatus) {
    }

    // ---- recording ----

    /** FR-031/FR-032: cash taken at the counter by Staff with collection authority, confirmed on the spot. */
    @Transactional
    public CollectionOutcome collectCash(String bookingReference, long amountVnd, long cashReceivedVnd,
                                         String receiptReference, String payerLabel, Long branchId,
                                         Long actorAccountId, String correlationReference) {
        if (cashReceivedVnd < amountVnd) {
            throw new WorkshopSettlementValidationException("CASH_RECEIVED_BELOW_APPLIED_AMOUNT");
        }
        if (branchId == null) {
            throw new WorkshopSettlementValidationException("CASH_NEEDS_A_BRANCH");
        }
        SettlementFinalBill folio = lockOpenFolio(bookingReference);
        requireNoPendingGatewayPart(folio);
        requireFreeAmount(folio, amountVnd, FolioCollectionMethod.CASH);
        requireNotRecordedTwice(FolioCollectionMethod.CASH, receiptReference);

        long excess = excessOver(folio, amountVnd);
        SettlementFolioCollection collection = collections.saveAndFlush(SettlementFolioCollection.builder()
                .collectionReference(reference())
                .billReference(folio.getBillReference())
                .bookingReference(folio.getBookingReference())
                .method(FolioCollectionMethod.CASH)
                .amountVnd(amountVnd)
                .cashReceivedVnd(cashReceivedVnd)
                .cashChangeVnd(cashReceivedVnd - amountVnd)
                .excessVnd(excess)
                .status(FolioCollectionStatus.CONFIRMED)
                .externalReference(blankToNull(receiptReference))
                .payerLabel(blankToNull(payerLabel))
                .branchId(branchId)
                .cashBusinessDay(cashBusinessDay(branchId))
                .recordedByAccountId(actorAccountId)
                .confirmedByAccountId(actorAccountId)
                .confirmedAt(Instant.now())
                .currencyCode("VND")
                .build());
        audit(folio, "FOLIO_CASH_COLLECTED", actorAccountId, collection, correlationReference);
        flagExcess(folio, collection, actorAccountId, correlationReference);
        return settle(folio, collection, correlationReference);
    }

    /**
     * FR-031: the customer transfers to the shop account and leaves the reference. Nothing is proven yet, so
     * the balance does not move and the product is not handed over.
     */
    @Transactional
    public CollectionOutcome recordBankTransfer(String bookingReference, long amountVnd,
                                                String customerTransactionReference, String payerLabel,
                                                Long actorAccountId, String correlationReference) {
        if (customerTransactionReference == null || customerTransactionReference.isBlank()) {
            throw new WorkshopSettlementValidationException("TRANSFER_REFERENCE_REQUIRED");
        }
        SettlementFinalBill folio = lockOpenFolio(bookingReference);
        requireNoPendingGatewayPart(folio);
        requireFreeAmount(folio, amountVnd, FolioCollectionMethod.BANK_TRANSFER);
        requireNotRecordedTwice(FolioCollectionMethod.BANK_TRANSFER, customerTransactionReference);

        SettlementFolioCollection collection = collections.saveAndFlush(SettlementFolioCollection.builder()
                .collectionReference(reference())
                .billReference(folio.getBillReference())
                .bookingReference(folio.getBookingReference())
                .method(FolioCollectionMethod.BANK_TRANSFER)
                .amountVnd(amountVnd)
                .status(FolioCollectionStatus.PENDING_VERIFICATION)
                .externalReference(customerTransactionReference.trim())
                .payerLabel(blankToNull(payerLabel))
                .recordedByAccountId(actorAccountId)
                .currencyCode("VND")
                .build());
        audit(folio, "FOLIO_TRANSFER_RECORDED", actorAccountId, collection, correlationReference);
        return outcome(folio, collection);
    }

    /** Reserves the part a VNPay attempt is about, so no other method may take it meanwhile (FR-034). */
    @Transactional
    public CollectionOutcome recordGatewayAttempt(String bookingReference, long amountVnd, String merchantReference,
                                                  Long actorAccountId, String correlationReference) {
        if (merchantReference == null || merchantReference.isBlank()) {
            throw new WorkshopSettlementValidationException("MERCHANT_REFERENCE_REQUIRED");
        }
        if (amountVnd < VNPAY_MINIMUM_VND) {
            // FR-033: the screen must say so before collecting; the system never raises or waives an amount.
            throw new WorkshopSettlementValidationException("VNPAY_BELOW_MINIMUM");
        }
        SettlementFinalBill folio = lockOpenFolio(bookingReference);
        requireNoPendingGatewayPart(folio);
        requireFreeAmount(folio, amountVnd, FolioCollectionMethod.VNPAY);
        requireNotRecordedTwice(FolioCollectionMethod.VNPAY, merchantReference);

        SettlementFolioCollection collection = collections.saveAndFlush(SettlementFolioCollection.builder()
                .collectionReference(reference())
                .billReference(folio.getBillReference())
                .bookingReference(folio.getBookingReference())
                .method(FolioCollectionMethod.VNPAY)
                .amountVnd(amountVnd)
                .status(FolioCollectionStatus.PENDING_VERIFICATION)
                .externalReference(merchantReference.trim())
                .recordedByAccountId(actorAccountId)
                .currencyCode("VND")
                .build());
        audit(folio, "FOLIO_GATEWAY_ATTEMPT_RECORDED", actorAccountId, collection, correlationReference);
        return outcome(folio, collection);
    }

    // ---- verifying ----

    /**
     * FR-032: a direct transfer becomes real money only when an actor who can see the shop account says so.
     * The caller has already checked that authority; remote confirmation is allowed.
     */
    @Transactional
    public CollectionOutcome confirmBankTransfer(String collectionReference, Long confirmingAccountId,
                                                 String correlationReference) {
        SettlementFolioCollection collection = requirePending(collectionReference,
                FolioCollectionMethod.BANK_TRANSFER);
        return confirmCollection(collection, confirmingAccountId, "FOLIO_TRANSFER_CONFIRMED", correlationReference);
    }

    /** FR-032: VNPay is confirmed from signed gateway evidence only, so no account id is recorded. */
    @Transactional
    public CollectionOutcome confirmGatewayCollection(String merchantReference, long verifiedAmountVnd,
                                                      String correlationReference) {
        SettlementFolioCollection collection = collections
                .findByMethodAndExternalReference(FolioCollectionMethod.VNPAY, merchantReference)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException());
        if (collection.getStatus() == FolioCollectionStatus.CONFIRMED) {
            return outcome(lockFolio(collection.getBookingReference()), collection);
        }
        if (!collection.isPending()) {
            throw new WorkshopSettlementConflictException("COLLECTION_NOT_PENDING");
        }
        if (collection.getAmountVnd() != verifiedAmountVnd) {
            throw new WorkshopSettlementConflictException("COLLECTION_AMOUNT_MISMATCH");
        }
        return confirmCollection(collection, null, "FOLIO_GATEWAY_COLLECTION_CONFIRMED", correlationReference);
    }

    /**
     * Reconciles a pending part as not paid, which is what frees the balance for another method. Closing the
     * QR or the screen is not proof of failure, so only a reconciled outcome reaches here.
     */
    @Transactional
    public CollectionOutcome markPendingFailed(String collectionReference, Long actorAccountId,
                                               String correlationReference) {
        SettlementFolioCollection collection = collections.findByCollectionReference(collectionReference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!collection.isPending()) {
            throw new WorkshopSettlementConflictException("COLLECTION_NOT_PENDING");
        }
        collection.setStatus(FolioCollectionStatus.FAILED);
        collections.saveAndFlush(collection);
        SettlementFinalBill folio = lockFolio(collection.getBookingReference());
        audit(folio, "FOLIO_COLLECTION_FAILED", actorAccountId, collection, correlationReference);
        return outcome(folio, collection);
    }

    @Transactional(readOnly = true)
    public List<SettlementFolioCollection> listCollections(String bookingReference) {
        return collections.findByBillReferenceOrderByIdAsc(lockFolio(bookingReference).getBillReference());
    }

    // ---- internals ----

    private CollectionOutcome confirmCollection(SettlementFolioCollection collection, Long confirmingAccountId,
                                                String action, String correlationReference) {
        SettlementFinalBill folio = lockOpenFolio(collection.getBookingReference());
        long excess = excessOver(folio, collection.getAmountVnd());
        collection.setStatus(FolioCollectionStatus.CONFIRMED);
        collection.setConfirmedByAccountId(confirmingAccountId);
        collection.setConfirmedAt(Instant.now());
        collection.setExcessVnd(excess);
        collections.saveAndFlush(collection);
        audit(folio, action, confirmingAccountId, collection, correlationReference);
        flagExcess(folio, collection, confirmingAccountId, correlationReference);
        return settle(folio, collection, correlationReference);
    }

    /** Finalizes the folio when this confirmation cleared the balance and nothing is left unproven. */
    private CollectionOutcome settle(SettlementFinalBill folio, SettlementFolioCollection collection,
                                     String correlationReference) {
        SettlementFinalBill current = finalization.finalizeIfSettled(folio, correlationReference);
        return outcome(current, collection);
    }

    /** Locks the folio of one ticket or booking; a collection may only be added while it is OPEN. */
    private SettlementFinalBill lockOpenFolio(String bookingReference) {
        SettlementFinalBill folio = lockFolio(bookingReference);
        if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FOLIO_NOT_OPEN");
        }
        return folio;
    }

    private SettlementFinalBill lockFolio(String bookingReference) {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new WorkshopSettlementValidationException("BOOKING_REFERENCE_REQUIRED");
        }
        return bills.findByBookingReferenceForUpdate(bookingReference.trim())
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    /** FR-034: confirmed and pending money both count, so one balance is never assigned twice. */
    private void requireFreeAmount(SettlementFinalBill folio, long amountVnd, FolioCollectionMethod method) {
        if (amountVnd <= 0) {
            throw new WorkshopSettlementValidationException("COLLECTION_AMOUNT_INVALID");
        }
        if (method == FolioCollectionMethod.VNPAY && amountVnd < VNPAY_MINIMUM_VND) {
            throw new WorkshopSettlementValidationException("VNPAY_BELOW_MINIMUM");
        }
        FolioBalanceService.FolioBalance balance = balances.balanceOf(folio);
        if (balance.balanceDue() == 0) {
            throw new WorkshopSettlementConflictException("FOLIO_HAS_NO_BALANCE");
        }
        if (amountVnd > balance.unassignedVnd() && method != FolioCollectionMethod.CASH) {
            // Cash is already in the drawer, so it is recorded in full and the excess is owed back instead.
            throw new WorkshopSettlementConflictException("COLLECTION_EXCEEDS_UNASSIGNED_BALANCE");
        }
    }

    /**
     * FR-034: a pending gateway part must be reconciled before the balance is taken another way; a customer
     * closing the QR proves nothing.
     */
    private void requireNoPendingGatewayPart(SettlementFinalBill folio) {
        boolean pendingGateway = collections
                .findByBillReferenceAndStatusOrderByIdAsc(folio.getBillReference(),
                        FolioCollectionStatus.PENDING_VERIFICATION)
                .stream()
                .anyMatch(item -> item.getMethod() == FolioCollectionMethod.VNPAY);
        if (pendingGateway) {
            throw new WorkshopSettlementConflictException("PENDING_GATEWAY_PART_MUST_BE_RECONCILED");
        }
    }

    private void requireNotRecordedTwice(FolioCollectionMethod method, String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return;
        }
        collections.findByMethodAndExternalReference(method, externalReference.trim()).ifPresent(existing -> {
            throw new WorkshopSettlementConflictException("COLLECTION_ALREADY_RECORDED");
        });
    }

    private long excessOver(SettlementFinalBill folio, long amountVnd) {
        long balanceDue = balances.balanceOf(folio).balanceDue();
        return Math.max(0L, amountVnd - balanceDue);
    }

    private void flagExcess(SettlementFinalBill folio, SettlementFolioCollection collection, Long actorAccountId,
                            String correlationReference) {
        if (collection.getExcessVnd() <= 0) {
            return;
        }
        // Kept in full on the folio and owed back; 003.3 T097/T098 raise the Manager refund from this flag.
        log.info("Collection {} of folio {} exceeds the balance by {} VND and is owed back",
                collection.getCollectionReference(), folio.getBillReference(), collection.getExcessVnd());
        audit(folio, "FOLIO_COLLECTION_EXCESS_OWED", actorAccountId, collection, correlationReference);
    }

    /** FR-036: cash taken after a branch's close belongs to the next working day. */
    private LocalDate cashBusinessDay(Long branchId) {
        LocalDate today = LocalDate.now(VIETNAM);
        return cashCloses.findByBranchIdAndBusinessDay(branchId, today).isPresent() ? today.plusDays(1) : today;
    }

    private SettlementFolioCollection requirePending(String collectionReference, FolioCollectionMethod method) {
        SettlementFolioCollection collection = collections.findByCollectionReference(collectionReference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (collection.getMethod() != method) {
            throw new WorkshopSettlementConflictException("COLLECTION_METHOD_MISMATCH");
        }
        if (!collection.isPending()) {
            throw new WorkshopSettlementConflictException("COLLECTION_NOT_PENDING");
        }
        return collection;
    }

    private CollectionOutcome outcome(SettlementFinalBill folio, SettlementFolioCollection collection) {
        FolioBalanceService.FolioBalance balance = balances.balanceOf(folio);
        return new CollectionOutcome(collection.getCollectionReference(), collection.getMethod(),
                collection.getAmountVnd(), collection.getExcessVnd(), collection.getStatus(), balance.balanceDue(),
                balance.pendingVnd(), folio.getLifecycleStatus());
    }

    private void audit(SettlementFinalBill folio, String action, Long actorAccountId,
                       SettlementFolioCollection collection, String correlationReference) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_FOLIO_COLLECTION")
                .aggregateReference(collection.getCollectionReference())
                .actionCode(action)
                .actorReference(actorAccountId == null ? "SYSTEM" : "account:" + actorAccountId)
                .correlationReference(correlationReference == null || correlationReference.isBlank()
                        ? "CORR-" + UUID.randomUUID() : correlationReference)
                .evidenceReference(collection.getExternalReference())
                .reasonReference(folio.getBookingReference())
                .createdAt(Instant.now())
                .build());
    }

    private static String reference() {
        return "COLL-" + UUID.randomUUID();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
