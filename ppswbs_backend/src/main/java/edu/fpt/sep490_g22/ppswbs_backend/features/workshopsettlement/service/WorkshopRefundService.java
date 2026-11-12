package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundReconciliationCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRefundStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
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
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Objects;
import java.util.UUID;

/** Dispatches one durable refund operation per original payment and waits for signed QueryDR evidence. */
@Service
@Slf4j
public class WorkshopRefundService {
    private static final String CASE_TYPE = "CUSTOMER_CANCELLATION";
    private static final String CASE_PENDING = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseLifecycleStatus.PENDING_PROVIDER.name();
    private static final String CASE_RESOLVED = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseLifecycleStatus.RESOLVED.name();
    private static final String CASE_REJECTED = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCaseLifecycleStatus.REJECTED.name();
    private static final String REASON_MANUAL_REMEDIATION = "REFUND_PARTIAL_MANUAL_REMEDIATION_REQUIRED";
    /** The allocation is with the T097 bank refund and waits for a Manager to confirm the transfer. */
    static final String PENDING_BANK_REFUND = "PENDING_BANK_REFUND";
    private static final Duration QUERY_INTERVAL = Duration.ofMinutes(5);
    private static final Duration QUERY_WINDOW = Duration.ofHours(24);

    private final VnpayMerchantOperationsPort provider;
    private final SettlementRefundAllocationRepository allocations;
    private final SettlementCaseRepository cases;
    private final SettlementBusinessAuditRepository audits;
    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopCancellationLoyaltyService cancellationLoyalty;
    private final WorkshopBookingSettlementAdapter booking;
    private final SettlementFinalBillRepository bills;
    private final SettlementCustomerNotifier notifier;
    private final BankRefundService bankRefunds;
    private final TransactionTemplate transaction;
    private final String merchantApiIpAddress;
    private final String merchantApiCreatedBy;

