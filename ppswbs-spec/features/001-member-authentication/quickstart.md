# Quickstart: Member Authentication Delivery and Verification

## Prerequisites

1. Ratify the Firebase/JavaScript exception in [plan.md](./plan.md#blocking-architecture-exception-requiring-ratification).
2. Provide approved bilingual Terms/Privacy versions and publication ownership.
3. Configure Firebase outside source control: providers, authorized domains, action URLs, deployment-secret Admin credentials, and Auth Emulator.
4. Configure an approved transactional sender; tests use captured/fake mail only.

## Local verification

- Use JDK 21, Docker/Testcontainers MySQL, and Firebase Auth Emulator.
- Start the app with runtime secrets/public Firebase config from local secret tooling, never a committed `.env`.
- Confirm Flyway on empty MySQL; provision the same UID twice (one Member); attempt same email/new UID (no merge); accept policies then access protected API.
- Confirm payment is blocked until booking email is confirmed; test valid token, 15-minute expiry, and retry idempotency.
- Confirm verified-email import preview, decline no-op, explicit import, repeat no duplicates; test suspension, stale policy, logout, generic recovery error, and `noscript` UI.

Implementation must add one documented unit-test command and one integration-test command that cannot contact production Firebase/email. Browser smoke matrix: latest two Chrome/Edge/Firefox/Safari desktop, Chrome Android, Safari iPhone.

