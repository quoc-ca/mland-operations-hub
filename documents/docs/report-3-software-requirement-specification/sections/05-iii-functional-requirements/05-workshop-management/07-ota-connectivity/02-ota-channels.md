#### 5.7.2 OTA Channels

This screen allows Managers to:

- See every OTA channel mapping (for example Klook) with its state, commercial status, booking and sales-control badges.
- Create or edit a mapping between a provider product and option and a Mland workshop offer and session.
- Record externally confirmed price terms for a mapping.
- Start a preparation sync or a full resync of a mapping, with a reason.
- Close channel sales for a mapping, with a reason.
- Enable bookings for a mapping whose sales are closed, after attesting the 12 provider readiness checks.

The screen requires sign-in; the backend decides permissions. Per the spec, every OTA business action, including booking enablement, belongs to the Manager and Admin has no OTA route; the code still labels the enablement button "Admin Enable". Connection credentials are environment configuration and are not shown or edited here. The labels on this screen are in English.

| Field Name | Description |
| --- | --- |
| Mapping card header | Provider name with state badge (DRAFT, PREPARATION_SYNC, SALES_CLOSED, BOOKING_ENABLED, SYNC_SUSPENDED, DISABLED), commercial status (APPROVED or pending), "BOOKINGS OPEN" and any sales-control status. |
| Mapping details | Mapping ID and version, provider product and option references, workshop offer reference, session reference and readiness review status. |
| + Create Mapping / Edit Mapping | Opens the mapping dialog; Provider Product Reference, Provider Option Reference, Workshop Offer Reference and Session Reference are all required. |
| Price Terms | Opens "Record Confirmed Price Terms": retail amount and currency, target net amount (payout) and currency, a provider confirmation evidence reference (safe reference only, no secrets or personal data) and a reason. |
| Prep Sync / Full Resync | Asks for a reason, then requests a preparation sync or full resync of the mapping. |
| Sales Close | Asks for a reason, then closes channel sales; disabled when sales are already closed. |
| Admin Enable | Shown only for a mapping in SALES_CLOSED. Requires a readiness record reference, an attestation reason and a VERIFIED, FAILED or MISSING status with evidence for each of the 12 readiness checks. |
| Empty state | "No OTA channel mappings configured." with "Create First Mapping". |
