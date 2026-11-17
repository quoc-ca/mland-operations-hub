package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService.DesignRequestViewDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
        @JsonProperty("answer")
        private String answer;

        @JsonProperty("advisory")
        @Builder.Default
        private boolean advisory = true;

        @JsonProperty("scope")
        private String scope;

        @JsonProperty("correlationId")
        private String correlationId;

        // Dual-compatibility getters
        @JsonProperty("message")
        public String getMessage() {
            return answer;
        }

        public void setMessage(String message) {
            this.answer = message;
        }

        @JsonProperty("providerCorrelationId")
        public String getProviderCorrelationId() {
            return correlationId;
        }

        public void setProviderCorrelationId(String providerCorrelationId) {
            this.correlationId = providerCorrelationId;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewQueueDto {
        @JsonProperty("items")
        private List<DesignRequestViewDto> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewDecisionRequest {
        @NotBlank(message = "Decision must not be blank")
        private String decision;

        @JsonAlias({"designVersion", "requestVersion"})
        @JsonProperty("requestVersion")
        private Integer requestVersion;

        @NotBlank(message = "Reason must not be blank")
        @Size(min = 1, max = 2000, message = "Reason length must be between 1 and 2000 characters")
        private String reason;

        public Integer getDesignVersion() {
            return requestVersion;
        }

        public void setDesignVersion(Integer designVersion) {
            this.requestVersion = designVersion;
        }
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
