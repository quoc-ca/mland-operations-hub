---
name: e2e-browser-execute
description: Execute a validated v2 PPSWBS E2E scenario through browsermcp, capture evidence, and record outcomes and defects.
metadata:
  short-description: Execute an exploratory E2E browser scenario with evidence
---

# E2E Browser Execute

Use this skill to run a v2 scenario at `ppswbs-spec/features/<feature>/test-results/E2E-*.md` with `browsermcp`.

## 1. Mandatory Guards

1. Before reading or running a scenario, confirm that `browsermcp` is connected. If unavailable, stop immediately; do not simulate a result. Update the scenario as `BLOCKED` only when it is a v2 scenario, and direct the user to [the MCP prerequisite guide](references/mcp-prerequisite-guide.md).
2. Validate the scenario before browser actions. It must have `format_version: 2`, `browser_tool: browsermcp`, required v2 YAML, and every step's User charter, Explore & act, Expected, and Decision rule. Reject legacy/unversioned scenarios: do not run, edit, or infer a v2 mapping for them.
3. Check every `health_urls` endpoint. If a required endpoint is unavailable, stop as `BLOCKED`, identify it, and refer to [`RUN-PROJ-DEV.md`](../../../ppswbs-testing/RUN-PROJ-DEV.md).

## 2. Execute as the Actor

- Begin at `target_url` with only stated actor context and data. Do not inspect source code, UI-flow/design files, API contracts, internal routes, or old cases to discover the UI path.
- At each step, inspect rendered UI first, then use visible and accessible affordances. Semantic browser locators are allowed after the UI reveals them; never invent them from source knowledge.
- Evaluate every Expected bullet before applying that step's Decision rule. A transition-only step explicitly says `None — transition only`.
- Preserve the real result. Do not alter expected results, test data, product code, or the scenario merely to continue or obtain a pass.

## 3. Decision and Evidence Protocol

| Step outcome | Required action | Case result |
| --- | --- | --- |
| `CONTINUE` | Record normal step evidence as needed and continue. | Continue execution. |
| `LOG_AND_CONTINUE` | Record problem description, exact user actions, and a screenshot at occurrence. Capture console/network only when useful. Create a BUG-LOG entry if confirmed defect. | Continue; track finding. |
| `STOP` — environment/precondition | Capture available evidence and reason. | `BLOCKED`. |
| `STOP` — product/spec/core expected result | Capture screenshot and relevant diagnostics; add a BUG-LOG entry. | `FAIL`; stop immediately. |

Save screenshots repository-relative under the case's `screenshots/` directory using a quoted `.jpg` path. Never include secrets in screenshots, console output, or `BUG-LOG.md`.

## 4. Finalize Execution

Perform cleanup. A cleanup failure is `FAIL` when it leaves an unsafe or misleading product state; otherwise record it as a finding and explain remaining data.

Update its execution block with one final status:

- `PASS`: every step continued without findings.
- `PASS_WITH_FINDINGS`: every step completed and every recorded confirmed defect is `MINOR`; list findings, screenshot paths, and bug references.
- `FAIL`: a product/spec/core expectation stop or unsafe cleanup failure.
- `BLOCKED`: browser tool, required environment, or required precondition blocked execution.

For `FAIL` and confirmed findings, use the existing `BUG-LOG.md` contract. Do not claim `PASS` or `PASS_WITH_FINDINGS` without executing the scenario and capturing required evidence.
