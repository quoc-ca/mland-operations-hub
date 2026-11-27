### 1.4 UC-27 Change Password

#### UC Name

UC-27 Change Password

#### Primary Actors

Member, Staff, Manager, Admin, Carrier

#### Secondary Actors

SSO Provider (Firebase Authentication)

#### Description

As a signed-in user with a password account, I want to change my password after confirming the current one so that my account stays secure.

#### Preconditions

- The actor is signed in.
- The account has the email and password sign-in method.

#### Normal Flow

***Change Password***

1. The actor opens the Change Password section.
2. The system shows the current password, new password and confirmation fields.
3. The actor enters the current password, the new password and its confirmation.
4. The actor submits the change.
5. The system checks the required fields, the new password length and the matching confirmation.
6. The system re-authenticates the actor with the current password through Firebase Authentication.
7. The system updates the password in Firebase Authentication.
8. The system clears all password fields.
9. The system confirms that the password has been changed (MSG97).

#### Alternative Flows

***Step 2.1 Account Signs In Only with Google***

1. The system hides the password change section and its menu entry.
2. The use case ends without a change.

***Step 5.1 Password Input Is Invalid***

1. The system shows the validation error above the form (MSG96).
2. The use case continues at step 3.

***Step 6.1 Current Password Is Incorrect***

1. The system informs the actor that the current password is incorrect (MSG98).
2. The system clears only the current password field and keeps the new values in memory.
3. The use case continues at step 3.

***Step 7.1 Session Has Ended***

1. The system discards the unsaved form values.
2. The system informs the actor that the session has expired (MSG02).
3. The use case ends with the actor signed out.

***Step 7.2 Password Update Fails***

1. The system informs the actor that the password could not be changed (MSG99).
2. The use case continues at step 3.

#### Postconditions

- The new password is stored only in Firebase Authentication.
- No password value is kept by the platform or in browser storage.
- The account role, status and email are unchanged.

#### Business Rule

BR-41, BR-47
