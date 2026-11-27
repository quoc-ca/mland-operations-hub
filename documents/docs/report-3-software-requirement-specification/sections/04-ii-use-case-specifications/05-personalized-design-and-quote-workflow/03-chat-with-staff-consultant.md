### 5.3 Chat with Staff Consultant

| Use Case ID | UC-38 | Use Case Name | Chat with Staff Consultant |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | Staff, Manager |

#### Description

As a Member, I want to exchange plain-text messages with Staff or a Manager about my design so that I can receive consultation.

#### Trigger

A Member opens consultation from a workshop design or independent Design Request.

#### Preconditions

1. The Member is signed in.
2. The Member has an active owned workshop configuration or valid independent Design Request.
3. The server returns the selected context with matching code, name, version, and safe preview.
4. Staff or Manager consultation is available.

#### Postconditions

1. The Member's message and any consultant response are stored in the conversation history.
2. The related workshop configuration or design request remains unchanged unless authorized staff updates it.
3. No automatic design approval or final quotation is issued.
4. The transcript is retained per the approved three-year business-history policy.

#### Normal Flow

1. Member opens consultation from the workshop design or Design Request.
2. System displays a context selector with code, name, version, and preview.
3. Member confirms the context and sends a UTF-8 text message.
4. System validates ownership, context version, message length, rate limit, and idempotency, then stores the message.
5. System displays the new unread conversation in the Staff and Manager shared inbox through no-cache polling; visible tabs poll every 3 seconds and hidden tabs every 6 seconds.
6. Staff claims the conversation within their Workshop Location scope.
7. Staff or Manager sends a text response.
8. System displays the response to the Member after polling and preserves immutable history.

#### Alternative Flows

5a. No authorized consultant claims the conversation → System keeps it open in pending state while Member polls or closes.

6a. Manager claims or assigns the conversation globally → System assigns the conversation regardless of Workshop Location scope.

#### Exception Flows

3a. Message is empty or invalid → System rejects the message and asks the Member for valid content.

4a. Message delivery or storage fails → System displays a failure message and allows the Member to retry.

#### Business Rules

BR-34-01, BR-34-02, BR-34-03, BR-34-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-34-01 | Only authenticated members may access their consultation conversations. |
| BR-34-02 | Staff consultation does not replace the required Staff or Manager approval workflow. |
| BR-34-03 | Staff can access only conversations within its current Workshop Location scope; Manager has global chat authority. |
| BR-34-04 | Messages are immutable UTF-8 text, limited to 2,000 Unicode code points; raw message text is not written to application/audit logs. |
