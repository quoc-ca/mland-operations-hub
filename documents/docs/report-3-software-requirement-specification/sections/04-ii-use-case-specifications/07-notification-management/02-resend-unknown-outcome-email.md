### 7.2 UC-72 Resend Unknown-Outcome Email

#### UC Name

UC-72 Resend Unknown-Outcome Email

#### Primary Actors

Admin

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As an Admin, I want to resend an email whose delivery outcome is unknown so that the customer receives it without repeating the business action.

#### Preconditions

- The actor is signed in as an active Admin.
- The email request is in the unknown-outcome state.
- The Admin has checked the Mail Gateway (SMTP) evidence for that email.

#### Normal Flow

***Resend Unknown-Outcome Email***

1. The Admin opens the email request list.
2. The Admin filters the list by the unknown-outcome state.
3. The system lists the matching requests with masked recipient, template and attempt count.
4. The Admin selects a request.
5. The system shows the request details and its delivery attempts.
6. The Admin chooses to resend the email.
7. The system warns that the customer may receive a duplicate email (MSG357).
8. The Admin confirms acceptance of the duplicate-delivery risk.
9. The Admin enters the reason for the resend.
10. The system creates a linked request with the original recipient and rendered content.
11. The system records the resend, the reason and the accepted risk in the operation history.
12. The system confirms that the resend has been queued for the Mail Gateway (SMTP) (MSG351).

#### Alternative Flows

***Step 3.1 No Request Has an Unknown Outcome***

1. The system informs the Admin that no request matches the filters (MSG350).
2. The use case ends without any resend.

***Step 6.1 Actor Is a Manager***

1. The system refuses the resend of an unknown-outcome email (MSG01).
2. The system does not change the request.
3. The use case ends without any resend.

***Step 6.2 Request Is No Longer in the Unknown-Outcome State***

1. The system informs the Admin that the request state does not allow this action (MSG353).
2. The use case ends without any resend.

***Step 8.1 Admin Does Not Accept the Duplicate Risk***

1. The system informs the Admin that the duplicate-delivery risk must be accepted (MSG358).
2. The system does not create a request.
3. The use case continues at step 8.

***Step 9.1 Reason Is Missing or Too Short***

1. The system informs the Admin that a reason is required (MSG352).
2. The system does not create a request.
3. The use case continues at step 9.

#### Postconditions

- A linked request is queued with the original recipient and content.
- The unknown-outcome request and the business record remain unchanged.
- The resend is recorded with the Admin, the reason and the accepted duplicate risk.

#### Business Rule

BR-217, BR-211, BR-213, BR-216
