package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import java.util.UUID;

/** Public-reference-only representation of a mapping-linked sync/recovery item. */
public record OtaReconciliationWorkItemView(UUID workItemId, UUID mappingId, String status,
                                             UUID correlationId, String safeSummary) {}
