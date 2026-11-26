# Feature-012 Formula Reference

**Date created**: 2026-11-24
**Date updated**: 2026-11-26
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

## 1. Purpose and source of truth

Sections 1–8 record the formula currently implemented by Feature-012; Section 9 records the proposed Admin-owned policy and does not claim that code has changed. The executable current source of truth is:

- Backend: `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/ringdesignconfiguration/rules/ManagerDesignRuleAdapter.java`.
- Catalogue seed & pricing data: `V110__workshop_ring_design_configuration.sql`, `V142__ring_design_copper_steel_and_models.sql`, and `V204__update_ring_materials_base_pricing.sql`.
- Frontend classification: `ppswbs_frontend/src/features/ringdesignconfiguration/domain/designPreview.js`.

The backend owns the evaluation result. The frontend only formats and classifies the server-returned `difficultyScore`; it must not recalculate or override it.

Current backend rules version:

```text
2026.11-v1.0
```

The result is advisory and does not change the official workshop package price, deposit, booking, payment, or order state.

## 2. Difficulty formula

Let:

- `M` = material difficulty score;
- `S` = shape difficulty score;
- `G_i` = difficulty score of gemstone/component type `i`;
- `Q_i` = quantity of gemstone/component type `i`;
- `A_j` = difficulty score of attachment type `j`;
- `N_j` = quantity of attachment type `j`;
- `E` = `1` when engraving is enabled, otherwise `0`;
- `I` = number of custom laser engraving images.

The current formula is:

```text
D = M
  + S
  + Σ(G_i × Q_i)
  + Σ(A_j × N_j)
  + (0.5000 × E)
  + (0.3000 × I)
```

The backend returns:

```text
difficultyScore = round(D, 4 decimal places, HALF_UP)
```

There is no normalization, weighting, clamping, or automatic maximum of `10` in the backend formula. Therefore, a design may legitimately produce a score greater than `10`, such as `14.1000`.

## 3. Difficulty parameters

### 3.1 Material

The material contribution is read from `ring_materials.difficulty_score` for the selected material. Current seeded values include:

| Material reference | Difficulty |
| --- | ---: |
| `MAT-SILVER-925` | `1.0000` |
| `MAT-COPPER` | `1.0000` |
| `MAT-STAINLESS-STEEL` | `1.2000` |
| `MAT-GOLD-14K` | `2.5000` |
| `MAT-ROSE-GOLD-14K` | `2.5000` |
| `MAT-WHITE-GOLD-14K` | `2.8000` |

The active/inactive flag and the current catalogue row are authoritative at runtime. Some gold rows are deactivated by later migration rules, so a seeded score does not imply that the option is currently selectable.

### 3.2 Shape

The shape contribution is read from `ring_shapes.difficulty_score`.

| Shape reference | Difficulty |
| --- | ---: |
| `SHP-CLASSIC-ROUND` | `1.0000` |
| `SHP-COMFORT-FIT` | `1.2000` |
| `SHP-TWISTED` | `2.0000` |
| `SHP-HAMMERED` | `1.8000` |
| `SHP-SINGLE-TWIST` | `1.4000` |
| `SHP-WAVE` | `1.6000` |
| `SHP-BALL-PEEN` | `1.8000` |
| `SHP-FLAT-BAND` | `1.3000` |

### 3.3 Gemstone/component

Each selected gemstone contributes its catalogue difficulty multiplied by its quantity. Current seeded values include:

| Gemstone reference | Difficulty per unit | Maximum quantity in catalogue seed |
| --- | ---: | ---: |
| `GEM-CZ-WHITE-2MM` | `0.8000` | `10` |
| `GEM-CZ-BLUE-2MM` | `0.8000` | `10` |
| `GEM-CZ-RED-2MM` | `0.8000` | `10` |
| `GEM-MOISSANITE-3MM` | `1.5000` | `4` |

The aggregate gemstone quantity limit is a separate validation rule. It does not change the formula; it prevents an invalid configuration from reaching evaluation.

### 3.4 Attachment

Each selected attachment contributes its catalogue difficulty multiplied by its quantity.

