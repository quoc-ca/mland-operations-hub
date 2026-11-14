package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateDefinitionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateVersionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateDefinition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateManagementService {

    private final TemplateDefinitionRepository templateDefinitionRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final RenditionService renditionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TemplateResponseDto> listTemplates() {
        List<TemplateDefinition> definitions = templateDefinitionRepository.findAllByOrderByCreatedAtDesc();
        List<TemplateResponseDto> result = new ArrayList<>();

        for (TemplateDefinition def : definitions) {
            Optional<TemplateVersion> activeOpt = templateVersionRepository
                    .findByTemplateDefinitionIdAndStatus(def.getId(), "ACTIVE");

            TemplateResponseDto.ActiveVersionDto activeDto = activeOpt.map(v -> TemplateResponseDto.ActiveVersionDto.builder()
                    .versionNumber(v.getVersionNumber())
                    .subjectTemplate(v.getSubjectTemplate())
                    .activatedAt(v.getActivatedAt())
                    .activatedBy(v.getActivatedBy())
                    .build()).orElse(null);

            // Latest draft if any
            Integer latestDraft = templateVersionRepository
                    .findByTemplateDefinitionIdOrderByVersionNumberDesc(def.getId()).stream()
                    .filter(v -> "DRAFT".equalsIgnoreCase(v.getStatus()))
                    .map(TemplateVersion::getVersionNumber)
                    .findFirst()
                    .orElse(null);

            result.add(TemplateResponseDto.builder()
                    .code(def.getCode())
                    .name(def.getName())
                    .description(def.getDescription())
                    .owningModule(def.getOwningModule())
                    .lifecycleStatus(def.getLifecycleStatus())
                    .allowedPlaceholders(parseAllowedPlaceholders(def.getAllowedPlaceholders()))
                    .activeVersion(activeDto)
                    .latestDraftVersionNumber(latestDraft)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TemplateDetailResponseDto getTemplateDetails(String code) {
        TemplateDefinition def = findDefinitionOrThrow(code);
        List<TemplateVersion> versions = templateVersionRepository.findByTemplateDefinitionIdOrderByVersionNumberDesc(def.getId());

        List<TemplateDetailResponseDto.TemplateVersionDto> versionDtos = versions.stream()
                .map(this::toVersionDto)
                .toList();

        return TemplateDetailResponseDto.builder()
                .code(def.getCode())
                .name(def.getName())
                .description(def.getDescription())
                .owningModule(def.getOwningModule())
                .lifecycleStatus(def.getLifecycleStatus())
                .allowedPlaceholders(parseAllowedPlaceholders(def.getAllowedPlaceholders()))
                .versions(versionDtos)
                .build();
    }

    @Transactional
    public TemplateDetailResponseDto.TemplateVersionDto createDraft(String code, CreateTemplateVersionRequest request, String createdBy) {
        TemplateDefinition def = findDefinitionOrThrow(code);
        if ("EXTERNAL".equalsIgnoreCase(def.getLifecycleStatus())) {
            throw NotificationManagementException.badRequest("CANNOT_EDIT_EXTERNAL_TEMPLATE",
                    "External templates are managed by an external provider and cannot have version drafts.");
        }

        validatePlaceholders(def, request);

        int maxVersion = templateVersionRepository.findMaxVersionNumber(def.getId());
        int nextVersionNumber = maxVersion + 1;

        TemplateVersion version = new TemplateVersion();
        version.setTemplateDefinition(def);
        version.setVersionNumber(nextVersionNumber);
        version.setSubjectTemplate(request.subjectTemplate());
        version.setHtmlTemplate(request.htmlTemplate());
        version.setPlainTextTemplate(request.plainTextTemplate());
        version.setStatus("DRAFT");
        version.setCreatedBy(createdBy != null ? createdBy : "system");

        TemplateVersion saved = templateVersionRepository.save(version);
        log.info("Created draft version {} for template {}", nextVersionNumber, code);
        return toVersionDto(saved);
    }

    @Transactional
    public TemplateDetailResponseDto.TemplateVersionDto updateDraft(String code, int versionNumber, CreateTemplateVersionRequest request) {
        TemplateDefinition def = findDefinitionOrThrow(code);
        TemplateVersion version = templateVersionRepository
                .findByTemplateDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> NotificationManagementException.notFound("VERSION_NOT_FOUND",
                        "Template version " + versionNumber + " not found for template " + code));

        if (!"DRAFT".equalsIgnoreCase(version.getStatus())) {
            throw NotificationManagementException.conflict("CANNOT_EDIT_NON_DRAFT_VERSION",
                    "Only DRAFT versions can be modified. Version " + versionNumber + " status is " + version.getStatus());
        }

        validatePlaceholders(def, request);

        version.setSubjectTemplate(request.subjectTemplate());
        version.setHtmlTemplate(request.htmlTemplate());
        version.setPlainTextTemplate(request.plainTextTemplate());

        TemplateVersion saved = templateVersionRepository.save(version);
        log.info("Updated draft version {} for template {}", versionNumber, code);
        return toVersionDto(saved);
    }

    @Transactional
    public ActivateVersionResponse activateVersion(String code, int versionNumber, String activatedBy) {
        TemplateDefinition def = findDefinitionOrThrow(code);
        TemplateVersion target = templateVersionRepository
                .findByTemplateDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> NotificationManagementException.notFound("VERSION_NOT_FOUND",
                        "Template version " + versionNumber + " not found for template " + code));

        if ("ACTIVE".equalsIgnoreCase(target.getStatus())) {
            return ActivateVersionResponse.builder()
                    .activatedVersionNumber(versionNumber)
                    .deactivatedVersionNumber(null)
                    .activatedAt(target.getActivatedAt())
                    .activatedBy(target.getActivatedBy())
                    .build();
        }

        if (!"DRAFT".equalsIgnoreCase(target.getStatus()) && !"DEACTIVATED".equalsIgnoreCase(target.getStatus())) {
            throw NotificationManagementException.badRequest("INVALID_VERSION_STATUS",
                    "Cannot activate version with status: " + target.getStatus());
        }

        // Deactivate previous active version
        Integer previousActiveNumber = null;
        Optional<TemplateVersion> currentActive = templateVersionRepository
                .findByTemplateDefinitionIdAndStatus(def.getId(), "ACTIVE");
        if (currentActive.isPresent()) {
            TemplateVersion prev = currentActive.get();
            prev.setStatus("DEACTIVATED");
            templateVersionRepository.save(prev);
            previousActiveNumber = prev.getVersionNumber();
            log.info("Deactivated previous active version {} for template {}", previousActiveNumber, code);
        }

        Instant now = Instant.now();
        target.setStatus("ACTIVE");
        target.setActivatedBy(activatedBy != null ? activatedBy : "system");
        target.setActivatedAt(now);
        templateVersionRepository.save(target);

        if (!"ACTIVE".equalsIgnoreCase(def.getLifecycleStatus())) {
            def.setLifecycleStatus("ACTIVE");
            templateDefinitionRepository.save(def);
        }

        log.info("Activated version {} for template {}", versionNumber, code);
        return ActivateVersionResponse.builder()
                .activatedVersionNumber(versionNumber)
                .deactivatedVersionNumber(previousActiveNumber)
                .activatedAt(now)
                .activatedBy(target.getActivatedBy())
                .build();
    }

    @Transactional(readOnly = true)
    public TemplatePreviewResponse renderPreview(String code, TemplatePreviewRequest request) {
        TemplateDefinition def = findDefinitionOrThrow(code);

        TemplateVersion version;
        if (request != null && request.versionNumber() != null) {
            version = templateVersionRepository
                    .findByTemplateDefinitionIdAndVersionNumber(def.getId(), request.versionNumber())
                    .orElseThrow(() -> NotificationManagementException.notFound("VERSION_NOT_FOUND",
                            "Template version " + request.versionNumber() + " not found for template " + code));
        } else {
            version = templateVersionRepository
                    .findByTemplateDefinitionIdAndStatus(def.getId(), "ACTIVE")
                    .orElseThrow(() -> NotificationManagementException.badRequest("NO_ACTIVE_VERSION",
                            "No active version found for template " + code));
        }

        Map<String, Object> sampleData = request != null && request.sampleData() != null
                ? request.sampleData()
                : Collections.emptyMap();

        RenditionService.RenderedOutput rendered = renditionService.renderSample(
                version.getSubjectTemplate(),
                version.getHtmlTemplate(),
                version.getPlainTextTemplate(),
                sampleData
        );

        return TemplatePreviewResponse.builder()
                .renderedSubject(rendered.subject())
                .renderedHtml(rendered.html())
                .renderedPlainText(rendered.plainText())
                .build();
    }

    private TemplateDefinition findDefinitionOrThrow(String code) {
        return templateDefinitionRepository.findByCode(code)
                .orElseThrow(() -> NotificationManagementException.notFound("TEMPLATE_NOT_FOUND",
                        "Template definition not found for code: " + code));
    }

    private void validatePlaceholders(TemplateDefinition def, CreateTemplateVersionRequest request) {
        List<String> allowed = parseAllowedPlaceholders(def.getAllowedPlaceholders());
        Set<String> allowedSet = new HashSet<>(allowed);

        Set<String> used = new HashSet<>();
        used.addAll(renditionService.extractPlaceholders(request.subjectTemplate()));
        used.addAll(renditionService.extractPlaceholders(request.htmlTemplate()));
        used.addAll(renditionService.extractPlaceholders(request.plainTextTemplate()));

        List<String> unauthorized = new ArrayList<>();
        for (String placeholder : used) {
            if (!allowedSet.contains(placeholder)) {
                unauthorized.add(placeholder);
            }
        }

        if (!unauthorized.isEmpty()) {
            throw NotificationManagementException.badRequest("UNAUTHORIZED_PLACEHOLDER",
                    "Template uses unauthorized placeholder(s): " + String.join(", ", unauthorized) +
                    ". Allowed placeholders are: " + String.join(", ", allowed));
        }
    }

    private List<String> parseAllowedPlaceholders(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse allowed_placeholders JSON: {}", json, e);
            return Collections.emptyList();
        }
    }

    private TemplateDetailResponseDto.TemplateVersionDto toVersionDto(TemplateVersion v) {
        return TemplateDetailResponseDto.TemplateVersionDto.builder()
                .versionNumber(v.getVersionNumber())
                .status(v.getStatus())
                .subjectTemplate(v.getSubjectTemplate())
                .htmlTemplate(v.getHtmlTemplate())
                .plainTextTemplate(v.getPlainTextTemplate())
                .createdAt(v.getCreatedAt())
                .createdBy(v.getCreatedBy())
                .activatedAt(v.getActivatedAt())
                .activatedBy(v.getActivatedBy())
                .build();
    }
}

