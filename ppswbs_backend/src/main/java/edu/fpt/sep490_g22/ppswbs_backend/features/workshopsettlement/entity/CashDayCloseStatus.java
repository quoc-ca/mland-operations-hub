package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/** State of a branch's cash close (003.3 FR-036). A difference waits for a Manager before the day is closed. */
public enum CashDayCloseStatus {
    PENDING_MANAGER_CONFIRMATION,
    CLOSED
}
