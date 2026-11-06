package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkshopFinalBillApiService {
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final SettlementEvidenceConclusionRepository evidence;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementIdempotencyService idempotency;
    private final FinalBookingBillService billService;
    private final VariableChargeService variableChargeService;
    private final CatalogueAddOnService catalogueAddOnService;

    @Transactional(readOnly = true)
    public FinalBillProjectionDto read(String bookingCode, String contactEmail, String contactPhone,
                                       Authentication authentication) {
        CurrentAccountActor currentActor = actor(authentication);
        // This customer-facing route has only two proof modes: owning Member or Guest.
        // Do not let an authenticated staff/admin account fall back to Guest proof.
        if (currentActor != null && currentActor.role() != ActorRole.MEMBER) {
            throw new WorkshopSettlementNotFoundException();
        }
        Long memberId = currentActor == null ? null : currentActor.accountId();
        // Member identity is authoritative; never forward optional Guest contact data for it.
        if (memberId != null) {
            contactEmail = null;
            contactPhone = null;
        }
        if (memberId == null && ((blank(contactEmail) ? 0 : 1) + (blank(contactPhone) ? 0 : 1) != 1)) {
            throw new WorkshopSettlementNotFoundException();
        }
        var decision = booking.verifyCustomerAccess(bookingCode, memberId, contactEmail, contactPhone);
        if (decision == null || !decision.authorized()) throw new WorkshopSettlementNotFoundException();
        return projection(requireBill(bookingCode));
    }

    /**
     * The operator's own read of the bill: Staff within the booking's location scope, or a Manager. The customer route
     * above stays Member/Guest only, so an operator can never reach it through customer proof.
     */
    @Transactional(readOnly = true)
    public FinalBillProjectionDto readAsOperator(String bookingCode, Authentication authentication) {
        CurrentAccountActor actor = actor(authentication);
        if (actor == null) throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
        var confirmed = booking.confirmedProjection(bookingCode);
        if (confirmed == null) throw new WorkshopSettlementNotFoundException();
        authorization.requireManagerOrStaff(actor, confirmed.bookingLocationReference());
        return projection(requireBill(bookingCode));
    }

    @Transactional
    public FinalBillProjectionDto create(String bookingCode, String idempotencyKey, String correlation,
                                         Authentication authentication) {
        CurrentAccountActor actor = actor(authentication);
        if (actor == null) throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
        var confirmed = booking.confirmedProjection(bookingCode);
        if (confirmed == null) throw new WorkshopSettlementValidationException("BOOKING_NOT_FOUND");
        authorization.requireManagerOrStaff(actor, confirmed.bookingLocationReference());
        String op = operationReference("FINAL-BILL", bookingCode, idempotencyKey);
        String fingerprint = fingerprint("CREATE", bookingCode);
        SettlementIdempotencyResult result = idempotency.execute(op, fingerprint, () ->
                billService.create(bookingCode, op, correlationOrGenerated(correlation)).getBillReference());
        return projection(bills.findByBookingReference(bookingCode)
                .orElseThrow(() -> new WorkshopSettlementConflictException("FINAL_BILL_NOT_FOUND")));
    }

    /** Fixed-price catalogue add-ons the operator may add to this booking's folio; scope-checked like every operator action. */
    @Transactional(readOnly = true)
    public java.util.List<edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AddOnOptionDto> listAddOns(
            String bookingCode, Authentication authentication) {
        CurrentAccountActor actor = actor(authentication);
        if (actor == null) throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
        var confirmed = booking.confirmedProjection(bookingCode);
        if (confirmed == null) throw new WorkshopSettlementNotFoundException();
        authorization.requireManagerOrStaff(actor, confirmed.bookingLocationReference());
        return operations.listAddOns(confirmed.packageReference(), confirmed.bookingLocationReference()).stream()
                .map(a -> new edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AddOnOptionDto(
                        a.addOnReference(), a.description(), a.amountVnd()))
                .toList();
    }

    @Transactional
    public FinalBillProjectionDto addCharge(String bookingCode, String idempotencyKey, String correlation,
                                            ChargeCommandDto command, Authentication authentication) {
        requireBill(bookingCode);
        CurrentAccountActor actor = actor(authentication);
        String operation = operationReference("CHARGE", bookingCode, idempotencyKey);
        if (command.catalogueAddOnReference() != null && !command.catalogueAddOnReference().isBlank()) {
            if (actor == null) throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
            var confirmed = booking.confirmedProjection(bookingCode);
            if (confirmed == null) throw new WorkshopSettlementValidationException("BOOKING_NOT_FOUND");
            // Catalogue add-ons are selected and consented at the workshop by Staff/Manager.
            // Member/Guest routes are read-only and must never mutate settlement charges.
            authorization.requireManagerOrStaff(actor, confirmed.bookingLocationReference());
            // The operator must state that the customer agreed; the backend then generates and stores the consent
            // reference with who and when. It evidences the operator's confirmation, not the customer's own consent.
            if (!command.consentConfirmed()) throw new WorkshopSettlementValidationException("CUSTOMER_CONSENT_REQUIRED");
            String fingerprint = fingerprint("ADD_ON", bookingCode, command.catalogueAddOnReference());
            idempotency.execute(operation, fingerprint, () -> catalogueAddOnService.add(bookingCode,
                    command.catalogueAddOnReference(), actor.accountId())
                    .getChargeReference());
        } else {
            CurrentAccountActor currentActor = actor(authentication);
            if (currentActor == null) throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
            var confirmed = booking.confirmedProjection(bookingCode);
            if (confirmed == null) throw new WorkshopSettlementValidationException("BOOKING_NOT_FOUND");
            authorization.requireManagerOrStaff(currentActor, confirmed.bookingLocationReference());
            // Same rules as a catalogue add-on (owner decision 2026-11-06): the operator confirms that the customer
            // agreed, and the booking's session must have started.
            if (!command.consentConfirmed()) throw new WorkshopSettlementValidationException("CUSTOMER_CONSENT_REQUIRED");
            String fingerprint = fingerprint("VARIABLE", bookingCode, command.description().trim(), command.amount().toString(), command.reason().trim());
            idempotency.execute(operation, fingerprint, () -> {
                if (!operations.serviceStarted(bookingCode)) throw new WorkshopSettlementConflictException("SESSION_NOT_STARTED");
                return variableChargeService.add(bookingCode, command.description(), command.amount(), command.reason(),
                        operation, currentActor.accountId()).getChargeReference();
            });
        }
        return projection(requireBill(bookingCode));
    }

    @Transactional
    public FinalBillProjectionDto finalizeBill(String bookingCode, String key, String correlation,
                                               FinalizationCommandDto command, Authentication authentication) {
        CurrentAccountActor currentActor = actor(authentication);
        if (currentActor == null) throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
        var confirmed = booking.confirmedProjection(bookingCode);
        if (confirmed == null) throw new WorkshopSettlementValidationException("BOOKING_NOT_FOUND");
        authorization.requireManagerOrStaff(currentActor, confirmed.bookingLocationReference());
        SettlementFinalBill openBill = requireBill(bookingCode);
        if (outstanding(openBill) != 0L) {
            throw new WorkshopSettlementConflictException("OUTSTANDING_BALANCE");
        }
        String operation = operationReference("FINALIZE", bookingCode, key);
        String fingerprint = fingerprint("FINALIZE", bookingCode);
        idempotency.execute(operation, fingerprint, () -> {
            long currentOutstanding = outstanding(requireBill(bookingCode));
            var bill = billService.finalizeAuthorized(bookingCode, currentOutstanding, operation, correlationOrGenerated(correlation));
            return bill.getBillReference();
        });
        return projection(requireBill(bookingCode));
    }

    private FinalBillProjectionDto projection(SettlementFinalBill bill) {
        List<SettlementCharge> currentCharges = charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(
                bill.getBillReference(), "CONFIRMED");
        long chargeTotal = currentCharges.stream().mapToLong(SettlementCharge::getAmountVnd).sum();
        long finalPayments = totalEvidence(bill.getBookingReference(), "FINAL_PAYMENT_VERIFIED");
        long refunds = totalEvidence(bill.getBookingReference(), "REFUND_VERIFIED");
        long outstanding = outstanding(bill);
        long paid = Math.addExact(bill.getAcceptedDepositAmountVnd(), finalPayments);
        return new FinalBillProjectionDto(bill.getBookingReference(), bill.getLifecycleStatus(), bill.getCurrencyCode(),
                outstanding, paid, refunds, currentCharges.stream().map(c ->
                new FinalBillProjectionDto.CustomerVisibleChargeDto(c.getDescription(), c.getAmountVnd())).toList());
    }

    private long outstanding(SettlementFinalBill bill) {
        long chargeTotal = charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(
                bill.getBillReference(), "CONFIRMED").stream().mapToLong(c -> c.getAmountVnd() == null ? 0L : c.getAmountVnd()).sum();
        return Math.max(0L, Math.subtractExact(Math.addExact(bill.getRemainingAmountVnd(), chargeTotal),
                totalEvidence(bill.getBookingReference(), "FINAL_PAYMENT_VERIFIED")));
    }

    private long totalEvidence(String bookingCode, String conclusion) {
        return evidence.findByBookingReferenceAndConclusion(bookingCode, conclusion).stream()
                .mapToLong(item -> item.getAmountVnd() == null ? 0L : item.getAmountVnd()).sum();
    }

    private SettlementFinalBill requireBill(String bookingCode) {
        return bills.findByBookingReference(bookingCode)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }
    private static CurrentAccountActor actor(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor a ? a : null;
    }
    private static String operationReference(String kind, String booking, String key) {
        if (blank(key) || key.length() > 128) throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        return kind + "-" + digest(booking + "|" + key.trim());
    }
    private static String fingerprint(String... values) { return digest(String.join("|", values)); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String correlationOrGenerated(String correlation) { return blank(correlation) ? UUID.randomUUID().toString() : correlation.trim(); }
}
