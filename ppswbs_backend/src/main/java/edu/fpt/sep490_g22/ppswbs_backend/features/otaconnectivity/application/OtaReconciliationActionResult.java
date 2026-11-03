package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import java.util.UUID;

/** Safe acknowledgement snapshot for a reconciliation action or its idempotent replay. */
public record OtaReconciliationActionResult(UUID workItemId, UUID mappingId, String status,
                                             UUID correlationId, String safeSummary) {}
