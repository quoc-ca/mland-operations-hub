package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSyncDeliveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingEnableCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaChannelMappingService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaReadinessCheckSubmission;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.AuthenticatedOtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaInboundEventService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheck;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckOutcome;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({OtaSyncDeliveryService.class, OtaInboundEventService.class, OtaAuditCorrelationService.class,
        OtaChannelMappingService.class})
class OtaSyncDeliveryPersistenceIT {

    private static final Optional<TestDatabase> TEST_DATABASE = initializeDatabase();

    @Autowired private OtaChannelConnectionRepository connectionRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private OtaChannelMappingRepository mappingRepository;
    @Autowired private OtaChannelPriceSnapshotRepository priceSnapshotRepository;
    @Autowired private OtaReadinessAssessmentRepository readinessAssessmentRepository;
    @Autowired private OtaReadinessCheckEvidenceRepository readinessCheckEvidenceRepository;
    @Autowired private OtaOutboundChangeRepository outboundRepository;
    @Autowired private OtaDeliveryAttemptRepository attemptRepository;
    @Autowired private OtaInboundEventRepository inboundEventRepository;
    @Autowired private OtaReconciliationWorkItemRepository workItemRepository;
    @Autowired private OtaSyncDeliveryService deliveryService;
    @Autowired private OtaInboundEventService inboundEventService;
    @Autowired private OtaChannelMappingService channelMappingService;
    @MockBean private WorkshopOperationsFacade workshopOperationsFacade;

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
        if (TEST_DATABASE.isPresent()) {
            TEST_DATABASE.get().support().close();
        }
    }

    @BeforeEach
    void requireIsolatedMysqlConfiguration() {
        Assumptions.assumeTrue(TEST_DATABASE.isPresent(),
                "Set WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD to run against a generated isolated MySQL schema");
    }

    @Test
    void snapshotReplayCreatesOneDurableChangePerKindAndExhaustionSuspendsOnlyItsMapping() {
        String sessionReference = "ota-it-" + UUID.randomUUID();
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("ota-test-offer")
                .sessionReference(sessionReference)
                .locationReference("ota-test-location")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.BOOKING_ENABLED)
                .build());
        WorkshopSessionOtaSnapshot snapshot = new WorkshopSessionOtaSnapshot(sessionReference,
                "ota-test-location", Instant.parse("2026-11-01T10:00:00Z"),
                Instant.parse("2026-11-01T11:00:00Z"), 7, "PUBLISHED", 1,
                "change-" + UUID.randomUUID(), Instant.parse("2026-10-29T10:00:00Z"));

        assertThat(deliveryService.acceptSnapshot(snapshot)).isEqualTo(3);
        assertThat(deliveryService.acceptSnapshot(snapshot)).isZero();
        assertThat(outboundRepository.findByStatusOrderByCreatedAtAsc("QUEUED"))
                .filteredOn(change -> change.getMapping().getId().equals(mapping.getId()))
                .hasSize(3);

        OtaOutboundChange change = outboundRepository.findByMappingIdAndSourceVersionAndChangeKind(
                mapping.getId(), 1L, OtaChangeKind.AVAILABILITY).orElseThrow();
        Instant startedAt = Instant.parse("2026-10-29T10:01:00Z");
        deliveryService.recordDeliveryResult(change.getId(), new OtaDeliveryResult(1, startedAt,
                startedAt.plusSeconds(1), OtaDeliveryOutcome.EXHAUSTED, UUID.randomUUID().toString(), "TIMEOUT"));

        assertThat(mappingRepository.findById(mapping.getId()).orElseThrow().getStatus())
                .isEqualTo(OtaMappingStatus.SYNC_SUSPENDED);
        assertThat(attemptRepository.findByOutboundChangeIdOrderByAttemptNumberAsc(change.getId()))
                .singleElement().satisfies(attempt -> assertThat(attempt.getOutcome()).isEqualTo("EXHAUSTED"));
        assertThat(workItemRepository.findByLocationReferenceAndStatusOrderByCreatedAtAsc(
                "ota-test-location", "OPEN"))
                .anySatisfy(item -> assertThat(item.getMapping().getId()).isEqualTo(mapping.getId()));
    }

    @Test
    void inboundReplayReturnsPersistedOutcomeWithoutCreatingSecondReceipt() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.GETYOURGUIDE)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        String eventReference = "event-" + UUID.randomUUID();
        AuthenticatedOtaInboundEvent event = new AuthenticatedOtaInboundEvent(OtaProvider.GETYOURGUIDE,
                OtaEnvironment.TEST, eventReference, "c".repeat(64), "BOOKING");

        var first = inboundEventService.receive(event);
        var completed = inboundEventService.recordOutcome(first.eventId(),
                new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.ACCEPTED, "booking-ref-" + UUID.randomUUID()));
        var replay = inboundEventService.receive(event);

        assertThat(first.duplicate()).isFalse();
        assertThat(completed.processingStatus()).isEqualTo("ACCEPTED");
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.processingStatus()).isEqualTo(completed.processingStatus());
        assertThat(replay.outcomeReference()).isEqualTo(completed.outcomeReference());
        assertThat(inboundEventRepository.findAll()).filteredOn(saved ->
                saved.getConnection().getId().equals(connection.getId())).hasSize(1);
    }

    @Test
    void providerConfirmedPriceSnapshotPersistsAndDeterminesCommercialStatus() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("ota-test-offer")
                .sessionReference("ota-price-" + UUID.randomUUID())
                .locationReference("ota-test-location")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.SALES_CLOSED)
                .build());

        assertThat(priceSnapshotRepository.resolveCommercialStatus(mapping.getId()))
                .isEqualTo(OtaCommercialStatus.PENDING_COMMERCIAL_CONFIRMATION);
        var priceSnapshot = priceSnapshotRepository.saveAndFlush(OtaChannelPriceSnapshot.providerConfirmed(
                mapping, 500_000L, 420_000L, 80_000L, "VND", "contract-ref-" + UUID.randomUUID(),
                Instant.parse("2026-11-01T00:00:00Z"), null));

        assertThat(priceSnapshotRepository.resolveCommercialStatus(mapping.getId())).isEqualTo(OtaCommercialStatus.APPROVED);
        assertThat(priceSnapshotRepository.findById(priceSnapshot.getId())).get()
                .satisfies(saved -> {
                    assertThat(saved.getMapping().getId()).isEqualTo(mapping.getId());
                    assertThat(saved.getRetailAmountMinor()).isEqualTo(500_000L);
                    assertThat(saved.getTargetNetAmountMinor()).isEqualTo(420_000L);
                    assertThat(saved.getTermsReference()).startsWith("contract-ref-");
                });
    }

    @Test
    void completeAdminReadinessDecisionPersistsImmutableEvidenceAndEnablesSalesClosedMapping() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("9876")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("42")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.SALES_CLOSED)
                .build());
        priceSnapshotRepository.saveAndFlush(OtaChannelPriceSnapshot.providerConfirmed(mapping,
                500_000L, 420_000L, 80_000L, "VND", "provider-confirmation-ref",
                Instant.parse("2026-11-01T00:00:00Z"), null));
        stubEligibleOperationsMapping(mapping);
        var decision = channelMappingService.recordBookingEnableDecision(new OtaBookingEnableCommand(
                mapping.getId(), "controlled-evidence/ota/klook/test/mapping-1", "Sandbox verification passed",
                "admin-42", verifiedReadinessChecks()));

        assertThat(decision.decision()).isEqualTo(OtaReadinessAssessmentStatus.APPROVED);
        assertThat(decision.mappingState()).isEqualTo(OtaMappingStatus.BOOKING_ENABLED);
        assertThat(decision.safeReadinessStatus()).isEqualTo(OtaSafeReadinessStatus.APPROVED);
        assertThat(mappingRepository.findById(mapping.getId()).orElseThrow().getStatus())
                .isEqualTo(OtaMappingStatus.BOOKING_ENABLED);
        var assessment = readinessAssessmentRepository.findById(
                readinessAssessmentRepository.findAllByMapping_IdOrderByRecordedAtDescIdDesc(mapping.getId())
                        .getFirst().getId()).orElseThrow();
        assertThat(assessment.getProviderCode()).isEqualTo(OtaProvider.KLOOK);
        assertThat(assessment.getEnvironment()).isEqualTo(OtaEnvironment.TEST);
        assertThat(assessment.getReviewerAccountReference()).isEqualTo("admin-42");
        assertThat(readinessCheckEvidenceRepository.findAllByAssessment_IdOrderByCheckCodeAsc(assessment.getId()))
                .hasSize(OtaReadinessCheck.values().length)
                .allSatisfy(check -> {
                    assertThat(check.getOutcome()).isEqualTo(OtaReadinessCheckOutcome.VERIFIED);
                    assertThat(check.getEvidenceReference()).isNotBlank();
                });
        assertThat(channelMappingService.resolveSafeReadinessStatus(mapping.getId()))
                .isEqualTo(OtaSafeReadinessStatus.APPROVED);

        OtaChannelConnection updatedConnection = connectionRepository.findById(connection.getId()).orElseThrow();
        updatedConnection.setEnvironment(OtaEnvironment.PRODUCTION);
        connectionRepository.flush();
        assertThat(channelMappingService.resolveSafeReadinessStatus(mapping.getId()))
                .isEqualTo(OtaSafeReadinessStatus.INCOMPLETE);
        channelMappingService.closeSales(mapping.getId());
        channelMappingService.startPreparationSync(mapping.getId());
        channelMappingService.completePreparationSync(mapping.getId());
        assertThat(mappingRepository.findById(mapping.getId()).orElseThrow().getStatus())
                .isEqualTo(OtaMappingStatus.SALES_CLOSED);
    }

    @Test
    void incompleteAdminReadinessDecisionIsRetainedAndKeepsMappingSalesClosed() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.GETYOURGUIDE)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("8765")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("42")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.SALES_CLOSED)
                .build());
        priceSnapshotRepository.saveAndFlush(OtaChannelPriceSnapshot.providerConfirmed(mapping,
                500_000L, 420_000L, 80_000L, "VND", "provider-confirmation-ref",
                Instant.parse("2026-11-01T00:00:00Z"), null));
        stubEligibleOperationsMapping(mapping);
        var checks = verifiedReadinessChecks();
        checks.set(0, new OtaReadinessCheckSubmission(checks.get(0).check(), OtaReadinessCheckOutcome.MISSING, null));

        var decision = channelMappingService.recordBookingEnableDecision(new OtaBookingEnableCommand(
                mapping.getId(), "controlled-evidence/ota/gyg/test/mapping-2", "Ticket scan evidence absent",
                "admin-84", checks));

        assertThat(decision.decision()).isEqualTo(OtaReadinessAssessmentStatus.REJECTED);
        assertThat(decision.mappingState()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        assertThat(decision.safeReadinessStatus()).isEqualTo(OtaSafeReadinessStatus.REJECTED);
        assertThat(mappingRepository.findById(mapping.getId()).orElseThrow().getStatus())
                .isEqualTo(OtaMappingStatus.SALES_CLOSED);
        assertThat(readinessAssessmentRepository.findAllByMapping_IdOrderByRecordedAtDescIdDesc(mapping.getId()))
                .singleElement().satisfies(assessment ->
                        assertThat(assessment.getDecisionStatus()).isEqualTo(OtaReadinessAssessmentStatus.REJECTED));
    }

    @Test
    void bookingEnablementRequiresCommercialSnapshotAndSalesClosedState() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("ota-blocked-offer")
                .sessionReference("ota-blocked-" + UUID.randomUUID())
                .locationReference("ota-test-location")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.DRAFT)
                .build());

        assertThatThrownBy(() -> channelMappingService.recordBookingEnableDecision(new OtaBookingEnableCommand(
                mapping.getId(), "controlled-evidence/ota/klook/test/mapping-3", "Ready", "admin-42",
                verifiedReadinessChecks())))
                .isInstanceOf(OtaMappingLifecycleException.class);
        assertThat(readinessAssessmentRepository.findAllByMapping_IdOrderByRecordedAtDescIdDesc(mapping.getId()))
                .isEmpty();
    }

    @Test
    void salesClosedMappingWithoutConfirmedPriceCannotBeEnabled() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("6543")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("42")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.SALES_CLOSED)
                .build());
        stubEligibleOperationsMapping(mapping);

        assertThatThrownBy(() -> channelMappingService.recordBookingEnableDecision(new OtaBookingEnableCommand(
                mapping.getId(), "controlled-evidence/ota/klook/test/mapping-4", "Ready", "admin-42",
                verifiedReadinessChecks())))
                .isInstanceOf(OtaMappingLifecycleException.class)
                .hasMessageContaining("commercial terms");
        assertThat(readinessAssessmentRepository.findAllByMapping_IdOrderByRecordedAtDescIdDesc(mapping.getId()))
                .isEmpty();
        assertThat(mappingRepository.findById(mapping.getId()).orElseThrow().getStatus())
                .isEqualTo(OtaMappingStatus.SALES_CLOSED);
    }

    @Test
    void sharedMultiSessionPackageCannotBeEnabledForInitialPerPersonOtaRelease() {
        OtaChannelConnection connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("test-only-reference")
                .build());
        OtaChannelMapping mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder()
                .connection(connection)
                .offerReference("3210")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("42")
                .externalProductId("product-" + UUID.randomUUID())
                .externalOptionId("option-" + UUID.randomUUID())
                .status(OtaMappingStatus.SALES_CLOSED)
                .build());
        priceSnapshotRepository.saveAndFlush(OtaChannelPriceSnapshot.providerConfirmed(mapping,
                500_000L, 420_000L, 80_000L, "VND", "provider-confirmation-ref",
                Instant.parse("2026-11-01T00:00:00Z"), null));
        when(workshopOperationsFacade.getPublishedPackageSelection(any()))
                .thenReturn(new PublishedPackageSelectionSnapshot(mapping.getOfferReference(), 500_000L,
                        1, 10, 2, true, 0, "Multi-session workshop"));

        assertThatThrownBy(() -> channelMappingService.recordBookingEnableDecision(new OtaBookingEnableCommand(
                mapping.getId(), "controlled-evidence/ota/klook/test/mapping-5", "Readiness checks complete",
                "admin-42", verifiedReadinessChecks())))
                .isInstanceOf(OtaMappingLifecycleException.class)
                .hasMessageContaining("per-person");
        assertThat(readinessAssessmentRepository.findAllByMapping_IdOrderByRecordedAtDescIdDesc(mapping.getId()))
                .isEmpty();
        assertThat(mappingRepository.findById(mapping.getId()).orElseThrow().getStatus())
                .isEqualTo(OtaMappingStatus.SALES_CLOSED);
    }

    @Test
    void mappedWorkItemPersistsCorrelationAndQueriesExcludeMappingIndependentItems() {
        var connection = connectionRepository.saveAndFlush(OtaChannelConnection.builder()
                .providerCode(OtaProvider.KLOOK).environment(OtaEnvironment.TEST).status("ACTIVE").build());
        var mapping = mappingRepository.saveAndFlush(OtaChannelMapping.builder().connection(connection)
                .offerReference("correlation-offer").sessionReference(UUID.randomUUID().toString())
                .locationReference("test-location").externalProductId("correlation-product")
                .externalOptionId("correlation-option").status(OtaMappingStatus.SYNC_SUSPENDED).build());
        var mapped = workItemRepository.saveAndFlush(OtaReconciliationWorkItem.builder().mapping(mapping)
                .locationReference("test-location").category("SOURCE_VERSION_GAP").status("OPEN").build());
        String correlation = mapped.getAuditReference();
        String publicReference = mapped.getPublicReference();
        assertThat(UUID.fromString(correlation).toString()).isEqualTo(correlation);
        var event = inboundEventRepository.saveAndFlush(OtaInboundEvent.builder().connection(connection)
                .payloadFingerprint("a".repeat(64)).dedupeKey("b".repeat(64)).eventKind("BOOKING")
                .authenticationResult("VERIFIED").processingStatus("RECEIVED").receivedAt(Instant.now()).build());
        var independent = workItemRepository.saveAndFlush(OtaReconciliationWorkItem.builder().inboundEvent(event)
                .locationReference("test-location").category("UNMATCHED_EVENT").status("OPEN").build());
        String independentReference = independent.getPublicReference();
        entityManager.clear();

        assertThat(workItemRepository.findMappingLinkedOrderByCreatedAtAsc())
                .extracting(OtaReconciliationWorkItem::getPublicReference).containsExactly(publicReference);
        assertThat(workItemRepository.findMappingLinkedByStatusOrderByCreatedAtAsc("OPEN"))
                .extracting(OtaReconciliationWorkItem::getAuditReference).containsExactly(correlation);
        assertThat(workItemRepository.findMappingLinkedByStatusOrderByCreatedAtAsc("RESOLVED")).isEmpty();
        assertThat(workItemRepository.findMappingLinkedByPublicReferenceForUpdate(independentReference)).isEmpty();
        var reloaded = workItemRepository.findMappingLinkedByPublicReferenceForUpdate(publicReference).orElseThrow();
        reloaded.setStatus("IN_PROGRESS");
        workItemRepository.saveAndFlush(reloaded);
        entityManager.clear();
        assertThat(workItemRepository.findByPublicReference(publicReference).orElseThrow().getAuditReference())
                .isEqualTo(correlation);
    }

    private static java.util.ArrayList<OtaReadinessCheckSubmission> verifiedReadinessChecks() {
        java.util.ArrayList<OtaReadinessCheckSubmission> checks = new java.util.ArrayList<>();
        for (OtaReadinessCheck check : OtaReadinessCheck.values()) {
            checks.add(new OtaReadinessCheckSubmission(check, OtaReadinessCheckOutcome.VERIFIED,
                    "evidence/" + check.name().toLowerCase(java.util.Locale.ROOT)));
        }
        return checks;
    }

    private void stubEligibleOperationsMapping(OtaChannelMapping mapping) {
        when(workshopOperationsFacade.getPublishedPackageSelection(any()))
                .thenReturn(new PublishedPackageSelectionSnapshot(mapping.getOfferReference(), 500_000L,
                        1, 10, 1, true, 0, "Test workshop"));
        when(workshopOperationsFacade.getSaleEligibility(any()))
                .thenReturn(new SaleEligibilitySnapshot(UUID.fromString(mapping.getSessionReference()), true,
                        "PUBLISHED", 42L, Instant.parse("2026-11-01T10:00:00Z"),
                        Instant.parse("2026-11-01T11:00:00Z"), 10, LocalDate.parse("2026-11-01")));
    }

    private static Optional<TestDatabase> initializeDatabase() {
        Optional<OtaMigrationTestSupport> configured = OtaMigrationTestSupport.fromEnvironment();
        if (configured.isEmpty()) {
            return Optional.empty();
        }
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
            throw new IllegalStateException("Unable to prepare the isolated OTA MySQL integration schema", exception);
        }
    }

    private record TestDatabase(OtaMigrationTestSupport support, String schema) {
    }
}
