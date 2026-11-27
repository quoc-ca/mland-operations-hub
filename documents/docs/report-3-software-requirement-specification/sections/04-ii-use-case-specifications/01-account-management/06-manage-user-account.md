### 1.6 UC-70 Manage User Account

#### UC Name

UC-70 Manage User Account

#### Primary Actors

Admin

#### Secondary Actors

SSO Provider (Firebase Authentication), Mail Gateway (SMTP)

#### Description

As an Admin, I want to create, find, edit, scope, suspend and restore user accounts so that only the right people use the platform with the right role.

#### Preconditions

- The Admin is signed in with an active Admin account.
- At least one active Workshop Location exists for Staff and Manager accounts.

#### Normal Flow

***Create an Internal Account by Invitation***

1. The Admin opens Account Management.
2. The system lists the accounts, most recently changed first, twenty per page.
3. The Admin selects Create internal account.
4. The system shows the role, email, full name, phone and Workshop Location fields with short role guidance.
5. The Admin selects the Staff role and enters the email, full name and phone.
6. The Admin selects the Workshop Location of the new Staff member.
7. The Admin submits the form.
8. The system validates the fields and the number of locations for the role.
9. The system checks that the email is unused in the platform and in Firebase Authentication.
10. The system creates the Firebase identity and the Pending setup account with its location assignment.
11. The system issues a single-use setup invitation valid for 48 hours.
12. The system queues the invitation email for delivery through the Mail Gateway.
13. The system records the creation in the account audit.
14. The system confirms that the account has been created in Pending setup (MSG113).

#### Alternative Flows

***Step 2.1 The Admin Searches and Filters Accounts***

1. The Admin enters the start of an email or full name.
2. The Admin selects a role and a status filter.
3. The system shows the matching accounts page by page without credentials or provider identifiers.
4. The use case continues at step 3.

***Step 2.2 No Account Matches the Filters***

1. The system informs the Admin that no account matches the filters (MSG114).
2. The use case continues at step 2.1.

***Step 2.3 Account List Cannot Be Loaded***

1. The system informs the Admin that the data could not be loaded (MSG05).
2. The use case ends without showing accounts.

***Step 3.1 The Admin Edits the Profile of an Internal Account***

1. The Admin opens the detail page of a Staff, Manager or Carrier account.
2. The system shows the role, status, locations, revocation state, version and profile.
3. The Admin changes the full name and the phone.
4. The Admin saves the profile with the displayed version.
5. The system validates the full name and the phone.
6. The system saves the values without changing role, status, email or location.
7. The system records the change in the account audit.
8. The system confirms that the profile has been updated (MSG115).
9. The use case ends with the profile updated.

***Step 3.2 The Target Is a Member***

1. The system refuses to save the profile of a Member account (MSG01).
2. The use case ends without a change.

***Step 3.3 The Admin Changes the Role and Workshop Location***

1. The Admin opens the detail page of a Staff or Manager account.
2. The Admin selects Staff or Manager and the Workshop Locations.
3. The Admin saves the assignment with the displayed version.
4. The system checks the number of locations for the selected role.
5. The system replaces the active location assignments and the role.
6. The system records the old and new role in the account audit.
7. The system confirms that the role and location have been updated (MSG116).
8. The use case ends with the new role and scope in effect.

***Step 3.4 Profile or Assignment Input Is Invalid***

1. The system shows the validation error in the section being saved (MSG117).
2. The system keeps the entered values.
3. The use case ends without a change.

***Step 3.5 Account Was Changed by Another Session***

1. The system rejects the change because the displayed version is out of date (MSG03).
2. The system reloads the latest account details.
3. The use case ends without overwriting the newer data.

***Step 3.6 The Admin Suspends an Account***

1. The Admin selects Suspend on a Member, Staff, Manager or Carrier account.
2. The system shows the suspension dialog with its warning and a reason box (MSG118).
3. The Admin enters the reason and confirms the suspension.
4. The system sets the account to Suspended at once.
5. The system revokes the sessions of the account in Firebase Authentication.
6. The system records the previous status and the reason in the account audit.
7. The system confirms that the account has been suspended (MSG119).
8. The use case ends with the account suspended.

***Step 3.7 Suspension Reason Is Missing***

1. The system shows that a suspension reason is required in the dialog (MSG120).
2. The use case continues at step 3.6.3.

***Step 3.8 The Admin Restores a Suspended Account***

1. The Admin selects Restore on a suspended account.
2. The system checks that the session revocation has been confirmed.
3. The system restores the Pending setup status for an unfinished invitation and Active otherwise.
4. The system records the restoration in the account audit.
5. The system confirms the restoration and that the user must sign in again (MSG121).
6. The use case ends with the account restored.

***Step 3.9 Session Revocation Is Still Pending***

