package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.configuration.TimezoneConfiguration;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLineSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 003.1 T150: read-only list of the online booking-time VNPay payments of a location and a Vietnam working day, for
 * the 003.3 reconciliation list. Reads stored attempts only: it calls no VNPay endpoint and writes nothing. An
 * attempt whose merchant reference sits in {@code group_excess_payments} is excess money refunded in full, not a
 * booking-time payment, so it is not listed.
 */
@Service
@RequiredArgsConstructor
public class BookingTimePaymentQueryService {

    private final GroupPaymentAttemptRepository attempts;
    private final BookingGroupLineRepository lines;
    private final TicketBookingRepository tickets;

    @Transactional(readOnly = true)
    public List<BookingTimePayment> list(Long locationId, LocalDate workingDate) {
        if (locationId == null || workingDate == null) {
            throw new BookingValidationException("locationId and workingDate are required");
        }
        Instant from = workingDate.atStartOfDay(TimezoneConfiguration.ZONE_ID_VIETNAM).toInstant();
        Instant to = workingDate.plusDays(1).atStartOfDay(TimezoneConfiguration.ZONE_ID_VIETNAM).toInstant();
        List<GroupPaymentAttempt> found = attempts.findBookingTimePayments(locationId,
                List.of(PaymentAttemptStatus.SUCCESS, PaymentAttemptStatus.PENDING, PaymentAttemptStatus.EXPIRED),
                PaymentAttemptStatus.SUCCESS, BookingStatus.CONFIRMED, from, to);
        if (found.isEmpty()) return List.of();

        List<Long> groupIds = found.stream().map(a -> a.getGroup().getId()).distinct().toList();
        Map<Long, TreeSet<Long>> locationsByGroup = new LinkedHashMap<>();
        for (BookingGroupLine line : lines.findWithSessionsByGroupIdIn(groupIds)) {
            TreeSet<Long> set = locationsByGroup.computeIfAbsent(line.getGroup().getId(), k -> new TreeSet<>());
            for (BookingGroupLineSession s : line.getSessions()) {
                if (s.getLocationReference() != null) set.add(s.getLocationReference());
            }
        }
        Map<Long, List<String>> ticketsByGroup = new LinkedHashMap<>();
        for (TicketBooking t : tickets.findByGroupIdInOrderByGroupIdAscLineIndexAscParticipantOrdinalAsc(groupIds)) {
            ticketsByGroup.computeIfAbsent(t.getGroup().getId(), k -> new ArrayList<>()).add(t.getTicketCode());
        }

        List<BookingTimePayment> result = new ArrayList<>(found.size());
        for (GroupPaymentAttempt a : found) {
            var group = a.getGroup();
            BookingTimePaymentStatus status = statusOf(a, group.getGroupStatus());
            result.add(new BookingTimePayment(group.getGroupCode(),
                    ticketsByGroup.getOrDefault(group.getId(), List.of()), a.getAmountVnd(),
                    BookingTimePayment.METHOD_VNPAY_ONLINE, status, a.getMerchantReference(),
                    a.getAttemptReference(), a.getCreatedAt(),
                    status == BookingTimePaymentStatus.CONFIRMED ? group.getConfirmedAt() : null,
                    List.copyOf(locationsByGroup.getOrDefault(group.getId(), new TreeSet<>()))));
        }
        return List.copyOf(result);
    }

    static BookingTimePaymentStatus statusOf(GroupPaymentAttempt attempt, BookingStatus groupStatus) {
        if (attempt.getAttemptStatus() == PaymentAttemptStatus.SUCCESS) {
            return groupStatus == BookingStatus.CONFIRMED
                    ? BookingTimePaymentStatus.CONFIRMED : BookingTimePaymentStatus.LATE_PENDING_REVIEW;
        }
        return BookingTimePaymentStatus.UNVERIFIED;
    }
}
