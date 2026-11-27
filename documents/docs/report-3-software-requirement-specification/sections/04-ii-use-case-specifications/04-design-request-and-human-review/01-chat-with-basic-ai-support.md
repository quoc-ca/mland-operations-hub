### 4.1 UC-07 Chat with Basic AI Support

#### UC Name

UC-07 Chat with Basic AI Support

#### Primary Actors

Guest, Member

#### Secondary Actors

AI API (Gemini)

#### Description

As a Guest or Member, I want to ask the AI assistant about products, workshops and store information so that I get quick answers without waiting for Staff.

#### Preconditions

- The actor is a Guest or a signed-in active Member.
- The actor has prompts left in the daily allowance.

#### Normal Flow

***Chat with Basic AI Support***

1. The actor selects the AI assistant button.
2. The system shows the chat page, the advisory notice and the prompts left today.
3. The actor types a question.
4. The actor sends the question.
5. The system checks that the question has a valid length.
6. The system identifies the Member account or the Guest AI session.
7. The system takes one prompt from the actor's daily allowance.
8. The system screens the question for unsafe or out-of-scope instructions.
9. The system sends the question to Gemini.
10. The system stores the question and the answer for 30 days.
11. The system shows the answer with the advisory notice and the updated prompts left.

#### Alternative Flows

***Step 1.1 Staff, Manager, Admin or Carrier Opens the AI Assistant***

1. The system does not show the AI assistant button to the actor.
2. The system refuses a direct question from the actor (MSG01).
3. The use case ends without an answer.

***Step 5.1 Question Is Empty or Too Long***

1. The system informs the actor that the question is invalid (MSG261).
2. The system does not take a prompt from the allowance.
3. The use case continues at step 3.

***Step 6.1 Guest Has No AI Session Yet***

1. The system creates a Guest AI session valid for 24 hours.
2. The system returns the session to the Guest browser.
3. The use case continues at step 7.

***Step 6.2 Guest AI Session Is Invalid or Expired***

1. The system informs the Guest that the AI session is no longer valid (MSG265).
2. The system does not answer the question.
3. The use case ends without an answer.

***Step 7.1 Daily Allowance Is Used Up***

1. The system informs the actor that no prompts are left today (MSG262).
2. The system disables the question box until the allowance resets.
3. The use case ends without an answer.

***Step 8.1 Question Asks for Unsafe or Restricted Help***

1. The system records the refused question with a safe failure code.
2. The system informs the actor that the assistant cannot handle the request (MSG263).
3. The use case ends without an answer.

***Step 9.1 Question Is Outside the Public Topics***

1. The system answers with a reminder of the topics the assistant covers.
2. The use case continues at step 10.

***Step 9.2 Gemini Does Not Answer***

1. The system uses the prepared public answer for the detected topic.
2. The use case continues at step 10.

***Step 9.3 AI Answer Cannot Be Produced***

1. The system records the failed question with a safe failure code.
2. The system informs the actor that the AI service is unavailable (MSG264).
3. The use case ends without an answer.

#### Postconditions

- The question and answer are stored with a 30-day deletion date.
- One prompt is taken from the actor's daily allowance.
- No order, booking, payment, price or design record is changed.

#### Business Rule

BR-10, BR-144, BR-145, BR-146
