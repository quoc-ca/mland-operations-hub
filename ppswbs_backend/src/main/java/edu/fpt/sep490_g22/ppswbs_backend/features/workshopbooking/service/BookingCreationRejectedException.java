package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public class BookingCreationRejectedException extends RuntimeException {
    private final String code;
    public BookingCreationRejectedException(String code) { super("The Package or session selection is no longer available"); this.code = code; }
    public String getCode() { return code; }
}
