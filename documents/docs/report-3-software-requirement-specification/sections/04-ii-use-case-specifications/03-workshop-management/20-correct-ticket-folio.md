### 3.20 Correct Ticket Folio

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to void a wrong folio line or collection, or correct a finalized overcharge, so that the folio is corrected without deleting history.

#### Preconditions

1. The actor is an active Manager.
2. The folio is OPEN for a void, or FINALIZED for an overcharge correction.

#### Postconditions

- The original records are kept and the correction is audited with its reason.
- An overcharge on a finalized folio is returned through a bank refund request.

#### Normal Flow

**Correct Ticket Folio**

1. The Manager opens the ticket folio.
2. The Manager selects the wrong line or collection and enters a reason.
3. The system checks the folio state and the cash day of the collection.
4. The system voids the record and recalculates the balance.

#### Alternative Flows

**Step 2 — Overcharge found after finalization**

The Manager records a correction up to the unrefunded collected money; the folio is not changed or reopened and a bank refund request follows.

**Step 3 — Cash belongs to a closed day**

The void is refused; on an OPEN folio with no refund of that money, the Manager records a negative reversal entry on the current day.

**Step 3 — Folio is closed**

The system refuses the correction.

#### Business Rules

BR-82-01, BR-82-02, BR-82-03, BR-82-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-82-01 | A wrongly recorded line is voided whole; this is not a customer right to cancel a used service. |
| BR-82-02 | An undercharge found after finalization is absorbed by Mland and never collected. |
| BR-82-03 | Confirmed cash of a closed day is never edited or deleted. |
| BR-82-04 | The use-case list states that closed-day cash reversal is not yet built, but the current backend and tasks include it; to be confirmed. |
