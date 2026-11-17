package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMigrationTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutEvidenceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPosition;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPositionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(OtaPayoutReconciliationService.class)
class OtaPayoutReconciliationIT {
    private static final Optional<TestDatabase> TEST_DATABASE = initializeDatabase();
    private static final Instant TIME = Instant.parse("2026-10-31T12:00:00Z");
    private static final AccountSummary MANAGER = new AccountSummary(1L, ActorRole.MANAGER,
            ActorStatus.ACTIVE, null, null, null);

    @Autowired private OtaChannelConnectionRepository connectionRepository;
    @Autowired private OtaChannelMappingRepository mappingRepository;
    @Autowired private OtaChannelBookingRepository bookingRepository;
    @Autowired private OtaPayoutPositionRepository positionRepository;
    @Autowired private OtaPayoutLedgerEntryRepository ledgerRepository;
    @Autowired private OtaReconciliationWorkItemRepository workItemRepository;
    @Autowired private OtaPayoutReconciliationService service;

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
    void advancesOnlyWithMatchedStageEvidenceAndKeepsCorrectionsAppendOnly() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture();
        assertThat(record(fixture, OtaPayoutEvidenceType.ELIGIBILITY, 80000, "eligible", null, null)
                .payoutStatus()).isEqualTo(OtaPayoutStatus.ACCRUED);
        String eligibilityId = ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                fixture.positionId()).getFirst().getPublicReference();
        assertThat(record(fixture, OtaPayoutEvidenceType.ADJUSTMENT, -10000,
                "refund-impact", eligibilityId, null).adjustedExpectedMinor()).isEqualTo(70000);
        assertThat(record(fixture, OtaPayoutEvidenceType.INVOICE, 70000, "invoice", null, null)
                .payoutStatus()).isEqualTo(OtaPayoutStatus.INVOICED);
        assertThat(record(fixture, OtaPayoutEvidenceType.REMITTANCE, 70000, "remittance", null, null)
                .payoutStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
        OtaPayoutReconciliationResult paid = record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                70000, "bank-receipt", null, "bank-receipt-booking-1");
        assertThat(paid.payoutStatus()).isEqualTo(OtaPayoutStatus.PAID_OUT);
        assertThat(ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                fixture.positionId())).hasSize(5);

        OtaPayoutReconciliationResult replay = record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                70000, "bank-receipt", null, "bank-receipt-booking-1");
        assertThat(replay.entryPublicReference()).isEqualTo(paid.entryPublicReference());
        assertThat(ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                fixture.positionId())).hasSize(5);
        assertThatThrownBy(() -> record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                60000, "bank-receipt", null, "bank-receipt-booking-1"))
                .isInstanceOf(OtaMappingLifecycleException.class);
    }

    @Test
    void mismatchedReceiptCreatesDisputeAndCannotPayOutUntilAllocatedTotalMatches() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture();
        record(fixture, OtaPayoutEvidenceType.ELIGIBILITY, 80000, "eligible", null, null);
        record(fixture, OtaPayoutEvidenceType.INVOICE, 80000, "invoice", null, null);
        record(fixture, OtaPayoutEvidenceType.REMITTANCE, 80000, "remittance", null, null);

        assertThatThrownBy(() -> record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                75000, "bank-no-allocation", null, null)).isInstanceOf(IllegalArgumentException.class);
        OtaPayoutReconciliationResult partial = record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                75000, "bank-partial", null, "bank-partial-booking-1");
        assertThat(partial.payoutStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
        assertThat(partial.disputeReference()).isNotBlank();
        assertThat(workItemRepository.findByPublicReference(partial.disputeReference())).isPresent();

        OtaPayoutReconciliationResult completed = record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                5000, "bank-balance", null, "bank-balance-booking-1");
        assertThat(completed.payoutStatus()).isEqualTo(OtaPayoutStatus.PAID_OUT);
        assertThatThrownBy(() -> service.record(fixture.bookingRef(),
                new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.DISPUTE, 0, "VND", "forbidden",
                        "no access", null, null, null, TIME),
                new AccountSummary(2L, ActorRole.STAFF, ActorStatus.ACTIVE, null, null, null)))
                .isInstanceOf(OtaForbiddenException.class);
    }

    @Test
    void correctionReversesOnlyItsOriginalFactAndWrongInvoiceCannotAdvance() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture();
        record(fixture, OtaPayoutEvidenceType.ELIGIBILITY, 80000, "eligible", null, null);
        OtaPayoutReconciliationResult adjustment = record(fixture, OtaPayoutEvidenceType.ADJUSTMENT,
                -10000, "adjustment", ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                        fixture.positionId()).getFirst().getPublicReference(), null);
        assertThat(adjustment.adjustedExpectedMinor()).isEqualTo(70000);
        assertThat(record(fixture, OtaPayoutEvidenceType.REVERSAL, 10000, "reversal",
                adjustment.entryPublicReference(), null).adjustedExpectedMinor()).isEqualTo(80000);
        assertThatThrownBy(() -> record(fixture, OtaPayoutEvidenceType.REVERSAL, 5000,
                "bad-reversal", adjustment.entryPublicReference(), null))
                .isInstanceOf(OtaMappingLifecycleException.class);

        OtaPayoutReconciliationResult mismatch = record(fixture, OtaPayoutEvidenceType.INVOICE,
                79000, "wrong-invoice", null, null);
        assertThat(mismatch.payoutStatus()).isEqualTo(OtaPayoutStatus.ACCRUED);
        assertThat(mismatch.disputeReference()).isNotBlank();
        assertThat(record(fixture, OtaPayoutEvidenceType.INVOICE, 80000,
                "correct-invoice", null, null).payoutStatus()).isEqualTo(OtaPayoutStatus.INVOICED);
        assertThat(ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                fixture.positionId())).hasSize(5);
    }

    @Test
    void wrongCurrencyReceiptCannotMarkPositionPaidOut() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture();
        record(fixture, OtaPayoutEvidenceType.ELIGIBILITY, 80000, "eligible", null, null);
        record(fixture, OtaPayoutEvidenceType.INVOICE, 80000, "invoice", null, null);
        record(fixture, OtaPayoutEvidenceType.REMITTANCE, 80000, "remittance", null, null);
        OtaPayoutReconciliationResult mismatch = service.record(fixture.bookingRef(),
                new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.BANK_RECEIPT, 80000, "USD",
                        "bank-usd", "Wrong currency on bank evidence", null, "bank-usd-booking-1",
                        "payout-1", TIME),
                MANAGER);
        assertThat(mismatch.payoutStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
        assertThat(mismatch.disputeReference()).isNotBlank();
        assertThat(positionRepository.findById(fixture.positionId()).orElseThrow().getStatus())
                .isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
        OtaPayoutReconciliationResult reversal = service.record(fixture.bookingRef(),
                new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.REVERSAL, -80000, "USD",
                        "reverse-bank-usd", "Manager voided wrong-currency bank evidence",
                        mismatch.entryPublicReference(), null, null, TIME), MANAGER);
        assertThat(reversal.adjustedExpectedMinor()).isEqualTo(80000);
        assertThat(reversal.payoutStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
        assertThat(service.record(fixture.bookingRef(),
                new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.REVERSAL, -80000, "USD",
                        "reverse-bank-usd", "Manager voided wrong-currency bank evidence",
                        mismatch.entryPublicReference(), null, null, TIME), MANAGER).entryPublicReference())
                .isEqualTo(reversal.entryPublicReference());
        assertThatThrownBy(() -> service.record(fixture.bookingRef(),
                new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.REVERSAL, -80000, "USD",
                        "reverse-bank-usd-again", "Second reversal",
                        mismatch.entryPublicReference(), null, null, TIME), MANAGER))
                .isInstanceOf(OtaMappingLifecycleException.class);
        assertThat(record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                80000, "bank-vnd", null, "bank-vnd-booking-1").payoutStatus())
                .isEqualTo(OtaPayoutStatus.PAID_OUT);
    }

    @Test
    void bankReceiptWithDifferentPayoutReferenceStaysPendingUntilReversed() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture();
        record(fixture, OtaPayoutEvidenceType.ELIGIBILITY, 80000, "eligible", null, null);
        record(fixture, OtaPayoutEvidenceType.INVOICE, 80000, "invoice", null, null);
        record(fixture, OtaPayoutEvidenceType.REMITTANCE, 80000, "remittance", null, null);
        OtaPayoutReconciliationResult mismatch = service.record(fixture.bookingRef(),
                new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.BANK_RECEIPT, 80000, "VND",
                        "bank-other-payout", "Payout reference mismatch", null,
                        "other-payout-booking-1", "payout-other", TIME), MANAGER);
        assertThat(mismatch.payoutStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
        assertThat(mismatch.disputeReference()).isNotBlank();
        service.record(fixture.bookingRef(), new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.REVERSAL,
                -80000, "VND", "reverse-other-payout", "Manager voided unmatched receipt",
                mismatch.entryPublicReference(), null, null, TIME), MANAGER);
        assertThat(record(fixture, OtaPayoutEvidenceType.BANK_RECEIPT,
                80000, "bank-matched-payout", null, "matched-payout-booking-1").payoutStatus())
                .isEqualTo(OtaPayoutStatus.PAID_OUT);
    }

    private OtaPayoutReconciliationResult record(Fixture fixture, OtaPayoutEvidenceType type,
                                                  long amount, String reference,
                                                  String relatedEntry, String allocation) {
        return service.record(fixture.bookingRef(), new OtaPayoutEvidenceCommand(type, amount, "VND",
                reference, "Manager checked the provider evidence", relatedEntry, allocation,
                type == OtaPayoutEvidenceType.REMITTANCE || type == OtaPayoutEvidenceType.BANK_RECEIPT
                        ? "payout-1" : null, TIME), MANAGER);
    }

    private Fixture fixture() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK).environment(OtaEnvironment.TEST)
                .status("DISABLED").build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection).offerReference("package-17")
                .sessionReference(UUID.randomUUID().toString()).locationReference("location-1")
                .externalProductId("product-1").externalOptionId("option-1")
                .status(OtaMappingStatus.DRAFT).build());
        OtaChannelBooking booking = bookingRepository.saveAndFlush(OtaChannelBooking.capture(
                mapping, UUID.randomUUID().toString(), "RECEIVED", 1, null,
                "KLOOK", "policy-1", "v1", null, null,
                100000L, 80000L, 20000L, 0L, 100000L, "VND"));
        OtaPayoutPosition position = positionRepository.saveAndFlush(OtaPayoutPosition.create(
                booking.getId(), null, 80000L, "VND", OtaPayoutStatus.NOT_ELIGIBLE));
        return new Fixture(booking.getPublicReference(), position.getId());
    }

    private static Optional<TestDatabase> initializeDatabase() {
        Optional<OtaMigrationTestSupport> configured = OtaMigrationTestSupport.fromEnvironment();
        if (configured.isEmpty()) return Optional.empty();
        OtaMigrationTestSupport support = configured.orElseThrow();
        try {
            String schema = support.createDatabase();
            support.flyway(schema, null).migrate();
            return Optional.of(new TestDatabase(support, schema));
        } catch (SQLException | RuntimeException exception) {
            try { support.close(); } catch (SQLException cleanup) { exception.addSuppressed(cleanup); }
            throw new IllegalStateException("Unable to prepare isolated OTA payout test schema", exception);
        }
    }

    private record Fixture(String bookingRef, Long positionId) {}
    private record TestDatabase(OtaMigrationTestSupport support, String schema) {}
}
