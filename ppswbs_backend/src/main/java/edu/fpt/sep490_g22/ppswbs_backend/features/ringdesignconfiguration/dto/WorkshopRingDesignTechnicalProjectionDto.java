package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Technical / Audit Projection for Workshop Ring Design (Feature-012).
 * Strictly accessible by ADMIN and MANAGER for technical audit, compliance, and observability.
 * Commercial data (estimatedPrice) and customer private details (owner PII, custom engraving texts/images) are masked/excluded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkshopRingDesignTechnicalProjectionDto {

    private String designId;
    private String packageReference;
    private Integer designVersion;
    private String designType;
    private String status;
    private String materialReference;
    private String shapeReference;
    private String ringSize;
    private BigDecimal difficultyScore;
    private String rulesVersion;
    private Boolean isLocked;
    private Instant createdAt;
    private Instant updatedAt;
}
