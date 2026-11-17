package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.net.URI;
import java.util.List;

/**
 * Problem detail of a refused cart confirmation, plus the failing cart positions required by 003.1 FR-031.
 */
public record CartHoldRejectionResponse(
        URI type,
        String title,
        int status,
        String code,
        String detail,
        String correlationId,
        List<Integer> failingLines) {
}
