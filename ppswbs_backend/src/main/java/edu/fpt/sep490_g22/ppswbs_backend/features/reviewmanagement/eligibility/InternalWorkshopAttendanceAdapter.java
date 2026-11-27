package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.common.settlement.TicketBalancePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalWorkshopAttendanceAdapter implements WorkshopAttendanceReviewPort {

    /** 011 spec section 3: an attended ticket that still owes money cannot be reviewed. */
    static final String REASON_BALANCE_DUE = "BALANCE_DUE";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TicketBalancePort ticketBalances;

    /**
     * Checked-in tickets of both booking models as one derived table: a single booking (workshop_bookings, keyed by the
     * ticket's booking reference) and a booking group (ticket_bookings + booking_groups, keyed by the ticket's operations
     * reference; the group's buyer and contact stand in for the booking's). A group ticket's booking reference is a line
     * reference, so it never matches workshop_bookings. folio_ref is the key of the ticket's settlement folio.
     */
    private static final String BASE_SELECT =
            "SELECT src.* FROM (" +
            "SELECT at.reference AS ticket_ref, ws.reference AS session_ref, " +
            "       wb.package_name_snapshot AS target_name, wb.package_id AS package_id, " +
            "       tse.checked_in_at AS checked_in_at, wb.member_account_id AS member_account_id, " +
            "       wb.contact_email AS contact_email, wb.contact_phone AS contact_phone, " +
            "       wb.booking_code AS folio_ref " +
            "FROM attendance_tickets at " +
            "JOIN ticket_session_entitlements tse ON tse.ticket_id = at.id " +
            "JOIN workshop_sessions ws ON ws.id = tse.session_id " +
            "JOIN workshop_bookings wb ON wb.booking_code = at.booking_reference " +
            "WHERE tse.attendance_state = 'CHECKED_IN' " +
            "UNION ALL " +
            "SELECT at.reference, ws.reference, tb.package_name_snapshot, tb.package_id, tse.checked_in_at, " +
            "       bg.buyer_member_account_id, bg.contact_email, bg.contact_phone, tb.ticket_code " +
            "FROM attendance_tickets at " +
            "JOIN ticket_session_entitlements tse ON tse.ticket_id = at.id " +
            "JOIN workshop_sessions ws ON ws.id = tse.session_id " +
            "JOIN ticket_bookings tb ON tb.operations_ticket_reference = at.reference " +
            "JOIN booking_groups bg ON bg.id = tb.group_id " +
            "WHERE tse.attendance_state = 'CHECKED_IN'" +
            ") src WHERE 1 = 1 ";

    private record AttendedTicket(EligibleSourceItem item, String folioReference) {
    }

    private final RowMapper<AttendedTicket> rowMapper = new RowMapper<AttendedTicket>() {
        @Override
        public AttendedTicket mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp checkedIn = rs.getTimestamp("checked_in_at");
            Instant completedAt = checkedIn != null ? checkedIn.toInstant() : Instant.now();
            Long memberId = rs.getObject("member_account_id") != null ? rs.getLong("member_account_id") : null;

            EligibleSourceItem item = EligibleSourceItem.builder()
                    .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                    .sourceReference(rs.getString("ticket_ref"))
                    .targetType(TargetType.WORKSHOP_SESSION)
                    .targetReference(rs.getString("session_ref"))
                    .targetName(rs.getString("target_name"))
                    .packageId(rs.getObject("package_id") != null ? rs.getLong("package_id") : null)
                    .completedAt(completedAt)
                    .eligible(true)
                    .memberAccountId(memberId)
                    .contactEmail(rs.getString("contact_email"))
                    .contactPhone(rs.getString("contact_phone"))
                    .build();
            return new AttendedTicket(item, rs.getString("folio_ref"));
        }
    };

    /**
     * Runs the query and marks every ticket whose folio still has a balance due. A ticket without a folio owes nothing
     * on record. A balance that cannot be read propagates, so an unpaid ticket is never offered by mistake.
     */
    private List<EligibleSourceItem> query(String sql, MapSqlParameterSource params) {
        List<AttendedTicket> tickets = jdbcTemplate.query(sql, params, rowMapper);
        if (tickets.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, TicketBalancePort.TicketBalance> balances =
                ticketBalances.balancesOf(tickets.stream().map(AttendedTicket::folioReference).toList());
        return tickets.stream().map(ticket -> {
            TicketBalancePort.TicketBalance balance = balances.get(ticket.folioReference());
            if (balance != null && balance.balanceDueVnd() > 0) {
                ticket.item().setEligible(false);
                ticket.item().setReasonCode(REASON_BALANCE_DUE);
            }
            return ticket.item();
        }).toList();
    }

    @Override
    public List<EligibleSourceItem> findEligibleByMemberId(Long memberAccountId) {
        if (memberAccountId == null) {
            return Collections.emptyList();
        }
        String sql = BASE_SELECT + "AND src.member_account_id = :memberAccountId ORDER BY src.checked_in_at DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("memberAccountId", memberAccountId);
        try {
            return query(sql, params);
        } catch (DataAccessException ex) {
            log.error("Failed to query workshop attendance eligibility for member {}", memberAccountId, ex);
            return Collections.emptyList();
        }
    }

    @Override
    public List<EligibleSourceItem> findEligibleByContact(ContactType contactType, String contactValue) {
        if (contactType == null || contactValue == null || contactValue.isBlank()) {
            return Collections.emptyList();
        }
        String condition = contactType == ContactType.EMAIL ? "AND src.contact_email = :contactValue " : "AND src.contact_phone = :contactValue ";
        String sql = BASE_SELECT + condition + "ORDER BY src.checked_in_at DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("contactValue", contactValue.trim());
        try {
            return query(sql, params);
        } catch (DataAccessException ex) {
            log.error("Failed to query workshop attendance eligibility for contact {}: {}", contactType, contactValue, ex);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<EligibleSourceItem> verifySource(String sourceReference, Long memberAccountId, ContactType contactType, String contactValue) {
        if (sourceReference == null || sourceReference.isBlank()) {
            return Optional.empty();
        }
        String sql = BASE_SELECT + "AND src.ticket_ref = :sourceReference";
        MapSqlParameterSource params = new MapSqlParameterSource("sourceReference", sourceReference.trim());
        try {
            List<EligibleSourceItem> list = query(sql, params);
            if (list.isEmpty()) {
                return Optional.empty();
            }
            EligibleSourceItem item = list.get(0);
            if (memberAccountId != null) {
                if (memberAccountId.equals(item.getMemberAccountId())) {
                    return Optional.of(item);
                }
            } else if (contactType != null && contactValue != null) {
                if (contactType == ContactType.EMAIL && contactValue.equalsIgnoreCase(item.getContactEmail())) {
                    return Optional.of(item);
                }
                if (contactType == ContactType.PHONE && contactValue.equals(item.getContactPhone())) {
                    return Optional.of(item);
                }
            }
            return Optional.empty();
        } catch (DataAccessException ex) {
            log.error("Failed to verify workshop attendance source {}", sourceReference, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<EligibleSourceItem> verifySource(String sourceReference) {
        if (sourceReference == null || sourceReference.isBlank()) {
            return Optional.empty();
        }
        try {
            List<EligibleSourceItem> list = query(
                    BASE_SELECT + "AND src.ticket_ref = :sourceReference",
                    new MapSqlParameterSource("sourceReference", sourceReference.trim()));
            return list.stream().findFirst();
        } catch (DataAccessException ex) {
            log.error("Failed to verify workshop source {}", sourceReference, ex);
            return Optional.empty();
        }
    }
}

