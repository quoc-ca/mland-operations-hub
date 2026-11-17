package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AddressType;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CommuneType;

import java.time.Instant;

public record AddressResponse(
    Long id,
    String recipientName,
    String phoneNumber,
    String provinceCode,
    String provinceName,
    String communeCode,
    String communeName,
    CommuneType communeType,
    String detailAddress,
    String directoryVersion,
    AddressType addressType,
    boolean isDefault,
    Long version,
    Instant createdAt,
    Instant updatedAt
) {}
