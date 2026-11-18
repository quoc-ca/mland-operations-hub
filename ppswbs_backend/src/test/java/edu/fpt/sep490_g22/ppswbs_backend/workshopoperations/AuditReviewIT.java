package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AuditReviewService;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T170 (c)/(e), FR-047 to FR-055 on MySQL: the Manager audit read, its keyset paging under concurrent inserts, the
 * 7-day default window, the whitelist at the HTTP boundary, 403 + one DENIED row for Staff and Admin, and the
 * existing 400 {@code VALIDATION_ERROR} for bad parameters. Runs on the disposable Workshop Operations test schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class AuditReviewIT {

    private static final String URL = "/api/v1/workshop-audit/operations-events";
    private static final String MANAGER = "Bearer test-uid:manager";
    private static final String STAFF = "Bearer test-uid:staff";
    private static final String ADMIN = "Bearer test-uid:admin";
    private static final String CORR = "corr-us7";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OperationsAuditEventRepository auditRepository;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private WorkshopBookingFacade workshopBookingFacade;
    @MockBean private WorkshopBookingNotificationFacade workshopBookingNotificationFacade;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE operations_audit_events");
        account("manager", 10L, ActorRole.MANAGER);
        account("staff", 101L, ActorRole.STAFF);
        account("admin", 1L, ActorRole.ADMIN);
        when(accountFacade.findAccountFullName(anyLong())).thenReturn(Optional.empty());
        when(accountFacade.findAccountFullName(10L)).thenReturn(Optional.of("Manager One"));
    }

    private void account(String uid, long id, ActorRole role) {
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(id)).thenReturn(List.of());
        when(tokenVerificationService.verify("test-uid:" + uid))
                .thenReturn(new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of())));
    }

    private OperationsAuditEvent save(String action, String outcome, Long actorId, Instant at, String metadata) {
        return auditRepository.saveAndFlush(OperationsAuditEvent.builder().action(action).outcome(outcome)
                .actorId(actorId).correlationId("c-" + action).metadata(metadata).occurredAt(at).build());
    }

    private MockHttpServletRequestBuilder read(String token, String query) {
        MockHttpServletRequestBuilder request = get(URL + (query == null ? "" : "?" + query))
                .header("X-Correlation-Id", CORR);
        return token == null ? request : request.header("Authorization", token);
    }

    private JsonNode body(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Manager reads filtered rows: whitelisted metadata only, actor id + name, nothing written")
    void managerReadsFilteredRowsWithWhitelistedMetadata() throws Exception {
        Instant now = Instant.now();
        save("WORKSHOP_PACKAGE_AVAILABILITY_CHANGED", "SUCCESS", 10L, now.minusSeconds(30),
                "{\"toState\":\"ACTIVE\",\"phone\":\"0900000000\",\"reasonCode\":\"x@y.com\",\"bookingCode\":\"BK-1\"}");
        save("WORKSHOP_PACKAGE_AVAILABILITY_CHANGED", "DENIED", 101L, now.minusSeconds(20), "{\"code\":\"FORBIDDEN\"}");
        save("CHECK_IN_REVERSED", "SUCCESS", null, now.minusSeconds(10), "[1,2]"); // valid JSON but not an object; the column is JSON-typed
        long before = auditRepository.count();

        JsonNode all = body(read(MANAGER, null), 200);
        assertThat(all.get("items")).hasSize(3);
        assertThat(all.get("nextCursor").isNull()).isTrue();
        assertThat(all.get("items").get(0).get("action").asText()).isEqualTo("CHECK_IN_REVERSED");
        assertThat(all.get("items").get(0).path("actor").isMissingNode()
                || all.get("items").get(0).get("actor").isNull()).as("system action has no actor").isTrue();
        assertThat(all.get("items").get(0).get("metadata").size()).isZero();

        JsonNode filtered = body(read(MANAGER, "action=WORKSHOP_PACKAGE_AVAILABILITY_CHANGED&outcome=SUCCESS"), 200);
        assertThat(filtered.get("items")).hasSize(1);
        JsonNode row = filtered.get("items").get(0);
        assertThat(row.get("actor").get("accountId").asLong()).isEqualTo(10L);
        assertThat(row.get("actor").get("displayName").asText()).isEqualTo("Manager One");
        assertThat(row.get("correlationId").asText()).isEqualTo("c-WORKSHOP_PACKAGE_AVAILABILITY_CHANGED");
        assertThat(row.get("metadata").fieldNames()).toIterable().containsExactlyInAnyOrder("toState", "bookingCode");
        assertThat(row.toString()).doesNotContain("0900000000").doesNotContain("@");

        JsonNode byActor = body(read(MANAGER, "actorId=101"), 200);
        assertThat(byActor.get("items")).hasSize(1);
        assertThat(byActor.get("items").get(0).get("actor").get("displayName").isNull()).isTrue();

        assertThat(auditRepository.count()).as("reading writes no audit row (FR-053)").isEqualTo(before);
    }

    @Test
    @DisplayName("Staff and Admin get 403 and no data; each refused attempt writes one DENIED row")
    void staffAndAdminAreRefusedAndAudited() throws Exception {
        save("CHECK_IN_REVERSED", "SUCCESS", 10L, Instant.now(), "{\"code\":\"SECRET-ROW\"}");

        for (String caller : new String[] {STAFF, ADMIN}) {
            String text = mockMvc.perform(read(caller, null)).andExpect(status().isForbidden()).andReturn()
                    .getResponse().getContentAsString();
            assertThat(text).doesNotContain("items").doesNotContain("SECRET-ROW");
        }
        mockMvc.perform(read(null, null)).andExpect(status().isUnauthorized());

        List<OperationsAuditEvent> denied = auditRepository.findAll().stream()
                .filter(e -> AuditReviewService.DENIED_ACTION.equals(e.getAction())).toList();
        assertThat(denied).hasSize(2).allSatisfy(e -> {
            assertThat(e.getOutcome()).isEqualTo("DENIED");
            assertThat(e.getCorrelationId()).isEqualTo(CORR);
        });
        assertThat(denied).extracting(OperationsAuditEvent::getActorId).containsExactlyInAnyOrder(101L, 1L);
    }

    @Test
    @DisplayName("Default view is the last 7 days; an explicit from reaches older rows")
    void defaultWindowIsSevenDays() throws Exception {
        Instant now = Instant.now();
        save("OLD_ROW", "SUCCESS", null, now.minus(Duration.ofDays(8)), null);
        save("RECENT_ROW", "SUCCESS", null, now.minus(Duration.ofDays(1)), null);

        JsonNode defaults = body(read(MANAGER, null), 200);
        assertThat(defaults.get("items")).hasSize(1);
        assertThat(defaults.get("items").get(0).get("action").asText()).isEqualTo("RECENT_ROW");

        JsonNode explicit = body(read(MANAGER, "from=" + now.minus(Duration.ofDays(30))), 200);
        assertThat(explicit.get("items")).hasSize(2);

        JsonNode bounded = body(read(MANAGER, "from=" + now.minus(Duration.ofDays(30)) + "&to="
                + now.minus(Duration.ofDays(5))), 200);
        assertThat(bounded.get("items")).hasSize(1);
        assertThat(bounded.get("items").get(0).get("action").asText()).isEqualTo("OLD_ROW");
    }

    @Test
    @DisplayName("Keyset paging under concurrent inserts: no duplicate, no skipped row (FR-050)")
    void pagingIsStableWhileRowsAreInserted() throws Exception {
        Instant now = Instant.now();
        List<Long> original = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            original.add(save("ORIGINAL_" + i, "SUCCESS", null, now.minusSeconds(60 - i), null).getId());
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService writers = Executors.newFixedThreadPool(3);
        for (int w = 0; w < 3; w++) {
            int writer = w;
            writers.submit(() -> {
                int n = 0;
                while (!stop.get()) {
                    save("LIVE_" + writer + "_" + n++, "SUCCESS", null, Instant.now(), null);
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
                Thread.sleep(30); // let the writers insert between pages
                assertThat(++pages).as("paging terminates").isLessThan(200);
            } while (cursor != null);
        } finally {
            stop.set(true);
            writers.shutdown();
            writers.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }

        assertThat(new HashSet<>(seen)).as("no duplicate id across pages").hasSameSizeAs(seen);
        assertThat(seen).as("newest first, strictly descending").isSortedAccordingTo((a, b) -> Long.compare(b, a));
        Set<Long> seenSet = new HashSet<>(seen);
        assertThat(seenSet).as("no original row skipped").containsAll(original);
    }

    @Test
    @DisplayName("Bad limit, cursor, outcome or time range is the existing 400 VALIDATION_ERROR with the correlation id")
    void badParametersAreValidationErrors() throws Exception {
        String from = Instant.now().toString();
        String earlier = Instant.now().minus(Duration.ofDays(1)).toString();
        for (String query : new String[] {"limit=0", "limit=101", "limit=abc", "cursor=!!!", "outcome=NOPE",
                "from=" + from + "&to=" + earlier, "from=yesterday", "actorId=abc"}) {
            JsonNode problem = body(read(MANAGER, query), 400);
            assertThat(problem.get("code").asText()).as(query).isEqualTo("VALIDATION_ERROR");
            assertThat(problem.get("correlationId").asText()).isEqualTo(CORR);
        }
    }
}
