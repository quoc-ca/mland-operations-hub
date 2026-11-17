# Feature-011 Logical Entities

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

| Entity | Responsibility | Required rules |
|---|---|---|
| Review | Current public/moderated review for one source | Immutable source/target mapping; rating 1–5; text ≤2,000; status vocabulary closed. |
| ReviewRevision | Historical owner-content snapshot | Append-only; revision number unique per review. |
| ReviewReply | One current Manager reply | One-to-one with review; Manager-only write; edits audited. |
| ReviewModerationEvent | Status/action evidence | Append-only; records actor, reason, previous/new status and correlation. |
| ReviewEligibilitySource | Verified source boundary | Workshop attendance or retail order line only; never a public transaction DTO. |
| PublicReviewAggregate | Public count/average | Includes `PUBLIC` rows only; projection must be repairable if used. |

These are logical boundaries, not approved Java classes or physical tables. Physical mapping belongs to the later design/implementation decision and must preserve the constraints in `data-model.md`.
