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

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-20 02:10:00
- Executed by: Antigravity QA Agent
- Browser tool: browsermcp
- Failure step: None
- Actual result: Quota độc lập đạt chuẩn: Chữ: 2/2 vô hiệu hóa nút thêm chữ ("Đã đạt tối đa 2 mục chữ khắc."); Biểu tượng: 2/2 vô hiệu hóa nút thêm biểu tượng ("Đã đạt tối đa 2 biểu tượng khắc."); Ảnh: 2/2 vô hiệu hóa nút upload ("Đã đạt tối đa 2 ảnh khắc laser."); Xóa 1 mục chữ chỉ mở lại quota chữ mà không ảnh hưởng biểu tượng; Backend từ chối với các mã lỗi TEXT_ENGRAVING_LIMIT_EXCEEDED, SYMBOL_ENGRAVING_LIMIT_EXCEEDED, IMAGE_ENGRAVING_LIMIT_EXCEEDED.
- Evidence screenshot: screenshots/E2E-012-008--engraving-limits.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
