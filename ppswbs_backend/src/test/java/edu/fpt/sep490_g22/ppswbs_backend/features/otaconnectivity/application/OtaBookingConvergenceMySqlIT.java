package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaCancellationAdapterRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationConsequenceService.CancellationConsequenceResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaInboundConflictRecorder;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaInboundEventService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMigrationTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutEvidenceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPosition;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPositionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.OtaBookingOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Convergence ITs (T106–T116) on MySQL with the real handler, readiness, inbound-event and payout services; only the
 * cross-module facades are mocked. Skipped unless the WORKSHOP_OTA_TEST_JDBC_* variables are set.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({OtaProviderEventHandler.class, OtaCancellationService.class, OtaBookingReadinessService.class,
        OtaPayoutLedgerRecorder.class, OtaPayoutReconciliationService.class, OtaInboundEventService.class,
        OtaAuditCorrelationService.class, OtaInboundConflictRecorder.class})
class OtaBookingConvergenceMySqlIT {
    private static final Optional<TestDatabase> TEST_DATABASE = initializeDatabase();
    private static final AccountSummary MANAGER = new AccountSummary(1L, ActorRole.MANAGER,
            ActorStatus.ACTIVE, null, null, null);

    @Autowired private OtaProviderEventHandler handler;
    @Autowired private OtaCancellationService cancellationService;
    @Autowired private OtaPayoutReconciliationService payoutService;
    @Autowired private OtaChannelConnectionRepository connectionRepository;
    @Autowired private OtaChannelMappingRepository mappingRepository;
    @Autowired private OtaChannelPriceSnapshotRepository priceRepository;
    @Autowired private OtaChannelBookingRepository bookingRepository;
    @Autowired private OtaPayoutPositionRepository positionRepository;
    @Autowired private OtaPayoutLedgerEntryRepository ledgerRepository;
    @Autowired private OtaCustomerPaymentEvidenceRepository paymentRepository;
    @Autowired private OtaReconciliationWorkItemRepository workItemRepository;

    @MockitoBean private WorkshopBookingFacade bookingFacade;
    @MockitoBean private OtaCancellationConsequenceService consequenceService;
    @MockitoBean private OtaCancellationAdapterRegistry adapterRegistry;

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
    void bookingCallbackOpensThePayoutPositionAndReachesPaidOut() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture(null);
        when(bookingFacade.createOrReturnOtaBooking(any())).thenReturn(
                OtaBookingOutcome.accepted("MLAND-1", List.of(UUID.randomUUID(), UUID.randomUUID())));

        OtaBookingEventResult confirmed = handler.handleBookingEvent(command(fixture, "evt-pay-1", "ext-pay-1",
                "USD", 55_000L, "policy-free-cancel", "2026-11", "Asia/Bangkok", 5_000L));

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        OtaChannelBooking booking = bookingRepository.findByPublicReference(confirmed.channelBookingReference())
                .orElseThrow();
        // T111 / T113: amount, currency and the callback's policy facts are stored as reported.
        assertThat(booking.getAcceptedAmountMinor()).isEqualTo(55_000L);
        assertThat(booking.getCurrencyCode()).isEqualTo("USD");
        assertThat(booking.getDisplayedPolicyReference()).isEqualTo("policy-free-cancel");
        assertThat(booking.getBookingCutoffTimezone()).isEqualTo("Asia/Bangkok");
        assertThat(booking.getFeeAmountMinor()).isEqualTo(5_000L);

        // T110: one NOT_ELIGIBLE position, expected net and currency from the price snapshot.
        List<OtaPayoutPosition> positions = positionRepository
                .findAllByChannelBookingIdOrderByCreatedAtAscIdAsc(booking.getId());
        assertThat(positions).hasSize(1);
        OtaPayoutPosition position = positions.getFirst();
        assertThat(position.getStatus()).isEqualTo(OtaPayoutStatus.NOT_ELIGIBLE);
        assertThat(position.getExpectedAmountMinor()).isEqualTo(120_000L);
        assertThat(position.getCurrencyCode()).isEqualTo("VND");

        // T114: gross sale and commission are on the position from the start.
        List<OtaPayoutLedgerEntry> opening = ledgerRepository
                .findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(position.getId());
        assertThat(opening).extracting(OtaPayoutLedgerEntry::getEntryKind)
                .containsExactlyInAnyOrder("GROSS_SALE", "COMMISSION_FEE");
        assertThat(opening).filteredOn(e -> e.getEntryKind().equals("GROSS_SALE")).singleElement()
                .satisfies(e -> {
                    assertThat(e.getAmountMinor()).isEqualTo(55_000L);
                    assertThat(e.getCurrencyCode()).isEqualTo("USD");
                });
        assertThat(opening).filteredOn(e -> e.getEntryKind().equals("COMMISSION_FEE")).singleElement()
                .satisfies(e -> assertThat(e.getAmountMinor()).isEqualTo(30_000L));

