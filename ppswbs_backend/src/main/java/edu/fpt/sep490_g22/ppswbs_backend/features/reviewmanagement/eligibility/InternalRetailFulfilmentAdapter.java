package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
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
public class InternalRetailFulfilmentAdapter implements RetailFulfilmentReviewPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String BASE_SELECT =
            "SELECT roi.id AS item_id, " +
            "       roi.variant_id AS variant_id, " +
            "       ro.created_at AS order_created_at, " +
            "       rgc.member_account_id AS member_account_id, " +
            "       a.login_email AS contact_email, " +
            "       ap.phone AS contact_phone " +
            "FROM retail_order_items roi " +
            "JOIN retail_orders ro ON ro.id = roi.retail_order_id " +
            "JOIN retail_grouped_checkouts rgc ON rgc.id = ro.grouped_checkout_id " +
            "JOIN accounts a ON a.id = rgc.member_account_id " +
            "LEFT JOIN account_profiles ap ON ap.account_id = a.id " +
            "WHERE ro.fulfilment_state IN ('PICKED_UP', 'DELIVERED') ";

    private final RowMapper<EligibleSourceItem> rowMapper = new RowMapper<EligibleSourceItem>() {
        @Override
        public EligibleSourceItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp orderCreated = rs.getTimestamp("order_created_at");
            Instant completedAt = orderCreated != null ? orderCreated.toInstant() : Instant.now();
            Long memberId = rs.getObject("member_account_id") != null ? rs.getLong("member_account_id") : null;

            return EligibleSourceItem.builder()
                    .sourceType(SourceType.RETAIL_ORDER_LINE)
                    .sourceReference(rs.getString("item_id"))
                    .targetType(TargetType.RETAIL_PRODUCT)
                    .targetReference(rs.getString("variant_id"))
                    .targetName("Product Variant #" + rs.getString("variant_id"))
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
        String sql = BASE_SELECT + "AND rgc.member_account_id = :memberAccountId ORDER BY ro.created_at DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("memberAccountId", memberAccountId);
        try {
            return jdbcTemplate.query(sql, params, rowMapper);
        } catch (DataAccessException ex) {
            log.error("Failed to query retail fulfilment eligibility for member {}", memberAccountId, ex);
            return Collections.emptyList();
        }
    }

    @Override
    public List<EligibleSourceItem> findEligibleByContact(ContactType contactType, String contactValue) {
        if (contactType == null || contactValue == null || contactValue.isBlank()) {
            return Collections.emptyList();
        }
        String condition = contactType == ContactType.EMAIL ? "AND a.login_email = :contactValue " : "AND ap.phone = :contactValue ";
        String sql = BASE_SELECT + condition + "ORDER BY ro.created_at DESC";
        try {
            return jdbcTemplate.query(sql, new MapSqlParameterSource("contactValue", contactValue.trim()), rowMapper);
        } catch (DataAccessException ex) {
            log.error("Failed to query retail eligibility for contact type {}", contactType, ex);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<EligibleSourceItem> verifySource(String sourceReference, Long memberAccountId,
                                                      ContactType contactType, String contactValue) {
        if (sourceReference == null || sourceReference.isBlank()) {
            return Optional.empty();
        }
        String ownerCondition;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceReference", sourceReference.trim());
        if (memberAccountId != null) {
            ownerCondition = " AND rgc.member_account_id = :memberAccountId";
            params.addValue("memberAccountId", memberAccountId);
        } else if (contactType != null && contactValue != null && !contactValue.isBlank()) {
            ownerCondition = contactType == ContactType.EMAIL
                    ? " AND a.login_email = :contactValue"
                    : " AND ap.phone = :contactValue";
            params.addValue("contactValue", contactValue.trim());
        } else {
            return Optional.empty();
        }
        String sql = BASE_SELECT + "AND roi.id = :sourceReference" + ownerCondition;
        try {
            List<EligibleSourceItem> list = jdbcTemplate.query(sql, params, rowMapper);
            if (list.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(list.get(0));
        } catch (DataAccessException ex) {
            log.error("Failed to verify retail fulfilment source {}", sourceReference, ex);
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
                    BASE_SELECT + "AND roi.id = :sourceReference",
                    new MapSqlParameterSource("sourceReference", sourceReference.trim()), rowMapper);
            return list.stream().findFirst();
        } catch (DataAccessException ex) {
            log.error("Failed to verify retail source {}", sourceReference, ex);
            return Optional.empty();
        }
    }
}

