## 7. Custom Manufacturing

Custom manufacturing is a Member-only, quote-to-order flow. It is separate
from ready-ring retail checkout and does not use a retail cart. After Staff or
a Manager accepts a Member's design request, the reviewer prepares a quote for
the complete custom-order amount and a Manager approves it. The Member must pay
that amount in one VNPay payment before the quote deadline; there is no deposit
and no later balance. Only a verified full payment starts production, after
which Staff or Managers advance the order through production, handover and
closure. Feature-012 may provide an optional immutable design snapshot, but it
does not create a booking or payment when the design is saved.

The customer-facing label is **Custom orders** and each quote is shown as a
**Quote version**. A Feature-012 design may be attached as an owner-checked
reference regardless of its readable lifecycle state; the custom order keeps
its own immutable snapshot.
If payment is not verified before the deadline, the quote can no longer be paid
and the Member may ask for a new quote. A verified payment that arrives late, is
for a replaced quote or is for an already paid order is kept for manual review and
does not mark the order as paid; a payment with a wrong amount is rejected.
