package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import java.util.UUID;

public record OtaReconciliationWorkItemViewResponse(UUID workItemId, UUID mappingId, String status,
                                                     UUID correlationId, String safeSummary) {}
