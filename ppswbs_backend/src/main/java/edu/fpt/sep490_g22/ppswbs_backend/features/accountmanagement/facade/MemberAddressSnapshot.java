package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

public record MemberAddressSnapshot(
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
    Long version
) {}
