---
scenario_id: E2E-012-009
feature: FE-012
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: N
source_ref: spec.md#4A.3
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
browser_tool: browsermcp
isolation_key: e2e-012-image-preprocessing
cleanup:
  - delete_draft
---

# E2E-012-009 — Laser image preprocessing

## Steps

1. Upload a non-white test image and select a valid engraving placement.
2. Assert contrast and sharpness controls show min `0`, max `100`, default `50`.
3. Move contrast and sharpness to distinct values within the range.
4. Assert the preview changes and remains non-white.
5. Save/evaluate, reopen the design and inspect the restored values and preview.

## Expected result

- Values are integer values in `0..100`.
- Preview updates without creating a command request for every slider movement.
- Saved design restores both slider values and the processed preview.
- Out-of-range values are rejected before persistence.
