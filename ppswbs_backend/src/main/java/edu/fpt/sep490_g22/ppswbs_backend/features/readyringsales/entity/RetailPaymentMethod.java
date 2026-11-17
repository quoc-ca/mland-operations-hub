package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

/** How a grouped checkout is paid (FR-08-041). */
public enum RetailPaymentMethod {
    /** One online VNPay payment for the combined total; the 5,000 VND minimum applies. */
    VNPAY,
    /** Cash at each branch counter when the goods are collected; store pickup only, no minimum. */
    CASH_AT_COUNTER
}
