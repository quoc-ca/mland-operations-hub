package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables application background jobs outside test contexts.
 *
 * <p>Tests invoke scheduler services directly unless they explicitly provide
 * their own {@link EnableScheduling} configuration.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ApplicationSchedulingConfiguration {
}
