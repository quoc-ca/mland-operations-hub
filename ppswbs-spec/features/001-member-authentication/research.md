# Research and decisions: Member Authentication

## Database baseline

**Decision**: use the official mysql:9.7.2 image as the exact MySQL baseline
for local, CI/integration, staging, and production. The tag is pinned rather
than relying on a moving major/minor tag.

**Evidence**: [official image tags](https://hub.docker.com/_/mysql?tab=tags)
and [MySQL 9.7.2 release notes](https://dev.mysql.com/doc/relnotes/mysql/9.7/en/news-9-7-2.html).

**Consequence**: Flyway/JPA compatibility is demonstrated against MySQL 9.7.2.
H2 compatibility mode is retained for fast tests only and cannot close a
release verification gate.

## Credentials and migration privileges

**Decision**: use two non-root database principals. ppswbs_migrator runs
Flyway before service deployment; ppswbs_app runs the application with DML-only
privileges. Secrets are injected at execution time through JDBC_* and FLYWAY_*
environment variables.

**Reason**: an application account that can alter schemas unnecessarily widens
production blast radius. The service must not run Flyway with its runtime
principal.

## Identity and authorization

**Decision**: Firebase Admin verifies token authenticity and UID; MySQL
accounts/account_roles determines business role and status.

**Reason**: social provider and Firebase claims are identity information, not
Mland authorization. UID is the only identity join key; email is never merge
evidence.

## Policy-current invariant

**Decision**: a forward migration adds a stored generated effective-policy key,
equal to policy_type only when state is EFFECTIVE, and a unique index on that
key.

**Reason**: MySQL unique indexes permit multiple NULL values, allowing any
number of non-effective versions while structurally preventing two effective
records of the same type. Application checks remain useful but are not the only
protection.

## Browser and booking workflow

**Decision**: Firebase browser credential operations use Firebase Web SDK; the
server receives only a fresh bearer token. Booking confirmation accepts a
one-time plaintext token only in the request, hashes it before persistence, and
maps malformed/expired/used tokens to one generic 400 response.

**Consequence**: browser smoke testing needs Firebase Auth Emulator or an
approved non-production Firebase project. Token, password, and provider-code
logging is prohibited.
