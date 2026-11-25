package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCollisionResponse {
    private String normalizedEmail;
    private String outcome; // NONE, MEMBER_REVIEW, STAFF_MANAGER_MAINTENANCE, FIREBASE_ONLY, BLOCKED_ROLE
    private Long accountId;
}
