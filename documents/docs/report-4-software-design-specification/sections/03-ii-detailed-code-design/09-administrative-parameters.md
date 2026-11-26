## 9. Administrative Ring Difficulty Configuration (FE-09)

**Observed:** FE-09 owns the Admin route, editor and local candidate state. FE-12 owns the policy API, validation, evaluation and persistence. An active Admin can publish difficulty values; Manager catalogue commands do not own them.

### 9.1 Class Diagram

{{r4-cd-fe09-ring-difficulty width=100% align=center}}

| Component | Responsibility |
| --- | --- |
| `RingDifficultyPolicyPage` | Renders the effective values, candidate editor, examples, model impact, confirmation and history. |
| `useRingDifficultyPolicy` | Loads policy and options, holds local edits and samples, invalidates stale preview and manages one publish command key. |
| `ringDifficultyAdminApi` | Calls the active, versions, preview and publication endpoints through the shared authenticated HTTP client. |
| `AdminRingDifficultyController` | Requires an active Admin for every private request and requires an idempotency key for publication. |
| `AdminRingDifficultyService` | Validates complete candidates, compares examples and active models, checks proof, publishes a version and writes audit. |
| `RingDifficultyPolicyAccessService` | Loads the effective immutable policy and option scores for Admin reads and Member evaluation. |
| Policy repositories | Persist immutable versions and scores, the effective pointer, catalogue revision, expiring proofs and publication audit. |

The fixed evaluation formula stays in FE-12. Prices, package publication, checkout parameters and Manager-owned catalogue fields are outside this Admin editor.

### 9.2 Sequence Diagram — Read Effective Policy and History

{{r4-sd-fe09-read-policy-history width=100% align=center}}

1. The Admin UI requests the effective policy and history through the authenticated API client.
2. The controller checks the resolved account role and active status for each request.
3. The service loads the effective pointer, immutable version and option scores, then lists prior versions.
4. The controller returns policy values and publication metadata to the UI.
5. The UI builds a local candidate and renders the version history without changing stored policy.

History restoration copies old values into the local candidate. It requires another preview and a new publication; it never rewrites an old version.

### 9.3 Sequence Diagram — Preview Candidate

{{r4-sd-fe09-preview-policy width=100% align=center}}

1. The UI validates the local candidate and sends it with the expected active version and optional non-personal samples.
2. The controller requires an active Admin and passes the request to the service.
3. The service loads the effective policy and catalogue revision, then validates the complete candidate and active-option coverage.
4. The evaluator compares standard and optional examples under the current and candidate policies.
5. The service checks every active published model's complete defaults and eligibility.
6. The service stores a 15-minute proof only if no model blocks publication, then returns comparisons and impact.
7. The UI displays old and new outcomes, unavailable standard cases and all model rows.

The proof stores an opaque reference hash, Admin reference, candidate digest and expected policy and catalogue versions. It stores no Member design or example contents.

### 9.4 Sequence Diagram — Publish Candidate

{{r4-sd-fe09-publish-policy width=100% align=center}}

1. The UI sends the complete candidate, expected version, preview reference and idempotency key after confirmation.
2. The controller requires an active Admin and calls the publication service.
3. The service checks an identical completed command for an idempotent replay.
4. The service locks the effective pointer and catalogue revision, then checks the expected policy version.
5. The service validates the candidate and matching, unexpired proof, then rechecks every active published model.
6. The service writes a new immutable version, option scores and publication audit, then switches the effective pointer atomically.
7. The controller returns the new version; the UI refreshes history and clears its local preview.

The audit retains the Admin actor, publication time and old/new values. A saved design keeps its original evaluation and rules version.

### 9.5 Sequence Diagram — Reject Stale Publication

{{r4-sd-fe09-reject-stale-policy width=100% align=center}}

1. The UI sends a previously previewed candidate and its publication command.
2. The controller checks Admin authority and calls the publication service.
3. The service locks and reads the current policy and catalogue versions.
4. The service detects a version or proof mismatch and returns a conflict without switching the effective pointer.
5. The UI preserves candidate edits, clears the stale preview and offers reload and another preview.

Invalid input or an ineligible published model also leaves the prior version effective. A storage outage reports failure; the Admin can retry the same command key.

