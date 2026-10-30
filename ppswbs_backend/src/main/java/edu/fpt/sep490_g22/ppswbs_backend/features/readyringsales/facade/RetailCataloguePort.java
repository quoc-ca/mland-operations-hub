package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

/**
 * FE-08 boundary to FE-02 catalogue facts. The local implementation is only a
 * development substitute and is replaced by the approved FE-02 facade.
 */
public interface RetailCataloguePort {

    boolean isEligibleForCart(Long variantId, Long locationId);
}
