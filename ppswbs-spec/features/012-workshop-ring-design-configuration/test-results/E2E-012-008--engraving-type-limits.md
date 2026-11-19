---
scenario_id: E2E-012-008
feature: FE-012
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: B
source_ref: spec.md#4A.2
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
browser_tool: browsermcp
isolation_key: e2e-012-engraving-type-limits
cleanup:
  - delete_draft
---

# E2E-012-008 — Independent engraving quotas

## Steps

1. Enable engraving and add two `TEXT` items.
2. Attempt to add a third `TEXT` item.
3. Add two `SYMBOL` items and attempt to add a third.
4. Upload two custom images and attempt to upload a third.
5. Remove one item of each type and verify the corresponding add action is available again.

## Expected result

- Counters show `TEXT 2/2`, `SYMBOL 2/2` and `IMAGE 2/2`.
- Each third-item attempt is blocked independently.
- The UI displays a localized limit message and keeps existing items.
- Removing an item releases only that item type's quota.
- A bypassed API payload returns the matching limit error.

## Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-19 23:15:35
- Executed by: Antigravity Verification Agent
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  1. Enabled standard engraving, entered text, clicked `+ Thêm dòng chữ`. Counter reached `TEXT 2/2` and disabled `add-text-engraving-btn`.
  2. Clicked `+ Thêm biểu tượng` twice. Counter reached `SYMBOL 2/2` and disabled `add-symbol-engraving-btn`.
  3. Uploaded 2 custom laser images via `upload-engraving-image-input`. Counter reached `2 / 2`, banner displayed "Số lượng ảnh khắc laser đã đạt tối đa (2/2)", and `upload-engraving-image-btn` was disabled.
  4. Removed one symbol item and one image. Quotas updated independently to `SYMBOL 1/2` and `1 / 2`, and their respective add buttons became enabled again immediately.
- Bug reference: NONE
<!-- EXECUTION:END -->
