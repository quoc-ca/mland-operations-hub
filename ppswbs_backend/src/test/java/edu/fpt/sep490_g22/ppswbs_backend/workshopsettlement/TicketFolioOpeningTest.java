package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandTicketConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketConfirmedFolioConsumer;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketFolioOpeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 003.3 T093, FR-030: one OPEN folio per confirmed ticket of a booking group. The amounts come from 003.1's
 * immutable per-ticket snapshot, so this layer stores them and never recomputes a price.
 */
class TicketFolioOpeningTest {

    private static final String GROUP = "GRP-TEST0001";
    private static final String TICKET = "GRP-TEST0001-L0-T1";
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-12T10:05:00Z");

    private SettlementFinalBillRepository bills;
    private TicketFolioOpeningService service;

    @BeforeEach
    void setUp() {
        bills = mock(SettlementFinalBillRepository.class);
        when(bills.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new TicketFolioOpeningService(bills);
    }

    @Test
    void opensAnOpenFolioWithTheTicketsOwnPriceCollectionAndBalance() {
        var folio = service.openTicketFolio(GROUP, TICKET, 500_000, 200_000, 300_000, CONFIRMED_AT);

        assertThat(folio).isPresent();
        SettlementFinalBill saved = folio.orElseThrow();
        assertThat(saved.getBookingReference()).isEqualTo(TICKET);
        assertThat(saved.getGroupReference()).isEqualTo(GROUP);
        assertThat(saved.getPostPromotionAmountVnd()).isEqualTo(500_000);
        assertThat(saved.getAcceptedDepositAmountVnd()).isEqualTo(200_000);
        assertThat(saved.getRemainingAmountVnd()).isEqualTo(300_000);
        assertThat(saved.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());
        assertThat(saved.getFrozenAt()).isEqualTo(CONFIRMED_AT);
        assertThat(saved.getCurrencyCode()).isEqualTo("VND");
        assertThat(saved.getBillReference()).startsWith("SETTLE-BILL-");
    }

    @Test
    void aTicketThatPaidInFullAtBookingHasNothingLeftToCollect() {
        var folio = service.openTicketFolio(GROUP, TICKET, 150_000, 150_000, 0, CONFIRMED_AT);

        assertThat(folio.orElseThrow().getRemainingAmountVnd()).isZero();
    }

    @Test
    void aFreeTicketOpensItsFolioWithNoPaymentEvidence() {
        var folio = service.openTicketFolio(GROUP, TICKET, 0, 0, 0, CONFIRMED_AT);

        SettlementFinalBill saved = folio.orElseThrow();
        assertThat(saved.getPostPromotionAmountVnd()).isZero();
        assertThat(saved.getAcceptedDepositAmountVnd()).isZero();
        assertThat(saved.getRemainingAmountVnd()).isZero();
        assertThat(saved.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());
    }

    @Test
    void aTicketOfADeferredGroupCarriesItsWholePriceToCheckout() {
        var folio = service.openTicketFolio(GROUP, TICKET, 3_000, 0, 3_000, CONFIRMED_AT);

        SettlementFinalBill saved = folio.orElseThrow();
        assertThat(saved.getAcceptedDepositAmountVnd()).isZero();
        assertThat(saved.getRemainingAmountVnd()).isEqualTo(3_000);
    }

    @Test
    void aReplayedConfirmationReturnsTheFolioThatAlreadyExists() {
        SettlementFinalBill existing = SettlementFinalBill.builder().bookingReference(TICKET).build();
        when(bills.findByBookingReference(TICKET)).thenReturn(Optional.of(existing));

        var folio = service.openTicketFolio(GROUP, TICKET, 500_000, 200_000, 300_000, CONFIRMED_AT);

        assertThat(folio).containsSame(existing);
        verify(bills, never()).saveAndFlush(any());
    }

    @Test
    void twoDeliveriesThatRaceEndWithTheWinnersFolio() {
        SettlementFinalBill winner = SettlementFinalBill.builder().bookingReference(TICKET).build();
        when(bills.findByBookingReference(TICKET)).thenReturn(Optional.empty(), Optional.of(winner));
        when(bills.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        var folio = service.openTicketFolio(GROUP, TICKET, 500_000, 200_000, 300_000, CONFIRMED_AT);

        assertThat(folio).containsSame(winner);
    }

    @Test
    void amountsThatDoNotAddUpOpenNoFolio() {
        assertThat(service.openTicketFolio(GROUP, TICKET, 500_000, 200_000, 299_999, CONFIRMED_AT)).isEmpty();
        assertThat(service.openTicketFolio(GROUP, TICKET, 100_000, 200_000, 0, CONFIRMED_AT)).isEmpty();
        assertThat(service.openTicketFolio(GROUP, TICKET, -1, 0, 0, CONFIRMED_AT)).isEmpty();
        assertThat(service.openTicketFolio(GROUP, TICKET, 100_000, -1, 100_001, CONFIRMED_AT)).isEmpty();
        verify(bills, never()).saveAndFlush(any());
    }

    @Test
    void aTicketWithoutAReferenceOpensNoFolio() {
        assertThat(service.openTicketFolio(GROUP, null, 0, 0, 0, CONFIRMED_AT)).isEmpty();
        assertThat(service.openTicketFolio(GROUP, "   ", 0, 0, 0, CONFIRMED_AT)).isEmpty();
        verifyNoInteractions(bills);
    }

    @Test
    void aMissingConfirmationTimeDoesNotStopTheFolio() {
        var folio = service.openTicketFolio(GROUP, TICKET, 150_000, 150_000, 0, null);

        assertThat(folio.orElseThrow().getFrozenAt()).isNotNull();
    }

    @Test
    void theConsumerOpensOneFolioPerTicketEventAndIgnoresAnEmptyOne() {
        TicketFolioOpeningService opening = mock(TicketFolioOpeningService.class);
        TicketConfirmedFolioConsumer consumer = new TicketConfirmedFolioConsumer(opening);

        consumer.onTicketConfirmed(new MlandTicketConfirmedEvent(GROUP, TICKET, 500_000, 200_000, 300_000,
                CONFIRMED_AT));
        consumer.onTicketConfirmed(new MlandTicketConfirmedEvent(GROUP, "GRP-TEST0001-L0-T2", 0, 0, 0,
                CONFIRMED_AT));
        consumer.onTicketConfirmed(null);
        consumer.onTicketConfirmed(new MlandTicketConfirmedEvent(GROUP, null, 0, 0, 0, CONFIRMED_AT));

        ArgumentCaptor<String> ticketReferences = ArgumentCaptor.forClass(String.class);
        verify(opening, times(2)).openTicketFolio(eq(GROUP), ticketReferences.capture(), anyLong(), anyLong(),
                anyLong(), any());
        assertThat(ticketReferences.getAllValues()).containsExactly(TICKET, "GRP-TEST0001-L0-T2");
    }
}
