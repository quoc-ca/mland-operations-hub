---
name: feature-design
description: Create or review a full-stack feature design pack before implementation, including architecture, workflows, UI flow, readiness gaps, and Tech Lead approval evidence.
metadata:
  short-description: Design and approve implementation-ready full-stack feature packs
  user-invocable: true
  when-to-use: Use after feature clarification/planning when implementation needs decisions beyond a business-facing spec, and before tasks are accepted for code generation.
  category: specification
  keywords: [feature-design, architecture, workflow, ui-flow, readiness, approval]
---

# Feature Design Pack

Create an implementation-ready design pack for one feature without turning unresolved product or technical choices into invented baselines. This skill complements SpecKit: `speckit-specify` owns the business-facing **what/why**; this skill owns the feature-level **how, boundaries, and handoff evidence**.

Do not use this skill to write production code, migrations, deployment configuration, or to replace an approved contract. Do not modify generic SpecKit skills.

## Preconditions and authoritative sources

1. Identify one feature directory and read its `spec.md`, `plan.md`, `tasks.md` when present, plus applicable `research.md`, `data-model.md`, `contracts/`, the spec-folder `AGENT.md`, constitution, and approved UX sources.
2. Read `.specify/metadata-policy.md` before creating or updating a feature Markdown artifact. Obtain the responsible member required by that policy; do not invent an author from Git, the OS account, or the agent.
3. Treat `data-model.md` as the schema/ownership source and `contracts/` as the transport/interface source. Link to the authoritative section rather than duplicating DTOs, routes, schemas, or field definitions.
4. Class or interface design is conditional. Add it inside `architecture.md` only when a public facade, orchestration boundary, concurrency mechanism, or non-trivial algorithm cannot otherwise be implemented unambiguously. Do not create a default `class-design.md`.

Read [design-pack templates and review protocol](references/design-pack.md) before drafting or assessing the pack.

## Workflow

1. Inventory the existing artifacts and map each applicable D01–D20 gate in `ppswbs-spec/AGENT.md` to a precise section or identify the gap.
2. Create or update these documents under `<feature>/design/` when the feature is full-stack:
   - `architecture.md` — ownership, frontend/backend/module boundaries, allowed dependencies, public facades/interfaces, integrations, and conditional class/interface detail.
   - `workflows.md` — primary and failure flows, state and transaction boundaries, side effects, recovery, idempotency, and concurrency when applicable.
   - `ui-flow.md` — actors and authorization, navigation, client/server interaction, loading/empty/validation/success/error/partial states, session recovery, and approved accessibility/browser baseline.
3. Preserve uncertainty. A decision missing an authoritative source may be documented as a clearly labeled proposal with alternatives and impact, but it is not an implementation decision.
4. Maintain a requirement → design → task → verification mapping. A requirement with no approved design or verification is a gap, not a reason to invent work.
5. Reconcile the pack with its linked data model and contracts. Flag disagreement; never resolve it by silently editing one source to match another.
6. Before handoff to implementation, require `<feature>/approval.md` using the protocol in the reference. It must identify the design revision, approved decisions and scope, date, and named Tech Lead. A missing or incomplete record means the affected scope is not ready for implementation.
7. Report one of: `DRAFT/PROPOSED`, `BLOCKED`, or `READY FOR IMPLEMENTATION`. Only use the last status when every applicable gate is mapped, no decision affecting the handoff is unresolved, and the matching approval record is complete.

## Blocking behavior

For any unresolved API/schema, authorization, lifecycle, concurrency, integration, or observable UI behavior, do not choose a convention as a substitute for approval. Produce a blocker section or a feature-local blocker report that states the source, impact, decision question, Tech Lead owner, and exact condition to resume.

An implementation agent may decide only private code organization that preserves all approved behavior and contracts. It must not treat a draft, checkbox, file existence, or an `approval.md` missing revision/scope/Tech Lead/date as approval.
