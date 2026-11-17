package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/**
 * Status of the 003.2 session-cancellation handoff record
 * ({@link SettlementCancellationCase}). It is not the FR-022b1 settlement-case
 * lifecycle; that is {@link SettlementCaseLifecycleStatus} on {@link SettlementCase}.
 */
public enum SettlementCaseStatus { OPEN, RESOLVED, REJECTED, FAILED }
