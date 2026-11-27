### 7.1 UC-69 Manage Email Templates and Delivery

#### UC Name

UC-69 Manage Email Templates and Delivery

#### Primary Actors

Manager, Admin

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Manager or Admin, I want to maintain the transactional email templates and inspect, resend, redeliver or cancel email requests so that customers receive correct confirmation emails.

#### Preconditions

- The actor is signed in as an active Manager or Admin.
- The transactional email templates are registered in the system.

#### Normal Flow

***Maintain a Template Version***

1. The actor opens the email template list.
2. The system shows each template with its status, active version and latest draft.
3. The actor selects a template.
4. The system shows the allowed placeholders and the version history of the template.
5. The actor chooses to create a new draft version.
6. The actor enters the subject, the HTML body and the plain-text body.
7. The system checks the content and the placeholders used.
8. The system saves the content as a new draft version (MSG345).
9. The actor requests a preview of the draft.
10. The system renders the draft with synthetic sample data only.
11. The actor confirms activation of the draft version.
12. The system makes the draft the only active version and deactivates the previous active version.
13. The system confirms that the version is now active for new emails (MSG347).

#### Alternative Flows

***Step 1.1 Actor Is Not a Manager or Admin***

1. The system refuses access to email management (MSG01).
2. The use case ends without showing any template or request.

***Step 1.2 Actor Inspects Email Requests***

1. The actor opens the email request list.
2. The system lists the requests, newest first, with masked recipient, template, state and attempt count.
3. The actor filters the list by delivery state, template code or business reference.
4. The actor selects a request.
5. The system shows the request details, its delivery attempts and its operation history.
6. The use case ends after the inspection.

***Step 1.3 No Email Request Matches the Filters***

1. The system informs the actor that no request matches the filters (MSG350).
2. The actor clears or changes the filters.
3. The system shows the requests that match the new filters.
4. The use case ends after the inspection.

***Step 1.4 Actor Resends a Sent or Failed Email***

1. The actor selects a sent or failed request and chooses to resend it.
2. The actor enters the reason for the resend.
3. The system creates a linked request with the original recipient and rendered content.
4. The system records the resend in the operation history.
5. The system queues the new request for the Mail Gateway (SMTP) (MSG351).
6. The use case ends after the resend is queued.

***Step 1.5 Reason Is Missing***

1. The system informs the actor that a reason is required (MSG352).
2. The system does not change the request.
3. The use case ends without any change.

***Step 1.6 Request State Does Not Allow the Action***

1. The system informs the actor that the request state does not allow this action (MSG353).
2. The system does not change the request.
3. The use case ends without any change.

***Step 1.7 Actor Cancels a Waiting Email***

1. The actor selects a pending or retry-scheduled request and chooses to cancel it.
2. The actor enters the reason for the cancellation.
3. The system sets the request to cancelled and stops its delivery.
4. The system records the cancellation in the operation history.
5. The system confirms that the request has been cancelled (MSG354).
6. The use case ends after the cancellation.

***Step 1.8 Actor Redelivers With a Renewed Link or Ticket***

1. The actor selects a request and chooses to redeliver it with a renewed link or ticket.
2. The actor enters the reason for the redelivery.
3. The system creates a linked request without the expired or revoked credential.
4. The system records the redelivery in the operation history.
5. The system queues the new request for the Mail Gateway (SMTP) (MSG355).
6. The use case ends after the redelivery is queued.

***Step 1.9 Request Is an Internal Account Invitation***

1. The system refuses resend, redelivery and cancellation of the invitation request (MSG356).
2. The use case ends without any change.

***Step 2.1 Templates Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The use case ends without showing templates.

***Step 3.1 Template Is Managed by Firebase***

1. The system shows the Firebase-managed template as read-only.
2. The system refuses any new draft for that template.
3. The use case continues at step 3.

***Step 5.1 Actor Edits an Existing Draft***

1. The actor selects a version in draft status and chooses to edit it.
2. The actor changes the subject or the bodies.
3. The system checks the content and the placeholders used.
4. The system saves the changes to the same draft (MSG345).
5. The use case continues at step 9.

***Step 5.2 Version Is No Longer a Draft***

1. The system informs the actor that only a draft version can be changed (MSG346).
2. The system keeps the active or archived version unchanged.
3. The use case continues at step 4.

***Step 7.1 Content Is Incomplete***

1. The system informs the actor that the subject and the bodies are required (MSG348).
2. The system keeps the entered content.
3. The use case continues at step 6.

***Step 7.2 Content Uses a Placeholder That Is Not Allowed***

1. The system lists the placeholders that are not allowed for the template (MSG349).
2. The system does not save the draft.
3. The use case continues at step 6.

***Step 11.1 Actor Activates an Archived Version***

1. The actor selects a deactivated version and confirms its activation.
2. The system makes it the only active version and deactivates the current one.
3. The use case continues at step 13.

#### Postconditions

- A template has at most one active version, and new emails use it.
- Active and referenced versions remain unchanged and are never deleted.
- Each resend, redelivery or cancellation is recorded with actor, role, reason and states.
- The original request and its history remain unchanged.

#### Business Rule

BR-206, BR-207, BR-208, BR-209, BR-210, BR-211, BR-212, BR-213, BR-214, BR-215, BR-216
