package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

/** Reference-only post-commit notification handoff. Never add recipient or delivery secrets here. */
public record SettlementNotificationReady(String notificationReference, String settlementReference,
                                          String notificationKind, String correlationReference) { }
