## 13. Member Consultation Chat (FE-13)

**Implementation status:** Consultation chat backend/frontend slices exist; current Member-only and Staff/Manager operational boundaries remain authoritative.

### 13.1 Class Diagram

{{r4-cd-fe13-consultation-chat width=100% align=center}}

The module owns conversations, immutable messages, claims, context snapshots, soft-hidden Member visibility and audit evidence. It does not mutate design, booking, payment, quote or order state.

Consultation is one of the post-design options, not an approval shortcut. A Member may open a context-linked conversation from a saved Feature-012 design or a Feature-004 request, or start a contextless consultation. Staff and Manager may discuss the design, but the chat does not choose the workshop recommendation, approve a custom request, set a quote, create a product order or create a booking.

### 13.2 Sequence Diagram — Create Contextless or Design-Context Conversation

{{r4-sd-fe13-create-conversation width=100% align=center}}

### 13.3 Sequence Diagram — Send Message with Scope and Idempotency Checks

{{r4-sd-fe13-send-message width=100% align=center}}

### 13.4 Sequence Diagram — Staff/Manager Close and Member Soft-Hide Conversation

{{r4-sd-fe13-close-hide-chat width=100% align=center}}

