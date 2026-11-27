package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.common.settlement.TicketBalancePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.EligibleSourceItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.InternalWorkshopAttendanceAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalWorkshopAttendanceAdapterTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final TicketBalancePort balances = mock(TicketBalancePort.class);
    private final InternalWorkshopAttendanceAdapter adapter = new InternalWorkshopAttendanceAdapter(jdbc, balances);

    @Test
    @DisplayName("Only an attended ticket with a balance due is blocked; paid and folio-less tickets stay eligible")
    void balanceDueBlocksOnlyOwingTicket() throws Exception {
        List<ResultSet> rows = List.of(row("OWING"), row("PAID"), row("NO-FOLIO"));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<?> mapper = call.getArgument(2);
            List<Object> mapped = new ArrayList<>();
            for (ResultSet row : rows) mapped.add(mapper.mapRow(row, mapped.size()));
            return mapped;
        });
        when(balances.balancesOf(List.of("F-OWING", "F-PAID", "F-NO-FOLIO"))).thenReturn(Map.of(
                "F-OWING", new TicketBalancePort.TicketBalance(500L, 0L, 200L, 300L),
                "F-PAID", new TicketBalancePort.TicketBalance(500L, 0L, 500L, 0L)));

        List<EligibleSourceItem> items = adapter.findEligibleByMemberId(1L);

        assertThat(items).extracting(EligibleSourceItem::getSourceReference, EligibleSourceItem::isEligible,
                        EligibleSourceItem::getReasonCode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("OWING", false, "BALANCE_DUE"),
                        org.assertj.core.groups.Tuple.tuple("PAID", true, null),
                        org.assertj.core.groups.Tuple.tuple("NO-FOLIO", true, null));
    }

    private static ResultSet row(String ticket) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ticket_ref")).thenReturn(ticket);
        when(rs.getString("folio_ref")).thenReturn("F-" + ticket);
        return rs;
    }
}
