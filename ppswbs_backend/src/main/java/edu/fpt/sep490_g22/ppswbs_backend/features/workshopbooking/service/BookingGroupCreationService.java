package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopCartQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingGroupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.GroupCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveGroupCapacityCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Confirms a quoted cart into one booking group (003.1 T092, FR-030..FR-032, FR-036).
 *
 * <p>Every line is held all or none: on refusal nothing is held, the failing cart positions are reported and
 * the cart stays editable (FR-031). The group then stores its lines with the hold reference of each session,
 * one ticket per person, and each ticket's immutable price snapshot. The group hold lasts 12 minutes and the
 * customer's payment deadline is 2 minutes earlier (FR-032); the payment itself is T093.
 *
 * <p>No Loyalty reservation is made yet: a cart with benefits is refused at quote time while the 006 cart
 * contract is pending, so every group created here has a zero discount and zero points.
 */
@Service
@RequiredArgsConstructor
public class BookingGroupCreationService {

    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopCartPricingService pricing;
    private final WorkshopOperationsAdapter operations;
    private final WorkshopCartQuoteRepository quotes;
    private final BookingGroupRepository groups;
    private final BookingGroupLineRepository lines;
    private final TicketBookingRepository tickets;
    private final TicketPriceSnapshotRepository snapshots;
    private final BookingIdempotencyService idempotency;
    private final WorkshopCartBenefitService benefits;
    private final GroupConfirmationService groupConfirmation;
    private final ObjectMapper mapper;

    /** The link expires 2 minutes before the hold so a late result never books a released seat (FR-032). */
    @Value("${workshop.booking.group-payment-buffer:PT2M}")
    private Duration paymentBuffer = Duration.ofMinutes(2);

    /** Authenticate before the controller may return a cached idempotent response. */
    public Long requireBuyer(String authorizationHeader, String quoteRef) {
        WorkshopCartQuote quote = quotes.findByQuoteRef(quoteRef)
                .orElseThrow(() -> new BookingNotFoundException("The cart quote was not found"));
        if (quote.getBuyerMemberAccountId() == null) {
            return null;
        }
        Long memberAccountId = authorization.requireMemberOwnership(authorizationHeader, null).accountId();
        if (!memberAccountId.equals(quote.getBuyerMemberAccountId())) {
            throw new BookingNotFoundException("The cart quote was not found");
        }
        return memberAccountId;
    }

