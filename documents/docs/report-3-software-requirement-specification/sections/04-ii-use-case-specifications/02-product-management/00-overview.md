## 2. Product Management

This feature covers public discovery and maintenance of retail ring products through UC11–UC14. Product is the shared catalogue parent; each predefined size/engraving combination is a separately identified purchasable variant with its own price and manual remaining-unsold quantity.

| Use Case | Specification |
| --- | --- |
| UC11 | [Browse Product Catalogue](01-browse-product-catalogue.md) |
| UC12 | [Create Product](02-create-product.md) |
| UC13 | [Update Product Information](03-update-product-information.md) |
| UC14 | [Publish or Unpublish Product](04-publish-or-unpublish-product.md) |

**Group decision — confirmed feature baseline**: The [Product Management feature specification](../../../../../../ppswbs-spec/features/002-product-management/spec.md), decisions D-01–D-11 and the user's approval of proposals Q01–Q20, supplies the confirmed business behavior below. The [use-case inventory](../../03-i-overall-requirements/04-user-requirements/02-use-cases.md), [permission matrix](../../03-i-overall-requirements/04-user-requirements/04-permission-matrix.md), and [entity descriptions](../../03-i-overall-requirements/03-conceptual-data-model/02-entity-descriptions.md) supply existing context; differences requiring alignment are explicitly identified rather than silently inferred.

| Area | Confirmed behavior |
| --- | --- |
| Authority | All five platform roles may browse public content. Active Staff maintains products, variants, images, quantities, price proposals/application, and publication. Manager maps to OWNER and approves prices; other maintenance requires explicit applicable delegation. Admin maps to ADMIN_TECHNICAL and has no business mutation or price-approval rights. Authority is checked at submission; role names alone do not grant permission. |
| Variant selection | Staff configures predefined size/engraving combinations, unique within each product. Customers select existing variants; free-text retail engraving and customer-created variants are excluded. Configuration becomes immutable after any hold/order use, even after a hold expires; approved price and quantity may still change. |
| Price workflow | New products are saved unpublished with proposed prices. Only Manager approves exact variant/configuration/amount/currency terms inside the system. A permitted maintainer explicitly applies matching approved prices. A proposal or approval alone neither changes an effective sale price nor publishes a product; changed terms need fresh review. |
| Publication | Publish requires an active category, valid shared attributes, at least one usable image and configured variant, and an applied Manager-approved price/currency with nonnegative quantity for every variant. Zero quantity is allowed. A maintainer save that breaks these prerequisites is rejected as a whole; it does not automatically unpublish. |
| Purchase preservation | Applied price changes and unpublish affect new checkout immediately. Previously accepted valid held orders retain frozen variant/options/price/amount and original 10-minute payment-link and 15-minute hold deadlines. Catalogue actions do not extend/revive holds or automatically cancel orders. Distinct variants require distinct purchase lines/hold identities. |
| Accountability | Creation, updates, proposals, approvals, price application, and publication attempts retain safe attributable SUCCESS/REJECTED/FAILED evidence with five-year business audit retention. Prior reviewed terms/decisions are preserved; credentials, tokens, and avoidable personal data are excluded. |

**Scope boundary**: Human catalogue price approval/application belongs to this feature and does not require an AI suggestion. AI suggestion integration (UC31), category/delegation administration, product/variant deletion, customer custom manufacturing, workshop components, full inventory, physical-stock guarantees, and cart/checkout/payment/fulfilment implementation are outside UC11–UC14. These use cases state the purchase contract but do not perform purchase operations. Core catalogue forms/links remain usable without optional browser scripting; sign-in remains a separate identity dependency.

**Observed — source alignment required**: Existing Product-only price/quantity and product-only Order Item aggregation must be reconciled with variant identity and frozen option snapshots. Generic Restricted permissions do not establish an Admin business override. Only this Product Management subsection is updated here; the entity model, general permission matrix, and purchase specifications still require corresponding alignment. Legacy records must not be assigned guessed variant options.

**Group decision — approved operational requirements**: The user accepted proposals Q01–Q20. The following values and behaviors are requirements; the retained A/D identifiers trace the earlier specification questions.

