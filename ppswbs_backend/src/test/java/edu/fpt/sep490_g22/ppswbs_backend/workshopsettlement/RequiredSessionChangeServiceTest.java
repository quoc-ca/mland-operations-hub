package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RequiredSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RequiredSessionChangeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RequiredSessionChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequiredSessionChangeServiceTest {
    private static final String SESSION_1 = "11111111-1111-1111-1111-111111111111";
    private static final String SESSION_2 = "22222222-2222-2222-2222-222222222222";

    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private final SettlementCaseRepository cases = mock(SettlementCaseRepository.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private final RequiredSessionChangeService service =
            new RequiredSessionChangeService(booking, operations, authorization, idempotency, cases, audits);
    private final AtomicReference<SettlementCase> savedCase = new AtomicReference<>();
    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER,
            ActorStatus.ACTIVE, true, List.of());

    @Test
    void sendsCompleteTargetSetToOperationsAndDoesNotRewriteFrozenBookingMoney() {
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 100_000L, 50_000L, 50_000L, 1,
                List.of(SESSION_1, SESSION_2), 5L));
        when(operations.replace(eq("BK-1"), eq(List.of(SESSION_1, SESSION_2)), anyString(), anyString()))
                .thenReturn(RequiredSessionReplacementOutcome.accepted());
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Supplier<String> outcome = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, outcome.get());
        });
        when(cases.saveAndFlush(any(SettlementCase.class))).thenAnswer(invocation -> {
            SettlementCase row = invocation.getArgument(0);
            savedCase.set(row);
            return row;
        });
        when(cases.findByCaseReference(anyString())).thenAnswer(invocation -> {
            SettlementCase row = savedCase.get();
            return row != null && row.getCaseReference().equals(invocation.getArgument(0))
                    ? Optional.of(row) : Optional.empty();
        });

        var result = service.open("BK-1", "key-1", "CORR-1",
                new RequiredSessionChangeCommandDto(List.of(SESSION_1, SESSION_2), "weather", null), manager);

        assertThat(result.caseType()).isEqualTo("REQUIRED_SESSION_CHANGE");
        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(result.bookingCode()).isEqualTo("BK-1");
        assertThat(savedCase.get().getReasonReference()).isEqualTo("weather");
        verify(audits).save(argThat(audit -> audit.getActionCode().equals("REQUIRED_SESSION_CHANGE_ACCEPTED")
                && audit.getActorReference().equals("10")));
        verify(authorization).requireManager(manager);
        verify(operations).replace(eq("BK-1"), eq(List.of(SESSION_1, SESSION_2)), anyString(), eq("CORR-1"));
        verify(booking).confirmedProjection("BK-1");
        verifyNoMoreInteractions(booking);
    }

    @Test
    void rejectsPartialTargetSetBeforeCallingOperations() {
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 100_000L, 50_000L, 50_000L, 1,
                List.of(SESSION_1, SESSION_2), 5L));

        assertThatThrownBy(() -> service.open("BK-1", "key-1", null,
                new RequiredSessionChangeCommandDto(List.of(SESSION_1), "weather", null), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("complete target");
        verifyNoInteractions(operations, idempotency, cases, audits);
    }

    @Test
    void mlandCancellationHandoffCannotBeSilentlyTreatedAsOrdinaryManagerChange() {
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 100_000L, 50_000L, 50_000L, 1,
                List.of(SESSION_1, SESSION_2), 5L));

        assertThatThrownBy(() -> service.open("BK-1", "key-1", null,
                new RequiredSessionChangeCommandDto(List.of(SESSION_1, SESSION_2), "weather", "HANDOFF-1"), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("MLAND_CANCELLATION_REQUIRES_REMEDY_FLOW");
        verifyNoInteractions(operations, idempotency, cases, audits);
    }

    @Test
    void customerCanReadMatchingCaseUsingGuestProofAndGetsOnlySafeProjection() {
        var row = SettlementCase.builder().caseReference("CASE-1").bookingReference("BK-1")
                .caseType("REQUIRED_SESSION_CHANGE").lifecycleStatus("RESOLVED")
                .correlationReference("CORR-1").reasonReference("private reason").build();
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 100_000L, 50_000L, 50_000L, 1,
                List.of(SESSION_1, SESSION_2), 5L));
        when(booking.verifyCustomerAccess("BK-1", null, "guest@example.com", null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        when(cases.findByCaseReference("CASE-1")).thenReturn(Optional.of(row));

        var result = service.read("BK-1", "CASE-1", "guest@example.com", null, null);

        assertThat(result.caseReference()).isEqualTo("CASE-1");
        assertThat(result.bookingCode()).isEqualTo("BK-1");
        assertThat(result.correlationReference()).isEqualTo("CORR-1");
        assertThat(result.toString()).doesNotContain("private reason", "guest@example.com");
    }

    @Test
    void guestReadRequiresExactlyOneContactProofAndCaseMustBelongToRouteBooking() {
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 100_000L, 50_000L, 50_000L, 1,
                List.of(SESSION_1, SESSION_2), 5L));

        assertThatThrownBy(() -> service.read("BK-1", "CASE-1", null, null, null))
                .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> service.read("BK-1", "CASE-1", "a@example.com", "0900000000", null))
                .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementNotFoundException.class);
        verifyNoInteractions(cases);
    }
}
