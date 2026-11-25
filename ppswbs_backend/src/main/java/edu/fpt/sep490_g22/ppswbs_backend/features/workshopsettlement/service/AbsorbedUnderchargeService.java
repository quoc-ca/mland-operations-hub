package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementAbsorbedUnderchargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.AbsorbedUnderchargeDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.AbsorbedUnderchargeListDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAbsorbedUndercharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Absorbed undercharges of FINALIZED ticket folios (003.3 FR-056/FR-057, T135 and the T156 cap).
 *
 * <p>Documentation only: a record collects nothing and changes no folio, line, balance, Loyalty earn, cash or cash
 * close, and names no employee. It is immutable except the one void; a correct amount is a new record. Manager only,
 * reason required, {@code Idempotency-Key} required, audited on record and on void.
 *
 * <p>T156 (CHK009): a record may not exceed the ticket's folio total (price plus add-ons and variable charges, voided
 * lines excluded), and the ACTIVE records of one ticket together may not exceed it either. The check runs under the
 * folio lock so two concurrent records cannot both pass; a voided record frees its amount.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbsorbedUnderchargeService {

    static final String CAP_EXCEEDED = "UNDERCHARGE_EXCEEDS_FOLIO_TOTAL";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkshopSettlementAuthorizationService authorization;
    private final WorkshopBookingSettlementAdapter booking;
    private final SettlementFinalBillRepository bills;
    private final SettlementAbsorbedUnderchargeRepository undercharges;
    private final FolioBalanceService balances;
    private final SettlementIdempotencyService idempotency;
    private final SettlementBusinessAuditRepository audits;

    @Transactional
    public AbsorbedUnderchargeDto record(String ticketCode, String idempotencyKey, String correlationId,
                                         long amountVnd, String reason, CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        String code = required(ticketCode, "ticketCode");
        if (amountVnd <= 0) {
            throw new WorkshopSettlementValidationException("AMOUNT_MUST_BE_POSITIVE");
        }
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        TicketFolioContext context = booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        String key = idempotencyKey.trim();
        String op = "UNDERCHARGE:" + code + ":" + key;
        String fp = "UNDERCHARGE:" + code + ":" + amountVnd + ":" + reason.trim();
        var outcome = idempotency.execute(op, fp,
                () -> create(folio, context, key, amountVnd, reason.trim(), manager.accountId(), correlation(correlationId)));
        return toDto(find(outcome.outcomeReference()), folio.getGroupReference());
    }

    @Transactional
    public AbsorbedUnderchargeDto voidRecord(String ticketCode, String underchargeReference, String idempotencyKey,
                                             String correlationId, String reason, CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        String code = required(ticketCode, "ticketCode");
        String reference = required(underchargeReference, "underchargeReference");
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementAbsorbedUndercharge record = undercharges.findByUnderchargeReferenceForUpdate(reference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!code.equals(record.getTicketCode())) {
            throw new WorkshopSettlementNotFoundException();
        }
        String key = idempotencyKey.trim();
        String op = "UNDERCHARGE_VOID:" + code + ":" + reference + ":" + key;
        String fp = "UNDERCHARGE_VOID:" + code + ":" + reference + ":" + reason.trim();
        var outcome = idempotency.execute(op, fp, () -> {
            if (record.isVoided()) {
                throw new WorkshopSettlementConflictException("UNDERCHARGE_ALREADY_VOIDED");
            }
            Instant now = Instant.now();
            record.setStatus(SettlementAbsorbedUndercharge.VOIDED);
            record.setVoidReason(reason.trim());
            record.setVoidedByAccountId(manager.accountId());
            record.setVoidedAt(now);
            record.setVoidIdempotencyKey(key);
            undercharges.saveAndFlush(record);
            audit(record, "ABSORBED_UNDERCHARGE_VOIDED", manager.accountId(), reason.trim(), correlation(correlationId));
            return record.getUnderchargeReference();
        });
        return toDto(find(outcome.outcomeReference()), folio.getGroupReference());
    }

    /** Branch and period list: records recorded on Vietnam days from..to inclusive; the total sums ACTIVE only. */
    // Not @Transactional(readOnly): a refused actor is audited by the authorization service, which must be able to write.
    public AbsorbedUnderchargeListDto list(Long locationId, LocalDate from, LocalDate to, CurrentAccountActor actor) {
        authorization.requireManager(actor);
        if (locationId == null || from == null || to == null || from.isAfter(to)) {
            throw new ConstraintViolationException("locationId, from and to are required and from may not be after to",
                    Set.of());
        }
        Instant start = from.atStartOfDay(VIETNAM).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(VIETNAM).toInstant();
        List<SettlementAbsorbedUndercharge> records = undercharges.findForPeriod(locationId, start, end);
        long total = records.stream().filter(r -> !r.isVoided()).mapToLong(SettlementAbsorbedUndercharge::getAmountVnd).sum();
        return new AbsorbedUnderchargeListDto(locationId, from, to, String.valueOf(total),
                records.stream().map(r -> toDto(r, groupOf(r))).toList());
    }

    private String create(SettlementFinalBill folio, TicketFolioContext context, String key, long amountVnd,
                          String reason, Long managerAccountId, String correlation) {
        if (!SettlementFinalBillLifecycleStatus.FINALIZED.name().equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FOLIO_NOT_FINALIZED");
        }
        // T156: the folio lock is already held, so the ACTIVE sum cannot change between this check and the insert.
        long folioTotal = Math.addExact(folio.ticketPriceVnd(),
                balances.confirmedCharges(folio.getBillReference()));
        long alreadyAbsorbed = undercharges.sumActiveByTicketCode(folio.getBookingReference());
        if (amountVnd > folioTotal || Math.addExact(alreadyAbsorbed, amountVnd) > folioTotal) {
            throw new SettlementUnprocessableException(CAP_EXCEEDED);
        }
        Instant now = Instant.now();
        SettlementAbsorbedUndercharge saved = undercharges.saveAndFlush(SettlementAbsorbedUndercharge.builder()
                .underchargeReference("UNDERCHARGE-" + UUID.randomUUID())
                .ticketCode(folio.getBookingReference())
                .billReference(folio.getBillReference())
                .locationReference(context.locationReference())
                .amountVnd(amountVnd)
                .currencyCode("VND")
                .reason(reason)
                .recordedByAccountId(managerAccountId)
                .recordedAt(now)
                .idempotencyKey(key)
                .status(SettlementAbsorbedUndercharge.ACTIVE)
                .createdAt(now)
                .build());
        audit(saved, "ABSORBED_UNDERCHARGE_RECORDED", managerAccountId, reason, correlation);
        return saved.getUnderchargeReference();
    }

    private void audit(SettlementAbsorbedUndercharge record, String action, Long actorAccountId, String reason,
                       String correlation) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_ABSORBED_UNDERCHARGE")
                .aggregateReference(record.getUnderchargeReference())
                .actionCode(action)
                .actorReference("account:" + actorAccountId)
                .correlationReference(correlation)
                .reasonReference(reason)
                .createdAt(Instant.now())
                .build());
    }

    private SettlementAbsorbedUndercharge find(String reference) {
        return undercharges.findByUnderchargeReference(reference).orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    private String groupOf(SettlementAbsorbedUndercharge record) {
        return bills.findByBookingReference(record.getTicketCode()).map(SettlementFinalBill::getGroupReference)
                .orElse(null);
    }

    private static AbsorbedUnderchargeDto toDto(SettlementAbsorbedUndercharge r, String groupCode) {
        return new AbsorbedUnderchargeDto(r.getUnderchargeReference(), r.getTicketCode(), groupCode,
                r.getLocationReference(), String.valueOf(r.getAmountVnd()), r.getReason(), r.getStatus(),
                String.valueOf(r.getRecordedByAccountId()), r.getRecordedAt(), r.getVoidReason(),
                r.getVoidedByAccountId() == null ? null : String.valueOf(r.getVoidedByAccountId()), r.getVoidedAt());
    }

    private static String correlation(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? "CORR-" + UUID.randomUUID() : correlationId.trim();
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
        return value.trim();
    }
}
