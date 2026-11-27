package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

/**
 * What Operations needs to know about a ticket's folio (003.2 FR-062). Declared here and implemented by 003.3
 * Settlement, which already depends on this module, so no dependency cycle appears.
 */
public interface TicketFolioStateQuery {

    /** True once the folio of this Operations ticket reference is finalized; false when open, closed or absent. */
    boolean isTicketFolioFinalized(String operationsTicketReference);
}
