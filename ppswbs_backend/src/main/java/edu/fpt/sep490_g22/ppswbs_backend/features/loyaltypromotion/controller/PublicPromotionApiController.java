package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PublicPromotionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PublicPromotionQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public promotion cards (T069): listPublicPromotions and getPublicPromotion. No bearer is needed (route-scoped
 * public chain); nothing private is returned. Responses are not cached so a paused or ended programme disappears at
 * once.
 */
@RestController
@RequestMapping("/api/v1/promotions")
class PublicPromotionApiController {

    private final PublicPromotionQueryService promotions;

    PublicPromotionApiController(PublicPromotionQueryService promotions) {
        this.promotions = promotions;
    }

    @GetMapping
    ResponseEntity<PublicPromotionDtos.PromotionPage> list(@RequestParam(required = false) Integer limit,
                                                           @RequestParam(required = false) String cursor,
                                                           @RequestParam(required = false) String scope) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(promotions.list(limit, cursor, scope));
    }

    @GetMapping("/{promotionId}")
    ResponseEntity<PublicPromotionDtos.PublicPromotion> get(@PathVariable String promotionId) {
        long id = LoyaltyScalarCodec.parseId("promotionId", promotionId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(promotions.get(id));
    }
}
