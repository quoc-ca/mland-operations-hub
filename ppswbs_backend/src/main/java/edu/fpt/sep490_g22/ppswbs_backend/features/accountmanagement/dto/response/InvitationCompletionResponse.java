package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCompletionResponse {
    private Long accountId;
    private String role; // STAFF, MANAGER
    private String status; // ACTIVE
    private String homeRoute; // /staff, /manager
}
