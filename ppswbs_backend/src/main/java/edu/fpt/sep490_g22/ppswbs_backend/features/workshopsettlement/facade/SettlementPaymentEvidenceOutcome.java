package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

public record SettlementPaymentEvidenceOutcome(Status status, String reason) {
    public enum Status { ACCEPTED, REJECTED }
    public static SettlementPaymentEvidenceOutcome accepted() { return new SettlementPaymentEvidenceOutcome(Status.ACCEPTED, null); }
    public static SettlementPaymentEvidenceOutcome rejected(String reason) { return new SettlementPaymentEvidenceOutcome(Status.REJECTED, reason); }
}
