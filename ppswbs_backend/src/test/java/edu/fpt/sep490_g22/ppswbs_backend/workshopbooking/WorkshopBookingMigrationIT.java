package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class WorkshopBookingMigrationIT {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private WorkshopOperationsFacade operationsFacade;

    @Autowired
    private WorkshopBookingRepository bookingRepository;

    @Autowired
    private BookingSessionCommitmentRepository commitmentRepository;

    @Autowired
    private BookingCapacityHoldRepository holdRepository;

    @Autowired
    private BookingInvoiceRepository invoiceRepository;

    @Autowired
    private BookingPaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private BookingPaymentReceiptRepository paymentReceiptRepository;

    @Autowired
    private BookingTicketRepository ticketRepository;

    @Autowired
    private BookingTicketSessionEntitlementRepository entitlementRepository;

    @Autowired
    private MagicLinkAccessRepository magicLinkRepository;

    @Autowired
    private BookingNotificationIntentRepository notificationIntentRepository;

    @Autowired
    private BookingAuditEvidenceRepository auditEvidenceRepository;

    @Autowired
    private BookingIdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private WorkshopLocationRepository locationRepository;

    private Long testLocationId;

    @BeforeEach
    void setUp() {
        WorkshopLocation loc = locationRepository.findByCode("LOC-BKG-TEST").orElseGet(() -> {
            WorkshopLocation newLoc = WorkshopLocation.builder()
                    .code("LOC-BKG-TEST")
                    .displayName("Booking Test Location")
                    .addressSnapshot("123 Test Street, Hanoi")
                    .active(true)
                    .build();
            return locationRepository.save(newLoc);
        });
        testLocationId = loc.getId();
    }

    @Test
    @DisplayName("T010-1: Verify Flyway V9 applied on MySQL and all 12 tables exist")
    void verifyV9MigrationAppliedOnMySQL() {
        assertThat(jdbcTemplate).isNotNull();

        Integer v9Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = 1",
                Integer.class
        );
        assertThat(v9Count).as("Flyway migration V9 must be applied successfully").isNotNull().isGreaterThanOrEqualTo(1);

        String[] expectedTables = {
                "workshop_bookings",
                "booking_session_commitments",
                "booking_capacity_holds",
                "booking_invoices",
                "booking_payment_attempts",
                "booking_payment_receipts",
                "booking_tickets",
                "booking_ticket_session_entitlements",
                "booking_magic_links",
                "booking_notification_intents",
                "booking_audit_evidence",
                "booking_idempotency_records"
        };

        for (String table : expectedTables) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("Table %s should exist in database", table).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify V34 payment integration operation journal exists")
    void verifyV34MerchantOperationJournalApplied() {
        assertThat(jdbcTemplate).isNotNull();
        Integer v34Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '34' AND success = 1",
                Integer.class);
        assertThat(v34Count).as("V34 must be applied by Flyway").isNotNull().isGreaterThanOrEqualTo(1);
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, "vnpay_merchant_operations");
        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    @DisplayName("T010: Verify V37 migrated commitments and ticket entitlements to opaque UUID references")
    void verifyV37OpaqueSessionReferenceMigrationApplied() {
        Integer v37Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '37' AND success = 1",
                Integer.class);
        assertThat(v37Count).as("V37 must be applied by Flyway").isNotNull().isGreaterThanOrEqualTo(1);

        assertColumnExists("booking_session_commitments", "session_reference");
        assertColumnExists("booking_ticket_session_entitlements", "session_reference");
        assertColumnMissing("booking_session_commitments", "session_id");
        assertColumnMissing("booking_ticket_session_entitlements", "session_id");

        Integer invalidCommitments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_session_commitments WHERE session_reference NOT REGEXP " +
                        "'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'",
                Integer.class);
        assertThat(invalidCommitments).as("persisted session references must be canonical UUIDs").isZero();
        Integer mismatchedEntitlements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_ticket_session_entitlements btse " +
                        "JOIN booking_session_commitments bsc ON bsc.id = btse.session_commitment_id " +
                        "WHERE btse.session_reference <> bsc.session_reference",
                Integer.class);
        assertThat(mismatchedEntitlements).as("ticket entitlements must match their booking commitment").isZero();
    }

    private void assertColumnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        assertThat(count).as("Column %s.%s should exist", table, column).isEqualTo(1);
    }

    private void assertColumnMissing(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        assertThat(count).as("Legacy column %s.%s should be removed", table, column).isZero();
    }

    @Test
    @Transactional
    @DisplayName("T009-1: Verify Booking, commitment, hold and invoice persistence and invariants")
    void verifyBookingPersistenceLifecycle() {
        String code = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        WorkshopBooking booking = WorkshopBooking.builder()
                .bookingCode(code)
                .packageId(100L)
                .packageNameSnapshot("Basic Ring Workshop")
                .contactEmail("customer@example.com")
                .contactPhone("0901234567")
                .participantCount(2)
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .holdExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        booking = bookingRepository.save(booking);
        assertThat(booking.getId()).isNotNull();

        String sessionReference = UUID.randomUUID().toString();
        BookingSessionCommitment commitment = BookingSessionCommitment.builder()
                .booking(booking)
                .sessionReference(sessionReference)
                .locationId(testLocationId)
                .ordinal(1)
                .seatCount(2)
                .build();
        commitment = commitmentRepository.save(commitment);
        assertThat(commitment.getId()).isNotNull();

        BookingCapacityHold hold = BookingCapacityHold.builder()
                .booking(booking)
                .sessionCommitment(commitment)
                .holdReference(UUID.randomUUID().toString())
                .holdStatus("RESERVED")
                .build();
        hold = holdRepository.save(hold);
        assertThat(hold.getId()).isNotNull();

        BookingInvoice invoice = BookingInvoice.builder()
                .booking(booking)
                .invoiceNumber("INV-" + code)
                .pricePerParticipant(500000L)
                .packageTotal(1000000L)
                .postPromotionAmount(1000000L)
                .depositAmount(500000L)
                .remainingAmount(500000L)
                .currency("VND")
                .build();
        invoice = invoiceRepository.save(invoice);
        assertThat(invoice.getId()).isNotNull();

        WorkshopBooking fetched = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(fetched.getBookingCode()).isEqualTo(code);
        assertThat(fetched.getParticipantCount()).isEqualTo(2);

        List<BookingSessionCommitment> commitments = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        assertThat(commitments).hasSize(1);
        assertThat(commitments.get(0).getSeatCount()).isEqualTo(2);
        assertThat(UUID.fromString(commitments.get(0).getSessionReference()).toString()).isEqualTo(sessionReference);

        BookingInvoice fetchedInvoice = invoiceRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(fetchedInvoice.getDepositAmount()).isEqualTo(500000L);
        assertThat(fetchedInvoice.getRemainingAmount()).isEqualTo(500000L);
    }

    @Test
    @Transactional
    @DisplayName("T009-2: Verify payment attempts and safe receipts persistence without raw secrets")
    void verifyPaymentAndReceiptPersistence() {
        String code = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        WorkshopBooking booking = bookingRepository.save(WorkshopBooking.builder()
                .bookingCode(code)
                .packageId(101L)
                .packageNameSnapshot("Silverclay Workshop")
                .contactEmail("payer@example.com")
                .contactPhone("0912345678")
                .participantCount(1)
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .build());

        BookingInvoice invoice = invoiceRepository.save(BookingInvoice.builder()
                .booking(booking)
                .invoiceNumber("INV-" + code)
                .pricePerParticipant(800000L)
                .packageTotal(800000L)
                .postPromotionAmount(800000L)
                .depositAmount(400000L)
                .remainingAmount(400000L)
                .currency("VND")
                .build());

        String attemptRef = "ATT-" + UUID.randomUUID().toString().substring(0, 8);
        BookingPaymentAttempt attempt = paymentAttemptRepository.save(BookingPaymentAttempt.builder()
                .booking(booking)
                .invoice(invoice)
                .attemptReference(attemptRef)
                .merchantReference("MERCH-" + code)
                .attemptAmount(400000L)
                .status(PaymentAttemptStatus.INITIATED)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build());
        assertThat(attempt.getId()).isNotNull();

        BookingPaymentReceipt receipt = paymentReceiptRepository.save(BookingPaymentReceipt.builder()
                .booking(booking)
                .paymentAttempt(attempt)
                .providerEventId("EVT-" + UUID.randomUUID())
                .providerName("VNPAY")
                .amountReceived(400000L)
                .receivedAt(Instant.now())
                .safePayloadHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .build());
        assertThat(receipt.getId()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("T009-3: Verify tickets, entitlements, magic links, notification intents and audit evidence")
    void verifyDeliveryAndAuditPersistence() {
        String code = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        WorkshopBooking booking = bookingRepository.save(WorkshopBooking.builder()
                .bookingCode(code)
                .packageId(102L)
                .packageNameSnapshot("Ring Workshop")
                .contactEmail("tickets@example.com")
                .contactPhone("0934567890")
                .participantCount(1)
                .bookingStatus(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.DEPOSIT_PAID)
                .build());

        String sessionReference = UUID.randomUUID().toString();
        BookingSessionCommitment commitment = commitmentRepository.save(BookingSessionCommitment.builder()
                .booking(booking)
                .sessionReference(sessionReference)
                .locationId(testLocationId)
                .ordinal(1)
                .seatCount(1)
                .build());

        BookingTicket ticket = ticketRepository.save(BookingTicket.builder()
                .booking(booking)
                .ticketCode("TK-" + code + "-1")
                .qrReference("QR-REF-" + UUID.randomUUID())
                .deliveryStatus(TicketDeliveryStatus.PENDING)
                .build());
        assertThat(ticket.getId()).isNotNull();

        BookingTicketSessionEntitlement entitlement = entitlementRepository.save(BookingTicketSessionEntitlement.builder()
                .bookingTicket(ticket)
                .sessionCommitment(commitment)
                .sessionReference(sessionReference)
                .build());
        assertThat(entitlement.getId()).isNotNull();

        MagicLinkAccess magicLink = magicLinkRepository.save(MagicLinkAccess.builder()
                .booking(booking)
                .tokenHash("hash-" + UUID.randomUUID())
                .lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());
        assertThat(magicLink.getId()).isNotNull();

        BookingNotificationIntent intent = notificationIntentRepository.save(BookingNotificationIntent.builder()
                .booking(booking)
                .notificationReference("NOTIF-" + UUID.randomUUID())
                .notificationKind("CONFIRMED_DELIVERY")
                .correlationReference("CORR-TEST")
                .deliverySnapshot("{\"bookingCode\":\"" + code + "\",\"recipient\":\"tickets@example.com\"}")
                .build());
        assertThat(intent.getId()).isNotNull();

        BookingAuditEvidence audit = auditEvidenceRepository.save(BookingAuditEvidence.builder()
                .bookingCode(code)
                .locationId(testLocationId)
                .actorType("MANAGER")
                .actorId(10L)
                .action("CONFIRMATION_RESEND")
                .outcome("ACCEPTED")
                .correlationReference("CORR-TEST")
                .managerReason("Customer requested resend")
                .occurredAt(Instant.now())
                .build());
        assertThat(audit.getId()).isNotNull();
    }

    @Test
    @DisplayName("T009-4: Verify raw-sensitive-data exclusion across entity models")
    void verifyRawSensitiveDataExclusion() {
        Class<?>[] entities = {
                WorkshopBooking.class,
                BookingSessionCommitment.class,
                BookingCapacityHold.class,
                BookingInvoice.class,
                BookingPaymentAttempt.class,
                BookingPaymentReceipt.class,
                BookingTicket.class,
                BookingTicketSessionEntitlement.class,
                MagicLinkAccess.class,
                BookingNotificationIntent.class,
                BookingAuditEvidence.class,
                BookingIdempotencyRecord.class
        };

        for (Class<?> clazz : entities) {
            List<String> fieldNames = Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).toList();
            assertThat(fieldNames)
                    .as("Entity %s should not contain raw token or credential fields", clazz.getSimpleName())
                    .noneMatch(name -> name.equalsIgnoreCase("rawToken")
                            || name.equalsIgnoreCase("password")
                            || name.equalsIgnoreCase("otp")
                            || name.equalsIgnoreCase("secretKey")
                            || name.equalsIgnoreCase("gatewaySecret")
                            || name.equalsIgnoreCase("rawPayload"));
        }
    }
}
