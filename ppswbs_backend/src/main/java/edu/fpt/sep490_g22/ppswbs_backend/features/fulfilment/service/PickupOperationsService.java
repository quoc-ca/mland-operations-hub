package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.dao.PickupEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.entity.PickupEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPickupException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPickupOrderFacade;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PickupOperationsService {
    private final RetailPickupOrderFacade orders;
    private final PickupEvidenceRepository evidence;

    public record Result(String orderId, String fulfilmentState, String milestone, Instant recordedAt) { }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result prepare(CurrentAccountActor actor, String orderId) { return perform(actor, orderId, "PREPARED"); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result handOver(CurrentAccountActor actor, String orderId) { return perform(actor, orderId, "HANDED_OVER"); }

    private Result perform(CurrentAccountActor actor, String orderId, String milestone) {
        if (actor == null || actor.accountId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        if (actor.status() != ActorStatus.ACTIVE || actor.role() != ActorRole.STAFF) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active Staff access is required");
        try {
            var order = orders.lock(orderId);
            if (actor.activeLocationIds() == null || !actor.activeLocationIds().contains(order.locationId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order is outside the assigned location");
            }
            var previous = evidence.findByOrderIdAndMilestone(orderId, milestone);
            if (previous.isPresent()) return new Result(orderId, order.fulfilmentState(), milestone, previous.get().getOccurredAt());
            if ("HANDED_OVER".equals(milestone) && evidence.findByOrderIdAndMilestone(orderId, "PREPARED").isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order must be prepared before handover");
            }
            var updated = "PREPARED".equals(milestone) ? orders.markPrepared(orderId) : orders.markHandedOver(orderId);
            var saved = evidence.saveAndFlush(new PickupEvidence(orderId, order.locationId(), actor.accountId(), milestone,
                Instant.now().truncatedTo(ChronoUnit.MICROS)));
            return new Result(orderId, updated.fulfilmentState(), milestone, saved.getOccurredAt());
        } catch (RetailPickupException ex) {
            throw new ResponseStatusException(ex.reason() == RetailPickupException.Reason.NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                "Pickup order is unavailable or in an invalid state");
        }
    }
}
