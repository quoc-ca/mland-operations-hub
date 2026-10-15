<!--
Sync Impact Report
- Version change: 1.0.0 -> 2.0.0
- Modified principles: I-III strengthened; IV and V added.
- Added sections: Additional Constraints; Development Workflow and Quality Gates.
- Removed sections: none; Architecture Exceptions was moved beneath Additional Constraints.
- Follow-up TODOs: none.
-->

# PPSWBS Project Constitution

## Core Principles

### I. Module Integrity and Data Ownership

Business modules MUST own their web, application, domain, infrastructure, and
facade packages. A module facade is the only named interface another business
module may use; controllers, HTTP DTOs, entities, repositories, and adapters
remain internal. The common package is a technical kernel only and MUST NOT
contain business entities or shared business workflows.

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

The system MUST NOT capture, persist, or log passwords, raw tokens, provider
codes, signing secrets, or avoidable PII. Audit and error records MUST contain
only safe, redacted operational data sufficient for support and correlation.

### III. Contract and Progressive-Web Baseline

Public REST behavior MUST use versioned plural kebab-case routes, explicit DTOs,
Bean Validation, server-side authorization, and a versioned OpenAPI contract.
Errors MUST be typed, safe, and correlation-linked; no JPA entity, stack trace,
secret, or sensitive payload may be exposed to a client.

Thymeleaf is the server-rendered baseline and htmx only enhances it. Core forms
and links MUST remain usable without htmx or JavaScript unless a ratified,
bounded exception states the affected journey and its safe fallback.

### IV. Evidence-Based Verification and Compatibility

Every behavior, security, API-contract, persistence, or integration change MUST
have fresh verification evidence proportionate to its risk. Documentation-only
changes require an appropriate static check rather than an artificial test.

Module-boundary changes require Spring Modulith verification. Public API changes
require contract, validation, error, and authorization coverage. Schema changes
require empty-database migration coverage and a supported upgrade/preservation
test on the database baseline selected by the owning feature plan.

### V. Simplicity and Explicit Decisions

Features MUST follow approved SpecKit artifacts before implementation. Unknown
provider, retention, governance, pricing, or lifecycle decisions remain TBD and
MUST NOT be invented during implementation. New frameworks, external system
boundaries, or irreversible data behavior require approved feature scope and an
explicit decision.

Prefer the smallest solution with clear ownership, compatibility, and rollback
behavior. Complexity that cannot be justified by a requirement or risk is not
added.

## Additional Constraints

The approved platform baseline is Java and Spring Boot, Thymeleaf with htmx,
JPA with Flyway, Firebase Authentication, and MySQL. Feature plans select
compatible versions, environment details, and provider-specific behavior.
Changes to this baseline require a constitution amendment or an explicit
ratified exception.

### Ratified Exception 001: Firebase Web SDK JavaScript Requirement for Member Auth

- Status: Ratified by Project Owner on 2026-09-30.
- Scope: Member-authentication pages only.
- Rationale: Firebase browser authentication must negotiate credentials and
  obtain an ID token without delegating password capture to the Mland backend.
- Default rejected: a non-JavaScript password form would violate the identity
  and zero-password-storage boundary.
- Guardrails: pages remain Thymeleaf-rendered; a noscript view provides safe
  support/retry guidance; booking-email confirmation remains server-rendered and
  JavaScript-independent; backend credential-capture fallback is prohibited.
- Review condition: review before expanding the scope, changing Firebase
  authentication mechanics, or adding any other JavaScript-only core journey.

## Development Workflow and Quality Gates

Feature work MUST have an approved specification, implementation plan, and
dependency-ordered task list before code changes. Completion requires fresh
evidence linked to the relevant acceptance scenario and failure path. A failed,
missing, or inapplicable check MUST be recorded with its reason and a manual
verification alternative where one exists.

Every review checks compliance with these principles, the owning feature
contract, migration compatibility, authorization, audit/sensitive-data impact,
and the verification evidence required by the change. The project AGENT.md is
the implementation playbook; it may add procedure and detail but MUST NOT
weaken or contradict this Constitution.

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

**Version**: 2.0.0 | **Ratified**: 2026-09-30 | **Last Amended**: 2026-09-30
