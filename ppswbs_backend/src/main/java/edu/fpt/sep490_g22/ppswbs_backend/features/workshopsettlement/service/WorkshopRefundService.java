package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRefundStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAllocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Routes each refund allocation to the manual bank refund (FR-038/FR-039/FR-040); VNPay Refund is not used. */
@Service
@Slf4j
public class WorkshopRefundService {
    private static final String CASE_TYPE = "CUSTOMER_CANCELLATION";
    private static final String CASE_PENDING = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseLifecycleStatus.PENDING_PROVIDER.name();
    private static final String CASE_RESOLVED = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseLifecycleStatus.RESOLVED.name();
    private static final String CASE_REJECTED = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseLifecycleStatus.REJECTED.name();
    /** The allocation is with the T097 bank refund and waits for a Manager to confirm the transfer. */
    static final String PENDING_BANK_REFUND = "PENDING_BANK_REFUND";

    private final SettlementRefundAllocationRepository allocations;
    private final SettlementCaseRepository cases;
    private final SettlementBusinessAuditRepository audits;
    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopBookingSettlementAdapter booking;
    private final SettlementFinalBillRepository bills;
    private final SettlementCustomerNotifier notifier;
    private final BankRefundService bankRefunds;
    private final TransactionTemplate transaction;

