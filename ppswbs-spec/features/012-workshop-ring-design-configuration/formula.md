# Feature-012 Formula Reference

Date created: 2026-11-24  
Date updated: 2026-11-24  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt  

## 1. Purpose and source of truth

This document records the difficulty and related evaluation formulas currently implemented by Feature-012. The executable source of truth is:

- Backend: `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/ringdesignconfiguration/rules/ManagerDesignRuleAdapter.java`.
- Catalogue seed data: `V110__workshop_ring_design_configuration.sql` and `V142__ring_design_copper_steel_and_models.sql`.
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

The evaluation also adds material base price, shape price adjustment, gemstone cost by quantity, attachment cost by quantity, standard engraving price, and custom-image price. If estimated mass exceeds `3.00 g`, an excess-material surcharge is calculated. These amounts are not difficulty points.

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
if total gemstone quantity > 5:
    recommend Silver Clay
else if difficultyScore > 9:
    recommend Wax Casting
else:
    recommend the applicable Signature package
```

When both conditions are true, the current Feature-012 baseline gives the primary recommendation to Wax Casting. The Wax Casting message must explain that the workshop requires two sessions on different days. The recommendation is advisory and must not hide other workshop options or create booking/payment state.

## 8. Important implementation observations

- Difficulty is server-owned and catalogue-driven; clients must not submit or override the result.
- Missing or unknown catalogue options do not contribute their difficulty in the current adapter, although request validation is expected to reject invalid options before evaluation.
- Ring size, dimensions, placement, engraving text count, symbols, image preprocessing, package, branch, and catalogue model do not currently contribute to the difficulty score.
- The frontend displays the score as `/10`, but the backend does not clamp the value to that range. This is a presentation inconsistency that should be resolved in a future approved change if a strict 0–10 scale is required.
- Any change to coefficients, normalization, maximum score, or recommendation precedence requires a new Manager-approved rules version and corresponding backend, frontend, API, and test updates.
