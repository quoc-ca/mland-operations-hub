package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.common.notification.NotificationIntentPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.UnresolvedCancellationAlertScheduler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.UnresolvedCancellationAlertTransition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T133: the 24-hour Manager alert for a session still awaiting cancellation, without a database. */
class UnresolvedCancellationAlertTransitionTest {

    private static final String REF = "11111111-1111-1111-1111-111111111111";
    private static final Instant END = Instant.parse("2026-11-10T10:00:00Z");

    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final OperationsAuditEventRepository audits = mock(OperationsAuditEventRepository.class);
    private final AccountFacade accounts = mock(AccountFacade.class);
    private final NotificationIntentPort notifications = mock(NotificationIntentPort.class);
    private final UnresolvedCancellationAlertTransition transition =
            new UnresolvedCancellationAlertTransition(sessions, audits, accounts, notifications);
    private WorkshopSession session;

    @BeforeEach
    void setUp() {
        WorkshopLocation location = WorkshopLocation.builder().displayName("Chi nhánh Q1").build();
        session = WorkshopSession.builder().id(7L).reference(REF).state(SessionState.CANCELLATION_REQUESTED)
                .location(location).slot(WorkshopSlot.builder().label("Sáng").build())
                .workshopDate(LocalDate.of(2026, 11, 10)).scheduledEndAt(END).build();
        when(sessions.findByReferenceWithLock(REF)).thenReturn(Optional.of(session));
        when(accounts.listActiveManagerEmails()).thenReturn(List.of("a@mland.test", "b@mland.test"));
        when(notifications.submitIntent(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void doesNotAlertBefore24Hours() {
        assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(24)).minusSeconds(60))).isFalse();
        verify(notifications, never()).submitIntent(any(), any(), any(), any());
        verify(audits, never()).save(any());
    }

    @Test
    void alertsEveryManagerAtExactly24HoursAndWritesSentMarker() {
        assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(24)))).isTrue();

        ArgumentCaptor<String> events = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> templates = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<java.util.Map<String, Object>> snapshots = ArgumentCaptor.forClass(java.util.Map.class);
        verify(notifications, org.mockito.Mockito.times(2)).submitIntent(events.capture(), templates.capture(),
                recipients.capture(), snapshots.capture());
        assertThat(recipients.getAllValues()).containsExactly("a@mland.test", "b@mland.test");
        assertThat(templates.getValue()).isEqualTo("WORKSHOP_CANCELLATION_UNRESOLVED_ALERT");
        assertThat(events.getValue()).isEqualTo(REF + ":CANCELLATION_UNRESOLVED");
        assertThat(snapshots.getValue().keySet()).isSubsetOf(
                "sessionReference", "sessionName", "locationName", "scheduledEndAt", "hoursSinceScheduledEnd");
        assertThat(snapshots.getValue()).containsEntry("hoursSinceScheduledEnd", 24L);

        ArgumentCaptor<OperationsAuditEvent> audit = ArgumentCaptor.forClass(OperationsAuditEvent.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("CANCELLATION_UNRESOLVED_ALERT");
        assertThat(audit.getValue().getOutcome()).isEqualTo("SENT");
        assertThat(audit.getValue().getActorId()).isNull();
        assertThat(audit.getValue().getMetadata()).doesNotContain("@");
        assertThat(session.getState()).isEqualTo(SessionState.CANCELLATION_REQUESTED);
    }

    @Test
    void secondRunAfterSentMarkerSendsNothing() {
        when(audits.existsBySessionIdAndAction(7L, "CANCELLATION_UNRESOLVED_ALERT")).thenReturn(true);
        assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(30)))).isFalse();
        verify(notifications, never()).submitIntent(any(), any(), any(), any());
        verify(audits, never()).save(any());
    }

    @Test
    void noActiveManagerWritesNoMarkerSoNextScanRetries() {
        when(accounts.listActiveManagerEmails()).thenReturn(List.of());
        assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(25)))).isFalse();
        verify(notifications, never()).submitIntent(any(), any(), any(), any());
        verify(audits, never()).save(any());
    }

    @Test
    void failedSendWritesNoMarker() {
        when(notifications.submitIntent(any(), any(), any(), any()))
                .thenReturn(false);
        assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(25)))).isFalse();
        verify(audits, never()).save(any());
    }

    @Test
    void oneRejectedIntentWritesNoMarker() {
        when(notifications.submitIntent(any(), any(), any(), any()))
                .thenReturn(true)
                .thenReturn(false);
        assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(25)))).isFalse();
        verify(audits, never()).save(any());
    }

    @Test
    void duplicateIgnoredCountsAsAccepted() {
        when(notifications.submitIntent(any(), any(), any(), any()))
                .thenReturn(true);
        assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(25)))).isTrue();
        verify(audits).save(any());
    }

    @Test
    void cancelledOrOtherStatesAreSkipped() {
        for (SessionState state : List.of(SessionState.CANCELLED, SessionState.IN_PROGRESS, SessionState.PUBLISHED)) {
            session.setState(state);
            assertThat(transition.alertIfStillDue(REF, END.plus(Duration.ofHours(48)))).isFalse();
        }
        verify(notifications, never()).submitIntent(any(), any(), any(), any());
        verify(audits, never()).save(any());
    }

    @Test
    void schedulerKeepsGoingWhenOneSessionThrows() {
        UnresolvedCancellationAlertTransition mockTransition = mock(UnresolvedCancellationAlertTransition.class);
        when(sessions.findByStateAndScheduledEndAtLessThanEqual(eq(SessionState.CANCELLATION_REQUESTED), any()))
                .thenReturn(List.of(
                        WorkshopSession.builder().reference("r1").build(),
                        WorkshopSession.builder().reference("r2").build()));
        when(mockTransition.alertIfStillDue(eq("r1"), any())).thenThrow(new IllegalStateException("boom"));

        new UnresolvedCancellationAlertScheduler(sessions, mockTransition).alertUnresolvedCancellations();

        verify(mockTransition).alertIfStillDue(eq("r2"), any());
    }
}
