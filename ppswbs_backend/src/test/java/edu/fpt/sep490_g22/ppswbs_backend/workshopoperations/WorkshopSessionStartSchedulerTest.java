package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionStartScheduler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionStartTransition;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionStartTransition.Started;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T119 (lateness monitoring) and T125 (one failing session does not stop the batch), without a database. */
class WorkshopSessionStartSchedulerTest {

    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final WorkshopSessionStartTransition transition = mock(WorkshopSessionStartTransition.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WorkshopSessionStartScheduler scheduler =
            new WorkshopSessionStartScheduler(sessions, transition, registry, Duration.ofMinutes(5));
    private final Logger schedulerLog = (Logger) LoggerFactory.getLogger(WorkshopSessionStartScheduler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        schedulerLog.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        schedulerLog.detachAppender(logs);
    }

    private static WorkshopSession due(String reference) {
        return WorkshopSession.builder().reference(reference).state(SessionState.PUBLISHED).build();
    }

    private void dueSessions(String... references) {
        when(sessions.findByStateInAndScheduledStartAtLessThanEqual(any(), any()))
                .thenReturn(java.util.Arrays.stream(references).map(WorkshopSessionStartSchedulerTest::due).toList());
    }

    private long errorLogs() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).count();
    }

    @Test
    void startMoreThanFiveMinutesLateIsLoggedAtErrorAndCounted() {
        dueSessions("S-LATE");
        when(transition.startIfStillDue(eq("S-LATE"), any())).thenReturn(Optional.of(
                new Started("S-LATE", SessionState.PUBLISHED, Instant.now().minus(Duration.ofMinutes(10)))));

        scheduler.transitionDueSessions();

        assertThat(errorLogs()).isEqualTo(1);
        assertThat(logs.list.getFirst().getFormattedMessage()).contains("S-LATE");
        assertThat(registry.counter(WorkshopSessionStartScheduler.LATE_START_METRIC).count()).isEqualTo(1.0);
    }

    @Test
    void startWithinFiveMinutesLogsNoErrorAndDoesNotCount() {
        dueSessions("S-OK");
        when(transition.startIfStillDue(eq("S-OK"), any())).thenReturn(Optional.of(
                new Started("S-OK", SessionState.SALES_PAUSED, Instant.now().minus(Duration.ofMinutes(4)))));

        scheduler.transitionDueSessions();

        assertThat(errorLogs()).isZero();
        assertThat(registry.counter(WorkshopSessionStartScheduler.LATE_START_METRIC).count()).isZero();
    }

    @Test
    void sessionAlreadyChangedByAnotherActorIsNotReportedLate() {
        dueSessions("S-GONE");
        when(transition.startIfStillDue(eq("S-GONE"), any())).thenReturn(Optional.empty());

        scheduler.transitionDueSessions();

        assertThat(errorLogs()).isZero();
        assertThat(registry.counter(WorkshopSessionStartScheduler.LATE_START_METRIC).count()).isZero();
    }

    @Test
    void failingSessionIsLoggedAndTheRestOfTheBatchStillStarts() {
        dueSessions("S-BAD", "S-GOOD");
        when(transition.startIfStillDue(eq("S-BAD"), any())).thenThrow(new IllegalStateException("boom"));
        when(transition.startIfStillDue(eq("S-GOOD"), any())).thenReturn(Optional.of(
                new Started("S-GOOD", SessionState.PUBLISHED, Instant.now())));

        scheduler.transitionDueSessions();

        verify(transition).startIfStillDue(eq("S-GOOD"), any());
        assertThat(errorLogs()).isEqualTo(1);
        assertThat(logs.list.getFirst().getFormattedMessage()).contains("S-BAD");
        assertThat(registry.counter(WorkshopSessionStartScheduler.LATE_START_METRIC).count()).isZero();
    }

    @Test
    void queriesBothPublishedAndSalesPausedSessions() {
        when(sessions.findByStateInAndScheduledStartAtLessThanEqual(any(), any())).thenReturn(List.of());

        scheduler.transitionDueSessions();

        verify(sessions).findByStateInAndScheduledStartAtLessThanEqual(
                eq(List.of(SessionState.PUBLISHED, SessionState.SALES_PAUSED)), any());
    }
}
