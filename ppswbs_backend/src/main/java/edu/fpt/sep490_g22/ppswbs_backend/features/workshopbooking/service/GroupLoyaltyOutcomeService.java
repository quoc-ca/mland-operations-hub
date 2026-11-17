package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopCartQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopCartQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Tells 006 what became of a booking group's one voucher use and points (003.1 FR-039, 006 T098).
 *
 * <p>One group holds one reservation, so one outcome settles or releases it: the group confirming consumes
 * the held components, and the group's hold running out releases them and closes the earn. 003.1 is the owner
 * of that decision; 006 never reads a gateway callback. Both calls are keyed on the group, so a replayed
 * confirmation or a second expiry sweep reaches the same stored result instead of consuming twice.
 *
 * <p>A group with no reservation — a cart that got no automatic discount and asked for nothing — is skipped, since
 * there is no component to settle. A Guest group with an automatic discount is reserved (006 D-014) and is settled
 * or released like a Member's.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupLoyaltyOutcomeService {

    /** The reference of an internal confirmation, which is a settle key and not payment evidence. */
    static final String INTERNAL_EVIDENCE_PREFIX = "INTERNAL-";

    private final WorkshopCartQuoteRepository quotes;
    private final LoyaltyFacade loyalty;

    /**
     * The group is confirmed, so its held voucher use and points are consumed once.
     *
     * @param evidenceReference the verified gateway evidence, or an internal reference for a group that
     *                          collected nothing at booking
     */
    public void settleConfirmedGroup(BookingGroup group, String evidenceReference) {
        WorkshopCartQuote quote = reservationOf(group).orElse(null);
        if (quote == null) {
            return;
        }
        String evidence = evidenceReference == null || evidenceReference.isBlank()
                ? INTERNAL_EVIDENCE_PREFIX + group.getGroupCode() : evidenceReference;
        loyalty.settleAcceptedBenefits(new AuthoritativeOutcome(SourceKind.WORKSHOP, group.getGroupCode(),
                "workshopbooking", "group-confirmed-" + group.getGroupCode(), version(group),
                OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, Instant.now(), group.getHoldExpiresAt(),
                quote.getQuoteRef(), true, evidence));
        log.info("Settled the Loyalty components of booking group {}", group.getGroupCode());
    }

    /** The hold ran out with nothing confirmed, so the components go back and the earn is closed. */
    public void releaseExpiredGroup(BookingGroup group) {
        WorkshopCartQuote quote = reservationOf(group).orElse(null);
        if (quote == null) {
            return;
        }
        Instant now = Instant.now();
        String key = "group-expired-" + group.getGroupCode();
        String evidence = INTERNAL_EVIDENCE_PREFIX + group.getGroupCode();
        loyalty.releaseHeldBenefits(new AuthoritativeOutcome(SourceKind.WORKSHOP, group.getGroupCode(),
                "workshopbooking", key, version(group), OutcomeKind.UNPAID_EXPIRY_CONFIRMED, now,
                group.getHoldExpiresAt(), quote.getQuoteRef(), true, evidence));
        loyalty.finishIneligibleEarn(new AuthoritativeOutcome(SourceKind.WORKSHOP, group.getGroupCode(),
                "workshopbooking", key + "-earn", version(group) + 1, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, now,
                group.getHoldExpiresAt(), quote.getQuoteRef(), true, evidence));
        log.info("Released the Loyalty components of expired booking group {}", group.getGroupCode());
    }

    /** The quote behind this group, only when a reservation was actually made for it. */
    private Optional<WorkshopCartQuote> reservationOf(BookingGroup group) {
        if (group == null || group.getVoucherUseReference() == null) {
            return Optional.empty();
        }
        return quotes.findByAcceptedGroupCode(group.getGroupCode());
    }

    private static long version(BookingGroup group) {
        return group.getVersion() == null ? 0L : group.getVersion();
    }
}
