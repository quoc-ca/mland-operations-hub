package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.CashDayCloseStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 013 Manager Home, R-2 and R-3 (T014, T015), on MySQL through the real security chain: the waiting count equals the
 * size of the list the card opens (spec FR-004), and a non-Manager gets no number (403) and no rows. The tests count
 * before and after inserting, so rows left by other suites in the shared test schema do not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class ManagerHomeWaitingCountsIT {

    private static final String REFUNDS = "/api/v1/workshop-refund-requests";
    private static final String CASH_CLOSES = "/api/v1/workshop-cash-closes/awaiting-confirmation-count";
    private static final String MANAGER = "Bearer test-uid:manager";
    private static final String STAFF = "Bearer test-uid:staff";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private SettlementRefundRequestRepository refunds;
    @Autowired private SettlementCashDayCloseRepository cashCloses;
    @Autowired private SettlementBusinessAuditRepository audits;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private WorkshopBookingFacade workshopBookingFacade;
    @MockBean private WorkshopBookingNotificationFacade workshopBookingNotificationFacade;

    @BeforeEach
    void setUp() {
        account("manager", 10L, ActorRole.MANAGER);
        account("staff", 101L, ActorRole.STAFF);
    }

    private void account(String uid, long id, ActorRole role) {
        when(tokenVerificationService.verify("test-uid:" + uid))
                .thenReturn(new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.findAccountFullName(id)).thenReturn(Optional.empty());
    }

    private void refund(RefundRequestStatus status) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        boolean refunded = status == RefundRequestStatus.REFUNDED;
        boolean closed = status == RefundRequestStatus.CLOSED_UNREACHABLE;
        refunds.saveAndFlush(SettlementRefundRequest.builder().requestReference("IT-RR-" + id)
                .bookingReference("IT-BK-" + id).reason(RefundReason.MLAND_CANCELLATION).sourceReference("IT-SRC-" + id)
                .amountVnd(100_000).currencyCode("VND").status(status)
                .recipientAccountMasked(status == RefundRequestStatus.PENDING_ACCOUNT ? null : "****1234")
                .approvedByAccountId(refunded ? 10L : null).approvedAt(refunded ? now : null).approvalReason(refunded ? "it" : null)
                .transferredByAccountId(refunded ? 10L : null).transferredAt(refunded ? now : null)
                .bankTransferReference(refunded ? "IT-BANK" : null)
                .confirmedByAccountId(refunded ? 10L : null).confirmedAt(refunded ? now : null)
                .closedByAccountId(closed ? 10L : null).closedAt(closed ? now : null).closureReason(closed ? "it" : null).build());
    }

    private void cashClose(CashDayCloseStatus status, long branchId, LocalDate day) {
        Instant now = Instant.now();
        cashCloses.saveAndFlush(SettlementCashDayClose.builder().closeReference("IT-CC-" + UUID.randomUUID())
                .branchId(branchId).businessDay(day).expectedCashVnd(1_000).countedCashVnd(900).differenceVnd(-100)
                .differenceReason("it").status(status)
                .confirmedByAccountId(status == CashDayCloseStatus.CLOSED ? 10L : null)
                .confirmedAt(status == CashDayCloseStatus.CLOSED ? now : null).closedByAccountId(10L).closedAt(now).createdAt(now)
                .updatedAt(now).build());
    }

    private JsonNode body(String url, String token, int expected) throws Exception {
        var request = get(url);
        if (token != null) {
            request = request.header("Authorization", token);
        }
        return JSON.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString());
    }

    @Test
    @DisplayName("R-2: the refund waiting count equals the size of ?waiting=true and counts only the four PENDING_* statuses")
    void refundWaitingCountEqualsTheListItOpens() throws Exception {
        long before = body(REFUNDS + "/waiting-count", MANAGER, 200).get("waiting").asLong();
        assertThat(body(REFUNDS + "?waiting=true", MANAGER, 200)).hasSize((int) before);

        refund(RefundRequestStatus.PENDING_ACCOUNT);
        refund(RefundRequestStatus.PENDING_APPROVAL);
        refund(RefundRequestStatus.PENDING_TRANSFER);
        refund(RefundRequestStatus.PENDING_RECONCILIATION);
        refund(RefundRequestStatus.REFUNDED);
        refund(RefundRequestStatus.CLOSED_UNREACHABLE);

        long after = body(REFUNDS + "/waiting-count", MANAGER, 200).get("waiting").asLong();
        assertThat(after - before).as("only the four PENDING_* statuses are waiting").isEqualTo(4);
        JsonNode list = body(REFUNDS + "?waiting=true", MANAGER, 200);
        assertThat(list).hasSize((int) after);
        list.forEach(row -> assertThat(row.get("status").asText()).startsWith("PENDING_"));
        // the unfiltered list still holds the closed ones: that is why the card link carries waiting=true
        assertThat(body(REFUNDS, MANAGER, 200).size()).isGreaterThanOrEqualTo((int) after + 2);
    }

    @Test
    @DisplayName("R-2: waiting cannot be combined with a status, and a non-Manager gets 403 and no number or rows")
    void refundWaitingIsManagerOnlyAndExclusiveWithStatus() throws Exception {
        body(REFUNDS + "?waiting=true&status=PENDING_ACCOUNT", MANAGER, 422);
        JsonNode denied = body(REFUNDS + "/waiting-count", STAFF, 403);
        assertThat(denied.has("waiting")).isFalse();
        assertThat(body(REFUNDS + "?waiting=true", STAFF, 403).has("waiting")).isFalse();
        body(REFUNDS + "/waiting-count", null, 401);
    }

    @Test
    @DisplayName("R-3: the cash-close count counts only PENDING_MANAGER_CONFIRMATION, is Manager only and writes no audit row")
    void cashCloseCountCountsOnlyDifferencesAwaitingAManager() throws Exception {
        long before = body(CASH_CLOSES, MANAGER, 200).get("waiting").asLong();
        long auditsBefore = audits.count();
        long branch = 900_000L + (System.nanoTime() % 90_000L);

        cashClose(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION, branch, LocalDate.of(2026, 11, 1));
        cashClose(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION, branch, LocalDate.of(2026, 11, 2));
        cashClose(CashDayCloseStatus.CLOSED, branch, LocalDate.of(2026, 11, 3));

        assertThat(body(CASH_CLOSES, MANAGER, 200).get("waiting").asLong() - before).isEqualTo(2);
        assertThat(audits.count()).as("a successful read writes no audit row").isEqualTo(auditsBefore);
        long counted = body(CASH_CLOSES, MANAGER, 200).get("waiting").asLong();
        JsonNode list = body("/api/v1/workshop-cash-closes?awaitingConfirmation=true", MANAGER, 200);
        assertThat(list).as("the card number equals the list it opens").hasSize((int) counted);
        list.forEach(row -> assertThat(row.get("status").asText()).isEqualTo("PENDING_MANAGER_CONFIRMATION"));
        assertThat(body("/api/v1/workshop-cash-closes?awaitingConfirmation=true", STAFF, 403).isArray()).isFalse();
        assertThat(body(CASH_CLOSES, STAFF, 403).has("waiting")).isFalse();
    }
}
