package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.util.List;

/**
 * SPI owned by loyaltypromotion and implemented by productmanagement (002) for the sample programmes of
 * {@code PromotionSeedRunner} only (T116, Constitution I): the seed names its targets by category, product and branch
 * name, and the owner of those records turns the names into ids. loyaltypromotion never reads another module's
 * tables. Read-only; ids are oldest first. Without an implementation bean the seed does nothing.
 */
public interface PromotionSeedCataloguePort {

    /** Ids of products that are not deleted, in the category with this exact name. */
    List<Long> productIdsInCategory(String categoryName);

    /** Ids of products that are not deleted, with this exact name. */
    List<Long> productIdsNamed(String productName);

    /** Number of published products that are not deleted, in the category with this exact name. */
    long publishedProductCount(String categoryName);

    /** Ids of the active branches with this exact display name. */
    List<Long> activeBranchIdsNamed(String displayName);
}
