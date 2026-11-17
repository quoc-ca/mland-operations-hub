package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.LatePaymentReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.LatePaymentReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryFailed;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.DeliveryFailureCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.1 T147 / T129 (FR-015a, FR-015b, FR-040a): a failed confirmation email is resent 3 times, then a Manager phones. */
class GroupNotificationFailureServiceTest {

    private static final String CODE = "GRP-NOTIF0001";
    private BookingGroupRepository groups;
    private BookingAuditEvidenceRepository audit;
    private LatePaymentReviewRepository reviews;
    private GroupConfirmationService confirmation;
    private GroupNotificationService notifications;
    private ManagerAlertService alerts;
    private GroupNotificationFailureService service;
    private BookingGroup group;
    private LatePaymentReview review;

    @BeforeEach
    void setUp() {
        groups = mock(BookingGroupRepository.class);
        audit = mock(BookingAuditEvidenceRepository.class);
        reviews = mock(LatePaymentReviewRepository.class);
        confirmation = mock(GroupConfirmationService.class);
        notifications = mock(GroupNotificationService.class);
        alerts = mock(ManagerAlertService.class);
        service = new GroupNotificationFailureService(groups, audit, reviews, confirmation, notifications, alerts,
                mock(PlatformTransactionManager.class));
        group = BookingGroup.builder().id(1L).groupCode(CODE).groupStatus(BookingStatus.CONFIRMED).build();
        review = LatePaymentReview.builder().resultDueAt(Instant.now().plus(Duration.ofHours(20))).build();
        when(groups.findByGroupCodeForUpdate(CODE)).thenReturn(Optional.of(group));
        when(reviews.findFirstByGroupIdOrderByIdDesc(1L)).thenReturn(Optional.of(review));
    }

    private static BookingNotificationDeliveryFailed failure(String reference, String kind, Instant at) {
        return new BookingNotificationDeliveryFailed(reference, CODE, kind, DeliveryFailureCode.PROVIDER_UNAVAILABLE, at, "corr");
    }

    private void failuresSoFar(long count) {
        when(audit.countByBookingCodeAndAction(CODE, GroupNotificationFailureService.AUDIT_FAILED)).thenReturn(count);
    }

    @Test
    void aDuplicateOrForeignSignalChangesNothing() {
        when(audit.existsByBookingCodeAndActionAndActionReason(CODE, GroupNotificationFailureService.AUDIT_FAILED, "N-1"))
                .thenReturn(true);
        service.onDeliveryFailed(failure("N-1", GroupNotificationService.KIND_GROUP_CONFIRMATION, Instant.now()));
        service.onDeliveryFailed(failure("N-2", GroupNotificationService.KIND_BUYER_VERIFICATION_OTP, Instant.now()));
        service.onDeliveryFailed(new BookingNotificationDeliveryFailed("N-3", "GRP-UNKNOWN",
                GroupNotificationService.KIND_GROUP_CONFIRMATION, DeliveryFailureCode.RECIPIENT_INVALID, Instant.now(), "c"));

        verify(audit, never()).save(any());
        verifyNoInteractions(alerts, confirmation);
    }

    @Test
    void theFirstFailureIsAuditedMarksTheReviewResendScheduledAndAlertsNobody() {
        failuresSoFar(1);

        service.onDeliveryFailed(failure("N-1", GroupNotificationService.KIND_GROUP_CONFIRMATION, Instant.now()));

        verify(audit).save(argThat((BookingAuditEvidence a) -> a.getAction().equals(GroupNotificationFailureService.AUDIT_FAILED)
                && a.getActionReason().equals("N-1")));
        assertThat(review.getResultDeliveryState()).isEqualTo(LatePaymentReview.DELIVERY_RESEND_SCHEDULED);
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(alerts);
    }

