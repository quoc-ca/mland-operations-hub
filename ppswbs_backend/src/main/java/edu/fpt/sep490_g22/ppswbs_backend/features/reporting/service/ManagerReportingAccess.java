package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * FR-001/FR-003/FR-006 (Session 2026-11-14): `REPORTING_EXECUTIVE_VIEW` is held by exactly an ACTIVE Manager with
 * at least one active location assignment. Admin parameter authority never implies it. A requested branch set must
 * be wholly inside the assignments; an empty request means all assigned branches.
 */
@Component
public class ManagerReportingAccess {

    public List<Long> authorize(Object actorCandidate, List<Long> requestedLocationIds) {
        if (!(actorCandidate instanceof CurrentAccountActor actor) || actor.accountId() == null) {
            throw ReportingException.unauthenticated();
        }
        if (actor.role() != ActorRole.MANAGER || actor.status() != ActorStatus.ACTIVE) {
            throw ReportingException.forbidden("Active Manager access is required");
        }
        List<Long> assigned = actor.activeLocationIds().stream().distinct().sorted().toList();
        if (assigned.isEmpty()) {
            throw ReportingException.forbidden("The Manager has no active branch assignment");
        }
        if (requestedLocationIds == null || requestedLocationIds.isEmpty()) return assigned;
        Set<Long> requested = new LinkedHashSet<>(requestedLocationIds);
        if (requested.size() != requestedLocationIds.size()) {
            throw ReportingException.invalidFilter("A branch is listed more than once");
        }
        if (!assigned.containsAll(requested)) {
            throw ReportingException.forbidden("A requested branch is outside the Manager's scope");
        }
        return requested.stream().sorted().toList();
    }
}
