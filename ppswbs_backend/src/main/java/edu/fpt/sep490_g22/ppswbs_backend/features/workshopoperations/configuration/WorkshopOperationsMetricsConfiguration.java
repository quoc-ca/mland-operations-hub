package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process fallback registry so operations metrics (T119) can be recorded. The backend has no
 * actuator yet; a registry bean added later (actuator, Prometheus) replaces this one.
 */
@Configuration(proxyBeanMethods = false)
public class WorkshopOperationsMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry workshopOperationsMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
