# Feature 003 Verification Follow-up

**Owner**: Hồ Hoàng Quốc
**Recorded**: 2026-10-29

## Temporary baseline assumption

For the current Feature 003 implementation pass, assume the shared Account
Management baseline owned by FE-001 is complete and will be corrected/merged
by that owner. Do not change Account Management code from Workshop Operations
to hide baseline failures.

## Required retest after Feature 003 is complete

Run the full relevant suite again on a clean MySQL database after all Feature
003 implementation and merges are finished:

```powershell
mvn -f .\ppswbs_backend\pom.xml test "-Dtest=WorkshopOperationsOpenApiIT,WorkshopOperationsQuickstartIT,WorkshopOperationsFoundationIT,WorkshopOperationsMySQLIT,WorkshopSessionManagementIT,WorkshopLifecycleIT,TicketRecoveryContractIT,TicketRecoveryIT,WorkshopClosureContractIT,WorkshopClosureIT,CancellationHandoffOutboxIT,WorkshopSettlementContractIT,WorkshopSettlementMigrationIT,ModulithArchitectureTests"
```

The final evidence must confirm:

- Maven compile succeeds without exclusions or ignored migrations;
- Flyway validates and applies the canonical migration sequence on fresh MySQL;
- all Feature 003 tests pass with zero failures/errors;
- Modulith boundaries remain valid;
- no Account Management baseline mismatch remains.

Until this retest is complete, passing H2/default-profile tests are useful
development feedback only and are not final MySQL acceptance evidence.

## MySQL run finding (2026-10-29)

The MySQL attempt reached Flyway but stopped before application startup because
the local `ppswbs_test` schema has migration-history drift: resolved migration
`V4__ai_chatbox_and_design_review.sql` is present in source but is not recorded
as applied in `flyway_schema_history` (reported checksum `469179046`). This is
a test-database baseline issue, not an Account Management code failure.

Acceptance must use a fresh isolated test schema (or an explicitly approved
database-history repair by its owner). Do not use `outOfOrder`, ignored
migrations, or checksum repair as a shortcut for this verification.

The next run reached Flyway validation but found a checksum mismatch for
`V12__loyalty_program_config_version.sql` (applied `-118695652`, resolved
`469179046`). V12 belongs to the Loyalty/Feature 006 migration owner, not
Workshop Operations. Before any repair, that owner must verify whether
`promotion_program.config_version` and its constraint already exist in the
local schema; only then may the owner approve a checksum repair.

## Successful isolated MySQL verification (2026-10-29)

Using the existing fresh-baseline schema `ppswbs_v18_fresh_20261028`, Flyway
validated all 15 migrations through V18 and reported the schema up to date.
`WorkshopOperationsMySQLIT` passed 9/9 and `WorkshopOperationsQuickstartIT`
passed 6/6 (15/15 total). This is valid T049 MySQL/Flyway evidence. T052
remains open because its quickstart command is the full backend `mvn test`, not
only the isolated Feature 003 suite.

## FE-001 baseline remediation required before full `mvn test`

**Owner**: FE-001 Account Management owner / shared-baseline maintainer
**Status**: OPEN — do not treat isolated Feature 003 evidence as full-backend
acceptance.

The full backend run must be repeated after FE-001 confirms and fixes the
Account schema/entity baseline, including the known
`account_audit_events.outcome` database-type versus entity-mapping mismatch
and any related migration-history drift. The owner must provide:

1. the canonical migration/entity decision;
2. a clean compile and Account Management test result;
3. fresh MySQL Flyway validation evidence; and
4. a full `mvn test` result with no Account tests excluded or disabled.

### Exact baseline mismatch location

- `ppswbs_backend/src/main/resources/db/migration/V5__account_management.sql:63`
  creates `account_audit_events.outcome` as MySQL
  `ENUM('SUCCESS','FAILURE')`.
- `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/entity/AccountAuditEvent.java:31-32`
  maps the same column as a plain `String` without an explicit enum mapping.

This is an FE-001 schema/entity alignment decision. Do not comment out either
side from the 003 branch; FE-001 must reconcile it and provide the final
full-backend verification.

Do not comment out Account code, disable Account tests, add Maven excludes, or
ignore migrations to manufacture a full-backend pass. A filtered command may be
used only as explicitly labelled diagnostic evidence, never as T052 acceptance.

## Latest suite findings (2026-10-29)

The first broad suite ran 117 tests and exposed 5 failures/errors. After
rebasing the stale lifecycle assertions, the focused lifecycle suite now
passes 10/10. Remaining acceptance work is:

- `WorkshopLifecycleIT` now verifies that the retired public operational-lead
  endpoint is not exposed and preserves the in-progress responsibility guard.
- `WorkshopOperationsFoundationIT` now verifies that Staff in active location
  scope does not require a per-session assignment.
- Two Flyway-history assertions ran against H2 and failed because
  `flyway_schema_history` is not present; run those checks in the required
  MySQL/Flyway profile rather than adding an ignore or exclusion workaround.

The tests are now isolated explicitly: Foundation no longer queries Flyway
history from an H2 context, while `WorkshopOperationsMySQLIT` self-skips unless
the configured datasource is actually MySQL. This is test-scope isolation,
not a production or migration workaround.

## Latest Feature 003 verification (2026-10-29)

The package-selection and OTA snapshot producer group was committed locally as
`1dcd997`. Its targeted regression run used the existing local MySQL 8.0 schema
`ppswbs_v18_fresh_20261028`; Flyway validated 18 migrations and reported schema
version 21 current. The run passed 102 tests across Operations lifecycle,
foundation, idempotency, OpenAPI, package, OTA snapshot and settlement-facade
coverage. This is scoped MySQL evidence, not final whole-backend acceptance.

T055 remains open only for consumer-dependent acceptance after 003.4 implements
its consumer (replay/restart, duplicate/older delivery, gap resync and failure
isolation). The 003.4 T011 task wording now depends on 003.2 T053-T054 and does
not wait for those consumer-side T055 checks.

After all 003.1–003.4 implementation and merges are complete, rerun the full
backend Maven test suite with the approved MySQL/Flyway environment and no
Account Management exclusions or Flyway workarounds. Record that final result
here. Until then, do not describe scoped Feature 003 runs as full-backend pass.
