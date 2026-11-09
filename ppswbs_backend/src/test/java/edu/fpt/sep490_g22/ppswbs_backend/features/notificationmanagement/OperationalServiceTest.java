package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.CancelRequestCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.CancelResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.RedeliverRequestCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.RedeliverResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.ResendRequestCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.ResendResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.DeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.OperationalAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.RenditionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.OperationalAuditRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalServiceTest {

    @Mock
    private NotificationRequestRepository notificationRequestRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private OperationalAuditRepository operationalAuditRepository;
    @Mock
    private RenditionRepository renditionRepository;

    private OperationalService operationalService;

    @BeforeEach
    void setUp() {
        operationalService = new OperationalService(
                notificationRequestRepository,
                deliveryAttemptRepository,
                operationalAuditRepository,
                renditionRepository
        );
    }

    @Test
    @DisplayName("Should successfully resend a SENT request and copy rendition")
    void shouldResendSentRequest() {
        NotificationRequest original = new NotificationRequest();
        original.setId(10L);
        original.setDeliveryState("SENT");
        original.setBusinessEventReference("BK-100");
        original.setTemplateCode("BOOKING_CONFIRMED");

        Rendition origRendition = new Rendition();
        origRendition.setRenderedSubject("Original Subject");
        origRendition.setRenderedHtml("<p>Original HTML</p>");
        origRendition.setRenderedPlainText("Original Text");

        when(notificationRequestRepository.findById(10L)).thenReturn(Optional.of(original));
        when(renditionRepository.findByNotificationRequestId(10L)).thenReturn(Optional.of(origRendition));
        when(notificationRequestRepository.save(any(NotificationRequest.class))).thenAnswer(inv -> {
            NotificationRequest r = inv.getArgument(0);
            r.setId(11L);
            return r;
        });

        CurrentAccountActor actor = new CurrentAccountActor(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        ResendRequestCommand command = ResendRequestCommand.builder()
                .reason("Customer requested new email")
                .build();

        ResendResponse response = operationalService.resendRequest(10L, actor, command);

        assertThat(response.newRequestId()).isEqualTo(11L);
        assertThat(response.originalRequestId()).isEqualTo(10L);
        assertThat(response.deliveryState()).isEqualTo("PENDING");

        verify(renditionRepository).save(any(Rendition.class));
        verify(operationalAuditRepository).save(any(OperationalAuditRecord.class));
    }

    @Test
    @DisplayName("Should forbid Manager from resending UNKNOWN_OUTCOME request (FR-016)")
    void shouldForbidManagerResendUnknownOutcome() {
        NotificationRequest original = new NotificationRequest();
        original.setId(20L);
        original.setDeliveryState("UNKNOWN_OUTCOME");

        when(notificationRequestRepository.findById(20L)).thenReturn(Optional.of(original));

        CurrentAccountActor actor = new CurrentAccountActor(2L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        ResendRequestCommand command = ResendRequestCommand.builder()
                .reason("Manager wants to resend")
                .duplicateRiskAccepted(true)
                .build();

        assertThatThrownBy(() -> operationalService.resendRequest(20L, actor, command))
                .isInstanceOf(NotificationManagementException.class)
                .hasMessageContaining("Chỉ Quản trị viên (ADMIN) mới có quyền");
    }

    @Test
    @DisplayName("Should require duplicateRiskAccepted when Admin resends UNKNOWN_OUTCOME request (FR-016)")
    void shouldRequireDuplicateRiskAcceptedForAdmin() {
        NotificationRequest original = new NotificationRequest();
        original.setId(20L);
        original.setDeliveryState("UNKNOWN_OUTCOME");

        when(notificationRequestRepository.findById(20L)).thenReturn(Optional.of(original));

        CurrentAccountActor actor = new CurrentAccountActor(3L, ActorRole.ADMIN, ActorStatus.ACTIVE, true, List.of());
        ResendRequestCommand commandWithoutRisk = ResendRequestCommand.builder()
                .reason("Admin wants to resend")
                .duplicateRiskAccepted(false)
                .build();

        assertThatThrownBy(() -> operationalService.resendRequest(20L, actor, commandWithoutRisk))
                .isInstanceOf(NotificationManagementException.class)
                .hasMessageContaining("duplicateRiskAccepted = true");
    }

    @Test
    @DisplayName("Should successfully cancel PENDING or RETRY_SCHEDULED request (FR-017)")
    void shouldCancelPendingRequest() {
        NotificationRequest request = new NotificationRequest();
        request.setId(30L);
        request.setDeliveryState("PENDING");

        when(notificationRequestRepository.findById(30L)).thenReturn(Optional.of(request));

        CurrentAccountActor actor = new CurrentAccountActor(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        CancelRequestCommand command = CancelRequestCommand.builder().reason("Customer cancelled order").build();

        CancelResponse response = operationalService.cancelRequest(30L, actor, command);

        assertThat(response.deliveryState()).isEqualTo("CANCELLED");
        assertThat(request.getDeliveryState()).isEqualTo("CANCELLED");
        assertThat(request.getTerminalAt()).isNotNull();

        verify(operationalAuditRepository).save(any(OperationalAuditRecord.class));
    }

    @Test
    @DisplayName("Should reject cancellation if request is currently PROCESSING (FR-017)")
    void shouldRejectCancelIfProcessing() {
        NotificationRequest request = new NotificationRequest();
        request.setId(40L);
        request.setDeliveryState("PROCESSING");

        when(notificationRequestRepository.findById(40L)).thenReturn(Optional.of(request));

        CurrentAccountActor actor = new CurrentAccountActor(1L, ActorRole.ADMIN, ActorStatus.ACTIVE, true, List.of());
        CancelRequestCommand command = CancelRequestCommand.builder().reason("Cancel request").build();

        assertThatThrownBy(() -> operationalService.cancelRequest(40L, actor, command))
                .isInstanceOf(NotificationManagementException.class)
                .hasMessageContaining("đang trong tiến trình xử lý");
    }

    @Test
    @DisplayName("Should successfully redeliver with refreshed credential (FR-019)")
    void shouldRedeliverWithRefreshedCredential() {
        NotificationRequest original = new NotificationRequest();
        original.setId(50L);
        original.setDeliveryState("SENT");
        original.setBusinessEventReference("BK-500");
        original.setTemplateCode("BOOKING_CONFIRMED");
        original.setRecipientRole("CUSTOMER");
        original.setRecipientAddressFingerprint("fp_123");
        original.setRecipientAddressMasked("c***r@example.com");
        original.setRecipientSnapshot("{\"email\":\"customer@example.com\"}");
        original.setBusinessDataSnapshot("{\"bookingCode\":\"BK-500\"}");
        original.setArtifactReference("ART-OLD");
        original.setCredentialReference("CRED-OLD");

        when(notificationRequestRepository.findById(50L)).thenReturn(Optional.of(original));
        when(notificationRequestRepository.save(any(NotificationRequest.class))).thenAnswer(inv -> {
            NotificationRequest r = inv.getArgument(0);
            r.setId(51L);
            return r;
        });

        CurrentAccountActor actor = new CurrentAccountActor(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        RedeliverRequestCommand command = RedeliverRequestCommand.builder()
                .reason("Khách hàng yêu cầu link vé mới sau khi vé cũ hết hạn 24h")
                .build();

        RedeliverResponse response = operationalService.redeliverWithRefreshedCredential(50L, actor, command);

        assertThat(response.newRequestId()).isEqualTo(51L);
        assertThat(response.originalRequestId()).isEqualTo(50L);
        assertThat(response.deliveryState()).isEqualTo("PENDING");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationRequestRepository).save(captor.capture());
        NotificationRequest created = captor.getValue();
        assertThat(created.getOriginalRequest()).isSameAs(original);
        assertThat(created.getBusinessEventReference()).isEqualTo("BK-500");
        assertThat(created.getBusinessDataSnapshot()).isEqualTo("{\"bookingCode\":\"BK-500\"}");
        assertThat(created.getDeliveryState()).isEqualTo("PENDING");

        ArgumentCaptor<OperationalAuditRecord> auditCaptor = ArgumentCaptor.forClass(OperationalAuditRecord.class);
        verify(operationalAuditRepository).save(auditCaptor.capture());
        OperationalAuditRecord audit = auditCaptor.getValue();
        assertThat(audit.getActionType()).isEqualTo("REDELIVER_REFRESHED_CREDENTIAL");
        assertThat(audit.getNotificationRequest()).isSameAs(original);
    }
}

