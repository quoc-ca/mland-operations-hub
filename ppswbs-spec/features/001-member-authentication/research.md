# Research: Member Authentication Decisions

## Token verification boundary

**Decision**: Firebase Web SDK authenticates the browser; the browser sends an ID token in `Authorization: Bearer`; Spring Boot verifies it using Firebase Admin. The verified `uid` is `external_user_id`.

**Rejected**: custom JWTs, server-stored Firebase tokens, password handling in Spring, and email as account key.

**Evidence**: [Firebase Admin: Verify ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens).

## Same-email collision and provider linking

**Decision**: Matching email never merges identities. A provider is linked only from the authenticated Firebase identity via Firebase's explicit credential-linking flow. Credential collisions produce a generic recovery/sign-in instruction.

**Rejected**: changing a MySQL UID by email, silent linking at Google login, or manual identity transfer for a Google-only Member.

**Evidence**: [Firebase Web: Link multiple auth providers](https://firebase.google.com/docs/auth/web/account-linking).

## Verification, recovery, and integration testing

**Decision**: Firebase sends email verification and password reset. Responses remain generic. Test Firebase integration against Auth Emulator, never a production project; emulator tokens must never be accepted by production configuration.

**Evidence**: [Firebase Web: Manage users](https://firebase.google.com/docs/auth/web/manage-users), [Firebase Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth).

## MySQL baseline and JavaScript exception

**Decision**: Target MySQL 8.4 LTS with JPA/Flyway. Authentication UI stays SSR but Firebase credential actions require JS; `noscript` gives safe support/retry, not an unsafe password form. Ratification is required because this is a bounded exception to `AGENT.md` progressive enhancement.

**Evidence**: [MySQL release model](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html).

## Booking confirmation

**Decision**: Store a hash of a random one-time booking-email token, consume it with a server-rendered link, and expire bookings idempotently after 15 minutes. No invoice/payment exists before confirmation.

**Rejected**: plaintext tokens, client-only timer, payment before confirmation, and automatic resend.