    @Transactional
    public BookingGroupResponse create(CreateBookingGroupRequest request, Long buyerMemberAccountId,
                                       String correlationReference, BookingIdempotencyClaim claim) {
        if (request == null || request.quoteRef() == null) {
            throw new BookingValidationException("A cart quote reference is required");
        }
        WorkshopCartQuote quote = quotes.findForUpdate(request.quoteRef())
                .orElseThrow(() -> new BookingNotFoundException("The cart quote was not found"));
        if (quote.getBuyerMemberAccountId() != null
                && !quote.getBuyerMemberAccountId().equals(buyerMemberAccountId)) {
            throw new BookingNotFoundException("The cart quote was not found");
        }
        if (!Instant.now().isBefore(quote.getExpiresAt())) {
            throw new BookingCreationRejectedException("QUOTE_EXPIRED", "The cart quote has expired");
        }
        if (quote.getQuoteState() != CartQuoteState.OPEN) {
            throw new BookingCreationRejectedException("QUOTE_ALREADY_ACCEPTED",
                    "The cart quote has already been confirmed");
        }

        List<WorkshopCartQuoteService.StoredLine> stored = readLines(quote.getLinesJson());
        List<WorkshopCartPricingService.LineInput> inputs = stored.stream()
                .map(line -> new WorkshopCartPricingService.LineInput(line.packageId(),
                        WorkshopCartQuoteService.canonicalUuids(line.sessionReferences()), line.participantCount()))
                .toList();
        WorkshopCartPricingService.PricedCart cart = pricing.price(inputs);
        // The quote already holds 006's answer: the discount, the points and which tickets the voucher
        // covers. Re-applying it here is what the drift checks below are compared against.
        List<Integer> eligibleTickets = readEligibleTickets(quote.getEligibleTicketIndexesJson());
        if (quote.getVoucherDiscountVnd() > 0 || quote.getPointsUsed() > 0) {
            cart = cart.withAllocation(TicketBenefitAllocator.allocate(
                    benefits.allocatorInputs(cart, eligibleTickets), quote.getVoucherDiscountVnd(),
                    quote.getPointsUsed(), quote.getPointValueVnd()));
        }
        for (int index = 0; index < stored.size(); index++) {
            if (stored.get(index).pricePerParticipantVnd() != cart.lines().get(index).pricePerParticipantVnd()) {
                throw new BookingCreationRejectedException("QUOTE_CHANGED",
                        "A package price in the cart has changed since the quote");
            }
        }
        if (cart.allocation().groupCollectionVnd() != quote.getGroupCollectionVnd()
                || cart.allocation().paymentMode() != quote.getPaymentMode()) {
            throw new BookingCreationRejectedException("QUOTE_CHANGED",
                    "The amount to collect has changed since the quote");
        }

        String groupCode = "GRP-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase(Locale.ROOT);
        List<ReserveGroupCapacityCommand.Line> commandLines = new ArrayList<>();
        for (WorkshopCartPricingService.PricedLine line : cart.lines()) {
            commandLines.add(new ReserveGroupCapacityCommand.Line(lineReference(groupCode, line.lineIndex()),
                    Long.toString(line.packageId()),
                    line.sessions().stream().map(WorkshopCartPricingService.PricedSession::reference).toList(),
                    line.participantCount()));
        }
        GroupCapacityOutcome outcome = operations.reserveGroupCapacity(groupCode, commandLines,
                "CREATE-" + groupCode, correlationReference);
        requireHeld(outcome, cart.lines().size());

        // One Loyalty reservation per group, inside this transaction, for the cart exactly as it was quoted.
        AcceptedBenefitRef acceptedBenefits = null;
        if (quote.getBenefitSelectionJson() != null) {
            acceptedBenefits = benefits.accept(quote.getBuyerMemberAccountId(), cart,
                    readSelection(quote.getBenefitSelectionJson()), groupCode, quote.getQuoteRef(),
                    quote.getTermsDigest(), Instant.now(), outcome.expiresAt());
            long acceptedDiscount = acceptedBenefits.programDiscount().longValueExact();
            long acceptedPoints = acceptedBenefits.pointsHeld().longValueExact();
            if (acceptedDiscount != quote.getVoucherDiscountVnd() || acceptedPoints != quote.getPointsUsed()) {
                throw new BookingCreationRejectedException("QUOTE_CHANGED",
                        "The benefit of this cart changed since the quote");
            }
        }

        BookingGroup group = groups.save(BookingGroup.builder()
                .groupCode(groupCode)
                .buyerMemberAccountId(quote.getBuyerMemberAccountId())
                .contactEmail(request.contactEmail().trim())
                .contactPhone(request.contactPhone().trim())
                .groupStatus(BookingStatus.PENDING_PAYMENT)
                .paymentMode(cart.allocation().paymentMode())
                .groupCollectionVnd(cart.allocation().groupCollectionVnd())
                .holdExpiresAt(outcome.expiresAt())
                .paymentDeadlineAt(outcome.expiresAt().minus(paymentBuffer))
                .voucherUseReference(acceptedBenefits == null ? null
                        : Long.toString(acceptedBenefits.acceptedBenefitId()))
                .loyaltyPolicyRevision(acceptedBenefits == null ? null : acceptedBenefits.policyRevisionNo())
                .build());

        List<BookingGroupResponse.GroupLine> responseLines = new ArrayList<>();
        List<BookingGroupResponse.GroupTicket> responseTickets = new ArrayList<>();
        List<TicketBooking> createdTickets = new ArrayList<>();
        List<TicketBenefitAllocator.TicketResult> allocations = new ArrayList<>();
        for (WorkshopCartPricingService.PricedLine line : cart.lines()) {
            List<UUID> holdReferences = outcome.lineHoldReferences().get(line.lineIndex());
            if (holdReferences.size() != line.sessions().size()) {
                throw new BookingConflictException("Workshop Operations returned an incomplete group hold outcome");
            }
            BookingGroupLine entity = BookingGroupLine.builder()
                    .group(group).lineIndex(line.lineIndex())
                    .lineReference(lineReference(groupCode, line.lineIndex()))
                    .packageId(line.packageId()).packageNameSnapshot(line.packageName())
                    .pricePerParticipantVnd(line.pricePerParticipantVnd())
                    .ticketCount(line.participantCount())
                    .build();
            for (int i = 0; i < line.sessions().size(); i++) {
                WorkshopCartPricingService.PricedSession session = line.sessions().get(i);
                entity.addSession(BookingGroupLineSession.builder()
                        .ordinal(session.ordinal())
                        .sessionReference(session.reference().toString())
                        .locationReference(session.locationReference())
                        .scheduledStart(session.scheduledStart())
                        .holdReference(holdReferences.get(i).toString())
                        .holdStatus(LineHoldStatus.RESERVED)
                        .build());
            }
            lines.save(entity);
            responseLines.add(new BookingGroupResponse.GroupLine(line.lineIndex(), line.packageId(),
                    line.packageName(), line.pricePerParticipantVnd(), line.participantCount(),
                    line.sessions().stream().map(WorkshopCartPricingService.PricedSession::reference).toList()));

            for (int ordinal = 1; ordinal <= line.participantCount(); ordinal++) {
                TicketBenefitAllocator.TicketResult allocated = cart.ticket(line.lineIndex(), ordinal);
                TicketBooking ticket = tickets.save(TicketBooking.builder()
                        .ticketCode(ticketCode(groupCode, line.lineIndex(), ordinal))
                        .group(group).lineIndex(line.lineIndex()).participantOrdinal(ordinal)
                        .packageId(line.packageId()).packageNameSnapshot(line.packageName())
                        .ticketStatus(BookingStatus.PENDING_PAYMENT).financialStatus(PaymentStatus.UNPAID)
                        .customerChangesUsed(0)
                        .build());
                snapshots.save(TicketPriceSnapshot.builder()
                        .ticket(ticket)
                        .packagePriceVnd(allocated.packagePriceVnd())
                        .voucherDiscountVnd(allocated.voucherDiscountVnd())
                        .pointsUsed(allocated.pointsUsed())
                        .pointDiscountVnd(allocated.pointDiscountVnd())
                        .finalPriceVnd(allocated.finalPriceVnd())
                        .collectedAtBookingVnd(allocated.collectedAtBookingVnd())
                        .collectionKind(allocated.collectionKind())
                        .remainingVnd(allocated.remainingVnd())
                        .nonRefundableDisclosed(true)
                        .build());
                createdTickets.add(ticket);
                allocations.add(allocated);
            }
        }

        quote.setQuoteState(CartQuoteState.ACCEPTED);
        quote.setAcceptedGroupCode(groupCode);
        quotes.save(quote);

        // FR-039: a group that collects nothing at booking needs no gateway, so it is confirmed here and now.
        if (group.getPaymentMode() != GroupPaymentMode.VNPAY) {
            groupConfirmation.confirmInternalGroup(groupCode, "CONFIRM-" + groupCode, correlationReference);
        }

        for (int i = 0; i < createdTickets.size(); i++) {
            TicketBooking ticket = createdTickets.get(i);
            TicketBenefitAllocator.TicketResult allocated = allocations.get(i);
            responseTickets.add(new BookingGroupResponse.GroupTicket(ticket.getTicketCode(), ticket.getLineIndex(),
                    ticket.getParticipantOrdinal(), ticket.getPackageId(), ticket.getPackageNameSnapshot(),
                    ticket.getTicketStatus().name(), ticket.getFinancialStatus().name(), allocated.finalPriceVnd(),
                    allocated.collectedAtBookingVnd(), allocated.collectionKind().name(), allocated.remainingVnd()));
        }
        BookingGroupResponse response = new BookingGroupResponse(groupCode, group.getGroupStatus().name(),
                group.getPaymentMode().name(), group.getGroupCollectionVnd(), group.getHoldExpiresAt(),
                group.getPaymentDeadlineAt(), List.copyOf(responseLines), List.copyOf(responseTickets));
        if (claim != null) {
            try {
                idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), 201,
                        mapper.writeValueAsString(response));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("The booking group response could not be stored", ex);
            }
        }
        return response;
    }

    private void requireHeld(GroupCapacityOutcome outcome, int expectedLines) {
        if (outcome == null) {
            throw new BookingConflictException("Workshop Operations did not answer the group hold");
        }
        if (outcome.status() == OutcomeStatus.IN_PROGRESS) {
            throw new BookingConflictException("The cart is already being held; retry with the same key");
        }
        if (outcome.status() != OutcomeStatus.ACCEPTED) {
            RejectionReason reason = outcome.reason();
            String code = reason == RejectionReason.INSUFFICIENT_CAPACITY ? "INSUFFICIENT_CAPACITY"
                    : reason == RejectionReason.PACKAGE_NOT_PUBLISHED ? "PACKAGE_NOT_PUBLISHED"
                    : reason == RejectionReason.SALE_NOT_ELIGIBLE ? "SALE_NOT_ELIGIBLE"
                    : "PACKAGE_SELECTION_INVALID";
            throw new CartHoldRejectedException(code,
                    "No seat was held; the cart is unchanged and the listed lines need attention",
                    outcome.failingLines());
        }
        if (outcome.expiresAt() == null || outcome.lineHoldReferences().size() != expectedLines) {
            throw new BookingConflictException("Workshop Operations returned an incomplete group hold outcome");
        }
    }

    private List<Integer> readEligibleTickets(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("The stored voucher scope could not be read", ex);
        }
    }

    private edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection readSelection(
            String json) {
        try {
            return mapper.readValue(json,
                    edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("The stored benefit selection could not be read", ex);
        }
    }

    private List<WorkshopCartQuoteService.StoredLine> readLines(String json) {
        try {
            return mapper.readValue(json, WorkshopCartQuoteService.STORED_LINES);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("The stored cart quote could not be read", ex);
        }
    }

    private static String lineReference(String groupCode, int lineIndex) {
        return groupCode + "-L" + lineIndex;
    }

    private static String ticketCode(String groupCode, int lineIndex, int participantOrdinal) {
        return groupCode + "-L" + lineIndex + "-T" + participantOrdinal;
    }
}
