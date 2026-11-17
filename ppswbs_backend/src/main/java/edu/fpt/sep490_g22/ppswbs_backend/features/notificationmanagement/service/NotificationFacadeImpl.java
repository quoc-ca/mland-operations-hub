package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationIntentResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationSummaryDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.SubmitNotificationIntentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationFacadeImpl implements NotificationFacade {

    private final NotificationRequestService notificationRequestService;

    @Override
    public NotificationIntentResult submitNotificationIntent(SubmitNotificationIntentCommand command) {
        return notificationRequestService.submitNotificationIntent(command);
    }

    @Override
    public Optional<NotificationSummaryDto> findNotificationSummary(String businessEventReference, String templateCode) {
        return notificationRequestService.findNotificationSummary(businessEventReference, templateCode);
    }
}

