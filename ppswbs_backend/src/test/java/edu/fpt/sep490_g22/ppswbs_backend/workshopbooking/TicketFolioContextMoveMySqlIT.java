package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.TimezoneConfiguration;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRefundStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordBookingRefundStatusCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupConfirmationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageLocationAvailabilityRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageLocationAvailability;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceTicketSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T155 on MySQL (FR-053, SC-009(a)): a ticket of a confirmed group is moved with the real 003.2
 * {@code replaceTicketSessions} to a location where the Package is ACTIVE, and {@code getTicketFolioContext} then
 * reads the new location and session, while the booking-time copy on the line is untouched, a ticket of the same line
 * that did not move still reads its own location, and a settlement-conclusion audit written after the move carries the
 * new location while the earlier row keeps the old one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class TicketFolioContextMoveMySqlIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkshopBookingFacade facade;
    @Autowired private WorkshopOperationsFacade operations;
    @Autowired private GroupConfirmationService confirmation;
    @Autowired private BookingGroupRepository groups;
    @Autowired private TicketBookingRepository tickets;
    @Autowired private BookingAuditEvidenceRepository audits;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;
    @Autowired private WorkshopPackageLocationAvailabilityRepository availability;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private VnpayMerchantOperationsPort vnpay;

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private int hourOffset;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "TicketFolioContextMoveMySqlIT requires real MySQL evidence");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("promotion_revision_target", "promotion_revision", "promotion_program",
                "group_excess_payments", "late_payment_reviews",
                "booking_group_notification_intents", "group_payment_attempts", "booking_group_magic_links",
                "ticket_price_snapshots", "ticket_bookings", "booking_group_line_sessions", "booking_group_lines",
                "booking_groups", "workshop_cart_quotes", "booking_idempotency_records", "booking_audit_evidence",
                "facade_command_idempotency_records", "ticket_session_entitlements", "attendance_tickets",
                "capacity_holds", "operations_audit_events", "workshop_sessions", "workshop_slots",
                "attendance_actions", "cancellation_handoffs", "session_staff_assignments", "ticket_entitlement_releases",
                "ticket_qr_generations", "ticket_session_replacements", "workshop_addons",
                "workshop_package_location_availability", "workshop_packages", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        locationA = locations.save(WorkshopLocation.builder().code("LOC-M1").displayName("Move Hub A")
                .addressSnapshot("1 Move St").active(true).build());
        locationB = locations.save(WorkshopLocation.builder().code("LOC-M2").displayName("Move Hub B")
                .addressSnapshot("2 Move St").active(true).build());
        hourOffset = 0;
    }

    @Test
    @DisplayName("T155: a moved ticket reads the new location and session; the line copy, the unmoved ticket and the earlier audit keep the old location")
    void movedTicketReadsTheNewLocation() throws Exception {
        // sameLocationRequired = false lets 003.2 FR-041 allow a move to another location; the pair is ACTIVE at both.
        Long packageId = packages.save(WorkshopPackage.builder().name("Move package " + UUID.randomUUID())
                .description("fixture").publicPrice(100_000L).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(1).sameLocationRequired(false).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();
        sellAt(packageId, locationA);
        sellAt(packageId, locationB);
        WorkshopSession atA = session(locationA, 10);
        WorkshopSession atB = session(locationB, 11);

        String code = confirmedGroup(packageId, atA, 2);
        List<TicketBooking> ticketsOfGroup = tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(
                groups.findByGroupCode(code).orElseThrow().getId());
        TicketBooking moving = ticketsOfGroup.get(0);
        TicketBooking staying = ticketsOfGroup.get(1);
        assertThat(moving.getOperationsTicketReference()).isNotBlank();
        assertThat(facade.getTicketFolioContext(moving.getTicketCode()).orElseThrow().locationReference())
                .isEqualTo(locationA.getId());

        // A settlement-conclusion audit before the move belongs to location A.
        assertThat(facade.recordBookingRefundStatus(new RecordBookingRefundStatusCommand(moving.getTicketCode(),
                BookingRefundStatus.REFUND_PENDING, "SET-" + UUID.randomUUID(), "op-" + UUID.randomUUID(), "corr-before"))
                .status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);

        TicketSessionReplacementOutcome outcome = operations.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(moving.getOperationsTicketReference()),
                        List.of(UUID.fromString(atB.getReference())))),
                TicketReplacementType.MLAND, "op-" + UUID.randomUUID(), "corr-move"));
        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        TicketFolioContext moved = facade.getTicketFolioContext(moving.getTicketCode()).orElseThrow();
        assertThat(moved.locationReference()).isEqualTo(locationB.getId());
        assertThat(moved.sessionReferences()).containsExactly(atB.getReference());
        assertThat(moved.firstSessionStart()).isEqualTo(atB.getScheduledStartAt());

        // The booking-time copy on the line is never rewritten (FR-053).
        assertThat(jdbc.queryForList("SELECT location_reference FROM booking_group_line_sessions", Long.class))
                .containsExactly(locationA.getId());
        // Another ticket of the same line that did not move still reads its own location and session.
        TicketFolioContext unmoved = facade.getTicketFolioContext(staying.getTicketCode()).orElseThrow();
        assertThat(unmoved.locationReference()).isEqualTo(locationA.getId());
        assertThat(unmoved.sessionReferences()).containsExactly(atA.getReference());

        // A conclusion audit written after the move carries the new location; the earlier row keeps the old one.
        assertThat(facade.recordBookingRefundStatus(new RecordBookingRefundStatusCommand(moving.getTicketCode(),
                BookingRefundStatus.REFUNDED, "SET-" + UUID.randomUUID(), "op-" + UUID.randomUUID(), "corr-after"))
                .status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        List<BookingAuditEvidence> rows = audits.findAll().stream()
                .filter(row -> row.getAction().startsWith("TICKET_REFUND_")).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(row -> row.getAction().equals("TICKET_REFUND_REFUND_PENDING")).findFirst()
                .orElseThrow().getLocationId()).isEqualTo(locationA.getId());
        assertThat(rows.stream().filter(row -> row.getAction().equals("TICKET_REFUND_REFUNDED")).findFirst()
                .orElseThrow().getLocationId()).isEqualTo(locationB.getId());
    }

    // ---- helpers ----

    /** Pair ACTIVE at the location; the V178 seed may already hold a row for a recycled id, so it is updated, not duplicated. */
    private void sellAt(Long packageId, WorkshopLocation location) {
        WorkshopPackageLocationAvailability row = availability.findByPackageIdAndLocationId(packageId, location.getId())
                .orElseGet(() -> WorkshopPackageLocationAvailability.builder().packageId(packageId)
                        .locationId(location.getId()).version(0L).createdAt(Instant.now()).build());
        row.setActive(true);
        row.setUpdatedAt(Instant.now());
        availability.save(row);
    }

    private String confirmedGroup(Long packageId, WorkshopSession session, int participants) throws Exception {
        String quoteBody = "{\"lines\":[{\"packageId\":" + packageId + ",\"sessionReferences\":[\"" + session.getReference()
                + "\"],\"participantCount\":" + participants + "}]}";
        MvcResult quote = mockMvc.perform(post("/api/v1/workshop-cart-quotes").contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody)).andExpect(status().isCreated()).andReturn();
        String quoteRef = JSON.readTree(quote.getResponse().getContentAsString()).get("quoteRef").asText();
        MvcResult created = mockMvc.perform(post("/api/v1/workshop-booking-groups")
                        .header("Idempotency-Key", "move-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"move.buyer@example.com\","
                                + "\"contactPhone\":\"+84900000155\"}"))
                .andExpect(status().isCreated()).andReturn();
        String code = JSON.readTree(created.getResponse().getContentAsString()).get("groupCode").asText();
        long collected = groups.findByGroupCode(code).orElseThrow().getGroupCollectionVnd();
        confirmation.confirmPaidGroup(code, collected, "ipn-" + code, "op-" + code, "corr");
        return code;
    }

    private WorkshopSession session(WorkshopLocation location, int daysAhead) {
        Instant start = Instant.now().plus(Duration.ofDays(daysAhead)).plus(hourOffset++, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.MINUTES);
        var zone = TimezoneConfiguration.ZONE_ID_VIETNAM;
        LocalTime startTime = start.atZone(zone).toLocalTime();
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1))
                .active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED)
                .build());
    }
}
