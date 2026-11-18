package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementAuditReviewDtos.SettlementAuditPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementAuditReviewDtos.SettlementAuditRow;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementAuditCursorCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementAuditQueryException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementAuditReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

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

/** T171, FR-048/FR-050/FR-052/FR-053: guard, filters, keyset page, actor split, reason text never in toString. */
class SettlementAuditReviewServiceTest {

    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(101L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of());

    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementBusinessAuditRepository repository = mock(SettlementBusinessAuditRepository.class);
    private final AccountFacade accounts = mock(AccountFacade.class);
    private final SettlementAuditCursorCodec codec = new SettlementAuditCursorCodec();
    private final SettlementAuditReviewService service =
            new SettlementAuditReviewService(authorization, repository, codec, accounts);

    private SettlementAuditPage read(CurrentAccountActor actor, String actionCode, String actorId, String from,
                                     String to, String cursor, String limit) {
        return service.readRecords(actor, actionCode, actorId, null, null, from, to, cursor, limit);
    }

    @Test
    void aNonManagerIsRefusedBeforeAnyDataIsRead() {
        when(authorization.requireManager(STAFF)).thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));

        assertThatThrownBy(() -> read(STAFF, null, null, null, null, null, null))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        // even with bad parameters a refused caller gets the refusal, not a validation answer
        assertThatThrownBy(() -> read(STAFF, null, "abc", "nope", null, "!!!", "0"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verifyNoInteractions(repository, accounts);
    }

    @Test
    void managerGetsRowsWithFullReasonAndSplitActorWithoutAnyWrite() {
        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(
                row(9L, "account:7", "mot ly do dai"), row(8L, "SYSTEM", null), row(7L, "55", "so thuong"), row(6L, null, null)));
        when(accounts.findAccountFullName(7L)).thenReturn(Optional.of("Nguyen Van A"));

        SettlementAuditPage page = read(MANAGER, null, null, null, null, null, null);

        assertThat(page.items()).extracting(SettlementAuditRow::id).containsExactly(9L, 8L, 7L, 6L);
        SettlementAuditRow first = page.items().get(0);
        assertThat(first.reasonText()).isEqualTo("mot ly do dai");
        assertThat(first.actor().accountId()).isEqualTo(7L);
        assertThat(first.actor().displayName()).isEqualTo("Nguyen Van A");
        assertThat(first.actorReference()).isEqualTo("account:7");
        assertThat(page.items().get(1).actor()).as("SYSTEM is shown as is").isNull();
        assertThat(page.items().get(1).actorReference()).isEqualTo("SYSTEM");
        assertThat(page.items().get(2).actor()).as("a plain number is not an account reference").isNull();
        assertThat(page.items().get(3).actorReference()).isNull();
        verify(repository, never()).save(any());
        verify(accounts, times(1)).findAccountFullName(7L);
    }

    @Test
    void nameLookupFailureOrMissingNameLeavesTheIdOnlyAndNeverFailsThePage() {
        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(
                row(3L, "account:7", null), row(2L, "account:8", null), row(1L, "account:7", null)));
        when(accounts.findAccountFullName(7L)).thenThrow(new IllegalStateException("down"));
        when(accounts.findAccountFullName(8L)).thenReturn(Optional.empty());

        SettlementAuditPage page = read(MANAGER, null, null, null, null, null, null);

        assertThat(page.items()).allSatisfy(item -> assertThat(item.actor().displayName()).isNull());
        verify(accounts, times(1)).findAccountFullName(7L);
        verify(accounts, times(1)).findAccountFullName(8L);
    }

    @Test
    void asksForOneRowMoreThanTheLimitAndReturnsACursorOnlyWhenMoreExist() {
        List<SettlementBusinessAudit> rows = new ArrayList<>(List.of(row(50L, null, null), row(49L, null, null), row(48L, null, null)));
        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(rows);

        SettlementAuditPage page = read(MANAGER, null, null, null, null, null, "2");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findPage(any(), any(), any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
        assertThat(page.items()).extracting(SettlementAuditRow::id).containsExactly(50L, 49L);
        assertThat(codec.decode(page.nextCursor())).isEqualTo(49L);

        when(repository.findPage(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(row(48L, null, null)));
        assertThat(read(MANAGER, null, null, null, null, null, "2").nextCursor()).isNull();
    }

    @Test
    void actorFilterMatchesTheStoredAccountReferenceAndTheCursorIsDecoded() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-10T00:00:00Z");

        service.readRecords(MANAGER, " REFUND_APPROVED ", "12", "REFUND_REQUEST", "RR-1", from.toString(),
                to.toString(), codec.encode(40L), null);

        verify(repository).findPage(eq("REFUND_APPROVED"), eq("account:12"), eq("REFUND_REQUEST"), eq("RR-1"),
                eq(from), eq(to), eq(40L), any(Pageable.class));
    }

    @Test
    void defaultViewIsTheLastSevenDays() {
        Instant before = Instant.now();

        read(MANAGER, null, null, null, null, null, null);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findPage(isNull(), isNull(), isNull(), isNull(), from.capture(), isNull(), isNull(), any(Pageable.class));
        assertThat(from.getValue()).isBetween(before.minusSeconds(7 * 86_400L + 5), Instant.now().minusSeconds(7 * 86_400L - 5));
    }

    @Test
    void badParametersAreValidationErrorsNamingTheField() {
        String[][] cases = {{"limit", "0"}, {"limit", "101"}, {"limit", "abc"}, {"cursor", "!!!"},
                {"actorId", "abc"}, {"from", "yesterday"}, {"to", "tomorrow"}};
        for (String[] bad : cases) {
            assertThatThrownBy(() -> service.readRecords(MANAGER, null, "actorId".equals(bad[0]) ? bad[1] : null, null,
                    null, "from".equals(bad[0]) ? bad[1] : null, "to".equals(bad[0]) ? bad[1] : null,
                    "cursor".equals(bad[0]) ? bad[1] : null, "limit".equals(bad[0]) ? bad[1] : null))
                    .as(bad[0] + "=" + bad[1])
                    .isInstanceOfSatisfying(SettlementAuditQueryException.class,
                            ex -> assertThat(ex.getField()).isEqualTo(bad[0]));
        }
        assertThatThrownBy(() -> read(MANAGER, null, null, "2026-11-10T00:00:00Z", "2026-11-01T00:00:00Z", null, null))
                .isInstanceOfSatisfying(SettlementAuditQueryException.class, ex -> assertThat(ex.getField()).isEqualTo("from"));
        assertThatThrownBy(() -> read(MANAGER, "x".repeat(65), null, null, null, null, null))
                .isInstanceOfSatisfying(SettlementAuditQueryException.class, ex -> assertThat(ex.getField()).isEqualTo("actionCode"));
    }

    @Test
    void theReasonTextNeverAppearsInToString() {
        SettlementAuditRow row = new SettlementAuditRow(1L, Instant.now(), "REFUND_APPROVED", "REFUND_REQUEST", "RR-1",
                null, "account:7", "corr", "ev", "SECRET personal reason");

        assertThat(row.toString()).doesNotContain("SECRET").doesNotContain("personal");
        assertThat(new SettlementAuditPage(List.of(row), null).toString()).doesNotContain("SECRET");
    }

    @Test
    void cursorCodecRoundTripsAndRejectsMalformedTokens() {
        assertThat(codec.decode(codec.encode(77L))).isEqualTo(77L);
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("  ")).isNull();
        for (String bad : new String[] {"!!!", raw("12"), raw("s1:abc"), raw("s1:0"), raw("a1:5"), "x".repeat(65)}) {
            assertThatThrownBy(() -> codec.decode(bad)).as(bad).isInstanceOf(SettlementAuditQueryException.class);
        }
    }

    private static String raw(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static SettlementBusinessAudit row(Long id, String actorReference, String reason) {
        return SettlementBusinessAudit.builder().id(id).auditReference("A-" + id).aggregateType("REFUND_REQUEST")
                .aggregateReference("RR-" + id).actionCode("REFUND_APPROVED").actorReference(actorReference)
                .correlationReference("corr-" + id).evidenceReference(null).reasonReference(reason)
                .createdAt(Instant.parse("2026-11-17T10:00:00Z")).build();
    }
}
