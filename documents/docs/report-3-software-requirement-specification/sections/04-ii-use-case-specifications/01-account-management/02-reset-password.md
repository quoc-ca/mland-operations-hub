### 1.2 UC-02 Reset Password

#### UC Name

UC-02 Reset Password

#### Primary Actors

Guest, Member, Staff, Manager, Admin, Carrier

#### Secondary Actors

SSO Provider (Firebase Authentication)

#### Description

As a user who forgot my password, I want to receive a password reset email so that I can set a new password and sign in again.

#### Preconditions

- The actor is on the Sign In page.
- The account uses email and password sign-in.

#### Normal Flow

***Reset Password***

1. The actor selects Forgot password on the Sign In page.
2. The system shows the password recovery form.
3. The actor enters the email address.
4. The actor submits the request.
5. The system checks that the email address is present and well formed.
6. The system asks Firebase Authentication to send a password reset email.
7. The system shows a notice that does not reveal whether the account exists (MSG92).
8. The actor opens the reset link in the email.
9. The actor enters a new password on the Firebase Authentication reset page.
10. The system confirms through Firebase Authentication that the password has been reset.

#### Alternative Flows

***Step 5.1 Email Is Missing or Invalid***

1. The system shows the email error under the email box (MSG86).
2. The use case continues at step 3.

***Step 6.1 Firebase Rejects the Email Format***

1. The system shows the email error under the email box (MSG86).
2. The use case continues at step 3.

***Step 6.2 Email Has No Account or Sending Fails***

1. The system shows the same neutral notice as for a sent email (MSG92).
2. The use case ends without sending an email.

***Step 9.1 Reset Link Has Expired or Was Used***

1. The system shows the expiry notice of Firebase Authentication.
2. The use case continues at step 1.

#### Postconditions

- The password is changed only in Firebase Authentication.
- The platform stores no password and no reset link.
- The response never reveals whether an email has an account.

#### Business Rule

BR-41, BR-45