        // The opening facts do not disturb the approved payout lifecycle: callback -> PAID_OUT.
        String ref = booking.getPublicReference();
        assertThat(payoutService.record(ref, evidence(OtaPayoutEvidenceType.ELIGIBILITY, 120_000, "eligible", null),
                MANAGER).payoutStatus()).isEqualTo(OtaPayoutStatus.ACCRUED);
        assertThat(payoutService.record(ref, evidence(OtaPayoutEvidenceType.INVOICE, 120_000, "invoice", null),
                MANAGER).payoutStatus()).isEqualTo(OtaPayoutStatus.INVOICED);
        assertThat(payoutService.record(ref, evidence(OtaPayoutEvidenceType.REMITTANCE, 120_000, "remit", "payout-1"),
                MANAGER).payoutStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
        assertThat(payoutService.record(ref, new OtaPayoutEvidenceCommand(OtaPayoutEvidenceType.BANK_RECEIPT,
                120_000, "VND", "bank", "bank receipt checked", null, "alloc-1", "payout-1", Instant.now()),
                MANAGER).payoutStatus()).isEqualTo(OtaPayoutStatus.PAID_OUT);

        // T114: COMPENSATION is an accepted evidence type and records its own fact without moving the stage.
        assertThat(payoutService.record(ref, evidence(OtaPayoutEvidenceType.COMPENSATION, 15_000, "comp-1", null),
                MANAGER).payoutStatus()).isEqualTo(OtaPayoutStatus.PAID_OUT);
        assertThat(ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(position.getId()))
                .extracting(OtaPayoutLedgerEntry::getEntryKind).contains("COMPENSATION");
    }

    @Test
    void replayedBookingCallbackReturnsTheStoredOutcomeAndRunsNothingTwice() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture(null);
        when(bookingFacade.createOrReturnOtaBooking(any())).thenReturn(
                OtaBookingOutcome.accepted("MLAND-2", List.of(UUID.randomUUID())));
        OtaBookingEventCommand cmd = command(fixture, "evt-replay-1", "ext-replay-1", "VND", 150_000L,
                null, null, null, null);

        OtaBookingEventResult first = handler.handleBookingEvent(cmd);
        OtaBookingEventResult replay = handler.handleBookingEvent(cmd);

