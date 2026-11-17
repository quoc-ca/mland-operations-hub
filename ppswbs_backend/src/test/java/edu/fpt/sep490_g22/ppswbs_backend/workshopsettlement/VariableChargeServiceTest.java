package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VariableChargeServiceTest {
    @Mock SettlementFinalBillRepository bills;
    @Mock SettlementChargeRepository charges;

    @Test void storesPositiveWholeVndChargeAgainstOpenBill() {
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(openBill()));
        when(charges.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = new VariableChargeService(bills, charges).add("BK-1", "extra material", 25_000L, "customer request", "OP-1");
        assertThat(result.getAmountVnd()).isEqualTo(25_000L);
        assertThat(result.getCurrencyCode()).isEqualTo("VND");
        assertThat(result.getSourceCategory()).isEqualTo("VARIABLE_INTERNAL");
        verify(charges).saveAndFlush(any());
    }

    @Test void rejectsNonPositiveChargeWithoutPersistence() {
        assertThatThrownBy(() -> new VariableChargeService(bills, charges).add("BK-1", "extra", 0L, "reason", "OP-1"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        verifyNoInteractions(charges);
    }

    @Test void rejectsClosedBill() {
        SettlementFinalBill bill = openBill(); bill.setLifecycleStatus("FINALIZED");
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(bill));
        assertThatThrownBy(() -> new VariableChargeService(bills, charges).add("BK-1", "extra", 1L, "reason", "OP-1"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("FINAL_BILL_NOT_OPEN");
        verifyNoInteractions(charges);
    }

    private static SettlementFinalBill openBill() {
        return SettlementFinalBill.builder().billReference("BILL-1").bookingReference("BK-1")
                .postPromotionAmountVnd(80_000L).acceptedDepositAmountVnd(40_000L)
                .remainingAmountVnd(40_000L).currencyCode("VND").lifecycleStatus("OPEN").build();
    }
}
