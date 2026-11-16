package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.ManagerAlertRecipientSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.ManagerAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.1 T130 / FR-040a: one alert path, active Managers only, and the no-Manager fallback. */
class ManagerAlertServiceTest {

    private ManagerAlertRecipientSource recipients;
    private GroupNotificationService notifications;
    private BookingAuditEvidenceRepository audit;
    private ManagerAlertService service;
    private final BookingGroup group = BookingGroup.builder().id(1L).groupCode("GRP-ALERT0001").build();

    @BeforeEach
    void setUp() {
        recipients = mock(ManagerAlertRecipientSource.class);
        notifications = mock(GroupNotificationService.class);
        audit = mock(BookingAuditEvidenceRepository.class);
        service = new ManagerAlertService(recipients, notifications, audit);
    }

    @Test
    void everyActiveManagerReceivesOneAlertAndNothingIsAudited() {
        when(recipients.activeManagerEmails()).thenReturn(List.of("m1@mland.test", " M1@mland.test", "m2@mland.test"));

        int alerted = service.alert(group, GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT, "item-1",
                "subject", "body", "corr");

        assertThat(alerted).isEqualTo(2);
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        verify(notifications, times(2)).queue(eq(group), isNull(), eq(GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT),
                to.capture(), eq("subject"), eq("body"), eq(List.of()), isNull(), eq("corr"));
        assertThat(to.getAllValues()).containsExactlyInAnyOrder("m1@mland.test", "m2@mland.test");
        verifyNoInteractions(audit);
    }

    @Test
    void withNoActiveManagerNoEmailIsSentButAnAuditEntryWithoutAddressesIsWritten() {
        when(recipients.activeManagerEmails()).thenReturn(List.of());

        int alerted = service.alert(group, GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT, "item-1",
                "subject", "body", "corr");

        assertThat(alerted).isZero();
        verifyNoInteractions(notifications);
        ArgumentCaptor<BookingAuditEvidence> saved = ArgumentCaptor.forClass(BookingAuditEvidence.class);
        verify(audit).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo(ManagerAlertService.AUDIT_NO_RECIPIENT);
        assertThat(saved.getValue().getBookingCode()).isEqualTo("GRP-ALERT0001");
        assertThat(saved.getValue().getActionReason()).isEqualTo(GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT + ":item-1");
    }
}
