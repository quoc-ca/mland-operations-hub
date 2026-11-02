package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestLookupRequest {

    @NotBlank(message = "bookingCode is required")
    private String bookingCode;

    @Email(message = "contactEmail must be a valid email format")
    private String contactEmail;

    private String contactPhone;

    @AssertTrue(message = "Exactly one of contactEmail or contactPhone must be provided")
    public boolean isContactProofProvided() {
        boolean hasEmail = contactEmail != null && !contactEmail.isBlank();
        boolean hasPhone = contactPhone != null && !contactPhone.isBlank();
        return (hasEmail && !hasPhone) || (!hasEmail && hasPhone);
    }
}
