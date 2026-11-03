package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AddressType;

import java.time.Instant;

public record AddressResponse(
    Long id,
    String recipientName,
    String phoneNumber,
    String provinceId,
    String provinceName,
    String districtId,
    String districtName,
    String wardId,
    String wardName,
    String hamletId,
    String hamletName,
    String detailAddress,
    AddressType addressType,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {}

