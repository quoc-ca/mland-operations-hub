# Voucher scope after a ticket change: what 003.3 needs from 006 (FE-06)

**Raised by:** owner Feature 003 (Hồ Hoàng Quốc), 2026-11-14. **Owner of the
change:** 006 Loyalty & Promotion (Đặng Thị Khương). **Blocks:** 003.3 T101
(ticket changes). **Record of the block:** `ppswbs_backend/ERROR-LOG.md`,
BE-DESIGN-0007. **Convention:** same as 003.1
[cart-benefit-contract-request.md](../003.1-workshop-booking/cart-benefit-contract-request.md)
— a cross-owner request recorded before either side writes code.

## Why

003.3 FR-044 lets the buyer move a ticket's sessions once (advance change or
late-arrival support). FR-045 then says what that does to the money:

> The package price stays frozen, so a change never lowers the price and never
> creates a refund. **A ticket moved outside its voucher scope loses its
> allocated voucher discount**; used points stay. A ticket moved into scope
> gains no new voucher. Any increase is shown with the new schedule and amount
> and confirmed by the buyer, then added to the ticket's checkout balance.

FR-045 states the rule but never says **who decides that the ticket is now
outside its voucher scope**. Voucher scope is 006's: per 003.1 FR-033 and the
006 cart contract, a ticket is in scope when its package is targeted **and every
session of the ticket** satisfies the branch, date and session restrictions, and
006 is what answers that, returning `eligibleLineIndexes`. 003.1 only allocates;
its own `WorkshopCartBenefitService` records that it "never re-checks terms,
re-evaluates scope or invents a split".

T101 was implemented without that answer and inferred scope itself, comparing
only the target session's branch (`SaleEligibilitySnapshot.locationReference`).
That is wrong in both directions:

- a voucher restricted by **date** or by **session**, whose ticket moves outside
  that restriction, is not detected, so the customer keeps a discount they lost;
- a ticket moved to another **branch** under a voucher with no branch
  restriction is charged an increase it does not owe.

It also added the increase to the folio's `postPromotionAmountVnd`, although
003.3 FR-030 says a ticket folio's amounts come from 003.1's immutable snapshot
and are never recomputed.

## What 003.3 needs

One read-only answer, per ticket, for a proposed new session set:

- **Input:** the accepted benefit of the group (006 already holds it by
  `(sourceKind, sourceRef)`), the ticket's reference (`ticketRef`, the
  `L{line}-T{ordinal}` 003.1 sent at accept), and the proposed session set for
  that ticket (reference, branch and date per session, exactly the shape of
  `WorkshopSessionFact`).
- **Output:** whether that ticket would still be inside the accepted voucher's
  scope, and — when it would not — the discount amount that ticket loses. That
  amount is the per-ticket share 006 already holds in the accepted snapshot's
  part (006 D-012, `BenefitPartRef`), so no new arithmetic is needed on either
  side.
- **Read-only:** it holds no quota, consumes nothing and changes no snapshot. It
  is asked while the change is still being previewed, and asked again on commit.

## What 003.3 will do with it

- No increase when the ticket stays in scope.
- When the ticket leaves scope, the lost discount becomes the price increase of
  FR-045: shown to the buyer, confirmed by them, and added **only** to the
  ticket's checkout balance (`remainingAmountVnd`). The folio's base line and
  `postPromotionAmountVnd` stay as 003.1 snapshotted them (FR-030).
- 003.3 will not re-evaluate scope, will not read voucher targets, and will not
  guess which dimension failed.
- An automatic workshop discount (006 FR-044) has no branch, date or session
  dimension, so a session move never takes it away; 003.3 needs no answer for
  that case and will not ask for one.

### Cases 006 should cover

1. Voucher targeted by package only; the ticket moves to another session of the
   same package → still in scope, no increase.
2. Voucher restricted by branch; the ticket moves to another branch → out of
   scope, loses exactly that ticket's allocated share.
3. Voucher restricted by **date**; the ticket moves to another date → out of
   scope (the case T101's branch comparison misses).
4. Voucher restricted by **session**; the ticket moves to another session → out
   of scope (also missed today).
5. Voucher with no branch restriction; the ticket moves to another branch →
   **still in scope**, no increase (today's code wrongly charges one).
6. A two-session ticket where only one session moves out of the restriction →
   out of scope, because every session of a ticket must match (the 2026-10-29
   rule).
7. The group used points but no voucher → nothing is lost; points stay with the
   ticket (FR-045).
8. The group used an automatic workshop discount → never lost on a session move.
9. Replay: asking twice for the same proposed set gives the same answer and
   changes nothing.

## Until this lands

003.3 T101 stays **open**. Its code is in place and its tests pass, but they
only assert the branch comparison, so the task is not ticked. The owner's
instruction of 2026-11-14 is that **006 provides this check**; 003.3 will not
keep its own inference once the operation exists.

Buildable meanwhile, and unaffected: a change of a ticket that carries no
voucher discount, every auto-approval condition of FR-045a (capacity, quota, the
24-hour and late-arrival windows, the change allowance), the Manager-granted
extra change (FR-054) and the Staff-on-behalf change (FR-055).
