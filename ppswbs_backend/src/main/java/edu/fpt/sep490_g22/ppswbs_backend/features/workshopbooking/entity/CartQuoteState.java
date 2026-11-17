package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

/** A cart quote is used once: it becomes ACCEPTED when its booking group is created (003.1 FR-030). */
public enum CartQuoteState {
    OPEN,
    ACCEPTED
}
