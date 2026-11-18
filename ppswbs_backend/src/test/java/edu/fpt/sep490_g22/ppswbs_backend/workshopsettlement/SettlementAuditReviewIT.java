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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T171 (003.2 FR-047 to FR-053, FR-052): the Settlement tab route served by 003.3, on MySQL through the real
 * security chain. Manager-only; Staff and Admin get 403 and no data, recorded as {@code DENIED_<reason>} the way
 * 003.3 records authorization denials; keyset paging under concurrent inserts; full reason text for the Manager;
 * bad parameters are the 400 {@code VALIDATION_ERROR}; reading writes no audit row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class SettlementAuditReviewIT {

    private static final String URL = "/api/v1/workshop-audit/settlement-records";
    private static final String MANAGER = "Bearer test-uid:manager";
    private static final String STAFF = "Bearer test-uid:staff";
    private static final String ADMIN = "Bearer test-uid:admin";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REASON = "Khach goi dien xin hoan tien vi ly do ca nhan";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SettlementBusinessAuditRepository auditRepository;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private WorkshopBookingFacade workshopBookingFacade;
    @MockBean private WorkshopBookingNotificationFacade workshopBookingNotificationFacade;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE settlement_business_audits");
        account("manager", 10L, ActorRole.MANAGER);
        account("staff", 101L, ActorRole.STAFF);
        account("admin", 1L, ActorRole.ADMIN);
        when(accountFacade.findAccountFullName(anyLong())).thenReturn(Optional.empty());
        when(accountFacade.findAccountFullName(10L)).thenReturn(Optional.of("Manager One"));
    }

    private void account(String uid, long id, ActorRole role) {
        when(tokenVerificationService.verify("test-uid:" + uid))
                .thenReturn(new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of())));
    }

    private SettlementBusinessAudit save(String actionCode, String actorReference, String aggregateType,
                                         String aggregateReference, Instant at, String reason) {
        return auditRepository.saveAndFlush(SettlementBusinessAudit.builder()
                .auditReference("IT-" + UUID.randomUUID()).aggregateType(aggregateType)
                .aggregateReference(aggregateReference).actionCode(actionCode).actorReference(actorReference)
                .correlationReference("corr-" + actionCode).evidenceReference("evidence-1").reasonReference(reason)
                .createdAt(at).build());
    }

    private MockHttpServletRequestBuilder read(String token, String query) {
        MockHttpServletRequestBuilder request = get(URL + (query == null ? "" : "?" + query));
        return token == null ? request : request.header("Authorization", token);
    }

    private JsonNode body(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Manager reads rows with the full reason text, actor id + name, and the actor filter matches account:<id>")
    void managerReadsFullReasonAndFiltersByActor() throws Exception {
        Instant now = Instant.now();
        save("REFUND_APPROVED", "account:10", "REFUND_REQUEST", "RR-1", now.minusSeconds(30), REASON);
        save("CASH_DAY_CLOSED", "SYSTEM", "CASH_DAY", "2026-11-17", now.minusSeconds(20), null);
        save("FOLIO_CORRECTED", "55", "FOLIO", "F-9", now.minusSeconds(10), "so thuong");
        long before = auditRepository.count();

        JsonNode all = body(read(MANAGER, null), 200);
        assertThat(all.get("items")).hasSize(3);
        assertThat(all.get("nextCursor").isNull()).isTrue();

        JsonNode byActor = body(read(MANAGER, "actorId=10"), 200);
        assertThat(byActor.get("items")).hasSize(1);
        JsonNode row = byActor.get("items").get(0);
        assertThat(row.get("actionCode").asText()).isEqualTo("REFUND_APPROVED");
        assertThat(row.get("reasonText").asText()).as("full reason text for the Manager").isEqualTo(REASON);
        assertThat(row.get("actor").get("accountId").asLong()).isEqualTo(10L);
        assertThat(row.get("actor").get("displayName").asText()).isEqualTo("Manager One");
        assertThat(row.get("actorReference").asText()).isEqualTo("account:10");
        assertThat(row.get("evidenceReference").asText()).isEqualTo("evidence-1");

        JsonNode system = body(read(MANAGER, "actionCode=CASH_DAY_CLOSED"), 200).get("items").get(0);
        assertThat(system.get("actorReference").asText()).isEqualTo("SYSTEM");
        assertThat(system.path("actor").isMissingNode() || system.get("actor").isNull()).isTrue();

        assertThat(body(read(MANAGER, "aggregateType=FOLIO&aggregateReference=F-9"), 200).get("items")).hasSize(1);
        assertThat(body(read(MANAGER, "aggregateType=FOLIO&aggregateReference=NOPE"), 200).get("items")).isEmpty();

        assertThat(auditRepository.count()).as("reading writes no audit row (FR-053)").isEqualTo(before);
    }

    @Test
    @DisplayName("Staff and Admin get 403 and never see a row or the reason text; each refusal is recorded DENIED_<reason>")
    void staffAndAdminAreRefusedAndRecorded() throws Exception {
        save("REFUND_APPROVED", "account:10", "REFUND_REQUEST", "RR-1", Instant.now(), REASON);

        for (String caller : new String[] {STAFF, ADMIN}) {
            JsonNode problem = body(read(caller, null), 403);
            assertThat(problem.get("code").asText()).isEqualTo("MANAGER_REQUIRED");
            assertThat(problem.toString()).doesNotContain(REASON).doesNotContain("items");
        }
        mockMvc.perform(read(null, null)).andExpect(status().isUnauthorized());

        List<SettlementBusinessAudit> denied = auditRepository.findAll().stream()
                .filter(a -> a.getActionCode().startsWith("DENIED_")).toList();
        assertThat(denied).hasSize(2).allSatisfy(a -> assertThat(a.getActionCode()).isEqualTo("DENIED_MANAGER_REQUIRED"));
        assertThat(denied).extracting(SettlementBusinessAudit::getActorReference).containsExactlyInAnyOrder("101", "1");
    }

    @Test
    @DisplayName("Default view is the last 7 days; an explicit from reaches older rows")
    void defaultWindowIsSevenDays() throws Exception {
        Instant now = Instant.now();
        save("OLD_ROW", "SYSTEM", "X", "1", now.minus(Duration.ofDays(8)), null);
        save("RECENT_ROW", "SYSTEM", "X", "2", now.minus(Duration.ofDays(1)), null);

        JsonNode defaults = body(read(MANAGER, null), 200);
        assertThat(defaults.get("items")).hasSize(1);
        assertThat(defaults.get("items").get(0).get("actionCode").asText()).isEqualTo("RECENT_ROW");
        assertThat(body(read(MANAGER, "from=" + now.minus(Duration.ofDays(30))), 200).get("items")).hasSize(2);
        JsonNode bounded = body(read(MANAGER, "from=" + now.minus(Duration.ofDays(30)) + "&to="
                + now.minus(Duration.ofDays(5))), 200);
        assertThat(bounded.get("items")).hasSize(1);
        assertThat(bounded.get("items").get(0).get("actionCode").asText()).isEqualTo("OLD_ROW");
    }

    @Test
    @DisplayName("Keyset paging under concurrent inserts: no duplicate, no skipped row (FR-050)")
    void pagingIsStableWhileRowsAreInserted() throws Exception {
        Instant now = Instant.now();
        List<Long> original = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            original.add(save("ORIGINAL_" + i, "SYSTEM", "X", "r" + i, now.minusSeconds(60 - i), null).getId());
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService writers = Executors.newFixedThreadPool(3);
        for (int w = 0; w < 3; w++) {
            int writer = w;
            writers.submit(() -> {
                int n = 0;
                while (!stop.get()) {
                    save("LIVE_" + writer, "SYSTEM", "X", "live-" + writer + "-" + n++, Instant.now(), null);
                }
            });
        }

        List<Long> seen = new ArrayList<>();
        try {
            String cursor = null;
            int pages = 0;
            do {
                JsonNode page = body(read(MANAGER, "limit=7" + (cursor == null ? "" : "&cursor=" + cursor)), 200);
                page.get("items").forEach(item -> seen.add(item.get("id").asLong()));
                cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
                Thread.sleep(30);
                assertThat(++pages).as("paging terminates").isLessThan(200);
            } while (cursor != null);
        } finally {
            stop.set(true);
            writers.shutdown();
            writers.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(new HashSet<>(seen)).as("no duplicate id across pages").hasSameSizeAs(seen);
        assertThat(seen).as("newest first, strictly descending").isSortedAccordingTo((a, b) -> Long.compare(b, a));
        Set<Long> seenSet = new HashSet<>(seen);
        assertThat(seenSet).as("no original row skipped").containsAll(original);
    }

    @Test
    @DisplayName("Bad limit, cursor, number or time range is 400 VALIDATION_ERROR with the offending field")
    void badParametersAreValidationErrors() throws Exception {
        String now = Instant.now().toString();
        String earlier = Instant.now().minus(Duration.ofDays(1)).toString();
        String[][] cases = {{"limit=0", "limit"}, {"limit=101", "limit"}, {"limit=abc", "limit"},
                {"cursor=!!!", "cursor"}, {"actorId=abc", "actorId"}, {"from=yesterday", "from"},
                {"from=" + now + "&to=" + earlier, "from"}, {"actionCode=" + "x".repeat(65), "actionCode"}};
        for (String[] bad : cases) {
            JsonNode problem = body(read(MANAGER, bad[0]), 400);
            assertThat(problem.get("code").asText()).as(bad[0]).isEqualTo("VALIDATION_ERROR");
            assertThat(problem.get("fieldErrors").get(0).get("field").asText()).isEqualTo(bad[1]);
            assertThat(problem.get("correlationReference").asText()).isNotBlank();
        }
    }
}
