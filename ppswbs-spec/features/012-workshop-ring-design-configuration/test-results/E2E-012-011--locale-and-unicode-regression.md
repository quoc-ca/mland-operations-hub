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
