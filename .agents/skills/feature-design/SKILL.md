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
4. Write the pack in clear Vietnamese unless the feature's approved source uses another language. It must be understandable by the next implementation agent without reading code first.
5. Every planned production unit belongs in `architecture.md`: frontend component/hook/API client and backend controller/service/facade/repository/entity/DTO as applicable. List its path, responsibility, proposed class/export name, and public or use-case method names. Omit only small private helpers. Do not write code, pseudo-code, or method bodies.
6. Mark every folder, library, class, method, and data-flow node as `existing`, `approved`, or `proposed`, with a source. `proposed` is not a baseline or approval and blocks dependent implementation until the Tech Lead approves it.

Read [design-pack templates and review protocol](references/design-pack.md) before drafting or assessing the pack.

## Workflow

1. Inventory the existing artifacts and map each applicable D01–D20 gate in `ppswbs-spec/AGENT.md` to a precise section or identify the gap.
2. Create or update these documents under `<feature>/design/` when the feature is full-stack:
   - `architecture.md` — implementation tree/package layout, production units and draft method names, allowed dependencies, FE/BE libraries with rationale, and arrow data flows through those units.
   - `workflows.md` — every independently triggered feature workflow in numbered order, with actor, preconditions, owning class/method boundaries, data/state effects, success, failure/recovery, and retry/concurrency where applicable.
   - `ui-flow.md` — actors and authorization, navigation, loading/empty/validation/success/error/partial states, session recovery, accessibility baseline, and links to the architecture components/hooks and workflow IDs that implement each screen flow.
3. Preserve uncertainty. A decision missing an authoritative source may be documented as a clearly labeled proposal with alternatives and impact, but it is not an implementation decision.
4. Maintain a requirement → design → task → verification mapping. A requirement with no approved design or verification is a gap, not a reason to invent work.
5. Reconcile the pack with its linked data model and contracts. Flag disagreement; never resolve it by silently editing one source to match another.
6. Before handoff to implementation, require `<feature>/approval.md` using the protocol in the reference. It must identify the design revision, approved decisions and scope, date, and named Tech Lead. A missing or incomplete record means the affected scope is not ready for implementation.
7. Report one of: `DRAFT/PROPOSED`, `BLOCKED`, or `READY FOR IMPLEMENTATION`. Only use the last status when every applicable gate is mapped, no decision affecting the handoff is unresolved, and the matching approval record is complete.

## Blocking behavior

For any unresolved API/schema, authorization, lifecycle, concurrency, integration, or observable UI behavior, do not choose a convention as a substitute for approval. Produce a blocker section or a feature-local blocker report that states the source, impact, decision question, Tech Lead owner, and exact condition to resume.

An implementation agent may decide only private code organization that preserves all approved behavior and contracts. It must not treat a draft, checkbox, file existence, or an `approval.md` missing revision/scope/Tech Lead/date as approval.
