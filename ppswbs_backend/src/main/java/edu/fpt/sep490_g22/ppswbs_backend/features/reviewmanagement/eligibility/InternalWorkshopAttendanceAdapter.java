package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
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
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalWorkshopAttendanceAdapter implements WorkshopAttendanceReviewPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String BASE_SELECT =
            "SELECT at.reference AS ticket_ref, " +
            "       ws.reference AS session_ref, " +
            "       wb.package_name_snapshot AS target_name, " +
            "       wb.package_id AS package_id, " +
            "       tse.checked_in_at AS checked_in_at, " +
            "       wb.member_account_id AS member_account_id, " +
            "       wb.contact_email AS contact_email, " +
            "       wb.contact_phone AS contact_phone " +
            "FROM attendance_tickets at " +
            "JOIN ticket_session_entitlements tse ON tse.ticket_id = at.id " +
            "JOIN workshop_sessions ws ON ws.id = tse.session_id " +
            "JOIN workshop_bookings wb ON wb.booking_code = at.booking_reference " +
            "WHERE tse.attendance_state = 'CHECKED_IN' ";

    private final RowMapper<EligibleSourceItem> rowMapper = new RowMapper<EligibleSourceItem>() {
        @Override
        public EligibleSourceItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp checkedIn = rs.getTimestamp("checked_in_at");
            Instant completedAt = checkedIn != null ? checkedIn.toInstant() : Instant.now();
            Long memberId = rs.getObject("member_account_id") != null ? rs.getLong("member_account_id") : null;

            return EligibleSourceItem.builder()
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
        }
    };

    @Override
    public List<EligibleSourceItem> findEligibleByMemberId(Long memberAccountId) {
        if (memberAccountId == null) {
            return Collections.emptyList();
        }
        String sql = BASE_SELECT + "AND wb.member_account_id = :memberAccountId ORDER BY tse.checked_in_at DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("memberAccountId", memberAccountId);
        try {
            return jdbcTemplate.query(sql, params, rowMapper);
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
        String condition = contactType == ContactType.EMAIL ? "AND wb.contact_email = :contactValue " : "AND wb.contact_phone = :contactValue ";
        String sql = BASE_SELECT + condition + "ORDER BY tse.checked_in_at DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("contactValue", contactValue.trim());
        try {
            return jdbcTemplate.query(sql, params, rowMapper);
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
        String sql = BASE_SELECT + "AND at.reference = :sourceReference";
        MapSqlParameterSource params = new MapSqlParameterSource("sourceReference", sourceReference.trim());
        try {
            List<EligibleSourceItem> list = jdbcTemplate.query(sql, params, rowMapper);
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
            List<EligibleSourceItem> list = jdbcTemplate.query(
                    BASE_SELECT + "AND at.reference = :sourceReference",
                    new MapSqlParameterSource("sourceReference", sourceReference.trim()), rowMapper);
            return list.stream().findFirst();
        } catch (DataAccessException ex) {
            log.error("Failed to verify workshop source {}", sourceReference, ex);
            return Optional.empty();
        }
    }
}

