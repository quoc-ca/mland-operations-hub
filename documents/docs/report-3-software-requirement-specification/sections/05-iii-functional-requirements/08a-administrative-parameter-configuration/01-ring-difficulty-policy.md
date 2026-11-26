#### 8A.1 Admin Ring Difficulty Policy

{{100-admin-ring-difficulty width=100% align=center}}

**Observed:** Screen 100 is available at `/admin/ring-difficulty` for an active Admin. Other signed-in roles are routed to a masked page. The server checks Admin role and active status again for every policy request.

This screen allows Admins to:

- Read the effective policy version, publication details and immutable history.
- Edit option scores, engraving coefficients and difficulty or recommendation cutoffs as one local candidate.
- Preview standard and optional non-personal examples, plus every active published model's eligibility.
- Publish the complete candidate only after a valid preview, or load historical values as a new candidate.

{{100-admin-ring-difficulty-preview width=100% align=center}}

| Field Name | Description |
| --- | --- |
| Effective policy summary | Shows version, publication time and actor, unsaved change count and preview-proof status. |
| Material, shape, gemstone and attachment tables | Show option reference, name, effective score and editable candidate score. Newly active unscored options are highlighted. |
| Engraving coefficients | Edit the standard engraving bonus and the bonus per custom image. |
| Thresholds | Edit four ordered difficulty-level cutoffs, the Wax Casting cutoff and the Silver Clay gemstone-count cutoff. |
| Optional non-personal example | Accepts a name, material, shape, engraving flag and image count; the list is limited to 20 examples. |
| Preview | Compares old and proposed score, level and recommendation on standard and optional examples. It also displays unavailable standard cases. |
| Published-model impact | Shows every active published model, before/after outcome and eligibility. Filters show all, affected or blocked models without dropping the total counts. |
| Publish | Opens a confirmation dialog only with a valid preview reference for the exact candidate. A blocking model prevents publication. |
| History | Shows prior immutable versions. Loading old values requires another preview and a new publication. |
| Validation and recovery | Field errors remain next to inputs. A stale policy or catalogue response clears preview proof and offers reload while preserving the candidate. |

The policy changes later design evaluations only. Saved evaluations retain their recorded result and rules version. The screen does not edit prices, package publication, checkout parameters or the formula structure.

**TBD — Mland decision required:** The approved optional-example design includes gemstone and attachment selections. The running screen currently offers material, shape, engraving and image count only.

