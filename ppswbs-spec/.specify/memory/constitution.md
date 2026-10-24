<!--
Sync Impact Report
- Version change: 2.0.0 -> 3.0.0 (MAJOR: replaces SSR obligations and redefines persona scope).
- Decision evidence: the user confirmed the group's architecture decisions and explicitly
  requested this amendment in the 2026-10-24 session; no additional ratifier identity is inferred.
- Modified principles: I (feature packaging); II (credential boundary); III (Contract and
  Progressive-Web Baseline -> Contract and Separate Frontend Baseline); IV (frontend/state
  verification); V (hybrid design and shared contracts).
- Added sections: none; Exception 001 is retained as superseded history.
- Removed obligations: Thymeleaf/htmx, JavaScript-independent core forms and links,
  and use of the spec-folder AGENT as an implementation persona.
- Compliance impact: synchronize spec-general.md and AGENT.md in this amendment.
- Deferred follow-up: detailed feature artifacts, APIs.md, design kit and code are not updated
  in this amendment; reassess affected designs against v3.0.0 before implementation.
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

### III. Contract and Separate Frontend Baseline

Public REST behavior MUST use versioned plural kebab-case routes, explicit DTOs,
Bean Validation, server-side authorization, and a versioned OpenAPI contract.
Errors MUST be typed, safe, and correlation-linked; no JPA entity, stack trace,
secret, or sensitive payload may be exposed to a client.

The frontend MUST be a separate React + Vite application using client-side
rendering and REST APIs. The Java 21/Spring Boot backend MUST NOT render the
application UI. SSR, Thymeleaf pages, htmx enhancement, and JavaScript-independent
core forms are no longer baseline obligations. This UI decision does not prohibit
HTML email templates or determine production hosting or repository separation.

Feature designs MUST define applicable UI loading, empty, validation, success,
failure, navigation, and credential/state-recovery behavior. Frontend visibility
or route guards MUST NOT substitute for backend authorization.

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

**Version**: 3.0.0 | **Ratified**: 2026-09-30 | **Last Amended**: 2026-10-24
