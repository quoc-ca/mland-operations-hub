package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashReversalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.CashDayCloseStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Closes a branch's cash book once per Vietnam working day (003.3 T096, FR-036).
 *
 * <p>The system totals the branch's confirmed cash for that day, net of change returned, the closer enters
 * what was counted, and a difference carries a reason and waits for a Manager before the day is closed. Cash
 * taken after the close belongs to the next day, which {@code FolioCollectionService} applies when it records
 * the collection. Transfers and VNPay are reconciled in their own reports and are deliberately absent here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashDayCloseService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SettlementCashDayCloseRepository closes;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementBusinessAuditRepository audits;
    private final SettlementCashReversalRepository cashReversals;

    /** navigation shell and dashboard (014 R-3): cash closes whose difference waits for a Manager. */
    @Transactional(readOnly = true)
    public long countAwaitingConfirmation() {
        return closes.countByStatus(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION);
    }

    /** navigation shell and dashboard (014 R-3): the closes counted by {@link #countAwaitingConfirmation()}, oldest first. */
    @Transactional(readOnly = true)
    public java.util.List<SettlementCashDayClose> listAwaitingConfirmation() {
        return closes.findByStatusOrderByClosedAtAsc(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION);
    }

    /** What the system expects in the drawer for a branch and day, before anyone counts. */
    @Transactional(readOnly = true)
    public long expectedCash(Long branchId, LocalDate businessDay) {
        return cashOf(branchId, dayOrToday(businessDay));
    }

    /**
     * FR-036b: the entries behind {@link #expectedCash}, oldest first, read in one transaction so their sum is the
     * figure shown beside them.
     */
    @Transactional(readOnly = true)
    public List<CashEntry> cashEntries(Long branchId, LocalDate businessDay) {
        LocalDate day = dayOrToday(businessDay);
        Stream<CashEntry> collected = collections.findConfirmedCash(branchId, day).stream()
                .map(c -> new CashEntry("COLLECTION", c.getBookingReference(), c.getAmountVnd(),
                        c.getConfirmedAt() != null ? c.getConfirmedAt() : c.getCreatedAt()));
        Stream<CashEntry> reversed = cashReversals.findByBranchIdAndCashBusinessDay(branchId, day).stream()
                .map(r -> new CashEntry("REVERSAL", r.getTicketCode(), r.getAmountVnd(), r.getRecordedAt()));
        return Stream.concat(collected, reversed).sorted(Comparator.comparing(CashEntry::recordedAt)).toList();
    }

    /**
     * Closes the day. A difference is never closed silently: it needs a reason and leaves the close
     * {@code PENDING_MANAGER_CONFIRMATION} until a Manager confirms it.
     */
    @Transactional
    public SettlementCashDayClose close(Long branchId, LocalDate businessDay, long countedCashVnd, String reason,
                                        Long closerAccountId, String correlationReference) {
        if (branchId == null) {
            throw new WorkshopSettlementValidationException("BRANCH_REQUIRED");
        }
        if (countedCashVnd < 0) {
            throw new WorkshopSettlementValidationException("COUNTED_CASH_INVALID");
        }
        LocalDate day = dayOrToday(businessDay);
        if (day.isAfter(LocalDate.now(VIETNAM))) {
            throw new WorkshopSettlementValidationException("BUSINESS_DAY_IN_THE_FUTURE");
        }
        if (closes.findForUpdate(branchId, day).isPresent()) {
            throw new WorkshopSettlementConflictException("CASH_DAY_ALREADY_CLOSED");
        }
        long expected = cashOf(branchId, day);
        long difference = Math.subtractExact(countedCashVnd, expected);
        if (difference != 0 && (reason == null || reason.isBlank())) {
            throw new WorkshopSettlementValidationException("CASH_DIFFERENCE_REASON_REQUIRED");
        }
        SettlementCashDayClose close = closes.saveAndFlush(SettlementCashDayClose.builder()
                .closeReference("CASHCLOSE-" + UUID.randomUUID())
                .branchId(branchId)
                .businessDay(day)
                .expectedCashVnd(expected)
                .countedCashVnd(countedCashVnd)
                .differenceVnd(difference)
                .differenceReason(difference == 0 ? null : reason.trim())
                .status(difference == 0 ? CashDayCloseStatus.CLOSED
                        : CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION)
                .closedByAccountId(closerAccountId)
                .closedAt(Instant.now())
                .build());
        audit(close, difference == 0 ? "CASH_DAY_CLOSED" : "CASH_DAY_CLOSE_PENDING_MANAGER", closerAccountId,
                close.getDifferenceReason(), correlationReference);
        log.info("Cash close for branch {} on {}: expected {}, counted {}, difference {}", branchId, day, expected,
                countedCashVnd, difference);
        return close;
    }

    /** A Manager confirms the difference, which is what actually closes a day that did not balance. */
    @Transactional
    public SettlementCashDayClose confirmDifference(Long branchId, LocalDate businessDay, Long managerAccountId,
                                                    String correlationReference) {
        LocalDate day = dayOrToday(businessDay);
        SettlementCashDayClose close = closes.findForUpdate(branchId, day)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (close.getStatus() == CashDayCloseStatus.CLOSED) {
            return close;
        }
        close.setStatus(CashDayCloseStatus.CLOSED);
        close.setConfirmedByAccountId(managerAccountId);
        close.setConfirmedAt(Instant.now());
        SettlementCashDayClose saved = closes.saveAndFlush(close);
        audit(saved, "CASH_DAY_DIFFERENCE_CONFIRMED", managerAccountId, saved.getDifferenceReason(),
                correlationReference);
        return saved;
    }

    /** FR-052: Manager confirms difference by close id or close reference with required reason and Idempotency-Key. */
    @Transactional
    public SettlementCashDayClose confirmDifferenceById(String cashCloseId, String reason, Long managerAccountId,
                                                        String correlationReference) {
        if (cashCloseId == null || cashCloseId.isBlank()) {
            throw new WorkshopSettlementValidationException("CASH_CLOSE_ID_REQUIRED");
        }
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("DIFFERENCE_CONFIRMATION_REASON_REQUIRED");
        }
        SettlementCashDayClose close = null;
        try {
            Long id = Long.parseLong(cashCloseId.trim());
            close = closes.findById(id).orElse(null);
        } catch (NumberFormatException ignored) {}
        if (close == null) {
            close = closes.findByCloseReference(cashCloseId.trim())
                    .orElseThrow(WorkshopSettlementNotFoundException::new);
        }
        if (close.getStatus() == CashDayCloseStatus.CLOSED) {
            return close;
        }
        close.setStatus(CashDayCloseStatus.CLOSED);
        close.setConfirmedByAccountId(managerAccountId);
        close.setConfirmedAt(Instant.now());
        SettlementCashDayClose saved = closes.saveAndFlush(close);
        audit(saved, "CASH_DAY_DIFFERENCE_CONFIRMED", managerAccountId, reason.trim(), correlationReference);
        return saved;
    }

    @Transactional(readOnly = true)
    public SettlementCashDayClose find(Long branchId, LocalDate businessDay) {
        return closes.findByBranchIdAndBusinessDay(branchId, dayOrToday(businessDay))
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    /** FR-036: a branch's working day is closed once a close exists for it, confirmed or not. */
    @Transactional(readOnly = true)
    public boolean isClosed(Long branchId, LocalDate businessDay) {
        return branchId != null && businessDay != null
                && closes.findByBranchIdAndBusinessDay(branchId, businessDay).isPresent();
    }

    /** T120: a close by its reference, for an idempotent replay of {@code POST /workshop-cash-closes}. */
    @Transactional(readOnly = true)
    public SettlementCashDayClose findByReference(String closeReference) {
        return closes.findByCloseReference(closeReference).orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    /** FR-036: confirmed cash of the day net of change, plus the day's cash reversals (negative, T144). */
    private long cashOf(Long branchId, LocalDate day) {
        return Math.addExact(collections.sumConfirmedCash(branchId, day), cashReversals.sumForBranchDay(branchId, day));
    }

    /**
     * FR-036 cut-off: the working day a cash entry recorded now belongs to. Once the counted amount of today was
     * submitted (a close row exists, confirmed or not) it is the next day.
     */
    @Transactional(readOnly = true)
    public LocalDate currentCashDay(Long branchId) {
        LocalDate today = LocalDate.now(VIETNAM);
        return closes.findByBranchIdAndBusinessDay(branchId, today).isPresent() ? today.plusDays(1) : today;
    }

    static LocalDate dayOrToday(LocalDate businessDay) {
        return businessDay != null ? businessDay : LocalDate.now(VIETNAM);
    }

    private void audit(SettlementCashDayClose close, String action, Long actorAccountId, String reason,
                       String correlationReference) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_CASH_DAY_CLOSE")
                .aggregateReference(close.getCloseReference())
                .actionCode(action)
                .actorReference(actorAccountId == null ? "SYSTEM" : "account:" + actorAccountId)
                .correlationReference(correlationReference == null || correlationReference.isBlank()
                        ? "CORR-" + UUID.randomUUID() : correlationReference)
                .reasonReference(reason)
                .createdAt(Instant.now())
                .build());
    }
}
