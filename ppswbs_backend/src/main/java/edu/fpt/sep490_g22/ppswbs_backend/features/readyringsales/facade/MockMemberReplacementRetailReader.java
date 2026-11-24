package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountId;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MemberReplacementDataOrigin;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MemberReplacementInventoryStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RetailMemberReplacementReader;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RetailReplacementInventory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Local-only mock of FE-008 cart/order data for FE-001 ADMIN MEMBER-collision preview and JSON export.
 * It never checks real Retail state and always returns UNAVAILABLE, so it must never authorize retirement.
 * Replace this class with an owner-approved live provider of the same FE-001 reader contract.
 */
@Component
@Profile("local")
public class MockMemberReplacementRetailReader implements RetailMemberReplacementReader {
    private final long demoAccountId;

    public MockMemberReplacementRetailReader(
            @Value("${app.account.member-replacement-demo.account-id:0}") long demoAccountId) {
        this.demoAccountId = demoAccountId;
    }

    /** Returns sample rows only for the explicitly configured demo Account ID. */
    @Override
    public RetailReplacementInventory readForMemberReplacement(AccountId accountId) {
        Instant now = Instant.now();
        if (demoAccountId <= 0 || accountId.value() != demoAccountId) {
            return new RetailReplacementInventory(accountId, MemberReplacementInventoryStatus.UNAVAILABLE,
                    MemberReplacementDataOrigin.NONE, null, now, List.of(), List.of(), List.of());
        }
        return new RetailReplacementInventory(accountId, MemberReplacementInventoryStatus.UNAVAILABLE,
                MemberReplacementDataOrigin.DEMO, null, now,
                List.of(new RetailReplacementInventory.CartLine("DEMO-PRODUCT", "DEMO-VARIANT",
                        "Sản phẩm minh họa", 1, now.minus(2, ChronoUnit.DAYS))),
                List.of(new RetailReplacementInventory.OrderSummary("DEMO-ORDER", "DEMO-ORDER",
                        now.minus(1, ChronoUnit.DAYS), "PAID", "DELIVERED", now, "1 sản phẩm minh họa")),
                List.of());
    }
}
