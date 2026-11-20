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

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-20 02:17:15
- Executed by: Antigravity QA Agent
- Browser tool: browsermcp
- Failure step: None
- Actual result: Trình cấu hình hiển thị đồng thời cả 2 mục chữ khắc (mặt trong và mặt ngoài) và cả 2 bản xem trước ảnh laser (160x160px); lưu và thẩm định thành công; khi tải lại trang mẫu đã lưu (reload saved-design view), toàn bộ chữ khắc, nhãn vị trí mặt trong/mặt ngoài, 2 ảnh laser cùng thông số tương phản và sắc nét đều được khôi phục nguyên vẹn.
- Evidence screenshot: screenshots/E2E-012-010--restored.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
