package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the catalogue background jobs: import batch cleanup (T100) and media cleanup (T047).
 * {@code catalogue.scheduling.enabled=false} turns them off; the tests call the jobs directly instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "catalogue.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogueSchedulingConfiguration {
}
