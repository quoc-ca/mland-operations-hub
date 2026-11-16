package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementAbsorbedUnderchargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAbsorbedUndercharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** T135 and T156 (FR-057, CHK009): refusals, the folio-total cap and the one void, without a database. */
class AbsorbedUnderchargeServiceTest {

    private static final String KEY = "UNDERCHARGE-KEY-12345";
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(7L));

    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementAbsorbedUnderchargeRepository repository = mock(SettlementAbsorbedUnderchargeRepository.class);
    private final FolioBalanceService balances = mock(FolioBalanceService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private AbsorbedUnderchargeService service;
    private SettlementFinalBill folio;

    @BeforeEach
    void setUp() {
        service = new AbsorbedUnderchargeService(authorization, booking, bills, repository, balances, idempotency,
                mock(SettlementBusinessAuditRepository.class));
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(authorization.requireManager(STAFF)).thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));
        folio = SettlementFinalBill.builder().billReference("BILL-1").bookingReference("T-1").groupReference("G-1")
                .postPromotionAmountVnd(500_000L).lifecycleStatus("FINALIZED").build();
        when(bills.findByBookingReferenceForUpdate("T-1")).thenReturn(Optional.of(folio));
        when(bills.findByBookingReference("T-1")).thenReturn(Optional.of(folio));
        when(balances.confirmedCharges("BILL-1")).thenReturn(100_000L); // folio total = 600,000
        when(booking.getTicketFolioContext("T-1")).thenReturn(Optional.of(new TicketFolioContext("T-1", "G-1",
                "G-1-L1", "7", 7L, List.of(), null, null, null, 0L, "L1-T-1", 0L)));
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Supplier<String> action = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, action.get());
        });
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByUnderchargeReference(anyString())).thenAnswer(invocation -> {
            SettlementAbsorbedUndercharge saved = mockingDetails(repository).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("saveAndFlush"))
                    .map(i -> (SettlementAbsorbedUndercharge) i.getArgument(0)).reduce((a, b) -> b).orElseThrow();
            return Optional.of(saved);
        });
    }

    private void refusedForCap(long amount) {
        assertThatThrownBy(() -> service.record("T-1", KEY, "c", amount, "reason", MANAGER))
                .isInstanceOf(SettlementUnprocessableException.class).hasMessage("UNDERCHARGE_EXCEEDS_FOLIO_TOTAL");
    }

    @Test
    void staffIsRefused() {
        assertThatThrownBy(() -> service.record("T-1", KEY, "c", 1_000, "reason", STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> service.voidRecord("T-1", "U-1", KEY, "c", "reason", STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> service.list(7L, java.time.LocalDate.now(), java.time.LocalDate.now(), STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verifyNoInteractions(repository, idempotency);
    }

    @Test
    void aFolioThatIsNotFinalizedIsRefused() {
        for (String status : List.of("OPEN", "CLOSED")) {
            folio.setLifecycleStatus(status);
            assertThatThrownBy(() -> service.record("T-1", KEY, "c", 1_000, "reason", MANAGER))
                    .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("FOLIO_NOT_FINALIZED");
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void inputIsValidated() {
        assertThatThrownBy(() -> service.record("T-1", null, "c", 1_000, "reason", MANAGER))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> service.record("T-1", KEY, "c", 0, "reason", MANAGER))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> service.record("T-1", KEY, "c", 1_000, " ", MANAGER))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void theFolioTotalIsAllowedAndOneMoreIsRefused() {
        var dto = service.record("T-1", KEY, "c", 600_000, "reason", MANAGER);
        assertThat(dto.amountVnd()).isEqualTo("600000");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.locationId()).isEqualTo(7L);
        assertThat(dto.groupCode()).isEqualTo("G-1");
        assertThat(dto.recordedBy()).isEqualTo("10");

        refusedForCap(600_001);
    }

    @Test
    void activeRecordsOfOneTicketTogetherMayNotExceedTheTotal() {
        when(repository.sumActiveByTicketCode("T-1")).thenReturn(400_000L);

        service.record("T-1", KEY, "c", 200_000, "reason", MANAGER); // 600,000 exactly
        refusedForCap(200_001);
    }

    @Test
    void aVoidedRecordFreesItsAmount() {
        when(repository.sumActiveByTicketCode("T-1")).thenReturn(600_000L);
        refusedForCap(1);

        when(repository.sumActiveByTicketCode("T-1")).thenReturn(100_000L); // the voided 500,000 no longer counts
        assertThat(service.record("T-1", KEY, "c", 500_000, "reason", MANAGER).amountVnd()).isEqualTo("500000");
    }

    @Test
    void theRecordDoesNotChangeTheFolio() {
        service.record("T-1", KEY, "c", 1_000, "reason", MANAGER);

        verify(bills, never()).save(any());
        verify(bills, never()).saveAndFlush(any());
        assertThat(folio.getLifecycleStatus()).isEqualTo("FINALIZED");
        assertThat(folio.getPostPromotionAmountVnd()).isEqualTo(500_000L);
    }

    @Test
    void aVoidKeepsTheContentAndASecondVoidIsRefused() {
        SettlementAbsorbedUndercharge record = SettlementAbsorbedUndercharge.builder().underchargeReference("U-1")
                .ticketCode("T-1").billReference("BILL-1").locationReference(7L).amountVnd(50_000).reason("original")
                .recordedByAccountId(10L).status("ACTIVE").build();
        when(repository.findByUnderchargeReferenceForUpdate("U-1")).thenReturn(Optional.of(record));
        doReturn(Optional.of(record)).when(repository).findByUnderchargeReference("U-1");

        var voided = service.voidRecord("T-1", "U-1", KEY, "c", "Typed twice", MANAGER);

        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(voided.voidReason()).isEqualTo("Typed twice");
        assertThat(voided.voidedBy()).isEqualTo("10");
        assertThat(voided.amountVnd()).isEqualTo("50000");
        assertThat(voided.reason()).isEqualTo("original");
        assertThatThrownBy(() -> service.voidRecord("T-1", "U-1", "ANOTHER-KEY-1234567", "c", "again", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("UNDERCHARGE_ALREADY_VOIDED");
    }

    @Test
    void aRecordOfAnotherTicketIsNotFound() {
        SettlementAbsorbedUndercharge record = SettlementAbsorbedUndercharge.builder().underchargeReference("U-9")
                .ticketCode("T-OTHER").status("ACTIVE").build();
        when(repository.findByUnderchargeReferenceForUpdate("U-9")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.voidRecord("T-1", "U-9", KEY, "c", "reason", MANAGER))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }
}
