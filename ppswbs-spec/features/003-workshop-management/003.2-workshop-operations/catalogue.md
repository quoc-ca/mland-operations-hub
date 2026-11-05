# Workshop catalogue baseline

**Date created**: 2026-11-02  
**Source**: Product catalogue image supplied by Feature 003 owner.  
**Status**: Confirmed commercial catalogue facts; operational configuration
values marked **TBD** are not approved defaults.

This catalogue supersedes older illustrative references to one “Basic Ring”
package and a two-session “Silverclay” package. It does **not** authorize an
in-place overwrite of already-published package records or booking snapshots.
Existing records must be retired prospectively or migrated through an approved
data plan.

## Published offer baseline

| Public offer | Price | Included commercial facts | Session requirement | Collection / delivery fact |
| --- | ---: | --- | --- | --- |
| Signature Workshop — Brass | 750,000 VND per person/piece | Brass, maximum 10g; basic metalworking: torch heating, soldering and shaping; personalised ring | One 1–2 hour session | Customer takes the product home the same day |
| Signature Workshop — Silver 925 | 900,000 VND per person/piece | Silver 925, maximum 4g; basic metalworking: torch heating, soldering and shaping; personalised ring | One 1–2 hour session | Customer takes the product home the same day |
| Silver Clay Workshop — 5g package | 1,350,000 VND per person/piece | S999 Silver Clay, 5g included, standard ring design, up to five stones | One 2–3 hour session | Customer takes the product home the same day |
| Silver Clay Workshop — 7g package | 1,950,000 VND per person/piece | S999 Silver Clay, 7g included, unlimited stones | One 2–3 hour session | Customer takes the product home the same day |
| Wax Casting Workshop | 1,000,000 VND per person/piece | S999 / S925 / Gold; material cost is not included; session 1 is wax carving/shaping and session 2 is polishing/finishing | Two sessions; at least two foundry-working dates must fall between them | Not same-day pickup |

Global catalogue facts:

- A price is per person/piece; reservation is required.
- Customer-facing schedule slots are 09:30–12:00, 13:00–16:00 and
  16:00–19:00.
- Capacity is limited per concrete session. The authoritative remaining seats
  are supplied by Workshop Operations at discovery and rechecked at reserve.

## Add-on catalogue facts

| Add-on | Advertised price/fact | Current modelling consequence |
| --- | --- | --- |
| Gemstones | 50,000–200,000 VND | A price range; cannot be represented as one fixed-price add-on without selectable variants or an approved quote flow. |
| Gold plating | 150,000 VND | Candidate fixed-price direct-shop add-on. |
| Laser engraving | 100,000 VND | Candidate fixed-price direct-shop add-on. The separately approved OTA benefit of free engraving for an eligible OTA buyer is an eligibility/bundling rule, not a contradiction of this retail price. |
| Extra S999 Silver Clay | 900,000 VND / 5g | Candidate fixed-price direct-shop add-on. |
| Extra S925 Silver | Market price | No fixed catalogue price; requires an approved staff quote/price-snapshot rule before it can be sold through a fixed-price add-on flow. |

## Mapping to the current Operations model

**Facts supported by the current model**

- It can store a name, description, public price, participant bounds, required
  session count and package lifecycle for all five offers.
- The four Signature/Silver Clay offers use `requiredSessionCount = 1`; Wax
  Casting uses `requiredSessionCount = 2`.
- The package does not own concrete dated sessions. A customer selects future
  eligible sessions and Operations performs the final capacity decision.

**Design gaps and approved scheduling rules**

1. The present `minimumSameDaySlotGap` only constrains sessions on the same
   local date. It cannot express the approved Wax Casting rule. Wax Casting
   must use at least two **foundry-working dates strictly between** the local
   dates of its two sessions. A foundry-working date is Monday through Friday;
   Saturday and Sunday are skipped even though customer workshops may operate
   on those days. Slot/time does not affect the calculation: for example,
   Monday 16:00–19:00 followed by Thursday 09:30–12:00 is valid because
   Tuesday and Wednesday are the two intervening foundry-working dates.
   The sessions must still be distinct, chronological and at the same location.
2. The catalogue does not state a minimum or maximum tickets per booking,
   concrete offering locations, or capacity of each session. The existing
   `minParticipants`/`maxParticipants` fields therefore remain required
   Operations configuration, not facts inferred from the poster. Capacity
   always remains a second, independent limit.
3. Material limits, duration, included stones and pickup statements are only
   stored in the current public description. Structured fields would be a new
   data/API contract and need separate design approval.
4. The V1 OTA mapping still names “Basic Ring” as its sellable offer. Whether
   that legacy name maps to either Signature Workshop variant is unresolved;
   do not rename an OTA mapping or publish a provider change from this
   catalogue alone.

## Implementation and data-release boundary

Before operational data is changed, prepare a Manager-reviewed release plan
that creates the five offers, configures their participant bounds and eligible
locations, publishes only supported offers, and retires legacy offers
prospectively. It must preserve package and booking snapshots already used by
accepted bookings. No migration or seed is authorized by this document.

## Approved Wax Casting scheduling rule

For a Wax Casting booking or session change, Operations must accept exactly two
distinct sessions at the same location in chronological order. It counts only
the local dates strictly between their local session dates. Monday–Friday count
as foundry-working dates; Saturday and Sunday do not count. The count must be
at least `2`; there is no maximum. This is a date rule, not a six-slot rule: session times and slot
ordinals never change the count. The rule does not close normal workshops on a
weekend; it only excludes weekends while calculating the foundry interval.
