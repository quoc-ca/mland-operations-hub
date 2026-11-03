package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Creates staff-visible, short-lived final-balance checkouts and reconciles expired attempts before retry. */
@Service
@Slf4j
public class FinalPaymentCheckoutService {
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter PROVIDER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.ofHours(7));

    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementFinalPaymentAttemptRepository attempts;
    private final SettlementBusinessAuditRepository audits;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementIdempotencyService idempotency;
    private final PaymentGatewayCheckoutPort checkout;
    private final PaymentOwnerRegistrationPort owners;
    private final VnpayMerchantOperationsPort merchantOperations;
    private final FinalPaymentService finalPaymentService;
    private final TransactionTemplate transaction;
    private final String merchantApiIpAddress;

    public FinalPaymentCheckoutService(SettlementFinalBillRepository bills,
            SettlementChargeRepository charges,
            SettlementEvidenceConclusionRepository evidence,
            SettlementFinalPaymentAttemptRepository attempts,
            SettlementBusinessAuditRepository audits,
            WorkshopBookingSettlementAdapter booking,
            WorkshopSettlementAuthorizationService authorization,
            SettlementIdempotencyService idempotency,
            PaymentGatewayCheckoutPort checkout,
            PaymentOwnerRegistrationPort owners,
            VnpayMerchantOperationsPort merchantOperations,
            FinalPaymentService finalPaymentService,
            PlatformTransactionManager transactionManager,
            @Value("${vnpay.merchant-api-ip-address:}") String merchantApiIpAddress) {
        this.bills = bills;
        this.charges = charges;
        this.evidence = evidence;
        this.attempts = attempts;
        this.audits = audits;
        this.booking = booking;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.checkout = checkout;
        this.owners = owners;
        this.merchantOperations = merchantOperations;
        this.finalPaymentService = finalPaymentService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.merchantApiIpAddress = merchantApiIpAddress == null ? "" : merchantApiIpAddress.trim();
    }

    public FinalPaymentAttemptResponse start(String bookingCode, String idempotencyKey,
            String correlationReference, String clientIp, CurrentAccountActor actor) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
        if (bookingCode == null || bookingCode.isBlank()) {
            throw new WorkshopSettlementValidationException("BOOKING_REFERENCE_REQUIRED");
        }
        var confirmed = booking.confirmedProjection(bookingCode);
        if (confirmed == null) throw new WorkshopSettlementNotFoundException();
        authorization.requireManagerOrStaff(actor, confirmed.bookingLocationReference());

        String operationReference = operationReference(bookingCode, idempotencyKey);
        String fingerprint = digest("START_FINAL_PAYMENT|" + bookingCode);
        Optional<String> prior = idempotency.findOutcomeReference(operationReference, fingerprint);
        if (prior.isPresent()) {
            SettlementFinalPaymentAttempt priorAttempt = attempts.findByAttemptReference(prior.get())
                    .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
            return response(priorAttempt);
        }

        StartResult initial = transaction.execute(status -> startInTransaction(bookingCode, operationReference,
                fingerprint, correlationReference, clientIp, actor));
        if (initial == null) throw new WorkshopSettlementConflictException("FINAL_PAYMENT_CHECKOUT_UNAVAILABLE");
        if (initial.attempt() != null) return response(initial.attempt());

        // Merchant API network I/O must not run while a Settlement transaction is open.
        if (merchantApiIpAddress.isBlank()) {
            throw new WorkshopSettlementConflictException("PAYMENT_RECONCILIATION_CONFIGURATION_MISSING");
        }
        SettlementFinalPaymentAttempt expiredAttempt = initial.expiredAttempt();
        String queryOperation = "FINAL-QDR-" + digest(expiredAttempt.getAttemptReference() + "|" + UUID.randomUUID());
        VnpayMerchantOperationResult query = merchantOperations.queryTransaction(new VnpayQueryDrCommand(
                queryOperation, expiredAttempt.getMerchantReference(), expiredAttempt.getProviderCreateDate(),
                orderInfo(bookingCode), merchantApiIpAddress));

        StartResult afterQuery = transaction.execute(status -> reconcileThenStart(bookingCode, operationReference,
                fingerprint, correlationReference, clientIp, actor, expiredAttempt.getAttemptReference(), query));
        if (afterQuery == null) throw new WorkshopSettlementConflictException("PAYMENT_ATTEMPT_RECONCILIATION_PENDING");
        if (afterQuery.blockingCode() != null) throw new WorkshopSettlementConflictException(afterQuery.blockingCode());
        return response(afterQuery.attempt());
    }

    private StartResult startInTransaction(String bookingCode, String operationReference, String fingerprint,
            String correlationReference, String clientIp, CurrentAccountActor actor) {
        SettlementFinalBill bill = bills.findByBookingReferenceForUpdate(bookingCode)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        long outstanding = outstanding(bill);
        if (outstanding <= 0) throw new WorkshopSettlementConflictException("NO_OUTSTANDING_BALANCE");

        SettlementFinalPaymentAttempt latest = attempts
                .findFirstByBookingReferenceOrderByCreatedAtDescIdDesc(bookingCode).orElse(null);
        Instant now = Instant.now();
        if (latest != null && ("PENDING".equals(latest.getLifecycleStatus())
                || "UNKNOWN".equals(latest.getLifecycleStatus()))) {
            if (latest.getExpiresAt().isAfter(now)) {
                SettlementIdempotencyResult result = idempotency.execute(operationReference, fingerprint,
                        latest::getAttemptReference);
                return new StartResult(attempts.findByAttemptReference(result.outcomeReference()).orElseThrow(), null, null);
            }
            return new StartResult(null, latest, null);
        }
        return createAttempt(bill, outstanding, operationReference, fingerprint, correlationReference, clientIp, actor);
    }

    private StartResult reconcileThenStart(String bookingCode, String operationReference, String fingerprint,
            String correlationReference, String clientIp, CurrentAccountActor actor,
            String expiredAttemptReference, VnpayMerchantOperationResult query) {
        SettlementFinalBill bill = bills.findByBookingReferenceForUpdate(bookingCode)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFinalPaymentAttempt expired = attempts.findByAttemptReferenceForUpdate(expiredAttemptReference)
                .orElseThrow(() -> new WorkshopSettlementConflictException("PAYMENT_ATTEMPT_NOT_FOUND"));

        SettlementFinalPaymentAttempt newest = attempts
                .findFirstByBookingReferenceOrderByCreatedAtDescIdDesc(bookingCode).orElse(expired);
        if (!newest.getAttemptReference().equals(expired.getAttemptReference())
                && ("PENDING".equals(newest.getLifecycleStatus()) || "UNKNOWN".equals(newest.getLifecycleStatus()))) {
            if (newest.getExpiresAt().isAfter(Instant.now())) {
                SettlementIdempotencyResult result = idempotency.execute(operationReference, fingerprint,
                        newest::getAttemptReference);
                return new StartResult(attempts.findByAttemptReference(result.outcomeReference()).orElseThrow(), null, null);
            }
            return new StartResult(null, null, "PAYMENT_ATTEMPT_RECONCILIATION_PENDING");
        }
        if (!newest.getAttemptReference().equals(expired.getAttemptReference())
                && ("VERIFIED_PAID".equals(newest.getLifecycleStatus())
                    || "VERIFIED_UNPAID".equals(newest.getLifecycleStatus()))) {
            long due = outstanding(bill);
            if (due <= 0) return new StartResult(newest, null, null);
            return createAttempt(bill, due, operationReference, fingerprint, correlationReference, clientIp, actor);
        }

        if ("VERIFIED_PAID".equals(expired.getLifecycleStatus())) {
            long due = outstanding(bill);
            if (due <= 0) return new StartResult(expired, null, null);
            return createAttempt(bill, due, operationReference, fingerprint, correlationReference, clientIp, actor);
        }
        if ("VERIFIED_UNPAID".equals(expired.getLifecycleStatus())) {
            long due = outstanding(bill);
            if (due <= 0) return new StartResult(expired, null, null);
            return createAttempt(bill, due, operationReference, fingerprint, correlationReference, clientIp, actor);
        }

        if (isVerifiedNotFound(expired, query)) {
            expired.setLifecycleStatus("VERIFIED_UNPAID");
            expired.setPaymentUrlSnapshot(null);
            audit(expired, actor, correlationReference, "FINAL_PAYMENT_QUERYDR_UNPAID");
            long due = outstanding(bill);
            if (due <= 0) return new StartResult(expired, null, null);
            return createAttempt(bill, due, operationReference, fingerprint, correlationReference, clientIp, actor);
        }

        if (isVerifiedSuccess(expired, query)) {
            String providerTransaction = query.providerTransactionNumber();
            String evidenceReference = "VNPAY-" + providerTransaction;
            var outcome = finalPaymentService.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                    bookingCode, evidenceReference, expired.getMerchantReference(), "FINAL_PAYMENT",
                    query.amountVnd(), "VND", providerTransaction, expired.getProviderCreateDate(),
                    evidenceReference, evidenceReference));
            if (outcome == null || outcome.status() != edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade
                    .SettlementPaymentEvidenceOutcome.Status.ACCEPTED) {
                expired.setLifecycleStatus("UNKNOWN");
                expired.setPaymentUrlSnapshot(null);
                return new StartResult(null, null, "PAYMENT_ATTEMPT_RECONCILIATION_PENDING");
            }
            expired.setLifecycleStatus("VERIFIED_PAID");
            expired.setPaymentUrlSnapshot(null);
            audit(expired, actor, correlationReference, "FINAL_PAYMENT_QUERYDR_PAID");
            long due = outstanding(bill);
            if (due <= 0) return new StartResult(expired, null, null);
            return createAttempt(bill, due, operationReference, fingerprint, correlationReference, clientIp, actor);
        }

        expired.setLifecycleStatus("UNKNOWN");
        expired.setPaymentUrlSnapshot(null);
        audit(expired, actor, correlationReference, "FINAL_PAYMENT_QUERYDR_UNKNOWN");
        return new StartResult(null, null, "PAYMENT_ATTEMPT_RECONCILIATION_PENDING");
    }

    private StartResult createAttempt(SettlementFinalBill bill, long amountVnd, String operationReference,
            String fingerprint, String correlationReference, String clientIp, CurrentAccountActor actor) {
        SettlementIdempotencyResult result = idempotency.execute(operationReference, fingerprint, () -> {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(ATTEMPT_TTL);
            String attemptReference = "FPA-" + UUID.randomUUID();
            String merchantReference = "FIN-" + digest(bill.getBookingReference() + "|" + UUID.randomUUID());
            String providerCreateDate = PROVIDER_DATE.format(now);
            String safeClientIp = clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp;
            String url = checkout.createFinalPaymentUrl(merchantReference, bill.getBookingReference(), amountVnd,
                    now, expiresAt, safeClientIp);
            owners.registerOwner(merchantReference, bill.getBookingReference(), "FINAL_PAYMENT", amountVnd,
                    "VND", providerCreateDate);
            SettlementFinalPaymentAttempt attempt = attempts.saveAndFlush(SettlementFinalPaymentAttempt.builder()
                    .attemptReference(attemptReference)
                    .billReference(bill.getBillReference())
                    .bookingReference(bill.getBookingReference())
                    .operationReference(operationReference)
                    .merchantReference(merchantReference)
                    .amountVnd(amountVnd)
                    .currencyCode("VND")
                    .providerCreateDate(providerCreateDate)
                    .paymentUrlSnapshot(url)
                    .lifecycleStatus("PENDING")
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .build());
            audit(attempt, actor, correlationReference, "FINAL_PAYMENT_ATTEMPT_CREATED");
            return attempt.getAttemptReference();
        });
        SettlementFinalPaymentAttempt attempt = attempts.findByAttemptReference(result.outcomeReference())
                .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
        return new StartResult(attempt, null, null);
    }

    private boolean isVerifiedNotFound(SettlementFinalPaymentAttempt attempt, VnpayMerchantOperationResult query) {
        return query != null && query.responseSignatureVerified() && "RECONCILED".equals(query.status())
                && "91".equals(query.providerResponseCode())
                && attempt.getMerchantReference().equals(query.merchantReference());
    }

    private boolean isVerifiedSuccess(SettlementFinalPaymentAttempt attempt, VnpayMerchantOperationResult query) {
        return query != null && query.responseSignatureVerified() && "RECONCILED".equals(query.status())
                && "00".equals(query.providerResponseCode()) && "00".equals(query.transactionStatus())
                && attempt.getMerchantReference().equals(query.merchantReference())
                && attempt.getAmountVnd().equals(query.amountVnd())
                && query.providerTransactionNumber() != null && !query.providerTransactionNumber().isBlank();
    }

    private long outstanding(SettlementFinalBill bill) {
        long confirmedCharges = charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(
                        bill.getBillReference(), "CONFIRMED").stream()
                .mapToLong(charge -> charge.getAmountVnd() == null ? 0L : charge.getAmountVnd()).sum();
        long paid = evidence.findByBookingReferenceAndConclusion(bill.getBookingReference(), "FINAL_PAYMENT_VERIFIED")
                .stream().mapToLong(item -> item.getAmountVnd() == null ? 0L : item.getAmountVnd()).sum();
        return Math.max(0, Math.subtractExact(Math.addExact(bill.getRemainingAmountVnd(), confirmedCharges), paid));
    }

    private void audit(SettlementFinalPaymentAttempt attempt, CurrentAccountActor actor,
            String correlationReference, String action) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("FINAL_PAYMENT_ATTEMPT")
                .aggregateReference(attempt.getAttemptReference())
                .actionCode(action)
                .actorReference(actor == null || actor.accountId() == null ? null : actor.accountId().toString())
                .correlationReference(correlationReference == null || correlationReference.isBlank()
                        ? attempt.getOperationReference() : correlationReference.trim())
                .evidenceReference(action.contains("QUERYDR") ? attempt.getMerchantReference() : null)
                .createdAt(Instant.now())
                .build());
    }

    private FinalPaymentAttemptResponse response(SettlementFinalPaymentAttempt attempt) {
        Instant now = Instant.now();
        String status = attempt.getLifecycleStatus();
        String url = attempt.getPaymentUrlSnapshot();
        if ("PENDING".equals(status) && !attempt.getExpiresAt().isAfter(now)) {
            status = "EXPIRED";
            url = null;
        }
        return new FinalPaymentAttemptResponse(attempt.getAttemptReference(), attempt.getMerchantReference(),
                attempt.getAmountVnd(), attempt.getCurrencyCode(), url, attempt.getExpiresAt(), status);
    }

    @Scheduled(fixedDelay = 60_000L)
    public void scrubExpiredCheckoutUrls() {
        transaction.executeWithoutResult(status -> {
            for (SettlementFinalPaymentAttempt attempt : attempts
                    .findTop100ByPaymentUrlSnapshotIsNotNullAndExpiresAtLessThanEqual(Instant.now())) {
                attempt.setPaymentUrlSnapshot(null);
            }
        });
    }

    private static String operationReference(String bookingCode, String key) {
        return "FINAL-PAY-" + digest(bookingCode + "|" + key.trim());
    }

    private static String orderInfo(String bookingCode) { return "Workshop final payment " + bookingCode; }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record StartResult(SettlementFinalPaymentAttempt attempt,
                               SettlementFinalPaymentAttempt expiredAttempt,
                               String blockingCode) { }
}
