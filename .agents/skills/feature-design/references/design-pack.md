# Design Pack Templates and Review Protocol

Use these structures for a full-stack feature. Keep headings that apply; for an inapplicable concern, state why it does not apply. The feature documents are governed by `ppswbs-spec/.specify/metadata-policy.md`: include its required provenance block immediately below each H1 and never create a document until the responsible member is known.

## `design/architecture.md`

```markdown
# Architecture Design: <feature>

<metadata block required by metadata policy>

## Scope and source map

| Requirement / scenario | Authoritative source | Architecture response | Task | Verification |
| --- | --- | --- | --- | --- |

## Ownership and boundaries

Describe frontend, backend/module, data, and integration owners; identify what is private versus a named public boundary.

## Allowed dependencies and public interfaces

List allowed cross-feature calls, facade/interface consumers, direction, and cycle prevention. Link to contracts rather than repeating their payloads.

## Conditional implementation structure

Only document public facade signatures, orchestration responsibilities, concurrency mechanisms, or non-trivial algorithms that need design-level precision. Otherwise explain why no class/interface detail is needed.

## Open decisions and dependencies

State source, alternatives, impact, decision owner, and whether the gap blocks handoff.
```

## `design/workflows.md`

```markdown
# Workflow Design: <feature>

<metadata block required by metadata policy>

## Workflow index

| Workflow | Trigger and actor | Source requirements | Contract/data references | Status |
| --- | --- | --- | --- | --- |

## <workflow name>

- Preconditions and authorization:
- Ordered flow and responsible boundary:
- State transitions and forbidden transitions:
- Transaction boundary, side effects, and audit:
- Failures, partial failures, user-visible outcome, and recovery:
- Retry, duplicate, out-of-order, and concurrency behavior:
- Postconditions and verification:
```

## `design/ui-flow.md`

```markdown
# UI Flow Design: <feature>

<metadata block required by metadata policy>

## Actors, routes, and authorization

## Screen and interaction flows

| Screen / flow | Entry and actor | API/contract reference | Loading / empty / validation / success / error / partial states | Recovery |
| --- | --- | --- | --- | --- |

## Session, data handling, and accessibility

Document approved session-loss behavior, client-side data boundaries, and applicable accessibility/browser baseline. Link to source UX decisions; do not infer them from prototypes.

## Open decisions and dependencies
```

## `approval.md`

Create this only for a handoff to implementation, not merely because design drafting begins.

```markdown
# Implementation Design Approval: <feature>

<metadata block required by metadata policy>

## Approval status

**Status**: APPROVED FOR IMPLEMENTATION | PARTIALLY APPROVED | NOT APPROVED

## Approved revision and scope

- Design revision: [exact paths and commit/version/content marker]
- Approved implementation scope: [requirements, workflows, contracts, and exclusions]
- Approval date: YYYY-MM-DD
- Tech Lead: [named person]

## Decisions approved

| Decision | Approved value | Evidence/source | Affected artifact revision |
| --- | --- | --- | --- |

## Exclusions and remaining blockers

List any scope not approved. A partial approval must not be used to implement excluded or dependent work.
```

`READY FOR IMPLEMENTATION` requires `Status: APPROVED FOR IMPLEMENTATION` plus non-empty values for every field in **Approved revision and scope**. Tech Lead must be a named person, not a role label, agent, or placeholder.

## Gate review record

For every D01–D20, record `MET`, `NOT MET`, or `NOT APPLICABLE — <reason>` with a file path and heading. A gate is `MET` only when the linked source answers the decision; a heading, checkbox, or phrase such as “handle appropriately” is insufficient. Report all `NOT MET` items with source, impact, question, Tech Lead owner, and resume condition.
