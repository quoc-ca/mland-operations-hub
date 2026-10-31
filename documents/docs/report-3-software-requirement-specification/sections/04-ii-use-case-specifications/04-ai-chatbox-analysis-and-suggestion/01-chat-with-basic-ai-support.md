### 4.1 Chat with Basic AI Support

#### Primary Actors

Guest, Member

#### Secondary Actors

AI API

#### Description

As a Guest or Member, I want to ask natural-language questions about products, workshops, store information, and supported FAQs so that I can receive basic guidance before continuing with a platform workflow.

#### Preconditions

1. The platform is available.
2. The approved Gemini AI service is configured and enabled.
3. The user's request is within the supported product, workshop, store-information, or FAQ scope.
4. The user has not exceeded the configured AI request limit.

#### Normal Flow

**Chat with Basic AI Support**

1. The Guest or Member opens the AI Chatbox.
2. The system displays the chat input and the permitted support scope.
3. The actor enters a natural-language question.
4. The system validates the request scope, length, and rate limit.
5. The system sends the permitted prompt and relevant public information to the AI API.
6. The AI API returns a text response.
7. The system validates the response and displays it in the chat conversation.
8. The actor may ask a follow-up question or continue to another platform function.
9. The system records the permitted text prompt and response for the approved retention period.

#### Alternative Flows

**Step 3 — Question is empty, too long, or outside the supported scope**

The system asks the actor to provide a supported question and does not send the invalid request to the AI API.

**Step 4 — AI request limit has been exceeded**

The system informs the actor that the request limit has been reached and provides the configured retry guidance.

**Step 5 — AI API is unavailable or times out**

The system displays a temporary service-unavailable message and allows the actor to retry later. No business transaction is created.

**Step 6 — AI response is empty, invalid, or not relevant**

The system does not present the response as a confirmed business answer and asks the actor to rephrase the question or contact Staff.

**Step 8 — Actor asks for a business decision**

The system explains that AI support cannot confirm a booking, payment, final price, custom-order acceptance, or manufacturing feasibility. The actor is directed to the relevant workflow or Staff review path.

#### Postconditions

- The actor receives a basic AI response or an appropriate error/scope message.
- No booking, order, payment, price publication, or custom-manufacturing decision is created by this use case.
- Permitted text prompts and responses are retained for no more than 30 days.
- AI image input is not accepted or retained by this text-support use case.

#### Business Rules

BR-29-01, BR-29-02, BR-29-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-29-01 | Basic AI support is limited to product, workshop, store-information, and approved FAQ questions. |
| BR-29-02 | AI support may provide guidance but must not confirm transactions, publish prices, or make business decisions. |
| BR-29-03 | Application-level text prompts and responses must not be retained for more than 30 days. |
