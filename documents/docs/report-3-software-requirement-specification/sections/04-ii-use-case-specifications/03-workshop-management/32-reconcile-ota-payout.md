### 3.32 Reconcile OTA Payout

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to view OTA financial evidence and record payout evidence and adjustments so that the customer channel payment, the amount owed to Mland, and the amount received stay separate. Live data depends on partner go-live.

#### Preconditions

1. The actor is an active Manager.
2. A channel booking exists.
3. The Manager has checked the payout evidence to be recorded.

#### Postconditions

- Each piece of evidence is appended and audited; earlier facts are not overwritten.
- The payout is marked paid out only when the received amount matches.

#### Normal Flow

**Reconcile OTA Payout**

1. The Manager opens the financial evidence of a channel booking.
2. The system shows the channel payment state, the payout state, and the ledger entries.
3. The Manager records eligibility, invoice, remittance, and bank receipt evidence as it becomes available, with amount, reference, reason, and time.
4. The system validates the evidence and advances the payout state.
5. The system marks the booking paid out when its allocated bank receipt matches the expected net plus evidenced adjustments and reversals.

#### Alternative Flows

**Step 3 — Grouped OTA payout**

Each booking needs its own allocation reference; the system does not guess the split.

**Step 4 — Evidence is missing or does not match**

A payout dispute is opened and the payout state does not advance.

**Step 4 — Bank receipt is not in the payout currency**

A payout dispute is opened.

**Step 4 — A difference is explained**

The Manager records an adjustment linked to an existing entry, with evidence and a reason.

**Step 4 — Evidence is repeated**

The same evidence returns the original fact; a conflicting repeat is refused.

#### Business Rules

BR-94-01, BR-94-02, BR-94-03, BR-94-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-94-01 | Only an active Manager may read or record payout evidence; Staff and Admin are refused before the booking is looked up. |
| BR-94-02 | The payout states are not eligible, accrued, invoiced, payout pending, and paid out, each advanced only with matching evidence. |
| BR-94-03 | Expected net is the net price per person times the booked participant count; cancellations do not change it, and the system posts no adjustment by itself. |
| BR-94-04 | Payout is reconciled in VND with no exchange-rate variance; the amount paid by the customer is evidence only. |
