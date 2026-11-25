## 12. Workshop Ring Design Configuration (FE-12)

**Implementation status:** Backend and frontend slices exist, with Member-only protected persistence and Guest local preview.

### 12.1 Class Diagram

<!-- Temporarily disabled: r4-cd-fe12-ring-design. The approved Drive image will be reconnected after the shared asset update. -->

The module owns catalogue-controlled design options, immutable Member design versions, evaluation snapshots and archive state. Guest preview never creates server state. The module does not attach a design to a booking.

The current flow is intentionally different from the earlier booking-attached design flow. After a Member saves a design, the saved-design page presents an advisory workshop recommendation based on the server evaluation. The Member may follow that recommendation or use the saved design as an idea/reference for a Feature-004 custom-manufacturing request. All workshop discovery actions remain visible. The design itself does not create a booking, ticket, payment, quote or order; purchase of the resulting custom product starts only after human review, approved quote and full payment.

### 12.2 Sequence Diagram — Configure and Save Design Version

<!-- Temporarily disabled: r4-sd-fe12-save-design. The approved Drive image will be reconnected after the shared asset update. -->

### 12.3 Sequence Diagram — Evaluate Design and Produce Recommendation

<!-- Temporarily disabled: r4-sd-fe12-evaluate-recommendation. The approved Drive image will be reconnected after the shared asset update. -->

### 12.4 Sequence Diagram — Edit or Archive Member Design

<!-- Temporarily disabled: r4-sd-fe12-edit-archive. The approved Drive image will be reconnected after the shared asset update. -->