| Attachment reference | Difficulty per unit |
| --- | ---: |
| `ATT-BEAD-EDGE` | `1.2000` |
| `ATT-SATIN-FINISH` | `0.8000` |
| `ATT-HEART-EMBOSS` | `1.0000` |

### 3.5 Engraving

When `engraving.enabled = true`, the backend adds exactly `0.5000` once. The current score does not add a separate amount for:

- number of text engraving items;
- number of symbol engraving items;
- text length;
- engraving placement;
- contrast slider;
- sharpness slider.

Custom uploaded laser images add `0.3000` per image, independently of the standard engraving bonus:

```text
custom image contribution = image count × 0.3000
```

The current implementation counts the `customImages` collection whenever it is present. It does not add a further difficulty coefficient based on image content or preprocessing values.

## 4. Worked example

For a design with:

- Silver 925: `1.0000`;
- Comfort Fit shape: `1.2000`;
- two CZ gemstones: `2 × 0.8000`;
- one Milgrain attachment: `1 × 1.2000`;
- standard engraving enabled: `0.5000`;
- one custom laser image: `1 × 0.3000`;

the score is:

```text
D = 1.0000
  + 1.2000
  + (2 × 0.8000)
  + (1 × 1.2000)
  + 0.5000
  + (1 × 0.3000)
  = 5.8000
```

The existing backend unit-test example without the custom laser image is:

```text
1.0000 + 1.2000 + (2 × 0.8000) + 1.0000 + 0.5000 = 5.3000
```

## 5. Values used for evaluation but not difficulty

The following parameters are read by the same evaluation method but currently affect estimated weight or price, not `difficultyScore`:

### 5.1 Ring geometry

```text
perimeterMm = ringSize + 40.0
thicknessMm = request.thicknessMm, default 1.8
bandWidthMm = request.bandWidthMm, default 3.5
```

The estimated hollow-band volume is:

```text
volumeMm3 = (perimeterMm + π × thicknessMm)
             × thicknessMm
             × bandWidthMm
volumeCm3 = volumeMm3 / 1000
```

### 5.2 Estimated material mass

```text
estimatedWeightGrams = volumeCm3 × materialDensity
```

Current densities:

| Material | Density |
| --- | ---: |
| `MAT-SILVER-925` | `10.36 g/cm³` |
| `MAT-COPPER` | `8.96 g/cm³` |
| `MAT-STAINLESS-STEEL` | `7.99 g/cm³` |

### 5.3 Price-related values

The evaluation calculates estimated price and material cost based on authoritative catalogue and physical attributes:

1. **Base material cost ($\le 3.00\text{ g}$)**:
   - For designs with estimated mass up to $3.00\text{ g}$, the base material price is fixed:
     - `MAT-SILVER-925`: **900,000 VND** (a 1.0g, 2.5g or 3.0g silver ring all have base price 900,000 VND)
     - `MAT-COPPER`: **750,000 VND**
     - `MAT-STAINLESS-STEEL`: **750,000 VND**
2. **Excess material surcharge ($> 3.00\text{ g}$)**:
   - When estimated mass exceeds $3.00\text{ g}$, the excess mass is charged at a fixed rate of **120,000 VND** per excess gram across all supported materials:
     $$\text{excessWeight} = \text{estimatedWeight} - 3.00$$
     $$\text{excessMaterialCost} = 120,000 \times \text{excessWeight}$$
     $$\text{materialCost} = \text{baseUnitPrice} + \text{excessMaterialCost} = \text{baseUnitPrice} + 120,000 \times (\text{estimatedWeight} - 3.00)$$
   - Estimated mass above $3.00\text{ g}$ produces an advisory excess-material surcharge, not a design rejection or an automatic workshop payment.
3. **Add-on components**:
   - Shape adjustment (`ring_shapes.price_adjustment`)
   - Gemstones (`gemstone.unitPrice × quantity`)
   - Attachments (`attachment.unitPrice × quantity`)
   - Standard engraving: **80,000 VND** once when enabled
   - Custom laser images: **100,000 VND** per image

$$\text{estimatedPrice} = \text{materialCost} + \text{shapeAdjustment} + \sum(\text{gemstoneCost}) + \sum(\text{attachmentCost}) + \text{engravingCost}$$

These amounts are advisory, are not difficulty points, and do not alter workshop package deposit or confirmed bookings.

