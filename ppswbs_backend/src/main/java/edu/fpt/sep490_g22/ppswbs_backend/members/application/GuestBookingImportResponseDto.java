package edu.fpt.sep490_g22.ppswbs_backend.members.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestBookingImportResponseDto {
    private int linkedCount;
    private int skippedCount;
}
