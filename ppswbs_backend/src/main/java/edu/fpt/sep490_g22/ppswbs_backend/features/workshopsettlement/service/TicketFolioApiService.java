package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ChargeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Service orchestrating per-ticket folio routes (FR-048–FR-051, FR-052–FR-054).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketFolioApiService {

    private final WorkshopSettlementAuthorizationService authorization;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementBusinessAuditRepository audits;
    private final FolioBalanceService folioBalances;
    private final FolioCollectionService folioCollectionService;
    private final FolioFinalizationService folioFinalizationService;
    private final CashDayCloseService cashCloses;
    private final SettlementIdempotencyService idempotency;

    @Transactional(readOnly = true)
    public TicketFolioDto readFolio(String ticketCode, CurrentAccountActor actor) {
        String code = required(ticketCode, "ticketCode");
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFinalBill folio = bills.findByBookingReference(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);

        if (actor == null) {
            throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
        }
        if (actor.role() == ActorRole.MEMBER) {
            if (!Objects.equals(actor.accountId(), ctx.buyerMemberAccountId())) {
                throw new WorkshopSettlementAuthorizationException("MEMBER_OWNERSHIP_REQUIRED");
            }
            return toDto(folio, false);
        }
        if (actor.role() == ActorRole.MANAGER) {
            return toDto(folio, true);
        }
        if (actor.role() == ActorRole.STAFF) {
            authorization.requireManagerOrAssignedStaff(actor, ctx.locationReference(), ctx.lineReference());
            return toDto(folio, true);
        }
        throw new WorkshopSettlementAuthorizationException("FORBIDDEN");
    }

    @Transactional(readOnly = true)
    public TicketFolioDto lookupFolio(String ticketCode, GuestFolioLookupRequest request) {
        String code = required(ticketCode, "ticketCode");
        if (request == null || !request.hasOneProof()) {
            throw new WorkshopSettlementValidationException("ONE_PROOF_REQUIRED");
        }
        booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFinalBill folio = bills.findByBookingReference(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);

        CustomerBookingAccessDecision decision = booking.verifyCustomerAccess(code, null,
                request.contactEmail(), request.contactPhone());
        if (decision == null || !decision.authorized()) {
            throw new WorkshopSettlementNotFoundException();
        }
        return toDto(folio, false);
    }

    @Transactional(readOnly = true)
    public List<CatalogueAddOnOptionDto> listAddOns(String ticketCode, CurrentAccountActor actor) {
        String code = required(ticketCode, "ticketCode");
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        bills.findByBookingReference(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        authorization.requireManagerOrAssignedStaff(actor, ctx.locationReference(), ctx.lineReference());

        List<WorkshopAddOnPriceSnapshot> addOns = operations.listAddOns(ctx.packageReference(), ctx.locationReference());
        return addOns.stream()
                .map(a -> new CatalogueAddOnOptionDto(a.addOnReference(), a.description(), a.amountVnd()))
                .toList();
    }

    @Transactional
    public TicketFolioDto addCharge(String ticketCode, String idempotencyKey, String correlationId,
                                    ChargeCommandDto command, CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        String code = required(ticketCode, "ticketCode");
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        authorization.requireManagerOrAssignedStaff(actor, ctx.locationReference(), ctx.lineReference());

        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!"OPEN".equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }
        if (!operations.serviceStarted(ctx.lineReference())) {
            throw new WorkshopSettlementConflictException("SESSION_NOT_STARTED");
        }
        if (!command.consentConfirmed()) {
            throw new WorkshopSettlementValidationException("CUSTOMER_CONSENT_REQUIRED");
        }

        String correlation = correlation(correlationId);
        String op = "CHARGE:" + code + ":" + idempotencyKey.trim();

        if (command.catalogueAddOnReference() != null && !command.catalogueAddOnReference().isBlank()) {
            var snapshot = operations.addOnPrice(ctx.packageReference(), ctx.locationReference(),
                    command.catalogueAddOnReference().trim());
            if (snapshot == null) {
                throw new WorkshopSettlementValidationException("ADD_ON_UNAVAILABLE");
            }
            if (snapshot.amountVnd() < 5000) {
                throw new WorkshopSettlementValidationException("AMOUNT_BELOW_MINIMUM");
            }
            String fp = "ADD_ON:" + code + ":" + command.catalogueAddOnReference().trim();
            idempotency.execute(op, fp, () -> {
                SettlementCharge charge = charges.saveAndFlush(SettlementCharge.builder()
                        .chargeReference("SETTLE-CHARGE-" + UUID.randomUUID())
                        .billReference(folio.getBillReference())
                        .sourceCategory("CATALOGUE_ADD_ON")
                        .description(snapshot.description())
                        .amountVnd(snapshot.amountVnd())
                        .currencyCode(snapshot.currencyCode())
                        .lifecycleStatus("CONFIRMED")
                        .reasonReference("CATALOGUE-ADDON:" + snapshot.addOnReference())
                        .consentReference("CONSENT-" + UUID.randomUUID())
                        .consentRecordedBy(actor.accountId())
                        .consentRecordedAt(Instant.now())
                        .createdAt(Instant.now())
                        .build());
                return charge.getChargeReference();
            });
        } else {
            if (command.amount() == null || command.amount() < 5000) {
                throw new WorkshopSettlementValidationException("AMOUNT_BELOW_MINIMUM");
            }
            String fp = "VARIABLE:" + code + ":" + command.description().trim() + ":" + command.amount()
                    + ":" + command.reason().trim();
            idempotency.execute(op, fp, () -> {
                SettlementCharge charge = charges.saveAndFlush(SettlementCharge.builder()
                        .chargeReference("SETTLE-CHARGE-" + UUID.randomUUID())
                        .billReference(folio.getBillReference())
                        .sourceCategory("VARIABLE_CHARGE")
                        .description(command.description().trim())
                        .amountVnd(command.amount())
                        .currencyCode("VND")
                        .lifecycleStatus("CONFIRMED")
                        .reasonReference(command.reason().trim())
                        .consentReference("CONSENT-" + UUID.randomUUID())
                        .consentRecordedBy(actor.accountId())
                        .consentRecordedAt(Instant.now())
                        .createdAt(Instant.now())
                        .build());
                return charge.getChargeReference();
            });
        }
        return toDto(folio, true);
    }

    @Transactional
    public TicketFolioDto finalizeFolio(String ticketCode, String idempotencyKey, String correlationId,
                                        CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        String code = required(ticketCode, "ticketCode");
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        authorization.requireManagerOrAssignedStaff(actor, ctx.locationReference(), ctx.lineReference());

        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if ("FINALIZED".equals(folio.getLifecycleStatus())) {
            return toDto(folio, true);
        }
        if (!"OPEN".equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }
        FolioBalanceService.FolioBalance balance = folioBalances.balanceOf(folio);
        if (balance.balanceDue() != 0) {
            throw new WorkshopSettlementConflictException("OUTSTANDING_BALANCE");
        }
        if (balance.hasPendingPart()) {
            throw new WorkshopSettlementConflictException("COLLECTION_PENDING_VERIFICATION");
        }

        String correlation = correlation(correlationId);
        String op = "FINALIZE:" + code + ":" + idempotencyKey.trim();
        String fp = "FINALIZE:" + code;
        idempotency.execute(op, fp, () -> {
            folio.setLifecycleStatus(SettlementFinalBillLifecycleStatus.FINALIZED.name());
            bills.saveAndFlush(folio);
            audits.save(SettlementBusinessAudit.builder()
                    .auditReference("AUDIT-" + UUID.randomUUID())
                    .aggregateType("SETTLEMENT_FINAL_BILL")
                    .aggregateReference(folio.getBillReference())
                    .actionCode("FOLIO_FINALIZED")
                    .actorReference("account:" + actor.accountId())
                    .correlationReference(correlation)
                    .createdAt(Instant.now())
                    .build());
            folioFinalizationService.concludeTicketSettled(folio, correlation);
            return folio.getBillReference();
        });
        return toDto(folio, true);
    }

    @Transactional
    public void recordDebt(String ticketCode, String idempotencyKey, String correlationId,
                           FolioCollectionDtos.DebtRequest request, CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        String code = required(ticketCode, "ticketCode");
        String correlation = correlation(correlationId);
        String op = "DEBT:" + code + ":" + idempotencyKey.trim();
        String fp = "DEBT:" + code + ":" + request.amountVnd() + ":" + request.reason().trim();
        idempotency.execute(op, fp, () -> {
            folioFinalizationService.recordDebt(code, manager.accountId(), request.amountVnd(),
                    request.reason(), correlation);
            return "DEBT-RECORDED";
        });
    }

    @Transactional
    public void closeDebt(String ticketCode, String idempotencyKey, String correlationId,
                          FolioCollectionDtos.ManagerReasonRequest request, CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        String code = required(ticketCode, "ticketCode");
        String correlation = correlation(correlationId);
        String op = "CLOSE_DEBT:" + code + ":" + idempotencyKey.trim();
        String fp = "CLOSE_DEBT:" + code + ":" + request.reason().trim();
        idempotency.execute(op, fp, () -> {
            folioFinalizationService.closeDebt(code, manager.accountId(), request.reason(), correlation);
            return "DEBT-CLOSED";
        });
    }

    @Transactional
    public CollectionDto recordCollection(String ticketCode, String idempotencyKey, String correlationId,
                                          TicketCollectionRequest request, CurrentAccountActor actor) {
        return recordCollection(ticketCode, idempotencyKey, correlationId, request, null, actor);
    }

    @Transactional
    public CollectionDto recordCollection(String ticketCode, String idempotencyKey, String correlationId,
                                          TicketCollectionRequest request, String clientIp,
                                          CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        String code = required(ticketCode, "ticketCode");
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        // T111, FR-051/FR-002: the cash branch is the ticket's own location; a client-sent branchId is ignored.
        Long branchId = ctx.locationReference();
        CurrentAccountActor staff = authorization.requireManagerOrAssignedStaff(actor, branchId, ctx.lineReference());

        long amt = request.amountVnd() != null ? request.amountVnd() : Long.parseLong(request.amount().trim());
        String correlation = correlation(correlationId);
        String method = request.method().trim().toUpperCase(Locale.ROOT);
        String op = "COLLECT:" + code + ":" + idempotencyKey.trim();
        String fp = "COLLECT:" + code + ":" + method + ":" + amt;

        var outcomeRef = idempotency.execute(op, fp, () -> {
            FolioCollectionService.CollectionOutcome result;
            if ("CASH".equals(method)) {
                long rcvd = request.cashReceivedVnd() != null ? request.cashReceivedVnd()
                        : (request.cashReceived() != null ? Long.parseLong(request.cashReceived().trim()) : amt);
                String receipt = request.customerTransactionReference() != null && !request.customerTransactionReference().isBlank()
                        ? request.customerTransactionReference().trim() : "CASH-" + UUID.randomUUID();
                result = folioCollectionService.collectCash(code, amt, rcvd, receipt, request.payerLabel(),
                        branchId, staff.accountId(), correlation);
            } else if ("BANK_TRANSFER".equals(method)) {
                if (request.customerTransactionReference() == null || request.customerTransactionReference().isBlank()) {
                    throw new WorkshopSettlementValidationException("TRANSACTION_REFERENCE_REQUIRED");
                }
                result = folioCollectionService.recordBankTransfer(code, amt, request.customerTransactionReference().trim(),
                        request.payerLabel(), staff.accountId(), correlation);
            } else if ("VNPAY".equals(method)) {
                // T108: the merchant reference and checkout URL are created by the server; a client value is ignored.
                result = folioCollectionService.recordGatewayAttempt(code, amt, clientIp, staff.accountId(), correlation);
            } else {
                throw new WorkshopSettlementValidationException("UNSUPPORTED_METHOD: " + method);
            }
            return result.collectionReference();
        });

        SettlementFolioCollection col = collections.findByCollectionReference(outcomeRef.outcomeReference())
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        return toCollectionDto(col);
    }

    @Transactional
    public CollectionDto confirmCollection(String ticketCode, String collectionId, String idempotencyKey,
                                           String correlationId, ConfirmBankTransferRequest request,
                                           CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        String code = required(ticketCode, "ticketCode");
        // T119, FR-031: the part must belong to this ticket's own folio, otherwise it is not found.
        SettlementFinalBill folio = bills.findByBookingReference(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFolioCollection col = findCollection(required(collectionId, "collectionId"));
        if (!folio.getBillReference().equals(col.getBillReference())) {
            throw new WorkshopSettlementNotFoundException();
        }
        String correlation = correlation(correlationId);
        String op = "CONFIRM_COLL:" + code + ":" + col.getCollectionReference() + ":" + idempotencyKey.trim();
        String fp = "CONFIRM_COLL:" + code + ":" + col.getCollectionReference();
        var outcome = idempotency.execute(op, fp, () -> folioCollectionService.confirmBankTransfer(
                col.getCollectionReference(), manager.accountId(), correlation).collectionReference());
        SettlementFolioCollection confirmed = collections.findByCollectionReference(outcome.outcomeReference())
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        return toCollectionDto(confirmed);
    }

    /**
     * T109, FR-031/FR-034: a Manager reconciles a stuck pending part of this ticket's folio (VNPay or transfer) as
     * not paid, with a reason, so another method can take the balance. Idempotent per key; the part must belong
     * to this ticket's folio, otherwise it is not found.
     */
    @Transactional
    public CollectionDto failCollection(String ticketCode, String collectionId, String idempotencyKey,
                                        String correlationId, FolioCollectionDtos.ManagerReasonRequest request,
                                        CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        String code = required(ticketCode, "ticketCode");
        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFolioCollection col = findCollection(required(collectionId, "collectionId"));
        if (!folio.getBillReference().equals(col.getBillReference())) {
            throw new WorkshopSettlementNotFoundException();
        }

        String correlation = correlation(correlationId);
        String op = "FAIL_COLL:" + code + ":" + col.getCollectionReference() + ":" + idempotencyKey.trim();
        String fp = "FAIL_COLL:" + code + ":" + col.getCollectionReference() + ":" + request.reason().trim();
        idempotency.execute(op, fp, () -> folioCollectionService.markPendingFailed(col.getCollectionReference(),
                manager.accountId(), request.reason().trim(), correlation).collectionReference());
        return toCollectionDto(col);
    }

    @Transactional
    public void approveHandoverException(String ticketCode, String idempotencyKey, String correlationId,
                                         FolioCollectionDtos.ManagerReasonRequest request,
                                         CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new WorkshopSettlementValidationException("HANDOVER_REASON_REQUIRED");
        }
        String code = required(ticketCode, "ticketCode");
        String correlation = correlation(correlationId);
        // T119, FR-022d: replay with the same key returns the recorded exception without a second audit row.
        String op = "HANDOVER_EXC:" + code + ":" + idempotencyKey.trim();
        String fp = "HANDOVER_EXC:" + code + ":" + request.reason().trim();
        idempotency.execute(op, fp, () -> folioFinalizationService.approveHandoverException(code,
                manager.accountId(), request.reason(), correlation).getBillReference());
    }

    @Transactional
    public TicketFolioDto voidLine(String ticketCode, String lineId, String idempotencyKey,
                                   String correlationId, FolioCollectionDtos.ManagerReasonRequest request,
                                   CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        String code = required(ticketCode, "ticketCode");
        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!"OPEN".equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }
        SettlementCharge charge = findCharge(lineId);
        if (!folio.getBillReference().equals(charge.getBillReference())) {
            throw new WorkshopSettlementNotFoundException();
        }

        String correlation = correlation(correlationId);
        String op = "VOID_LINE:" + code + ":" + lineId.trim() + ":" + idempotencyKey.trim();
        String fp = "VOID_LINE:" + code + ":" + lineId.trim() + ":" + request.reason().trim();
        idempotency.execute(op, fp, () -> {
            if (!"VOIDED".equals(charge.getLifecycleStatus())) {
                charge.setLifecycleStatus("VOIDED");
                charges.saveAndFlush(charge);
                audits.save(SettlementBusinessAudit.builder()
                        .auditReference("AUDIT-" + UUID.randomUUID())
                        .aggregateType("SETTLEMENT_CHARGE")
                        .aggregateReference(charge.getChargeReference())
                        .actionCode("CHARGE_VOIDED")
                        .actorReference("account:" + manager.accountId())
                        .reasonReference(request.reason().trim())
                        .correlationReference(correlation)
                        .createdAt(Instant.now())
                        .build());
            }
            return charge.getChargeReference();
        });
        return toDto(folio, true);
    }

    @Transactional
    public TicketFolioDto voidCollection(String ticketCode, String collectionId, String idempotencyKey,
                                         String correlationId, FolioCollectionDtos.ManagerReasonRequest request,
                                         CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        String code = required(ticketCode, "ticketCode");
        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!"OPEN".equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }
        SettlementFolioCollection col = findCollection(collectionId);
        if (!folio.getBillReference().equals(col.getBillReference())) {
            throw new WorkshopSettlementNotFoundException();
        }

        String correlation = correlation(correlationId);
        String op = "VOID_COLL:" + code + ":" + collectionId.trim() + ":" + idempotencyKey.trim();
        String fp = "VOID_COLL:" + code + ":" + collectionId.trim() + ":" + request.reason().trim();
        idempotency.execute(op, fp, () -> {
            if (col.getStatus() != FolioCollectionStatus.VOIDED) {
                // FR-053/FR-050: cash already counted into a branch's close is refused, whatever the
                // close's confirmation status (the same rule FolioCollectionService uses to send
                // later cash to the next day). The closed day stays locked; nothing is written here,
                // because any cash effect is recorded by the Manager on the current working day.
                if (col.getMethod() == FolioCollectionMethod.CASH && col.getCashBusinessDay() != null
                        && cashCloses.isClosed(col.getBranchId(), col.getCashBusinessDay())) {
                    throw new WorkshopSettlementConflictException("CASH_DAY_ALREADY_CLOSED");
                }
                col.setStatus(FolioCollectionStatus.VOIDED);
                collections.saveAndFlush(col);
                audits.save(SettlementBusinessAudit.builder()
                        .auditReference("AUDIT-" + UUID.randomUUID())
                        .aggregateType("SETTLEMENT_FOLIO_COLLECTION")
                        .aggregateReference(col.getCollectionReference())
                        .actionCode("COLLECTION_VOIDED")
                        .actorReference("account:" + manager.accountId())
                        .reasonReference(request.reason().trim())
                        .correlationReference(correlation)
                        .createdAt(Instant.now())
                        .build());
            }
            return col.getCollectionReference();
        });
        return toDto(folio, true);
    }

    public TicketFolioDto toDto(SettlementFinalBill folio, boolean isOperator) {
        FolioBalanceService.FolioBalance balance = folioBalances.balanceOf(folio);
        long chargeTotal = charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(folio.getBillReference(), "CONFIRMED")
                .stream().mapToLong(c -> c.getAmountVnd() == null ? 0L : c.getAmountVnd()).sum();
        long totalAmount = Math.addExact(folio.getPostPromotionAmountVnd(), chargeTotal);
        long balanceDue = balance.balanceDue();
        long pendingAmount = balance.pendingVnd();
        long paidAmount = Math.max(0L, totalAmount - balanceDue);

        List<CollectionDto> collectionDtos = new ArrayList<>();
        if (folio.getAcceptedDepositAmountVnd() != null && folio.getAcceptedDepositAmountVnd() > 0) {
            collectionDtos.add(new CollectionDto("BOOKING_TIME", "BOOKING_TIME",
                    String.valueOf(folio.getAcceptedDepositAmountVnd()), "CONFIRMED", null));
        }
        for (SettlementFolioCollection c : collections.findByBillReferenceOrderByIdAsc(folio.getBillReference())) {
            collectionDtos.add(toCollectionDto(c));
        }

        List<ChargeDto> chargeDtos = new ArrayList<>();
        for (SettlementCharge ch : charges.findByBillReference(folio.getBillReference())) {
            chargeDtos.add(new ChargeDto(ch.getChargeReference(), ch.getDescription(),
                    String.valueOf(ch.getAmountVnd()), ch.getLifecycleStatus()));
        }

        return new TicketFolioDto(
                folio.getBookingReference(),
                folio.getLifecycleStatus(),
                String.valueOf(totalAmount),
                String.valueOf(paidAmount),
                String.valueOf(pendingAmount),
                String.valueOf(balanceDue),
                collectionDtos,
                chargeDtos
        );
    }

    private static CollectionDto toCollectionDto(SettlementFolioCollection c) {
        // T108: the checkout is shown only while the VNPay part can still be paid.
        boolean payable = c.getMethod() == FolioCollectionMethod.VNPAY && c.isPending()
                && c.getPaymentUrlSnapshot() != null && c.getPaymentExpiresAt() != null
                && c.getPaymentExpiresAt().isAfter(Instant.now());
        return new CollectionDto(
                c.getCollectionReference(),
                c.getMethod().name(),
                String.valueOf(c.getAmountVnd()),
                c.getStatus().name(),
                c.getCashChangeVnd() == null ? null : String.valueOf(c.getCashChangeVnd()),
                payable ? c.getPaymentUrlSnapshot() : null,
                payable ? c.getPaymentExpiresAt().toString() : null
        );
    }

    private SettlementCharge findCharge(String lineId) {
        String idStr = lineId.trim();
        try {
            Long id = Long.parseLong(idStr);
            var opt = charges.findById(id);
            if (opt.isPresent()) return opt.get();
        } catch (NumberFormatException ignored) {}
        return charges.findByChargeReference(idStr)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    private SettlementFolioCollection findCollection(String collectionId) {
        String idStr = collectionId.trim();
        try {
            Long id = Long.parseLong(idStr);
            var opt = collections.findById(id);
            if (opt.isPresent()) return opt.get();
        } catch (NumberFormatException ignored) {}
        return collections.findByCollectionReference(idStr)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new WorkshopSettlementValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
        return value.trim();
    }

    private static String correlation(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? "CORR-" + UUID.randomUUID() : correlationId.trim();
    }
}

