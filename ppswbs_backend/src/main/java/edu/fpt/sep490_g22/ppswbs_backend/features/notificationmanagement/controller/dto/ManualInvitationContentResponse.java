package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualInvitationContentResponse {
    private Long requestId;
    private int invitationVersion;
    private String subject;
    private String html;
    private String plainText;
    private Instant expiresAt;
    private Instant serverNow;
}
