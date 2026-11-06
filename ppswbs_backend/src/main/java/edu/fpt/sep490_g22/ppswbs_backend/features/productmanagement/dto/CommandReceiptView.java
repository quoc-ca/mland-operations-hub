package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto;

import java.time.Instant;
import java.util.UUID;

/** OpenAPI {@code CommandReceipt}: the committed minimal result of one command of the current actor. */
public record CommandReceiptView(UUID commandId, String action, ActionResult result, Instant committedAt) {
}
