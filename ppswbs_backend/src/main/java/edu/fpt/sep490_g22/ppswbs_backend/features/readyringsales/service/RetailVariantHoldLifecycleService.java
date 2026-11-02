package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Mutates FE-08 holds only while {@link RetailAvailabilityGuardService#withGuard} is active on this thread.
 * Checkout/payment orchestration will call this service only from an FE-02 locked catalogue context.
 */
@Service
@RequiredArgsConstructor
public class RetailVariantHoldLifecycleService {

    private final RetailVariantHoldRepository holds;
    private final RetailAvailabilityGuardService availability;

    @Transactional(propagation = Propagation.MANDATORY)
    public RetailVariantHold createHeld(GroupedCheckout checkout, Long variantId, int quantity, Instant expiresAt) {
        if (checkout == null || checkout.getId() == null || variantId == null || variantId <= 0
            || quantity < 1 || quantity > 99 || expiresAt == null) {
            throw new RetailConflictException("Invalid retail variant hold");
        }
        availability.touchLockedGuard(variantId);
        if (holds.findByGroupedCheckoutIdAndVariantId(checkout.getId(), variantId).isPresent()) {
            throw new RetailConflictException("A variant hold already exists for this checkout");
        }
        return holds.save(new RetailVariantHold(checkout, variantId, quantity, expiresAt));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void settle(RetailVariantHold hold) {
        transition(hold, RetailVariantHoldStatus.SETTLED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(RetailVariantHold hold) {
        transition(hold, RetailVariantHoldStatus.RELEASED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expire(RetailVariantHold hold) {
        transition(hold, RetailVariantHoldStatus.EXPIRED);
    }

    private void transition(RetailVariantHold hold, RetailVariantHoldStatus target) {
        if (hold == null || hold.getStatus() != RetailVariantHoldStatus.HELD) {
            throw new RetailConflictException("Only a held variant quantity can change state");
        }
        availability.touchLockedGuard(hold.getVariantId());
        switch (target) {
            case SETTLED -> hold.settle();
            case RELEASED -> hold.release();
            case EXPIRED -> hold.expire();
            default -> throw new IllegalArgumentException("Unsupported hold transition");
        }
    }
}
