package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record CancelConfirmedBookingCommand(
        String bookingReference,
        String cancellationCaseReference,
        String operationReference,
        String correlationReference
) {}
