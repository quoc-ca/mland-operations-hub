package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.OtaAddonPaymentDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtaAddonPaymentService {
    private static final DateTimeFormatter PROVIDER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.ofHours(7));

    private final SettlementAddonBillRepository addonBills;
    private final SettlementAddonPaymentAttemptRepository addonAttempts;
    private final SettlementAddonRefundRepository addonRefunds;
    private final SettlementCaseRepository cases;
    private final SettlementRefundAllocationRepository allocations;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementBusinessAuditRepository audits;
    private final SettlementIdempotencyService idempotency;
    private final WorkshopSettlementAuthorizationService authorization;
    private final PaymentGatewayCheckoutPort checkout;
    private final PaymentOwnerRegistrationPort owners;
    private final WorkshopRefundService refunds;
    private final PlatformTransactionManager transactionManager;

    public OtaAddonPaymentAttemptResponse startPaymentAttempt(
            String billReference,
            String idempotencyKey,
            String correlationReference,
            String clientIp,
            CurrentAccountActor actor) {
        String billRef = required(billReference, "billReference");
        String key = required(idempotencyKey, "Idempotency-Key");
        if (key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_INVALID");
        }

        return new TransactionTemplate(transactionManager).execute(status -> {
            SettlementAddonBill bill = addonBills.findByBillReferenceForUpdate(billRef)
                    .orElseThrow(WorkshopSettlementNotFoundException::new);
            authorization.requireManagerOrStaff(actor, bill.getLocationId());

            if (!"OPEN".equals(bill.getLifecycleStatus())) {
                throw new WorkshopSettlementConflictException("BILL_NOT_OPEN");
            }

            Instant now = Instant.now();
            Optional<SettlementAddonPaymentAttempt> latestOpt = addonAttempts
                    .findFirstByBillReferenceOrderByCreatedAtDescIdDesc(bill.getBillReference());
            if (latestOpt.isPresent()) {
                SettlementAddonPaymentAttempt latest = latestOpt.get();
                if ("PENDING".equals(latest.getLifecycleStatus()) || "UNKNOWN".equals(latest.getLifecycleStatus())) {
                    if (latest.getExpiresAt().isAfter(now)) {
                        String operationReference = "OTA-ADDON-PAY-" + digest(bill.getBillReference() + "|" + key.trim()).substring(0, 48);
                        String fingerprint = digest("START_OTA_ADDON_PAYMENT|" + bill.getBillReference());
                        idempotency.execute(operationReference, fingerprint, latest::getAttemptReference);
                        return toResponse(latest);
                    }
                    latest.setLifecycleStatus("EXPIRED");
                    latest.setPaymentUrlSnapshot(null);
                    addonAttempts.saveAndFlush(latest);
                }
            }

            String operationReference = "OTA-ADDON-PAY-" + digest(bill.getBillReference() + "|" + key.trim()).substring(0, 48);
            String fingerprint = digest("START_OTA_ADDON_PAYMENT|" + bill.getBillReference());
            Optional<String> prior = idempotency.findOutcomeReference(operationReference, fingerprint);
            if (prior.isPresent()) {
                SettlementAddonPaymentAttempt priorAttempt = addonAttempts.findByAttemptReference(prior.get())
                        .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
                return toResponse(priorAttempt);
            }

            SettlementIdempotencyResult result = idempotency.execute(operationReference, fingerprint, () -> {
                String attemptReference = "OAA-" + UUID.randomUUID();
                String merchantReference = "OTA-" + digest(bill.getBillReference() + "|" + UUID.randomUUID()).substring(0, 24);
                String providerCreateDate = PROVIDER_DATE.format(now);
                Instant expiresAt = now.plus(Duration.ofMinutes(10));
                String safeClientIp = clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp;
                String url = checkout.createShopAddonUrl(merchantReference, bill.getChannelBookingReference(),
                        bill.getAmountMinor(), now, expiresAt, safeClientIp);
                owners.registerOwner(merchantReference, bill.getChannelBookingReference(), "SHOP_ADDON",
                        bill.getAmountMinor(), "VND", providerCreateDate);

                SettlementAddonPaymentAttempt attempt = addonAttempts.saveAndFlush(SettlementAddonPaymentAttempt.builder()
                        .attemptReference(attemptReference)
                        .billReference(bill.getBillReference())
                        .channelBookingReference(bill.getChannelBookingReference())
                        .operationReference(operationReference)
                        .merchantReference(merchantReference)
                        .amountVnd(bill.getAmountMinor())
                        .currencyCode("VND")
                        .providerCreateDate(providerCreateDate)
                        .paymentUrlSnapshot(url)
                        .lifecycleStatus("PENDING")
                        .createdAt(now)
                        .expiresAt(expiresAt)
                        .build());
                audit(attempt.getAttemptReference(), actor, correlationReference, "OTA_ADDON_PAYMENT_ATTEMPT_CREATED");
                return attempt.getAttemptReference();
            });

            SettlementAddonPaymentAttempt attempt = addonAttempts.findByAttemptReference(result.outcomeReference())
                    .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
            return toResponse(attempt);
        });
    }

    public OtaAddonBillProjection getBillStatus(String billReference, CurrentAccountActor actor) {
        String billRef = required(billReference, "billReference");
        SettlementAddonBill bill = addonBills.findByBillReference(billRef)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        authorization.requireManagerOrStaff(actor, bill.getLocationId());

        OtaAddonPaymentAttemptResponse latestAttempt = addonAttempts
                .findFirstByBillReferenceOrderByCreatedAtDescIdDesc(bill.getBillReference())
                .map(OtaAddonPaymentService::toResponse)
                .orElse(null);

        List<SettlementAddonRefund> refundList = addonRefunds.findAllByBillReferenceOrderByCreatedAtAsc(bill.getBillReference());
        List<OtaAddonRefundProjection> refundProjections = refundList.stream().map(r -> {
            SettlementCase sc = cases.findByCaseReference(r.getCaseReference()).orElse(null);
            String status = sc != null ? sc.getLifecycleStatus() : "PENDING_PROVIDER";
            Instant updatedAt = sc != null ? sc.getUpdatedAt() : r.getUpdatedAt();
            return new OtaAddonRefundProjection(
                    r.getRefundReference(),
                    r.getCaseReference(),
                    r.getBillReference(),
                    r.getChannelBookingReference(),
                    r.getAmountVnd(),
                    r.getCurrencyCode(),
                    r.getReason(),
                    status,
                    r.getCreatedAt(),
                    updatedAt);
        }).toList();

        return new OtaAddonBillProjection(
                bill.getBillReference(),
                bill.getChannelBookingReference(),
                bill.getQuoteReference(),
                bill.getItemCode(),
                bill.getItemDescription(),
                bill.getAmountMinor(),
                bill.getCurrencyCode(),
                bill.getLifecycleStatus(),
                bill.getLocationId() != null ? String.valueOf(bill.getLocationId()) : null,
                bill.getCreatedAt(),
                latestAttempt,
                refundProjections);
    }

    public OtaAddonRefundProjection createRefund(
            String billReference,
            String idempotencyKey,
            String correlationReference,
            OtaAddonRefundCommand command,
            CurrentAccountActor actor) {
        OpenedRefund opened = new TransactionTemplate(transactionManager).execute(status ->
                createRefundInTransaction(billReference, idempotencyKey, correlationReference, command, actor));
        if (opened.dispatchRefund()) {
            try {
                refunds.dispatchCase(opened.caseReference());
            } catch (RuntimeException failure) {
                log.warn("OTA add-on refund dispatch deferred to recovery; caseReference={} ({})",
                        opened.caseReference(), failure.getClass().getSimpleName());
            }
        }
        return opened.response();
    }

    private OpenedRefund createRefundInTransaction(
            String billReference,
            String idempotencyKey,
            String correlationReference,
            OtaAddonRefundCommand command,
            CurrentAccountActor actor) {
        String billRef = required(billReference, "billReference");
        String key = required(idempotencyKey, "Idempotency-Key");
        if (key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_INVALID");
        }
        if (command == null) throw new WorkshopSettlementValidationException("command is required");
        Long amountVnd = command.amountVnd();
        if (amountVnd == null || amountVnd <= 0) {
            throw new WorkshopSettlementValidationException("amountVnd must be positive");
        }
        String reason = required(command.reason(), "reason");

        CurrentAccountActor manager = authorization.requireManager(actor);

        SettlementAddonBill bill = addonBills.findByBillReferenceForUpdate(billRef)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!"PAID".equals(bill.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("BILL_NOT_PAID");
        }

        String correlation = correlationReference == null || correlationReference.isBlank()
                ? "CORR-" + UUID.randomUUID() : correlationReference.trim();
        String operationReference = "OTA-ADDON-REFUND-" + digest(bill.getBillReference() + "|" + key.trim()).substring(0, 48);
        String fingerprint = digest(String.join("|", bill.getBillReference(), String.valueOf(manager.accountId()),
                String.valueOf(amountVnd), reason.trim()));

        Optional<String> prior = idempotency.findOutcomeReference(operationReference, fingerprint);
        if (prior.isPresent()) {
            SettlementAddonRefund priorRefund = addonRefunds.findByRefundReference(prior.get())
                    .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
            SettlementCase sc = cases.findByCaseReference(priorRefund.getCaseReference()).orElseThrow();
            return new OpenedRefund(toRefundProjection(priorRefund, sc), false, null);
        }

        // Checked only after the idempotent replay above: a retry of the same key must get the original refund
        // back even while it is still waiting for QueryDR, and only a different request is refused.
        for (SettlementAddonRefund r : addonRefunds.findAllByBillReferenceOrderByCreatedAtAsc(bill.getBillReference())) {
            Optional<SettlementCase> sc = cases.findByCaseReference(r.getCaseReference());
            if (sc.isPresent() && List.of("OPEN", "PENDING_PROVIDER").contains(sc.get().getLifecycleStatus())) {
                throw new WorkshopSettlementConflictException("ADDON_REFUND_ALREADY_IN_PROGRESS");
            }
        }

        SettlementIdempotencyResult result = idempotency.execute(operationReference, fingerprint, () -> {
            List<RefundablePayment> payments = verifiedAddonPayments(bill);
            Map<String, Long> unrefundedByPayment = new LinkedHashMap<>();
            long totalUnrefunded = 0L;
            for (RefundablePayment p : payments) {
                long allocated = allocations.findAllByOriginalPaymentReference(p.paymentReference()).stream()
                        .filter(a -> !"REJECTED".equals(a.getLifecycleStatus()))
                        .mapToLong(SettlementRefundAllocation::getAllocatedAmountVnd)
                        .sum();
                long unrefunded = Math.max(0L, p.amountVnd() - allocated);
                unrefundedByPayment.put(p.paymentReference(), unrefunded);
                totalUnrefunded += unrefunded;
            }

            if (amountVnd > totalUnrefunded) {
                throw new WorkshopSettlementValidationException("OVERCHARGE_EXCEEDS_UNREFUNDED_COLLECTIONS");
            }

            Instant now = Instant.now();
            String caseReference = "SETTLE-ADDON-CASE-" + UUID.randomUUID();
            String refundReference = "REFUND-ADDON-" + UUID.randomUUID();

            long remaining = amountVnd;
            List<SettlementRefundAllocation> planned = new ArrayList<>();
            for (RefundablePayment p : payments) {
                if (remaining == 0) break;
                long available = unrefundedByPayment.getOrDefault(p.paymentReference(), 0L);
                if (available <= 0) continue;
                long alloc = Math.min(available, remaining);

                planned.add(SettlementRefundAllocation.builder()
                        .allocationReference("REFUND-ALLOCATION-" + UUID.randomUUID())
                        .caseReference(caseReference)
                        .originalPaymentReference(p.paymentReference())
                        .originalProviderTransactionNumber(p.providerTransactionNumber())
                        .originalProviderCreateDate(p.providerCreateDate())
                        .originalAmountVnd(p.amountVnd())
                        .allocatedAmountVnd(alloc)
                        .currencyCode("VND")
                        .operationReference("VNPAY-REFUND-" + UUID.randomUUID())
                        .lifecycleStatus("PREPARED")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                remaining -= alloc;
            }

            if (remaining != 0) {
                throw new WorkshopSettlementConflictException("REFUND_ALLOCATION_TOTAL_MISMATCH");
            }

            cases.saveAndFlush(SettlementCase.builder()
                    .caseReference(caseReference)
                    .bookingReference(bill.getChannelBookingReference())
                    .caseType("ADDON_REFUND")
                    .lifecycleStatus("PENDING_PROVIDER")
                    .reasonReference(reason.trim())
                    .correlationReference(correlation)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());

            SettlementAddonRefund savedRefund = addonRefunds.saveAndFlush(SettlementAddonRefund.builder()
                    .refundReference(refundReference)
                    .caseReference(caseReference)
                    .billReference(bill.getBillReference())
                    .channelBookingReference(bill.getChannelBookingReference())
                    .amountVnd(amountVnd)
                    .currencyCode("VND")
                    .reason(reason.trim())
                    .managerActorReference(String.valueOf(manager.accountId()))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());

            for (SettlementRefundAllocation alloc : planned) {
                allocations.saveAndFlush(alloc);
            }

            audit(refundReference, actor, correlationReference, "ADDON_REFUND_CREATED");
            return refundReference;
        });

        SettlementAddonRefund refund = addonRefunds.findByRefundReference(result.outcomeReference())
                .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
        SettlementCase sc = cases.findByCaseReference(refund.getCaseReference()).orElseThrow();
        return new OpenedRefund(toRefundProjection(refund, sc), true, refund.getCaseReference());
    }

    private List<RefundablePayment> verifiedAddonPayments(SettlementAddonBill bill) {
        Set<String> billPaidMerchants = addonAttempts.findAllByBillReferenceOrderByCreatedAtDescIdDesc(bill.getBillReference()).stream()
                .filter(a -> "VERIFIED_PAID".equals(a.getLifecycleStatus()))
                .map(SettlementAddonPaymentAttempt::getMerchantReference)
                .collect(Collectors.toSet());

        List<RefundablePayment> payments = new ArrayList<>();
        for (SettlementEvidenceConclusion item : evidence.findByBookingReferenceAndConclusion(
                bill.getChannelBookingReference(), "SHOP_ADDON_VERIFIED")) {
            if (billPaidMerchants.contains(item.getProviderTransactionReference())) {
                payments.add(new RefundablePayment(
                        item.getProviderTransactionReference(),
                        item.getProviderTransactionNumber(),
                        item.getOriginalProviderCreateDate(),
                        item.getAmountVnd(),
                        item.getCurrencyCode()));
            }
        }
        payments.sort(Comparator.comparing(RefundablePayment::providerCreateDate)
                .thenComparing(RefundablePayment::paymentReference));
        boolean valid = !payments.isEmpty() && payments.stream().allMatch(p ->
                p.paymentReference() != null && !p.paymentReference().isBlank()
                        && p.providerTransactionNumber() != null && p.providerTransactionNumber().matches("[0-9]{1,15}")
                        && p.providerCreateDate() != null && p.providerCreateDate().matches("[0-9]{14}")
                        && p.amountVnd() != null && p.amountVnd() > 0
                        && "VND".equals(p.currencyCode()));
        if (!valid) throw new WorkshopSettlementConflictException("VERIFIED_PAYMENT_EVIDENCE_INVALID");
        return List.copyOf(payments);
    }

    private static OtaAddonPaymentAttemptResponse toResponse(SettlementAddonPaymentAttempt attempt) {
        String paymentUrl = null;
        if ("PENDING".equals(attempt.getLifecycleStatus()) && attempt.getExpiresAt().isAfter(Instant.now())) {
            paymentUrl = attempt.getPaymentUrlSnapshot();
        }
        return new OtaAddonPaymentAttemptResponse(
                attempt.getAttemptReference(),
                attempt.getBillReference(),
                attempt.getMerchantReference(),
                attempt.getAmountVnd(),
                attempt.getCurrencyCode(),
                paymentUrl,
                attempt.getExpiresAt(),
                attempt.getLifecycleStatus());
    }

    private static OtaAddonRefundProjection toRefundProjection(SettlementAddonRefund refund, SettlementCase sc) {
        String status = sc != null ? sc.getLifecycleStatus() : "PENDING_PROVIDER";
        Instant updatedAt = sc != null ? sc.getUpdatedAt() : refund.getUpdatedAt();
        return new OtaAddonRefundProjection(
                refund.getRefundReference(),
                refund.getCaseReference(),
                refund.getBillReference(),
                refund.getChannelBookingReference(),
                refund.getAmountVnd(),
                refund.getCurrencyCode(),
                refund.getReason(),
                status,
                refund.getCreatedAt(),
                updatedAt);
    }

    private void audit(String aggregateReference, CurrentAccountActor actor,
                       String correlationReference, String action) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("OTA_ADDON_PAYMENT")
                .aggregateReference(aggregateReference)
                .actionCode(action)
                .actorReference(actor == null || actor.accountId() == null ? null : actor.accountId().toString())
                .correlationReference(correlationReference == null || correlationReference.isBlank() ? "CORR-" + UUID.randomUUID() : correlationReference.trim())
                .build());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
        return value.trim();
    }

    private static String digest(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private record OpenedRefund(OtaAddonRefundProjection response, boolean dispatchRefund, String caseReference) {}
    private record RefundablePayment(String paymentReference, String providerTransactionNumber,
                                     String providerCreateDate, Long amountVnd, String currencyCode) {}
}

