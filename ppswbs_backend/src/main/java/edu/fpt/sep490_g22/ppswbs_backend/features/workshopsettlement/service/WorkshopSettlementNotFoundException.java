package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

public class WorkshopSettlementNotFoundException extends RuntimeException {
    public WorkshopSettlementNotFoundException() { super("RESOURCE_NOT_FOUND"); }
}