        assertThat(replay.status()).isEqualTo("CONFIRMED");
        assertThat(replay.channelBookingReference()).isEqualTo(first.channelBookingReference());
        assertThat(replay.ticketReferences()).containsExactlyElementsOf(first.ticketReferences());
        verify(bookingFacade, times(1)).createOrReturnOtaBooking(any());
        OtaChannelBooking booking = bookingRepository.findByPublicReference(first.channelBookingReference())
                .orElseThrow();
        assertThat(positionRepository.findAllByChannelBookingIdOrderByCreatedAtAscIdAsc(booking.getId())).hasSize(1);
        // T111: without callback facts the policy comes from the mapping's terms; cutoff and fee stay unknown.
        assertThat(booking.getDisplayedPolicyReference()).isEqualTo("terms-1");
        assertThat(booking.getBookingCutoffAt()).isNull();
        assertThat(booking.getFeeAmountMinor()).isNull();
    }

    @Test
    void replayedRejectionKeepsOneWorkItemAndNeverReevaluates() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture(Instant.now().minusSeconds(60));
        OtaBookingEventCommand cmd = command(fixture, "evt-expired-1", "ext-expired-1", "VND", 150_000L,
                null, null, null, null);

        OtaBookingEventResult first = handler.handleBookingEvent(cmd);
        OtaBookingEventResult replay = handler.handleBookingEvent(cmd);

        // T112: the only snapshot expired a minute ago.
        assertThat(first.rejectionReason()).isEqualTo("NO_ACTIVE_COMMERCIAL_TERMS");
        assertThat(first.reconciliationWorkItemReference()).isNotNull();
        assertThat(replay.status()).isEqualTo("REJECTED");
        assertThat(replay.rejectionReason()).isEqualTo("NO_ACTIVE_COMMERCIAL_TERMS");
        assertThat(workItemRepository.findByCategoryOrderByCreatedAtAsc("INBOUND_EVENT")
                .stream().filter(i -> "NO_ACTIVE_COMMERCIAL_TERMS".equals(i.getDecision()))).hasSize(1);
        verify(bookingFacade, never()).createOrReturnOtaBooking(any());
    }

    @Test
    void unmappedBookingCallbackOpensOneInboundEventWorkItemThatNamesItsProvider() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture(null);
        OtaBookingEventCommand cmd = new OtaBookingEventCommand(OtaProvider.KLOOK, OtaEnvironment.TEST,
                "evt-unmapped-1", "ext-unmapped-1", "no-such-product", "no-such-option", 1, 150_000L, "VND",
                "pay-x", "cor-x");

        OtaBookingEventResult first = handler.handleBookingEvent(cmd);
        OtaBookingEventResult replay = handler.handleBookingEvent(cmd);

        assertThat(first.rejectionReason()).isEqualTo("MAPPING_NOT_FOUND");
        assertThat(first.reconciliationWorkItemReference()).isNotNull();
        assertThat(replay.rejectionReason()).isEqualTo("MAPPING_NOT_FOUND");
        OtaReconciliationWorkItem item = workItemRepository.findByPublicReference(first.reconciliationWorkItemReference())
                .orElseThrow();
        assertThat(item.getCategory()).isEqualTo("INBOUND_EVENT");
        assertThat(item.getMapping()).isNull();
        assertThat(item.getInboundEvent().getConnection().getProviderCode()).isEqualTo(OtaProvider.KLOOK);
        assertThat(workItemRepository.findByCategoryOrderByCreatedAtAsc("INBOUND_EVENT")
                .stream().filter(i -> "MAPPING_NOT_FOUND".equals(i.getDecision()))).hasSize(1);
        assertThat(fixture.mapping().getId()).isNotNull();
    }

    @Test
    void cancellationReleasesOnceIgnoresReplayAndLateRequestAndLinksUnknownBookingToItsEvent() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture(null);
        when(bookingFacade.createOrReturnOtaBooking(any())).thenReturn(
                OtaBookingOutcome.accepted("MLAND-3", List.of(UUID.randomUUID())));
        when(consequenceService.applyConfirmedConsequence(any(), any(), any()))
                .thenReturn(CancellationConsequenceResult.success());
        OtaBookingEventResult confirmed = handler.handleBookingEvent(command(fixture, "evt-canc-b", "ext-canc-1",
                "VND", 150_000L, null, null, null, null));
        OtaChannelBooking booking = bookingRepository.findByPublicReference(confirmed.channelBookingReference())
                .orElseThrow();

        OtaCancellationEventCommand cancel = new OtaCancellationEventCommand(OtaProvider.KLOOK, OtaEnvironment.TEST,
                "evt-canc-1", "ext-canc-1", "CONFIRMED_CANCELLED", null, null, "cancelled", "cor-1");
        OtaCancellationEventResult first = cancellationService.handleCancellationEvent(cancel);
        OtaCancellationEventResult replay = cancellationService.handleCancellationEvent(cancel);
        OtaCancellationEventResult late = cancellationService.handleCancellationEvent(new OtaCancellationEventCommand(
                OtaProvider.KLOOK, OtaEnvironment.TEST, "evt-canc-late", "ext-canc-1", "REQUESTED", null, null,
                "late", "cor-2"));

        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(replay.status()).isEqualTo("CONFIRMED");
        assertThat(late.status()).isEqualTo("REJECTED");
        assertThat(late.rejectionReason()).isEqualTo("LATE_REQUEST_AFTER_CANCELLATION");
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getProviderStatus())
                .isEqualTo("CANCELLED");
        verify(consequenceService, times(1)).applyConfirmedConsequence(any(), any(), any());
        // T109: no refund fact of Mland's own was written, only the sale payment evidence.
        assertThat(paymentRepository.findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(booking.getId())).hasSize(1);

        // T116: an unknown booking opens an INBOUND_EVENT item bound to its inbound event, once.
        OtaCancellationEventCommand unknown = new OtaCancellationEventCommand(OtaProvider.KLOOK, OtaEnvironment.TEST,
                "evt-canc-unknown", "ext-does-not-exist", "CONFIRMED_CANCELLED", null, null, "cancelled", "cor-3");
        OtaCancellationEventResult unknownFirst = cancellationService.handleCancellationEvent(unknown);
        OtaCancellationEventResult unknownReplay = cancellationService.handleCancellationEvent(unknown);
        assertThat(unknownFirst.reconciliationWorkItemReference()).isNotNull();
        assertThat(unknownReplay.rejectionReason()).isEqualTo("UNKNOWN_BOOKING_REFERENCE");
        OtaReconciliationWorkItem item = workItemRepository
                .findByPublicReference(unknownFirst.reconciliationWorkItemReference()).orElseThrow();
        assertThat(item.getInboundEvent().getConnection().getProviderCode()).isEqualTo(OtaProvider.KLOOK);
        assertThat(workItemRepository.findByCategoryOrderByCreatedAtAsc("INBOUND_EVENT").stream()
                .filter(i -> "UNKNOWN_BOOKING_REFERENCE".equals(i.getDecision()))).hasSize(1);
    }

    @Test
    void refundEventWritesTheCustomerRefundFactAndLedgerEntry() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent());
        Fixture fixture = fixture(null);
        when(bookingFacade.createOrReturnOtaBooking(any())).thenReturn(
                OtaBookingOutcome.accepted("MLAND-4", List.of(UUID.randomUUID())));
        when(consequenceService.applyConfirmedConsequence(any(), any(), any()))
                .thenReturn(CancellationConsequenceResult.success());
        OtaBookingEventResult confirmed = handler.handleBookingEvent(command(fixture, "evt-ref-b", "ext-ref-1",
                "VND", 150_000L, null, null, null, null));
        OtaChannelBooking booking = bookingRepository.findByPublicReference(confirmed.channelBookingReference())
                .orElseThrow();

        cancellationService.handleCancellationEvent(new OtaCancellationEventCommand(OtaProvider.KLOOK,
                OtaEnvironment.TEST, "evt-ref-1", "ext-ref-1", "REFUNDED", 100_000L, "VND", "partial refund",
                "cor-4"));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getProviderStatus()).isEqualTo("REFUNDED");
        assertThat(paymentRepository.findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(booking.getId()))
                .extracting(e -> e.getEvidenceStatus().name()).containsExactly("PAID_ON_CHANNEL", "REFUNDED");
        OtaPayoutPosition position = positionRepository
                .findAllByChannelBookingIdOrderByCreatedAtAscIdAsc(booking.getId()).getFirst();
        assertThat(ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(position.getId()))
                .filteredOn(e -> e.getEntryKind().equals("CUSTOMER_REFUND")).singleElement()
                .satisfies(e -> assertThat(e.getAmountMinor()).isEqualTo(100_000L));
        // A partial refund is the OTA's own logic: no reconciliation item is opened for it.
        assertThat(workItemRepository.findByCategoryOrderByCreatedAtAsc("INBOUND_EVENT")).isEmpty();
    }

    private OtaPayoutEvidenceCommand evidence(OtaPayoutEvidenceType type, long amount, String reference,
                                              String payoutReference) {
        return new OtaPayoutEvidenceCommand(type, amount, "VND", reference, "Manager checked the evidence", null,
                null, payoutReference, Instant.now());
    }

    private OtaBookingEventCommand command(Fixture fixture, String eventRef, String bookingRef, String currency,
                                           long amount, String policy, String version, String zone, Long fee) {
        return new OtaBookingEventCommand(OtaProvider.KLOOK, OtaEnvironment.TEST, eventRef, bookingRef,
                "product-1", "option-1", 2, amount, currency, "pay-" + bookingRef, "cor-" + bookingRef,
                policy, version, null, zone, fee);
    }

    private Fixture fixture(Instant termsEffectiveTo) {
        OtaChannelConnection connection = connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK,
                OtaEnvironment.TEST).orElseGet(() -> connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK).environment(OtaEnvironment.TEST).status("ACTIVE").build()));
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection).offerReference("package-" + UUID.randomUUID())
                .sessionReference(UUID.randomUUID().toString()).locationReference("location-1")
                .externalProductId("product-1").externalOptionId("option-1")
                .status(OtaMappingStatus.BOOKING_ENABLED).build());
        priceRepository.saveAndFlush(OtaChannelPriceSnapshot.providerConfirmed(mapping, 150_000L, 120_000L,
                30_000L, "VND", "terms-1", Instant.now().minusSeconds(3600), termsEffectiveTo));
        return new Fixture(mapping);
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
            throw new IllegalStateException("Unable to prepare isolated OTA convergence test schema", exception);
        }
    }

    private record Fixture(OtaChannelMapping mapping) {}
    private record TestDatabase(OtaMigrationTestSupport support, String schema) {}
}
