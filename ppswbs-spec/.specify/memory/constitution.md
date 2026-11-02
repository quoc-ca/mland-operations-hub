<!--
Sync Impact Report
- Version change: 3.1.0 -> 3.2.0 (MINOR amendment: adds a canonical shared
  Workshop Location identifier and ownership obligation).
- Decision evidence: group decision approved by Hồ Hoàng Quốc (PM) on 2026-10-27.
  Phan Sơn is the responsible document updater.
- Modified principles: I (canonical cross-feature business identifiers).
- Added sections: proposed frontend architecture baseline (pending Project Owner ratification). Removed sections: none.
- Compliance impact: synchronize spec-general.md and affected feature contracts
  so `workshopLocationId` replaces any separate branch/location identity.
- Deferred follow-up: legacy constitution metadata was not backfilled.
-->

# PPSWBS Project Constitution

## Core Principles

### I. Module Integrity and Data Ownership

The backend MUST remain a Spring Modulith modular monolith, organized under
`features/<feature>/` with `controller/`, `dto/`, `dao/`, `entity/`, `service/`,
and `facade/` packages. Additional internal packages require a feature-design
reason. Java package names MUST be valid identifiers; a documentation slug such
as `account-management` does not prescribe an invalid Java package name.

Each business feature MUST be recognized as a distinct application module.
Module detection and verification MUST preserve those boundaries after adding
the `features` grouping package; detecting only the grouping package is insufficient.
The named `facade/` interface, including its explicit boundary types, is the only
Java interface another business feature may use. Controllers, HTTP DTOs, DAOs,
entities, and internal services or adapters MUST remain internal.

Controllers handle transport and validation; services own use cases and transaction
boundaries; DAOs use JPA/Spring Data; entities represent persistence; DTOs define
transport data. Business logic MUST NOT live in controllers or the technical
`common` kernel. `common` MUST NOT own business entities, business DTOs, or shared workflows.

Application code MUST use JPA for business persistence and MUST NOT issue raw
SQL. Schema evolution MUST use ordered, append-only Flyway migrations. An
applied migration MUST NOT be edited or reused. Cross-module work uses
one-way facade calls or minimal, idempotent domain events only when asynchronous
processing or cycle removal requires them.

Shared business concepts MUST have one canonical name, identifier, and owning
feature. `Workshop Location` is the canonical site-and-scope concept for
workshop operations; its identifier is `workshopLocationId` and its lifecycle
is owned by the workshop-location feature. A feature MAY use “branch” as a
display label, but MUST NOT create a second branch identifier or a Branch-to-
Workshop-Location mapping for the same scope without a ratified exception.

### II. Identity and Sensitive-Data Boundary

Firebase Authentication manages credentials and proves identity only. The
backend MUST verify Firebase ID tokens before using their UID. MySQL is the
authoritative source for internal roles, account status, entitlement, policy
consent, and audit evidence; provider, email, and Firebase claims MUST NOT
grant an Mland business role or merge identities.

The Mland backend MUST NOT capture provider passwords or maintain a separate
password store. Passwords, raw tokens, provider codes, signing secrets, and
avoidable PII MUST NOT appear in logs, committed files, or diagnostic output.
Credential handling MUST stay within the approved identity flow; application
state MUST NOT become an unapproved credential store. Audit and error records
MUST contain only safe, redacted data sufficient for support and correlation.

Runtime database, identity-provider, and integration credentials MUST be supplied
through an approved environment or secret channel. Committed configuration MUST
NOT contain credential defaults or silently fall back to an unintended service
when required configuration is absent.

### III. Contract and Separate Frontend Baseline

Public REST behavior MUST use versioned plural kebab-case routes, explicit DTOs,
Bean Validation, server-side authorization, and a versioned OpenAPI contract.
Errors MUST be typed, safe, and correlation-linked; no JPA entity, stack trace,
secret, or sensitive payload may be exposed to a client.

Every backend route MUST be covered by a security policy. Only routes explicitly
declared public by their owning versioned contract may proceed unauthenticated;
all other routes MUST fail closed when a current authenticated actor cannot be
established. CORS MUST be configured for explicit approved origins, and CSRF
MUST be scoped to the selected credential transport. Neither control may be
disabled globally as a shortcut for a feature or test.

The frontend MUST be a separate React + Vite application using client-side
rendering and REST APIs. The Java 21/Spring Boot backend MUST NOT render the
application UI. SSR, Thymeleaf pages, htmx enhancement, and JavaScript-independent
core forms are no longer baseline obligations. This UI decision does not prohibit
HTML email templates or determine production hosting or repository separation.

Feature designs MUST define applicable UI loading, empty, validation, success,
failure, navigation, and credential/state-recovery behavior. Frontend visibility
or route guards MUST NOT substitute for backend authorization.

#### Proposed Frontend Architecture Baseline (Pending Ratification)

The following rules are the project frontend proposal and are not a ratified
constitution amendment until the Project Owner records approval. Production
frontend work MUST either follow these rules after ratification or identify an
explicit feature-scoped exception.

The frontend MUST use a vertical-slice boundary based on user capability or
journey. A frontend feature is not required to mirror a backend module one to
one. `src/main.jsx` is a mount-only entrypoint and `src/app/App.jsx` is the
composition root for providers, routing, and application layouts.

Business code MUST live under `src/features/<capability>/`; root-level
`screens/`, `hooks/`, `services/`, or `templates/` directories MUST NOT become
business-code dumping grounds. A feature MAY contain only the layers it needs:
`ui/`, `application/`, `domain/`, `infrastructure/`, `routes/`, and `tests/`.

