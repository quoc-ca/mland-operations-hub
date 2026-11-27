## 4. Design Request and Human Review (FE-04)

### 4.1 Class Diagram

{{r4-cd-fe04-design-review width=100% align=center}}

### 4.2 Sequence Diagram — Submit Member Design Request

{{r4-sd-fe04-submit-request width=100% align=center}}

### 4.3 Sequence Diagram — Staff or Manager Reviews Request

{{r4-sd-fe04-human-review width=100% align=center}}

### 4.4 Sequence Diagram — Clarification, Quote Handoff and Member Payment

{{r4-sd-fe04-clarification-quote width=100% align=center}}

### 4.5 Current review and quote interaction

The review screen presents acceptance and quote preparation as one confirmed Staff action. For an accepted request, the UI validates a positive amount up to 1,000,000,000 VND and a payment deadline of 24–72 hours, records the accepted decision, and prepares the quote; the resulting order waits in `QUOTE_PENDING` for Manager approval. An acceptance note is optional and receives a default explanation when omitted. Rejection and clarification still require a non-empty explanation of at most 2,000 characters. When a saved design snapshot is present, Staff and Managers can open a read-only visual preview and technical-specification modal.

