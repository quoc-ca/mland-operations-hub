## 12. Workshop Ring Design Configuration (FE-12)

**Implementation status:** Backend and frontend slices exist, with Member-only protected persistence and Guest local preview.

### 12.1 Class Diagram

{{r4-cd-fe12-ring-design width=100% align=center}}

The module owns catalogue-controlled design options, immutable Member design versions, evaluation snapshots and archive state. Guest preview never creates server state. The module does not attach a design to a booking.

FE-12 also owns the Admin difficulty-policy API and its append-only policy store. FE-09 calls this API; it does not persist a second policy. The server validates complete option-score coverage and active published models before publication. Newly active options without a published score remain visible but unselectable.

The current flow is intentionally different from the earlier booking-attached design flow. After a Member saves a design, the saved-design page presents an advisory workshop recommendation based on the server evaluation. The Member may follow that recommendation or use the saved design as an idea/reference for a Feature-004 custom-manufacturing request. All workshop discovery actions remain visible. The design itself does not create a booking, ticket, payment, quote or order; purchase of the resulting custom product starts only after human review, approved quote and full payment.

### 12.2 Sequence Diagram — Configure and Save Design Version

{{r4-sd-fe12-save-design width=100% align=center}}

### 12.3 Sequence Diagram — Evaluate Design and Produce Recommendation

{{r4-sd-fe12-evaluate-recommendation width=100% align=center}}

Each accepted evaluation reads one effective policy version. The server stores its score, level, recommendation and version with the immutable design snapshot. Later policy publication affects later evaluations only; history reads return stored outcomes without recalculation. The score has no fixed maximum, and a workshop recommendation remains advisory.

### 12.4 Sequence Diagram — Edit or Archive Member Design

{{r4-sd-fe12-edit-archive width=100% align=center}}

