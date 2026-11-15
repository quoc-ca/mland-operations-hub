package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.1 T133 / FR-039: 3 retries 1 minute apart, one Manager alert, then every 5 minutes; every retry idempotent. */
class GroupExpiryReleaseRetryTest {

    private static final String CODE = "GRP-EXPIRY0001";
    private BookingAuditEvidenceRepository audit;
    private ManagerAlertService alerts;
    private GroupExpiryReleaseRetryService retries;
    private BookingGroup group;

    @BeforeEach
    void setUp() {
        audit = mock(BookingAuditEvidenceRepository.class);
        alerts = mock(ManagerAlertService.class);
        retries = new GroupExpiryReleaseRetryService(audit, alerts);
        group = BookingGroup.builder().id(1L).groupCode(CODE).build();
    }

    private void failures(long count, Instant last) {
        when(audit.countByBookingCodeAndAction(CODE, GroupExpiryReleaseRetryService.AUDIT_FAILED)).thenReturn(count);
        when(audit.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(CODE, GroupExpiryReleaseRetryService.AUDIT_FAILED))
                .thenReturn(Optional.of(BookingAuditEvidence.builder().occurredAt(last).build()));
    }

    @Test
    void aGroupThatNeverFailedIsAlwaysDue() {
        assertThat(retries.isDue(CODE, Instant.now())).isTrue();
    }

    @Test
    void theFirstThreeRetriesAreOneMinuteApart() {
        Instant last = Instant.parse("2026-11-16T10:00:00Z");
        for (long failed = 1; failed <= 3; failed++) {
            failures(failed, last);
            assertThat(retries.isDue(CODE, last.plusSeconds(59))).as("failures=%d at 59s", failed).isFalse();
            assertThat(retries.isDue(CODE, last.plus(Duration.ofMinutes(1)))).as("failures=%d at 60s", failed).isTrue();
        }
    }

    @Test
    void afterTheThreeRetriesFailTheCadenceIsFiveMinutes() {
        Instant last = Instant.parse("2026-11-16T10:00:00Z");
        failures(4, last);
        assertThat(retries.isDue(CODE, last.plus(Duration.ofMinutes(4)))).isFalse();
        assertThat(retries.isDue(CODE, last.plus(Duration.ofMinutes(5)))).isTrue();
        failures(9, last);
        assertThat(retries.isDue(CODE, last.plus(Duration.ofMinutes(4)))).isFalse();
    }

    @Test
    void theManagersAreAlertedOnlyOnTheFailureThatEndsTheThreeRetries() {
        for (long failed = 1; failed <= 6; failed++) {
            when(audit.countByBookingCodeAndAction(CODE, GroupExpiryReleaseRetryService.AUDIT_FAILED)).thenReturn(failed);
            retries.recordFailure(group, "corr", "BookingConflictException");
        }
        verify(audit, times(6)).save(any(BookingAuditEvidence.class));
        verify(alerts, times(1)).alert(eq(group), eq(GroupNotificationService.KIND_EXPIRY_RELEASE_MANAGER_ALERT),
                eq(CODE), anyString(), anyString(), eq("corr"));
    }

    @Test
    void theSchedulerSkipsAGroupNotYetDueRecordsAFailureAndStopsAfterASuccess() {
        BookingGroupRepository groups = mock(BookingGroupRepository.class);
        GroupConfirmationService confirmation = mock(GroupConfirmationService.class);
        GroupExpiryReleaseRetryService gate = mock(GroupExpiryReleaseRetryService.class);
        GroupHoldExpiryScheduler scheduler = new GroupHoldExpiryScheduler(groups, confirmation, gate);
        when(groups.findExpiredPending(any(), any(Pageable.class))).thenReturn(List.of(group));

        when(gate.isDue(eq(CODE), any())).thenReturn(false);
        scheduler.releaseExpiredGroups();
        verifyNoInteractions(confirmation);

        when(gate.isDue(eq(CODE), any())).thenReturn(true);
        when(confirmation.releaseExpiredGroup(eq(CODE), anyString(), anyString()))
                .thenThrow(new BookingConflictException("Operations refused")).thenReturn(true);
        scheduler.releaseExpiredGroups();
        verify(gate).recordFailure(eq(group), anyString(), eq("BookingConflictException"));

        scheduler.releaseExpiredGroups();
        verify(confirmation, times(2)).releaseExpiredGroup(eq(CODE), anyString(), anyString());
        verify(gate, times(1)).recordFailure(any(), anyString(), anyString());
    }
}
