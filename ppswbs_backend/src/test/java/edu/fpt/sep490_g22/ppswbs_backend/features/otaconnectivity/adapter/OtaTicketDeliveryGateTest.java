package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaTicketDeliveryGate.ProviderTicketDeliveryProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaTicketDeliveryGate.TicketDeliveryMode;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingEnableCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingEnableDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaChannelMappingService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaReadinessCheckSubmission;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheck;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("T028: OTA Ticket Delivery Gate Tests")
class OtaTicketDeliveryGateTest {

    private final OtaTicketDeliveryGate ticketDeliveryGate = new OtaTicketDeliveryGate();

    @Mock private OtaChannelMappingRepository mappingRepository;
    @Mock private OtaChannelPriceSnapshotRepository priceSnapshotRepository;
    @Mock private OtaReadinessAssessmentRepository assessmentRepository;
    @Mock private OtaReadinessCheckEvidenceRepository checkEvidenceRepository;
    @Mock private WorkshopOperationsFacade workshopOperationsFacade;

    private OtaChannelMappingService mappingService;

    @BeforeEach
    void setUp() {
        mappingService = new OtaChannelMappingService(
                mappingRepository,
                priceSnapshotRepository,
                assessmentRepository,
                checkEvidenceRepository,
                workshopOperationsFacade
        );
    }

    @ParameterizedTest
    @EnumSource(OtaProvider.class)
    @DisplayName("Shared voucher alone cannot satisfy the ticket delivery gate for candidate providers")
    void sharedVoucherAlone_failsTicketDeliveryGate(OtaProvider provider) {
        // Given: Provider only provides a single shared booking-level voucher
        ProviderTicketDeliveryProfile profile = new ProviderTicketDeliveryProfile(
                provider,
                TicketDeliveryMode.SHARED_BOOKING_VOUCHER,
                false, // individualQrSupported
                false, // individualScanVerified
                "ev-shared-voucher-doc-001"
        );

        // When
        OtaReadinessCheckOutcome outcome = ticketDeliveryGate.evaluateTicketDeliveryGate(profile);

        // Then: Gate strictly rejects
        assertThat(outcome).isEqualTo(OtaReadinessCheckOutcome.FAILED);
    }

    @ParameterizedTest
    @EnumSource(OtaProvider.class)
    @DisplayName("Individual ticket mode with verified scan proof satisfies the gate")
    void individualTicketWithScanProof_satisfiesTicketDeliveryGate(OtaProvider provider) {
        // Given: Provider implements 1-ticket-per-person with verified QR scan proof
        ProviderTicketDeliveryProfile profile = new ProviderTicketDeliveryProfile(
                provider,
                TicketDeliveryMode.INDIVIDUAL_TICKET_PER_PERSON,
                true,
                true,
                "ev-individual-ticket-scan-proof-002"
        );

        // When
        OtaReadinessCheckOutcome outcome = ticketDeliveryGate.evaluateTicketDeliveryGate(profile);

        // Then: Gate approves
        assertThat(outcome).isEqualTo(OtaReadinessCheckOutcome.VERIFIED);
    }

    @Test
    @DisplayName("Individual ticket mode without scan verification proof is rejected")
    void individualTicketWithoutScanProof_isRejected() {
        ProviderTicketDeliveryProfile profile = new ProviderTicketDeliveryProfile(
                OtaProvider.KLOOK,
                TicketDeliveryMode.INDIVIDUAL_TICKET_PER_PERSON,
                true,
                false, // Scan proof missing
                "ev-unverified-scan"
        );

        assertThat(ticketDeliveryGate.evaluateTicketDeliveryGate(profile))
                .isEqualTo(OtaReadinessCheckOutcome.FAILED);
    }

    @Test
    @DisplayName("Shared voucher outcome prevents mapping from becoming booking-enabled in OtaChannelMappingService")
    void sharedVoucherRejection_preventsBookingEnablement() {
        UUID sessionUuid = UUID.randomUUID();
        OtaChannelMapping mapping = OtaChannelMapping.builder()
                .id(99L)
                .connection(OtaChannelConnection.builder()
                        .providerCode(OtaProvider.KLOOK)
                        .environment(OtaEnvironment.TEST)
                        .build())
                .offerReference("offer-pkg-001")
                .sessionReference(sessionUuid.toString())
                .locationReference("1001")
                .externalProductId("prod-klook-001")
                .externalOptionId("opt-klook-001")
                .status(OtaMappingStatus.SALES_CLOSED)
                .build();

        when(mappingRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(mapping));
        when(workshopOperationsFacade.getPublishedPackageSelection(any(PublishedPackageSelectionQuery.class)))
                .thenReturn(new PublishedPackageSelectionSnapshot(
                        "offer-pkg-001", 500_000L, 1, 10, 1, true, 0, "Basic Ring"));
        when(workshopOperationsFacade.getSaleEligibility(any(SaleEligibilityQuery.class)))
                .thenReturn(new SaleEligibilitySnapshot(
                        sessionUuid, true, "PUBLISHED", 1001L,
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                        10, null));

        when(priceSnapshotRepository.existsByMapping_Id(99L)).thenReturn(true);

        when(assessmentRepository.saveAndFlush(any(OtaReadinessAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Submit checks: all VERIFIED except TICKET_DELIVERY_SCAN which is FAILED due to shared voucher
        List<OtaReadinessCheckSubmission> submissions = new ArrayList<>();
        for (OtaReadinessCheck check : OtaReadinessCheck.values()) {
            OtaReadinessCheckOutcome outcome = (check == OtaReadinessCheck.TICKET_DELIVERY_SCAN)
                    ? OtaReadinessCheckOutcome.FAILED
                    : OtaReadinessCheckOutcome.VERIFIED;
            submissions.add(new OtaReadinessCheckSubmission(check, outcome, "ev-" + check.name()));
        }

        OtaBookingEnableCommand command = new OtaBookingEnableCommand(
                99L,
                "readiness-rec-shared-voucher-fail",
                "Testing shared voucher failure",
                "admin-actor-uuid",
                submissions
        );

        // When
        OtaBookingEnableDecision decision = mappingService.recordBookingEnableDecision(command);

        // Then: Decision is REJECTED and mapping status remains SALES_CLOSED
        assertThat(decision.decision()).isEqualTo(OtaReadinessAssessmentStatus.REJECTED);
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
    }
}
