package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String messageKey;
    private String correlationId;
    private List<FieldErrorDetail> fieldErrors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldErrorDetail {
        private String field;
        private String messageKey;
    }
}
