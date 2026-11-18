package edu.fpt.sep490_g22.ppswbs_backend.designreview.facade;

import java.util.List;
import java.util.Optional;

public interface DesignRequestConsultationFacade {

    List<DesignRequestConsultationOption> findMemberRequestsForConsultation(Long memberAccountId);

    Optional<DesignRequestConsultationOption> validateMemberRequestForConsultation(
            Long memberAccountId, String requestReference, Integer requestVersion
    );
}
