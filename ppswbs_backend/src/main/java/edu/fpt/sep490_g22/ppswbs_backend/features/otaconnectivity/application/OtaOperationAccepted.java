package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import java.util.UUID;

public record OtaOperationAccepted(UUID operationReference, UUID correlationId, String status) {}
