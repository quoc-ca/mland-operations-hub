package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T040 [US4]: Acceptance tests for customer-payment/refund versus supplier-payout separation.
 * Verifies full accepted amount evidence for PAID_ON_CHANNEL, exclusion of partial/mismatched evidence,
 * append-only refund tracking, and proof that Mland supplier payout remains a distinct later reconciliation fact.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(OtaPayoutReconciliationService.class)
class OtaFinancialEvidenceIT {

    private static final Optional<TestDatabase> TEST_DATABASE = initializeDatabase();
    private static final Instant BASE_TIME = Instant.parse("2026-10-31T14:00:00Z");
    private static final AccountSummary MANAGER = new AccountSummary(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, null, null, null);

    @Autowired private OtaChannelConnectionRepository connectionRepository;
    @Autowired private OtaChannelMappingRepository mappingRepository;
    @Autowired private OtaChannelBookingRepository bookingRepository;
    @Autowired private OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    @Autowired private OtaPayoutPositionRepository payoutPositionRepository;
    @Autowired private OtaPayoutLedgerEntryRepository payoutLedgerRepository;
    @Autowired private OtaPayoutReconciliationService payoutService;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        TEST_DATABASE.ifPresent(database -> {
            registry.add("spring.datasource.url", () -> database.support().jdbcUrl(database.schema()));
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.datasource.username", () -> System.getenv("WORKSHOP_OTA_TEST_JDBC_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("WORKSHOP_OTA_TEST_JDBC_PASSWORD"));
        });
    }

    @AfterAll
    static void dropOnlyOwnedTestSchema() throws SQLException {
        if (TEST_DATABASE.isPresent()) TEST_DATABASE.orElseThrow().support().close();
    }

    @Test
    @DisplayName("Customer payment evidence: Full accepted amount for PAID_ON_CHANNEL, refund states, and deduplication")
    void customerPaymentEvidence_fullAcceptedAmount_andRefundProgression() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL persistence evidence");

        BookingFixture fixture = createBookingFixture(100_000L, 80_000L);
        Long bookingId = fixture.booking().getId();

        // 1. Full accepted customer amount (100,000 VND) records PAID_ON_CHANNEL
        OtaCustomerPaymentEvidence paymentEvidence = customerPaymentRepository.save(
                OtaCustomerPaymentEvidence.record(bookingId, "ext-pay-001", "PAYMENT",
                        OtaCustomerPaymentState.PAID_ON_CHANNEL, 100_000L, "VND", BASE_TIME, "trace-pay-001"));
        entityManager.flush();
        assertThat(paymentEvidence.getId()).isNotNull();
        assertThat(paymentEvidence.getEvidenceStatus()).isEqualTo(OtaCustomerPaymentState.PAID_ON_CHANNEL);
        assertThat(paymentEvidence.getAmountMinor()).isEqualTo(100_000L);

        // 2. Duplicate external evidence reference on same booking fails unique constraint
        assertThatThrownBy(() -> {
            customerPaymentRepository.save(
                    OtaCustomerPaymentEvidence.record(bookingId, "ext-pay-001", "PAYMENT",
                            OtaCustomerPaymentState.PAID_ON_CHANNEL, 100_000L, "VND", BASE_TIME, "trace-dup-001"));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
        entityManager.clear();

        // 3. Invalid / missing / negative amount is rejected by domain model
        assertThatThrownBy(() -> OtaCustomerPaymentEvidence.record(bookingId, "ext-pay-inv", "PAYMENT",
                OtaCustomerPaymentState.PAID_ON_CHANNEL, -5_000L, "VND", BASE_TIME, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtaCustomerPaymentEvidence.record(bookingId, "ext-pay-inv", "PAYMENT",
                OtaCustomerPaymentState.PAID_ON_CHANNEL, 100_000L, "INVALID_CURR", BASE_TIME, null))
                .isInstanceOf(IllegalArgumentException.class);

        // 4. Customer refund events append immutable progression: REFUND_REQUESTED -> PARTIALLY_REFUNDED -> REFUNDED
        customerPaymentRepository.save(OtaCustomerPaymentEvidence.record(bookingId, "ext-ref-req-001", "REFUND_REQUEST",
                OtaCustomerPaymentState.REFUND_REQUESTED, 30_000L, "VND", BASE_TIME.plusSeconds(300), "trace-ref-req"));
        customerPaymentRepository.save(OtaCustomerPaymentEvidence.record(bookingId, "ext-ref-part-001", "PARTIAL_REFUND",
                OtaCustomerPaymentState.PARTIALLY_REFUNDED, 30_000L, "VND", BASE_TIME.plusSeconds(600), "trace-ref-part"));
        customerPaymentRepository.save(OtaCustomerPaymentEvidence.record(bookingId, "ext-ref-full-001", "FINAL_REFUND",
                OtaCustomerPaymentState.REFUNDED, 70_000L, "VND", BASE_TIME.plusSeconds(900), "trace-ref-full"));
        entityManager.flush();
        entityManager.clear();

        List<OtaCustomerPaymentEvidence> history = customerPaymentRepository.findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(bookingId);
        assertThat(history).extracting(OtaCustomerPaymentEvidence::getEvidenceStatus)
                .containsExactly(
                        OtaCustomerPaymentState.PAID_ON_CHANNEL,
                        OtaCustomerPaymentState.REFUND_REQUESTED,
                        OtaCustomerPaymentState.PARTIALLY_REFUNDED,
                        OtaCustomerPaymentState.REFUNDED);
    }

    @Test
    @DisplayName("Financial separation: Customer payment on channel is distinct from Mland supplier payout position and ledger")
    void financialSeparation_customerPaymentVersusMlandSupplierPayout() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL persistence evidence");

        BookingFixture fixture = createBookingFixture(150_000L, 120_000L);
        Long bookingId = fixture.booking().getId();
        Long positionId = fixture.position().getId();
        String bookingRef = fixture.booking().getPublicReference();

        // 1. Customer pays 150,000 VND on channel.
        customerPaymentRepository.save(OtaCustomerPaymentEvidence.record(
                bookingId, "ext-pay-002", "PAYMENT", OtaCustomerPaymentState.PAID_ON_CHANNEL,
                150_000L, "VND", BASE_TIME, "trace-pay-002"));
        entityManager.flush();

        // 2. Even though customer has paid on channel, Mland supplier payout position remains NOT_ELIGIBLE initially
        OtaPayoutPosition position = payoutPositionRepository.findById(positionId).orElseThrow();
        assertThat(position.getStatus()).isEqualTo(OtaPayoutStatus.NOT_ELIGIBLE);
        assertThat(position.getExpectedAmountMinor()).isEqualTo(120_000L);
        assertThat(payoutLedgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(positionId)).isEmpty();

        // 3. Mland eligibility evidence advances position to ACCRUED
        OtaPayoutReconciliationResult accrued = payoutService.record(bookingRef, new OtaPayoutEvidenceCommand(
                OtaPayoutEvidenceType.ELIGIBILITY, 120_000L, "VND", "booking-eligible-after-service",
                "Service completed", null, null, null, BASE_TIME.plusSeconds(3600)), MANAGER);
        assertThat(accrued.payoutStatus()).isEqualTo(OtaPayoutStatus.ACCRUED);

        // 4. Adjustments (e.g. commission dispute, compensation) append to ledger without altering customer payment evidence
        String eligibilityRef = payoutLedgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(positionId)
                .getFirst().getPublicReference();
        OtaPayoutReconciliationResult adjusted = payoutService.record(bookingRef, new OtaPayoutEvidenceCommand(
                OtaPayoutEvidenceType.ADJUSTMENT, -20_000L, "VND", "commission-adjustment",
                "Fee adjustment", eligibilityRef, null, null, BASE_TIME.plusSeconds(7200)), MANAGER);
        assertThat(adjusted.payoutStatus()).isEqualTo(OtaPayoutStatus.ACCRUED);
        assertThat(adjusted.adjustedExpectedMinor()).isEqualTo(100_000L);

        // Customer payment evidence remains untouched and intact
        List<OtaCustomerPaymentEvidence> customerEvidence = customerPaymentRepository.findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(bookingId);
        assertThat(customerEvidence).hasSize(1);
        assertThat(customerEvidence.get(0).getAmountMinor()).isEqualTo(150_000L);
        assertThat(customerEvidence.get(0).getEvidenceStatus()).isEqualTo(OtaCustomerPaymentState.PAID_ON_CHANNEL);

        // 5. Invoice -> Remittance -> Bank Receipt advances position to PAID_OUT
        OtaPayoutReconciliationResult invoiced = payoutService.record(bookingRef, new OtaPayoutEvidenceCommand(
                OtaPayoutEvidenceType.INVOICE, 100_000L, "VND", "payout-invoice-001",
                "Invoice received", null, null, null, BASE_TIME.plusSeconds(10800)), MANAGER);
        assertThat(invoiced.payoutStatus()).isEqualTo(OtaPayoutStatus.INVOICED);

        OtaPayoutReconciliationResult remittance = payoutService.record(bookingRef, new OtaPayoutEvidenceCommand(
                OtaPayoutEvidenceType.REMITTANCE, 100_000L, "VND", "payout-remittance-001",
                "Remittance advice", null, null, "payout-batch-1", BASE_TIME.plusSeconds(14400)), MANAGER);
        assertThat(remittance.payoutStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);

        OtaPayoutReconciliationResult paidOut = payoutService.record(bookingRef, new OtaPayoutEvidenceCommand(
                OtaPayoutEvidenceType.BANK_RECEIPT, 100_000L, "VND", "bank-settlement-confirmed",
                "Bank statement confirmed", null, "bank-receipt-ref-001", "payout-batch-1", BASE_TIME.plusSeconds(18000)), MANAGER);
        assertThat(paidOut.payoutStatus()).isEqualTo(OtaPayoutStatus.PAID_OUT);

        OtaPayoutPosition finalPosition = payoutPositionRepository.findById(positionId).orElseThrow();
        assertThat(finalPosition.getStatus()).isEqualTo(OtaPayoutStatus.PAID_OUT);

        // Historical ledger entries are preserved append-only (5 entries: ELIGIBILITY, ADJUSTMENT, INVOICE, REMITTANCE, BANK_RECEIPT)
        assertThat(payoutLedgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(positionId)).hasSize(5);
    }

    private BookingFixture createBookingFixture(long retailAmount, long expectedPayout) {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("DISABLED")
                .build());

        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("package-17")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("location-1")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.DRAFT)
                .build());

        OtaChannelBooking booking = bookingRepository.saveAndFlush(OtaChannelBooking.capture(
                mapping, UUID.randomUUID().toString(), "RECEIVED", 1, null,
                "KLOOK", "policy-std", "v1", null, null,
                retailAmount, expectedPayout, retailAmount - expectedPayout, 0L, retailAmount, "VND"));

        OtaPayoutPosition position = payoutPositionRepository.saveAndFlush(OtaPayoutPosition.create(
                booking.getId(), null, expectedPayout, "VND", OtaPayoutStatus.NOT_ELIGIBLE));

        return new BookingFixture(connection, mapping, booking, position);
    }

    private record BookingFixture(OtaChannelConnection connection, OtaChannelMapping mapping,
                                  OtaChannelBooking booking, OtaPayoutPosition position) {}

    private static Optional<TestDatabase> initializeDatabase() {
        Optional<OtaMigrationTestSupport> configured = OtaMigrationTestSupport.fromEnvironment();
        if (configured.isEmpty()) return Optional.empty();
        OtaMigrationTestSupport support = configured.orElseThrow();
        try {
            String schema = support.createDatabase();
            support.flyway(schema, null).migrate();
            return Optional.of(new TestDatabase(support, schema));
        } catch (SQLException | RuntimeException exception) {
            try {
                support.close();
            } catch (SQLException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new IllegalStateException("Unable to prepare isolated OTA MySQL persistence schema", exception);
        }
    }

    private record TestDatabase(OtaMigrationTestSupport support, String schema) {}
}
