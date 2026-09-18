# **Part 5 — Business Rules**

The following rules are group decisions dated 17 September 2026, informed by lecturer direction. Rules not expressly stated remain `Open/TBD`.

| ID | Rule |
| --- | --- |
| BR-01 | Guest and Member may both book a workshop package. Each booking creates an invoice and requires a 50% package deposit through the Payment Gateway. |
| BR-02 | A booking is not confirmed by a gateway redirect alone; only a verified gateway confirmation/reconciliation can confirm its deposit. |
| BR-03 | Staff may add an invoice adjustment only with reason, actor, timestamp and customer consent. The issued invoice history is preserved. |
| BR-04 | The workshop final balance is the remaining 50% package price plus approved adjustments, settled cash/bank at the shop when the work is completed or collected. |
| BR-05 | Only a Member may buy a ready-made ring. Full verified online payment reserves one unit, then the Member selects pickup or third-party delivery handoff. |
| BR-06 | the system records delivery only up to staff handoff to a third-party carrier. Carrier integration, live tracking, delivery failure, return and shipping-refund workflows are excluded. |
| BR-07 | Admin Technical may manage technical configuration, integrations, monitoring and technical audit only; this actor cannot change business records. |
