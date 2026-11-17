package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

/**
 * One store-pickup branch order on the Staff pickup desk: the member-safe order facts, the raw handover facts
 * Staff act on, and the counter cash collection once confirmed.
 */
public record StaffRetailPickupView(RetailOrderHistoryView order, String fulfilmentState, boolean pickupReadyConfirmed,
                                    CheckoutDtos.CashCollectionView cashCollection) { }
