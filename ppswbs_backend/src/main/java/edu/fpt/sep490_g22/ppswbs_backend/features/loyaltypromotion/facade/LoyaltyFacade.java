package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/**
 * The only public Java interface of loyaltypromotion. Mutations are MANDATORY-transaction: they join the
 * source owner's accept/outcome transaction (same DataSource and JPA transaction manager) and are rejected
 * outside one. Every failure is a typed {@link LoyaltyException}; no entity leaves the module.
 */
public interface LoyaltyFacade {

    /** Read-only breakdown; holds no quota or points and writes nothing. */
    BenefitPreview previewBenefits(TrustedSourceContext context, BenefitSelection selection);

    /**
     * Re-checks terms, holds quota/points and stores the snapshot together with the source accept. Idempotent by
     * {@code (sourceKind, sourceRef)}: a repeat with the same terms digest and parts returns the stored snapshot, a
     * different one is {@code IDEMPOTENCY_CONFLICT}. {@code sourceCommandKey} is not stored or compared (T124); it
     * is kept so the callers of 003.1 and FE-08 do not change.
     */
    AcceptedBenefitRef reserveAcceptedTransaction(AcceptedSourceContext context, BenefitSelection selection,
                                                  String sourceCommandKey);

    /** Consumes held components on a verified paid, deposit or zero outcome. */
    BenefitResult settleAcceptedBenefits(AuthoritativeOutcome outcome);

    /** Grants earned points once on a retail paid/zero or a workshop completed-and-fully-paid outcome. */
    BenefitResult grantEarnedPoints(AuthoritativeOutcome outcome);

    /** Releases HELD components on a confirmed unpaid expiry or valid cancellation. */
    BenefitResult releaseHeldBenefits(AuthoritativeOutcome outcome);

    /** Restores CONSUMED components (and releases HELD ones) on a valid workshop cancellation. */
    BenefitResult restoreCancelledWorkshopBenefits(AuthoritativeOutcome outcome);

    /** Marks a pending earn ineligible on a terminal outcome without refunding consumed components. */
    BenefitResult finishIneligibleEarn(AuthoritativeOutcome outcome);

    /** Current result for the owner's reconciliation; never returns another owner's transaction. */
    BenefitResult findBenefitResult(SourceKind sourceKind, String sourceRef);

    /**
     * Read-only per-ticket voucher scope for a proposed session set (003.3 FR-045, BE-DESIGN-0007): whether the ticket
     * would stay inside the ACCEPTED voucher revision's targets and, if not, the allocated share it would lose. An
     * automatic discount or points are never lost. Holds nothing and changes no snapshot; asking twice answers the same.
     */
    TicketVoucherScope checkTicketVoucherScope(TicketVoucherScopeQuery query);

    /**
     * 003.3 FR-042/FR-043 (006 T110): a workshop ticket was cancelled by Mland. Its points come back (held released,
     * consumed restored in the original amount) once per ticket, and the group's voucher comes back once per group,
     * only while the voucher is still valid; an expired voucher answers {@code EXPIRED} and is never extended. A
     * repeat for the same group or ticket restores nothing twice. Mandatory transaction.
     */
    VoucherRestorationOutcome restoreVoucherForCancelledTicket(RestoreVoucherCommand command);
}
