### 1.1 Authentication Flow

1. A customer signs in with email and password or the configured Google redirect through Firebase. Facebook sign-in is deferred.
2. The browser sends the Firebase ID token as `Authorization: Bearer` on protected API requests. The backend verifies the token and revocation state, then loads the current Mland Account.
3. A pending or suspended Account cannot use protected business functions. An unverified email may sign in, but each function that requires verification checks it on the server.
4. A Guest booking Magic Link grants access only to its booking view. It does not create a Member session. Staff check-in still requires Staff authorization and ticket validation.

Internal Staff and Manager accounts remain `PENDING_SETUP` until their invitation is used, the intended email is verified, and the assigned location is valid. Firebase sends Member verification and password-reset emails. The notification service sends internal invitations and business emails.
