## 12. Workshop Ring Design Configuration (FE-12)

**Implementation status:** Backend and frontend slices exist, with Member-only protected persistence and Guest local preview.

### 12.1 Class Diagram

{{r4-cd-fe12-ring-design width=100% align=center}}

The module owns catalogue-controlled design options, immutable Member design versions, evaluation snapshots and archive state. Guest preview never creates server state. The module does not attach a design to a booking.

### 12.2 Sequence Diagram — Configure and Save Design Version

{{r4-sd-fe12-save-design width=100% align=center}}

### 12.3 Sequence Diagram — Evaluate Design and Produce Recommendation

{{r4-sd-fe12-evaluate-recommendation width=100% align=center}}

### 12.4 Sequence Diagram — Edit or Archive Member Design

{{r4-sd-fe12-edit-archive width=100% align=center}}

