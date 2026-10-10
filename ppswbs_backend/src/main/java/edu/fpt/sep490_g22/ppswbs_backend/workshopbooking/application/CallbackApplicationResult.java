package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackApplicationResult {
    private WorkshopBooking booking;
    private boolean idempotentDuplicate;

    public static CallbackApplicationResult of(WorkshopBooking booking, boolean idempotentDuplicate) {
        return CallbackApplicationResult.builder()
                .booking(booking)
                .idempotentDuplicate(idempotentDuplicate)
                .build();
    }
}
