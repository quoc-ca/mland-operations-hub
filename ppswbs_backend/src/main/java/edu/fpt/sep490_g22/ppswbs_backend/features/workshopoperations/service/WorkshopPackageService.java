package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageLifecycleTargetState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
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
public class WorkshopPackageService {

    private final WorkshopPackageRepository packageRepository;
    private final OperationsAuditEventRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PackageResponse createDraft(PackageRequest request, Long actorId, String correlationReference) {
        WorkshopPackage created = packageRepository.save(toEntity(request, WorkshopPackageState.DRAFT));
        recordAudit("WORKSHOP_PACKAGE_CREATED", actorId, created.getId(), created.getState(), correlationReference);
        return toResponse(created);
    }

    @Transactional(readOnly = true)
    public List<PackageResponse> listPublished() {
        return packageRepository.findByStateOrderByIdAsc(WorkshopPackageState.PUBLISHED).stream()
                .map(WorkshopPackageService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PackageResponse getPublished(Long packageId) {
        WorkshopPackage workshopPackage = packageRepository.findById(packageId)
                .filter(candidate -> candidate.getState() == WorkshopPackageState.PUBLISHED)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Package not found"));
        return toResponse(workshopPackage);
    }

    @Transactional
    public PackageResponse updateProspectively(Long packageId, PackageRequest request, Long actorId,
                                               String correlationReference) {
        WorkshopPackage workshopPackage = packageRepository.findByIdWithLock(packageId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Package not found"));
        applyRequest(workshopPackage, request);
        WorkshopPackage saved = packageRepository.save(workshopPackage);
        recordAudit("WORKSHOP_PACKAGE_UPDATED", actorId, saved.getId(), saved.getState(), correlationReference);
        return toResponse(saved);
    }

    @Transactional
    public PackageResponse transition(Long packageId, PackageLifecycleTargetState targetState, Long actorId,
                                      String correlationReference) {
        WorkshopPackage workshopPackage = packageRepository.findByIdWithLock(packageId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Package not found"));
        WorkshopPackageState target = WorkshopPackageState.valueOf(targetState.name());
        boolean validTransition = (workshopPackage.getState() == WorkshopPackageState.DRAFT
                && target == WorkshopPackageState.PUBLISHED)
                || (workshopPackage.getState() == WorkshopPackageState.PUBLISHED
                && target == WorkshopPackageState.RETIRED);
        if (!validTransition) {
            throw new WorkshopLifecycleException("Workshop Package lifecycle transition is not allowed");
        }

        workshopPackage.setState(target);
        WorkshopPackage saved = packageRepository.save(workshopPackage);
        recordAudit("WORKSHOP_PACKAGE_" + target.name(), actorId, saved.getId(), saved.getState(), correlationReference);
        return toResponse(saved);
    }

    private void recordAudit(String action, Long actorId, Long packageId, WorkshopPackageState state,
                             String correlationReference) {
        String correlationId = correlationReference == null || correlationReference.isBlank()
                ? UUID.randomUUID().toString()
                : correlationReference;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageId", packageId);
        metadata.put("state", state.name());
        try {
            auditRepository.save(OperationsAuditEvent.builder()
                    .actorId(actorId)
                    .action(action)
                    .outcome("SUCCESS")
                    .correlationId(correlationId)
                    .metadata(objectMapper.writeValueAsString(metadata))
                    .build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize safe Workshop Package audit metadata", exception);
        }
    }

    private static WorkshopPackage toEntity(PackageRequest request, WorkshopPackageState state) {
        WorkshopPackage entity = new WorkshopPackage();
        applyRequest(entity, request);
        entity.setState(state);
        return entity;
    }

    private static void applyRequest(WorkshopPackage entity, PackageRequest request) {
        if (request.getMinParticipants() != null && request.getMaxParticipants() != null
                && request.getMaxParticipants() < request.getMinParticipants()) {
            throw new WorkshopValidationException("maxParticipants must be greater than or equal to minParticipants");
        }
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setPublicPrice(request.getPublicPrice());
        entity.setMinParticipants(request.getMinParticipants());
        entity.setMaxParticipants(request.getMaxParticipants());
        entity.setRequiredSessionCount(request.getRequiredSessionCount());
        entity.setSameLocationRequired(Boolean.TRUE.equals(request.getSameLocationRequired()));
        entity.setMinimumSameDaySlotGap(request.getMinimumSameDaySlotGap());
    }

    private static PackageResponse toResponse(WorkshopPackage entity) {
        return PackageResponse.builder()
                .packageId(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .publicPrice(entity.getPublicPrice())
                .minParticipants(entity.getMinParticipants())
                .maxParticipants(entity.getMaxParticipants())
                .requiredSessionCount(entity.getRequiredSessionCount())
                .sameLocationRequired(entity.isSameLocationRequired())
                .minimumSameDaySlotGap(entity.getMinimumSameDaySlotGap())
                .state(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.WorkshopPackageState
                        .valueOf(entity.getState().name()))
                .build();
    }
}
