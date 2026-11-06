package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtaChannelCatalogService {
    private static final List<String> BLOCKING_OUTBOUND_STATUSES = List.of("QUEUED", "RETRYABLE");

    private final OtaChannelConnectionRepository connectionRepository;
    private final OtaChannelMappingRepository mappingRepository;
    private final OtaOutboundChangeRepository outboundChangeRepository;
    private final WorkshopOperationsFacade workshopOperationsFacade;

    @Transactional(readOnly = true)
    public List<OtaChannelMapping> listMappings() {
        return mappingRepository.findAllWithConnectionOrderByIdAsc();
    }

    @Transactional
    public OtaChannelMapping upsertDraft(String channelPublicReference, String providerProductReference,
                                          String providerOptionReference, String workshopOfferReference,
                                          String sessionReference) {
        UUID channelReference = parseUuid(channelPublicReference, "Channel reference");
        String product = required(providerProductReference, 200, "Provider product reference is required");
        String option = required(providerOptionReference, 200, "Provider option reference is required");
        String offer = required(workshopOfferReference, 128, "Workshop offer reference is required");
        String session = parseUuid(sessionReference, "Workshop session reference").toString();

        OtaChannelConnection connection = connectionRepository.findByPublicReference(channelReference.toString())
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA channel connection was not found"));
        var byExternal = mappingRepository.findByConnectionIdAndExternalProductIdAndExternalOptionId(
                connection.getId(), product, option);
        var bySession = mappingRepository.findByConnectionIdAndSessionReference(connection.getId(), session);
        if (byExternal.isPresent() || bySession.isPresent()) {
            OtaChannelMapping existing = byExternal.orElseGet(bySession::orElseThrow);
            if (existing.getStatus() == OtaMappingStatus.DRAFT
                    && sameMapping(existing, offer, session, product, option)) {
                return existing;
            }
            throw new OtaMappingLifecycleException("Provider product/option or workshop session is already mapped");
        }

        SaleEligibilitySnapshot sale = validateOfferSession(offer, session);

        OtaChannelMapping mapping = OtaChannelMapping.builder()
                .connection(connection)
                .offerReference(offer)
                .sessionReference(session)
                .locationReference(sale.locationReference().toString())
                .externalProductId(product)
                .externalOptionId(option)
                .status(OtaMappingStatus.DRAFT)
                .build();
        return mappingRepository.saveAndFlush(mapping);
    }

    @Transactional
    public OtaChannelMapping update(String mappingPublicReference, String providerProductReference,
                                   String providerOptionReference, String workshopOfferReference,
                                   String sessionReference, long expectedVersion) {
        UUID publicReference = parseUuid(mappingPublicReference, "Mapping reference");
        String product = required(providerProductReference, 200, "Provider product reference is required");
        String option = required(providerOptionReference, 200, "Provider option reference is required");
        String offer = required(workshopOfferReference, 128, "Workshop offer reference is required");
        String session = parseUuid(sessionReference, "Workshop session reference").toString();
        if (expectedVersion < 1) throw new IllegalArgumentException("Mapping version must be positive");

        OtaChannelMapping mapping = mappingRepository.findByPublicReferenceForUpdate(publicReference.toString())
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA channel mapping was not found"));
        if (mapping.getVersion() == null || mapping.getVersion() != expectedVersion) {
            throw new OtaMappingLifecycleException("Mapping version is stale; reload before updating");
        }
        if (mapping.getStatus() != OtaMappingStatus.DRAFT && mapping.getStatus() != OtaMappingStatus.SALES_CLOSED) {
            throw new OtaMappingLifecycleException("Only a draft or sales-closed mapping can be updated");
        }
        SaleEligibilitySnapshot sale = validateOfferSession(offer, session);
        boolean identityChanged = !mapping.getOfferReference().equals(offer)
                || !mapping.getSessionReference().equals(session)
                || !mapping.getExternalProductId().equals(product)
                || !mapping.getExternalOptionId().equals(option);
        if (identityChanged && outboundChangeRepository.existsByMapping_IdAndStatusIn(
                mapping.getId(), BLOCKING_OUTBOUND_STATUSES)) {
            throw new OtaMappingLifecycleException("Mapping identity cannot change while outbound inventory is queued or retryable");
        }
        if (mappingRepository.existsByConnection_IdAndExternalProductIdAndExternalOptionIdAndIdNot(
                mapping.getConnection().getId(), product, option, mapping.getId())
                || mappingRepository.existsByConnection_IdAndSessionReferenceAndIdNot(
                mapping.getConnection().getId(), session, mapping.getId())) {
            throw new OtaMappingLifecycleException("Provider product/option or workshop session is already mapped");
        }
        mapping.updateDetails(offer, session, sale.locationReference().toString(), product, option);
        return mappingRepository.saveAndFlush(mapping);
    }

    private SaleEligibilitySnapshot validateOfferSession(String offerReference, String sessionReference) {
        PublishedPackageSelectionSnapshot packageSnapshot = workshopOperationsFacade
                .getPublishedPackageSelection(new PublishedPackageSelectionQuery(offerReference));
        UUID sessionUuid = UUID.fromString(sessionReference);
        SaleEligibilitySnapshot sale = workshopOperationsFacade.getSaleEligibility(
                new SaleEligibilityQuery(sessionUuid));
        if (packageSnapshot == null || sale == null
                || !offerReference.equals(packageSnapshot.packageReference())
                || packageSnapshot.publicPerParticipantPrice() < 0
                || packageSnapshot.minParticipants() < 1
                || packageSnapshot.maxParticipants() < packageSnapshot.minParticipants()
                || packageSnapshot.requiredSessionCount() != 1
                || !packageSnapshot.sameLocationRequired()
                || packageSnapshot.minimumSameDaySlotGap() != 0
                || !sessionUuid.equals(sale.sessionReference())
                || !sale.customerSelectable()
                || !"PUBLISHED".equals(sale.saleState())
                || sale.locationReference() == null
                || sale.scheduledStart() == null
                || !sale.scheduledStart().isAfter(Instant.now())) {
            throw new OtaMappingLifecycleException("Only a future published single-session PER_PERSON workshop mapping is supported");
        }
        return sale;
    }

    private static boolean sameMapping(OtaChannelMapping mapping, String offer, String session,
                                       String product, String option) {
        return offer.equals(mapping.getOfferReference()) && session.equals(mapping.getSessionReference())
                && product.equals(mapping.getExternalProductId())
                && option.equals(mapping.getExternalOptionId());
    }

    private static UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(OtaPublicReference.require(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be a UUID");
        }
    }

    private static String required(String value, int max, String message) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