## 6. Frontend difficulty classification

The frontend function `getDifficultyMeta()` formats the backend value to one decimal place and classifies it as follows:

| Backend score | Frontend level | Vietnamese label |
| ---: | --- | --- |
| `≤ 3.0` | `EASY` | Dễ |
| `> 3.0` and `≤ 6.0` | `MEDIUM` | Trung bình |
| `> 6.0` and `≤ 9.0` | `HARD` | Khó |
| `> 9.0` and `≤ 11.0` | `VERY_HARD` | Rất khó |
| `> 11.0` | `TOO_RISKY` | Quá rủi ro |

This is presentation logic only. It does not modify the server score.

## 7. Workshop recommendation thresholds

Recommendation is a separate rule from difficulty calculation:

```text
if difficultyScore > 11:
    show TOO_RISKY; no workshop recommendation
else if difficultyScore > 9:
    recommend Wax Casting
else if total gemstone quantity > 5:
    recommend Silver Clay
else:
    recommend the applicable Signature package
```

This is the order observed in the current frontend, including the `>11` no-workshop state. When Wax and Silver Clay conditions are both true below that safety cutoff, Wax is primary. The Wax Casting message must explain that the workshop requires two sessions on different days. The recommendation is advisory and must not hide general workshop discovery or create booking/payment state.

## 8. Important implementation observations

- Difficulty is server-owned and catalogue-driven; clients must not submit or override the result.
- Missing or unknown catalogue options do not contribute their difficulty in the current adapter, although request validation is expected to reject invalid options before evaluation.
- Ring size, dimensions, placement, engraving text count, symbols, image preprocessing, package, branch, and catalogue model do not currently contribute to the difficulty score.
- The frontend displays the score as `/10`, but the backend does not clamp the value to that range. This is a presentation inconsistency that should be resolved in a future approved change if a strict 0–10 scale is required.
- Current coefficients and classification/recommendation thresholds are not yet administered through an API. The Manager-owned wording in historical artifacts is superseded for the proposed policy by Section 9; it is not evidence of a completed permission change.

## 9. Proposed Admin-owned policy — 2026-11-26

The additive expression in Section 2 and four-decimal `HALF_UP` rounding remain
fixed. An Admin may change each material, shape, gemstone and attachment
contribution, the `0.5000` engraving coefficient, the `0.3000` per-image
coefficient, the level cutoffs, the Wax Casting score cutoff and Silver Clay
gemstone-count cutoff. Quantities remain design input, not Admin settings.
No arbitrary formula expression, new contribution type, normalization or clamp
is offered in this change.

The initial policy copies the currently seeded scores and coefficients, with
level cutoffs `3`, `6`, `9`, `11`, Wax cutoff `9` and gemstone-count cutoff `5`.
The score remains unbounded above; customer labels omit `/10`. The server
classifies and recommends from the same immutable policy version used to
calculate a newly saved score. The ordered decision is: score above the
too-risky cutoff means no workshop recommendation; otherwise score above Wax
cutoff means Wax Casting when that workshop supports the saved material;
otherwise gemstone count above its cutoff means Silver Clay for the silver
family, with explicit S999 workshop-material disclosure for an S925 design;
otherwise use the applicable Signature mapping. With no approved compatible
package, return `NO_COMPATIBLE_PACKAGE` and consultation/general discovery.
Copper and Steel 316L currently follow that no-package path at every score.
Equality stays in the lower category at every `>` boundary.
The candidate preview and publication also evaluate every active published
model from its full default material, shape, gemstone and attachment inputs.
Every model must have complete contributions and score at or below the
candidate too-risky cutoff. Representative standard examples alone cannot
prove that invariant. An unscored newly active option may be visible but
disabled only when no active published model depends on it.

The previous Section 7 draft listed gemstone count first although its prose
said Wax wins when both apply. Section 7 now records the observed frontend
order; the proposed policy makes that order server-owned and configurable only
at its numeric cutoffs. This is a proposed contract change until the
Feature-012 design pack is approved. Historical scores and policy versions are
not recalculated merely because the Admin publishes new values. Artisan ratings
may later be compared
with predicted scores; configuration capability alone is not accuracy evidence.
