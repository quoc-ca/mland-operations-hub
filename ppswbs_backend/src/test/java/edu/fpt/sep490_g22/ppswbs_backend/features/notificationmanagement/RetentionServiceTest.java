package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.RenditionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TombstoneRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Tombstone;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RetentionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock
    private NotificationRequestRepository notificationRequestRepository;
    @Mock
    private RenditionRepository renditionRepository;
    @Mock
    private TombstoneRepository tombstoneRepository;

    @InjectMocks
    private RetentionService retentionService;

    @Test
    @DisplayName("Should scrub requests older than 90 days and create Tombstones (SC-007 / FR-025)")
    void shouldScrubOldRequests() {
        NotificationRequest oldReq = new NotificationRequest();
        oldReq.setId(99L);
        oldReq.setBusinessEventReference("BK-OLD-1");
        oldReq.setTemplateCode("BOOKING_CONFIRMED");
        oldReq.setDeliveryState("SENT");
        oldReq.setAttemptCount(1);
        oldReq.setCreatedAt(Instant.now().minus(95, ChronoUnit.DAYS));
        oldReq.setTerminalAt(Instant.now().minus(92, ChronoUnit.DAYS));
        oldReq.setRecipientSnapshot("{\"email\":\"secret@example.com\"}");
        oldReq.setBusinessDataSnapshot("{\"key\":\"secret_data\"}");

        Rendition rendition = new Rendition();
        rendition.setId(10L);

        when(notificationRequestRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(notificationRequestRepository.findByTerminalAtBeforeAndDeliveryStateIn(any(Instant.class), anyList()))
                .thenReturn(List.of(oldReq));
        when(tombstoneRepository.findByOriginalRequestId(99L)).thenReturn(Optional.empty());
        when(renditionRepository.findByNotificationRequestId(99L)).thenReturn(Optional.of(rendition));

        int scrubbed = retentionService.sweepExpiredData(90);

        assertThat(scrubbed).isEqualTo(1);
        assertThat(oldReq.getRecipientSnapshot()).isEqualTo("[SCRUBBED]");
        assertThat(oldReq.getBusinessDataSnapshot()).isEqualTo("[SCRUBBED]");

        verify(renditionRepository).delete(rendition);

        ArgumentCaptor<Tombstone> captor = ArgumentCaptor.forClass(Tombstone.class);
        verify(tombstoneRepository).save(captor.capture());
        Tombstone tombstone = captor.getValue();
        assertThat(tombstone.getOriginalRequestId()).isEqualTo(99L);
        assertThat(tombstone.getBusinessEventReference()).isEqualTo("BK-OLD-1");
        assertThat(tombstone.getTerminalState()).isEqualTo("SENT");
    }
}