    @Autowired
    public WorkshopRefundService(VnpayMerchantOperationsPort provider,
            SettlementRefundAllocationRepository allocations,
            SettlementCaseRepository cases,
            SettlementBusinessAuditRepository audits,
            WorkshopOperationsSettlementAdapter operations,
            WorkshopCancellationLoyaltyService cancellationLoyalty,
            WorkshopBookingSettlementAdapter booking,
            SettlementFinalBillRepository bills,
            SettlementCustomerNotifier notifier,
            BankRefundService bankRefunds,
            PlatformTransactionManager transactionManager,
            @Value("${vnpay.merchant-api-ip-address:}") String merchantApiIpAddress,
            @Value("${vnpay.merchant-api-created-by:}") String merchantApiCreatedBy) {
        this.provider = provider;
        this.allocations = allocations;
        this.cases = cases;
        this.audits = audits;
        this.operations = operations;
        this.cancellationLoyalty = cancellationLoyalty;
        this.booking = booking;
        this.bills = bills;
        this.notifier = notifier;
        this.bankRefunds = bankRefunds;
        this.transaction = new TransactionTemplate(transactionManager);
        this.merchantApiIpAddress = trimToNull(merchantApiIpAddress);
        this.merchantApiCreatedBy = trimToNull(merchantApiCreatedBy);
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
            if (merchantApiCreatedBy == null || merchantApiIpAddress == null) {
                log.warn("Refund dispatch awaits merchant API configuration; caseReference={}", caseReference);
                return;
            }
            dispatchPreparedAllocation(allocation.getAllocationReference());
            SettlementRefundAllocation latest = findAllocation(allocation.getAllocationReference());
            if (latest == null || !"VERIFIED".equals(latest.getLifecycleStatus())) return;
        }
        applyCancellationConsequenceIfFullyRefunded(caseReference);
    }

    /**
     * T098: Mland's own money — a remedy for a cancellation Mland caused, an overcharge corrected after the
     * folio was finalized, and a late payment that was not re-confirmed — is refunded by bank transfer, so
     * these never reach the gateway. {@code ADDON_REFUND} joins them in T104.
     */
    private static final java.util.Set<String> BANK_ROUTED_CASE_TYPES =
            java.util.Set.of("MLAND_CANCELLATION_REMEDY", "FOLIO_CORRECTION", "LATE_PAYMENT_REFUND");

    /** Hands one prepared allocation to the bank refund of T097 and leaves it waiting for a Manager. */
    private void routeToBankRefund(SettlementRefundAllocation allocation, SettlementCase settlementCase) {
        // A legacy refund already at the gateway is never duplicated by hand: reconcile it first (T098).
        if ("99".equals(allocation.getProviderResponseCode())
                || allocation.getProviderRefundTransactionNumber() != null) {
            log.warn("Allocation {} has an unreconciled gateway refund; the bank refund waits for it",
                    allocation.getAllocationReference());
            return;
        }
        RefundReason reason = "FOLIO_CORRECTION".equals(settlementCase.getCaseType())
                ? RefundReason.OVERCHARGE_AFTER_FINALIZED
                : "LATE_PAYMENT_REFUND".equals(settlementCase.getCaseType())
                ? RefundReason.LATE_PAYMENT_NOT_RECONFIRMED
                : RefundReason.MLAND_CANCELLATION;
        var opened = bankRefunds.open(settlementCase.getBookingReference(), null, reason,
                allocation.getAllocationReference(), allocation.getAllocatedAmountVnd(), null,
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

        String expectedType = expectedRefundType(allocation);
        String orderInfo = "FOLIO_CORRECTION".equals(settlementCase.getCaseType())
                ? "Workshop folio correction " + settlementCase.getCaseReference()
                : "ADDON_REFUND".equals(settlementCase.getCaseType())
                ? "Workshop add-on refund " + settlementCase.getCaseReference()
                : "Workshop cancellation " + settlementCase.getCaseReference();
        VnpayMerchantOperationResult result;
        try {
            result = provider.requestRefund(new VnpayRefundCommand(
                    allocation.getOperationReference(), allocation.getOriginalPaymentReference(),
                    allocation.getOriginalProviderTransactionNumber(), allocation.getOriginalProviderCreateDate(),
                    allocation.getAllocatedAmountVnd(), orderInfo, merchantApiCreatedBy, merchantApiIpAddress));
        } catch (RuntimeException uncertainProviderFailure) {
            updateAllocation(allocationReference, current -> {
                current.setLifecycleStatus("UNKNOWN");
                current.setUpdatedAt(Instant.now());
            }, "REFUND_REQUEST_UNKNOWN");
            log.warn("Refund request outcome is unknown; allocationReference={} exceptionType={}",
                    allocationReference, uncertainProviderFailure.getClass().getSimpleName());
            return;
        }

        if (!matchesRefundResponse(result, allocation, expectedType)) {
            updateAllocation(allocationReference, current -> {
                current.setLifecycleStatus("UNKNOWN");
                current.setProviderResponseCode(safeCode(result));
                current.setUpdatedAt(Instant.now());
            }, "REFUND_RESPONSE_UNVERIFIED");
            return;
        }

        if ("REJECTED".equals(result.status())) {
            rejectAllocation(allocationReference, safeCode(result));
            return;
        }
        if (!"ACCEPTED".equals(result.status()) || !"00".equals(result.providerResponseCode())
                || !isProviderTransactionNumber(result.providerTransactionNumber())) {
            updateAllocation(allocationReference, current -> {
                current.setLifecycleStatus("UNKNOWN");
                current.setProviderResponseCode(safeCode(result));
                current.setProviderTransactionType(result.transactionType());
                current.setProviderTransactionStatus(result.transactionStatus());
                current.setProviderRefundTransactionNumber(isProviderTransactionNumber(result.providerTransactionNumber())
                        ? result.providerTransactionNumber() : null);
                current.setProviderAcceptedAt("00".equals(result.providerResponseCode()) ? Instant.now() : null);
                current.setUpdatedAt(Instant.now());
            }, "REFUND_REQUEST_PENDING_OR_UNKNOWN");
            if (isProviderTransactionNumber(result.providerTransactionNumber()))
                reconcileAllocation(allocationReference);
            return;
        }

        updateAllocation(allocationReference, current -> {
            current.setLifecycleStatus("ACCEPTED");
            current.setProviderResponseCode(result.providerResponseCode());
            current.setProviderTransactionType(result.transactionType());
            current.setProviderTransactionStatus(result.transactionStatus());
            current.setProviderRefundTransactionNumber(result.providerTransactionNumber());
            current.setProviderAcceptedAt(Instant.now());
            current.setUpdatedAt(Instant.now());
        }, "REFUND_REQUEST_ACCEPTED");
        reconcileAllocation(allocationReference);
    }

    private void reconcileAllocation(String allocationReference) {
        SettlementRefundAllocation allocation = findAllocation(allocationReference);
        if (allocation == null || !List.of("ACCEPTED", "UNKNOWN").contains(allocation.getLifecycleStatus())
                || !isProviderTransactionNumber(allocation.getProviderRefundTransactionNumber())) return;
        SettlementCase settlementCase = cases.findByCaseReference(allocation.getCaseReference()).orElse(null);
        if (!isPendingRefundCase(settlementCase)) return;
        String orderInfo = "FOLIO_CORRECTION".equals(settlementCase.getCaseType())
                ? "Workshop folio correction " + settlementCase.getCaseReference()
                : "ADDON_REFUND".equals(settlementCase.getCaseType())
                ? "Workshop add-on refund " + settlementCase.getCaseReference()
                : "Workshop cancellation " + settlementCase.getCaseReference();
        String queryReference = "VNPAY-REFUND-QUERY-" + UUID.randomUUID().toString().replace("-", "");
        VnpayMerchantOperationResult result;
        try {
            result = provider.queryRefund(new VnpayRefundReconciliationCommand(
                    queryReference, allocation.getOriginalPaymentReference(),
                    allocation.getProviderRefundTransactionNumber(), allocation.getOriginalProviderCreateDate(),
                    allocation.getAllocatedAmountVnd(), orderInfo, merchantApiIpAddress));
        } catch (RuntimeException queryFailure) {
            updateAllocation(allocationReference, current -> {
                current.setLifecycleStatus("UNKNOWN");
                current.setLastQueryAt(Instant.now());
                current.setUpdatedAt(Instant.now());
            }, "REFUND_QUERY_UNKNOWN");
            log.warn("Refund QueryDR failed; allocationReference={} exceptionType={}",
                    allocationReference, queryFailure.getClass().getSimpleName());
            return;
        }

        String expectedType = expectedRefundType(allocation);
        if (!matchesRefundQuery(result, allocation, expectedType)) {
            updateAllocation(allocationReference, current -> {
                current.setLifecycleStatus("UNKNOWN");
                current.setProviderResponseCode(safeCode(result));
                current.setLastQueryAt(Instant.now());
                current.setUpdatedAt(Instant.now());
            }, "REFUND_QUERY_UNVERIFIED");
            return;
        }

        String nextStatus = switch (result.transactionStatus()) {
            case "00" -> "VERIFIED";
            case "09" -> "REJECTED";
            default -> "ACCEPTED";
        };
        updateAllocation(allocationReference, current -> {
            current.setLifecycleStatus(nextStatus);
            current.setProviderResponseCode(result.providerResponseCode());
            current.setProviderTransactionType(result.transactionType());
            current.setProviderTransactionStatus(result.transactionStatus());
            current.setLastQueryAt(Instant.now());
            current.setUpdatedAt(Instant.now());
        }, "REFUND_QUERY_" + nextStatus);

        if ("REJECTED".equals(nextStatus)) rejectAllocation(allocationReference, result.providerResponseCode());
        else if ("VERIFIED".equals(nextStatus)) applyCancellationConsequenceIfFullyRefunded(allocation.getCaseReference());
    }

    @Scheduled(fixedDelayString = "${workshop.settlement.refund-query-delay-ms:300000}")
    public void reconcileDueRefunds() {
        Instant now = Instant.now();
        List<SettlementRefundAllocation> due;
        try {
            due = allocations.findDueRefundQueries(List.of("ACCEPTED", "UNKNOWN"),
                    now.minus(QUERY_WINDOW), now.minus(QUERY_INTERVAL));
        } catch (RuntimeException storageFailure) {
            log.warn("Refund QueryDR scheduler could not load due allocations ({})",
                    storageFailure.getClass().getSimpleName());
            return;
        }
        if (merchantApiIpAddress == null) return;
        for (SettlementRefundAllocation allocation : due) {
            try {
                reconcileAllocation(allocation.getAllocationReference());
            } catch (RuntimeException failure) {
                log.warn("Refund QueryDR scheduler deferred allocationReference={} ({})",
                        allocation.getAllocationReference(), failure.getClass().getSimpleName());
            }
        }
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

    private void rejectAllocation(String allocationReference, String responseCode) {
        SettlementRefundAllocation rejected = updateAllocation(allocationReference, current -> {
            current.setLifecycleStatus("REJECTED");
            current.setProviderResponseCode(responseCode);
            current.setUpdatedAt(Instant.now());
        }, "REFUND_ALLOCATION_REJECTED");
        if (rejected == null) return;
        List<SettlementRefundAllocation> caseAllocations = allocations
                .findAllByCaseReferenceOrderByCreatedAtAsc(rejected.getCaseReference());
        boolean anyVerified = caseAllocations.stream().anyMatch(a -> "VERIFIED".equals(a.getLifecycleStatus()));
        SettlementCase settlementCase = cases.findByCaseReference(rejected.getCaseReference()).orElse(null);
        if (!isPendingRefundCase(settlementCase)) return;
        if ("FOLIO_CORRECTION".equals(settlementCase.getCaseType())) {
            if (anyVerified) {
                updateCase(settlementCase, CASE_PENDING, REASON_MANUAL_REMEDIATION);
            } else {
                updateCase(settlementCase, CASE_REJECTED, "FOLIO_CORRECTION_REFUND_REJECTED");
            }
            return;
        }
        if ("ADDON_REFUND".equals(settlementCase.getCaseType())) {
            if (anyVerified) {
                updateCase(settlementCase, CASE_PENDING, REASON_MANUAL_REMEDIATION);
            } else {
                updateCase(settlementCase, CASE_REJECTED, "ADDON_REFUND_REJECTED");
            }
            return;
        }
        if (anyVerified) {
            updateCase(settlementCase, CASE_PENDING, REASON_MANUAL_REMEDIATION);
        } else {
            updateCase(settlementCase, CASE_REJECTED, "CUSTOMER_CANCELLATION_REFUND_REJECTED");
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
        String evidenceReference = "VERIFIED-CANCEL-REFUND-" + digest(caseReference).substring(0, 32);
        String operationReference = "OPS-REFUND-CONSEQUENCE-" + digest(caseReference).substring(0, 32);
        try {
            var outcome = operations.applyRefund(settlementCase.getBookingReference(), evidenceReference,
                    operationReference, settlementCase.getCorrelationReference());
            if (outcome == null || outcome.status() == OutcomeStatus.IN_PROGRESS) return;
            if (outcome.status() == OutcomeStatus.ACCEPTED) {
                transaction.executeWithoutResult(status -> {
                    SettlementCase latest = cases.findByCaseReferenceForUpdate(caseReference).orElse(null);
                    if (!isPendingRefundCase(latest)) return;
                    cancellationLoyalty.restoreVerifiedCancellation(latest, evidenceReference);
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
            } else {
                transaction.executeWithoutResult(status -> {
                    SettlementCase latest = cases.findByCaseReference(caseReference).orElse(null);
                    if (isPendingRefundCase(latest))
                        updateCase(latest, CASE_PENDING, "REFUND_VERIFIED_CANCELLATION_CONSEQUENCE_RETRY");
                });
            }
        } catch (RuntimeException failure) {
            log.warn("Verified refund consequence is pending retry; caseReference={} exceptionType={}",
                    caseReference, failure.getClass().getSimpleName());
        }
    }

    /** Customer-facing refund notice for a direct booking; ADDON_REFUND (OTA bill) deliberately sends none. */
    private void notifyRefund(SettlementCase resolved, List<SettlementRefundAllocation> verified) {
        long amount = verified.stream().mapToLong(a -> a.getAllocatedAmountVnd() == null ? 0L : a.getAllocatedAmountVnd()).sum();
        notifier.notifyVerified(resolved.getBookingReference(), SettlementCustomerNotifier.REFUND_VERIFIED, amount,
                resolved.getCaseReference(), resolved.getCorrelationReference());
    }

    private SettlementRefundAllocation updateAllocation(String allocationReference,
            java.util.function.Consumer<SettlementRefundAllocation> change, String action) {
        return transaction.execute(status -> {
            SettlementRefundAllocation current = allocations.findByAllocationReferenceForUpdate(allocationReference)
                    .orElse(null);
            if (current == null) return null;
            String oldStatus = current.getLifecycleStatus();
            change.accept(current);
            SettlementRefundAllocation saved = allocations.save(current);
            if (!oldStatus.equals(saved.getLifecycleStatus())) {
                SettlementCase settlementCase = cases.findByCaseReference(saved.getCaseReference()).orElse(null);
                if (settlementCase != null) saveAudit(settlementCase, action, saved.getAllocationReference());
            }
            return saved;
        });
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

    private static boolean matchesRefundResponse(VnpayMerchantOperationResult result,
            SettlementRefundAllocation allocation, String expectedType) {
        return result != null && "REFUND".equals(result.operationKind())
                && result.responseSignatureVerified()
                && allocation.getOriginalPaymentReference().equals(result.merchantReference())
                && Objects.equals(result.amountVnd(), allocation.getAllocatedAmountVnd())
                && expectedType.equals(result.transactionType());
    }

    private static boolean matchesRefundQuery(VnpayMerchantOperationResult result,
            SettlementRefundAllocation allocation, String expectedType) {
        return result != null && "REFUND_QUERYDR".equals(result.operationKind())
                && result.responseSignatureVerified() && "00".equals(result.providerResponseCode())
                && allocation.getOriginalPaymentReference().equals(result.merchantReference())
                && allocation.getProviderRefundTransactionNumber().equals(result.providerTransactionNumber())
                && Objects.equals(result.amountVnd(), allocation.getAllocatedAmountVnd())
                && expectedType.equals(result.transactionType());
    }

    private static String expectedRefundType(SettlementRefundAllocation allocation) {
        return allocation.getAllocatedAmountVnd().equals(allocation.getOriginalAmountVnd()) ? "02" : "03";
    }

    private static String safeCode(VnpayMerchantOperationResult result) {
        return result == null || result.providerResponseCode() == null ? null
                : result.providerResponseCode().replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static boolean isProviderTransactionNumber(String value) {
        return value != null && value.matches("[0-9]{1,15}");
    }

    private static boolean isPendingRefundCase(SettlementCase value) {
        return value != null && List.of(CASE_TYPE, "MLAND_CANCELLATION_REMEDY", "FOLIO_CORRECTION", "ADDON_REFUND").contains(value.getCaseType())
                && CASE_PENDING.equals(value.getLifecycleStatus());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
