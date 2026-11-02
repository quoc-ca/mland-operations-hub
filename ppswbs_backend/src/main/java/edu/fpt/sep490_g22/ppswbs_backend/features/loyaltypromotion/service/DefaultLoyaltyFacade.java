package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * The implementation behind {@link LoyaltyFacade} (T053). Every mutation is MANDATORY: it joins the source owner's
 * transaction and is refused outside one (never REQUIRES_NEW, never over HTTP). Lock timeouts and deadlocks are
 * reported as the typed, retryable {@code RETRYABLE_CONTENTION}; the owner's transaction is rolled back with them.
 * So is a concurrent accept of the same source that committed first (unique source key): the owner's retry then
 * returns that accept's snapshot.
 * Only the owner of a transaction calls these operations with its own source reference; a result lookup returns that
 * transaction only.
 */
@Service
public class DefaultLoyaltyFacade implements LoyaltyFacade {

    private final LoyaltyQuoteService quotes;
    private final LoyaltyReservationService reservations;
    private final LoyaltySettlementService settlements;
    private final LoyaltyEarnService earn;
    private final LoyaltyReleaseService releases;
    private final LoyaltyRestorationService restorations;
    private final AcceptedBenefitRepository benefits;

    public DefaultLoyaltyFacade(LoyaltyQuoteService quotes, LoyaltyReservationService reservations,
                                LoyaltySettlementService settlements, LoyaltyEarnService earn,
                                LoyaltyReleaseService releases, LoyaltyRestorationService restorations,
                                AcceptedBenefitRepository benefits) {
        this.quotes = quotes;
        this.reservations = reservations;
        this.settlements = settlements;
        this.earn = earn;
        this.releases = releases;
        this.restorations = restorations;
        this.benefits = benefits;
    }

    @Override
    public BenefitPreview previewBenefits(TrustedSourceContext context, BenefitSelection selection) {
        return quotes.preview(context, selection);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AcceptedBenefitRef reserveAcceptedTransaction(AcceptedSourceContext context, BenefitSelection selection,
                                                         String sourceCommandKey) {
        try {
            return contended(() -> reservations.reserve(context, selection, sourceCommandKey));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateSource(e)) {
                throw new LoyaltyException(LoyaltyErrorCode.RETRYABLE_CONTENTION,
                        "The system is busy. Retry with the same request.");
            }
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult settleAcceptedBenefits(AuthoritativeOutcome outcome) {
        return contended(() -> settlements.settleAcceptedBenefits(outcome));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult grantEarnedPoints(AuthoritativeOutcome outcome) {
        return contended(() -> earn.grantEarnedPoints(outcome));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult releaseHeldBenefits(AuthoritativeOutcome outcome) {
        return contended(() -> releases.releaseHeldBenefits(outcome));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult restoreCancelledWorkshopBenefits(AuthoritativeOutcome outcome) {
        return contended(() -> restorations.restoreCancelledWorkshopBenefits(outcome));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult finishIneligibleEarn(AuthoritativeOutcome outcome) {
        return contended(() -> earn.finishIneligibleEarn(outcome));
    }

    @Override
    @Transactional(readOnly = true)
    public BenefitResult findBenefitResult(SourceKind sourceKind, String sourceRef) {
        return benefits.findBySourceKindAndSourceRef(sourceKind, sourceRef).map(BenefitEffectSupport::result)
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "No accepted benefit exists for this source transaction."));
    }

    private static boolean isDuplicateSource(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null
                && violation.getConstraintName().endsWith("uk_loyalty_benefit_source");
    }

    private static <T> T contended(Supplier<T> action) {
        try {
            return action.get();
        } catch (PessimisticLockingFailureException | QueryTimeoutException e) {
            throw new LoyaltyException(LoyaltyErrorCode.RETRYABLE_CONTENTION,
                    "The system is busy. Retry with the same request.");
        }
    }
}
