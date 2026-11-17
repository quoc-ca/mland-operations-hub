package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.DesignOptionsResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.EvaluationResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.SaveDesignVersionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.ValidationResultDto;

public interface DesignOptionRulesPort {

    DesignOptionsResponseDto loadPackageOptions(String packageReference, String branchReference);

    ValidationResultDto validateCompatibility(SaveDesignVersionRequest request, String branchReference);

    EvaluationResultDto evaluate(SaveDesignVersionRequest request);
}

