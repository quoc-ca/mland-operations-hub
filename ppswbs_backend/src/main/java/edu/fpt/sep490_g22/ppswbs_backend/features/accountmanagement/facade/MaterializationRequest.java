package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MaterializationRequest {
    private final Long invitationId;
    private final int version;
    private final String eventReference;
    private final Long requestId;
    private final MaterializationPurpose purpose;
    private final Long attemptId;
    private final Long adminActorAccountId;
}
