package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

import java.util.List;

/**
 * Public card and Member saved-voucher models (T069, OpenAPI {@code PublicPromotion}, {@code SavedVoucher} and their
 * pages). A card is information, not a quote: it does not promise the voucher can be used on a given order.
 */
public final class PublicPromotionDtos {

    private PublicPromotionDtos() {
    }

    public record PublicPromotion(String id, String kind, String scope, String code, String name, String startAt,
                                  String endAt, PromotionReadDtos.Discount discount, String minimumEligibleAmount,
                                  boolean allowPoints, PromotionReadDtos.Targets targets, String totalLimit,
                                  String perMemberLimit) {
    }

    public record PromotionPage(List<PublicPromotion> items, String nextCursor) {
    }

    /** {@code availability}: ACTIVE, SCHEDULED, PAUSED, EXPIRED or NO_PUBLIC_TARGET. */
    public record SavedVoucher(PublicPromotion promotion, String savedAt, String availability) {
    }

    public record SavedVoucherPage(List<SavedVoucher> items, String nextCursor) {
    }
}
