package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.ActivateVersionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.CreateTemplateVersionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.TemplateDetailResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateDefinitionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateVersionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateDefinition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RenditionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.TemplateManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateManagementServiceTest {

    @Mock
    private TemplateDefinitionRepository templateDefinitionRepository;
    @Mock
    private TemplateVersionRepository templateVersionRepository;
    @Mock
    private RenditionService renditionService;

    private TemplateManagementService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TemplateManagementService(
                templateDefinitionRepository,
                templateVersionRepository,
                renditionService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should create draft version with allowed placeholders")
    void shouldCreateDraftVersion() {
        TemplateDefinition def = new TemplateDefinition();
        def.setId(1L);
        def.setCode("BOOKING_CONFIRMED");
        def.setLifecycleStatus("ACTIVE");
        def.setAllowedPlaceholders("[\"customerName\", \"bookingCode\"]");

        when(templateDefinitionRepository.findByCode("BOOKING_CONFIRMED")).thenReturn(Optional.of(def));
        when(renditionService.extractPlaceholders(anyString())).thenReturn(Set.of("customerName", "bookingCode"));
        when(templateVersionRepository.findMaxVersionNumber(1L)).thenReturn(2);
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTemplateVersionRequest req = CreateTemplateVersionRequest.builder()
                .subjectTemplate("Booking {{bookingCode}}")
                .htmlTemplate("<p>{{customerName}}</p>")
                .plainTextTemplate("{{customerName}}")
                .build();

        TemplateDetailResponseDto.TemplateVersionDto result = service.createDraft("BOOKING_CONFIRMED", req, "admin_user");

        assertThat(result.versionNumber()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.createdBy()).isEqualTo("admin_user");
    }

    @Test
    @DisplayName("Should reject draft version if unauthorized placeholders are used")
    void shouldRejectUnauthorizedPlaceholder() {
        TemplateDefinition def = new TemplateDefinition();
        def.setId(1L);
        def.setCode("BOOKING_CONFIRMED");
        def.setAllowedPlaceholders("[\"customerName\"]");

        when(templateDefinitionRepository.findByCode("BOOKING_CONFIRMED")).thenReturn(Optional.of(def));
        when(renditionService.extractPlaceholders(anyString())).thenReturn(Set.of("customerName", "unauthorizedField"));

        CreateTemplateVersionRequest req = CreateTemplateVersionRequest.builder()
                .subjectTemplate("Booking {{unauthorizedField}}")
                .htmlTemplate("<p>{{customerName}}</p>")
                .plainTextTemplate("{{customerName}}")
                .build();

        assertThatThrownBy(() -> service.createDraft("BOOKING_CONFIRMED", req, "admin_user"))
                .isInstanceOf(NotificationManagementException.class)
                .hasMessageContaining("unauthorized placeholder");
    }

    @Test
    @DisplayName("Should activate draft and deactivate previous active version (SC-005)")
    void shouldActivateDraftAndDeactivatePrevious() {
        TemplateDefinition def = new TemplateDefinition();
        def.setId(1L);
        def.setCode("BOOKING_CONFIRMED");
        def.setLifecycleStatus("ACTIVE");

        TemplateVersion currentActive = new TemplateVersion();
        currentActive.setId(10L);
        currentActive.setVersionNumber(1);
        currentActive.setStatus("ACTIVE");

        TemplateVersion targetDraft = new TemplateVersion();
        targetDraft.setId(11L);
        targetDraft.setVersionNumber(2);
        targetDraft.setStatus("DRAFT");

        when(templateDefinitionRepository.findByCode("BOOKING_CONFIRMED")).thenReturn(Optional.of(def));
        when(templateVersionRepository.findByTemplateDefinitionIdAndVersionNumber(1L, 2)).thenReturn(Optional.of(targetDraft));
        when(templateVersionRepository.findByTemplateDefinitionIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(currentActive));

        ActivateVersionResponse response = service.activateVersion("BOOKING_CONFIRMED", 2, "mgr_01");

        assertThat(response.activatedVersionNumber()).isEqualTo(2);
        assertThat(response.deactivatedVersionNumber()).isEqualTo(1);
        assertThat(response.activatedBy()).isEqualTo("mgr_01");

        assertThat(currentActive.getStatus()).isEqualTo("DEACTIVATED");
        assertThat(targetDraft.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should reject draft creation for EXTERNAL template (FR-002)")
    void shouldRejectDraftCreationForExternalTemplate() {
        TemplateDefinition def = new TemplateDefinition();
        def.setId(3L);
        def.setCode("AUTH_EMAIL_VERIFICATION");
        def.setLifecycleStatus("EXTERNAL");

        when(templateDefinitionRepository.findByCode("AUTH_EMAIL_VERIFICATION")).thenReturn(Optional.of(def));

        CreateTemplateVersionRequest req = CreateTemplateVersionRequest.builder()
                .subjectTemplate("Verify email")
                .htmlTemplate("<p>Verify</p>")
                .plainTextTemplate("Verify")
                .build();

        assertThatThrownBy(() -> service.createDraft("AUTH_EMAIL_VERIFICATION", req, "admin_user"))
                .isInstanceOf(NotificationManagementException.class)
                .hasMessageContaining("External templates are managed by an external provider");

        verify(templateVersionRepository, never()).save(any());
    }
}

