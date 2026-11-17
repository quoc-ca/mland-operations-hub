package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressCreateRequest(
    @NotBlank(message = "Recipient name is required")
    @Size(max = 255, message = "Recipient name cannot exceed 255 characters")
    String recipientName,

    @NotBlank(message = "Phone number is required")
    String phoneNumber,

    @NotBlank(message = "Province code is required")
    String provinceCode,

    @NotBlank(message = "Commune code is required")
    String communeCode,

    @NotBlank(message = "Detail address is required")
    @Size(max = 255, message = "Detail address cannot exceed 255 characters")
    String detailAddress,

    AddressType addressType,

    Boolean isDefault
) {
    public AddressType resolvedAddressType() {
        return addressType != null ? addressType : AddressType.HOME;
    }

    public boolean resolvedIsDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
