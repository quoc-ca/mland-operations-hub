---
scenario_id: E2E-012-010
feature: FE-012
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: N
source_ref: spec.md#4A.4
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
browser_tool: browsermcp
isolation_key: e2e-012-preview-restoration
cleanup:
  - delete_draft
---

# E2E-012-010 — Complete engraving preview and restoration

## Steps

1. Add two text items with different content and placements.
2. Add two custom images with different source files and placements.
3. Assert the configurator preview contains both text contents and both image previews.
4. Save/evaluate and navigate to the saved-design view.
5. Reload the saved-design view.

## Expected result

- Every text item is visible; only the first item is not sufficient.
- Every selected image is visible and is not replaced by a white placeholder.
- Placement, crop, contrast and sharpness are preserved.
- Missing image data produces an explicit retry/error state.
- Capture screenshots of configurator, evaluation and restored state.
