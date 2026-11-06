package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleSourceItem {
    private SourceType sourceType;
    private String sourceReference;
    private TargetType targetType;
    private String targetReference;
    private String targetName;
    /** Workshop package of the attended booking (null for retail). */
    private Long packageId;
    private Instant completedAt;
    private boolean eligible;
    private String reasonCode;
    private Long memberAccountId;
    private String contactEmail;
    private String contactPhone;
}

