package edu.fpt.sep490_g22.ppswbs_backend.members.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPolicyResponseDto {
    private List<PolicyItemDto> policies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyItemDto {
        private String policyType;
        private String version;
        private String contentUri;
        private String effectiveAt;
    }
}
