package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Default {@link ManagerAlertRecipientSource} while FE-001 has no facade method for Manager emails: no recipient,
 * so no alert email is sent and the review still waits in the Manager queue. Replaced by an AccountFacade adapter
 * once FE-001 agrees; any other bean of the interface takes precedence.
 */
@Configuration
@Slf4j
class PendingManagerAlertRecipientSource {

    @Bean
    @ConditionalOnMissingBean(ManagerAlertRecipientSource.class)
    ManagerAlertRecipientSource pendingManagerAlertRecipients() {
        return () -> {
            log.warn("Manager alert email skipped: no Manager recipient source is connected yet (FE-001 pending)");
            return List.of();
        };
    }
}
