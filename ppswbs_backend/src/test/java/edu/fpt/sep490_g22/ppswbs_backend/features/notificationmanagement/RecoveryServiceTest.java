package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    @Mock
    private NotificationRequestRepository notificationRequestRepository;

    @InjectMocks
    private RecoveryService recoveryService;

    @Test
    @DisplayName("Should transition interrupted request with SMTP invocation started to UNKNOWN_OUTCOME (SC-004)")
    void shouldRecoverToUnknownOutcomeWhenSmtpStarted() {
        NotificationRequest req1 = new NotificationRequest();
        req1.setId(101L);
        req1.setDeliveryState("PROCESSING");
        req1.setSmtpInvocationStartedAt(Instant.now().minusSeconds(60));

        NotificationRequest req2 = new NotificationRequest();
        req2.setId(102L);
        req2.setDeliveryState("PROCESSING");
        req2.setSmtpInvocationStartedAt(null); // SMTP not yet started

        when(notificationRequestRepository.findByDeliveryStateIn(List.of("PROCESSING")))
                .thenReturn(List.of(req1, req2));

        int count = recoveryService.recoverInterruptedRequests();

        assertThat(count).isEqualTo(2);
        assertThat(req1.getDeliveryState()).isEqualTo("UNKNOWN_OUTCOME");
        assertThat(req2.getDeliveryState()).isEqualTo("PENDING");
        verify(notificationRequestRepository).save(req1);
        verify(notificationRequestRepository).save(req2);
    }
}

