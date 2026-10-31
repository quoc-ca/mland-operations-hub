# FE-10 Implementation Readiness

**Date created**: 2026-10-27
**Date updated**: 2026-10-27
**Author**: Thinh
**Last updated by**: Thinh

## Approved development slice: local Manager dashboard demo

FE-10 uses `locationId: Long` consistently with account management and FE-08.
The production scope authority will be active `account_location_assignments`;
location display names are presentation data only.

The first implementation slice is enabled only by the `local` Spring profile.
It provides a read-only endpoint, Vietnam-time period handling, KPI composition,
and explicit data-quality states through deterministic local adapters. Manager
scope and the retail, workshop, capacity, and attendance aggregates are demo
facts only. They must be visibly identified as development data and must not
be used as production revenue evidence.

Non-local profiles fail closed until account management exports approved
Manager capability/location-scope facts and source owners export aggregate
facades. No FE-10 Flyway migration is authorized: reporting owns response
composition, not source transactions or analytics persistence.