    @Test
    void theFailureThatEndsTheThreeResendsAlertsManagersOnceAndFlagsManualContact() {
        failuresSoFar(4);

        service.onDeliveryFailed(failure("N-4", GroupNotificationService.KIND_GROUP_CONFIRMATION_RESEND, Instant.now()));

        verify(alerts, times(1)).alert(eq(group), eq(GroupNotificationService.KIND_CONFIRMATION_DELIVERY_MANAGER_ALERT),
                eq("N-4"), anyString(), anyString(), eq("corr"));
        verify(audit).save(argThat((BookingAuditEvidence a) -> a.getAction().equals(GroupNotificationFailureService.AUDIT_EXHAUSTED)));
        assertThat(review.getResultDeliveryState()).isEqualTo(LatePaymentReview.DELIVERY_MANUAL_CONTACT);
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void aFailureAfterTheResultDeadlineFlagsManualContactAtOnce() {
        review.setResultDueAt(Instant.now().minusSeconds(60));
        failuresSoFar(1);

        service.onDeliveryFailed(failure("N-1", GroupNotificationService.KIND_GROUP_CONFIRMATION, Instant.now()));

        assertThat(review.getResultDeliveryState()).isEqualTo(LatePaymentReview.DELIVERY_MANUAL_CONTACT);
    }

    @Test
    void resendsComeAt5Minutes30MinutesAnd2HoursAndOnlyOncePerFailure() {
        Instant failedAt = Instant.parse("2026-11-16T10:00:00Z");
        Duration[] delays = {Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2)};
        when(notifications.findActiveAccessLink(group)).thenReturn("https://x/#token");
        when(audit.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(CODE, GroupNotificationFailureService.AUDIT_FAILED))
                .thenReturn(Optional.of(BookingAuditEvidence.builder().occurredAt(failedAt).build()));
        when(audit.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(CODE, GroupNotificationFailureService.AUDIT_RESEND_QUEUED))
                .thenReturn(Optional.empty());
        for (int failures = 1; failures <= 3; failures++) {
            failuresSoFar(failures);
            service.resendIfDue(CODE, failedAt.plus(delays[failures - 1]).minusSeconds(1));
            verify(confirmation, times(failures - 1)).resendConfirmationEmail(any(), anyString(), anyString());
            service.resendIfDue(CODE, failedAt.plus(delays[failures - 1]));
            verify(confirmation, times(failures)).resendConfirmationEmail(eq(group), eq("https://x/#token"), anyString());
        }

        // A resend already queued after the last failure is not queued twice.
        when(audit.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(CODE, GroupNotificationFailureService.AUDIT_RESEND_QUEUED))
                .thenReturn(Optional.of(BookingAuditEvidence.builder().occurredAt(failedAt.plusSeconds(1)).build()));
        service.resendIfDue(CODE, failedAt.plus(Duration.ofHours(3)));
        verify(confirmation, times(3)).resendConfirmationEmail(any(), anyString(), anyString());

        // After the fourth failure nothing is resent any more.
        failuresSoFar(4);
        service.resendIfDue(CODE, failedAt.plus(Duration.ofHours(5)));
        verify(confirmation, times(3)).resendConfirmationEmail(any(), anyString(), anyString());
    }

    @Test
    void withoutAnActiveAccessLinkTheManagerIsAlertedInsteadOfResending() {
        Instant failedAt = Instant.parse("2026-11-16T10:00:00Z");
        failuresSoFar(1);
        when(notifications.findActiveAccessLink(group)).thenReturn(null);
        when(audit.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(CODE, GroupNotificationFailureService.AUDIT_FAILED))
                .thenReturn(Optional.of(BookingAuditEvidence.builder().occurredAt(failedAt).build()));
        when(audit.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(CODE, GroupNotificationFailureService.AUDIT_RESEND_QUEUED))
                .thenReturn(Optional.empty());

        service.resendIfDue(CODE, failedAt.plus(Duration.ofMinutes(5)));

        verify(confirmation, never()).resendConfirmationEmail(any(), anyString(), anyString());
        verify(alerts).alert(eq(group), eq(GroupNotificationService.KIND_CONFIRMATION_DELIVERY_MANAGER_ALERT),
                eq(CODE), anyString(), anyString(), anyString());
        assertThat(review.getResultDeliveryState()).isEqualTo(LatePaymentReview.DELIVERY_MANUAL_CONTACT);
    }
}
