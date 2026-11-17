package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.util.List;

/**
 * SPI owned by productmanagement and implemented by the owner of the canonical branch list (003.2
 * workshopoperations, {@code workshop_locations}); contracts/interfaces.md "SellingLocationPort", decision A of
 * 2026-11-01. productmanagement never imports that module. In V1 every active branch sells every published product
 * (quantities stay per size), so the list does not depend on the product.
 *
 * <ul>
 *   <li>Returns only branches that are active now, each once; never a placeholder or a guessed branch.</li>
 *   <li>Unverifiable data must throw (preferably {@link CatalogueSalesException} with {@code DEPENDENCY_UNAVAILABLE}),
 *       never return an empty list in place of an error. Without an implementation bean the public read answers
 *       {@code LOCATION_DEPENDENCY_UNAVAILABLE}.</li>
 * </ul>
 */
public interface SellingLocationPort {

    List<SellingLocation> activeSellingLocations();
}
