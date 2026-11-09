package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.OperationalFailureRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateDefinitionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateVersionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.OperationalFailure;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateDefinition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationIntentResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.SubmitNotificationIntentCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationRequestService;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RenditionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRequestServiceTest {

    @Mock
    private TemplateDefinitionRepository templateDefinitionRepository;
    @Mock
    private TemplateVersionRepository templateVersionRepository;
    @Mock
    private NotificationRequestRepository notificationRequestRepository;
    @Mock
    private OperationalFailureRepository operationalFailureRepository;
    @Mock
    private RenditionService renditionService;

    private NotificationRequestService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new NotificationRequestService(
                templateDefinitionRepository,
                templateVersionRepository,
                notificationRequestRepository,
                operationalFailureRepository,
                renditionService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should successfully enqueue valid notification intent (SC-001)")
    void shouldEnqueueValidIntent() {
        TemplateDefinition def = new TemplateDefinition();
        def.setId(1L);
        def.setCode("BOOKING_CONFIRMED");
        def.setLifecycleStatus("ACTIVE");

        TemplateVersion ver = new TemplateVersion();
        ver.setId(10L);
        ver.setVersionNumber(1);
        ver.setSubjectTemplate("Booking {{bookingCode}}");
        ver.setHtmlTemplate("<p>{{customerName}}</p>");
        ver.setPlainTextTemplate("{{customerName}}");

        when(templateDefinitionRepository.findByCode("BOOKING_CONFIRMED")).thenReturn(Optional.of(def));
        when(templateVersionRepository.findByTemplateDefinitionIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(ver));
        when(renditionService.extractPlaceholders(anyString())).thenReturn(Set.of("bookingCode", "customerName"));
        when(notificationRequestRepository.findByBusinessEventReferenceAndTemplateCodeAndRecipientRoleAndRecipientAddressFingerprintAndOriginalRequestIsNull(
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(Optional.empty());

        when(notificationRequestRepository.save(any(NotificationRequest.class))).thenAnswer(inv -> {
            NotificationRequest r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        SubmitNotificationIntentCommand cmd = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-001")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("customer@example.com")
                .recipientName("Customer A")
                .businessDataSnapshot(Map.of("bookingCode", "BK-001", "customerName", "Customer A"))
                .build();

        NotificationIntentResult result = service.submitNotificationIntent(cmd);

        assertThat(result.getStatus()).isEqualTo(NotificationIntentResult.Status.ENQUEUED);
        assertThat(result.getNotificationRequestId()).isEqualTo(100L);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationRequestRepository).save(captor.capture());
        NotificationRequest saved = captor.getValue();
        assertThat(saved.getBusinessEventReference()).isEqualTo("BK-001");
        assertThat(saved.getRecipientAddressMasked()).isEqualTo("c***r@example.com");
        assertThat(saved.getDeliveryState()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should return DUPLICATE_IGNORED if intent is submitted twice (SC-002)")
    void shouldIgnoreDuplicateIntent() {
        TemplateDefinition def = new TemplateDefinition();
        def.setId(1L);
        def.setCode("BOOKING_CONFIRMED");
        def.setLifecycleStatus("ACTIVE");

        TemplateVersion ver = new TemplateVersion();
        ver.setId(10L);

        NotificationRequest existing = new NotificationRequest();
        existing.setId(77L);

        when(templateDefinitionRepository.findByCode("BOOKING_CONFIRMED")).thenReturn(Optional.of(def));
        when(templateVersionRepository.findByTemplateDefinitionIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(ver));
        when(renditionService.extractPlaceholders(any())).thenReturn(Set.of());
        when(notificationRequestRepository.findByBusinessEventReferenceAndTemplateCodeAndRecipientRoleAndRecipientAddressFingerprintAndOriginalRequestIsNull(
                eq("BK-001"), eq("BOOKING_CONFIRMED"), eq("CUSTOMER"), anyString()
        )).thenReturn(Optional.of(existing));

        SubmitNotificationIntentCommand cmd = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-001")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("customer@example.com")
                .businessDataSnapshot(Map.of())
                .build();

        NotificationIntentResult result = service.submitNotificationIntent(cmd);

        assertThat(result.getStatus()).isEqualTo(NotificationIntentResult.Status.DUPLICATE_IGNORED);
        assertThat(result.getNotificationRequestId()).isEqualTo(77L);
        verify(notificationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should record OperationalFailure and NOT throw if template is inactive (SC-001)")
    void shouldRecordFailureWhenTemplateInactive() {
        TemplateDefinition def = new TemplateDefinition();
        def.setId(1L);
        def.setCode("BOOKING_CONFIRMED");
        def.setLifecycleStatus("DRAFT");

        when(templateDefinitionRepository.findByCode("BOOKING_CONFIRMED")).thenReturn(Optional.of(def));
        when(operationalFailureRepository.save(any(OperationalFailure.class))).thenAnswer(inv -> {
            OperationalFailure f = inv.getArgument(0);
            f.setId(500L);
            return f;
        });

        SubmitNotificationIntentCommand cmd = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-002")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("customer@example.com")
                .businessDataSnapshot(Map.of())
                .build();

        NotificationIntentResult result = service.submitNotificationIntent(cmd);

        assertThat(result.getStatus()).isEqualTo(NotificationIntentResult.Status.OPERATIONAL_FAILURE);
        assertThat(result.getFailureCode()).isEqualTo("TEMPLATE_INACTIVE");
        assertThat(result.getOperationalFailureId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Should record OperationalFailure if recipient email is invalid")
    void shouldRecordFailureWhenEmailInvalid() {
        when(operationalFailureRepository.save(any(OperationalFailure.class))).thenAnswer(inv -> {
            OperationalFailure f = inv.getArgument(0);
            f.setId(501L);
            return f;
        });

        SubmitNotificationIntentCommand cmd = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-003")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("invalid-email-no-at")
                .businessDataSnapshot(Map.of())
                .build();

        NotificationIntentResult result = service.submitNotificationIntent(cmd);

        assertThat(result.getStatus()).isEqualTo(NotificationIntentResult.Status.OPERATIONAL_FAILURE);
        assertThat(result.getFailureCode()).isEqualTo("INVALID_RECIPIENT");
    }

    @Test
    @DisplayName("Should mask email safely")
    void testMaskEmail() {
        assertThat(NotificationRequestService.maskEmail("n@example.com")).isEqualTo("n***@example.com");
        assertThat(NotificationRequestService.maskEmail("nguyenvana@gmail.com")).isEqualTo("n***a@gmail.com");
        assertThat(NotificationRequestService.maskEmail("ab@yahoo.com")).isEqualTo("a***b@yahoo.com");
        assertThat(NotificationRequestService.maskEmail("invalid")).isEqualTo("***");
    }
}

