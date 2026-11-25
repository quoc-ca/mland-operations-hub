package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RetailReplacementInventory;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.WorkshopReplacementInventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplacementReviewResponse {
    private Long accountId;
    private Long accountVersion;
    private String normalizedEmail;
    private Map<String, Object> profile;
    private List<Map<String, Object>> addresses;
    private RetailReplacementInventory retail;
    private WorkshopReplacementInventory workshop;
    private Instant generatedAt;
}
