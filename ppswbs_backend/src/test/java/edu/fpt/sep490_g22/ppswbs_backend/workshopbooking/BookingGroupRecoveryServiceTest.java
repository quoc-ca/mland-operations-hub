package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupView;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.1 FR-048 (decision 2026-11-13): group read, lookup, Magic Link, pending-hold release, resend and incidents. */
class BookingGroupRecoveryServiceTest {

    private static final String CODE = "GRP-REC00001";
    private static final String TOKEN = "raw-token-123";

    private BookingGroupRepository groups;
    private BookingGroupLineRepository lines;
    private BookingGroupMagicLinkRepository magicLinks;
    private GroupPaymentAttemptRepository attempts;
    private BookingAuditEvidenceRepository audit;
    private BookingGroupProjectionService projection;
    private GroupConfirmationService confirmation;
    private GroupNotificationService notifications;
    private WorkshopBookingAuthorizationService authorization;
    private BookingIdempotencyService idempotency;
    private BookingGroupRecoveryService service;
    private BookingGroup group;
    private final BookingIdempotencyClaim claim = BookingIdempotencyClaim.live(7L, 1L);
    private final BookingGroupView view = new BookingGroupView(CODE, "CONFIRMED", "VNPAY", 0, null, null, null, null,
            List.of(), List.of());

