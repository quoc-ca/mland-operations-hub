### 3.33 Record OTA Shop Add-on

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As an assigned Staff member or a Manager, I want to record a free engraving or sell a paid add-on at the Mland shop for an OTA booking so that the extra service is transparent and separate from the OTA sale. Live OTA bookings depend on partner go-live.

#### Preconditions

1. The actor is a Manager, or Staff who has the location in scope and is assigned to a session for which the booking holds a valid ticket.
2. The OTA booking is not cancelled, refunded, or awaiting cancellation.
3. A paid add-on is active in the catalogue for the booking package and location.

#### Postconditions

- A free engraving is recorded at zero charge, or a separate Mland bill is created for the accepted paid add-on.
- OTA price, payment, commission, payout, and refund records are unchanged.

#### Normal Flow

**Record OTA Shop Add-on**

1. The actor looks up the OTA booking by its Mland booking reference.
2. The actor selects a paid catalogue add-on.
3. The system creates a quote with the catalogue description and price.
4. The actor records the customer acceptance with an acceptance reference.
5. The actor creates the direct bill.
6. The system creates a separate Mland bill, and payment is collected under the Mland settlement rules.

#### Alternative Flows

**Step 1 — Booking is not available to the actor**

A non-OTA, out-of-scope, or unassigned booking is reported as not found.

**Step 2 — Laser engraving is requested**

The system records it at zero charge; no acceptance or bill is needed.

**Step 4 — Customer does not accept**

No bill is created and nothing is charged.

**Step 6 — The add-on is later found overcharged**

The excess is refunded by bank transfer with Manager approval; Staff may enter the payer account at the counter.

#### Business Rules

BR-95-01, BR-95-02, BR-95-03, BR-95-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-95-01 | Staff needs both location scope and session assignment; a Manager has global access; Admin has no authority. |
| BR-95-02 | The price of a paid add-on comes from the catalogue; the actor cannot enter an amount. |
| BR-95-03 | The bill keeps the item, price, currency, consent, actor, and payment reference. |
| BR-95-04 | Payment reaches the bill only as verified shop add-on payment evidence; refunds never use a payment gateway. |
