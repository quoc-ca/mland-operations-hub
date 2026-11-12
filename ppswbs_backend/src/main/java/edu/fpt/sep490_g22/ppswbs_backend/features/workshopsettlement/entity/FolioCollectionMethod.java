package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

/**
 * How one part of a folio balance is collected (003.3 FR-031). The checkout screen offers them in this
 * order: VNPay, cash, direct transfer.
 */
public enum FolioCollectionMethod {
    /** Gateway payment; verified only by signed IPN or QueryDR, never by Staff, and never under 5,000 VND. */
    VNPAY,
    /** Taken at the counter by Staff with collection authority, who records what was handed over and returned. */
    CASH,
    /** Direct transfer to the shop account, confirmed only after the money is seen in that account. */
    BANK_TRANSFER
}
