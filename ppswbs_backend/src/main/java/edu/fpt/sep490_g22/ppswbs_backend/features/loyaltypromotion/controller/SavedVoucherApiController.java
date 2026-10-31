package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PublicPromotionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.SavedVoucherService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Member's voucher wallet (T069): listOwnSavedVouchers and saveOwnVoucher. Only the signed-in, active Member's own
 * list; the member id comes from the verified actor. Saving needs {@code Idempotency-Key}, is idempotent and never
 * holds a use or points. Private responses are never cached.
 */
@RestController
@RequestMapping("/api/v1/members/me/saved-vouchers")
class SavedVoucherApiController {

    private final LoyaltyAuthorization authorization;
    private final SavedVoucherService saved;

    SavedVoucherApiController(LoyaltyAuthorization authorization, SavedVoucherService saved) {
        this.authorization = authorization;
        this.saved = saved;
    }

    @GetMapping
    ResponseEntity<PublicPromotionDtos.SavedVoucherPage> list(@RequestParam(required = false) Integer limit,
                                                              @RequestParam(required = false) String cursor) {
        long memberId = authorization.requireMember();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(saved.list(memberId, limit, cursor));
    }

    @PutMapping("/{programId}")
    ResponseEntity<PublicPromotionDtos.SavedVoucher> save(@PathVariable String programId,
                                                          @RequestHeader("Idempotency-Key") UUID key) {
        long memberId = authorization.requireMember();
        long id = LoyaltyScalarCodec.parseId("programId", programId);
        saved.save(memberId, key, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(saved.saved(memberId, id));
    }
}
