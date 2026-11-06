package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetailConfirmedUnpaidExpirySchedulingTest {
    @Test void schedulerInvokesTheExpiryJob() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test", java.util.Map.of("retail.started-expiry.delay-ms", "60000")));
            context.register(TestConfig.class);
            context.refresh();
            verify(context.getBean(RetailVariantHoldRepository.class), timeout(5000).atLeastOnce())
                .findStartedExpiryCandidates(any(), any(), any(), any());
        }
    }

    @Configuration
    @EnableScheduling
    static class TestConfig {
        @Bean RetailVariantHoldRepository holds() {
            var source = mock(RetailVariantHoldRepository.class);
            when(source.findStartedExpiryCandidates(any(), any(), any(), any())).thenReturn(List.of());
            return source;
        }
        @Bean RetailQueryReconciliationService reconciliation() { return mock(RetailQueryReconciliationService.class); }
        @Bean RetailConfirmedUnpaidExpiryJob job(RetailVariantHoldRepository holds, RetailQueryReconciliationService reconciliation) {
            return new RetailConfirmedUnpaidExpiryJob(holds, reconciliation);
        }
    }
}
