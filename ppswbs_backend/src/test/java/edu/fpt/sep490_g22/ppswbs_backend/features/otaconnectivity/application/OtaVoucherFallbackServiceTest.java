package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnauthorizedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("003.4 T033: Shared-Voucher Incident Fallback Tests")
class OtaVoucherFallbackServiceTest {

    @Mock private OtaAuthorizationService authorization;
    @Mock private OtaReconciliationWorkItemRepository workItems;
    @Mock private OtaAuditCorrelationService audit;
    @Mock private OtaChannelBookingRepository bookingRepository;

    private OtaVoucherFallbackService service;

    private final AccountSummary manager = new AccountSummary(42L, ActorRole.MANAGER, ActorStatus.ACTIVE,
            "manager@example.test", Instant.now(), Instant.now());
    private final AccountSummary staff = new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE,
            "staff@example.test", Instant.now(), Instant.now());
    private final AccountSummary member = new AccountSummary(202L, ActorRole.MEMBER, ActorStatus.ACTIVE,
            "member@example.test", Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        service = new OtaVoucherFallbackService(authorization, workItems, audit, bookingRepository);
        when(audit.safeCorrelationReference(any())).thenAnswer(invocation -> {
            String ref = invocation.getArgument(0);
            return ref != null && !ref.isBlank() ? ref : "00000000-0000-0000-0000-000000000042";
        });
        when(workItems.findByAuditReference(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Success: Manager records audited incident work item without automatic check-in")
    void managerRecordsAuditedIncidentWithoutAutomaticCheckIn() {
        when(authorization.authenticate("Bearer test-mgr")).thenReturn(manager);
        when(audit.safeCorrelationReference("corr-42")).thenReturn("00000000-0000-0000-0000-000000000042");
        when(workItems.save(any(OtaReconciliationWorkItem.class))).thenAnswer(invocation -> {
            OtaReconciliationWorkItem item = invocation.getArgument(0);
            item.setPublicReference("WI-1");
            return item;
        });

        var outcome = service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test-mgr", "OTA-BK-1", "1001", "MISSING_TICKETS", "EV-1", "corr-42", false));

        assertThat(outcome.status()).isEqualTo("OPEN");
        assertThat(outcome.workItemReference()).isEqualTo("WI-1");
        assertThat(outcome.correlationReference()).isEqualTo("00000000-0000-0000-0000-000000000042");

        // Verify work item category is SHARED_VOUCHER_INCIDENT and status is OPEN
        verify(workItems).save(argThat(item -> "SHARED_VOUCHER_INCIDENT".equals(item.getCategory())
                && "OPEN".equals(item.getStatus())
                && "1001".equals(item.getLocationReference())));

        // Verify audit event recorded
        verify(audit).record(eq(OtaAuditCorrelationService.IntegrationAction.RECONCILIATION_REQUIRED),
                isNull(), isNull(), eq("00000000-0000-0000-0000-000000000042"),
                eq(OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED));

        // Invariant: no check-in operation is performed
    }

    @Test
    @DisplayName("Success: Scoped Staff with matching location scope records audited incident")
    void scopedStaff_recordsIncident() {
        when(authorization.authenticate("Bearer test-staff")).thenReturn(staff);
        when(authorization.requireStaffLocation("Bearer test-staff", 1001L)).thenReturn(staff);
        when(workItems.save(any(OtaReconciliationWorkItem.class))).thenAnswer(invocation -> {
            OtaReconciliationWorkItem item = invocation.getArgument(0);
            item.setPublicReference("WI-STAFF-1");
            return item;
        });

        var outcome = service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test-staff", "OTA-BK-1", "1001", "SHARED_VOUCHER_ONLY", "EV-STAFF", "corr-staff", false));

        assertThat(outcome.status()).isEqualTo("OPEN");
        assertThat(outcome.workItemReference()).isEqualTo("WI-STAFF-1");
        verify(authorization).requireStaffLocation("Bearer test-staff", 1001L);
    }

    @Test
    @DisplayName("Fail-closed: Individual ticket proof exists rejects fallback and persists nothing")
    void individualProofRejectsFallbackAndPersistsNothing() {
        assertThatThrownBy(() -> service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test", "OTA-BK-1", "1001", "MISSING_TICKETS", "EV-1", null, true)))
                .isInstanceOf(OtaForbiddenException.class)
                .hasMessageContaining("individual ticket proof exists");

        verifyNoInteractions(authorization, workItems, audit);
    }

    @Test
    @DisplayName("Fail-closed: Missing evidence reference is rejected")
    void missingEvidence_isRejected() {
        assertThatThrownBy(() -> service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test", "OTA-BK-1", "1001", "MISSING_TICKETS", "", null, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence are required");

        assertThatThrownBy(() -> service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test", "OTA-BK-1", "1001", "MISSING_TICKETS", null, null, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence are required");

        verifyNoInteractions(authorization, workItems, audit);
    }

    @Test
    @DisplayName("Fail-closed: Unauthorized caller throws exception")
    void unauthorizedCaller_throwsException() {
        when(authorization.authenticate(null)).thenThrow(new OtaUnauthorizedException("A valid Bearer token is required"));

        assertThatThrownBy(() -> service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                null, "OTA-BK-1", "1001", "REASON", "EV-1", null, false)))
                .isInstanceOf(OtaUnauthorizedException.class);

        verifyNoInteractions(workItems, audit);
    }

    @Test
    @DisplayName("Fail-closed: Non-staff/non-manager role (e.g. MEMBER) is forbidden")
    void nonStaffRole_isForbidden() {
        when(authorization.authenticate("Bearer test-member")).thenReturn(member);

        assertThatThrownBy(() -> service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test-member", "OTA-BK-1", "1001", "REASON", "EV-1", null, false)))
                .isInstanceOf(OtaForbiddenException.class)
                .hasMessageContaining("requires an active Manager, Admin, or scoped Staff");

        verifyNoInteractions(workItems, audit);
    }

    @Test
    @DisplayName("Fail-closed: Staff outside location scope throws OtaLocationScopeException")
    void staffOutsideLocationScope_throwsException() {
        when(authorization.authenticate("Bearer test-staff")).thenReturn(staff);
        when(authorization.requireStaffLocation("Bearer test-staff", 9999L))
                .thenThrow(new OtaLocationScopeException("Location is outside active scope"));

        assertThatThrownBy(() -> service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test-staff", "OTA-BK-1", "9999", "REASON", "EV-1", null, false)))
                .isInstanceOf(OtaLocationScopeException.class);

        verifyNoInteractions(workItems, audit);
    }

    @Test
    @DisplayName("Fail-closed: Booking location mismatch with command location reference throws OtaLocationScopeException")
    void bookingLocationMismatch_throwsException() {
        when(authorization.authenticate("Bearer test-mgr")).thenReturn(manager);

        OtaChannelMapping mapping = mock(OtaChannelMapping.class);
        when(mapping.getLocationReference()).thenReturn("1001");
        OtaChannelBooking booking = mock(OtaChannelBooking.class);
        when(booking.getMapping()).thenReturn(mapping);
        when(bookingRepository.findByPublicReference("OTA-BK-1")).thenReturn(Optional.of(booking));

        // Command passes location "2002", but booking is at "1001"
        assertThatThrownBy(() -> service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test-mgr", "OTA-BK-1", "2002", "REASON", "EV-1", null, false)))
                .isInstanceOf(OtaLocationScopeException.class)
                .hasMessageContaining("does not match booking location");

        verifyNoInteractions(workItems, audit);
    }

    @Test
    @DisplayName("Idempotent replay: Replaying same correlation returns existing work item without duplicate save")
    void idempotentReplay_returnsExistingWorkItem() {
        when(authorization.authenticate("Bearer test-mgr")).thenReturn(manager);
        when(audit.safeCorrelationReference("corr-repeat")).thenReturn("00000000-0000-0000-0000-000000000099");

        OtaReconciliationWorkItem existing = OtaReconciliationWorkItem.builder()
                .publicReference("WI-EXISTING-99")
                .status("OPEN")
                .build();
        when(workItems.findByAuditReference("00000000-0000-0000-0000-000000000099")).thenReturn(Optional.of(existing));

        var outcome = service.recordSharedVoucherIncident(new OtaVoucherFallbackService.IncidentCommand(
                "Bearer test-mgr", "OTA-BK-1", "1001", "REASON", "EV-1", "corr-repeat", false));

        assertThat(outcome.status()).isEqualTo("OPEN");
        assertThat(outcome.workItemReference()).isEqualTo("WI-EXISTING-99");
        assertThat(outcome.correlationReference()).isEqualTo("00000000-0000-0000-0000-000000000099");
        verify(workItems, never()).save(any());
    }
}
