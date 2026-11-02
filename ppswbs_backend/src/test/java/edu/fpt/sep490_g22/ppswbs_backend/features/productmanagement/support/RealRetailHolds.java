package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailVariantHoldLifecycleService;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Creates and moves REAL FE-08 holds (readyringsales guard rows and holds, develop b0e7796) the way FE-08 does at
 * checkout: catalogue sale lock → FE-08 source guard → hold. No hold is written directly to a table and no
 * catalogue state is faked. The checkout row is a minimal FE-08 {@link GroupedCheckout} owning the holds;
 * FE-08's payment flow is not on develop yet, so {@link #settleAndRecord} plays that caller.
 */
@TestComponent
public class RealRetailHolds {

    private final CatalogueSalesFacade sales;
    private final AvailabilityGuardPort source;
    private final RetailVariantHoldLifecycleService lifecycle;
    private final RetailVariantHoldRepository holds;
    private final GroupedCheckoutRepository checkouts;
    private final AccountRepository accounts;
    private final TransactionTemplate readCommitted;

    public RealRetailHolds(CatalogueSalesFacade sales, AvailabilityGuardPort source,
                           RetailVariantHoldLifecycleService lifecycle, RetailVariantHoldRepository holds,
                           GroupedCheckoutRepository checkouts, AccountRepository accounts,
                           PlatformTransactionManager transactionManager) {
        this.sales = sales;
        this.source = source;
        this.lifecycle = lifecycle;
        this.holds = holds;
        this.checkouts = checkouts;
        this.accounts = accounts;
        this.readCommitted = new TransactionTemplate(transactionManager);
        this.readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** A new FE-08 checkout (with its Member account) that holds can belong to. */
    public String newCheckout() {
        String uid = "pm-hold-it-" + UUID.randomUUID();
        long memberId = inTx(() -> accounts.saveAndFlush(Account.builder().firebaseUid(uid)
                .loginEmail(uid + "@example.test").role(AccountRole.MEMBER).status(AccountStatus.ACTIVE).build()).getId());
        return inTx(() -> checkouts.saveAndFlush(new GroupedCheckout(memberId, UUID.randomUUID().toString(), 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).getId());
    }

    /** A new hold valid for 15 minutes, created as FE-08 checkout does; refuses when not enough is free. */
    public String hold(String checkoutId, long sizeId, int quantity) {
        return hold(checkoutId, sizeId, quantity, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    public String hold(String checkoutId, long sizeId, int quantity, Instant expiresAt) {
        return inTx(() -> sales.withLockedSaleContext(List.of(sizeId), context ->
                source.withGuard(List.of(sizeId), context.asOf(), snapshots -> {
                    BigInteger free = context.variants().get(0).remainingUnsold()
                            .subtract(snapshots.get(sizeId).heldQuantity());
                    if (free.compareTo(BigInteger.valueOf(quantity)) < 0) {
                        throw new IllegalStateException("Not enough quantity left to hold");
                    }
                    return lifecycle.createHeld(checkouts.findById(checkoutId).orElseThrow(), sizeId, quantity, expiresAt)
                            .getId();
                })));
    }

    /** FE-08 releases its hold under its guard (also allowed after the product was hidden or deleted). */
    public void release(String checkoutId, long sizeId) {
        inTx(() -> sales.withLockedHistoricalContext(checkoutId, List.of(sizeId), context ->
                source.withGuard(List.of(sizeId), context.asOf(), snapshots -> {
                    lifecycle.release(holds.findByGroupedCheckoutIdAndVariantId(checkoutId, sizeId).orElseThrow());
                    return null;
                })));
    }

    /** FE-08 marks a hold EXPIRED under its guard. */
    public void expire(String checkoutId, long sizeId) {
        inTx(() -> source.withGuard(List.of(sizeId), Instant.now(), snapshots -> {
            lifecycle.expire(holds.findByGroupedCheckoutIdAndVariantId(checkoutId, sizeId).orElseThrow());
            return null;
        }));
    }

    /**
     * The FE-08 payment path for one size: in one transaction, settle the hold under the guard and then record the
     * accepted sale once. A repeat with the same operation only replays the sale.
     */
    public void settleAndRecord(String checkoutId, long sizeId, int quantity, UUID operation) {
        inTx(() -> sales.withLockedHistoricalContext(checkoutId, List.of(sizeId), context -> {
            source.withGuard(List.of(sizeId), context.asOf(), snapshots -> {
                var hold = holds.findByGroupedCheckoutIdAndVariantId(checkoutId, sizeId).orElseThrow();
                if (hold.getStatus().name().equals("HELD")) {
                    lifecycle.settle(hold);
                }
                return null;
            });
            return sales.recordAcceptedSale(operation, sizeId, BigInteger.valueOf(quantity));
        }));
    }

    public BigInteger held(long sizeId) {
        return inTx(() -> source.readSnapshots(List.of(sizeId), Instant.now()).get(sizeId).heldQuantity());
    }

    public long sourceRevision(long sizeId) {
        return inTx(() -> source.readSnapshots(List.of(sizeId), Instant.now()).get(sizeId).sourceRevision());
    }

    public String holdStatus(String checkoutId, long sizeId) {
        return inTx(() -> holds.findByGroupedCheckoutIdAndVariantId(checkoutId, sizeId).orElseThrow().getStatus().name());
    }

    private <T> T inTx(Supplier<T> work) {
        return readCommitted.execute(status -> work.get());
    }
}
