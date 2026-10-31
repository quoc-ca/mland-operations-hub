package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Single place that takes loyalty row locks, always in the agreed order (interfaces "Transaction and lock order"):
 *
 * <ol>
 *   <li>identity guard and</li>
 *   <li>source guards: taken by the caller (the source owner or the API) before any loyalty lock;</li>
 *   <li>registry: READ (shared) when a transaction is accepted, WRITE (exclusive) when a programme or the policy
 *       changes;</li>
 *   <li>programmes, ascending ID (voucher quota counters live on the programme row);</li>
 *   <li>the Member's wallet, then that Member's per-programme counters, ascending programme ID;</li>
 *   <li>the accepted benefit.</li>
 * </ol>
 * The coordinator remembers, per transaction, the last step taken and refuses to go back to an earlier step
 * (for example wallet then registry), so no code path can invert the order. Locks are pessimistic and held until
 * commit; no remote call may run while they are held. A lock timeout or deadlock rolls the transaction back and
 * surfaces as the retryable {@code RETRYABLE_CONTENTION}. Optimistic {@code expectedVersion} checks for Manager
 * edits use {@link #requireVersion}.
 */
@Service
public class LoyaltyLockCoordinator {

    /** Lock steps in their only allowed order. */
    enum Step {
        REGISTRY,
        PROGRAMS,
        WALLET,
        MEMBER_PROGRAMS,
        BENEFIT
    }

    private static final Object STEP_KEY = new Object();

    private final LoyaltyRegistryRepository registries;
    private final PromotionProgramRepository programs;
    private final LoyaltyWalletRepository wallets;
    private final MemberProgramRepository memberPrograms;
    private final AcceptedBenefitRepository benefits;

    public LoyaltyLockCoordinator(LoyaltyRegistryRepository registries, PromotionProgramRepository programs,
                                  LoyaltyWalletRepository wallets, MemberProgramRepository memberPrograms,
                                  AcceptedBenefitRepository benefits) {
        this.registries = registries;
        this.programs = programs;
        this.wallets = wallets;
        this.memberPrograms = memberPrograms;
        this.benefits = benefits;
    }

    /** Shared registry lock for an accept: the candidate programmes and the current policy stay stable. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoyaltyRegistry readRegistry() {
        enter(Step.REGISTRY, List.of("registry"));
        return registries.lockForRead(LoyaltyRegistry.SINGLETON_ID).orElseThrow(LoyaltyLockCoordinator::missingSeed);
    }

    /** Exclusive registry lock for a programme or policy change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoyaltyRegistry writeRegistry() {
        enter(Step.REGISTRY, List.of("registry"));
        return registries.lockForWrite(LoyaltyRegistry.SINGLETON_ID).orElseThrow(LoyaltyLockCoordinator::missingSeed);
    }

    /** Programme locks in ascending ID order; every requested programme must exist. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, PromotionProgram> lockPrograms(Collection<Long> programIds) {
        enter(Step.PROGRAMS, programIds.stream().map(id -> "program:" + id).toList());
        if (programIds.isEmpty()) {
            return Map.of();
        }
        SortedSet<Long> ascending = new TreeSet<>(programIds);
        Map<Long, PromotionProgram> locked = new LinkedHashMap<>();
        for (PromotionProgram program : programs.lockAllByIdIn(List.copyOf(ascending))) {
            locked.put(program.getId(), program);
        }
        if (!locked.keySet().containsAll(ascending)) {
            throw new LoyaltyException(LoyaltyErrorCode.PROMOTION_NOT_FOUND, "The promotion was not found.");
        }
        return locked;
    }

    /** The Member's wallet, locked; empty when the Member has none yet. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LoyaltyWallet> lockWallet(long memberId) {
        enter(Step.WALLET, List.of("wallet:" + memberId));
        return wallets.lockByMemberId(memberId);
    }

    /**
     * The Member's wallet, locked, created empty on first use. The first insert is serialized by the identity
     * guard the caller holds; a concurrent first insert fails on the primary key and rolls back (retryable).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoyaltyWallet lockOrOpenWallet(long memberId) {
        enter(Step.WALLET, List.of("wallet:" + memberId));
        return wallets.lockByMemberId(memberId).orElseGet(() -> wallets.saveAndFlush(LoyaltyWallet.open(memberId)));
    }

    /**
     * One Member's counters for one programme, locked, created on first use. The caller must already hold that
     * programme's lock, which serializes the first insert.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemberProgram lockOrCreateMemberProgram(long programId, long memberId) {
        enter(Step.MEMBER_PROGRAMS, List.of("member-program:" + programId + ":" + memberId));
        return memberPrograms.lockByProgramIdAndMemberId(programId, memberId)
                .orElseGet(() -> memberPrograms.saveAndFlush(MemberProgram.create(programId, memberId)));
    }

    /** The accepted benefit of one source transaction, locked. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AcceptedBenefit> lockBenefit(SourceKind sourceKind, String sourceRef) {
        enter(Step.BENEFIT, List.of("benefit:" + sourceKind + ":" + sourceRef));
        return benefits.lockBySource(sourceKind, sourceRef);
    }

    /** Rejects a change prepared from an older displayed version. */
    public static void requireVersion(long currentVersion, long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new LoyaltyException(LoyaltyErrorCode.STALE_VERSION,
                    "The data changed. Reload the latest version and try again.");
        }
    }

    /**
     * Records the step for this transaction and refuses an earlier one for rows not locked yet. Asking again for rows
     * this transaction already holds is allowed (for example a settle followed by an earn in the owner's transaction),
     * because it takes no new lock. The same step may repeat for new rows of that step.
     */
    static void enter(Step step, Collection<String> keys) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Loyalty locks need an active transaction");
        }
        LockState state = (LockState) TransactionSynchronizationManager.getResource(STEP_KEY);
        if (state == null) {
            state = new LockState();
            TransactionSynchronizationManager.bindResource(STEP_KEY, state);
            TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support
                    .TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(STEP_KEY);
                }
            });
        }
        if (!keys.isEmpty() && state.held.containsAll(keys)) {
            return; // already locked in this transaction: asking again takes no new lock
        }
        if (state.last != null && step.ordinal() < state.last.ordinal()) {
            throw new IllegalStateException("Lock order violated: " + step + " requested after " + state.last);
        }
        state.last = step;
        state.held.addAll(keys);
    }

    /** Last step and the rows already locked by this transaction. */
    private static final class LockState {
        private Step last;
        private final java.util.Set<String> held = new java.util.HashSet<>();
    }

    private static IllegalStateException missingSeed() {
        return new IllegalStateException("The loyalty registry row is missing; the V10 migration seed was not applied");
    }
}
