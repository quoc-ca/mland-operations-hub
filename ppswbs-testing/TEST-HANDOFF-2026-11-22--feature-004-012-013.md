# Test Handoff — Feature-004, Feature-012 and Feature-013 Change Request

Date created: 2026-11-22  
Date updated: 2026-11-22  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

This is a test plan only. No scenario is evidence of execution until it has a
browsermcp or automated execution block with screenshots/logs.

## Feature-012 — saved design and recommendation

| ID | Actor | Scenario | Expected result |
|---|---|---|---|
| E2E-012-024 | Guest | Save design with exactly 5 gemstones | State is `SAVED`; Silver Clay is not triggered. |
| E2E-012-025 | Member | Save design with 6 gemstones | Silver Clay recommendation, reason and link are visible; all workshops remain discoverable. |
| E2E-012-026 | Member | Save design with difficulty exactly 9 | Wax Casting is not triggered. |
| E2E-012-027 | Member | Save design with difficulty above 9 | Wax Casting link and two-session/two-day note are visible. |
| E2E-012-028 | Member | Save design with both thresholds exceeded | Wax Casting is the primary recommendation. |
| E2E-012-029 | Guest/Member | Save Silver or Brass design below thresholds | Matching Signature Silver/Brass package is recommended through catalogue data. |
| E2E-012-030 | Guest/Member | Open consultation from saved design | Feature-013 opens with safe design context; no raw ID is shown. |
| E2E-012-031 | Guest/Member | Archive then use design in custom request | Owner can select it and Feature-004 stores an immutable snapshot. |
| E2E-012-032 | Guest/Member | Use another user's design | Access is denied and no design data is disclosed. |

Additional checks: saved design displays `Đã lưu`, not `Đã thẩm định`; loading,
empty, unavailable recommendation and retry states preserve layout width; the
Explore workshops action is never hidden.

## Feature-004 — custom request and human review

| ID | Actor | Scenario | Expected result |
|---|---|---|---|
| E2E-004-034 | Guest | Submit request with an owner design in any readable state | Request is pending human review with an immutable snapshot. |
| E2E-004-035 | Member | Submit another member's design reference | Ownership violation is rejected before snapshot creation. |
| E2E-004-036 | Staff | Accept without explanation | Command is rejected; request remains pending. |
| E2E-004-037 | Staff | Accept with explanation | Decision and explanation are visible to the customer. |
| E2E-004-038 | Manager | Reject with explanation | Request is rejected and explanation is visible. |
| E2E-004-039 | Staff/Manager | Review stale or already-decided request | `409` conflict; no second terminal decision is created. |
| E2E-004-040 | Staff/Manager | Participate in design consultation | Safe design context is visible; role can reply and close within scope. |

Also verify the approved labels `Yêu cầu chế tác`, `Đơn hàng cá nhân` and
`Phiên bản báo giá`, no AI-analysis UI, no cart, no deposit, and 100% payment
only after an approved quote.

## Feature-013 — Guest, Staff and Manager coverage

| ID | Actor | Scenario | Expected result |
|---|---|---|---|
| E2E-013-014 | Guest | Start contextless consultation | Opaque session is created without Firebase login. |
| E2E-013-015 | Guest | Start consultation from saved design | Ownership is checked and safe design context is attached. |
| E2E-013-016 | Guest | Try another user's design | Access is denied; no context preview is returned. |
| E2E-013-017 | Staff | Claim, reply and close in scope | Actions succeed and are audited. |
| E2E-013-018 | Staff | Close out-of-scope conversation | Action is rejected; conversation remains open. |
| E2E-013-019 | Manager | Claim, reply and close globally | Actions succeed without branch-scope failure. |
| E2E-013-020 | Staff/Manager | Reopen or reply after close | Close/reopen policy applies and duplicate commands are idempotent. |
| E2E-013-021 | Guest/Member | Sidebar loading, empty and pagination | Sidebar width remains stable; hidden chats are absent from customer view. |

## Execution requirements

Every new E2E scenario must use `format_version: 2`, explicit `health_urls`,
an isolation key and cleanup. Each step must contain user charter, observe/act,
observable expected result and a `CONTINUE`, `LOG_AND_CONTINUE` or `STOP`
decision. Use browsermcp semantic selectors after observing the UI, save JPEG
screenshots for findings and do not report PASS without execution evidence.

## 2026-11-22 superseding scenarios

| ID | Actor | Scenario | Expected result |
|---|---|---|---|
| E2E-012-033 | Guest/Member | Save a design and open the recommendation block | The recommendation appears below archive, exact reason text is localized, and Explore workshops remains visible. |
| E2E-012-034 | Guest/Member | Save with more than five gemstones | Silver Clay is recommended; recommendation is advisory and does not hide Signature or Wax packages. |
| E2E-012-035 | Guest/Member | Save with difficulty above nine | Wax Casting is recommended with the two-session/two-different-days note. |
| E2E-012-036 | Guest/Member | Save with both conditions | Wax Casting is the primary recommendation; no booking attachment control is rendered. |
| E2E-004-041 | Guest | Upload five valid images and submit | All five images are shown in the request summary; the request enters human review; the sixth image is blocked. |
| E2E-004-042 | Guest | Submit a sixth request in the same day | The request is rejected by the daily Guest limit before creation; the user receives a clear localized message. |
| E2E-004-043 | Guest/Member | Upload a blurry image | Quality feedback appears immediately after upload and the submit action remains blocked until the image is replaced or removed. |
| E2E-004-044 | Guest/Member | Start custom request without a design reference | The request can be submitted with text/images only; no design attachment is required. |
| E2E-004-045 | Guest/Member | Open consultation from saved design | Feature-013 opens with the selected design attached; no raw ID is shown and the request remains independent from booking. |
| E2E-004-046 | Staff/Manager | Decide request without explanation | Backend rejects both acceptance and rejection; UI shows the mandatory explanation error. |
| E2E-004-047 | Staff/Manager | Decide request with explanation | Customer sees the new status and explanation in the request detail/history projection. |

These scenarios supersede AI-analysis and booking-attachment expectations. The
agent must retain old execution records for audit but must not use them as
acceptance criteria for the current baseline.
