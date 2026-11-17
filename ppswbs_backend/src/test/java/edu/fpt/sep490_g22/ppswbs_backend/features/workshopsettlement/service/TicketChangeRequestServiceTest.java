package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestStatusDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecisionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.3 T117 (FR-045a): the Staff/Manager queue of changes; owner decision 2026-11-17: Staff decide inside their locations, Admin has no part. */
class TicketChangeRequestServiceTest {

    private static final String KEY = "idem-key-decision-0001";
    private static final String REQUEST_REF = "CHG-REQ-1";

    private final SettlementTicketChangeRequestRepository requests = mock(SettlementTicketChangeRequestRepository.class);
    private final TicketChangeService changes = mock(TicketChangeService.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private final WorkshopBookingFacade booking = mock(WorkshopBookingFacade.class);
    private final TicketChangeRequestService service = new TicketChangeRequestService(requests, changes,
            new WorkshopSettlementAuthorizationService(mock(AccountFacade.class), audits,
                    mock(WorkshopOperationsSettlementAdapter.class)), audits, booking);

    private final CurrentAccountActor manager =
            new CurrentAccountActor(300L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staffAtBranch7 =
            new CurrentAccountActor(200L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(7L));

    private SettlementTicketChangeRequest pending(String reference, long location, Instant expiresAt) {
        return SettlementTicketChangeRequest.builder().requestReference(reference).idempotencyKey("k-" + reference)
                .ticketCode("T-" + reference).groupCode("GRP-1").locationReference(location).changeType("ADVANCE")
                .targetSessionReferences(UUID.randomUUID().toString()).blockedRules("CHANGE_ALLOWANCE_EXHAUSTED")
                .status(SettlementTicketChangeRequest.PENDING).requesterRole("MEMBER").channel("ONLINE")
                .expiresAt(expiresAt).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private ChangeRequestDecisionDto decision(ChangeRequestDecision decision) {
        return new ChangeRequestDecisionDto(decision, "Customer is a long-standing member");
    }

    @Test
    @DisplayName("list: defaults to PENDING, Staff sees only their locations, Manager sees all, stale requests are expired first")
    void list_scopesByRoleAndExpiresStale() {
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
        SettlementTicketChangeRequest here = pending("A", 7L, future);
        SettlementTicketChangeRequest elsewhere = pending("B", 9L, future);
        SettlementTicketChangeRequest stale = pending("C", 7L, Instant.now().minus(1, ChronoUnit.HOURS));
        when(requests.findByStatusAndExpiresAtLessThanEqual(eq("PENDING"), any())).thenReturn(List.of(stale));
        when(requests.findByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(here, elsewhere));

        assertThat(service.list(staffAtBranch7, null)).extracting(ChangeRequestDto::requestReference).containsExactly("A");
        assertThat(service.list(manager, "pending")).extracting(ChangeRequestDto::requestReference)
                .containsExactly("A", "B");
        verify(changes, times(2)).expire(eq(stale), any());
    }

    @Test
    @DisplayName("list: a member, or an unknown status, is refused")
    void list_refusesMemberAndBadStatus() {
        CurrentAccountActor member = new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

        assertThatThrownBy(() -> service.list(member, null)).isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> service.list(manager, "DONE")).isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("STATUS_INVALID");
    }

    @Test
    @DisplayName("decide APPROVE: grants and applies the change through the ticket service, then records the decision")
    void approve_appliesChangeAndRecordsDecision() {
        SettlementTicketChangeRequest request = pending(REQUEST_REF, 7L, Instant.now().plus(1, ChronoUnit.DAYS));
        when(requests.findByRequestReferenceForUpdate(REQUEST_REF)).thenReturn(Optional.of(request));
        when(changes.applyApproved(eq(request), eq(manager), anyString(), anyString(), any())).thenReturn("TCHG-1");

        ChangeRequestDto result = service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, "CORR", manager);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.appliedChangeReference()).isEqualTo("TCHG-1");
        assertThat(request.getDecidedByAccountId()).isEqualTo(300L);
        assertThat(request.getDecisionIdempotencyKey()).isEqualTo(KEY);
        verify(audits).save(org.mockito.ArgumentMatchers.argThat(a -> "TICKET_CHANGE_REQUEST_APPROVED".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("decide: a Staff member of the request's location approves and the decision is recorded under their account")
    void staffInScopeApproves() {
        SettlementTicketChangeRequest request = pending(REQUEST_REF, 7L, Instant.now().plus(1, ChronoUnit.DAYS));
        when(requests.findByRequestReferenceForUpdate(REQUEST_REF)).thenReturn(Optional.of(request));
        when(changes.applyApproved(eq(request), eq(staffAtBranch7), anyString(), anyString(), any())).thenReturn("TCHG-2");

        ChangeRequestDto result = service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, null, staffAtBranch7);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(request.getDecidedByAccountId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("decide REJECT: nothing is applied and the request is closed with the reason")
    void reject_appliesNothing() {
        SettlementTicketChangeRequest request = pending(REQUEST_REF, 7L, Instant.now().plus(1, ChronoUnit.DAYS));
        when(requests.findByRequestReferenceForUpdate(REQUEST_REF)).thenReturn(Optional.of(request));

        ChangeRequestDto result = service.decide(REQUEST_REF, decision(ChangeRequestDecision.REJECT), KEY, null, manager);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.decisionReason()).isEqualTo("Customer is a long-standing member");
        verify(changes, never()).applyApproved(any(), any(), any(), any(), any());
        verify(audits).save(org.mockito.ArgumentMatchers.argThat(a -> "TICKET_CHANGE_REQUEST_REJECTED".equals(a.getActionCode())));
    }

    @Test
    @DisplayName("decide: the same Idempotency-Key replays the stored decision without applying again")
    void decide_replayIsIdempotent() {
        SettlementTicketChangeRequest request = pending(REQUEST_REF, 7L, Instant.now().plus(1, ChronoUnit.DAYS));
        request.setStatus(SettlementTicketChangeRequest.APPROVED);
        request.setDecisionIdempotencyKey(KEY);
        when(requests.findByRequestReferenceForUpdate(REQUEST_REF)).thenReturn(Optional.of(request));

        ChangeRequestDto result = service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, null, manager);

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(changes, never()).applyApproved(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("decide: Staff of another location and an Admin cannot decide, a reason is required, and a decided or expired request is a 409")
    void decide_refusals() {
        SettlementTicketChangeRequest request = pending(REQUEST_REF, 7L, Instant.now().plus(1, ChronoUnit.DAYS));
        when(requests.findByRequestReferenceForUpdate(REQUEST_REF)).thenReturn(Optional.of(request));

        CurrentAccountActor staffAtBranch9 = new CurrentAccountActor(201L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(9L));
        CurrentAccountActor admin = new CurrentAccountActor(400L, ActorRole.ADMIN, ActorStatus.ACTIVE, true, List.of());
        assertThatThrownBy(() -> service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, null, staffAtBranch9))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessageContaining("LOCATION_SCOPE_DENIED");
        assertThatThrownBy(() -> service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, null, admin))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessageContaining("STAFF_OR_MANAGER_REQUIRED");
        assertThatThrownBy(() -> service.decide(REQUEST_REF,
                new ChangeRequestDecisionDto(ChangeRequestDecision.APPROVE, " "), KEY, null, manager))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessageContaining("REASON_REQUIRED");

        request.setStatus(SettlementTicketChangeRequest.REJECTED);
        assertThatThrownBy(() -> service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, null, manager))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("CHANGE_REQUEST_NOT_PENDING");

        request.setStatus(SettlementTicketChangeRequest.PENDING);
        request.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThatThrownBy(() -> service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, null, manager))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("CHANGE_REQUEST_EXPIRED");

        when(requests.findByRequestReferenceForUpdate("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decide("NOPE", decision(ChangeRequestDecision.APPROVE), KEY, null, manager))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }

    @Test
    @DisplayName("decide APPROVE: when 003.2 still refuses (quota still full) the request stays PENDING")
    void approve_refusedByOperations_staysPending() {
        SettlementTicketChangeRequest request = pending(REQUEST_REF, 7L, Instant.now().plus(1, ChronoUnit.DAYS));
        when(requests.findByRequestReferenceForUpdate(REQUEST_REF)).thenReturn(Optional.of(request));
        doAnswer(invocation -> {
            throw new WorkshopSettlementConflictException("RESCHEDULE_QUOTA_EXHAUSTED");
        }).when(changes).applyApproved(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.decide(REQUEST_REF, decision(ChangeRequestDecision.APPROVE), KEY, null, manager))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("RESCHEDULE_QUOTA_EXHAUSTED");
        assertThat(request.getStatus()).isEqualTo(SettlementTicketChangeRequest.PENDING);
        assertThat(request.getDecisionIdempotencyKey()).isNull();
        verify(requests, never()).save(any());
    }

    private final CurrentAccountActor buyer =
            new CurrentAccountActor(77L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

    private void ticketBoughtBy(String ticket, Long buyerAccountId) {
        when(booking.getTicketFolioContext(ticket)).thenReturn(Optional.of(new TicketFolioContext(
                ticket, "GRP-1", "L1", "7", 7L, List.of(UUID.randomUUID().toString()), buyerAccountId)));
    }

    @Test
    @DisplayName("latestStatusForBuyer: the buyer sees the state and the Manager reason of the latest request")
    void latestStatusForBuyer_returnsLatestState() {
        ticketBoughtBy("T-A", 77L);
        SettlementTicketChangeRequest approved = pending("A", 7L, Instant.now().plus(1, ChronoUnit.DAYS));
        approved.setStatus(SettlementTicketChangeRequest.APPROVED);
        approved.setDecisionReason("ok");
        approved.setDecidedAt(Instant.now());
        when(requests.findFirstByTicketCodeOrderByCreatedAtDesc("T-A")).thenReturn(Optional.of(approved));

        Optional<ChangeRequestStatusDto> status = service.latestStatusForBuyer("T-A", buyer);

        assertThat(status).get().extracting(ChangeRequestStatusDto::status, ChangeRequestStatusDto::decisionReason)
                .containsExactly("APPROVED", "ok");
    }

    @Test
    @DisplayName("latestStatusForBuyer: a pending request past its expiry is reported as EXPIRED, without writing")
    void latestStatusForBuyer_reportsLapsedPendingAsExpired() {
        ticketBoughtBy("T-B", 77L);
        when(requests.findFirstByTicketCodeOrderByCreatedAtDesc("T-B"))
                .thenReturn(Optional.of(pending("B", 7L, Instant.now().minus(1, ChronoUnit.HOURS))));

        assertThat(service.latestStatusForBuyer("T-B", buyer)).get()
                .extracting(ChangeRequestStatusDto::status).isEqualTo("EXPIRED");
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("latestStatusForBuyer: no request yet gives an empty answer")
    void latestStatusForBuyer_emptyWhenNeverRequested() {
        ticketBoughtBy("T-C", 77L);
        when(requests.findFirstByTicketCodeOrderByCreatedAtDesc("T-C")).thenReturn(Optional.empty());

        assertThat(service.latestStatusForBuyer("T-C", buyer)).isEmpty();
    }

    @Test
    @DisplayName("latestStatusForBuyer: only the Member who bought the ticket may read it")
    void latestStatusForBuyer_rejectsOtherActors() {
        ticketBoughtBy("T-D", 99L);

        assertThatThrownBy(() -> service.latestStatusForBuyer("T-D", buyer))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessage("NOT_BUYER");
        assertThatThrownBy(() -> service.latestStatusForBuyer("T-D", manager))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> service.latestStatusForBuyer("T-D", null))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verify(requests, never()).findFirstByTicketCodeOrderByCreatedAtDesc(any());
    }
}
