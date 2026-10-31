package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
class OtaChannelBookingPersistenceIT {
    private static final Optional<TestDatabase> TEST_DATABASE = initializeDatabase();

    @Autowired private OtaChannelConnectionRepository connectionRepository;
    @Autowired private OtaChannelMappingRepository mappingRepository;
    @Autowired private OtaChannelBookingRepository bookingRepository;
    @Autowired private OtaBookingTicketLinkRepository ticketLinkRepository;
    @Autowired private OtaPayoutPositionRepository payoutPositionRepository;
    @Autowired private OtaPayoutLedgerEntryRepository payoutLedgerRepository;
    @Autowired private OtaCustomerPaymentEvidenceRepository paymentEvidenceRepository;
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
    void persistsPublicBookingIdentityImmutableSnapshotAndParticipantLinks() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL persistence evidence");

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
                .externalProductId("product-1")
                .externalOptionId("option-1")
                .status(OtaMappingStatus.DRAFT)
                .build());
        Instant cutoff = Instant.parse("2026-11-01T10:00:00Z");
        OtaChannelBooking booking = bookingRepository.saveAndFlush(OtaChannelBooking.capture(
                mapping, "provider-booking-1", "RECEIVED", 2, null,
                "KLOOK", "policy-1", "v1", cutoff, "Asia/Ho_Chi_Minh",
                50000L, 40000L, 10000L, 0L, 50000L, "VND"));
        String publicReference = booking.getPublicReference();
        assertThat(UUID.fromString(publicReference)).isNotNull();
        assertThat(booking.getId()).isPositive();

        ticketLinkRepository.saveAndFlush(OtaBookingTicketLink.link(booking.getId(), 1, "ticket-1"));
        ticketLinkRepository.saveAndFlush(OtaBookingTicketLink.link(booking.getId(), 2, "ticket-2"));
        entityManager.clear();

        OtaChannelBooking reloaded = bookingRepository.findByPublicReference(publicReference).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(booking.getId());
        assertThat(reloaded.getExternalBookingReference()).isEqualTo("provider-booking-1");
        assertThat(reloaded.getBookingCutoffAt()).isEqualTo(cutoff);
        assertThat(reloaded.getAcceptedAmountMinor()).isEqualTo(50000L);
        assertThat(reloaded.getCurrencyCode()).isEqualTo("VND");
        assertThat(ticketLinkRepository.findAllByChannelBookingIdOrderByParticipantOrdinalAsc(reloaded.getId()))
                .extracting(OtaBookingTicketLink::getOperationsTicketReference)
                .containsExactly("ticket-1", "ticket-2");
    }

    @Test
    void persistsPayoutExplanationAndRelatedRecordWithoutConfusingTraceReference() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL persistence evidence");

        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.GETYOURGUIDE)
                .environment(OtaEnvironment.TEST)
                .status("DISABLED")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("package-18")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("location-2")
                .externalProductId("product-2")
                .externalOptionId("option-2")
                .status(OtaMappingStatus.DRAFT)
                .build());
        OtaChannelBooking booking = bookingRepository.saveAndFlush(OtaChannelBooking.capture(
                mapping, "provider-booking-2", "RECEIVED", 1, null,
                "GETYOURGUIDE", "policy-2", "v1", null, null,
                50000L, 40000L, 10000L, 0L, 50000L, "VND"));
        OtaPayoutPosition position = payoutPositionRepository.saveAndFlush(OtaPayoutPosition.create(
                booking.getId(), null, 40000L, "VND", OtaPayoutStatus.ACCRUED));
        Instant occurredAt = Instant.parse("2026-10-31T12:00:00Z");
        OtaPayoutLedgerEntry saved = payoutLedgerRepository.save(OtaPayoutLedgerEntry.record(
                position.getId(), OtaPayoutLedgerKind.GROSS_SALE, 50000L, "VND", "evidence-2", occurredAt,
                "Provider confirmed gross sale", "provider-booking-2", "trace-2"));
        entityManager.flush();
        entityManager.createNativeQuery("INSERT INTO ota_payout_ledger_entries "
                        + "(public_reference, payout_position_id, entry_kind, amount_minor, currency_code, "
                        + "external_evidence_reference, occurred_at) VALUES "
                        + "(UUID(), ?1, 'LEGACY_KIND', 100, 'VND', 'legacy-evidence-2', '2026-10-30 12:00:00')")
                .setParameter(1, position.getId())
                .executeUpdate();
        entityManager.clear();

        var entries = payoutLedgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(position.getId());
        assertThat(entries).hasSize(2);
        OtaPayoutLedgerEntry reloaded = entries.getLast();
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(UUID.fromString(reloaded.getPublicReference())).isNotNull();
        assertThat(reloaded.getReason()).isEqualTo("Provider confirmed gross sale");
        assertThat(reloaded.getEntryKind()).isEqualTo(OtaPayoutLedgerKind.GROSS_SALE.name());
        assertThat(reloaded.getRelatedRecordReference()).isEqualTo("provider-booking-2");
        assertThat(reloaded.getCorrelationReference()).isEqualTo("trace-2");
        assertThat(reloaded.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(entries.getFirst().getEntryKind()).isEqualTo("LEGACY_KIND");
        assertThat(entries.getFirst().getReason()).isNull();
        assertThatThrownBy(() -> OtaPayoutLedgerEntry.record(position.getId(), OtaPayoutLedgerKind.GROSS_SALE,
                50000L, "VND", "evidence-3", occurredAt, " ", "provider-booking-2", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtaPayoutLedgerEntry.record(position.getId(), OtaPayoutLedgerKind.GROSS_SALE,
                50000L, "VND", "evidence-4", occurredAt, "Provider confirmed sale", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesProviderScopedCustomerPaymentEvidenceAsSeparateFacts() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL persistence evidence");

        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.PRODUCTION)
                .status("DISABLED")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("package-19")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("location-3")
                .externalProductId("product-3")
                .externalOptionId("option-3")
                .status(OtaMappingStatus.DRAFT)
                .build());
        OtaChannelBooking booking = bookingRepository.saveAndFlush(OtaChannelBooking.capture(
                mapping, "provider-booking-3", "RECEIVED", 1, null,
                "KLOOK", "policy-3", "v1", null, null,
                50000L, 40000L, 10000L, 0L, 50000L, "VND"));
        Instant occurredAt = Instant.parse("2026-10-31T13:00:00Z");
        paymentEvidenceRepository.save(OtaCustomerPaymentEvidence.record(booking.getId(),
                "provider-payment-3", "PAYMENT", OtaCustomerPaymentState.PAID_ON_CHANNEL,
                50000L, "VND", occurredAt, "trace-payment-3"));
        paymentEvidenceRepository.save(OtaCustomerPaymentEvidence.record(booking.getId(),
                "provider-refund-3", "REFUND", OtaCustomerPaymentState.PARTIALLY_REFUNDED,
                10000L, "VND", occurredAt.plusSeconds(60), "trace-refund-3"));
        entityManager.flush();
        entityManager.clear();

        assertThat(paymentEvidenceRepository.findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(booking.getId()))
                .extracting(OtaCustomerPaymentEvidence::getEvidenceStatus)
                .containsExactly(OtaCustomerPaymentState.PAID_ON_CHANNEL,
                        OtaCustomerPaymentState.PARTIALLY_REFUNDED);
        assertThat(paymentEvidenceRepository.findByChannelBookingIdAndExternalEvidenceReference(
                booking.getId(), "provider-payment-3").orElseThrow().getAmountMinor())
                .isEqualTo(50000L);
        assertThatThrownBy(() -> OtaCustomerPaymentEvidence.record(booking.getId(),
                "provider-payment-4", "PAYMENT", null, 50000L, "VND", occurredAt, null))
                .isInstanceOf(IllegalArgumentException.class);
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
            try {
                support.close();
            } catch (SQLException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new IllegalStateException("Unable to prepare the isolated OTA MySQL persistence schema", exception);
        }
    }

    private record TestDatabase(OtaMigrationTestSupport support, String schema) {}
}