The dependency direction is `app -> feature ui -> feature application ->
feature domain`, with `application` and `ui` allowed to use the feature's
`infrastructure` through an explicit feature port. `domain` MUST NOT depend on
React, routing, Firebase, HTTP, or browser APIs. UI code MUST NOT call `fetch()`
or Firebase directly. `shared/` contains technical foundations only and MUST
NOT import feature code or encode business endpoints.

Features MUST communicate through public ports or API boundaries. They MUST NOT
import another feature's private components, hooks, mutable state, or
infrastructure implementation. Server DTOs MUST be mapped at the
infrastructure boundary; UI components MUST NOT become coupled to backend
entities or raw response shapes.

Feature state MUST remain local by default. React Context is reserved for
cross-application concerns such as authenticated session/account, i18n, and
application configuration. Redux, Zustand, TanStack Query, and global server
cache MUST NOT be introduced as project defaults without an explicit decision
covering ownership, freshness, invalidation, isolation, and recovery.

The frontend baseline is JavaScript/JSX. New framework, state, UI-kit, or
transport dependencies require an explicit approved decision. Prototype screens
and local example data are reference material only and MUST NOT be used as
evidence of production feature completion or API integration.

### IV. Evidence-Based Verification and Compatibility

Every behavior, security, API-contract, persistence, or integration change MUST
have fresh verification evidence proportionate to its risk. Documentation-only
changes require an appropriate static check rather than an artificial test.

Module-boundary changes require Spring Modulith verification. Public API changes
require contract, validation, error, and authorization coverage. Schema changes
require empty-database migration coverage and a supported upgrade/preservation
test on the database baseline selected by the owning feature plan.
Frontend changes require appropriate build and UI/API integration verification;
state changes require lifecycle, isolation, expiry/invalidation, and recovery
coverage where applicable. Evidence MUST match the actual scope of the change.

### V. Simplicity and Explicit Decisions

Features MUST follow approved SpecKit artifacts before implementation. Unknown
provider, retention, governance, pricing, or lifecycle decisions remain TBD and
MUST NOT be invented during implementation. New frameworks, external system
boundaries, or irreversible data behavior require approved feature scope and an
explicit decision.

Prefer the smallest solution with clear ownership, compatibility, and rollback
behavior. Complexity that cannot be justified by a requirement or risk is not
added.

The system MUST follow a hybrid stateless/stateful direction. Each feature MUST
identify which interactions are stateless and which require retained state, with
the rationale, owner, storage, lifecycle, consistency, security, and recovery
defined where applicable. A feature need not use both mechanisms. Authentication
state and business state MUST be distinguished; using cookies or a database alone
does not establish the authentication model. Shared authentication or state
mechanisms MUST have a consistent contract referenced by consuming features.
This direction does not select a global session policy, Redis, a session store,
application-signed JWTs, or a separate refresh-token system.

## Additional Constraints

The approved platform baseline is React + Vite, Java 21/Spring Boot REST APIs,
Spring Modulith, JPA/Spring Data with Flyway, Firebase Authentication, and MySQL.
Feature plans select compatible versions, environment details, module detection,
credential transport, and feature-specific state behavior within this baseline.
Changes to this baseline require a constitution amendment or an explicit
ratified exception.

### Superseded Exception 001: Firebase Web SDK JavaScript Requirement for Member Auth

- Historical ratification: Project Owner, 2026-09-30; Member-authentication pages only.
- Historical rationale/default rejected: Firebase Web SDK required JavaScript;
  a non-JavaScript password form would move credential capture to the backend.
- Historical guardrails: Thymeleaf-rendered pages, safe noscript guidance, and
  JavaScript-independent booking-email confirmation; review before expansion.
- Status: superseded by the separate React frontend baseline in v3.0.0 on
  2026-10-24. These historical UI constraints are no longer active obligations.
- Still active under Principle II: no backend provider-password capture or
  unapproved credential-storage fallback. This is not a new authentication exception.

## Development Workflow and Quality Gates

Feature work MUST have an approved specification, implementation plan, and
dependency-ordered task list before code changes. Completion requires fresh
evidence linked to the relevant acceptance scenario and failure path. A failed,
missing, or inapplicable check MUST be recorded with its reason and a manual
verification alternative where one exists.

Every review checks compliance with these principles, the owning feature
contract, migration compatibility, authorization, audit/sensitive-data impact,
and the verification evidence required by the change.

The spec-folder AGENT.md MUST govern research, clarification, specification,
design, and pre-code handoff only. It MUST allow investigation and draft design
within the assigned scope without requiring that design to be complete first;
unapproved decisions MUST remain explicit. Design readiness is not ratification
or authorization to implement. Code-folder AGENT files define their respective
implementation personas and procedures. No persona may weaken this Constitution;
loading the spec persona does not authorize production/test code, executable
migrations, or deployment changes.

Older feature artifacts and the design kit MUST be checked for superseded
assumptions before use. This amendment does not migrate them, approve their
contents, or demonstrate implementation readiness.

## Governance

This Constitution is the highest project governance authority. When it
conflicts with AGENT.md, feature guidance, or an informal practice, this
Constitution prevails.

An amendment MUST document its rationale, Sync Impact Report, semantic-version
bump, and compliance impact. A MAJOR version changes or removes an established
governance obligation; a MINOR version adds a principle or material obligation;
a PATCH version clarifies wording without changing obligations. Amendments and
exceptions require Project Owner ratification.

Each exception MUST identify its scope, rationale, rejected default, guardrails,
ratifier, ratification date, and review condition. No unexplained template
placeholder is permitted. Reviews and implementation handoffs MUST verify
constitution compliance before claiming completion.

**Version**: 3.2.0 | **Ratified**: Hồ Hoàng Quốc (PM), 2026-10-26 |
**Last Amended**: 2026-10-27
