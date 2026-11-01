package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.util.List;

/** Product-management port for the canonical list of currently sellable locations. */
public interface SellingLocationPort {
    List<SellingLocation> activeSellingLocations();
}
