package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupConfirmationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupMagicLinkTokens;
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
import org.springframework.test.web.servlet.ResultActions;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T116 (US7) on MySQL through the real routes: group lookup, Magic-Link exchange (the buyer's full group view
 * with QR, no contact data, no VNPay reference), a buyer's pending-hold release refused once a payment attempt
 * exists, and the confirmation resend with its limits (FR-048, FR-049).
 */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class BookingGroupRecoveryMySqlIT {

    private static final String EMAIL = "recovery.buyer@example.com";
    private static final String PHONE = "+84900000001";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupConfirmationService confirmation;
    @Autowired private GroupNotificationService notifications;
    @Autowired private BookingGroupRepository groups;
    @Autowired private GroupPaymentAttemptRepository attempts;
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
                    "BookingGroupRecoveryMySqlIT requires real MySQL evidence");
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
        location = locations.save(WorkshopLocation.builder().code("LOC-RCV").displayName("Recovery Hub")
                .addressSnapshot("1 Recovery St").active(true).build());
        hourOffset = 0;
    }

    @Test
    @DisplayName("T116-1: a Guest finds the group with its code and one stored contact; a wrong contact answers 404")
    void guestLookup() throws Exception {
        String code = confirmedGroup(2);

        mockMvc.perform(post("/api/v1/workshop-booking-group-lookups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"" + code + "\",\"contactEmail\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupCode").value(code))
                .andExpect(jsonPath("$.tickets.length()").value(2));
        mockMvc.perform(post("/api/v1/workshop-booking-group-lookups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"" + code + "\",\"contactPhone\":\"" + PHONE + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/workshop-booking-group-lookups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"" + code + "\",\"contactEmail\":\"someone.else@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T116-2: the Magic Link opens the buyer's full group view with QR and no contact data or VNPay reference")
    void magicLinkExchange() throws Exception {
        String code = confirmedGroup(2);
        String token = magicToken(code);

        MvcResult opened = mockMvc.perform(post("/api/v1/workshop-booking-group-magic-links/exchanges")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andReturn();

        String body = opened.getResponse().getContentAsString();
        JsonNode view = JSON.readTree(body);
        assertThat(view.get("groupCode").asText()).isEqualTo(code);
        assertThat(view.get("tickets")).hasSize(2).allSatisfy(t ->
                assertThat(t.path("qrReference").asText()).isNotBlank());
        assertThat(body).doesNotContain(EMAIL).doesNotContain(PHONE).doesNotContain(token)
                .doesNotContain("vnp_").doesNotContain("merchant");
        assertThat(opened.getResponse().getHeader("Cache-Control")).contains("no-store");

        mockMvc.perform(post("/api/v1/workshop-booking-group-magic-links/exchanges")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"not-a-real-token\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T116-3: the buyer releases an unpaid hold once; with a payment attempt started the release is refused")
    void pendingHoldRelease() throws Exception {
        String free = pendingGroup(1);
        release(free, "release-" + UUID.randomUUID()).andExpect(status().isNoContent());
        assertThat(groups.findByGroupCode(free).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(count("capacity_holds WHERE state = 'ACTIVE'")).isZero();

        String started = pendingGroup(1);
        BookingGroup group = groups.findByGroupCode(started).orElseThrow();
        attempts.save(GroupPaymentAttempt.builder().group(group).attemptReference("ATT-" + UUID.randomUUID())
                .merchantReference("MER-" + UUID.randomUUID().toString().substring(0, 12)).amountVnd(150_000)
                .attemptStatus(PaymentAttemptStatus.PENDING).expiresAt(Instant.now().plusSeconds(600))
                .createdAt(Instant.now()).build());
        release(started, "release-" + UUID.randomUUID()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_EXISTS"));
        assertThat(groups.findByGroupCode(started).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(count("capacity_holds WHERE state = 'ACTIVE'")).isEqualTo(1);
    }

    @Test
    @DisplayName("T116-4: the confirmation is resent to the stored email within the limit; the next request is rate limited")
    void confirmationResendAndLimit() throws Exception {
        String code = confirmedGroup(1);
        long baseline = kindCount(code, GroupNotificationService.KIND_GROUP_CONFIRMATION_RESEND);

        resend(code, "resend-" + UUID.randomUUID()).andExpect(status().isAccepted());
        assertThat(kindCount(code, GroupNotificationService.KIND_GROUP_CONFIRMATION_RESEND)).isEqualTo(baseline + 1);
        String sameKey = "resend-" + UUID.randomUUID();
        resend(code, sameKey).andExpect(status().isAccepted());
        resend(code, sameKey).andExpect(status().isAccepted());
        resend(code, "resend-" + UUID.randomUUID()).andExpect(status().isAccepted());

        resend(code, "resend-" + UUID.randomUUID()).andExpect(status().isTooManyRequests());
        // a wrong contact never reveals the group
        mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/confirmation-deliveries", code)
                        .header("Idempotency-Key", "resend-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"someone.else@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T118 SC-007: create, confirm, lookup, Magic-Link exchange and resend leave no contact, token or QR in the logs")
    void logs_holdNoContactTokenOrQr(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String code = confirmedGroup(1);
        String token = magicToken(code);
        mockMvc.perform(post("/api/v1/workshop-booking-group-lookups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupCode\":\"" + code + "\",\"contactEmail\":\"" + EMAIL + "\"}")).andExpect(status().isOk());
        MvcResult opened = mockMvc.perform(post("/api/v1/workshop-booking-group-magic-links/exchanges")
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String qr = JSON.readTree(opened.getResponse().getContentAsString()).get("tickets").get(0).get("qrReference").asText();
        resend(code, "resend-" + UUID.randomUUID()).andExpect(status().isAccepted());

        assertThat(output.getAll()).doesNotContain(EMAIL).doesNotContain(PHONE).doesNotContain(token).doesNotContain(qr);
    }

    // ---- helpers ----

    private ResultActions release(String code, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/pending-hold-release", code)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactEmail\":\"" + EMAIL + "\",\"reason\":\"Changed my mind\"}"));
    }

    private ResultActions resend(String code, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/confirmation-deliveries", code)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactEmail\":\"" + EMAIL + "\"}"));
    }

    private String confirmedGroup(int participants) throws Exception {
        String code = pendingGroup(participants);
        confirmation.confirmPaidGroup(code, 150_000L * participants, "ipn-" + code, "op-" + code, "corr");
        return code;
    }

    private String pendingGroup(int participants) throws Exception {
        Long pkg = packages.save(WorkshopPackage.builder().name("Recovery package " + UUID.randomUUID())
                .description("fixture").publicPrice(150_000L).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();
        WorkshopSession session = session(10);
        MvcResult quote = mockMvc.perform(post("/api/v1/workshop-cart-quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"packageId\":" + pkg + ",\"sessionReferences\":[\"" + session.getReference()
                                + "\"],\"participantCount\":" + participants + "}]}"))
                .andExpect(status().isCreated()).andReturn();
        String quoteRef = JSON.readTree(quote.getResponse().getContentAsString()).get("quoteRef").asText();
        MvcResult created = mockMvc.perform(post("/api/v1/workshop-booking-groups")
                        .header("Idempotency-Key", "recovery-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"" + EMAIL
                                + "\",\"contactPhone\":\"" + PHONE + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(created.getResponse().getContentAsString()).get("groupCode").asText();
    }

    /** The raw token as the buyer would read it from the confirmation email's access link. */
    private String magicToken(String code) {
        Long groupId = groups.findByGroupCode(code).orElseThrow().getId();
        BookingGroupNotificationIntent intent = intents.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .filter(i -> GroupNotificationService.KIND_GROUP_CONFIRMATION.equals(i.getNotificationKind()))
                .findFirst().orElseThrow();
        BookingNotificationDeliveryPayload payload = notifications.readPayload(intent.getNotificationReference());
        String token = GroupMagicLinkTokens.tokenOf(payload.bookingAccessLink());
        assertThat(token).isNotBlank();
        return token;
    }

    private long kindCount(String code, String kind) {
        Long groupId = groups.findByGroupCode(code).orElseThrow().getId();
        return intents.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .filter(i -> kind.equals(i.getNotificationKind())).count();
    }

    private WorkshopSession session(int daysAhead) {
        Instant start = Instant.now().plus(Duration.ofDays(daysAhead)).plus(hourOffset++, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.MINUTES);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalTime startTime = start.atZone(zone).toLocalTime();
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(10)
                .active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED)
                .build());
    }

    private int count(String fromClause) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
        return n == null ? 0 : n;
    }
}
