### 1.3 Reset Password

#### Primary Actors

Guest, Member

#### Secondary Actors

Firebase Authentication

#### Description

A person with an eligible email/password identity requests a Firebase recovery link without giving a password to Mland staff.

#### Preconditions

Firebase password recovery is configured and the person can provide a sign-in email.

#### Normal Flow

1. The person opens Forgot Password and enters an email address.
2. The browser requests a recovery email from Firebase and displays a response that does not disclose whether an account exists.
3. Firebase sends a time-limited recovery link to an eligible identity.
4. The person opens the link and sets a new password on Firebase's recovery page.
5. Firebase validates the link and updates the credential; the person signs in again.

#### Alternative Flows

**Unknown email or ineligible identity:** The page gives the same non-disclosing response. Google-only users recover access through Google.

**Expired link, delivery error, or password update failure:** The password remains unchanged and the person receives safe retry guidance.

#### Postconditions

- Firebase manages the new password; Mland does not store it.
- The response does not reveal account existence.

#### Business Rules

BR-03-01, BR-03-02, BR-03-03

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-03-01 | Recovery uses Firebase's time-limited link. |
| BR-03-02 | Recovery responses must not disclose whether an email is registered. |
| BR-03-03 | Mland must not store or disclose passwords to Staff or Admin. |
