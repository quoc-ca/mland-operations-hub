# Feature-013 Research — Staff Consultation Chat

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## 1. Sources checked

- `ppswbs-spec/AGENT.md` — specification/design responsibility and decision gates.
- `ppswbs-spec/spec-general.md` — fixed roles, Staff Workshop Location scope, privacy, audit and retention baseline.
- `ppswbs-spec/APIs.md` — REST, Spring Modulith facade and audit boundaries.
- `ppswbs-spec/DB.md` — UTF-8 storage, FK retention, audit and PII rules.
- Report 3 use case `5.3 Chat with Staff Consultant` — existing Member-to-Staff consultation behavior.
- Report 3 actor/use-case tables — Member, Staff and Manager responsibilities.
- `ppswbs-spec/screen.md` and `src/screenRegistry.js` — existing Consultation Inbox and Consultation Conversation entry points.
- Feature-004 `spec.md`/`design.md` — independent Design Request boundary and human-review boundary.
- Feature-012 `spec.md`, `data-model.md` and design pack — workshop design ownership, versioning and safe design projections.
- `ppswbs-testing/AGENT.md` and `RUN-PROJ-DEV.md` — unit/integration/E2E gates, Browser MCP, isolation and evidence rules.

## 2. Facts, inferences and proposals

### Confirmed facts

- Report 3 already describes plain-text consultation chat initiated by a Member.
- The conversation does not approve a design or publish a final quote.
- Current role baseline is `MEMBER`, `STAFF`, `MANAGER`, `ADMIN`; Staff is Workshop-Location-scoped and Manager may have global authority where the feature explicitly allows it.
- No chat controller, repository, migration or production feature module exists yet.
- Existing UI inventory names `Consultation Inbox` and `Consultation Conversation` but does not prove implementation approval.
- Feature-004 and Feature-012 own their design/request/configuration data; Feature-013 must consume safe facades rather than their repositories.
- Database and application logs must use UTF-8 and must not contain unnecessary PII, tokens or raw secrets.

### Chosen decisions for this preparation

- Create a separate `013-staff-consultation-chat` feature.
- Only authenticated Member customers may open or use conversations.
- Staff and Manager may participate; Admin has no business chat authority.
- Use REST plus ten-second polling while the page is visible; do not introduce WebSocket or SSE.
- Conversations are context-bound to `WORKSHOP_RING_DESIGN` or `INDEPENDENT_DESIGN_REQUEST`.
- Member selects context from a server-provided dropdown with code, name, version and safe preview.
- Messages are immutable UTF-8 text, maximum 2,000 Unicode code points.
- A Member may send at most 30 messages in a rolling minute; rejection returns `429` and `Retry-After`.
- Shared inbox uses claim/release. Staff remains branch-scoped; Manager is global.
- Conversation lifecycle is `OPEN`/`CLOSED`; message lifecycle has no edit/delete operation.
- Transcript retention is three years; audit stores metadata only and never raw message text.

### Important inference

Feature-012 creates a design before branch selection. Therefore a workshop-design
conversation may initially have no resolved branch. Such a conversation is visible
to Manager only until a Manager assigns a valid branch; Staff cannot bypass scope by
opening a URL or guessing a reference.

## 3. Boundary and blockers

Feature-013 owns conversation, message, claim, read-state and chat-audit records.
Feature-004 owns independent design requests. Feature-012 owns configured workshop
designs. Feature-003 owns booking. None of those features may access Feature-013's
repositories directly.

Before code, the following contracts must be supplied:

1. Feature-004 safe Design Request context facade with Member ownership and version validation.
2. Feature-012 safe configured-design context facade with Member ownership, version, display code and preview.
3. A branch-resolution/assignment boundary for contexts without a selected branch.
4. Account facade support for current actor role/status and Staff active Workshop Location scope.
5. Flyway history check confirming the next migration after current repository baseline `V182`.
6. Isolated Member, Staff Branch A, Staff Branch B and Manager test accounts.

## 4. Risks to verify

- Preview/code mismatch could cause a Member to attach the wrong design. The server
  must return one atomic context option projection and persist its immutable snapshot.
- Independent Design Requests have no branch by default, so Staff visibility must fail
  closed until Manager assignment exists.
- Polling must not create duplicate reads or write side effects.
- Audit and application logs must prove actions without copying private message content.
- Vietnamese text must survive browser, JSON, MySQL and polling round trips without mojibake.

