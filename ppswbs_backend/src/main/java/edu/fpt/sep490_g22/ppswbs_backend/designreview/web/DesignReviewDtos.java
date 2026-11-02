package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class DesignReviewDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiSupportRequest {
        @NotBlank(message = "Message must not be blank")
        @Size(min = 1, max = 2000, message = "Message length must be between 1 and 2000 characters")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiSupportResponse {
        private String message;
        private String scope;
        private String providerCorrelationId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewDecisionRequest {
        @NotBlank(message = "Decision must not be blank")
        private String decision;

        private Integer designVersion;

        @NotBlank(message = "Reason must not be blank")
        @Size(min = 1, max = 2000, message = "Reason length must be between 1 and 2000 characters")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
        private Long retryAfterSeconds;
    }
}
