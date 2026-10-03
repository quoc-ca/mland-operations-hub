# Verification ledger: Member Authentication

## Current status

No fresh command result is claimed by this document. Existing implementation
must be treated as unverified until the evidence below is recorded after the
Feature 001 reconciliation tasks run.

## Required evidence

| Gate | Required command or manual action | Passing evidence |
| --- | --- | --- |
| Static contract check | Compare all seven OpenAPI operations, request DTOs, controller mappings, and statuses | reviewer/date/result plus no unexplained mismatch |
| Fast tests | Run the H2 test profile | exact command, commit/worktree state, test count, result |
| MySQL migration | Flyway empty-database run on mysql:9.7.2 as migrator | image tag, migration list, result |
| Upgrade preservation | Apply V1 then forward migrations on mysql:9.7.2 | row/relationship preservation assertions |
| MySQL integration | Run Testcontainers mysql:9.7.2 integration suite | exact command and result |
| Browser auth | Firebase Emulator/non-production smoke for Google and email/password | environment label, scenarios, result; no secret |
| Security and audit | Inspect denied paths, error envelope, and logs/audits | evidence that no token/password/plaintext token/avoidable PII appears |
| Scheduler | Trigger overdue pending booking transition | cancellation, capacity/invoice gate, and idempotency evidence |
| Spec consistency | Run SpecKit analyze and converge after implementation | report/result and any appended tasks |

## Known static gaps to close

- Firebase browser flow is not yet evidenced as a real Web SDK bearer-token flow.
- Current role derivation must be replaced by MySQL accounts/account_roles lookup.
- Booking expiry scheduling must be enabled and tested.
- Guest-import request validation must enforce confirmed true.
- Email confirmation token must be required/nonblank and map all unsafe variants
  to one generic 400 response.
- Policy-current uniqueness requires a forward database constraint.
- Ready-ring/history owners still need to consume the shared Member route guard.

Do not mark a task complete or claim release readiness merely because a source
file exists. Each completion requires fresh, linked evidence above.
