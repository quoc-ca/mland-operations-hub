package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 003.3 T137 (FR-040): the Admin-only key replacement route; Manager and Staff are 403; a replay does nothing. */
class RefundAccountKeyReplacementControllerTest {

    private static final String KEY = "idem-key-00000001";
    private final RefundAccountKeyReplacementService replacement = mock(RefundAccountKeyReplacementService.class);
    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private final RefundAccountKeyReplacementController controller =
            new RefundAccountKeyReplacementController(replacement, authorization, idempotency);

    private static CurrentAccountActor actor(long id, ActorRole role) {
        return new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of(1L));
    }

    private static Authentication auth(CurrentAccountActor actor) {
        return new UsernamePasswordAuthenticationToken(actor, null, List.of());
    }

    @BeforeEach
    void setUp() {
        when(idempotency.execute(any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<String> supplier = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, supplier.get());
        });
    }

    @Test
    void anAdminReplacesTheKeyAndGetsOnlyCounts() {
        var admin = actor(1L, ActorRole.ADMIN);
        when(authorization.requireAdmin(admin)).thenReturn(admin);
        when(replacement.replace(eq(1L), eq("Khoa bi lo"), anyString()))
                .thenReturn(new RefundAccountKeyReplacementService.Result(3, 1));

        var response = controller.replace(KEY, "corr", new RefundAccountKeyReplacementController.KeyReplacementRequest("Khoa bi lo"), auth(admin));

        assertThat(response.requestsReset()).isEqualTo(3);
        assertThat(response.linksSent()).isEqualTo(3);
        assertThat(response.failed()).isEqualTo(1);
    }

    @Test
    void aManagerAndAStaffMemberAreForbiddenAndNothingRuns() {
        for (ActorRole role : List.of(ActorRole.MANAGER, ActorRole.STAFF)) {
            var other = actor(2L, role);
            when(authorization.requireAdmin(other)).thenThrow(new WorkshopSettlementAuthorizationException("ADMIN_REQUIRED"));
            assertThatThrownBy(() -> controller.replace(KEY, "corr", new RefundAccountKeyReplacementController.KeyReplacementRequest("x"), auth(other)))
                    .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        }
        verifyNoInteractions(replacement);
    }

    @Test
    void aMissingKeyIsRefusedAndAReplayReturnsTheStoredResultWithoutRunningAgain() {
        var admin = actor(1L, ActorRole.ADMIN);
        when(authorization.requireAdmin(admin)).thenReturn(admin);
        var body = new RefundAccountKeyReplacementController.KeyReplacementRequest("Khoa bi lo");
        assertThatThrownBy(() -> controller.replace(null, "corr", body, auth(admin)))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessage("IDEMPOTENCY_KEY_REQUIRED");

        doReturn(new SettlementIdempotencyResult(true, "returned=3;failed=0")).when(idempotency).execute(any(), any(), any());
        var replay = controller.replace(KEY, "corr", body, auth(admin));

        assertThat(replay.requestsReset()).isEqualTo(3);
        verify(replacement, never()).replace(any(), any(), any());
    }
}
