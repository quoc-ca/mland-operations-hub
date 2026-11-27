### 3.26 UC-57 Replace Refund Account Key

#### UC Name

UC-57 Replace Refund Account Key

#### Primary Actors

Manager

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Manager, I want to replace the key that protects stored refund bank accounts so that no account number remains encrypted with the old key.

#### Preconditions

- The actor is signed in as an active Manager.
- The new encryption key is configured and the service has been restarted.

#### Normal Flow

***Replace Refund Account Key***

1. The Manager opens the refund account key replacement screen.
2. The system warns that every stored account number will be deleted and requested again.
3. The Manager selects to run the replacement.
4. The system asks for a reason in a dialog.
5. The Manager enters the reason and confirms.
6. The system finds every refund request that still holds an encrypted account number.
7. The system deletes the encrypted number of each request and keeps its mask.
8. The system returns each request to waiting for an account and voids its approval.
9. The system sends a new account link to the payer of each request.
10. The system writes one audit record per request without the account number.
11. The system shows the requests reset, the links sent and the failures (MSG250).

#### Alternative Flows

***Step 3.1 Actor Is Not a Manager***

1. The system refuses the replacement (MSG01).
2. The system changes no refund request.
3. The use case ends without a replacement.

***Step 5.1 Reason Is Missing***

1. The system shows that a reason is required (MSG235).
2. The system runs nothing.
3. The use case continues at step 5.

***Step 5.2 Same Replacement Is Sent Again***

1. The system returns the stored result of the earlier run.
2. The system touches no refund request.
3. The use case continues at step 11.

***Step 6.1 No Request Holds an Account Number***

1. The system resets no request and sends no link.
2. The use case continues at step 11.

***Step 7.1 One Request Cannot Be Reset***

1. The system counts the request as failed and continues with the other requests.
2. The Manager runs the replacement again later, which touches only requests still holding a number.
3. The use case continues at step 11.

#### Postconditions

- No refund request holds an account number encrypted with the old key.
- Each reset request waits for a new account and needs a new Manager approval.
- Each reset request has its own audit record.

#### Business Rule

BR-137, BR-136, BR-143
