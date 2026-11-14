package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupConfirmationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T097 on MySQL: the booking-group flow through the real routes and services, from cart quote to
 * group creation, confirmation and expiry, for a paid, a mixed free/paid, a 0 VND and a deferred group, and a
 * cart with one line out of capacity. Late payment and recipient/QR changes keep their own MySQL suites
 * ({@code LatePaymentReviewIT}, {@code TicketRecipientIT}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class GroupBookingFlowMySqlIT {

    private static final String EMAIL = "flow.buyer@example.com";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupConfirmationService confirmation;
    @Autowired private BookingGroupRepository groups;
    @Autowired private TicketBookingRepository tickets;
    @Autowired private TicketPriceSnapshotRepository snapshots;
    @Autowired private BookingGroupNotificationIntentRepository intents;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private int hourOffset;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "GroupBookingFlowMySqlIT requires real MySQL evidence");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("late_payment_reviews", "booking_group_notification_intents",
                "group_payment_attempts", "booking_group_magic_links", "ticket_price_snapshots", "ticket_bookings",
                "booking_group_line_sessions", "booking_group_lines", "booking_groups", "workshop_cart_quotes",
                "booking_idempotency_records", "booking_audit_evidence", "facade_command_idempotency_records",
                "ticket_session_entitlements", "attendance_tickets", "capacity_holds", "operations_audit_events",
                "workshop_sessions", "workshop_slots", "workshop_packages", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        location = locations.save(WorkshopLocation.builder().code("LOC-FLOW").displayName("Flow Hub")
                .addressSnapshot("1 Flow St").active(true).build());
        hourOffset = 0;
    }

    @Test
    @DisplayName("T097-1: a paid group is created pending, priced per ticket and confirmed once with a QR per ticket")
    void paidGroup() throws Exception {
        Long pkg = packageOf(150_000);
        WorkshopSession session = session(10);
        JsonNode quote = quote(List.of(line(pkg, session, 2)));
        assertThat(quote.get("paymentMode").asText()).isEqualTo("VNPAY");
        assertThat(quote.get("groupCollectionVnd").asLong()).isEqualTo(300_000);

        JsonNode group = createGroup(quote.get("quoteRef").asText(), 201);
        String code = group.get("groupCode").asText();
        assertThat(group.get("groupStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(collectedByTickets(code)).isEqualTo(300_000);
        assertThat(count("capacity_holds WHERE state = 'ACTIVE'")).isEqualTo(1);

        var confirmed = confirmation.confirmPaidGroup(code, 300_000, "ipn-" + code, "op-" + code, "corr");
        var replay = confirmation.confirmPaidGroup(code, 300_000, "ipn-" + code, "op-" + code, "corr");

        assertThat(confirmed.groupStatus()).isEqualTo("CONFIRMED");
        assertThat(replay.groupStatus()).isEqualTo("CONFIRMED");
        assertThat(count("attendance_tickets")).isEqualTo(2);
        assertThat(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(groupId(code)))
                .allSatisfy(t -> {
                    assertThat(t.getTicketStatus()).isEqualTo(BookingStatus.CONFIRMED);
                    assertThat(t.getQrReference()).isNotBlank();
                });
        assertThat(kindsOf(code)).containsOnlyOnce(GroupNotificationService.KIND_GROUP_CONFIRMATION);
    }

    @Test
    @DisplayName("T097-2: a mixed free/paid group collects only the paid line and confirms the free ticket with it")
    void mixedFreeAndPaidGroup() throws Exception {
        JsonNode quote = quote(List.of(line(packageOf(200_000), session(10), 1), line(packageOf(0), session(11), 1)));
        assertThat(quote.get("groupCollectionVnd").asLong()).isEqualTo(200_000);

        String code = createGroup(quote.get("quoteRef").asText(), 201).get("groupCode").asText();
        confirmation.confirmPaidGroup(code, 200_000, "ipn-" + code, "op-" + code, "corr");

        assertThat(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(groupId(code)))
                .hasSize(2).allMatch(t -> t.getTicketStatus() == BookingStatus.CONFIRMED);
        assertThat(count("attendance_tickets")).isEqualTo(2);
    }

    @Test
    @DisplayName("T097-3: a 0 VND group needs no gateway payment and confirms internally")
    void zeroVndGroup() throws Exception {
        JsonNode quote = quote(List.of(line(packageOf(0), session(10), 2)));
        assertThat(quote.get("paymentMode").asText()).isEqualTo("INTERNAL_FREE");
        String code = createGroup(quote.get("quoteRef").asText(), 201).get("groupCode").asText();

        var confirmed = confirmation.confirmInternalGroup(code, "op-" + code, "corr");

        assertThat(confirmed.groupStatus()).isEqualTo("CONFIRMED");
        assertThat(collectedByTickets(code)).isZero();
        assertThat(count("attendance_tickets")).isEqualTo(2);
    }

    @Test
    @DisplayName("T097-4: a group collecting under the 5,000 VND floor is deferred to checkout and confirms internally")
    void deferredGroup() throws Exception {
        JsonNode quote = quote(List.of(line(packageOf(3_000), session(10), 1)));
        assertThat(quote.get("paymentMode").asText()).isEqualTo("INTERNAL_DEFERRED");
        assertThat(quote.get("groupCollectionVnd").asLong()).isZero();
        assertThat(quote.get("remainingAtCheckoutVnd").asLong()).isEqualTo(3_000);
        String code = createGroup(quote.get("quoteRef").asText(), 201).get("groupCode").asText();

        assertThat(confirmation.confirmInternalGroup(code, "op-" + code, "corr").groupStatus()).isEqualTo("CONFIRMED");
        assertThat(snapshots.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getCollectionKind()).isEqualTo(TicketCollectionKind.DEFERRED_BELOW_VNPAY_MINIMUM);
            assertThat(s.getRemainingVnd()).isEqualTo(3_000);
        });
    }

    @Test
    @DisplayName("T097-5: one full line refuses the whole cart with 409, names that line and creates nothing")
    void oneLineWithoutCapacity() throws Exception {
        Long pkg = packageOf(100_000);
        WorkshopSession open = session(10);
        WorkshopSession small = session(11, 1);
        JsonNode quote = quote(List.of(line(pkg, open, 1), line(pkg, small, 1)));
        // Another buyer takes the only seat after the quote.
        String other = createGroup(quote(List.of(line(pkg, small, 1))).get("quoteRef").asText(), 201)
                .get("groupCode").asText();
        assertThat(other).isNotBlank();
        int holdsBefore = count("capacity_holds");

        MvcResult refused = mockMvc.perform(post("/api/v1/workshop-booking-groups")
                        .header("Idempotency-Key", "flow-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(quote.get("quoteRef").asText())))
                .andExpect(status().isConflict()).andReturn();

        JsonNode body = JSON.readTree(refused.getResponse().getContentAsString());
        // FR-031: pricing names the full line even though the session is no longer selectable, and holds nothing.
        assertThat(body.get("code").asText()).isEqualTo("CART_LINE_UNAVAILABLE");
        assertThat(body.get("failingLines").toString()).isEqualTo("[1]");
        assertThat(count("capacity_holds")).isEqualTo(holdsBefore);
        assertThat(count("booking_groups")).isEqualTo(1);
    }

    @Test
    @DisplayName("T097-6: an unpaid group whose hold ran out is released whole and the buyer is told once")
    void expiredGroup() throws Exception {
        JsonNode quote = quote(List.of(line(packageOf(150_000), session(10), 2)));
        String code = createGroup(quote.get("quoteRef").asText(), 201).get("groupCode").asText();
        BookingGroup stored = groups.findByGroupCode(code).orElseThrow();
        stored.setHoldExpiresAt(Instant.now().minusSeconds(30));
        stored.setPaymentDeadlineAt(Instant.now().minusSeconds(150));
        groups.saveAndFlush(stored);
        // (hold ran out)
        if (false) jdbc.update("UPDATE booking_groups SET hold_expires_at = ?, payment_deadline_at = ? WHERE group_code = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(30)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(150)), code);

        assertThat(confirmation.releaseExpiredGroup(code, "EXPIRE-" + code, "corr")).isTrue();
        assertThat(confirmation.releaseExpiredGroup(code, "EXPIRE-" + code, "corr")).isFalse();

        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(count("capacity_holds WHERE state = 'ACTIVE'")).isZero();
        assertThat(count("attendance_tickets")).isZero();
        assertThat(kindsOf(code)).containsExactly(GroupNotificationService.KIND_GROUP_HOLD_EXPIRED);
    }

    // ---- helpers ----

    private JsonNode quote(List<String> lines) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workshop-cart-quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[" + String.join(",", lines) + "]}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createGroup(String quoteRef, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workshop-booking-groups")
                        .header("Idempotency-Key", "flow-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(quoteRef)))
                .andExpect(status().is(expectedStatus)).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String createBody(String quoteRef) {
        return "{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"" + EMAIL + "\",\"contactPhone\":\"0900000000\"}";
    }

    private static String line(Long packageId, WorkshopSession session, int participants) {
        return "{\"packageId\":" + packageId + ",\"sessionReferences\":[\"" + session.getReference()
                + "\"],\"participantCount\":" + participants + "}";
    }

    private Long packageOf(long price) {
        return packages.save(WorkshopPackage.builder().name("Flow package " + price + "-" + UUID.randomUUID())
                .description("fixture").publicPrice(price).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();
    }

    private WorkshopSession session(int daysAhead) {
        return session(daysAhead, 10);
    }

    private WorkshopSession session(int daysAhead, int capacity) {
        Instant start = Instant.now().plus(Duration.ofDays(daysAhead)).plus(hourOffset++, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.MINUTES);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalTime startTime = start.atZone(zone).toLocalTime();
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(capacity)
                .active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(capacity).state(SessionState.PUBLISHED)
                .build());
    }

    private Long groupId(String code) {
        return groups.findByGroupCode(code).orElseThrow().getId();
    }

    private long collectedByTickets(String code) {
        return tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(groupId(code)).stream()
                .mapToLong(t -> snapshots.findByTicketId(t.getId()).orElseThrow().getCollectedAtBookingVnd()).sum();
    }

    private List<String> kindsOf(String code) {
        return intents.findByGroupIdOrderByCreatedAtAsc(groupId(code)).stream()
                .map(BookingGroupNotificationIntent::getNotificationKind).toList();
    }

    private int count(String fromClause) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
        return n == null ? 0 : n;
    }
}
