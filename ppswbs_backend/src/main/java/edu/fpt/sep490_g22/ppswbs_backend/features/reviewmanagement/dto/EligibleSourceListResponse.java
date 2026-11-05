package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleSourceListResponse {
    @JsonProperty("eligibilityHandle")
    private String guestEligibilityHandle;
    private Instant expiresAt;
    private String maskedContact;
    @JsonProperty("items")
    private List<EligibleSourceDto> sources;

}