| Reference | Approved requirement |
| --- | --- |
| D-04 / Q01 | Trim leading/trailing whitespace; product name is 1–200 characters and description is 1–5,000 characters. |
| D-04 / Q02–Q03 | At most 10 images per product, 5 MB per image, JPEG/PNG/WebP only. Each image requires alternative text of 1–200 characters. A usable image has been stored successfully, is readable, and conforms to an allowed format. Draft images are optional; publish needs at least one usable image. |
| D-04 / Q04 | V1 uses VND only. Proposed/applied prices are positive whole numbers; reject decimal amounts rather than silently round or convert currency. |
| D-04 / Q05 | Public access to catalogue images follows product public eligibility. Draft images require authority for the target product; a media link does not bypass this boundary. |
| D-04 / Q06 | Removing/replacing an image removes its product reference, not the shared file immediately. Retain files still referenced or needed for history. Delete eligible unreferenced files after 30 days; remove failed-upload files unlinked to catalogue data after 24 hours. Custom-reference-image deletion rules do not override this catalogue policy. |
| A-01 / Q12 | Search trimmed case-insensitive substrings of shared name/description. Combine active category, inclusive VND variant price bounds, and availability with AND. Order by name then stable product identity; reject negative bounds, min above max, unknown/inactive category, and unsupported criteria. Clearing criteria restores eligible public results. |
| A-09 / Q13 | Cards show min–max applied variant prices, or one price if equal; selected variants show exact VND price. Combined price/availability filters must match the same variant. Product media is shared across variants. |
| A-02/A-05 / Q14 | Availability is the variant's remaining-unsold quantity minus its active holds. Product availability requires at least one available variant. Reject quantity reductions below that same variant's held total; do not substitute another variant or alter holds. |
| A-04 / Q15 | Draft creation requires valid name/description, active category, at least one configured variant, a positive whole-VND proposal, and nonnegative integer quantity. Images and prior approval are not required to save the unpublished draft. |
| A-06 / Q16 | Reject stale conflicting submissions with reload/review guidance; never silently overwrite an accepted concurrent change. |
| A-07 / Q17 | A permitted repeated request for the current publication state succeeds without changing state or creating a second transition. Check current authority and retain safe attempt evidence. This does not establish creation retry deduplication. |
| A-11 / Q18 | Loss of every readable image through storage failure excludes the product from public reads while preserving its publication flag/history. When usable media returns, restore public eligibility if all other prerequisites still hold. Use safe fallback/retry for transient failure. |
| A-10 / Q19 | Repeated selections may merge only for the same product/variant with identical price and snapshot terms. Different variants or different agreed prices/snapshots remain separate. Never rewrite existing order snapshots. |
| D-06 / Q08 | Staff configures Mland-provided size/engraving values and their valid combinations. Include the engraving choice “Không khắc” (No engraving); customers select predefined variants only. No ring-size system, additional enum values, or SKU is inferred. |
| D-06 / Q09 | Preserve legacy product-only order snapshots and existing valid hold terms. Review/configure legacy products into variants before allowing new checkout; never guess historical size/engraving or destructively relabel old records. |
| D-05 / Q10 | Support the two most recent major versions of Chrome, Edge, Firefox, and Safari on desktop/mobile. Core controls support keyboard use, clear focus, labels, and understandable validation errors. Optional scripting is not required for catalogue forms/links. |
| D-05 / Q11 | Acceptance workload: 1,000 products, 10,000 variants, 50 concurrent users. At least 95% of catalogue read/search requests complete within 2 seconds. Monthly service availability target is 99.5%. These are approved targets, not observed results. |
| A-08 / Q20 | Usability review uses at least 10 browse participants and 10 authorized-maintainer trials with prepared catalogue/inputs/media and no facilitator assistance. At least 90% complete search within 2 minutes and unpublished-draft creation within 5 minutes; time from page entry to confirmation and report participant/trial composition. |

**Dependencies retained for planning/configuration**:

| Reference | Remaining input and boundary |
| --- | --- |
| D-04 / Q07 | Catalogue-storage provider selection is explicitly deferred to the plan. The approved access, media validation, and retention requirements apply to whichever provider is selected. |
| D-06 / Q08 | Mland must supply the actual size labels and predefined engraving templates for initial configuration. Their real values have not been supplied by the blanket approval; do not invent them. Configuration and legacy-data compatibility mechanics belong in the plan. |

The business baseline is confirmed. Provider choice and reference-data inputs remain explicit dependencies; reviewer checklist completion, technical planning, and implementation approval are separate steps.
