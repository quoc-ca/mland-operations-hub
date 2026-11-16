package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the T133 branch back-fill once the application is up. It only touches rows whose branch is still NULL, so
 * after the first start it is a cheap no-op; a failure is logged and never stops startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CollectionBranchBackfillRunner implements ApplicationRunner {

    private final CollectionBranchBackfillService backfill;

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill.backfill();
        } catch (RuntimeException e) {
            log.warn("Collection branch back-fill failed and will be retried at the next start: {}", e.toString());
        }
    }
}