1. The system disables Restore and refuses the restoration (MSG122).
2. The use case ends with the account still suspended.

***Step 3.10 The Target Is an Admin Account***

1. The system hides the edit, role and suspension actions.
2. The system shows that Admin accounts are protected (MSG123).
3. The use case ends without a change.

***Step 3.11 Account Is Not Found***

1. The system informs the Admin that the account was not found (MSG124).
2. The use case continues at step 2.

***Step 3.12 The Admin Resends a Setup Invitation***

1. The Admin selects Resend invitation on a Pending setup Staff or Manager account.
2. The system warns that the previous link stops working at once (MSG125).
3. The Admin confirms the resend.
4. The system invalidates the previous link and issues a new link valid for 48 hours.
5. The system queues the new invitation email for delivery through the Mail Gateway.
6. The system records the resend in the account audit.
7. The system confirms that the invitation has been resent (MSG126).
8. The use case ends with a new invitation pending.

***Step 3.13 Resend Is Requested Too Soon***

1. The system refuses a resend within five minutes of the previous invitation (MSG127).
2. The use case ends without a new invitation.

***Step 5.1 The Admin Creates a Manager Account***

1. The Admin selects the Manager role and enters the email, full name and phone.
2. The Admin selects one or more Workshop Locations.
3. The use case continues at step 7.

***Step 5.2 The Admin Creates a Carrier Account***

1. The Admin selects the Carrier role.
2. The Admin enters the login email, an initial password, the display name and the phone.
3. The Admin submits the form without any Workshop Location.
4. The system validates the fields.
5. The system checks that the email is unused in the platform and in Firebase Authentication.
6. The system creates the email and password identity in Firebase Authentication with a verified email.
7. The system creates the Active Carrier account without an invitation.
8. The system records the creation in the account audit.
9. The system confirms the Active account and asks the Admin to hand over the credentials (MSG128).
10. The use case ends with the Carrier account ready to sign in.

***Step 8.1 Creation Input Is Invalid***

1. The system shows each field error under its box (MSG129).
2. The use case continues at step 5.

***Step 9.1 Email Belongs to a Staff or Manager Account***

1. The system informs the Admin that the email belongs to an existing internal account (MSG130).
2. The system offers a link to that account's detail page.
3. The use case ends without creating an account.

***Step 9.2 Email Belongs to an Admin or Carrier Account***

1. The system informs the Admin that this email cannot be replaced here (MSG131).
2. The use case ends without creating an account.

***Step 9.3 Email Belongs to a Member Account***

1. The system shows the Member review dialog with profile, addresses, cart, purchases and bookings.
2. The system labels sample or unavailable source data as a preview.
3. The Admin exports the review as a JSON file.
4. The Admin selects Replace Member account.
5. The system refuses the replacement while a data source is sample or unavailable (MSG132).
6. The use case ends without creating an account and the email stays with the Member.

***Step 9.4 Email Exists Only in Firebase Authentication***

1. The system shows that the email has a Firebase identity without a platform account (MSG133).
2. The Admin confirms deletion of that orphan identity.
3. The system deletes the identity in Firebase Authentication and records the deletion in the audit.
4. The use case continues at step 10.

***Step 9.5 Email Check Cannot Be Completed***

1. The system informs the Admin that the email collision could not be checked (MSG134).
2. The use case continues at step 7.

***Step 10.1 Creation Is Retried with the Same Request***

1. The system recognises the retry key of the interrupted request.
2. The system returns the original account without creating another account or identity.
3. The use case continues at step 14.

***Step 10.2 Retry Key Is Reused with Different Details***

1. The system rejects the request because its details differ from the original (MSG135).
2. The use case ends without creating an account.

***Step 10.3 Account Cannot Be Created***

1. The system removes the Firebase identity it has just created.
2. The system informs the Admin that the account could not be created (MSG136).
3. The use case continues at step 7.

***Step 12.1 Invitation Email Fails Twice***

1. The system shows the delivery state Failed with the creation time, expiry and remaining time.
2. The Admin selects Copy email content manually.
3. The system shows the subject, the message and the current setup link.
4. The system records the access in the audit without the content (MSG137).
5. The Admin delivers the copied message outside the system.
6. The use case ends with the delivery state still Failed.

#### Postconditions

- Each created account has one Firebase identity, one fixed role and a valid location scope.
- A Staff or Manager account stays in Pending setup until its invitation is completed.
- A suspended account loses protected access at once and needs a new sign-in after restoration.
- Every accepted Admin command has an audit record without passwords, tokens or links.

#### Business Rule

BR-42, BR-50, BR-51, BR-52, BR-53, BR-54, BR-55, BR-56, BR-57, BR-58, BR-59, BR-60, BR-61, BR-62, BR-63
