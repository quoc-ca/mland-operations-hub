### 1.1 Authentication Flow

1. A customer signs in with email and password or the configured Google redirect through Firebase. Facebook sign-in is deferred.
2. The browser sends the Firebase ID token as `Authorization: Bearer` on protected API requests. The backend verifies the token and revocation state, then loads the current Mland Account.
3. A pending or suspended Account cannot use protected business functions. An unverified email may sign in, but each function that requires verification checks it on the server.
4. A Guest booking Magic Link grants access only to its booking view. It does not create a Member session. Staff check-in still requires Staff authorization and ticket validation.

An Admin supplies the intended email and active location for a new Staff or Manager. Account binds a real Firebase UID to a `PENDING_SETUP` account and records a mandatory invitation. Its single-use link expires 48 hours after version creation, even if mail fails. Resend is allowed after five minutes, rotates the link and keeps the same Account and UID. Setup can attach password or Google to that UID. Activation requires the current unexpired link, matching verified email and current active location. Direct login or Member registration cannot claim a reserved pending identity. Firebase sends Member verification and password-reset emails; Notification sends invitations and business emails after the owning transaction commits.
