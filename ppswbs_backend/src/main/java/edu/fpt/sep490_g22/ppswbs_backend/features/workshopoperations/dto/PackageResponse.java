package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageResponse {
    private Long packageId;
    private String name;
    private String description;
    private Long publicPrice;
    private Integer minParticipants;
    private Integer maxParticipants;
    private Integer requiredSessionCount;
    private Boolean sameLocationRequired;
    private Integer minimumSameDaySlotGap;
    private WorkshopPackageState state;
}
