package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopAddOnRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnLifecycleTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnWrite;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOn;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkshopAddOnService {
    private final WorkshopAddOnRepository addOns;
    private final WorkshopPackageRepository packages;
    private final WorkshopLocationRepository locations;
    private final OperationsAuditEventRepository audits;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AddOnResponse> listCustomerSelectable(Long packageId, Long locationId) {
        requirePublishedPackageAndActiveLocation(packageId, locationId);
        return addOns.findByPackageIdAndLocationIdAndStateOrderByDescriptionAsc(
                        packageId, locationId, WorkshopAddOnState.ACTIVE).stream()
                .map(AddOnResponse::from).toList();
    }

    @Transactional
    public AddOnResponse createDraft(Long packageId, Long locationId, AddOnWrite request,
                                     Long actorId, String correlationId) {
        requirePackageAndLocation(packageId, locationId);
        validate(request);
        WorkshopAddOn addOn = addOns.save(WorkshopAddOn.builder()
                .reference(UUID.randomUUID().toString())
                .packageId(packageId).locationId(locationId)
                .description(request.description().trim()).priceVnd(request.priceVnd())
                .state(WorkshopAddOnState.DRAFT).build());
        audit("WORKSHOP_ADDON_CREATED", addOn, actorId, correlationId);
        return AddOnResponse.from(addOn);
    }

    @Transactional
    public AddOnResponse updateProspectively(Long packageId, Long locationId, String reference,
                                             AddOnWrite request, Long actorId, String correlationId) {
        validate(request);
        WorkshopAddOn addOn = requireScopedWithLock(packageId, locationId, reference);
        if (addOn.getState() == WorkshopAddOnState.RETIRED) {
            throw new WorkshopLifecycleException("Retired add-ons cannot be edited");
        }
        addOn.setDescription(request.description().trim());
        addOn.setPriceVnd(request.priceVnd());
        addOn = addOns.save(addOn);
        audit("WORKSHOP_ADDON_UPDATED", addOn, actorId, correlationId);
        return AddOnResponse.from(addOn);
    }

    @Transactional
    public AddOnResponse transition(Long packageId, Long locationId, String reference,
                                    AddOnLifecycleTransitionRequest request, Long actorId, String correlationId) {
        WorkshopAddOn addOn = requireScopedWithLock(packageId, locationId, reference);
        WorkshopAddOnState target = request.targetState();
        boolean allowed = (addOn.getState() == WorkshopAddOnState.DRAFT && target == WorkshopAddOnState.ACTIVE)
                || (addOn.getState() == WorkshopAddOnState.ACTIVE && target == WorkshopAddOnState.RETIRED);
        if (!allowed) throw new WorkshopLifecycleException("Workshop add-on lifecycle transition is not allowed");
        addOn.setState(target);
        addOn = addOns.save(addOn);
        audit("WORKSHOP_ADDON_" + target.name(), addOn, actorId, correlationId);
        return AddOnResponse.from(addOn);
    }

    @Transactional(readOnly = true)
    public edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceSnapshot
    getEligibleSnapshot(String packageReference, Long locationReference, String addOnReference) {
        final Long packageId;
        try { packageId = Long.valueOf(packageReference); }
        catch (RuntimeException invalidReference) { return null; }
        try { UUID.fromString(addOnReference); }
        catch (RuntimeException invalidReference) { return null; }
        if (locationReference == null || packages.findById(packageId)
                .filter(p -> p.getState() == WorkshopPackageState.PUBLISHED).isEmpty()
                || locations.findByIdAndActiveTrue(locationReference).isEmpty()) return null;
        return addOns.findByReferenceAndPackageIdAndLocationIdAndState(
                        addOnReference, packageId, locationReference, WorkshopAddOnState.ACTIVE)
                .map(a -> new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceSnapshot(
                        a.getReference(), a.getDescription(), a.getPriceVnd(), "VND"))
                .orElse(null);
    }

    private void requirePackageAndLocation(Long packageId, Long locationId) {
        if (packages.findById(packageId).isEmpty()) throw new WorkshopNotFoundException("Workshop Package not found");
        if (locations.findById(locationId).isEmpty()) throw new WorkshopNotFoundException("Workshop location not found");
    }

    private void requirePublishedPackageAndActiveLocation(Long packageId, Long locationId) {
        if (packages.findById(packageId).filter(p -> p.getState() == WorkshopPackageState.PUBLISHED).isEmpty()
                || locations.findByIdAndActiveTrue(locationId).isEmpty()) {
            throw new WorkshopNotFoundException("Workshop add-on catalogue not found");
        }
    }

    private WorkshopAddOn requireScopedWithLock(Long packageId, Long locationId, String reference) {
        return addOns.findByReferenceAndScopeWithLock(reference, packageId, locationId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop add-on not found"));
    }

    private static void validate(AddOnWrite request) {
        if (request == null || request.description() == null || request.description().isBlank()
                || request.description().trim().length() > 200 || request.priceVnd() == null || request.priceVnd() <= 0) {
            throw new WorkshopValidationException("A non-blank description and positive whole-VND price are required");
        }
    }

    private void audit(String action, WorkshopAddOn addOn, Long actorId, String correlationId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("addOnReference", addOn.getReference());
        metadata.put("packageId", addOn.getPackageId());
        metadata.put("locationId", addOn.getLocationId());
        metadata.put("state", addOn.getState().name());
        metadata.put("description", addOn.getDescription());
        metadata.put("priceVnd", addOn.getPriceVnd());
        try {
            audits.save(OperationsAuditEvent.builder().actorId(actorId).action(action).outcome("SUCCESS")
                    .correlationId(correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId)
                    .metadata(objectMapper.writeValueAsString(metadata)).build());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize safe Workshop Add-on audit metadata", error);
        }
    }
}
