package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleSourceListResponse {
    @JsonProperty("eligibilityHandle")
    @JsonAlias({"guestEligibilityHandle", "eligibilityHandle"})
    private String guestEligibilityHandle;

    private Instant expiresAt;
    private String maskedContact;

    @JsonProperty("items")
    @JsonAlias({"sources", "items"})
    private List<EligibleSourceDto> sources;
}

