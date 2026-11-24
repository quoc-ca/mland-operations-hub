package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

/** Consumer contract owned by FE-001; a source-module provider supplies the data. */
public interface RetailMemberReplacementReader {
    RetailReplacementInventory readForMemberReplacement(AccountId accountId);
}
