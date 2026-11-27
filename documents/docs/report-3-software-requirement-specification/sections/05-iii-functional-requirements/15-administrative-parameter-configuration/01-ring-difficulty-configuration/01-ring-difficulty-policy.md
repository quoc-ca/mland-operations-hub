#### 15.1.1 Admin Ring Difficulty Policy

![](assets/screens/live/100-admin-ring-difficulty-policy.png)

This screen, titled **Ring Difficulty Policy Configuration**, allows Admins to:

- Read the effective policy version, publication details and immutable history.
- Edit option scores, engraving coefficients and difficulty or recommendation cutoffs as one local candidate.
- Preview standard and optional non-personal examples, plus every active published model's eligibility.
- Publish the complete candidate only after a valid preview, or load historical values as a new candidate.

Only an active Admin can open the screen; other signed-in roles are routed to the not-found page. The server checks the Admin role and active status again for every policy request.

On the screen, they can also:

- Discard all candidate changes with **Reset to effective**.
- Reload the latest policy after a conflict with **Reload latest version**.

![](assets/screens/live/100b-admin-ring-difficulty-preview.png)

**Field Description**

| Field Name | Description |
| --- | --- |
| Effective policy summary | Effective version, Published at, By, and either "Unsaved candidate changes" or "Matches effective policy". It also shows the preview state: "Preview proof ready (expires {time})", "Fresh preview required for current candidate" or "Preview has blocking models — publish disabled". |
| Reset to effective / Preview Impact / Publish Policy | Action buttons. **Preview Impact** runs the preview ("Running Preview..."). **Publish Policy** is enabled only with a valid preview for the exact candidate. |
| Material, Shape, Gemstone and Attachment Scores | One table per group with a filter "Filter by reference or name...", and columns Reference, Option Name, Current and Candidate. Newly active unscored options show "Score pending". Empty: "No matching options." |
| Engraving & Image Coefficients | "Standard engraving bonus (one-time)" and "Custom laser image bonus (per image)". |
| Difficulty & Recommendation Thresholds | Easy, Medium, Hard and Very hard / Too-risky cutoffs, which must be strictly increasing; the Wax Casting cutoff, below the Very hard cutoff; and the Silver Clay gemstone cutoff, a whole number from 0 to 9. |
| Optional Non-Personal Samples | Sample name, Material, Shape, Custom images (0-2) and Standard engraving, with **Add sample** (at most 20 samples) and **Remove**. Empty: "No optional Admin samples added. Required standard examples are always evaluated by the server." |
| Preview Impact Results | Catalogue revision, then Required Standard Examples, Optional Admin Examples and Unavailable Standard Cases, comparing Before and After (Score / Level / Advice). |
| Exhaustive Active Published-Model Impact | "Total models reviewed: {total}" and "Blocked models: {blocked}", filters All, Affected and Blocked with counts (Default: All), and columns Model Code / Name, Material, Before, After and Eligibility (Eligible or Blocked with its reason). A blocking model prevents publication. |
| Immutable Publication History | Prior versions marked Effective or Historical; **Load into candidate** loads old values, which then need another preview and a new publication. |
| Confirm Policy Publication | Dialog with the expected active version, the number of changed fields and a note that saved designs keep their scores; **Cancel** or **Confirm Publish**. Success: "Published new policy version {version}." |
| Validation and recovery | Field errors stay next to inputs. A conflict keeps the candidate and asks to reload the latest version and preview again; an expired preview asks for a new preview. |

The policy changes later design evaluations only. Saved evaluations keep their recorded result and rules version. The screen does not edit prices, package publication, checkout parameters or the formula structure.

**TBD — Mland decision required:** The approved optional-example design includes gemstone and attachment selections. The running screen offers material, shape, engraving and image count only.
