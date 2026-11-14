package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the reconciliation work item of a refused inbound callback (spec A1, T060).
 *
 * <p>This lives in its own bean, and its own transaction, on purpose. {@code OtaInboundEventService.receive}
 * reports a dedupe conflict by throwing, which rolls its transaction back; a work item written inside that
 * transaction would be rolled back with it and no operator would ever see the conflict. {@code REQUIRES_NEW}
 * commits the work item before the refusal propagates. The call must come from another bean so the
 * transactional proxy applies: a self-invocation inside one service would silently keep the caller's
 * transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtaInboundConflictRecorder {

    private final OtaReconciliationWorkItemRepository workItems;

    /**
     * @param prior the stored event whose reference the new callback reused with a different payload
     * @return the work item's public reference, or {@code null} when it could not be recorded
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String recordDedupeConflict(OtaInboundEvent prior) {
        try {
            OtaReconciliationWorkItem saved = workItems.save(OtaReconciliationWorkItem.builder()
                    .category("INBOUND_EVENT")
                    .status("OPEN")
                    .locationReference("GLOBAL")
                    .inboundEvent(prior)
                    .decision("DEDUPE_CONFLICT")
                    .build());
            return saved.getPublicReference();
        } catch (RuntimeException ex) {
            // The refusal itself matters more than the work item, so a failure here is logged and the
            // caller still refuses the callback. The payload is never logged.
            log.error("Could not record the dedupe-conflict work item for inbound event {}",
                    prior.getId(), ex);
            return null;
        }
    }
}
