package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

public record MemberAddressSnapshot(
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
    AddressType addressType
) {}

