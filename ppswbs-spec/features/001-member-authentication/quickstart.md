# Local and verification quickstart: Member Authentication

## Prerequisites

- Java 21 and the project build tool.
- Docker capable of running the official mysql:9.7.2 image.
- A Firebase Auth Emulator or an approved non-production Firebase project for
  browser authentication checks.

MySQL 9.7.2 is the baseline for local, CI, staging, and production. H2 may be
used only by the fast test profile and is not release evidence.

## Database principals and secrets

Provision database ppswbs with two non-root principals:

- ppswbs_migrator: schema creation/alteration for the Flyway deployment step.
- ppswbs_app: runtime DML privileges only.

Keep all password values in a local secret store, CI secret store, or deployment
secret manager. Do not place them in source, documentation, shell history,
logs, screenshots, test fixtures, or committed environment files.

The runtime process receives JDBC_URL, JDBC_DRIVER, JDBC_USER, and
JDBC_PASSWORD. The migration process receives FLYWAY_URL, FLYWAY_USER, and
FLYWAY_PASSWORD. JDBC_DRIVER is the MySQL Connector/J driver class. Local
connection configuration targets localhost port 3306 and database ppswbs, but
the credential values are intentionally not documented.

## Required profiles and commands

1. Start MySQL mysql:9.7.2 using local secret injection, create ppswbs, and
   provision the two principals.
2. Execute Flyway as ppswbs_migrator before launching the service.
3. Launch the service with the local-mysql profile and ppswbs_app runtime
   variables. Confirm startup reports neither an H2 URL nor a credential.
4. Run fast unit tests using the dedicated H2 test profile.
5. Run integration tests with Testcontainers pinned to mysql:9.7.2. They must
   apply Flyway to an empty database and an upgrade fixture containing V1 data.
6. Run browser smoke checks against Firebase Auth Emulator/non-production
   Firebase, then record exact command, profile, image tag, date, and result in
   verification.md.

## Smoke checklist

- Provision same Firebase UID twice: one Member/account results.
- Deny invalid/expired bearer token and Member access with missing policy
  acceptance or suspended status.
- Verify browser Google and email/password actions obtain a fresh token and do
  not expose credentials.
- Require confirmationToken to be nonblank; consume a valid booking token once;
  make expired/used/malformed values return the same safe 400.
- Create an overdue pending booking and verify the enabled expiry job cancels it
  idempotently without enabling payment.
- Verify Guest import refuses absent/false confirmation and links eligible
  verified-email bookings only after explicit true confirmation.

## External release prerequisites

Policy publication approval, Firebase provider/authorized-domain configuration,
transactional-email sender identity, migration-principal provisioning, and
deployment secret injection require external evidence. Mark them unverified
until that evidence exists.