    @BeforeEach
    void setUp() {
        groups = mock(BookingGroupRepository.class);
        lines = mock(BookingGroupLineRepository.class);
        magicLinks = mock(BookingGroupMagicLinkRepository.class);
        attempts = mock(GroupPaymentAttemptRepository.class);
        audit = mock(BookingAuditEvidenceRepository.class);
        projection = mock(BookingGroupProjectionService.class);
        confirmation = mock(GroupConfirmationService.class);
        notifications = mock(GroupNotificationService.class);
        authorization = mock(WorkshopBookingAuthorizationService.class);
        idempotency = mock(BookingIdempotencyService.class);
        service = new BookingGroupRecoveryService(groups, lines, magicLinks, attempts, audit, projection, confirmation,
                notifications, authorization, idempotency, new ObjectMapper());
        ReflectionTestUtils.setField(service, "selfServiceResendLimit", 3);
        ReflectionTestUtils.setField(service, "selfServiceResendWindowSeconds", 3600L);
        ReflectionTestUtils.setField(service, "internalResendLimit", 5);
        ReflectionTestUtils.setField(service, "internalResendWindowSeconds", 900L);

        group = BookingGroup.builder().id(1L).groupCode(CODE).buyerMemberAccountId(42L)
                .contactEmail("Buyer@Example.com").contactPhone("0900000001")
                .groupStatus(BookingStatus.CONFIRMED).paymentMode(GroupPaymentMode.VNPAY).groupCollectionVnd(200_000)
                .holdExpiresAt(Instant.now().plusSeconds(600)).build();
        when(groups.findByGroupCode(CODE)).thenReturn(Optional.of(group));
        when(groups.findByGroupCodeForUpdate(CODE)).thenReturn(Optional.of(group));
        when(lines.findByGroupIdOrderByLineIndexAsc(1L)).thenReturn(List.of());
        when(projection.project(any(), anyBoolean())).thenReturn(view);
        when(magicLinks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static AccountSummary account(long id, ActorRole role) {
        return new AccountSummary(id, role, ActorStatus.ACTIVE, "x@example.com", Instant.now(), Instant.now());
    }

    private static int status(Throwable thrown) {
        return ((WorkshopBookingProblemException) thrown).getStatus().value();
    }

    // ---- reads ----

    @Test
    void theOwningMemberReadsTheGroupWithTicketReferences() {
        when(authorization.requireMemberOwnership("Bearer m", null)).thenReturn(account(42L, ActorRole.MEMBER));

        assertThat(service.readOwned("Bearer m", CODE)).isSameAs(view);
        verify(projection).project(group, true);
    }

    @Test
    void anotherMemberGetsNotFoundNotForbidden() {
        when(authorization.requireMemberOwnership("Bearer o", null)).thenReturn(account(99L, ActorRole.MEMBER));

        assertThatThrownBy(() -> service.readOwned("Bearer o", CODE))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verify(projection, never()).project(any(), anyBoolean());
    }

    @Test
    void theMemberListSkipsGroupsWhoseHoldExpiredMoreThanADayAgo() {
        when(authorization.requireMemberOwnership("Bearer m", null)).thenReturn(account(42L, ActorRole.MEMBER));
        BookingGroup oldExpired = BookingGroup.builder().id(2L).groupCode("GRP-OLD").groupStatus(BookingStatus.EXPIRED)
                .holdExpiresAt(Instant.now().minusSeconds(90_000)).build();
        BookingGroup recentExpired = BookingGroup.builder().id(3L).groupCode("GRP-NEW").groupStatus(BookingStatus.EXPIRED)
                .holdExpiresAt(Instant.now().minusSeconds(600)).build();
        when(groups.findByBuyerMemberAccountIdOrderByCreatedAtDesc(eq(42L), any()))
                .thenReturn(List.of(group, oldExpired, recentExpired));

        assertThat(service.listOwned("Bearer m")).hasSize(2);
        verify(projection, never()).project(eq(oldExpired), anyBoolean());
    }

    @Test
    void guestLookupMatchesTheEmailIgnoringCaseAndHidesTicketReferences() {
        assertThat(service.lookupGuest(CODE, " buyer@example.COM ", null)).isSameAs(view);
        verify(projection).project(group, false);
    }

    @Test
    void guestLookupWithAWrongProofOrTwoProofsIsRefused() {
        assertThatThrownBy(() -> service.lookupGuest(CODE, null, "0911111111"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        assertThatThrownBy(() -> service.lookupGuest(CODE, "buyer@example.com", "0900000001"))
                .isInstanceOf(BookingValidationException.class);
        assertThatThrownBy(() -> service.lookupGuest("GRP-UNKNOWN", "buyer@example.com", null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void anActiveMagicLinkIsExchangedForTheGroupView() {
        when(magicLinks.findByTokenHash(GroupMagicLinkTokens.hash(TOKEN))).thenReturn(Optional.of(link(
                MagicLinkLifecycleStatus.ACTIVE, Instant.now().plusSeconds(3600))));

        assertThat(service.exchangeMagicLink(TOKEN)).isSameAs(view);
        verify(projection).project(group, false);
    }

    @Test
    void aRevokedOrExpiredMagicLinkAnswersNotFound() {
        when(magicLinks.findByTokenHash(GroupMagicLinkTokens.hash(TOKEN))).thenReturn(Optional.of(link(
                MagicLinkLifecycleStatus.REVOKED, Instant.now().plusSeconds(3600))));
        assertThatThrownBy(() -> service.exchangeMagicLink(TOKEN)).satisfies(e -> assertThat(status(e)).isEqualTo(404));

        when(magicLinks.findByTokenHash(GroupMagicLinkTokens.hash(TOKEN))).thenReturn(Optional.of(link(
                MagicLinkLifecycleStatus.ACTIVE, Instant.now().minusSeconds(1))));
        assertThatThrownBy(() -> service.exchangeMagicLink(TOKEN)).satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    // ---- pending-hold release ----

    @Test
    void aGuestWithTheRightPhoneReleasesTheGroupAndTheClaimRecords204() {
        group.setGroupStatus(BookingStatus.PENDING_PAYMENT);
        when(attempts.findByGroupIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        service.releasePendingHold(CODE, new BookingGroupRecoveryService.Actor(null), null, "0900000001",
                "Đổi ý", "corr", claim);

        verify(confirmation).releasePendingGroupByBuyer(eq(group), eq(false), anyString(), eq("corr"));
        verify(idempotency).completeClaimWithinTransaction(7L, 1L, 204, "");
        ArgumentCaptor<BookingAuditEvidence> saved = ArgumentCaptor.forClass(BookingAuditEvidence.class);
        verify(audit).save(saved.capture());
        assertThat(saved.getValue().getActorType()).isEqualTo("GUEST");
        assertThat(saved.getValue().getActionReason()).doesNotContain("0900000001");
    }

    @Test
    void releaseTellsTheGroupServiceWhenAPaymentAttemptExists() {
        group.setGroupStatus(BookingStatus.PENDING_PAYMENT);
        when(attempts.findByGroupIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(new GroupPaymentAttempt()));
        BookingGroupRecoveryService.Actor member = new BookingGroupRecoveryService.Actor(account(42L, ActorRole.MEMBER));
        when(authorization.isOwner(member.account(), 42L)).thenReturn(true);
        doThrow(new WorkshopBookingProblemException(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_EXISTS", "x"))
                .when(confirmation).releasePendingGroupByBuyer(eq(group), eq(true), anyString(), anyString());

        assertThatThrownBy(() -> service.releasePendingHold(CODE, member, null, null, "r", "corr", claim))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(idempotency, never()).completeClaimWithinTransaction(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void aMemberWhoIsNotTheBuyerCannotReleaseAndLearnsNothing() {
        BookingGroupRecoveryService.Actor other = new BookingGroupRecoveryService.Actor(account(99L, ActorRole.MEMBER));
        when(authorization.isOwner(other.account(), 42L)).thenReturn(false);

        assertThatThrownBy(() -> service.releasePendingHold(CODE, other, null, null, "r", "corr", claim))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verifyNoInteractions(confirmation);
    }

    // ---- confirmation resend ----

    @Test
    void aGuestResendReusesTheActiveLinkAndGoesOnlyToTheStoredContact() {
        when(notifications.findActiveAccessLink(group)).thenReturn("/workshop-booking/access#abc");

        var outcome = service.resendConfirmation(CODE, new BookingGroupRecoveryService.Actor(null),
                "buyer@example.com", null, "corr", claim);

        assertThat(outcome.outcome()).isEqualTo("QUEUED");
        verify(confirmation).resendConfirmationEmail(group, "/workshop-booking/access#abc", "corr");
        verify(magicLinks, never()).save(any());
    }

    @Test
    void theFourthSelfServiceResendInAnHourIsRateLimited() {
        when(audit.countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(eq(CODE),
                eq("CONFIRMATION_RESEND"), eq("ACCEPTED"), eq(List.of("GUEST", "MEMBER")), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.resendConfirmation(CODE, new BookingGroupRecoveryService.Actor(null),
                "buyer@example.com", null, "corr", claim))
                .satisfies(e -> assertThat(status(e)).isEqualTo(429));
        verify(confirmation, never()).resendConfirmationEmail(any(), any(), any());
    }

    @Test
    void staffOutsideTheLocationScopeCannotResend() {
        BookingGroupRecoveryService.Actor staff = new BookingGroupRecoveryService.Actor(account(5L, ActorRole.STAFF));
        when(authorization.isStaff(staff.account())).thenReturn(true);
        when(authorization.canResendConfirmation(staff.account(), null)).thenReturn(false);

        assertThatThrownBy(() -> service.resendConfirmation(CODE, staff, null, null, "corr", claim))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void aPendingGroupHasNoConfirmationToResend() {
        group.setGroupStatus(BookingStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> service.resendConfirmation(CODE, new BookingGroupRecoveryService.Actor(null),
                "buyer@example.com", null, "corr", claim))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    // ---- Magic Link incidents ----

    @Test
    void aManagerRotationRevokesTheOldLinkKeepsItsExpiryAndEmailsANewOne() {
        BookingGroupRecoveryService.Actor manager = new BookingGroupRecoveryService.Actor(account(1L, ActorRole.MANAGER));
        when(authorization.canHandleMagicLinkIncident(manager.account(), null)).thenReturn(true);
        Instant expiry = Instant.now().plusSeconds(86_400);
        BookingGroupMagicLink old = link(MagicLinkLifecycleStatus.ACTIVE, expiry);
        when(magicLinks.findByGroupIdAndLifecycleStatus(1L, MagicLinkLifecycleStatus.ACTIVE)).thenReturn(List.of(old));

        var outcome = service.handleMagicLinkIncident(CODE, manager, "ROTATE", "Link leaked", "corr", claim);

        assertThat(outcome.outcome()).isEqualTo("ROTATED");
        assertThat(old.getLifecycleStatus()).isEqualTo(MagicLinkLifecycleStatus.REVOKED);
        ArgumentCaptor<BookingGroupMagicLink> saved = ArgumentCaptor.forClass(BookingGroupMagicLink.class);
        verify(magicLinks, times(2)).save(saved.capture());
        BookingGroupMagicLink fresh = saved.getAllValues().get(1);
        assertThat(fresh.getLifecycleStatus()).isEqualTo(MagicLinkLifecycleStatus.ACTIVE);
        assertThat(fresh.getExpiresAt()).isEqualTo(expiry);
        verify(notifications).scrubAccessLinks(group);
        verify(notifications).queue(eq(group), isNull(), eq(GroupNotificationService.KIND_GROUP_MAGIC_LINK_ROTATED),
                eq("Buyer@Example.com"), anyString(), anyString(), anyList(), isNull(),
                startsWith(GroupMagicLinkTokens.ACCESS_PATH), eq("corr"));
    }

    @Test
    void aRevokeIssuesNoNewLink() {
        BookingGroupRecoveryService.Actor manager = new BookingGroupRecoveryService.Actor(account(1L, ActorRole.MANAGER));
        when(authorization.canHandleMagicLinkIncident(manager.account(), null)).thenReturn(true);
        BookingGroupMagicLink old = link(MagicLinkLifecycleStatus.ACTIVE, Instant.now().plusSeconds(60));
        when(magicLinks.findByGroupIdAndLifecycleStatus(1L, MagicLinkLifecycleStatus.ACTIVE)).thenReturn(List.of(old));

        assertThat(service.handleMagicLinkIncident(CODE, manager, "REVOKE", "r", "corr", claim).outcome())
                .isEqualTo("REVOKED");
        verify(magicLinks, times(1)).save(any());
        verify(notifications, never()).queue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void staffCannotHandleAMagicLinkIncident() {
        BookingGroupRecoveryService.Actor staff = new BookingGroupRecoveryService.Actor(account(5L, ActorRole.STAFF));
        when(authorization.canHandleMagicLinkIncident(staff.account(), null)).thenReturn(false);

        assertThatThrownBy(() -> service.handleMagicLinkIncident(CODE, staff, "REVOKE", "r", "corr", claim))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void theSweepExpiresRunOutLinksAndScrubsTheirUrl() {
        BookingGroupMagicLink ranOut = link(MagicLinkLifecycleStatus.ACTIVE, Instant.now().minusSeconds(5));
        when(magicLinks.findActiveExpired(any(), any())).thenReturn(List.of(ranOut));

        assertThat(service.expireMagicLinks()).isEqualTo(1);
        assertThat(ranOut.getLifecycleStatus()).isEqualTo(MagicLinkLifecycleStatus.EXPIRED);
        verify(notifications).scrubAccessLinks(group);
    }

    private BookingGroupMagicLink link(MagicLinkLifecycleStatus status, Instant expiresAt) {
        return BookingGroupMagicLink.builder().group(group).tokenHash(GroupMagicLinkTokens.hash(TOKEN))
                .lifecycleStatus(status).expiresAt(expiresAt).build();
    }
}
