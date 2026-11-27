#### 2.2.1 User Profile

![](assets/screens/live/05-user-profile.png)

This screen allows Members, Staff, Managers and Admins to:

- View their own name, role, sign-in email, phone number, account ID, account status and join date.
- Edit their full name and phone number after their email is verified.
- Resend the verification email and recheck the verification status.
- Jump to the password and email sections of Change Email / Password, or open Address Book.

The page title is **Profile & security**. The Carrier cannot open this screen: the route shows 404, and a Carrier reaching the page is sent to Carrier Portal.

**Field Description**

| Field Name | Description |
| --- | --- |
| ← Back to home | Returns to Home Page. |
| Verification banner | Shown while the email is not verified: "Your email is not verified yet!" with **Resend verification email**. After sending, the button shows "Resend in {seconds}s" for 90 seconds. |
| Email verification status | Shows Checking…, Verified or Not verified; **Reload verification** asks the system again. |
| Profile summary | Name (or "Member customer"), role badge, email badge ("Email verified" or "Email not verified"), full name, sign-in email, phone number, account ID, account status and join date; an empty value shows "(Not provided)". |
| Quick actions | **Edit profile**, **Change password** (hidden for a Google-only account) and **Change email** scroll to their section; **Delivery address book** opens Address Book. |
| Delivery address book card | Says the book holds up to 10 addresses; **Manage address book →** opens Address Book. |
| Sign-in address | Read-only email from the identity provider; it is changed only in the email section. |
| Full name | Required, up to 100 Unicode characters, no control characters. |
| Phone number | Optional, 7 to 30 characters. |
| Save changes | Disabled until the email is verified, with "* You need to verify your email before you can edit the profile."; shows a success or error message after saving. |
| Reset | Restores the stored values in the form. |
