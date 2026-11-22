---
scenario_id: E2E-012-011
feature: FE-012
actor: GUEST_OR_MEMBER
test_level: e2e
test_case_type: B
source_ref: spec.md#4A.5
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
browser_tool: browsermcp
isolation_key: e2e-012-locale-unicode
cleanup:
  - delete_draft
---

# E2E-012-011 — Locale and Vietnamese Unicode regression

## Steps

1. Open the feature in Vietnamese locale.
2. Inspect every visible label, help text, validation message, SVG label and quota message.
3. Enter Vietnamese text with diacritics and symbols `♥`, `∞`, `★`, `✿`, `💍`.
4. Save/evaluate and inspect the saved-design preview.
5. Switch to English locale and repeat the screen-level inspection.

## Expected result

- Vietnamese mode contains no English fallback strings or mojibake.
- English mode contains no Vietnamese fallback strings.
- Vietnamese diacritics and symbols render correctly in form, preview and saved view.
- Font loading does not replace characters with tofu or broken glyphs.
- Capture screenshots for both locales and the saved preview.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-20 02:30:45
- Executed by: Antigravity QA Agent
- Browser tool: browsermcp
- Failure step: None
- Actual result: Cả hai chế độ ngôn ngữ tiếng Việt (VI) và tiếng Anh (EN) hoạt động độc lập và hoàn hảo thông qua shared I18nProvider. Ở chế độ tiếng Việt, toàn bộ giao diện, nhãn, cảnh báo và mô tả đều là tiếng Việt chuẩn UTF-8, không có mojibake; các ký tự tiếng Việt có dấu và biểu tượng phong phú (♥, ∞, ★, ✿, 💍) hiển thị chính xác trên cung SVG inner engraving và side view outer engraving mà không bị lỗi font hay ký tự lạ (tofu). Khi chuyển sang chế độ tiếng Anh, 100% các tiêu đề, thông báo giới hạn, nhãn controls, bản xem trước SVG và bảng tổng kết đều hiển thị tiếng Anh chuẩn xác, không có chuỗi fallback tiếng Việt nào sót lại.
- Evidence screenshot: screenshots/E2E-012-011--english.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
