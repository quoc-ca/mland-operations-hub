### 1.1 UC-01 Sign In

#### UC Name

UC-01 Sign In

#### Primary Actors

Guest, Member, Staff, Manager, Admin, Carrier

#### Secondary Actors

SSO Provider (Firebase Authentication)

#### Description

As a user with a platform account, I want to sign in with email and password or with Google so that I can use the features of my role.

#### Preconditions

- The actor is not signed in.
- Firebase Authentication is configured and reachable.

#### Normal Flow

***Sign In with Email and Password***

1. The actor opens the Sign In page.
2. The system shows the email and password fields and the Google sign-in option.
3. The actor enters the email address and the password.
4. The actor submits the sign-in form.
5. The system checks the email format and the password length.
6. The system verifies the credentials with Firebase Authentication.
7. The system resolves the platform account, role and status from the verified identity.
8. The system starts the session with the role and status stored by the platform.
9. The system opens the landing page of the actor's role, confirming a successful sign-in.

#### Alternative Flows

***Step 3.1 The Actor Signs In with Google***

1. The actor selects Sign in with Google.
2. The system opens the Google sign-in window through Firebase Authentication.
3. The actor completes the Google sign-in.
4. The use case continues at step 7.

***Step 5.1 Sign-In Input Is Invalid***

1. The system shows the field error under the email or password box (MSG86).
2. The system keeps the entered email address.
3. The use case continues at step 3.

***Step 6.1 Email or Password Is Incorrect***

1. The system informs the actor that the email or password is incorrect (MSG87).
2. The system does not reveal which value is wrong.
3. The use case continues at step 3.

***Step 6.2 Too Many Failed Attempts***

1. The system informs the actor that too many invalid requests were made (MSG88).
2. The use case ends without a session.

***Step 6.3 Email Is Registered with Another Sign-In Method***

1. The system refuses the Google sign-in for an email already registered with a password.
2. The system asks the actor to sign in with the original email and password (MSG89).
3. The use case ends without merging or creating an account.

***Step 7.1 Account Is Suspended or Not Eligible***

1. The system refuses the session for a suspended account (MSG90).
2. The use case ends without a session.

***Step 7.2 Email Is Reserved by a Pending Invitation***

1. The system detects that a new identity uses the email of a pending Staff or Manager invitation.
2. The system refuses the sign-in and creates no Member account (MSG90).
3. The use case ends without a session.

***Step 7.3 Invited Staff or Manager Signs In***

1. The system activates the pending account when its email is verified and an active location assignment exists.
2. The system records the activation in the account audit.
3. The use case continues at step 8.

***Step 7.4 Identity Has No Platform Account***

1. The system creates one Member account and its initial profile for the new verified identity.
2. The use case continues at step 8.

***Step 7.5 Account Service Is Unavailable***

1. The system informs the actor that the account service is temporarily unavailable (MSG91).
2. The actor selects Retry connection.
3. The use case continues at step 7.

***Step 9.1 The Actor Came from a Protected Page***

1. The system returns the actor to the requested page on this site.
2. The system sends a Carrier to the carrier portal instead.
3. The use case ends with the actor signed in.

#### Postconditions

- The actor has a session for exactly one platform account.
- The session uses the role and status stored by the platform, never browser data.
- A failed sign-in creates no session and changes no account.

#### Business Rule

BR-41, BR-42, BR-43, BR-44, BR-50
