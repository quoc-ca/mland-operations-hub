### 3.5 Look Up Booking Details

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway

#### Description

As a Guest or Member, I want to open my booking group so that I can see its tickets, their status and money, and the check-in QR codes.

#### Preconditions

1. The platform is available.
2. A booking group exists.
3. The actor is the signed-in owning Member, or holds the group code with one stored contact value, or holds the emailed Magic Link.

#### Postconditions

- The actor sees the read-only group with each ticket, or a safe refusal.
- No booking, payment, or capacity changes.

#### Normal Flow

**Look Up Booking Details**

1. A Member opens the list of their booking groups; a Guest enters the group code and exactly one stored contact email or phone, or opens the Magic Link.
2. The system verifies ownership, the contact proof, or the link.
3. The system displays the group and each ticket with status, attendance, collected and remaining amounts.
4. The buyer also sees each ticket QR code.

#### Alternative Flows

**Step 2 — Proof does not match**

The system refuses without saying which value was wrong. Guest attempts are rate limited.

**Step 2 — Magic Link is expired or revoked**

The system refuses the link; the buyer may request the confirmation again.

**Step 3 — Group is still awaiting payment**

The buyer may retry payment or release the hold from this view.

#### Business Rules

BR-19-01, BR-19-02, BR-19-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-19-01 | A Guest needs the group code and exactly one stored contact value, or the Magic Link; the group code alone is never enough. |
| BR-19-02 | The Member list shows at most 30 of the Member own groups, newest first. |
| BR-19-03 | Only the buyer sees ticket QR codes; a ticket recipient QR email gives no group view. |
