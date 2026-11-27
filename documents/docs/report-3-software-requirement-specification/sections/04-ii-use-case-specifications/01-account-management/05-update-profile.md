### 1.5 UC-28 Update Profile

#### UC Name

UC-28 Update Profile

#### Primary Actors

Member, Staff, Manager, Admin

#### Secondary Actors

SSO Provider (Firebase Authentication)

#### Description

As a signed-in user, I want to view and update my name, phone number, login email and shipping addresses so that my account details stay correct.

#### Preconditions

- The actor is signed in with an active account.
- The account is not a Carrier account.

#### Normal Flow

***Update Name and Phone Number***

1. The actor opens the Profile page.
2. The system reloads the actor's identity and synchronises the verified email status.
3. The system shows the full name, phone number, login email and verification status.
4. The actor selects Edit profile.
5. The actor changes the full name and the phone number.
6. The actor saves the changes.
7. The system validates the full name and the phone number.
8. The system saves the values without changing role, status, email or location.
9. The system confirms that the profile has been updated (MSG100).

#### Alternative Flows

***Step 2.1 Email Is Not Verified***

1. The system shows a verification reminder with a resend option (MSG101).
2. The system disables saving of the profile (MSG102).
3. The actor selects Resend verification email.
4. The system asks Firebase Authentication to send the verification email.
5. The system disables resending for 90 seconds.
6. The use case ends without saving the profile.

***Step 2.2 Verification Status Cannot Be Checked***

1. The system informs the actor that the verification status could not be checked (MSG103).
2. The actor selects the reload verification icon.
3. The use case continues at step 2.

***Step 2.3 Session Has Ended***

1. The system discards the unsaved profile draft.
2. The system informs the actor that the session has expired (MSG02).
3. The use case ends with the actor signed out.

***Step 4.1 The Actor Changes the Login Email***

1. The actor selects Change email.
2. The actor enters the new email address and the current password.
3. The system re-authenticates the actor with the current password through Firebase Authentication.
4. The system asks Firebase Authentication to send a verification email to the new address.
5. The system keeps the current login email until the new address is verified (MSG104).
6. The system records the new email on the next Profile visit after verification.
7. The use case ends with the email change requested.

***Step 4.2 New Email Is Already Used or Password Is Wrong***

1. The system informs the actor that the email is used by another account (MSG105).
2. The system informs the actor when the current password is incorrect (MSG98).
3. The use case continues at step 4.

***Step 4.3 Account Signs In Only with Google***

1. The system hides the email change form.
2. The system asks the actor to change the email in the Google account (MSG106).
3. The use case ends without an email change.

***Step 4.4 The Member Manages the Shipping Address Book***

1. The Member opens the address book.
2. The system lists the saved addresses and marks the default address.
3. The Member adds an address with recipient name, phone, province, commune and detail address.
4. The system enables each field only after the previous field is valid.
5. The system normalises the phone and checks the province and commune in the active directory.
6. The system saves the address and makes the first address the default.
7. The system confirms that the address has been saved (MSG107).
8. The use case ends with the address book updated.

***Step 4.5 The Member Edits, Deletes or Sets a Default Address***

1. The Member selects an action on a saved address.
2. The system applies the change using the address version the Member viewed.
3. The system replaces the previous default when a new default is chosen.
4. The system confirms the change (MSG107).
5. The use case ends with the address book updated.

***Step 4.6 Address Change Is Rejected***

1. The system rejects an eleventh address (MSG108).
2. The system rejects deleting the default while other addresses exist (MSG109).
3. The system rejects a phone that is not a Vietnamese mobile number (MSG110).
4. The system rejects an address changed in another session (MSG03).
5. The use case continues at step 4.4.

***Step 7.1 Profile Input Is Invalid***

1. The system shows the error under the full name or phone box (MSG111).
2. The use case continues at step 5.

***Step 8.1 Profile Cannot Be Saved***

1. The system informs the actor that the profile could not be updated (MSG112).
2. The system keeps the entered values.
3. The use case continues at step 6.

#### Postconditions

- The profile shows the saved full name and phone number.
- The login email changes only after the new address is verified.
- A Member has at most 10 addresses with exactly one default.

#### Business Rule

BR-47, BR-48, BR-49, BR-50
