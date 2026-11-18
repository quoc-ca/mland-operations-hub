package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AuditReviewDtos.OperationsAuditPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** T170 (d), FR-048 to FR-053: guard per role, filters, keyset page, whitelist, actor names, no write on read. */
class AuditReviewServiceTest {

    private static final String MANAGER = "Bearer test-uid:manager";
    private static final String STAFF = "Bearer test-uid:staff";
    private static final String ADMIN = "Bearer test-uid:admin";
    private static final String MEMBER = "Bearer test-uid:member";

    private final WorkshopOperationsAuthorizationService authorization = mock(WorkshopOperationsAuthorizationService.class);
    private final OperationsDeniedAttemptRecorder deniedRecorder = mock(OperationsDeniedAttemptRecorder.class);
    private final OperationsAuditEventRepository repository = mock(OperationsAuditEventRepository.class);
    private final AccountFacade accounts = mock(AccountFacade.class);
    private final AuditReviewService service = new AuditReviewService(authorization, deniedRecorder, repository,
            new AuditMetadataWhitelist(), new AuditCursorCodec(), accounts);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The recorder runs the guarded operation and rethrows, as the real one does.
        when(deniedRecorder.guard(any(), any(), any(), any(), any())).thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
        when(authorization.requireGlobalManager(MANAGER)).thenReturn(account(1L, ActorRole.MANAGER));
        when(authorization.requireGlobalManager(STAFF)).thenThrow(new WorkshopForbiddenException("Access requires MANAGER role"));
        when(authorization.requireGlobalManager(ADMIN)).thenThrow(new WorkshopForbiddenException("Access requires MANAGER role"));
        when(authorization.requireGlobalManager(MEMBER)).thenThrow(new WorkshopForbiddenException("Access requires MANAGER role"));
        when(authorization.requireGlobalManager(null)).thenThrow(new WorkshopUnauthorizedException("Missing Authorization"));
        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    @Test
    void staffAdminAndMemberAreRefusedAndNoDataIsRead() {
        for (String caller : new String[] {STAFF, ADMIN, MEMBER}) {
            assertThatThrownBy(() -> read(caller, null, null, null))
                    .as(caller).isInstanceOf(WorkshopForbiddenException.class);
        }
        verifyNoInteractions(repository, accounts);
        verify(deniedRecorder, times(3)).guard(eq(AuditReviewService.DENIED_ACTION), isNull(), any(), eq("corr-1"), any());
    }

