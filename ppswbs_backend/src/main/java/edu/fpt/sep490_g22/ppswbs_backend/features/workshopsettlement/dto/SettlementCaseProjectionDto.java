package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;

public record SettlementCaseProjectionDto(String caseReference, String caseType,
                                          String status, String bookingCode,
                                          String correlationReference) {
    public static SettlementCaseProjectionDto from(SettlementCase settlementCase) {
        return new SettlementCaseProjectionDto(settlementCase.getCaseReference(), settlementCase.getCaseType(),
                settlementCase.getLifecycleStatus(), settlementCase.getBookingReference(),
                settlementCase.getCorrelationReference());
    }
}
