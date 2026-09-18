## **4.1  Core Entities & Relationships**

This is a conceptual baseline, not a physical data model. Attributes, retention, identifiers and relationships not stated here remain `TBD`.

| Entity | Conceptual relationship / purpose |
| --- | --- |
| Booking | Belongs to a Guest or Member workshop journey and has one package invoice. |
| Invoice | Records the package price, 50% deposit, approved invoice adjustments and final settlement. It may have multiple payment transactions. |
| Invoice Adjustment | Belongs to an invoice and preserves reason, actor, time and customer-consent evidence; it must not overwrite the issued invoice history. |
| Payment Transaction | Belongs to an invoice or retail order. A gateway transaction is confirmed only through the approved confirmation/reconciliation mechanism. |
| Retail Order | Belongs to a Member and is created for a ready-made ring after full online payment confirmation. |
| Stock Reservation | Belongs to a paid retail order and reserves exactly one ready-ring unit. It has no automatic expiry in V1; stock model and manual-exception process are `TBD`. |
| Delivery Handoff | Belongs to a retail order only when the Member selects delivery; it records carrier, handoff reference, Staff actor, and timestamp. Recipient-data retention and failed-handoff handling are `TBD`. |
