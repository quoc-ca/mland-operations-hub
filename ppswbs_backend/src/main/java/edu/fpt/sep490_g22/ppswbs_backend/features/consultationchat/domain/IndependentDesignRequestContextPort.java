package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ContextOptionDto;

import java.util.List;
import java.util.Optional;

public interface IndependentDesignRequestContextPort {

    List<ContextOptionDto> findMemberIndependentDesignRequests(Long memberAccountId);

    Optional<ContextOptionDto> validateIndependentDesignRequest(
            Long memberAccountId, String contextReference, Integer contextVersion
    );
}
