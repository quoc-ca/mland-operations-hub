### 3.21 Close Branch Cash Day

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member of the branch or a Manager, I want to submit the daily cash count so that the branch cash book is closed and any difference is resolved.

#### Preconditions

1. The actor is a Manager, or Staff whose location scope contains the branch.
2. The branch has not yet closed the selected working day (Vietnam time).

#### Postconditions

- The day is recorded with the expected cash, counted cash, difference, and closer.
- A day with a difference is closed only after Manager confirmation.

#### Normal Flow

**Close Branch Cash Day**

1. The actor opens the cash close of the branch.
2. The system shows the expected confirmed cash of the day, net of change returned.
3. The actor enters the counted amount and submits it.
4. The system records the close; with no difference the day is closed.

#### Alternative Flows

**Step 3 — Counted amount differs**

A reason is required and the close waits until a Manager confirms the difference with a reason.

**Step 4 — Day already closed or in the future**

The system refuses the close.

**After Step 4 — Cash collected after submission**

The cash belongs to the next working day, even while a difference awaits confirmation.

#### Business Rules

BR-83-01, BR-83-02, BR-83-03, BR-83-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-83-01 | Each branch closes its cash book once per working day. |
| BR-83-02 | Session assignment is not required because the close is per branch. |
| BR-83-03 | Bank transfers and VNPay are not part of the cash close. |
| BR-83-04 | Confirmed cash of a closed day never changes. |
