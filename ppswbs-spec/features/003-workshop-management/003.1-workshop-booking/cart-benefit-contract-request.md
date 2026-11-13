# Cart benefit contract: what 003.1 needs from 006 (FE-06)

**Raised by:** owner 003.1 (Hồ Hoàng Quốc), 2026-11-12. **Owner of the change:**
006 Loyalty & Promotion (Đặng Thị Khương). **Blocks:** 003.1 T092 and T093.
**Convention:** same as 006 T088 (a cross-owner request recorded before either
side writes code).

## Why

003.1 FR-030 introduced a multi-ticket cart: one **booking group** holds one or
more lines, each a package with its complete required-session set and a
participant count, and confirming the cart creates **one ticket booking per
person**. FR-033 and FR-034 then say how one voucher use and the Member's points
spread over those tickets:

- one voucher use covers one group confirmation;
- **cart-level** conditions (minimum amount, participant count) are checked only
  when the group is confirmed;
- **ticket-level** scope (package, date, session, branch) is checked per ticket,
  and a ticket outside the scope gets no discount;
- the group discount is allocated to in-scope tickets in proportion to ticket
  price, largest remainder in whole VND, capped at each ticket's price;
- the point cap follows the recorded FE-06 policy and applies to the cart total
  after the voucher; points are whole and weighted by post-voucher ticket price.

003.1 has already built the allocation itself (T090/T091,
`TicketBenefitAllocator`): given a group discount, a point total, a point value
and a per-ticket in-scope flag, it produces each ticket's voucher share, point
share, final price and booking-time collection. It is pure and unit-tested.

**What it cannot produce is the input.** The group discount, the point total,
the point value and above all *which tickets are in voucher scope* are 006's
decisions, and the current facade cannot express them for a cart.

## What the current facade can and cannot do

Read from `loyaltypromotion/facade` and
`loyaltypromotion/service/WorkshopBenefitEvaluator.java` on 2026-11-12:

| Need | Today | Verdict |
| --- | --- | --- |
| Guest cart benefits | `TrustedSourceContext` requires `memberId > 0` | **Not a gap.** All FE-06 benefits are Member-only, so a Guest cart has no voucher and no points. 003.1 quotes a Guest cart at package price. |
| Member cart, one package, benefits | `WorkshopFact(packageId, participantCount, grossPackageAmount, sessions)` fits: participants summed, sessions unioned, gross = cart total | Expressible, but see the scope row. |
| Member cart, several packages, benefits | `WorkshopFact` holds exactly **one** `packageId` and one `participantCount` | **Blocked.** A cart of a Wax Casting line plus a Ring Basic line cannot be previewed at all. |
| Per-ticket voucher scope | `WorkshopBenefitEvaluator.matches` requires the package to be targeted **and every** selected session to satisfy the branch, date and session restrictions (decision 2026-10-29) | **Blocked.** The rule is all-or-nothing over the whole booking, so it answers "does this booking qualify", never "which tickets qualify". A cart with one in-scope and one out-of-scope ticket currently gets either the full discount or none. |

The 2026-10-29 all-or-nothing rule was right for a single booking, where the
discount covers one package. It is the exact rule FR-033 overrides for a cart,
because a cart deliberately mixes packages, dates, branches and sessions.

## What 003.1 asks 006 to add

A cart-shaped preview and reserve that answer per ticket line. The retail side
already solved the same problem in 006 T085 (`SourceLine.locationId` plus
`BenefitPreview.eligibleLineIndexes`), so this mirrors a pattern 006 owns.

### Proposed shape

```java
/** One ticket line of a cart: one person, one package, that person's required sessions. */
public record WorkshopTicketFact(long packageId, BigInteger ticketPrice,
                                 List<WorkshopSessionFact> sessions) { }

/** A cart of ticket lines, in cart order. Replaces WorkshopFact for a group preview. */
public record WorkshopCartFact(List<WorkshopTicketFact> tickets) { }
```

`TrustedSourceContext` and `AcceptedSourceContext` carry a `WorkshopCartFact`
beside today's `WorkshopFact` (one of the two, never both), so existing single
booking callers keep compiling — the same compatibility approach as the retained
`BenefitPreview` constructors.

`BenefitPreview` then answers, for a cart context:

- `programDiscount` — the group discount, cart-level conditions applied;
- `pointsUsed`, `pointValue`, `pointDiscount`, `maxPoints` — cap on the cart
  total after voucher;
- `eligibleLineIndexes` — **the zero-based cart positions of the ticket lines
  inside the voucher scope**, which is the one field 003.1 cannot derive;
- `policyRevisionNo` and `termsDigest` — recorded per group, unchanged.

Scope per ticket line = the voucher targets the line's package **and** every
session of *that line* satisfies the branch, date and session restrictions. In
other words today's `matches` applied per ticket line instead of per booking.

`reserveAcceptedTransaction` accepts the same cart context and holds one voucher
use and the cart's points for the whole group, as it does today for one booking.

### What 003.1 does with it

`TicketBenefitAllocator.allocate(tickets, voucherDiscountVnd, pointsUsed, pointValueVnd)`
with `inVoucherScope = eligibleLineIndexes.contains(i)`. 003.1 stores the result
per ticket in `ticket_price_snapshots` and never recomputes it. 003.1 does not
re-check terms, re-evaluate scope or split the discount by any rule of its own.

### Cases 006 should cover

1. Cart of two packages, voucher targets one → only that package's tickets are
   eligible; the other ticket gets no discount.
2. Cart where one line's sessions fall outside the voucher's branch/date/session
   restriction → that line alone is out of scope.
3. One line whose *own* sessions all match while another line's do not → the
   matching line stays in scope (this is what the 2026-10-29 rule would refuse).
4. Cart minimum amount and participant count judged on the whole cart, not per
   line.
5. Point cap on the cart total after voucher; whole points only.
6. 100% voucher and a 0 VND ticket are valid (003.1 FR-033).
7. One voucher use and one points hold per group, idempotent on replay.

## Until this lands

003.1 T092 and T093 are blocked for a Member cart that requests a voucher or
points. 003.1 will not fake the missing input: no discount is invented inside
`workshopbooking`, no per-ticket scope is guessed, and the all-or-nothing rule is
not reused for carts, because any of those would diverge from FE-06 terms and
produce snapshots that are wrong and immutable.

Unblocked and buildable meanwhile: Guest carts and Member carts that request no
benefit, which quote at package price and go through the same allocator with a
zero discount and zero points.
