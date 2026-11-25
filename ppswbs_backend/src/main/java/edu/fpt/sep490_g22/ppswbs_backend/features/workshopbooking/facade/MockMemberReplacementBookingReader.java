package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountId;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MemberReplacementDataOrigin;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MemberReplacementInventoryStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.WorkshopMemberReplacementReader;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.WorkshopReplacementInventory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Local-only mock of FE-003.1 Booking data for FE-001 ADMIN MEMBER-collision preview and JSON export.
 * It never checks real Booking/Settlement state and always returns UNAVAILABLE, so it cannot authorize retirement.
 * Replace this class with an owner-approved live provider of the same FE-001 reader contract.
 */
@Component
@Profile("local")
public class MockMemberReplacementBookingReader implements WorkshopMemberReplacementReader {
    private final long demoAccountId;

    public MockMemberReplacementBookingReader(
            @Value("${app.account.member-replacement-demo.account-id:0}") long demoAccountId) {
        this.demoAccountId = demoAccountId;
    }

    /** Returns sample rows only for the explicitly configured demo Account ID. */
    @Override
    public WorkshopReplacementInventory readForMemberReplacement(AccountId accountId) {
        Instant now = Instant.now();
        if (demoAccountId <= 0 || accountId.value() != demoAccountId) {
            return new WorkshopReplacementInventory(accountId, MemberReplacementInventoryStatus.UNAVAILABLE,
                    MemberReplacementDataOrigin.NONE, null, now, List.of(), List.of());
        }
        return new WorkshopReplacementInventory(accountId, MemberReplacementInventoryStatus.UNAVAILABLE,
                MemberReplacementDataOrigin.DEMO, null, now,
                List.of(new WorkshopReplacementInventory.BookingSummary("DEMO-BOOKING",
                        now.minus(3, ChronoUnit.DAYS), "COMPLETED", "FULLY_PAID",
                        "Workshop minh họa", now.minus(1, ChronoUnit.DAYS))),
                List.of());
    }
}
