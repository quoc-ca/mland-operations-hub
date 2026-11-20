package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ContextOptionDto;

import java.util.List;
import java.util.Optional;

public interface WorkshopDesignContextPort {

    List<ContextOptionDto> findMemberWorkshopDesigns(Long memberAccountId);

    Optional<ContextOptionDto> validateWorkshopDesign(
            Long memberAccountId, String contextReference, Integer contextVersion
    );
}
