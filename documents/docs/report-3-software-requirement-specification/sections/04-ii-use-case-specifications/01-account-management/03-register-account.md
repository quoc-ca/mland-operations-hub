### 1.3 UC-06 Register Account

#### UC Name

UC-06 Register Account

#### Primary Actors

Guest

#### Secondary Actors

SSO Provider (Firebase Authentication)

#### Description

As a Guest, I want to register with email and password or with Google so that I get a Member account.

#### Preconditions

- The Guest is not signed in.
- Firebase Authentication is configured and reachable.

#### Normal Flow

***Register with Email and Password***

1. The Guest opens the Register page.
2. The system shows the email, password and password confirmation fields and the Google option.
3. The Guest enters the email address, the password and the password confirmation.
4. The Guest submits the registration form.
5. The system checks the email format, the password length and the matching confirmation.
6. The system creates the email and password identity in Firebase Authentication.
7. The system asks Firebase Authentication to send a verification email.
8. The system creates one Member account and its profile for the verified identity.
9. The system sets the initial full name to the part of the email before "@".
10. The system signs the Member in and opens the home page, confirming the registration.

#### Alternative Flows

***Step 3.1 The Guest Registers with Google***

1. The Guest selects Sign in with Google.
2. The Guest completes the Google sign-in through Firebase Authentication.
3. The use case continues at step 8.

***Step 5.1 Registration Input Is Invalid***

1. The system shows each field error under its box (MSG86, MSG93).
2. The use case continues at step 3.

***Step 6.1 Email Is Already Registered***

1. The system asks the Guest to sign in with the original email and password (MSG89).
2. The use case ends without creating an account.

***Step 6.2 Password Is Too Weak***

1. The system informs the Guest that the password is too weak (MSG94).
2. The use case continues at step 3.

***Step 7.1 Verification Email Cannot Be Sent***

1. The system keeps the new identity without rollback.
2. The system later shows a verification reminder with a resend option on the Profile page.
3. The use case continues at step 8.

***Step 8.1 Email Is Reserved by a Pending Invitation***

1. The system refuses to create a Member account for the reserved email (MSG90).
2. The use case ends without a Member account.

***Step 8.2 Identity Has No Valid Email***

1. The system creates neither an account nor a profile (MSG95).
2. The use case ends without an account.

***Step 8.3 Account Already Exists for the Identity***

1. The system returns the existing account without changing its role, status or history.
2. The use case continues at step 10.

***Step 8.4 Account Service Is Unavailable***

1. The system informs the Guest that the account service is temporarily unavailable (MSG91).
2. The Guest selects Retry connection.
3. The use case continues at step 8.

#### Postconditions

- Exactly one active Member account and profile exist for the identity.
- The email may stay unverified until the Member verifies it.
- No Staff, Manager, Admin or Carrier role is granted.

#### Business Rule

BR-41, BR-42, BR-43, BR-46
