package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.facade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEligibilityDto {
    private Long bookingId;
    private String bookingCode;
    private Long memberId;
    private Long packageId;
    private String packageName;
    private Long sessionId;
    private Long priceSnapshot;
    private String bookingStatus;
    private String paymentStatus;
    private boolean eligibleForDesign;
}
