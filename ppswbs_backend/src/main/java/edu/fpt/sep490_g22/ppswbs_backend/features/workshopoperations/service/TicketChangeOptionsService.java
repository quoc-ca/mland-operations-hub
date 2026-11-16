package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSalesChannel;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ListTicketChangeOptionsQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketChangeOptions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketChangeOptions.Status;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 003.3 FR-044a / T176: lists the sessions a ticket could move to, read only (no seat held, nothing consumed).
 * <p>
 * It uses the same rules as {@link TicketSessionReplacementService#replace}: {@link TicketReplacementRules} for the
 * source, target and late-arrival window, {@code remainingCapacity} for seats and
 * {@link WorkshopPackageReservationPolicy#isValidTicketReplacement} for the package structure. A candidate that
 * breaks the package structure, the position of the session it replaces or the late-arrival window is not listed;
 * a session with no free seat or no reschedule quota left is not listed (decision 2026-11-16); the quota left is
 * reported. The replacement itself still rechecks both when the change is confirmed.
 */
@Service
@RequiredArgsConstructor
public class TicketChangeOptionsService {

    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Pairs are evaluated for the nearest sessions only, so a two-session listing stays cheap. */
    static final int PAIR_POOL_LIMIT = 40;
    static final int PAIR_LIMIT = 200;

    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopPackageReservationPolicy packageReservationPolicy;
    private final TicketSessionReplacementService replacementService;
    private final TicketReplacementRules rules;

    @Transactional(readOnly = true)
    public TicketChangeOptions list(ListTicketChangeOptionsQuery query) {
        if (query.type() == TicketReplacementType.MLAND) {
            throw new IllegalArgumentException("MLAND replacements have their own listing (FR-043)");
        }
        AttendanceTicket ticket = ticketRepository.findByReference(query.ticketReference().toString()).orElse(null);
        if (ticket == null) {
            return TicketChangeOptions.refused(Status.TICKET_NOT_FOUND);
        }
        if (ticket.getSalesChannel() == TicketSalesChannel.OTA) {
            return TicketChangeOptions.refused(Status.OTA_TICKET_NOT_SUPPORTED);
        }
        if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
            return TicketChangeOptions.refused(Status.TICKET_NOT_ACTIVE);
        }
        List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketId(ticket.getId());
        List<WorkshopSession> sources = entitlements.stream().map(TicketSessionEntitlement::getSession)
                .sorted(sessionOrder()).toList();

        List<WorkshopSession> froms = new ArrayList<>();
        if (query.fromSessionReferences().isEmpty()) {
            froms.addAll(sources);
        } else {
            for (UUID reference : query.fromSessionReferences()) {
                Optional<WorkshopSession> found = sources.stream()
                        .filter(source -> source.getReference().equalsIgnoreCase(reference.toString())).findFirst();
                if (found.isEmpty()) {
                    return TicketChangeOptions.refused(Status.FROM_SESSION_NOT_OF_TICKET);
                }
                if (froms.stream().noneMatch(existing -> existing.getId().equals(found.get().getId()))) {
                    froms.add(found.get());
                }
            }
            froms.sort(sessionOrder());
        }

        Instant now = Instant.now();
        Optional<Boolean> sameLocationRule = packageReservationPolicy.sameLocationRequired(ticket.getBookingReference());
        boolean structureKnown = sameLocationRule.isPresent();
        boolean sameLocation = sameLocationRule.orElse(true);
        List<TicketChangeOptions.FromSession> perFrom = new ArrayList<>();
        List<WorkshopSession> changeable = new ArrayList<>();
        for (WorkshopSession from : froms) {
            TicketSessionEntitlement entitlement = entitlements.stream()
                    .filter(item -> item.getSession().getId().equals(from.getId())).findFirst().orElseThrow();
            RejectionReason sourceReason = rules.checkSource(query.type(), from, entitlement, now);
            if (sourceReason != null) {
                perFrom.add(new TicketChangeOptions.FromSession(UUID.fromString(from.getReference()),
                        from.getScheduledStartAt(), from.getScheduledEndAt(), false, sourceReason.name(), List.of()));
                continue;
            }
            changeable.add(from);
            List<TicketChangeOptions.Option> options = new ArrayList<>();
            if (structureKnown) {
                int index = indexOf(sources, from);
                for (WorkshopSession candidate : candidatesFor(from, sameLocation, now)) {
                    if (isSource(sources, candidate)
                            || rules.checkTarget(candidate, now) != null
                            || rules.checkLateArrivalWindow(query.type(), from, candidate, now) != null) {
                        continue;
                    }
                    List<WorkshopSession> resulting = new ArrayList<>(sources);
                    resulting.set(index, candidate);
                    List<WorkshopSession> sorted = resulting.stream().sorted(sessionOrder()).toList();
                    // The replacement pairs sessions by position, so the candidate keeps the replaced session's place.
                    if (!sorted.get(index).getId().equals(candidate.getId())
                            || !packageReservationPolicy.isValidTicketReplacement(ticket.getBookingReference(), sorted)) {
                        continue;
                    }
                    Optional<TicketChangeOptions.Option> listed = option(candidate, now);
                    listed.ifPresent(options::add);
                }
            }
            perFrom.add(new TicketChangeOptions.FromSession(UUID.fromString(from.getReference()),
                    from.getScheduledStartAt(), from.getScheduledEndAt(), true, null, options));
        }

        List<TicketChangeOptions.Pair> pairs = List.of();
        if (structureKnown && query.fromSessionReferences().size() >= 2 && froms.size() == 2
                && changeable.size() == 2 && sources.size() == 2) {
            pairs = pairs(ticket, query.type(), sources, froms, sameLocation, now);
        }
        return new TicketChangeOptions(Status.OK, perFrom, pairs);
    }

    private List<TicketChangeOptions.Pair> pairs(AttendanceTicket ticket, TicketReplacementType type,
                                                 List<WorkshopSession> sources, List<WorkshopSession> froms,
                                                 boolean sameLocation, Instant now) {
        List<WorkshopSession> firstPool = pool(froms.get(0), type, sources, sameLocation, now);
        List<WorkshopSession> secondPool = pool(froms.get(1), type, sources, sameLocation, now);
        List<TicketChangeOptions.Pair> pairs = new ArrayList<>();
        for (WorkshopSession first : firstPool) {
            for (WorkshopSession second : secondPool) {
                if (pairs.size() >= PAIR_LIMIT) {
                    return pairs;
                }
                if (first.getId().equals(second.getId())
                        || !first.getScheduledStartAt().isBefore(second.getScheduledStartAt())
                        || !packageReservationPolicy.isValidTicketReplacement(ticket.getBookingReference(),
                                List.of(first, second))) {
                    continue;
                }
                int firstSeats = replacementService.remainingCapacity(first, now);
                int secondSeats = replacementService.remainingCapacity(second, now);
                // A pair is listed only when both sessions still have a seat and reschedule quota.
                if (firstSeats < 1 || secondSeats < 1
                        || first.remainingRescheduleQuota() < 1 || second.remainingRescheduleQuota() < 1) {
                    continue;
                }
                pairs.add(new TicketChangeOptions.Pair(
                        new TicketChangeOptions.PairSession(UUID.fromString(first.getReference()),
                                first.getScheduledStartAt(), first.getScheduledEndAt(), firstSeats,
                                first.remainingRescheduleQuota()),
                        new TicketChangeOptions.PairSession(UUID.fromString(second.getReference()),
                                second.getScheduledStartAt(), second.getScheduledEndAt(), secondSeats,
                                second.remainingRescheduleQuota()), null));
            }
        }
        return pairs;
    }

    private List<WorkshopSession> pool(WorkshopSession from, TicketReplacementType type, List<WorkshopSession> sources,
                                       boolean sameLocation, Instant now) {
        return candidatesFor(from, sameLocation, now).stream()
                .filter(candidate -> !isSource(sources, candidate)
                        && rules.checkTarget(candidate, now) == null
                        && rules.checkLateArrivalWindow(type, from, candidate, now) == null)
                .limit(PAIR_POOL_LIMIT).toList();
    }

    private List<WorkshopSession> candidatesFor(WorkshopSession from, boolean sameLocation, Instant now) {
        LocalDate fromDate = now.atZone(WORKSHOP_ZONE).toLocalDate().minusDays(1);
        List<WorkshopSession> found = sameLocation && from.getLocation() != null
                ? sessionRepository.findByLocationIdAndStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
                        from.getLocation().getId(), SessionState.PUBLISHED, fromDate)
                : sessionRepository.findByStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
                        SessionState.PUBLISHED, fromDate);
        return found.stream().filter(session -> session.getScheduledStartAt() != null).toList();
    }

    /** Empty when the session has no free seat or no reschedule quota left: it is not offered at all. */
    private Optional<TicketChangeOptions.Option> option(WorkshopSession candidate, Instant now) {
        int seats = replacementService.remainingCapacity(candidate, now);
        int quota = candidate.remainingRescheduleQuota();
        if (seats < 1 || quota < 1) {
            return Optional.empty();
        }
        return Optional.of(new TicketChangeOptions.Option(UUID.fromString(candidate.getReference()),
                candidate.getScheduledStartAt(), candidate.getScheduledEndAt(),
                candidate.getLocation() != null ? candidate.getLocation().getId() : null, seats, quota, null));
    }

    private static int indexOf(List<WorkshopSession> sources, WorkshopSession session) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).getId().equals(session.getId())) {
                return i;
            }
        }
        throw new IllegalStateException("session is not one of the ticket's sessions");
    }

    private static boolean isSource(List<WorkshopSession> sources, WorkshopSession candidate) {
        return sources.stream().anyMatch(source -> source.getId().equals(candidate.getId()));
    }

    private static Comparator<WorkshopSession> sessionOrder() {
        return Comparator.comparing(WorkshopSession::getScheduledStartAt).thenComparing(WorkshopSession::getReference);
    }
}
