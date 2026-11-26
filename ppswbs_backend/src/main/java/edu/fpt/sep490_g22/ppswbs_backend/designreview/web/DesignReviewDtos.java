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
    public static class AiQuotaDto {
        @JsonProperty("actor")
        private String actor;

        @JsonProperty("limit")
        private int limit;

        @JsonProperty("remaining")
        private int remaining;

        @JsonProperty("resetAt")
        private String resetAt;
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

        @JsonProperty("quota")
        private AiQuotaDto quota;

        @JsonProperty("guestSessionToken")
        private String guestSessionToken;

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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageProjectionDto {
        @JsonProperty("imageReference")
        private String imageReference;

        @JsonProperty("qualityStatus")
        private String qualityStatus;

        @JsonProperty("qualityReason")
        private String qualityReason;

        @JsonProperty("originalFilename")
        private String originalFilename;

        @JsonProperty("fileSizeBytes")
        private Long fileSizeBytes;
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

        @JsonAlias({"reason", "explanation"})
        @JsonProperty("explanation")
        @NotBlank(message = "Explanation/Reason must not be blank")
        @Size(min = 1, max = 2000, message = "Explanation length must be between 1 and 2000 characters")
        private String explanation;

        public String getReason() {
            return explanation;
        }

        public void setReason(String reason) {
            this.explanation = reason;
        }

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
        private String correlationId;
        private AiQuotaDto quota;
        private String guestSessionToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateCustomDesignRequestDto {
        @Size(max = 4000, message = "Mô tả không được vượt quá 4000 ký tự")
        private String description;
        private String designReference;
        private Integer designVersion;
        private List<String> imageReferences;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomDesignRevisionDto {
        @Size(max = 4000, message = "Mô tả không được vượt quá 4000 ký tự")
        private String description;
        private String designReference;
        private Integer designVersion;
        private List<String> imageReferences;
        private String clarificationResponse;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomDesignDecisionDto {
        @NotBlank(message = "Decision must not be blank")
        private String decision; // CLARIFICATION_REQUIRED, ACCEPTED, REJECTED
        @NotBlank(message = "Explanation must not be blank")
        @Size(min = 1, max = 2000, message = "Explanation must be between 1 and 2000 characters")
        private String explanation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuoteCommandDto {
        @NotBlank(message = "Total amount is required")
        private String totalAmount;
        @Builder.Default
        private String currency = "VND";
        @NotBlank(message = "Quote expiry is required")
        private String quoteExpiresAt;
        private String managerReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomDesignRequestProjectionDto {
        private String requestReference;
        private String status; // NEED_REVIEW, CLARIFICATION_REQUIRED, ACCEPTED, REJECTED
        private Integer version;
        private String explanation;
        private Object designSnapshot;
        private String quoteReference;
        private String description;
        private List<ImageProjectionDto> images;
        private String trackingLink;
        private String createdAt;
        private String updatedAt;
        private String customOrderReference;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuoteProjectionDto {
        private String quoteReference;
        private String status; // QUOTE_READY, FULL_PAYMENT_PENDING, PAID, QUOTE_EXPIRED, PAYMENT_EXPIRED, CANCELLED
        private String expiresAt;
        private String totalAmount;
        @Builder.Default
        private String currency = "VND";
        @Builder.Default
        private String paymentMode = "FULL_CUSTOM_ORDER_PAYMENT";
        private Integer quoteGeneration;
        private Boolean approvedByManager;
        private String managerReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomOrderProjectionDto {
        private String customOrderReference;
        private String status; // QUOTE_PENDING, FULL_PAYMENT_PENDING, PAID, IN_PRODUCTION, READY_FOR_FULFILMENT, CLOSED, QUOTE_EXPIRED, CANCELLED
        private QuoteProjectionDto quote;
        private Object designSnapshot;
        private String nextAction;
        private String productionStatus;
        private String fulfilmentStatus;
        private String createdAt;
        private String updatedAt;

        // Customer and delivery fields
        private Long memberAccountId;
        private String customerName;
        private String customerEmail;
        private String shippingAddress;

        // Linking fields to DesignRequest (DR)
        private String designRequestId;
        private String designRequestReference;
        private String designOriginalDescription;
        private List<ImageProjectionDto> designImages;
        private String designRef;
        private String staffExplanation;

        // Shipment & Carrier fields (Mock GHTK - Method 1B)
        private String trackingNumber;
        private String shipmentStatus;
        private String carrierService;
        private String shipmentPickedUpAt;
        private String shipmentDeliveredAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentAttemptProjectionDto {
        private String paymentAttemptReference;
        private String status; // INITIATED, PENDING, EXPIRED, PAID, FAILED
        private String paymentUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomRequestListProjectionDto {
        private List<CustomDesignRequestProjectionDto> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomOrderListProjectionDto {
        private List<CustomOrderProjectionDto> items;
    }
}
