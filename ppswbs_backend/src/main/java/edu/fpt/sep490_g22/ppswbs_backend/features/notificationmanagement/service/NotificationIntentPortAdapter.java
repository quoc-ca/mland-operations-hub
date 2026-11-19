package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.notification.NotificationIntentPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationIntentResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.SubmitNotificationIntentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Implements the {@code common} port by delegating to this module's own facade (see {@link NotificationIntentPort}). */
@Component
@RequiredArgsConstructor
public class NotificationIntentPortAdapter implements NotificationIntentPort {

    private final NotificationFacade facade;

    @Override
    public boolean submitIntent(String businessEventReference, String templateCode, String recipientEmail,
                                Map<String, Object> businessDataSnapshot) {
        NotificationIntentResult result = facade.submitNotificationIntent(SubmitNotificationIntentCommand.builder()
                .businessEventReference(businessEventReference)
                .templateCode(templateCode)
                .recipientEmail(recipientEmail)
                .businessDataSnapshot(businessDataSnapshot)
                .build());
        return result != null && result.getStatus() != NotificationIntentResult.Status.OPERATIONAL_FAILURE;
    }
}
