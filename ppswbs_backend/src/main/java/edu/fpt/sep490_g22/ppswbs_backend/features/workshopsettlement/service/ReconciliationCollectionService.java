package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.Method;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.ReconciliationCollectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.State;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Manager's read-only reconciliation list of transfer, VNPay and online booking-time money of one branch and one
 * Vietnam working day (003.3 FR-056, T133). It writes nothing and records no "reconciled" mark.
 *
 * <p>Row sources and their working day:
 * <ul>
 *   <li>folio collections ({@code BANK_TRANSFER}, {@code VNPAY}) by {@code branch_id}: a confirmed one belongs to the
 *       day of {@code confirmed_at}, a pending or voided one to the day of its creation;</li>
 *   <li>{@code EXCESS} rows from {@code settlement_evidence_conclusions} with conclusion {@code FOLIO_VNPAY_EXCESS}:
 *       the day of the evidence time, the branch resolved through the 003.1 ticket folio context (such rows are rare,
 *       so one lookup per row in the day is acceptable);</li>
 *   <li>{@code BOOKING_TIME} rows from the read-only 003.1 operation, one row per payment; a failure answers
 *       {@link ReconciliationSourceUnavailableException} for the whole list, never a partial one.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationCollectionService {

    static final String FOLIO_VNPAY_EXCESS = "FOLIO_VNPAY_EXCESS";
    static final String LATE_PAYMENT_NOTE = "late payment under review";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementFinalBillRepository bills;
    private final SettlementRefundRequestRepository refunds;
    private final SettlementEvidenceConclusionRepository evidence;
    private final WorkshopBookingSettlementAdapter booking;

    // Not @Transactional(readOnly): a refused actor is audited by the authorization service, which must be able to write.
    public List<ReconciliationCollectionDto> list(Long locationId, LocalDate workingDate, Method method, State state,
                                                  CurrentAccountActor actor) {
        authorization.requireManager(actor);
        if (locationId == null || workingDate == null) {
            throw new WorkshopSettlementValidationException("LOCATION_AND_WORKING_DATE_REQUIRED");
        }
        Instant from = workingDate.atStartOfDay(VIETNAM).toInstant();
        Instant to = workingDate.plusDays(1).atStartOfDay(VIETNAM).toInstant();

        List<ReconciliationCollectionDto> rows = new ArrayList<>();
        Set<FolioCollectionMethod> methods = foliomethods(method);
        if (!methods.isEmpty() && state != State.EXCESS) {
            for (SettlementFolioCollection collection : collections.findForReconciliation(locationId, methods,
                    FolioCollectionStatus.CONFIRMED,
                    List.of(FolioCollectionStatus.PENDING_VERIFICATION, FolioCollectionStatus.VOIDED), from, to)) {
                ReconciliationCollectionDto row = toRow(collection, locationId);
                if (state == null || row.state() == state) rows.add(row);
            }
        }
        if ((method == null || method == Method.VNPAY) && (state == null || state == State.EXCESS)) {
            rows.addAll(excessRows(locationId, from, to));
        }
        if ((method == null || method == Method.BOOKING_TIME) && (state == null || state == State.CONFIRMED
                || state == State.PENDING_VERIFICATION)) {
            for (ReconciliationCollectionDto row : bookingTimeRows(locationId, workingDate)) {
                if (state == null || row.state() == state) rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(ReconciliationCollectionDto::createdAt));
        return rows;
    }

    private static Set<FolioCollectionMethod> foliomethods(Method method) {
        if (method == null) return EnumSet.of(FolioCollectionMethod.BANK_TRANSFER, FolioCollectionMethod.VNPAY);
        return switch (method) {
            case BANK_TRANSFER -> EnumSet.of(FolioCollectionMethod.BANK_TRANSFER);
            case VNPAY -> EnumSet.of(FolioCollectionMethod.VNPAY);
            case BOOKING_TIME -> EnumSet.noneOf(FolioCollectionMethod.class);
        };
    }

    private ReconciliationCollectionDto toRow(SettlementFolioCollection c, Long locationId) {
        String group = bills.findByBookingReference(c.getBookingReference())
                .map(bill -> bill.getGroupReference()).orElse(null);
        String refund = c.getExcessVnd() > 0
                ? refunds.findByReasonAndSourceReference(RefundReason.EXCESS_OR_DUPLICATE_MONEY,
                        c.getCollectionReference()).map(SettlementRefundRequest::getRequestReference).orElse(null)
                : null;
        State state = switch (c.getStatus()) {
            case CONFIRMED -> State.CONFIRMED;
            case VOIDED -> State.VOIDED;
            default -> State.PENDING_VERIFICATION;
        };
        return new ReconciliationCollectionDto(c.getCollectionReference(), c.getCreatedAt(), c.getBookingReference(),
                List.of(c.getBookingReference()), group, String.valueOf(c.getAmountVnd()),
                c.getExcessVnd() > 0 ? String.valueOf(c.getExcessVnd()) : null, refund,
                c.getMethod() == FolioCollectionMethod.VNPAY ? Method.VNPAY : Method.BANK_TRANSFER, state,
                c.getExternalReference(),
                c.getConfirmedByAccountId() == null ? null : String.valueOf(c.getConfirmedByAccountId()),
                List.of(String.valueOf(locationId)), c.getConfirmedAt(), null);
    }

    private List<ReconciliationCollectionDto> excessRows(Long locationId, Instant from, Instant to) {
        List<ReconciliationCollectionDto> rows = new ArrayList<>();
        for (SettlementEvidenceConclusion item : evidence
                .findByConclusionAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        FOLIO_VNPAY_EXCESS, from, to)) {
            Optional<TicketFolioContext> context;
            try {
                context = booking.getTicketFolioContext(item.getBookingReference());
            } catch (RuntimeException e) {
                log.warn("Ticket {} of an EXCESS row could not be resolved: {}", item.getBookingReference(),
                        e.toString());
                throw new ReconciliationSourceUnavailableException(e);
            }
            if (context == null || context.isEmpty() || !locationId.equals(context.get().locationReference())) {
                continue;
            }
            rows.add(new ReconciliationCollectionDto(null, item.getCreatedAt(), item.getBookingReference(),
                    List.of(item.getBookingReference()), context.get().groupCode(),
                    String.valueOf(item.getAmountVnd() == null ? 0L : item.getAmountVnd()), null, null, Method.VNPAY,
                    State.EXCESS, item.getProviderTransactionReference(), null, List.of(String.valueOf(locationId)),
                    null, null));
        }
        return rows;
    }

    private List<ReconciliationCollectionDto> bookingTimeRows(Long locationId, LocalDate workingDate) {
        List<BookingTimePayment> payments;
        try {
            payments = booking.listBookingTimePayments(locationId, workingDate);
        } catch (RuntimeException e) {
            log.warn("003.1 booking-time payments of location {} on {} are unavailable: {}", locationId, workingDate,
                    e.toString());
            throw new ReconciliationSourceUnavailableException(e);
        }
        List<ReconciliationCollectionDto> rows = new ArrayList<>();
        for (BookingTimePayment payment : payments == null ? List.<BookingTimePayment>of() : payments) {
            boolean confirmed = payment.verificationStatus() == BookingTimePaymentStatus.CONFIRMED;
            rows.add(new ReconciliationCollectionDto(null, payment.createdAt(),
                    payment.ticketCodes().size() == 1 ? payment.ticketCodes().get(0) : null, payment.ticketCodes(),
                    payment.bookingGroupCode(), String.valueOf(payment.amountVnd()), null, null, Method.BOOKING_TIME,
                    confirmed ? State.CONFIRMED : State.PENDING_VERIFICATION, payment.merchantReference(), null,
                    payment.locationIds().stream().map(String::valueOf).toList(),
                    confirmed ? payment.confirmedAt() : null,
                    payment.verificationStatus() == BookingTimePaymentStatus.LATE_PENDING_REVIEW
                            ? LATE_PAYMENT_NOTE : null));
        }
        return rows;
    }
}
