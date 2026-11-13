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
                "workshop_sessions", "workshop_slots", "workshop_locations")) {
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
        String lineReference = "LINE-" + UUID.randomUUID().toString().substring(0, 8);
        CapacityHoldOutcome hold = operations.reserveBookingCapacity(new ReserveBookingCapacityCommand(lineReference,
                String.valueOf(packageId), List.of(UUID.fromString(session.getReference())), 1,
                "op-h-" + UUID.randomUUID(), "corr"));
        assertThat(hold.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(operations.commitBookingHolds(new CommitBookingHoldsCommand(lineReference, hold.holdReferences(),
                "op-c-" + UUID.randomUUID(), "corr")).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        AttendanceTicket issued = attendanceTickets.findByBookingReference(lineReference).getFirst();

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
