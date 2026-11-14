package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailUnstartedExpiryJob;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailUnstartedExpiryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetailUnstartedExpiryJobTest {
    @Test void continuesToNextCandidateWhenOneCheckoutFails() {
        var holds = mock(RetailVariantHoldRepository.class);
        var expiry = mock(RetailUnstartedExpiryService.class);
        var first = mock(RetailVariantHoldRepository.ExpiryCandidate.class);
        var second = mock(RetailVariantHoldRepository.ExpiryCandidate.class);
        when(first.getMemberId()).thenReturn(1L);
        when(first.getCheckoutId()).thenReturn("first");
        when(second.getMemberId()).thenReturn(2L);
        when(second.getCheckoutId()).thenReturn("second");
        when(holds.findUnstartedExpiryCandidates(any(), any(), any(), any())).thenReturn(List.of(first, second));
        when(expiry.expire(1L, "first")).thenThrow(new IllegalStateException("temporary failure"));
        new RetailUnstartedExpiryJob(holds, expiry).process();
        verify(expiry).expire(1L, "first");
        verify(expiry).expire(2L, "second");
    }
}
