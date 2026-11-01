package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import java.util.List;

public record FinalBillProjectionDto(String bookingCode, String status, String currency,
                                     long outstandingAmount, long paidAmount, long refundedAmount,
                                     List<CustomerVisibleChargeDto> charges) {
    public record CustomerVisibleChargeDto(String description, long amount) { }
}
