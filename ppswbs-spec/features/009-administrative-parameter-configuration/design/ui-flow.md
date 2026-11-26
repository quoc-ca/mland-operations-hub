# UI Flow Design: FE-09 Admin Ring Difficulty

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** DRAFT/PROPOSED — written Admin visual baseline for Tech Lead review.

## Route and screen states

`/admin/ring-difficulty` is ADMIN-only. Guest follows existing login recovery;
other signed-in roles and inactive accounts follow the shared `/404` masking
rule before component mount or API request. Unresolved account bootstrap shows
neutral retry at the current URL. The backend independently rejects non-Admin.

| Screen/interaction | Trigger and workflow | Visible states and recovery |
| --- | --- | --- |
| Active policy and history | Entry; WF-09-01 | Loading without private values; effective version and publish actor/time; unavailable retry; empty state is an error because one seeded version is required. |
| Edit candidate | Field edit; WF-09-02 | Group option scores, two engraving coefficients and thresholds. Indicate unsaved edits; show per-field and cross-field validation; never silently reset. |
| Before/after preview | Preview; WF-09-02 | Required standard examples plus optional Admin-selected non-personal examples; show old/new score, level and recommendation. List every active published model's full-default old/new score and eligibility, highlighting any model that would become invalid, unscored or too risky. Changes to the candidate, active/catalogue version or expiry invalidate the 15-minute preview reference. |
| Confirm publication | Publish; WF-09-03 | Enable only with a matching fresh preview reference; show active/candidate version and changed fields, disable duplicate submit, then success with new version. Missing/expired proof requires preview again; `409` keeps edits and offers reload/reconcile; `422` focuses errors; `503` offers retry with the same command key. |
| Restore old values | Choose historical version; WF-09-04 | Load values into candidate, require preview and a new publish; historical version remains read-only. |

Use the existing admin shell, spacing, typography, bilingual messages, keyboard
navigation, visible focus and announced validation. Do not present the score as
`/10`.

## Written visual baseline for final review

The desktop screen uses the existing Admin page header and content width. A
sticky summary identifies the effective policy version, last publication and
unsaved candidate state. The editor groups material, shape, gemstone and
attachment scores in searchable tables with reference, name, current value,
candidate value and inline error; engraving coefficients and ordered cutoffs
appear in separate labelled groups. Newly active unscored options are called
out at the top of their group. The primary action sequence is **Preview**,
review changes and model impact, then **Publish** in a confirmation dialog
showing the expected version and changed-field count. Publish remains disabled
without a matching fresh proof or while any model impact is blocking.

The preview places standard example comparisons first, optional non-personal
examples second, and an exhaustive model-impact table after them. Each model
row shows model code/name, material, old/new score, old/new level and eligibility;
blocking rows include the exact missing option or cutoff reason. The list is
filterable by affected/blocking status, never silently truncated, and exposes
the total reviewed and blocked counts. A stale catalogue or policy response
preserves edits, clears proof and offers reload/reconcile. Validation messages
are adjacent to controls and announced; a publish outage leaves the same
candidate and command key available for retry.

At narrow widths, the tables become stacked labelled rows; the action bar
remains reachable without covering content. Keyboard users can reach every
field, preview comparison, model row and dialog control in reading order.
This written flow is the proposed visual baseline exception to a new Figma
frame; Ngô Tiến Đạt must explicitly approve this revision before UI handoff.
