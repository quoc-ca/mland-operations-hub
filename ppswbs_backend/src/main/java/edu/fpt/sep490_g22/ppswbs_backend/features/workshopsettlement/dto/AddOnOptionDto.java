package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

/** One fixed-price catalogue add-on Staff/Manager may add to the booking's OPEN folio. */
public record AddOnOptionDto(String addOnReference, String description, long amountVnd) { }
