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
public class GuestBookingImportPreviewDto {
    private String memberEmail;
    private boolean emailVerified;
    private int candidateCount;
    private List<CandidateBookingDto> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateBookingDto {
        private Long id;
        private String bookingCode;
        private String contactEmail;
        private String bookingStatus;
        private String createdAt;
    }
}
