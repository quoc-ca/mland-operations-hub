### 4.1 Chat with Basic AI Support

| Use Case ID | UC-33 | Use Case Name | Chat with Basic AI Support |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | AI API |

#### Description

As a Guest or Member, I want to ask basic questions in the AI chatbox so that I receive product, workshop, store, and FAQ guidance.

#### Trigger

A Guest or Member opens the AI Chatbox.

#### Preconditions

1. The platform is available.
2. The approved Gemini AI service is configured and enabled.
3. The request is within the supported product, workshop, store-information, or FAQ scope.
4. The actor has not exceeded the configured AI request limit.

#### Postconditions

1. The actor receives a basic AI response or an appropriate scope or error message.
2. No booking, order, payment, price publication, or custom-manufacturing decision is created.
3. Permitted text prompts and responses are retained for at most 30 days.
4. AI image input is neither accepted nor retained by this text-support use case.

#### Normal Flow

1. Guest or Member opens the AI Chatbox.
2. System displays the chat input and the permitted support scope.
3. Guest or Member enters a natural-language question.
4. System validates the request scope, length, and rate limit.
5. System sends the permitted prompt and relevant public information to AI API.
6. AI API returns a text response.
7. System validates the response and displays it in the chat conversation.
8. System records the permitted text prompt and response for the approved retention period.

#### Alternative Flows

7a. Guest or Member asks a follow-up question → System repeats steps 3 to 8.

#### Exception Flows

3a. Question is empty, too long, or outside supported scope → System requests a supported question without calling AI API.

3b. Actor asks for a business decision → System explains AI cannot confirm bookings, prices, or feasibility and directs to Staff.

4a. AI request limit is exceeded → System informs the actor and provides configured retry guidance.

5a. AI API is unavailable or times out → System shows a temporary unavailability message and allows retrying later.

6a. AI response is empty, invalid, or irrelevant → System rejects it as a confirmed answer and suggests rephrasing or contacting Staff.

#### Business Rules

BR-29-01, BR-29-02, BR-29-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-29-01 | Basic AI support is limited to product, workshop, store-information, and approved FAQ questions. |
| BR-29-02 | AI support may provide guidance but must not confirm transactions, publish prices, or make business decisions. |
| BR-29-03 | Application-level text prompts and responses must not be retained for more than 30 days. |
