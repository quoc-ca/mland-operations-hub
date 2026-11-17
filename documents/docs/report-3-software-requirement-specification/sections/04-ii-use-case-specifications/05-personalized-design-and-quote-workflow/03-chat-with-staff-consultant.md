### 5.3 Chat with Staff Consultant

#### Primary Actors

- Member

#### Secondary Actors

- Staff

#### Description

This use case allows a member to send a consultation request and exchange messages with a staff consultant about a workshop configuration, design request, or quotation. The conversation is handled by staff and does not automatically approve a design or publish a final quote.

#### Preconditions

1. The member is signed in.
2. The member has an active workshop configuration or a valid design-related request.
3. Staff consultation is available.

#### Normal Flow

**Chat with Staff Consultant**

1. The member opens the consultation function from the workshop or design request.
2. The system displays the related workshop configuration or request context.
3. The member enters and sends a message to staff.
4. The system validates and stores the message with its conversation context.
5. The system notifies the assigned staff consultant.
6. The staff consultant reviews the request and sends a response.
7. The system displays the response to the member and preserves the conversation history.

#### Alternative Flows

**Step 3 — Invalid or empty message**

1. The member submits an empty or invalid message.
2. The system rejects the message and asks the member to provide valid content.

**Step 5 — Staff is temporarily unavailable**

1. No staff consultant is available to respond immediately.
2. The system stores the message as pending and informs the member that staff will respond later.

**Step 4 — Message delivery failure**

1. The system cannot deliver or store the message.
2. The system displays a failure message and allows the member to retry.

#### Postconditions

- The member's message and staff response, if available, are stored in the conversation history.
- The related workshop configuration or design request remains unchanged unless staff performs an authorized update.
- No automatic design approval or final quotation is issued by this use case.

#### Business Rules

- BR-34-01
- BR-34-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-34-01 | Only authenticated members may access their consultation conversations. |
| BR-34-02 | Staff consultation does not replace the required staff or manager approval workflow. |

