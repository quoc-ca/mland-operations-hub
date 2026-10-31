# Design Pack Templates and Review Protocol

Use these structures for a full-stack feature. Write in clear Vietnamese unless the approved feature source uses another language. Keep headings that apply; for an inapplicable concern, state why it does not apply. The feature documents are governed by `ppswbs-spec/.specify/metadata-policy.md`: include its required provenance block immediately below each H1 and never create a document until the responsible member is known.

Every folder, library, production unit, and data-flow node must state one status: `existing`, `approved`, or `proposed`, followed by its source. A proposed name helps review the design but is not permission to code it. Do not use code blocks for implementation code, pseudo-code, method bodies, SQL, DTO payloads, or API schemas; link to the authoritative source instead.

## `design/architecture.md`

```markdown
# Architecture Design: <feature>

<metadata block required by metadata policy>

## Scope and source map

| Requirement / scenario | Authoritative source | Architecture response | Task | Verification |
| --- | --- | --- | --- | --- |

## Implementation tree and ownership

Show the intended production layout as an indented tree. Include all applicable frontend and backend folders/packages, mark each node's status/source, and state the dependency direction. Example shape only:

```text
frontend/src/features/<feature>/                 [proposed — plan.md §...]
  api/<Feature>ApiClient.ts                       [proposed]
  hooks/use<Feature>.ts                           [proposed]
  components/<Feature>Page.tsx                    [proposed]
backend/.../features/<feature>/                   [approved — constitution §...]
  controller/<Feature>Controller.java              [proposed]
  service/<Feature>Service.java                    [proposed]
  facade/<Feature>Facade.java                      [proposed]
  dao/<Feature>Repository.java                     [proposed]
```

Explain ownership and the allowed dependency direction below the tree. Do not claim a proposed folder/package already exists.

## Production-unit blueprint

List every expected production component, hook, API client, controller, service, facade, repository, entity and DTO that is applicable. Omit only small private helpers.

| Path | Type / status | Responsibility | Class, component, or export | Draft public / use-case methods or props | Allowed dependencies | Source |
| --- | --- | --- | --- | --- | --- |

Names may be proposed, but explain only their responsibility and input/output intent; never include a method body or pseudo-code.

## Libraries and integrations

| Layer | Library / version | Status | Purpose in this feature | Why it is needed | Approval/source |
| --- | --- | --- | --- | --- | --- |

List both reused and newly proposed FE/BE libraries. A library with no approved source remains proposed and blocks code that depends on it.

## Data flows

For every independently triggered operation, show an arrow flow through the exact production-unit names above. Include the main success path and its important error branch.

```text
<Component> → <hook> → <API client> → <Controller.method>
→ <Service.method> → <Repository or Facade.method> → response
error: <boundary> → <typed outcome> → <component recovery state>
```

Link each flow to its workflow ID. The arrow flow is implementation-level routing; `workflows.md` remains the business-level sequential description.

## Open decisions and dependencies

State source, alternatives, impact, decision owner, and whether the gap blocks handoff.
```

## `design/workflows.md`

```markdown
# Workflow Design: <feature>

<metadata block required by metadata policy>

## Workflow index

| ID | Workflow | Trigger and actor | Source requirements | Architecture data-flow link | Status |
| --- | --- | --- | --- | --- | --- |

## WF-<NN>: <workflow name>

- Trigger and actor: [who/what starts it, and from where].
- Preconditions and authorization: [conditions before step 1].
- Related architecture data flow: [link to architecture heading].

1. [Named class/component/method boundary receives the trigger.]
2. [Validation/authorization and the next named boundary.]
3. [Data/state read or mutation, owner and transaction boundary if applicable.]
4. [External/cross-feature call, side effect, audit, or response composition if applicable.]
5. [Success result visible to the actor.]

### Failure, recovery, and integrity

1. [Each observable failure branch, its owner, visible outcome, and recovery action.]
2. [Retry/idempotency/concurrency/out-of-order behavior when applicable; otherwise state why it is not applicable.]
3. [Postconditions and verification evidence.]

Do not combine distinct read, create, update, state-transition, retry, callback, or scheduled operations when they have different triggers or observable outcomes.
```

## `design/ui-flow.md`

```markdown
# UI Flow Design: <feature>

<metadata block required by metadata policy>

## Actors, routes, and authorization

## Screen and interaction flows

| Screen / flow | Entry and actor | Workflow ID | Architecture component/hook/API-client link | API/contract reference | Loading / empty / validation / success / error / partial states | Recovery |
| --- | --- | --- | --- | --- | --- | --- |

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