    @Autowired
    public WorkshopRefundService(SettlementRefundAllocationRepository allocations,
            SettlementCaseRepository cases,
            SettlementBusinessAuditRepository audits,
            WorkshopOperationsSettlementAdapter operations,
            WorkshopBookingSettlementAdapter booking,
            SettlementFinalBillRepository bills,
            SettlementCustomerNotifier notifier,
            BankRefundService bankRefunds,
            PlatformTransactionManager transactionManager) {
        this.allocations = allocations;
        this.cases = cases;
        this.audits = audits;
        this.operations = operations;
        this.booking = booking;
        this.bills = bills;
        this.notifier = notifier;
        this.bankRefunds = bankRefunds;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Invoked only after the cancellation case and every allocation have committed. */
    public void dispatchCase(String caseReference) {
        SettlementCase settlementCase = cases.findByCaseReference(caseReference).orElse(null);
        if (!isPendingRefundCase(settlementCase)) return;
        List<SettlementRefundAllocation> caseAllocations = allocations
                .findAllByCaseReferenceOrderByCreatedAtAsc(caseReference);
        if (caseAllocations.isEmpty()) {
            log.error("Customer cancellation has no durable refund allocation; caseReference={}", caseReference);
            return;
        }

        for (SettlementRefundAllocation allocation : caseAllocations) {
            if (!"PREPARED".equals(allocation.getLifecycleStatus())) {
                if (!"VERIFIED".equals(allocation.getLifecycleStatus())) return;
                continue;
            }
            dispatchPreparedAllocation(allocation.getAllocationReference());
            SettlementRefundAllocation latest = findAllocation(allocation.getAllocationReference());
            if (latest == null) return;
            // A case may be split across several original payments. Route every allocation to its own
            // durable bank-refund request before waiting for reconciliation; stopping after the first one
            // would leave the remaining allocations PREPARED forever.
            if (PENDING_BANK_REFUND.equals(latest.getLifecycleStatus())) continue;
            if (!"VERIFIED".equals(latest.getLifecycleStatus())) return;
        }
        applyCancellationConsequenceIfFullyRefunded(caseReference);
    }

    /**
     * T098: Mland's own money — a remedy for a cancellation Mland caused, an overcharge corrected after the
     * folio was finalized, and a late payment that was not re-confirmed — is refunded by bank transfer, so
     * these never reach the gateway. The overcharge refund of an OTA booking's separate Mland shop add-on
     * bill is Mland money too, so T104 brought {@code ADDON_REFUND} here as well; only the OTA's own base
     * ticket stays the OTA's process, which this service never touched.
     */
    private static final java.util.Set<String> BANK_ROUTED_CASE_TYPES =
            java.util.Set.of("MLAND_CANCELLATION_REMEDY", "FOLIO_CORRECTION", "LATE_PAYMENT_REFUND",
                    "ADDON_REFUND");

    /** Hands one prepared allocation to the bank refund of T097 and leaves it waiting for a Manager. */
    private void routeToBankRefund(SettlementRefundAllocation allocation, SettlementCase settlementCase) {
        RefundReason reason = switch (settlementCase.getCaseType()) {
            case "FOLIO_CORRECTION" -> RefundReason.OVERCHARGE_AFTER_FINALIZED;
            case "LATE_PAYMENT_REFUND" -> RefundReason.LATE_PAYMENT_NOT_RECONFIRMED;
            case "ADDON_REFUND" -> RefundReason.OTA_ADDON_OVERCHARGE;
            default -> RefundReason.MLAND_CANCELLATION;
        };
        var opened = bankRefunds.open(settlementCase.getBookingReference(), null, reason,
                allocation.getAllocationReference(), allocation.getAllocatedAmountVnd(),
                settlementCase.getCorrelationReference());
        allocation.setLifecycleStatus(PENDING_BANK_REFUND);
        allocation.setBankRefundReference(opened.request().getRequestReference());
        allocation.setUpdatedAt(Instant.now());
        allocations.saveAndFlush(allocation);
        log.info("Allocation {} of case {} goes back by bank refund {}", allocation.getAllocationReference(),
                settlementCase.getCaseReference(), opened.request().getRequestReference());
    }

    /**
     * The Manager matched the bank statement, so the allocation is settled and the consequences of
     * FR-039 may follow. Replay is harmless: an allocation already verified is left alone.
     */
    @org.springframework.context.event.EventListener
    @org.springframework.transaction.annotation.Transactional
    public void onBankRefundConfirmed(
            edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent event) {
        if (event == null || event.sourceReference() == null) {
            return;
        }
        SettlementRefundAllocation allocation = findAllocation(event.sourceReference());
        if (allocation == null || !PENDING_BANK_REFUND.equals(allocation.getLifecycleStatus())) {
            return;
        }
        allocation.setLifecycleStatus("VERIFIED");
        allocation.setUpdatedAt(Instant.now());
        allocations.saveAndFlush(allocation);
        applyCancellationConsequenceIfFullyRefunded(allocation.getCaseReference());
    }

    private void dispatchPreparedAllocation(String allocationReference) {
        SettlementRefundAllocation allocation = findAllocation(allocationReference);
        if (allocation == null || !"PREPARED".equals(allocation.getLifecycleStatus())) return;
        SettlementCase settlementCase = cases.findByCaseReference(allocation.getCaseReference()).orElse(null);
        if (!isPendingRefundCase(settlementCase)) return;

        if ("MLAND_CANCELLATION_REMEDY".equals(settlementCase.getCaseType())) {
            try {
                String opRef = "SETTLE-REFUND-PENDING-" + settlementCase.getCaseReference();
                booking.recordBookingRefundStatus(settlementCase.getBookingReference(),
                        BookingRefundStatus.REFUND_PENDING,
                        settlementCase.getCaseReference(),
                        opRef,
                        settlementCase.getCorrelationReference());
            } catch (RuntimeException failure) {
                log.warn("Failed to notify booking of refund pending status; caseReference={} ({})",
                        settlementCase.getCaseReference(), failure.getClass().getSimpleName());
            }
        }

        // FR-038 (T098): direct Mland money goes back by bank transfer, never through VNPay Refund. The
        // allocation keeps its original payment evidence and is handed to the T097 request instead.
        if (BANK_ROUTED_CASE_TYPES.contains(settlementCase.getCaseType())) {
            routeToBankRefund(allocation, settlementCase);
            return;
        }

        // T099 (FR-038, FR-041): a customer self-cancellation never refunds the booking-time collection.
        if ("CUSTOMER_CANCELLATION".equals(settlementCase.getCaseType())) {
            log.warn("Customer cancellation is not refundable; caseReference={}", settlementCase.getCaseReference());
            updateCase(settlementCase, CASE_REJECTED, "CUSTOMER_CANCELLATION_NON_REFUNDABLE");
            return;
        }

        // Removed 2026-11-15 (owner decision, Hồ Hoàng Quốc): VNPay Refund and refund-QueryDR are not used at all.
        // Every Mland refund goes through the bank refund lifecycle of FR-038/FR-039/FR-040 (T097/T098/T104) and a
        // customer self-cancellation is refused above. The reconciliation of refunds VNPay had accepted earlier is
        // gone with it (no such allocation exists in any local database).
        log.error("No refund route for case type {}; caseReference={} is left for a Manager",
                settlementCase.getCaseType(), settlementCase.getCaseReference());
        updateCase(settlementCase, CASE_PENDING, "REFUND_ROUTE_UNAVAILABLE");
    }

    @Scheduled(fixedDelayString = "${workshop.settlement.refund-query-delay-ms:300000}")
    public void reconcileDueRefunds() {
        // Recovers the consequence of a case whose allocations are all VERIFIED (a Manager confirmed the bank refund).
        try {
            for (SettlementCase settlementCase : cases.findTop100ByCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
                    List.of(CASE_TYPE, "MLAND_CANCELLATION_REMEDY", "FOLIO_CORRECTION", "ADDON_REFUND"), CASE_PENDING)) {
                List<SettlementRefundAllocation> caseAllocations = allocations
                        .findAllByCaseReferenceOrderByCreatedAtAsc(settlementCase.getCaseReference());
                if (!caseAllocations.isEmpty()
                        && caseAllocations.stream().allMatch(a -> "VERIFIED".equals(a.getLifecycleStatus())))
                    applyCancellationConsequenceIfFullyRefunded(settlementCase.getCaseReference());
            }
        } catch (RuntimeException storageFailure) {
            log.warn("Verified refund consequence recovery could not load pending cases ({})",
                    storageFailure.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${workshop.settlement.refund-dispatch-delay-ms:60000}")
    public void recoverPreparedRefunds() {
        List<SettlementRefundAllocation> prepared;
        try {
            prepared = allocations.findTop100ByLifecycleStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    "PREPARED", Instant.now());
        } catch (RuntimeException storageFailure) {
            log.warn("Refund dispatch recovery could not load prepared allocations ({})",
                    storageFailure.getClass().getSimpleName());
            return;
        }
        for (SettlementRefundAllocation allocation : prepared) {
            try {
                SettlementCase settlementCase = cases.findByCaseReference(allocation.getCaseReference()).orElse(null);
                if (isPendingRefundCase(settlementCase))
                    dispatchCase(settlementCase.getCaseReference());
            } catch (RuntimeException failure) {
                log.warn("Refund dispatch recovery deferred allocationReference={} ({})",
                        allocation.getAllocationReference(), failure.getClass().getSimpleName());
            }
        }
    }

    private void applyCancellationConsequenceIfFullyRefunded(String caseReference) {
        SettlementCase settlementCase = cases.findByCaseReference(caseReference).orElse(null);
        if (!isPendingRefundCase(settlementCase)) return;
        List<SettlementRefundAllocation> caseAllocations = allocations.findAllByCaseReferenceOrderByCreatedAtAsc(caseReference);
        if (caseAllocations.isEmpty() || caseAllocations.stream().anyMatch(a -> !"VERIFIED".equals(a.getLifecycleStatus())))
            return;
        if ("FOLIO_CORRECTION".equals(settlementCase.getCaseType())) {
            transaction.executeWithoutResult(status -> {
                SettlementCase latest = cases.findByCaseReferenceForUpdate(caseReference).orElse(null);
                if (!isPendingRefundCase(latest)) return;
                latest.setLifecycleStatus(CASE_RESOLVED);
                latest.setReasonReference(null);
                latest.setUpdatedAt(Instant.now());
                cases.save(latest);
                saveAudit(latest, "FOLIO_CORRECTION_REFUND_VERIFIED", null);
                notifyRefund(latest, caseAllocations);
            });
            return;
        }
        if ("ADDON_REFUND".equals(settlementCase.getCaseType())) {
            transaction.executeWithoutResult(status -> {
                SettlementCase latest = cases.findByCaseReferenceForUpdate(caseReference).orElse(null);
                if (!isPendingRefundCase(latest)) return;
                latest.setLifecycleStatus(CASE_RESOLVED);
                latest.setReasonReference(null);
                latest.setUpdatedAt(Instant.now());
                cases.save(latest);
                saveAudit(latest, "ADDON_REFUND_VERIFIED", null);
            });
            return;
        }
        // T168 (FR-038, 003.2 FR-010aa): a refund reaching REFUNDED is not an entitlement-revocation trigger, so no call
        // is made to the 003.2 facade here. Ticket entitlements and QR stay as cancelTicket left them.
        try {
            {
                transaction.executeWithoutResult(status -> {
                    SettlementCase latest = cases.findByCaseReferenceForUpdate(caseReference).orElse(null);
                    if (!isPendingRefundCase(latest)) return;
                    latest.setLifecycleStatus(CASE_RESOLVED);
                    latest.setReasonReference(null);
                    latest.setUpdatedAt(Instant.now());
                    cases.save(latest);
                    saveAudit(latest, "CUSTOMER_CANCELLATION_REFUND_VERIFIED", null);
                    notifyRefund(latest, caseAllocations);

                    bills.findByBookingReference(latest.getBookingReference()).ifPresent(bill -> {
                        if (SettlementFinalBillLifecycleStatus.OPEN.name().equals(bill.getLifecycleStatus())) {
                            bill.setLifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name());
                            bills.saveAndFlush(bill);
                            saveAudit(latest, "DIRECT_FOLIO_CLOSED_REFUNDED", bill.getBillReference());
                        }
                    });
                    if ("MLAND_CANCELLATION_REMEDY".equals(latest.getCaseType())) {
                        try {
                            String refundOpRef = "SETTLE-REFUND-VERIFIED-" + latest.getCaseReference();
                            booking.recordBookingRefundStatus(latest.getBookingReference(),
                                    BookingRefundStatus.REFUNDED,
                                    latest.getCaseReference(),
                                    refundOpRef,
                                    latest.getCorrelationReference());
                        } catch (RuntimeException ex) {
                            log.warn("Failed to notify booking of REFUNDED status; caseReference={} ({})",
                                    latest.getCaseReference(), ex.getClass().getSimpleName());
                        }
                        try {
                            String outcomeOpRef = "SETTLE-CANCEL-OUTCOME-" + latest.getCaseReference();
                            booking.recordBookingServiceOutcome(latest.getBookingReference(),
                                    BookingServiceOutcome.CANCELLED,
                                    latest.getCaseReference(),
                                    outcomeOpRef,
                                    latest.getCorrelationReference());
                        } catch (RuntimeException ex) {
                            log.warn("Failed to notify booking of CANCELLED outcome; caseReference={} ({})",
                                    latest.getCaseReference(), ex.getClass().getSimpleName());
                        }
                    }
                });
            }
        } catch (RuntimeException failure) {
            log.warn("Verified refund resolution is pending retry; caseReference={} exceptionType={}",
                    caseReference, failure.getClass().getSimpleName());
        }
    }

    /** Customer-facing refund notice for a direct booking; ADDON_REFUND (OTA bill) deliberately sends none. */
    private void notifyRefund(SettlementCase resolved, List<SettlementRefundAllocation> verified) {
        long amount = verified.stream().mapToLong(a -> a.getAllocatedAmountVnd() == null ? 0L : a.getAllocatedAmountVnd()).sum();
        notifier.notifyVerified(resolved.getBookingReference(), SettlementCustomerNotifier.REFUND_VERIFIED, amount,
                resolved.getCaseReference(), resolved.getCorrelationReference());
    }

    private void updateCase(SettlementCase settlementCase, String status, String reason) {
        settlementCase.setLifecycleStatus(status);
        settlementCase.setReasonReference(reason);
        settlementCase.setUpdatedAt(Instant.now());
        cases.save(settlementCase);
        saveAudit(settlementCase, reason, null);
    }

    private void saveAudit(SettlementCase settlementCase, String action, String evidenceReference) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_CASE").aggregateReference(settlementCase.getCaseReference())
                .actionCode(action).correlationReference(settlementCase.getCorrelationReference())
                .evidenceReference(evidenceReference).reasonReference(action).createdAt(Instant.now()).build());
    }

    private SettlementRefundAllocation findAllocation(String reference) {
        return allocations.findByAllocationReference(reference).orElse(null);
    }

    private static boolean isPendingRefundCase(SettlementCase value) {
        return value != null && List.of(CASE_TYPE, "MLAND_CANCELLATION_REMEDY", "FOLIO_CORRECTION", "ADDON_REFUND").contains(value.getCaseType())
                && CASE_PENDING.equals(value.getLifecycleStatus());
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
