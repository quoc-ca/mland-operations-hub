package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

/** State of one capacity hold of a booking group line (003.1 T092; committed or released by T093). */
public enum LineHoldStatus {
    RESERVED,
    COMMITTED,
    RELEASED
}
