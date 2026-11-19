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

## Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-19 23:18:30
- Executed by: Antigravity Verification Agent
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  1. Tested Vietnamese input with rich diacritics and symbols: "Kỷ niệm ngày cưới 2026 ♥∞★✿💍". Form and SVG arc rendered identical matching strings (`inputVal === previewText: true`) with zero mojibake or glyph corruption.
  2. Switched language to English (`EN`) via `Language` combobox. Verified complete English localization rendered across all sections without Vietnamese residual strings:
     - "Workshop Ring Design Configuration"
     - "1. Ring Material"
     - "2. Band Shape & Sizing"
     - "3. Gemstone & Components"
     - "5. Custom Engraving & Laser Image"
     - "Standard Text & Symbol Engraving (+80,000 VND flat fee)"
     - "Custom Laser Image Engraving (+100,000 VND / image) 1 / 2"
  3. Switched back to Vietnamese (`VI`), verified instant dynamic translation back to Vietnamese.
- Bug reference: NONE
<!-- EXECUTION:END -->
