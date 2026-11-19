### 5.3 Chat with Staff Consultant

#### Primary Actors

- Member

#### Secondary Actors

- Staff
- Manager

#### Description

This use case allows a Member to select an owned workshop-ring design or independent Design Request, then exchange plain-text messages with Staff or Manager. The conversation is handled through a shared inbox and does not automatically approve a design, publish a final quote, create a booking, or mutate payment/order state.

#### Preconditions

1. The member is signed in.
2. The member has an active owned workshop configuration or valid independent Design Request.
3. The selected context is returned by the server with a matching code, name, version and safe preview.
4. Staff or Manager consultation is available.

#### Normal Flow

**Chat with Staff Consultant**

1. The Member opens consultation from the workshop design or Design Request.
2. The system displays a context selector with code, name, version and preview as one unit.
3. The Member confirms the context and sends a UTF-8 text message.
4. The system validates ownership, context version, message length, rate limit and idempotency, then stores the message.
5. The Staff/Manager shared inbox shows the new unread conversation through polling.
6. An authorized Staff claims a conversation in its Workshop Location scope, or a Manager claims/assigns it globally.
7. Staff or Manager sends a text response.
8. The Member sees the response after polling and the system preserves immutable history.

#### Alternative Flows

**Step 3 — Invalid or empty message**

1. The member submits an empty or invalid message.
2. The system rejects the message and asks the member to provide valid content.

**Step 5 — No authorised consultant is currently available**

1. No Staff or Manager currently claims the conversation.
2. The system keeps the conversation open, shows its pending state, and allows the Member to continue polling or close the conversation.

**Step 4 — Message delivery failure**

1. The system cannot deliver or store the message.
2. The system displays a failure message and allows the member to retry.

#### Postconditions

- The member's message and staff response, if available, are stored in the conversation history.
- The related workshop configuration or design request remains unchanged unless staff performs an authorized update.
- No automatic design approval or final quotation is issued by this use case.
- The transcript is retained according to the approved three-year business-history policy.

#### Business Rules

- BR-34-01
- BR-34-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-34-01 | Only authenticated members may access their consultation conversations. |
| BR-34-02 | Staff consultation does not replace the required Staff or Manager approval workflow. |
| BR-34-03 | Staff can access only conversations within its current Workshop Location scope; Manager has global chat authority. |
| BR-34-04 | Messages are immutable UTF-8 text, limited to 2,000 Unicode code points; raw message text is not written to application/audit logs. |

