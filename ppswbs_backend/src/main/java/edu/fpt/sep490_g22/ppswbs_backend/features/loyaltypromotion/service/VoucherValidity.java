package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * FR-035 (Session 2026-11-21, T118): a cancellation gives a used voucher back only while the voucher is still valid
 * when the cancellation is decided, like the large marketplaces do. A voucher whose accepted revision window has
 * ended stays used and is never extended; the points used with it still come back. One rule for every cancellation
 * path: the whole booking (T023), a ticket cancelled validly or by Mland (D-012) and the 003.3 call of T110.
 */
@Component
public class VoucherValidity {

    private final PromotionRevisionRepository revisions;

    public VoucherValidity(PromotionRevisionRepository revisions) {
        this.revisions = revisions;
    }

    /** True when the voucher is CONSUMED and its accepted revision window had ended at {@code decidedAt}. */
    public boolean consumedAndEnded(AcceptedBenefit benefit, Instant decidedAt) {
        if (benefit.getVoucherState() != ComponentState.CONSUMED || benefit.getRevisionId() == null) {
            return false;
        }
        return revisions.findById(benefit.getRevisionId())
                .map(revision -> !decidedAt.isBefore(revision.getEndAt()))
                .orElse(false);
    }
}
