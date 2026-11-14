package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;

/**
 * Filters of the public review list (contract: GET /reviews). Every field is
 * optional: with no filter the list covers all
 * public reviews. {@code packageId} narrows to the reviews of one workshop
 * package (every session of it). {@code cursor} is
 * the opaque value returned as {@code nextCursor} by the previous page.
 */
public record PublicReviewQuery(TargetType targetType, String targetReference, Long packageId, Integer rating,
    String cursor, Integer limit,
    String sort){

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;

    /** 6-arg constructor without sort for backwards compatibility. */
    public PublicReviewQuery(TargetType targetType, String targetReference, Long packageId, Integer rating,
            String cursor, Integer limit) {
        this(targetType, targetReference, packageId, rating, cursor, limit, "recent");
    }

    /** Without a package filter. */
    public PublicReviewQuery(TargetType targetType, String targetReference, Integer rating, String cursor,
            Integer limit) {
        this(targetType, targetReference, null, rating, cursor, limit, "recent");
    }
}