    @Test
    void anonymousCallerIsRefusedAsUnauthenticated() {
        assertThatThrownBy(() -> read(null, null, null, null)).isInstanceOf(WorkshopUnauthorizedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void aRefusedCallerGetsForbiddenEvenWithBadParameters() {
        assertThatThrownBy(() -> service.readOperationsEvents(STAFF, "corr-1", null, "BOGUS", "x", null, "nope",
                null, "!!!", "0")).isInstanceOf(WorkshopForbiddenException.class);
    }

    @Test
    void managerGetsOnePageNewestFirstWithNoAuditWrite() {
        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(event(30L, 7L, "{\"toState\":\"CANCELLED\",\"phone\":\"0900\"}"), event(20L, null, null)));
        when(accounts.findAccountFullName(7L)).thenReturn(Optional.of("Nguyen Van A"));

        OperationsAuditPage page = read(MANAGER, null, null, null);

        assertThat(page.items()).extracting("id").containsExactly(30L, 20L);
        assertThat(page.items().get(0).actor().displayName()).isEqualTo("Nguyen Van A");
        assertThat(page.items().get(0).metadata()).containsOnlyKeys("toState");
        assertThat(page.items().get(1).actor()).isNull();
        assertThat(page.items().get(1).metadata()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void asksForOneRowMoreThanTheLimitAndReturnsACursorOnlyWhenMoreExist() {
        List<OperationsAuditEvent> rows = new ArrayList<>();
        for (long id = 50; id >= 48; id--) {
            rows.add(event(id, null, null));
        }
        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(rows);

        OperationsAuditPage page = service.readOperationsEvents(MANAGER, "corr-1", null, null, null, null, null,
                null, null, "2");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findPage(any(), any(), any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
        assertThat(page.items()).extracting("id").containsExactly(50L, 49L);
        assertThat(new AuditCursorCodec().decode(page.nextCursor())).isEqualTo(49L);
    }

    @Test
    void passesTheDecodedCursorAndNormalizedFiltersToTheQuery() {
        String cursor = new AuditCursorCodec().encode(40L);
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-10T00:00:00Z");

        service.readOperationsEvents(MANAGER, "corr-1", " WORKSHOP_PACKAGE_AVAILABILITY_CHANGED ", "DENIED", "12",
                "34", from.toString(), to.toString(), cursor, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findPage(eq("WORKSHOP_PACKAGE_AVAILABILITY_CHANGED"), eq("DENIED"), eq(12L), eq(34L),
                eq(from), eq(to), eq(40L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AuditReviewService.DEFAULT_LIMIT + 1);
    }

    @Test
    void defaultViewIsTheLastSevenDays() {
        Instant before = Instant.now();

        service.readOperationsEvents(MANAGER, "corr-1", null, null, null, null, null, null, null, null);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findPage(isNull(), isNull(), isNull(), isNull(), from.capture(), isNull(), isNull(),
                any(Pageable.class));
        assertThat(from.getValue()).isBetween(before.minus(Duration.ofDays(7)), Instant.now().minus(Duration.ofDays(7)));
    }

    @Test
    void badParametersAreValidationErrors() {
        for (String[] bad : new String[][] {
                // limit, from, to, cursor, outcome
                {"0", null, null, null, null}, {"101", null, null, null, null}, {"abc", null, null, null, null},
                {null, "2026-11-10T00:00:00Z", "2026-11-01T00:00:00Z", null, null},
                {null, "yesterday", null, null, null}, {null, null, null, "!!!", null},
                {null, null, null, null, "WHATEVER"}}) {
            assertThatThrownBy(() -> service.readOperationsEvents(MANAGER, "corr-1", null, bad[4], null, null,
                    bad[1], bad[2], bad[3], bad[0]))
                    .as("%s", List.of(java.util.Arrays.stream(bad).map(String::valueOf).toArray()))
                    .isInstanceOf(WorkshopValidationException.class);
        }
        assertThatThrownBy(() -> service.readOperationsEvents(MANAGER, "corr-1", "x".repeat(81), null, null, null,
                null, null, null, null)).isInstanceOf(WorkshopValidationException.class);
        assertThatThrownBy(() -> service.readOperationsEvents(MANAGER, "corr-1", null, null, "abc", null,
                null, null, null, null)).isInstanceOf(WorkshopValidationException.class);
    }

    @Test
    void looksUpEachDistinctActorOnceAndFallsBackToTheIdWhenTheLookupFailsOrIsEmpty() {
        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(
                event(5L, 7L, null), event(4L, 7L, null), event(3L, 8L, null), event(2L, 9L, null)));
        when(accounts.findAccountFullName(7L)).thenReturn(Optional.of("Nguyen Van A"));
        when(accounts.findAccountFullName(8L)).thenReturn(Optional.empty());
        when(accounts.findAccountFullName(9L)).thenThrow(new IllegalStateException("account service down"));

        OperationsAuditPage page = read(MANAGER, null, null, null);

        verify(accounts, times(1)).findAccountFullName(7L);
        verify(accounts, times(1)).findAccountFullName(8L);
        verify(accounts, times(1)).findAccountFullName(9L);
        assertThat(page.items().get(0).actor().displayName()).isEqualTo("Nguyen Van A");
        assertThat(page.items().get(1).actor().displayName()).isEqualTo("Nguyen Van A");
        assertThat(page.items().get(2).actor().accountId()).isEqualTo(8L);
        assertThat(page.items().get(2).actor().displayName()).isNull();
        assertThat(page.items().get(3).actor().accountId()).isEqualTo(9L);
        assertThat(page.items().get(3).actor().displayName()).isNull();
    }

    private OperationsAuditPage read(String caller, String action, String outcome, String limit) {
        return service.readOperationsEvents(caller, "corr-1", action, outcome, null, null, null, null, null, limit);
    }

    private static AccountSummary account(Long id, ActorRole role) {
        return new AccountSummary(id, role, ActorStatus.ACTIVE, "x@example.com", Instant.now(), Instant.now());
    }

    private static OperationsAuditEvent event(Long id, Long actorId, String metadata) {
        return OperationsAuditEvent.builder().id(id).actorId(actorId).sessionId(null)
                .action("WORKSHOP_PACKAGE_AVAILABILITY_CHANGED").outcome("SUCCESS").correlationId("c-" + id)
                .metadata(metadata).occurredAt(Instant.parse("2026-11-17T10:00:00Z")).build();
    }
}
