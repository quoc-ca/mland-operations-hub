package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementCustomerNotifier;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementNotificationHandoffService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementCustomerNotifierTest {
    private final SettlementNotificationHandoffService handoffs = mock(SettlementNotificationHandoffService.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final SettlementCustomerNotifier notifier = new SettlementCustomerNotifier(handoffs, booking);

    @Test
    void handsOneNoticeToTheBookingContactEmailWithoutAnyLink() {
        when(booking.notificationRecipient("BK-1")).thenReturn("buyer@example.test");
        when(handoffs.create(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("SETTLE-NOTIF-1");

        String reference = notifier.notifyVerified("BK-1", "REFUND_VERIFIED", 60_000L, "CASE-1", "CORR-1");

        assertThat(reference).isEqualTo("SETTLE-NOTIF-1");
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(handoffs).create(org.mockito.ArgumentMatchers.eq("CASE-1"), org.mockito.ArgumentMatchers.eq("REFUND_VERIFIED"),
                org.mockito.ArgumentMatchers.eq("CORR-1"), org.mockito.ArgumentMatchers.eq("buyer@example.test"), content.capture());
        assertThat(content.getValue()).contains("60.000").contains("BK-1").doesNotContain("http");
    }

    @Test
    void skipsTheNoticeWhenTheBookingHasNoEmail() {
        when(booking.notificationRecipient("BK-1")).thenReturn(" ");

        assertThat(notifier.notifyVerified("BK-1", "FINAL_PAYMENT_VERIFIED", 1L, "BILL-1", "CORR-1")).isNull();
        verify(handoffs, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void aFailedRecipientLookupNeverPropagatesToTheSettlement() {
        when(booking.notificationRecipient("BK-1")).thenThrow(new IllegalStateException("boom"));

        assertThat(notifier.notifyVerified("BK-1", "FINAL_PAYMENT_VERIFIED", 1L, "BILL-1", "CORR-1")).isNull();
        verify(handoffs, never()).create(any(), any(), any(), any(), any());
    }
}
