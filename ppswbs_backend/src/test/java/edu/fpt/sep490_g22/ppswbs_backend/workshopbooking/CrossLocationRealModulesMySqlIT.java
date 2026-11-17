package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.TimezoneConfiguration;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.3 T191, the three-module run: the real 003.1 booking, 003.2 operations and 003.3 settlement beans in one Spring
 * context on MySQL (profile {@code workshopoperations-mysql-test}). Only the account module, the token service, the
 * VNPay port and the buyer-proof consumption (a Spy on {@link WorkshopBookingFacade}; the proof is a one-time code
 * sent by email) are replaced. A Manager changes the tickets of a confirmed group (FR-059, FR-060, SC-021, SC-022).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class CrossLocationRealModulesMySqlIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(300L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupConfirmationService confirmation;
    @Autowired private BookingGroupRepository groups;
    @Autowired private TicketBookingRepository tickets;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;
    @Autowired private WorkshopPackageLocationAvailabilityRepository availability;
    @Autowired private TicketChangeService changeService;
    @Autowired private FolioCollectionService collectionService;
    @Autowired private SettlementFinalBillRepository bills;
    @Autowired private SettlementFolioCollectionRepository collections;
    @Autowired private SettlementTicketChangeRepository changes;
    @Autowired private SettlementBusinessAuditRepository settlementAudits;

    @Autowired private PlatformTransactionManager transactionManager;
    @SpyBean private WorkshopBookingFacade booking;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private VnpayMerchantOperationsPort vnpay;

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private int hourOffset;

    @BeforeEach
    void setUp() throws Exception {
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
        locationA = locations.save(WorkshopLocation.builder().code("LOC-X1").displayName("Cross Hub A")
                .addressSnapshot("1 Cross St").active(true).build());
        locationB = locations.save(WorkshopLocation.builder().code("LOC-X2").displayName("Cross Hub B")
                .addressSnapshot("2 Cross St").active(true).build());
        hourOffset = 0;
        // The facade method needs a surrounding transaction even while it is being stubbed through the Spring proxy.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                doReturn(ConsumeBuyerProofResult.accepted()).when(booking).consumeBuyerProof(any()));
    }

    @Test
    @DisplayName("T191: a Manager moves a one-session ticket from A to B; the context, audit and later collections follow B; the other ticket stays at A")
    void moveToAnotherLocation() throws Exception {
        Long pkg = newPackage(1, false);
        sellAt(pkg, locationA);
        sellAt(pkg, locationB);
        WorkshopSession atA = session(locationA, 10);
        WorkshopSession atB = session(locationB, 12);
        String group = confirmedGroup(pkg, List.of(atA), 2);
        TicketBooking moving = ticketsOf(group).get(0);
        TicketBooking staying = ticketsOf(group).get(1);
        awaitFolio(moving.getTicketCode());
        awaitFolio(staying.getTicketCode());
        // The booking paid the whole price, so give the folio a counter balance (fixture) to collect against.
        var bill = bills.findByBookingReference(moving.getTicketCode()).orElseThrow();
        bill.setRemainingAmountVnd(100_000L);
        bills.save(bill);
        collectionService.recordBankTransfer(moving.getTicketCode(), 10_000L, "TXN-A-" + group, "payer", 300L, "CORR");

        ChangeResultDto result = changeService.changeTicketSessions(moving.getTicketCode(), request(
                List.of(UUID.fromString(atB.getReference())), List.of(UUID.fromString(atA.getReference()))),
                "xloc-" + UUID.randomUUID(), "CORR-REAL-1", MANAGER);

        assertThat(result.applied()).isTrue();
        assertThat(booking.getTicketFolioContext(moving.getTicketCode()).orElseThrow().locationReference())
                .isEqualTo(locationB.getId());
        assertThat(booking.getTicketFolioContext(staying.getTicketCode()).orElseThrow().locationReference())
                .isEqualTo(locationA.getId());
        assertThat(settlementAudits.findAll()).anySatisfy(a -> {
            assertThat(a.getActionCode()).isEqualTo("TICKET_LOCATION_CHANGED");
            assertThat(a.getReasonReference()).isEqualTo("from=" + locationA.getId() + ";to=" + locationB.getId());
        });
        collectionService.recordBankTransfer(moving.getTicketCode(), 20_000L, "TXN-B-" + group, "payer", 300L, "CORR");
        var rows = collections.findByBookingReferenceOrderByIdAsc(moving.getTicketCode());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getBranchId()).as("collected before the move").isEqualTo(locationA.getId());
        assertThat(rows.get(1).getBranchId()).as("collected after the move").isEqualTo(locationB.getId());
    }

    @Test
    @DisplayName("T191: a two-session sameLocationRequired ticket moves both sessions together; one alone is INVALID_REPLACEMENT with nothing counted")
    void twoSessionPackageMovesTogether() throws Exception {
        Long pkg = newPackage(2, true);
        sellAt(pkg, locationA);
        sellAt(pkg, locationB);
        WorkshopSession a1 = session(locationA, 10);
        WorkshopSession a2 = session(locationA, 12);
        WorkshopSession b1 = session(locationB, 14);
        WorkshopSession b2 = session(locationB, 16);
        String group = confirmedGroup(pkg, List.of(a1, a2), 1);
        String ticket = ticketsOf(group).get(0).getTicketCode();
        awaitFolio(ticket);

        assertThatThrownBy(() -> changeService.changeTicketSessions(ticket, request(
                List.of(UUID.fromString(b1.getReference())), List.of(UUID.fromString(a1.getReference()))),
                "xloc-" + UUID.randomUUID(), "CORR-REAL-2", MANAGER)).hasMessageContaining("INVALID_REPLACEMENT");
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).isZero();

        ChangeResultDto both = changeService.changeTicketSessions(ticket, request(
                List.of(UUID.fromString(b1.getReference()), UUID.fromString(b2.getReference())),
                List.of(UUID.fromString(a1.getReference()), UUID.fromString(a2.getReference()))),
                "xloc-" + UUID.randomUUID(), "CORR-REAL-3", MANAGER);
        assertThat(both.applied()).isTrue();
        assertThat(booking.getTicketFolioContext(ticket).orElseThrow().locationReference()).isEqualTo(locationB.getId());
        assertThat(booking.getTicketFolioContext(ticket).orElseThrow().sessionReferences())
                .containsExactlyInAnyOrder(b1.getReference(), b2.getReference());
    }

    @Test
    @DisplayName("T191: a pair INACTIVE at B is refused with PACKAGE_NOT_AVAILABLE_AT_LOCATION; the ticket and its allowance are untouched")
    void inactivePairRefused() throws Exception {
        Long pkg = newPackage(1, false);
        sellAt(pkg, locationA);
        sellAt(pkg, locationB);
        WorkshopSession atA = session(locationA, 10);
        WorkshopSession atB = session(locationB, 12);
        String group = confirmedGroup(pkg, List.of(atA), 1);
        String ticket = ticketsOf(group).get(0).getTicketCode();
        awaitFolio(ticket);
        jdbc.update("UPDATE workshop_package_location_availability SET active = 0 WHERE package_id = ? AND location_id = ?",
                pkg, locationB.getId());

        assertThatThrownBy(() -> changeService.changeTicketSessions(ticket, request(
                List.of(UUID.fromString(atB.getReference())), List.of(UUID.fromString(atA.getReference()))),
                "xloc-" + UUID.randomUUID(), "CORR-REAL-4", MANAGER)).hasMessageContaining("PACKAGE_NOT_AVAILABLE_AT_LOCATION");
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).isZero();
        assertThat(booking.getTicketFolioContext(ticket).orElseThrow().locationReference()).isEqualTo(locationA.getId());
    }

    // ---- helpers ----

    private static TicketChangeRequestDto request(List<UUID> targets, List<UUID> from) {
        return new TicketChangeRequestDto(TicketChangeType.ADVANCE, targets, from, null, "bvr-0123456789abcdef", "counter");
    }

    private List<TicketBooking> ticketsOf(String groupCode) {
        return tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(groups.findByGroupCode(groupCode).orElseThrow().getId());
    }

    /** The folio is opened by the ticket-confirmed event; wait for it (bounded) instead of assuming it is synchronous. */
    private void awaitFolio(String ticketCode) throws InterruptedException {
        for (int i = 0; i < 100 && bills.findByBookingReference(ticketCode).isEmpty(); i++) {
            Thread.sleep(100);
        }
        assertThat(bills.findByBookingReference(ticketCode)).as("folio of " + ticketCode).isPresent();
    }

    private Long newPackage(int sessionCount, boolean sameLocation) {
        return packages.save(WorkshopPackage.builder().name("Cross package " + UUID.randomUUID())
                .description("fixture").publicPrice(100_000L).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(sessionCount).sameLocationRequired(sameLocation).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();
    }

    private void sellAt(Long packageId, WorkshopLocation location) {
        availability.save(WorkshopPackageLocationAvailability.builder().packageId(packageId).locationId(location.getId())
                .active(true).version(0L).createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    private String confirmedGroup(Long packageId, List<WorkshopSession> lineSessions, int participants) throws Exception {
        String refs = String.join(",", lineSessions.stream().map(s -> "\"" + s.getReference() + "\"").toList());
        String quoteBody = "{\"lines\":[{\"packageId\":" + packageId + ",\"sessionReferences\":[" + refs
                + "],\"participantCount\":" + participants + "}]}";
        MvcResult quote = mockMvc.perform(post("/api/v1/workshop-cart-quotes").contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody)).andExpect(status().isCreated()).andReturn();
        String quoteRef = JSON.readTree(quote.getResponse().getContentAsString()).get("quoteRef").asText();
        MvcResult created = mockMvc.perform(post("/api/v1/workshop-booking-groups")
                        .header("Idempotency-Key", "xloc-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"cross.buyer@example.com\","
                                + "\"contactPhone\":\"+84900000191\"}"))
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
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(10)
                .active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED)
                .build());
    }
}
