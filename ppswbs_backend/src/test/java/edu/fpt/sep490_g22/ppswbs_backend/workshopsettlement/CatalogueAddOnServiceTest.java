package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CatalogueAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementNotFoundException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogueAddOnServiceTest {
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementChargeRepository charges = mock(SettlementChargeRepository.class);
    private final WorkshopBookingFacade bookingFacade = mock(WorkshopBookingFacade.class);
    private final WorkshopOperationsFacade operationsFacade = mock(WorkshopOperationsFacade.class);
    private final CatalogueAddOnService service = new CatalogueAddOnService(bills, charges,
            new WorkshopBookingSettlementAdapter(bookingFacade), new WorkshopOperationsSettlementAdapter(operationsFacade));

    @Test void customerProofIsCheckedBeforeCataloguePriceIsRequested() {
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(false));
        assertThatThrownBy(() -> service.add("BK-1", null, "wrong@example.test", null, "ADDON-1"))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        verifyNoInteractions(operationsFacade, charges);
    }

    @Test void recordsTheScopedOperationsPriceAsAnImmutableVndCharge() {
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(SettlementFinalBill.builder()
                .billReference("BILL-1").bookingReference("BK-1").lifecycleStatus("OPEN").build()));
        when(bookingFacade.getConfirmedBookingFinancialProjection(any())).thenReturn(
                new ConfirmedBookingFinancialProjection("7", 100L, 100L, 50L, 50L, 1, java.util.List.of(), 9L));
        when(operationsFacade.getWorkshopAddOnPrice(any())).thenReturn(
                new WorkshopAddOnPriceSnapshot("ADDON-1", "Gift wrap", 15000L, "VND"));
        when(charges.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.add("BK-1", null, "guest@example.test", null, "ADDON-1");

        assertThat(result.getSourceCategory()).isEqualTo("CATALOGUE_ADD_ON");
        assertThat(result.getDescription()).isEqualTo("Gift wrap");
        assertThat(result.getAmountVnd()).isEqualTo(15000L);
        assertThat(result.getCurrencyCode()).isEqualTo("VND");
        assertThat(result.getReasonReference()).isEqualTo("CATALOGUE-ADDON:ADDON-1");
    }
}
