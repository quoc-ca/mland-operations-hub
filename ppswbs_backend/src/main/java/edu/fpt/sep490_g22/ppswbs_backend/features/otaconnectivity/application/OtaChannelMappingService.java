package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheck;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaSafeReadinessStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OtaChannelMappingService {

    private static final EnumSet<OtaReadinessCheck> REQUIRED_CHECKS = EnumSet.allOf(OtaReadinessCheck.class);

    private final OtaChannelMappingRepository mappingRepository;
    private final OtaChannelPriceSnapshotRepository priceSnapshotRepository;
    private final OtaReadinessAssessmentRepository assessmentRepository;
    private final OtaReadinessCheckEvidenceRepository checkEvidenceRepository;
    private final WorkshopOperationsFacade workshopOperationsFacade;

    @Transactional
    public OtaBookingEnableDecision recordBookingEnableDecision(OtaBookingEnableCommand command) {
        validateCommand(command);
        OtaChannelMapping mapping = mappingRepository.findByIdForUpdate(command.mappingId())
                .orElseThrow(() -> new OtaMappingLifecycleException("OTA channel mapping was not found"));
        if (mapping.getStatus() != OtaMappingStatus.SALES_CLOSED) {
            throw new OtaMappingLifecycleException("Only a sales-closed mapping can be enabled");
        }
        if (!isInitialPerPersonMappingEligible(mapping)) {
            throw new OtaMappingLifecycleException("Mapping is not an eligible initial per-person workshop offer");
        }
        if (!priceSnapshotRepository.existsByMapping_Id(mapping.getId())) {
            throw new OtaMappingLifecycleException("Provider-confirmed commercial terms must be recorded first");
        }

        Map<OtaReadinessCheck, OtaReadinessCheckSubmission> submitted = indexChecks(command.checks());
        boolean allVerified = REQUIRED_CHECKS.stream().allMatch(check ->
                submitted.get(check).outcome() == OtaReadinessCheckOutcome.VERIFIED);
        OtaReadinessAssessmentStatus decision = allVerified
                ? OtaReadinessAssessmentStatus.APPROVED : OtaReadinessAssessmentStatus.REJECTED;
        OtaReadinessAssessment assessment = assessmentRepository.saveAndFlush(OtaReadinessAssessment.record(
                mapping, fingerprint(mapping), command.readinessRecordReference(), decision,
                command.reason(), command.adminAccountReference()));
        List<OtaReadinessCheckEvidence> evidence = REQUIRED_CHECKS.stream()
                .map(check -> submitted.get(check))
                .map(item -> OtaReadinessCheckEvidence.record(assessment, item.check(), item.outcome(),
                        item.evidenceReference()))
                .toList();
        checkEvidenceRepository.saveAllAndFlush(evidence);

        if (decision == OtaReadinessAssessmentStatus.APPROVED) {
        mapping.enableBooking();
            mappingRepository.flush();
        }
        return new OtaBookingEnableDecision(UUID.fromString(assessment.getAssessmentReference()), decision,
                mapping.getStatus(), decision == OtaReadinessAssessmentStatus.APPROVED
                ? OtaSafeReadinessStatus.APPROVED : OtaSafeReadinessStatus.REJECTED,
                assessment.getRecordedAt());
    }

    @Transactional
    public void startPreparationSync(Long mappingId) {
        OtaChannelMapping mapping = lockedMapping(mappingId);
        transition(mapping::startPreparationSync);
    }

    @Transactional
    public void completePreparationSync(Long mappingId) {
        OtaChannelMapping mapping = lockedMapping(mappingId);
        transition(mapping::completePreparationSync);
    }

    @Transactional
    public void closeSales(Long mappingId) {
        OtaChannelMapping mapping = lockedMapping(mappingId);
        transition(mapping::closeSales);
    }

    @Transactional
    public void disable(Long mappingId) {
        OtaChannelMapping mapping = lockedMapping(mappingId);
        mapping.disable();
    }

    @Transactional(readOnly = true)
    public OtaSafeReadinessStatus resolveSafeReadinessStatus(Long mappingId) {
        OtaChannelMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new OtaMappingLifecycleException("OTA channel mapping was not found"));
        return assessmentRepository.findTopByMapping_IdOrderByRecordedAtDescIdDesc(mappingId)
                .filter(assessment -> assessment.getMappingFingerprint().equals(fingerprint(mapping)))
                .map(assessment -> assessment.getDecisionStatus() == OtaReadinessAssessmentStatus.APPROVED
                        ? OtaSafeReadinessStatus.APPROVED : OtaSafeReadinessStatus.REJECTED)
                .orElse(OtaSafeReadinessStatus.INCOMPLETE);
    }

    private OtaChannelMapping lockedMapping(Long mappingId) {
        return mappingRepository.findByIdForUpdate(mappingId)
                .orElseThrow(() -> new OtaMappingLifecycleException("OTA channel mapping was not found"));
    }

    private boolean isInitialPerPersonMappingEligible(OtaChannelMapping mapping) {
        PublishedPackageSelectionSnapshot packageSnapshot = workshopOperationsFacade.getPublishedPackageSelection(
                new PublishedPackageSelectionQuery(mapping.getOfferReference()));
        if (packageSnapshot == null
                || !mapping.getOfferReference().equals(packageSnapshot.packageReference())
                || packageSnapshot.publicPerParticipantPrice() < 0
                || packageSnapshot.minParticipants() < 1
                || packageSnapshot.maxParticipants() < packageSnapshot.minParticipants()
                || packageSnapshot.requiredSessionCount() != 1
                || !packageSnapshot.sameLocationRequired()
                || packageSnapshot.minimumSameDaySlotGap() != 0) {
            return false;
        }
        UUID sessionReference;
        try {
            sessionReference = UUID.fromString(mapping.getSessionReference());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        SaleEligibilitySnapshot session = workshopOperationsFacade.getSaleEligibility(
                new SaleEligibilityQuery(sessionReference));
        return session != null
                && sessionReference.equals(session.sessionReference())
                && "PUBLISHED".equals(session.saleState())
                && session.scheduledStart() != null
                && session.scheduledStart().isAfter(Instant.now())
                && session.locationReference() != null
                && mapping.getLocationReference().equals(session.locationReference().toString());
    }

    private static void transition(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalStateException exception) {
            throw new OtaMappingLifecycleException(exception.getMessage());
        }
    }

    private static void validateCommand(OtaBookingEnableCommand command) {
        if (command == null || command.mappingId() == null || blankOrTooLong(command.reason(), 500)
                || blankOrTooLong(command.readinessRecordReference(), 255)
                || blankOrTooLong(command.adminAccountReference(), 128) || command.checks() == null
                || command.checks().size() != REQUIRED_CHECKS.size()) {
            throw new IllegalArgumentException("A mapping, Admin, reason and all readiness checks are required");
        }
    }

    private static Map<OtaReadinessCheck, OtaReadinessCheckSubmission> indexChecks(
            List<OtaReadinessCheckSubmission> checks) {
        Map<OtaReadinessCheck, OtaReadinessCheckSubmission> indexed = new EnumMap<>(OtaReadinessCheck.class);
        for (OtaReadinessCheckSubmission item : checks) {
            if (item == null || item.check() == null || item.outcome() == null
                    || indexed.putIfAbsent(item.check(), item) != null) {
                throw new IllegalArgumentException("Readiness checks must contain each required check exactly once");
            }
            if (item.outcome() == OtaReadinessCheckOutcome.VERIFIED
                    && blankOrTooLong(item.evidenceReference(), 255)) {
                throw new IllegalArgumentException("Verified readiness checks require a safe evidence reference");
            }
            if (item.evidenceReference() != null && (item.evidenceReference().isBlank()
                    || item.evidenceReference().trim().length() > 255)) {
                throw new IllegalArgumentException("Readiness evidence reference is invalid");
            }
        }
        if (!indexed.keySet().equals(REQUIRED_CHECKS)) {
            throw new IllegalArgumentException("Every required readiness check must be present");
        }
        return indexed;
    }

    private static String fingerprint(OtaChannelMapping mapping) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, mapping.getId().toString());
                write(output, mapping.getConnection().getProviderCode().name());
                write(output, mapping.getConnection().getEnvironment().name());
                write(output, mapping.getOfferReference());
                write(output, mapping.getSessionReference());
                write(output, mapping.getLocationReference());
                write(output, mapping.getExternalProductId());
                write(output, mapping.getExternalOptionId());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to fingerprint OTA mapping", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(valueBytes.length);
        output.write(valueBytes);
    }

    private static boolean blankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.trim().length() > maxLength;
    }
}
