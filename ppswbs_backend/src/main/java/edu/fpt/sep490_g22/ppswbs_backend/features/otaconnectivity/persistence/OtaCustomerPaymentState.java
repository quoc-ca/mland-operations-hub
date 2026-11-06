package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

/** Customer payment outcome reported by an OTA; it is not an Mland payout state. */
public enum OtaCustomerPaymentState {
    PAID_ON_CHANNEL,
    REFUND_REQUESTED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
