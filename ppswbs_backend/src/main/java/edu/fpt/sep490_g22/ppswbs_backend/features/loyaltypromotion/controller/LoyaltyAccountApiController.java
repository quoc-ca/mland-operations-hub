package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyAccountDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAccountQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * My Loyalty API (T039): getOwnLoyaltyAccount and listOwnLoyaltyTransactions. Only the signed-in, active Member's
 * own data; the member id comes from the verified actor and is never read from the request. Private responses are
 * never cached. No grant, restore or other lifecycle effect is reachable over HTTP.
 */
@RestController
@RequestMapping("/api/v1/members/me")
class LoyaltyAccountApiController {

    private final LoyaltyAuthorization authorization;
    private final LoyaltyAccountQueryService accounts;

    LoyaltyAccountApiController(LoyaltyAuthorization authorization, LoyaltyAccountQueryService accounts) {
        this.authorization = authorization;
        this.accounts = accounts;
    }

    @GetMapping("/loyalty-accounts/current")
    ResponseEntity<LoyaltyAccountDtos.Account> account() {
        long memberId = authorization.requireMember();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.account(memberId));
    }

    @GetMapping("/loyalty-transactions")
    ResponseEntity<LoyaltyAccountDtos.TransactionPage> transactions(@RequestParam(required = false) Integer limit,
                                                                    @RequestParam(required = false) String cursor) {
        long memberId = authorization.requireMember();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.transactions(memberId, limit, cursor));
    }
}
