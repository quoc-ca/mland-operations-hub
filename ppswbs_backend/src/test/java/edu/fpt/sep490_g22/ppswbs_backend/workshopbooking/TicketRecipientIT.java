package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T095 / FR-041 (decision 2026-11-13) on MySQL with Flyway:
 * Member/Guest/Staff proof flows, strangers refused, first send keeps the QR, a change rotates it through 003.2,
 * the 3-change online limit, Staff changes beyond it with a reason, no change after check-in, idempotent replay,
 * OTP lock/rate limit, and masked-only audit.
 */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.booking.auth.allow-test-tokens=true"})
class TicketRecipientIT {

    private static final String BUYER_EMAIL = "buyer.owner@example.com";
    private static final String BUYER_PHONE = "0901234567";
    private static final long MEMBER_ID = 501L;
    private static final long OTHER_MEMBER_ID = 502L;
    private static final long STAFF_ID = 601L;
    private static final long STAFF_UNASSIGNED_ID = 603L;
    private static final long MANAGER_ID = 701L;
    private static final long STAFF_OTHER_LOCATION_ID = 602L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BookingGroupRepository groups;
    @Autowired private BookingGroupLineRepository lines;
    @Autowired private TicketBookingRepository tickets;
    @Autowired private TicketRecipientRepository recipients;
    @Autowired private TicketRecipientChangeRepository changes;
    @Autowired private BookingGroupNotificationIntentRepository intents;
    @Autowired private GroupNotificationService notifications;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;
    @Autowired private AttendanceTicketRepository attendanceTickets;
    @Autowired private WorkshopOperationsFacade operations;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private WorkshopLocation otherLocation;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "TicketRecipientIT requires real MySQL evidence");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("booking_group_notification_intents", "booking_buyer_verifications",
                "ticket_recipient_changes", "ticket_recipients", "ticket_price_snapshots", "ticket_bookings",
                "booking_group_line_sessions", "booking_group_lines", "booking_groups", "booking_idempotency_records",
                "booking_audit_evidence", "facade_command_idempotency_records", "ticket_qr_generations",
                "ticket_session_entitlements", "attendance_tickets", "capacity_holds", "operations_audit_events",
                "session_staff_assignments", "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        location = locations.save(WorkshopLocation.builder().code("LOC-RCP-01").displayName("Recipient Hub")
                .addressSnapshot("1 Test St").active(true).build());
        otherLocation = locations.save(WorkshopLocation.builder().code("LOC-RCP-02").displayName("Other Hub")
                .addressSnapshot("2 Test St").active(true).build());

        account("member-owner", MEMBER_ID, ActorRole.MEMBER, List.of());
        account("member-other", OTHER_MEMBER_ID, ActorRole.MEMBER, List.of());
        account("staff-shop", STAFF_ID, ActorRole.STAFF, List.of(location.getId()));
        account("staff-elsewhere", STAFF_OTHER_LOCATION_ID, ActorRole.STAFF, List.of(otherLocation.getId()));
    }

    @Test
    @DisplayName("T095-1: a Guest buyer verifies by OTP; the first send keeps the QR and is not counted")
    void guestBuyer_firstSend_keepsQrAndIsNotCounted() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        String qr = ticket.getQrReference();
        String reference = verifyBuyer(ticket);

        recipient(ticket, "friend.one@example.com", reference, null, null, key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientEmailMasked").value("fr***@example.com"))
                .andExpect(jsonPath("$.onlineRecipientChangesRemaining").value(3));

        assertThat(tickets.findById(ticket.getId()).orElseThrow().getQrReference()).isEqualTo(qr);
        assertThat(recipients.findByTicketId(ticket.getId()).orElseThrow().getChangeCount()).isZero();
        assertThat(kinds(ticket)).contains(GroupNotificationService.KIND_TICKET_RECIPIENT);
    }

    @Test
    @DisplayName("T095-2: strangers and recipients cannot change a recipient; nothing changes")
    void stranger_withoutProof_isRefusedWithoutSideEffect() throws Exception {
        TicketBooking ticket = confirmedTicket(MEMBER_ID);
        recipient(ticket, "friend.one@example.com", null, null, "member-owner", key()).andExpect(status().isOk());
        String qr = tickets.findById(ticket.getId()).orElseThrow().getQrReference();

        recipient(ticket, "attacker@example.com", null, null, null, key())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_REQUIRED"));
        recipient(ticket, "attacker@example.com", UUID.randomUUID().toString(), null, null, key())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_REQUIRED"));
        recipient(ticket, "attacker@example.com", null, null, "member-other", key())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECIPIENT_CHANGE_FORBIDDEN"));
        // A recipient asking for a code with the recipient email gets no OTP and no reference.
        mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications", ticket.getGroup().getGroupCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"friend.one@example.com\"}"))
                .andExpect(status().isAccepted());
        assertThat(kinds(ticket.getGroup().getId())).doesNotContain(GroupNotificationService.KIND_BUYER_VERIFICATION_OTP);

        assertThat(tickets.findById(ticket.getId()).orElseThrow().getQrReference()).isEqualTo(qr);
        assertThat(recipients.findByTicketId(ticket.getId()).orElseThrow().getRecipientEmail())
                .isEqualTo("friend.one@example.com");
        assertThat(changes.findByTicketIdOrderByChangedAtAsc(ticket.getId())).hasSize(1);
    }

    @Test
    @DisplayName("T095-3: a change revokes the old QR at once, issues a new one and notifies both people")
    void memberChange_rotatesQr_andNotifiesNewAndOldRecipient() throws Exception {
        TicketBooking ticket = confirmedTicket(MEMBER_ID);
        recipient(ticket, "friend.one@example.com", null, null, "member-owner", key()).andExpect(status().isOk());
        String oldQr = tickets.findById(ticket.getId()).orElseThrow().getQrReference();

        recipient(ticket, "friend.two@example.com", null, null, "member-owner", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineRecipientChangesRemaining").value(2));

        TicketBooking reloaded = tickets.findById(ticket.getId()).orElseThrow();
        AttendanceTicket attendance = attendanceTickets.findByReference(reloaded.getOperationsTicketReference()).orElseThrow();
        assertThat(reloaded.getQrReference()).isNotEqualTo(oldQr).isEqualTo(attendance.getQrReference());
        assertThat(attendance.getQrGeneration()).isEqualTo(2);
        assertThat(count("ticket_qr_generations WHERE qr_reference = '" + oldQr + "'")).isEqualTo(1);
        assertThat(kinds(ticket)).contains(GroupNotificationService.KIND_TICKET_RECIPIENT_REVOKED);
        TicketRecipientChange change = changes.findByTicketIdOrderByChangedAtAsc(ticket.getId()).getLast();
        assertThat(change.getOldEmailMasked()).isEqualTo("fr***@example.com");
        assertThat(change.getNewEmailMasked()).isEqualTo("fr***@example.com");
        assertThat(change.isQrRotated()).isTrue();
        assertThat(change.getChannel()).isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("T095-4: resending to the same email keeps the QR and is not counted")
    void sameEmail_resend_keepsQr() throws Exception {
        TicketBooking ticket = confirmedTicket(MEMBER_ID);
        recipient(ticket, "friend.one@example.com", null, null, "member-owner", key()).andExpect(status().isOk());
        String qr = tickets.findById(ticket.getId()).orElseThrow().getQrReference();

        recipient(ticket, "Friend.One@example.com", null, null, "member-owner", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineRecipientChangesRemaining").value(3));

        assertThat(tickets.findById(ticket.getId()).orElseThrow().getQrReference()).isEqualTo(qr);
        assertThat(kinds(ticket)).contains(GroupNotificationService.KIND_TICKET_RESEND);
        assertThat(count("ticket_qr_generations")).isZero();
    }

    @Test
    @DisplayName("T095-5: the 4th online change is refused with no side effect; Staff may continue with a reason")
    void fourthOnlineChange_refused_staffContinuesUncounted() throws Exception {
        TicketBooking ticket = confirmedTicket(MEMBER_ID);
        recipient(ticket, "r0@example.com", null, null, "member-owner", key()).andExpect(status().isOk());
        for (int i = 1; i <= 3; i++) {
            recipient(ticket, "r" + i + "@example.com", null, null, "member-owner", key()).andExpect(status().isOk());
        }
        String qr = tickets.findById(ticket.getId()).orElseThrow().getQrReference();
        int intentsBefore = kinds(ticket).size();

        recipient(ticket, "r4@example.com", null, null, "member-owner", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPIENT_CHANGE_LIMIT_REACHED"));
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getQrReference()).isEqualTo(qr);
        assertThat(kinds(ticket)).hasSize(intentsBefore);
        assertThat(count("ticket_qr_generations")).isEqualTo(3);

        recipient(ticket, "r4@example.com", verifyBuyer(ticket), null, "staff-shop", key())
                .andExpect(status().isBadRequest());
        recipient(ticket, "r4@example.com", verifyBuyer(ticket), "Buyer at the counter", "staff-elsewhere", key())
                .andExpect(status().isForbidden());
        recipient(ticket, "r4@example.com", null, "Buyer at the counter", "staff-shop", key())
                .andExpect(status().isUnauthorized());
        recipient(ticket, "r4@example.com", verifyBuyer(ticket), "Buyer at the counter", "staff-shop", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineRecipientChangesRemaining").value(0));
        TicketRecipientChange staffChange = changes.findByTicketIdOrderByChangedAtAsc(ticket.getId()).getLast();
        assertThat(staffChange.getChannel()).isEqualTo("SHOP");
        assertThat(staffChange.isCountedOnline()).isFalse();
        assertThat(staffChange.getReason()).isEqualTo("Buyer at the counter");
        assertThat(recipients.findByTicketId(ticket.getId()).orElseThrow().getChangeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("T135/T143: Staff need scope AND an assignment; a Manager changes a recipient globally with a verification reference")
    void staffNeedAnAssignmentAndAManagerIsGlobal() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        account("staff-unassigned", STAFF_UNASSIGNED_ID, ActorRole.STAFF, List.of(location.getId()));
        account("manager-global", MANAGER_ID, ActorRole.MANAGER, List.of());

        // Scope for the right location is not enough without an assigned session of the group.
        recipient(ticket, "r1@example.com", verifyBuyer(ticket), "Buyer at the counter", "staff-unassigned", key())
                .andExpect(status().isForbidden());
        assertThat(kinds(ticket)).doesNotContain(GroupNotificationService.KIND_TICKET_RECIPIENT);
        // A Manager with no scope and no assignment still needs the verification reference and a reason.
        recipient(ticket, "r1@example.com", null, "Buyer at the counter", "manager-global", key())
                .andExpect(status().isUnauthorized());
        recipient(ticket, "r1@example.com", verifyBuyer(ticket), null, "manager-global", key())
                .andExpect(status().isBadRequest());
        recipient(ticket, "r1@example.com", verifyBuyer(ticket), "Buyer phoned the head office", "manager-global", key())
                .andExpect(status().isOk());
        assertThat(changes.findByTicketIdOrderByChangedAtAsc(ticket.getId()).getLast().getChannel()).isEqualTo("SHOP");
        // T143: each refused attempt left a denied-attempt audit entry without contact data.
        assertThat(count("booking_audit_evidence WHERE action = 'TICKET_RECIPIENT_CHANGE' AND outcome = 'DENIED' AND actor_id = "
                + STAFF_UNASSIGNED_ID)).isEqualTo(1);
        assertThat(count("booking_audit_evidence WHERE action = 'TICKET_RECIPIENT_CHANGE' AND outcome = 'DENIED' AND actor_id = "
                + MANAGER_ID)).isEqualTo(1); // the Manager call without a buyer verification reference
    }

    private ResultActions internalOtp(TicketBooking ticket, String uid, String body) throws Exception {
        jdbc.update("UPDATE booking_buyer_verifications SET created_at = created_at - INTERVAL 2 MINUTE");
        MockHttpServletRequestBuilder request = post("/api/v1/workshop-booking-groups/{g}/buyer-verifications",
                ticket.getGroup().getGroupCode()).contentType(MediaType.APPLICATION_JSON).content(body);
        if (uid != null) request.header("Authorization", "Bearer test-uid:" + uid);
        return mockMvc.perform(request);
    }

    /**
     * T156 (FR-054, Q4; 003.2 FR-045): the Staff scope of a group-level operation is judged at the CURRENT location of
     * the ticket's session. After a move to the other location, the Staff member of that location who is assigned to
     * the new session may change the recipient; the Staff member of the old location is refused and the denied attempt
     * is audited at the current location. A Manager stays global.
     */
    @Test
    @DisplayName("T156: after a ticket moved to another location, Staff of the new location (assigned) operate, Staff of the old one are refused and audited")
    void staffScopeFollowsTheCurrentLocationOfAMovedTicket() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        // Only a Package that may change location can be moved (003.2 FR-041); it is sold at both locations.
        jdbc.update("UPDATE workshop_packages SET same_location_required = 0 WHERE id = ?", ticket.getPackageId());
        edu.fpt.sep490_g22.ppswbs_backend.workshopoperations.PackageAvailabilityFixture.activateEverywhere(jdbc, ticket.getPackageId());
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(otherLocation)
                .label("Afternoon").startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(17, 0)).defaultCapacity(10)
                .active(true).build());
        LocalDate date = LocalDate.now().plusDays(4);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        WorkshopSession target = sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString())
                .location(otherLocation).slot(slot).workshopDate(date)
                .scheduledStartAt(date.atTime(14, 0).atZone(zone).toInstant())
                .scheduledEndAt(date.atTime(17, 0).atZone(zone).toInstant())
                .checkInOpensAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED).build());
        assertThat(operations.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(ticket.getOperationsTicketReference()),
                        List.of(UUID.fromString(target.getReference())))),
                TicketReplacementType.MLAND, "op-" + UUID.randomUUID(), "corr-move")).status())
                .isEqualTo(OutcomeStatus.ACCEPTED);
        jdbc.update("INSERT INTO session_staff_assignments (session_id, account_id, assigned_by_id, active) VALUES (?, ?, ?, 1)",
                target.getId(), STAFF_OTHER_LOCATION_ID, 1L);
        account("manager-global", MANAGER_ID, ActorRole.MANAGER, List.of());

        // The Staff member of the old location (assigned to the old session before the move) is refused and audited.
        recipient(ticket, "r1@example.com", verifyBuyer(ticket), "Buyer at the counter", "staff-shop", key())
                .andExpect(status().isForbidden());
        assertThat(count("booking_audit_evidence WHERE action = 'TICKET_RECIPIENT_CHANGE' AND outcome = 'DENIED' AND actor_id = "
                + STAFF_ID + " AND location_id = " + otherLocation.getId())).isEqualTo(1);
        // The Staff member of the new location, assigned to the new session, may change the recipient.
        recipient(ticket, "r1@example.com", verifyBuyer(ticket), "Buyer at the counter", "staff-elsewhere", key())
                .andExpect(status().isOk());
        // A Manager stays global.
        recipient(ticket, "r2@example.com", verifyBuyer(ticket), "Head office", "manager-global", key())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T148: Staff in scope and assigned, and any Manager, request and confirm the OTP; others are refused per class")
    void internalBuyerVerificationAuthorization() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        account("staff-unassigned", STAFF_UNASSIGNED_ID, ActorRole.STAFF, List.of(location.getId()));
        account("manager-global", MANAGER_ID, ActorRole.MANAGER, List.of());

        // Refused: 403 only for a known Staff without authority, 404 for a Member, 401 for no valid credential, 400 for a contact value.
        internalOtp(ticket, "staff-unassigned", "{}").andExpect(status().isForbidden());
        internalOtp(ticket, "staff-elsewhere", "{}").andExpect(status().isForbidden());
        internalOtp(ticket, "member-owner", "{}").andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications", ticket.getGroup().getGroupCode())
                        .header("Authorization", "Basic not-a-bearer").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        internalOtp(ticket, "staff-shop", "{\"contactEmail\":\"" + BUYER_EMAIL + "\"}").andExpect(status().isBadRequest());
        assertThat(count("booking_buyer_verifications")).isZero();

        // Allowed: assigned Staff, then a Manager with no scope; the OTP goes only to the original contact email.
        for (String uid : List.of("staff-shop", "manager-global")) {
            String body = internalOtp(ticket, uid, "{}").andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            String verificationId = objectMapper.readTree(body).path("verificationId").asText();
            String otp = latestOtp(ticket.getGroup().getId());
            mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications/{v}/confirmations",
                            ticket.getGroup().getGroupCode(), verificationId)
                            .header("Authorization", "Bearer test-uid:member-owner")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isNotFound());
            String reference = objectMapper.readTree(mockMvc.perform(post(
                            "/api/v1/workshop-booking-groups/{g}/buyer-verifications/{v}/confirmations",
                            ticket.getGroup().getGroupCode(), verificationId)
                            .header("Authorization", "Bearer test-uid:" + uid)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                    .path("buyerVerificationReference").asText();
            assertThat(reference).isNotBlank();
        }
        assertThat(count("booking_group_notification_intents WHERE notification_kind = '" + GroupNotificationService.KIND_BUYER_VERIFICATION_OTP + "'")).isEqualTo(2);
        assertThat(count("booking_group_notification_intents WHERE notification_kind <> '" + GroupNotificationService.KIND_BUYER_VERIFICATION_OTP + "'")).isZero();
        assertThat(count("booking_audit_evidence WHERE action = 'BUYER_VERIFICATION' AND outcome = 'DENIED'")).isEqualTo(5); // 2 Staff, 1 Member request, 2 Member confirmations
    }

    private JsonNode answerOf(ResultActions actions, int expectedStatus) throws Exception {
        String body = actions.andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(BUYER_EMAIL).doesNotContain(BUYER_PHONE);
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("T139/T141 (SC-008): wrong proof, non-owner and unknown group give one identical 404 on every route; 401, 403 and 429 carry no booking data")
    void everyRouteGivesTheSameSafeAnswerPerClass() throws Exception {
        TicketBooking ticket = confirmedTicket(MEMBER_ID);
        String known = ticket.getGroup().getGroupCode();
        String unknown = "GRP-NOPE0001";
        String wrong = "{\"contactEmail\":\"wrong@example.com\"}";
        account("manager-global", MANAGER_ID, ActorRole.MANAGER, List.of());

        // Wrong proof on a known group is indistinguishable from an unknown group (status, code and detail).
        record Route(String name, String method, String path, String body) { }
        List<Route> guestRoutes = List.of(
                new Route("lookup", "POST", "/api/v1/workshop-booking-group-lookups", null),
                new Route("pending-hold-release", "POST", "/api/v1/workshop-booking-groups/%s/pending-hold-release", null),
                new Route("confirmation-deliveries", "POST", "/api/v1/workshop-booking-groups/%s/confirmation-deliveries", wrong),
                new Route("payment-attempts", "POST", "/api/v1/workshop-booking-groups/%s/payment-attempts", wrong));
        for (Route route : guestRoutes) {
            JsonNode forKnown = guestCall(route.name(), route.path(), known, wrong);
            JsonNode forUnknown = guestCall(route.name(), route.path(), unknown, wrong);
            assertThat(forKnown.path("code").asText()).as(route.name()).isEqualTo("BOOKING_NOT_FOUND");
            assertThat(forKnown.path("code").asText()).as(route.name()).isEqualTo(forUnknown.path("code").asText());
            assertThat(forKnown.path("detail").asText()).as(route.name()).isEqualTo(forUnknown.path("detail").asText());
            assertThat(forKnown.path("title").asText()).as(route.name()).isEqualTo(forUnknown.path("title").asText());
        }

        // Owner read: another Member and an unknown group are the same 404; no credential is 401.
        mockMvc.perform(get("/api/v1/workshop-booking-groups/{g}", known)).andExpect(status().isUnauthorized());
        JsonNode otherMember = answerOf(mockMvc.perform(get("/api/v1/workshop-booking-groups/{g}", known)
                .header("Authorization", "Bearer test-uid:member-other")), 404);
        JsonNode unknownGroup = answerOf(mockMvc.perform(get("/api/v1/workshop-booking-groups/{g}", unknown)
                .header("Authorization", "Bearer test-uid:member-owner")), 404);
        assertThat(otherMember.path("code").asText()).isEqualTo(unknownGroup.path("code").asText());
        assertThat(otherMember.path("detail").asText()).isEqualTo(unknownGroup.path("detail").asText());
        // An unknown, expired or revoked Magic Link is the same 404.
        answerOf(mockMvc.perform(post("/api/v1/workshop-booking-group-magic-links/exchanges")
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"no-such-token\"}")), 404);

        // Magic-Link incident: no credential 401, known Staff without authority 403, with no booking data.
        String incident = "{\"action\":\"REVOKE\",\"reason\":\"Customer lost the link\"}";
        mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/magic-link-incidents", known)
                .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(incident))
                .andExpect(status().isUnauthorized());
        answerOf(mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/magic-link-incidents", known)
                .header("Authorization", "Bearer test-uid:staff-shop").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(incident)), 403);
        // Late-payment queue: no credential 401, Staff 403.
        mockMvc.perform(get("/api/v1/late-payment-reviews")).andExpect(status().isUnauthorized());
        answerOf(mockMvc.perform(get("/api/v1/late-payment-reviews").header("Authorization", "Bearer test-uid:staff-shop")), 403);

        // 429 on repeated wrong proofs carries no booking data.
        String limited = null;
        for (int i = 0; i < 12 && limited == null; i++) {
            var result = mockMvc.perform(post("/api/v1/workshop-booking-group-lookups")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"groupCode\":\"" + known + "\",\"contactEmail\":\"guess" + i + "@example.com\"}")).andReturn();
            if (result.getResponse().getStatus() == 429) limited = result.getResponse().getContentAsString();
        }
        assertThat(limited).isNotNull().doesNotContain(BUYER_EMAIL).doesNotContain(known)
                .doesNotContain(ticket.getTicketCode());
    }

    private JsonNode guestCall(String name, String pathPattern, String groupCode, String body) throws Exception {
        MockHttpServletRequestBuilder request;
        if (name.equals("lookup")) {
            request = post(pathPattern).content("{\"groupCode\":\"" + groupCode + "\",\"contactEmail\":\"wrong@example.com\"}");
        } else if (name.equals("pending-hold-release")) {
            request = post(pathPattern.formatted(groupCode)).content(
                    "{\"contactEmail\":\"wrong@example.com\",\"reason\":\"Changed my mind\"}");
        } else {
            request = post(pathPattern.formatted(groupCode)).content(body);
        }
        request.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON);
        return answerOf(mockMvc.perform(request), 404);
    }

    @Test
    @DisplayName("T095-6: no change after check-in; the refusal leaves QR, recipient and proof untouched")
    void changeAfterCheckIn_isRefusedWithoutSideEffect() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        recipient(ticket, "friend.one@example.com", verifyBuyer(ticket), null, null, key()).andExpect(status().isOk());
        jdbc.update("UPDATE ticket_session_entitlements SET attendance_state = 'CHECKED_IN', checked_in_at = NOW(6)");
        String qr = tickets.findById(ticket.getId()).orElseThrow().getQrReference();
        String reference = verifyBuyer(ticket);

        recipient(ticket, "friend.two@example.com", reference, null, null, key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_ALREADY_CHECKED_IN"));

        assertThat(tickets.findById(ticket.getId()).orElseThrow().getQrReference()).isEqualTo(qr);
        assertThat(recipients.findByTicketId(ticket.getId()).orElseThrow().getRecipientEmail())
                .isEqualTo("friend.one@example.com");
        assertThat(count("booking_buyer_verifications WHERE buyer_verification_reference = '" + reference
                + "' AND status = 'VERIFIED'")).isEqualTo(1);
    }

    @Test
    @DisplayName("T095-7: the same Idempotency-Key replays the result without a second change or QR")
    void duplicateCommand_replays_withoutSecondRotation() throws Exception {
        TicketBooking ticket = confirmedTicket(MEMBER_ID);
        recipient(ticket, "friend.one@example.com", null, null, "member-owner", key()).andExpect(status().isOk());
        String key = key();
        recipient(ticket, "friend.two@example.com", null, null, "member-owner", key).andExpect(status().isOk());
        String qrAfterFirst = tickets.findById(ticket.getId()).orElseThrow().getQrReference();

        recipient(ticket, "friend.two@example.com", null, null, "member-owner", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineRecipientChangesRemaining").value(2));

        assertThat(tickets.findById(ticket.getId()).orElseThrow().getQrReference()).isEqualTo(qrAfterFirst);
        assertThat(count("ticket_qr_generations")).isEqualTo(1);
        assertThat(changes.findByTicketIdOrderByChangedAtAsc(ticket.getId())).hasSize(2);
    }

    @Test
    @DisplayName("T095-8: a used reference cannot be reused; 5 wrong OTPs lock; requests are rate limited")
    void buyerVerification_singleUse_lock_andRateLimit() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        String reference = verifyBuyer(ticket);
        recipient(ticket, "friend.one@example.com", reference, null, null, key()).andExpect(status().isOk());
        recipient(ticket, "friend.two@example.com", reference, null, null, key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_EXPIRED"));

        jdbc.update("UPDATE booking_buyer_verifications SET created_at = created_at - INTERVAL 2 MINUTE");
        String verificationId = requestOtp(ticket).path("verificationId").asText();
        mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications", ticket.getGroup().getGroupCode())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contactPhone\":\"" + BUYER_PHONE + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_RATE_LIMITED"));
        String otp = latestOtp(ticket.getGroup().getId());
        String wrong = otp.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 4; i++) {
            confirm(ticket, verificationId, wrong).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_FAILED"));
        }
        confirm(ticket, verificationId, wrong).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_LOCKED"));
        confirm(ticket, verificationId, otp).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_LOCKED"));
    }

    @Test
    @DisplayName("T095-9: audit, idempotency and notification events carry no full email, QR or OTP")
    void audit_isMaskedOnly() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        recipient(ticket, "friend.one@example.com", verifyBuyer(ticket), null, null, key()).andExpect(status().isOk());
        recipient(ticket, "friend.two@example.com", verifyBuyer(ticket), null, null, key()).andExpect(status().isOk());
        String qr = tickets.findById(ticket.getId()).orElseThrow().getQrReference();

        List<String> auditText = jdbc.queryForList("SELECT CONCAT_WS('|', old_email_masked, new_email_masked, "
                + "actor_type, IFNULL(actor_reference,''), channel, IFNULL(reason,'')) FROM ticket_recipient_changes", String.class);
        auditText.addAll(jdbc.queryForList("SELECT CONCAT_WS('|', action, IFNULL(action_reason,''), correlation_reference) "
                + "FROM booking_audit_evidence", String.class));
        auditText.addAll(jdbc.queryForList("SELECT IFNULL(response_payload,'') FROM booking_idempotency_records", String.class));
        assertThat(String.join("\n", auditText))
                .doesNotContain("friend.one@example.com").doesNotContain("friend.two@example.com")
                .doesNotContain(BUYER_EMAIL).doesNotContain(qr);
    }

    @Test
    @DisplayName("T120 FR-041a: an OTP older than 10 minutes is refused as expired")
    void otp_expiresAfterTenMinutes() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        String verificationId = requestOtp(ticket).path("verificationId").asText();
        String otp = latestOtp(ticket.getGroup().getId());
        jdbc.update("UPDATE booking_buyer_verifications SET otp_expires_at = otp_expires_at - INTERVAL 11 MINUTE");
        confirm(ticket, verificationId, otp).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("T120 FR-041a: a verified reference older than 15 minutes is refused as expired")
    void reference_expiresAfterFifteenMinutes() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        String reference = verifyBuyer(ticket);
        jdbc.update("UPDATE booking_buyer_verifications SET reference_expires_at = reference_expires_at - INTERVAL 16 MINUTE");
        recipient(ticket, "late.friend@example.com", reference, null, null, key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("T120 FR-041a: resend is accepted from 60 seconds on, and the sixth send within an hour is refused")
    void resend_acceptedAfterCooldown_sixthSendInAnHourRefused() throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        for (int i = 1; i <= 5; i++) {
            requestOtp(ticket);
            if (i == 1) {
                // inside the 60 second cooldown the next request is refused
                mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications",
                                ticket.getGroup().getGroupCode()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"contactEmail\":\"" + BUYER_EMAIL + "\"}"))
                        .andExpect(status().isTooManyRequests());
            }
            jdbc.update("UPDATE booking_buyer_verifications SET created_at = created_at - INTERVAL 61 SECOND");
        }
        mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications", ticket.getGroup().getGroupCode())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"" + BUYER_EMAIL + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BUYER_VERIFICATION_RATE_LIMITED"));
    }

    @Test
    @DisplayName("T118 SC-007: OTP, recipient change and QR rotation leave no OTP, reference, QR or email in the logs")
    void logs_holdNoOtpReferenceQrOrEmail(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        TicketBooking ticket = confirmedTicket(null);
        String reference = verifyBuyer(ticket);
        String otp = latestOtp(ticket.getGroup().getId());
        recipient(ticket, "friend.one@example.com", reference, null, null, key()).andExpect(status().isOk());
        // SC-007 (T142): a Staff change through a second verification reference leaves the same clean log.
        String staffReference = verifyBuyer(ticket);
        recipient(ticket, "friend.two@example.com", staffReference, "Buyer at the counter", "staff-shop", key())
                .andExpect(status().isOk());
        String qr = tickets.findById(ticket.getId()).orElseThrow().getQrReference();

        assertThat(output.getAll()).as("the log is actually captured").contains("Buyer verification challenge issued");
        assertThat(output.getAll()).doesNotContain(otp).doesNotContain(reference).doesNotContain(qr)
                .doesNotContain(BUYER_EMAIL).doesNotContain(BUYER_PHONE).doesNotContain("friend.one@example.com")
                .doesNotContain(staffReference).doesNotContain("friend.two@example.com");
        // No bank account number (a labelled run of digits) and no raw bearer/Magic-Link token in any log line.
        assertThat(output.getAll())
                .doesNotContainPattern("(?i)account\\w*[\"'=: ]+\\d{6,}")
                .doesNotContainPattern("(?i)(magic[-_ ]?link|token)[\"'=: ]+[A-Za-z0-9_-]{20,}");
    }

    // ------------------------------------------------------------------ helpers

    private String verifyBuyer(TicketBooking ticket) throws Exception {
        jdbc.update("UPDATE booking_buyer_verifications SET created_at = created_at - INTERVAL 2 MINUTE");
        String verificationId = requestOtp(ticket).path("verificationId").asText();
        String otp = latestOtp(ticket.getGroup().getId());
        String body = confirm(ticket, verificationId, otp).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("buyerVerificationReference").asText();
    }

    private JsonNode requestOtp(TicketBooking ticket) throws Exception {
        String body = mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications",
                        ticket.getGroup().getGroupCode())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"" + BUYER_EMAIL + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions confirm(TicketBooking ticket, String verificationId, String otp) throws Exception {
        return mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/buyer-verifications/{v}/confirmations",
                        ticket.getGroup().getGroupCode(), verificationId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}"));
    }

    /** Reads the OTP the way the email would: from the protected snapshot, through the notification facade path. */
    private String latestOtp(Long groupId) {
        BookingGroupNotificationIntent intent = intents.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .filter(i -> GroupNotificationService.KIND_BUYER_VERIFICATION_OTP.equals(i.getNotificationKind()))
                .reduce((a, b) -> b).orElseThrow();
        BookingNotificationDeliveryPayload payload = notifications.readPayload(intent.getNotificationReference());
        assertThat(payload.storedRecipient()).isEqualTo(BUYER_EMAIL);
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(payload.renderedBookingContent());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private ResultActions recipient(TicketBooking ticket, String email, String reference, String reason, String uid,
                                    String key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientEmail", email);
        if (reference != null) body.put("buyerVerificationReference", reference);
        if (reason != null) body.put("reason", reason);
        MockHttpServletRequestBuilder request = put("/api/v1/workshop-tickets/{t}/recipient", ticket.getTicketCode())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (uid != null) request.header("Authorization", "Bearer test-uid:" + uid);
        return mockMvc.perform(request);
    }

    private static String key() {
        return "idem-" + UUID.randomUUID();
    }

    private List<String> kinds(TicketBooking ticket) {
        return kinds(ticket.getGroup().getId()).stream().filter(k -> !k.equals(GroupNotificationService.KIND_BUYER_VERIFICATION_OTP)).toList();
    }

    private List<String> kinds(Long groupId) {
        return intents.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(BookingGroupNotificationIntent::getNotificationKind).toList();
    }

    private int count(String fromClause) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
        return n == null ? 0 : n;
    }

    private void account(String uid, long id, ActorRole role, List<Long> scope) {
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@mland.test", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(id)).thenReturn(scope);
        when(tokenVerificationService.verify("test-uid:" + uid)).thenReturn(
                new VerifiedIdentity(uid, uid + "@mland.test", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, scope)));
    }

    /** A confirmed one-ticket group whose ticket is issued by Operations (real QR and entitlement). */
    private TicketBooking confirmedTicket(Long memberId) {
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).defaultCapacity(10)
                .active(true).build());
        LocalDate date = LocalDate.now().plusDays(3);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        WorkshopSession session = sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString())
                .location(location).slot(slot).workshopDate(date)
                .scheduledStartAt(date.atTime(9, 0).atZone(zone).toInstant())
                .scheduledEndAt(date.atTime(12, 0).atZone(zone).toInstant())
                .checkInOpensAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED).build());
        Long packageId = packages.save(WorkshopPackage.builder().name("Recipient package").description("fixture")
                .publicPrice(100000L).minParticipants(1).maxParticipants(10).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0).state(WorkshopPackageState.PUBLISHED).build()).getId();
        edu.fpt.sep490_g22.ppswbs_backend.workshopoperations.PackageAvailabilityFixture.activateEverywhere(jdbc, packageId);
        String lineReference = "LINE-" + UUID.randomUUID().toString().substring(0, 8);
        CapacityHoldOutcome hold = operations.reserveBookingCapacity(new ReserveBookingCapacityCommand(lineReference,
                String.valueOf(packageId), List.of(UUID.fromString(session.getReference())), 1,
                "op-h-" + UUID.randomUUID(), "corr"));
        assertThat(hold.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(operations.commitBookingHolds(new CommitBookingHoldsCommand(lineReference, hold.holdReferences(),
                "op-c-" + UUID.randomUUID(), "corr")).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        AttendanceTicket issued = attendanceTickets.findByBookingReference(lineReference).getFirst();
        jdbc.update("INSERT INTO session_staff_assignments (session_id, account_id, assigned_by_id, active) VALUES (?, ?, ?, 1)",
                session.getId(), STAFF_ID, 1L);

        BookingGroup group = groups.save(BookingGroup.builder().groupCode("GRP-" + UUID.randomUUID().toString().substring(0, 8))
                .buyerMemberAccountId(memberId).contactEmail(BUYER_EMAIL).contactPhone(BUYER_PHONE)
                .groupStatus(BookingStatus.CONFIRMED).paymentMode(GroupPaymentMode.VNPAY).groupCollectionVnd(100000L)
                .confirmedAt(Instant.now()).build());
        BookingGroupLine line = BookingGroupLine.builder().group(group).lineIndex(0).lineReference(lineReference)
                .packageId(packageId).packageNameSnapshot("Recipient package").pricePerParticipantVnd(100000L)
                .ticketCount(1).build();
        line.getSessions().add(BookingGroupLineSession.builder().line(line).ordinal(1)
                .sessionReference(session.getReference()).locationReference(location.getId())
                .scheduledStart(session.getScheduledStartAt()).holdStatus(LineHoldStatus.COMMITTED).build());
        lines.save(line);
        return tickets.save(TicketBooking.builder().ticketCode(issued.getTicketCode()).group(group).lineIndex(0)
                .participantOrdinal(1).packageId(packageId).packageNameSnapshot("Recipient package")
                .operationsTicketReference(issued.getReference()).qrReference(issued.getQrReference())
                .ticketStatus(BookingStatus.CONFIRMED).financialStatus(PaymentStatus.FULLY_PAID)
                .confirmedAt(Instant.now()).build());
    }
}
