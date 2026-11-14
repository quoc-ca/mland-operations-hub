# Feature Specification: Workshop Ring Design Configuration

Date created: 2026-11-08
Date updated: 2026-11-14
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

**Feature:** 012 — Workshop Ring Design Configuration  
**Status:** IMPLEMENTED AND VERIFIED (5/5 E2E PASS) — SPEC SYNCHRONIZED
**Parent business scope:** FE-05 Personalized Design and Quote Workflow  
**Source priority:** This feature contract, approved Feature-003 contracts,
`spec-general.md`, `DB.md`, and approved Report 3 requirements. Existing code or
visual prototypes are evidence only and do not override a documented decision.

## 1. Objective and boundary

Feature-012 lets a Guest or Member configure one workshop ring from approved
catalogue options after selecting a workshop package and before selecting a
branch/session. The feature validates the current configuration, returns an
advisory estimate/difficulty result, maintains design versions, and exposes a
safe snapshot for workshop booking.

The feature does not create a booking, hold capacity, initiate payment, issue a
ticket, publish a product, accept a custom-manufacturing order, or perform AI
analysis.

## 2. Actors and permissions

| Actor | Allowed actions | Conditions |
| --- | --- | --- |
| Guest | Browse options, create/edit/evaluate a draft, view its current summary, and pass a validated design to a booking attempt | Must hold the current opaque design-session reference; session expires after 20 minutes of inactivity. |
| Member | Browse options, create/edit/evaluate designs, view own current designs and version history, archive own unsubmitted designs, and attach an eligible design to a booking | Firebase identity is authenticated; backend verifies Member status and ownership. |
| Staff | No Feature-012 customer-design mutation or business-rule administration | May receive only data exposed through another approved operational workflow. |
| Manager | Owns approved business rules, option availability, component price inputs and difficulty rules | Management operations require the separate approved authority contract. |
| Admin | Technical/audit access only where separately granted | Does not make routine design, price or feasibility decisions. |
| Workshop Booking | Consumes validated design snapshot through the Feature-012 facade | Must not access Feature-012 repositories directly. |
| AI Provider | No direct permission | Feature-004 remains the owner of any AI call. |

Client-provided owner, role, price, difficulty, compatibility, status or
booking association is untrusted. All authorization and validation are repeated
server-side.

## 3. Design input and lifecycle

### 3.1 Entry

```text
Guest/Member
→ select published workshop package
→ open Workshop Ring Design Configuration
→ choose published model or blank design
```

The package is required because it determines the supported option set. Branch
and session are selected after the design draft, but branch compatibility is
rechecked before booking acceptance.

### 3.2 Supported parameters

The server supplies the current option schema and values for:

- Base shape and surface texture (`SHP-CLASSIC-ROUND`, `SHP-BEVELED`, `SHP-SINGLE-TWIST`, `SHP-WAVE`, `SHP-BALL-PEEN`, `SHP-FLAT-BAND`).
- Base material (`MAT-SILVER-925`, `MAT-COPPER`, `MAT-STAINLESS-STEEL`).
- Ring size (`6` through `29`, with explicit mm perimeter).
- Dimensions: Ring thickness (`thicknessMm`) and band surface width (`bandWidthMm`).
- Gemstone/component selections, quantities and positions.
- Attachment selections, quantities and placement where supported.
- Engraving content and placement where supported.

The frontend renders these controls from the server-returned option schema. It
must not hard-code available materials, sizes, components, placements,
quantities or engraving limits. Each control shows its label, current value and
a short explanation of its effect on the visual preview. Sliders are used only
for numeric or ordered options; discrete catalogue choices use selectable cards,
pills or equivalent accessible controls.

#### 3.2.1 Material Catalogue Updates

- **Deactivated Materials**: All gold variants (`MAT-GOLD-14K`, `MAT-ROSE-GOLD-14K`, `MAT-WHITE-GOLD-14K`) are deactivated (`is_active = FALSE`) for workshop crafting to align with workshop feasibility and pricing constraints.
- **Active Workshop Materials**:
  - `MAT-SILVER-925`: Bạc Ý 925 tiêu chuẩn (độ sáng cao, dễ uốn dẻo, giá cơ sở 250.000 ₫ bao gồm tối đa 3.0 gam bạc).
  - `MAT-COPPER`: Đồng Đỏ / Đồng Thau mộc mạc (phong cách vintage thô mộc, giá cơ sở 150.000 ₫).
  - `MAT-STAINLESS-STEEL`: Thép Không Gỉ 316L (chống xước cao, độ cứng vượt trội, giá cơ sở 180.000 ₫).

#### 3.2.1.1 Volumetric Mass & Gram-Based Material Pricing Formula

Để phản ánh chính xác lượng kim loại quý tiêu hao theo kích cỡ thực tế của từng khách hàng, hệ thống tính toán khối lượng nhẫn từ các thông số hình học do khách hàng cấu hình:
1. **Thông số hình học đầu vào**:
   - Chu vi lòng trong: $P = \text{Size} + 40\text{ mm}$ (với $\text{Size} \in [6, 29]$).
   - Bán kính lòng trong: $r_{\text{in}} = \frac{P}{2\pi}$.
   - Độ dày nhẫn: $T$ (`thicknessMm`, phạm vi $1.0 - 3.0\text{ mm}$, mặc định $1.8\text{ mm}$).
   - Độ rộng bề mặt đai: $W$ (`bandWidthMm`, phạm vi $1.5 - 6.0\text{ mm}$, mặc định $3.5\text{ mm}$).
2. **Thể tích hình học đai nhẫn (Hollow Cylinder / Torus)**:
   $$V = (P + \pi T) \times T \times W \quad (\text{đơn vị: mm}^3)$$
   $$V_{\text{cm}^3} = \frac{V}{1000}$$
3. **Khối lượng vật liệu ước tính ($M$ tính bằng gam)**:
   $$M = V_{\text{cm}^3} \times D$$
   Trong đó $D$ là khối lượng riêng tiêu chuẩn ($\text{g/cm}^3$):
   - Bạc Ý 925: $D = 10.36\text{ g/cm}^3$.
   - Đồng Đỏ / Đồng Thau: $D = 8.96\text{ g/cm}^3$.
   - Thép Không Gỉ 316L: $D = 7.99\text{ g/cm}^3$.
4. **Quy tắc định giá theo gam**:
   - **Định mức chuẩn**: Một chiếc nhẫn từ **3.0 gam bạc trở xuống ($M \le 3.0\text{g}$)** được tính giá cơ sở bình thường (**250.000 ₫** đối với Bạc Ý 925).
   - **Phụ phí vượt mức**: Mỗi 1 gam bạc vượt quá 3.0 gam ($\Delta M = M - 3.0\text{g}$) được tính thêm **120.000 ₫/gam**:
     $$\text{Chi phí vật liệu Bạc} = 250.000\text{ ₫} + \max(0, M - 3.0) \times 120.000\text{ ₫/gam}$$
   - Đối với Đồng Đỏ và Thép Không Gỉ: Tương tự áp dụng định mức chuẩn $3.0\text{g}$, phần vượt định mức tính phụ phí theo tỷ lệ nguyên liệu (Đồng: $40.000\text{ ₫/gam}$, Thép: $50.000\text{ ₫/gam}$).

#### 3.2.2 Ring Size Taxonomy and Perimeter in Millimeters

- Standard workshop ring sizes span from **Size 6** to **Size 29** (total of 24 sizes).
- Each size option displays its corresponding circumference/perimeter in millimeters ($P = \text{Size} + 40\text{ mm}$), e.g.:
  - Size 6: Chu vi 46 mm (Đường kính lòng trong ~14.6 mm).
  - Size 10: Chu vi 50 mm (Đường kính lòng trong ~15.9 mm).
  - Size 29: Chu vi 69 mm (Đường kính lòng trong ~22.0 mm).
- Pills and selector controls display both the size number and millimeter perimeter (e.g., `Size 10 (50 mm)`), helping customers accurately gauge their fit.

#### 3.2.3 Base Shapes and Surface Textures

- In addition to standard classic profiles, the workshop supports specialized manual crafting textures:
  - `SHP-CLASSIC-ROUND`: Phom tròn cổ điển, bề mặt đánh bóng gương.
  - `SHP-BEVELED`: Phom vát cạnh công thái học, thoải mái khi đeo hàng ngày.
  - `SHP-SINGLE-TWIST`: Phom xoắn đơn nghệ thuật, tạo điểm nhấn xoắn 180 độ tinh tế.
  - `SHP-WAVE`: Phom lượn sóng mềm mại, mô phỏng sóng nước tự nhiên.
  - `SHP-BALL-PEEN`: Bề mặt gõ búa bi tạo các vết lõm tròn li ti thủ công độc đáo.
  - `SHP-FLAT-BAND`: Đai phẳng hiện đại, góc cạnh mạnh mẽ tối giản.

#### 3.2.4 Dimensions & Dual View SVG Preview

- **Two Synchronized Viewports**:
  1. **Top View (Mặt chính diện)**: Circular ring band rendering band thickness, surface textures (ball-peen indentations, twist effect, wave curves, smooth sheen), mounted gemstones with dynamic angles, and center size watermark.
  2. **Side View (Mặt cạnh / Tiết diện ngang)**: Cylindrical cross-section rendering ring band height scaled to `bandWidthMm`, wall thickness scaled to `thicknessMm`, surface texture profile, prong settings, and gemstone silhouettes.
- **Two Real-Time Dimension Sliders**:
  - **Độ dày nhẫn (`thicknessMm`)**: Range 1.0 mm to 3.0 mm, step 0.1 mm, default 1.8 mm.
  - **Độ rộng bề mặt (`bandWidthMm`)**: Range 1.5 mm to 6.0 mm, step 0.1 mm, default 3.5 mm.
  - Both sliders update the visual SVG in real time and are reflected in the technical summary.

#### 3.2.5 Multi-Gemstone Selection & Constrained Ring-Band Drag-and-Drop

- **Multi-Gemstone Quantities**:
  - Each gemstone type card includes an interactive stepper (`[-] {count} [+]`), permitting customers to add multiple stones of that type rather than only a single stone.
  - Quantities are validated against `maxQuantity` per component (`maxQuantity = 10` for CZ stones, `maxQuantity = 4` for Moissanite).
  - Subtotal per component type is dynamically calculated as $\text{unitPrice} \times \text{quantity}$.
- **Constrained Circular Drag-and-Drop**:
  - Each selected gemstone instance is rendered on the Top View SVG preview with an index label, color tint, and highlight.
  - Users can click/touch and drag any individual gemstone to position it anywhere around the 360-degree ring band.
  - **Mathematical Constraint**: All coordinates are strictly constrained by polar projection around ring center $C(150, 135)$ with radius $R = 85\text{px}$:
    $$\theta = \text{atan2}(y - 135, x - 150)$$
    $$cx = 150 + 85 \cdot \cos(\theta), \quad cy = 135 + 85 \cdot \sin(\theta)$$
  - Gemstones **cannot be dragged outside or away from the ring circle circumference**.
  - A dashed guide track and dynamic drag halo display during the drag operation for accessibility and tactile feedback.

#### 3.2.6 Published Catalogue Models

- A minimum of 10 catalogue models (currently 12 published models) are maintained to inspire customers:
  1. Nhẫn Bạc Cổ Điển Tối Giản (`MOD-CLASSIC-01`)
  2. Nhẫn Bạc Trơn Đai Cong (`MOD-BEVEL-01`)
  3. Nhẫn Bạc Đính Đá Đơn Sắc (`MOD-SOLITAIRE-01`)
  4. Nhẫn Đôi Tình Nhân Bạc Ý (`MOD-COUPLE-01`)
  5. Nhẫn Bạc Xoắn Đơn Tinh Tế (`MOD-TWIST-01`)
  6. Nhẫn Bạc Lượn Sóng Tự Nhiên (`MOD-WAVE-01`)
  7. Nhẫn Bạc Gõ Búa Bi Thủ Công (`MOD-BALLPEEN-01`)
  8. Nhẫn Bạc Giác Búa Thô Mộc (`MOD-HAMMERED-01`)
  9. Nhẫn Đồng Đỏ Vintage Rustic (`MOD-COPPER-VINTAGE`)
  10. Nhẫn Thép Không Gỉ Phay Xước (`MOD-STEEL-BRUSHED`)
  11. Nhẫn Bạc Milgrain Viền Hạt (`MOD-MILGRAIN-01`)
  12. Nhẫn Bạc Gõ Búa Khắc Tim (`MOD-HAMMER-HEART`)

#### 3.2.7 Customer Discovery and Navigation

- A primary navigation shortcut ("Thiết kế nhẫn" / "Cấu hình nhẫn workshop") is exposed in the main navbar and workshop package detail views, enabling direct discovery without deep nested navigation.
- Consistent Vietnamese UTF-8 typography is enforced across all configurator screens, evaluation views, disclaimers, and error dialogs.

#### 3.2.8 Multi-Item Engraving, Custom Image Upload & Surface Texture Warning

1. **One-Time Flat Standard Engraving Fee (Khắc chữ & biểu tượng tiêu chuẩn - Phí 1 lần 80.000 ₫)**:
   - Khách hàng chỉ trả phí dịch vụ khắc tiêu chuẩn **1 lần duy nhất (+80.000 ₫)**.
   - Khi đã bật tùy chọn khắc, khách hàng được phép thêm **nhiều vị trí và nội dung khắc** (chữ, ngày tháng, biểu tượng nhanh: ♥, ∞, ★, ✿, 💍).
   - Mỗi mục khắc có thể chỉ định vị trí độc lập: **Mặt trong (`INNER_BAND`)** hoặc **Mặt ngoài (`OUTER_BAND`)**.
2. **Cảnh báo kết cấu bề mặt gồ ghề (Surface Texture Collision Warning)**:
   - Khi khách hàng cấu hình khắc (khắc chữ tiêu chuẩn hoặc hình ảnh) tại **Mặt ngoài (`OUTER_BAND`)** trên đai nhẫn có dáng hoặc kết cấu gồ ghề (`SHP-HAMMERED`, `SHP-BALL-PEEN`, `SHP-SINGLE-TWIST`, `SHP-TWISTED`, `SHP-WAVE`), hệ thống hiển thị banner cảnh báo kỹ thuật nổi bật:
     > *"⚠️ Lưu ý: Đai nhẫn đang chọn có kết cấu bề mặt gồ ghề (đập búa / búa bi / xoắn / lượn sóng). Hình khắc ở mặt ngoài có thể bị biến dạng hoặc không hiển thị sắc nét như mong muốn trên bề mặt thực tế. Bạn nên ưu tiên chọn khắc ở mặt trong để đảm bảo chất lượng tinh xảo."*
   - Cảnh báo được quản lý tập trung từ backend domain rules (`EvaluationResultDto.warnings`) và phản ánh tức thì trên UI configurator/evaluation.
3. **Khắc hình ảnh cá nhân hóa tải lên (Custom Image Upload - 100.000 ₫ / ảnh)**:
   - Khách hàng có thể upload file ảnh (chữ ký, logo, hình vẽ tay).
   - Đơn giá: **100.000 ₫ / ảnh**. Khách hàng có thể tải lên một hoặc nhiều ảnh (+100.000 ₫ mỗi ảnh).
   - Hỗ trợ chọn vị trí khắc cho từng ảnh: **Mặt trong (`INNER_BAND`)** hoặc **Mặt ngoài (`OUTER_BAND`)**.
   - **Xử lý ảnh màu trắng đen laser (Monochrome B&W)**: Toàn bộ pixel trong vùng chọn được chuyển đổi sang màu đơn sắc trắng đen tương phản cao (Thresholding) mô phỏng chính xác vết khắc laser trên kim loại.
   - **Khung chọn vị trí 160px x 160px (Interactive Framing Box)**:
     - Giới hạn khung cắt chuẩn 160px x 160px.
     - Cho phép người dùng kéo thả di chuyển khung chọn 160x160 trên ảnh để định vị chính xác vùng khắc mong muốn.
     - Hiển thị trực tiếp bản xem trước laser trắng đen 160px x 160px và cập nhật vào giá ước tính thời gian thực.

### 3.3 Model-based and blank configuration

- A published catalogue model can be used as a starting point.
- A blank design starts from the package's server-supplied default option set.
- Selecting a model creates a new configured-design lineage; it does not edit the
  published catalogue model.
- A configured design is private to its Guest session or Member ownership scope.

### 3.4 Save and evaluate

Unsaved edits may remain local to the active form. A successful explicit
Save/Evaluate command:

1. Revalidates all current fields against the package and active catalogue.
2. Creates a new immutable logical design version.
3. Stores the selected component snapshot and rules version.
4. Calculates the server-owned estimate and difficulty result.
5. Returns validation messages and a safe design summary.

An invalid command creates no new version. A retry of the same command uses the
same `Idempotency-Key`.

## 4. Compatibility and validation

Validation is ordered as follows:

```text
Transport
→ Guest session/Firebase identity
→ ownership and status
→ package existence/publication
→ option schema and field validation
→ active catalogue validation
→ package compatibility
→ component cardinality/placement rules
→ estimate/difficulty evaluation
→ persistence/version transition
```

The backend rejects:

- Missing required design fields.
- Inactive, unpublished or unknown options.
- Options not supported by the selected package.
- Invalid ring size, quantity, placement or engraving content.
- Client-supplied estimate, difficulty or rules version that does not match the
  server result.
- A stale design version.
- A design belonging to another Member or Guest session.

After branch selection, the backend revalidates the design against branch
material/component support. Failure returns a safe compatibility error and keeps
the design available for correction.

## 5. Estimate and difficulty

Estimate and difficulty are derived server-side from the approved Manager-owned
catalogue/rule set (`ManagerDesignRuleAdapter`). They are advisory context only:

- **Estimated Price Formula**:
  $$\text{Total Estimate} = \text{MaterialCost}(M) + \text{ShapeAdjustment} + \sum (\text{GemstoneUnitPrice}_i \times \text{Qty}_i) + \sum (\text{AttachmentUnitPrice}_j \times \text{Qty}_j) + \text{EngravingPrice}$$
  where:
  - $\text{MaterialCost}(M) = \text{BaseUnitPrice} + \max(0, M - 3.0\text{g}) \times \text{ExcessRatePerGram}$
    with $M = \frac{(P + \pi T) \cdot T \cdot W}{1000} \cdot D$, baseline threshold $M_0 = 3.0\text{g}$, and excess silver rate $= 120.000\text{ ₫/g}$.
  - **Mẫu thiết kế đai nhẫn (Shape Adjustment)**: **$0.00\text{ ₫}$ (Miễn phí toàn bộ các mẫu dáng thiết kế)**. Khách hàng tự do chọn bất kỳ mẫu thiết kế nào (Tròn cổ điển, Comfort Fit, Xoắn vô cực, Búa gõ, Xoắn đơn, Lượn sóng, Búa bi, Đai phẳng) mà không bị phụ thu thêm chi phí mẫu.
  - **Đá quý (Gemstones)**: **Đồng giá $50.000\text{ ₫/viên}$ cho tất cả các loại đá quý** (CZ Trắng, CZ Lam Biển, CZ Đỏ Ruby, Moissanite). Phí đá tính theo số lượng: $\sum (50.000\text{ ₫} \times \text{Qty}_i)$.
  - **Họa tiết trang trí (Attachments)**: **Đồng giá $100.000\text{ ₫/họa tiết}$ cho tất cả các loại họa tiết** (Hàng viền bi Milgrain, Phay xước Satin mờ, Dập chìm họa tiết Trái Tim).
  - **Phí khắc (Engraving Price)**: Khắc tiêu chuẩn 1 lần trả phí $80.000\text{ ₫}$ (khắc không giới hạn chữ/ký hiệu cả trong và ngoài); Upload ảnh khắc theo yêu cầu $100.000\text{ ₫/ảnh}$.
- They do not change the workshop package price.
- They do not change the 50% package deposit.
- They are not an invoice, quote acceptance or final manufacturing amount.
- They are stored with the evaluated design version and `rules_version`.
- Historical versions retain their evaluation snapshot when rules later change.

No AI provider is called by Feature-012 to calculate these values.

## 6. Version and status rules

The logical lifecycle is:

```text
DRAFT
  → VALIDATED
  → ATTACHED_TO_BOOKING
  → LOCKED

DRAFT/VALIDATED → ARCHIVED
```

Rules:

- Each successful Save/Evaluate creates a new design version.
- Older versions remain readable in Member history and audit projections.
- Only the latest unarchived version may be edited.
- A version attached to a booking cannot be edited or archived through the
  customer flow.
- Booking submission locks the attached version.
- A failed booking submission does not invalidate or delete the design.
- Archive is a soft-delete operation; normal feature actions never hard-delete
  design history.

The physical status vocabulary and version-column mapping must be reconciled with
the existing `DB.md` proposal before migration or implementation.

## 7. Guest session and Member persistence

### Guest

- The server issues an opaque design-session reference.
- The reference contains no raw email, phone, database ID or design payload.
- The session is scoped to the Guest flow and expires after 20 minutes of
  inactivity.
- Expiry removes the ability to mutate or attach the draft.
- The expired session response must not reveal whether a particular internal
  design row existed.

### Member

- Firebase proves identity; MySQL/backend account state proves authorization.
- Member may view only own designs and versions.
- Member history contains current design and prior version snapshots.
- Member may archive an unsubmitted design but cannot hard-delete its history.

The server-session storage mechanism is the Feature-012-owned MySQL logical
`guest_design_sessions` boundary. The frontend stores only the opaque session
reference required by the approved transport and never persists raw identity,
database IDs or design payloads in local storage. Session persistence and
cleanup remain backend responsibilities.

## 8. Booking attachment

Feature-003 calls the Feature-012 facade with a design reference/version and its
booking context. Feature-012:

1. Verifies ownership or Guest-session proof.
2. Verifies the design version is `VALIDATED` and not stale/archived.
3. Revalidates package and selected branch compatibility.
4. Returns a safe immutable design snapshot.

Feature-003 stores the snapshot as part of its approved booking boundary. Feature-
012 does not create booking, invoice, hold, payment or ticket records.

Attachment must be all-or-nothing from the booking use case's perspective. If
booking creation or payment preparation fails, the validated design remains
available for a new attempt and no partial booking side effect is claimed.

## 9. Permission matrix

| Action | Guest | Member | Staff | Manager | Admin |
| --- | --- | --- | --- | --- | --- |
| Read package-scoped options | Public package context | Public/package context | No customer mutation | Business context | Technical projection only if granted |
| Create draft | Own active Guest session | Own account | Deny | Deny as customer actor | Deny |
| Save/Evaluate | Own active Guest session | Own design | Deny | Deny as customer actor | Deny |
| View design/version | Own session only | Own designs only | Deny by default | Approved operational projection only | Technical/audit projection only |
| Archive unsubmitted design | Own active session where applicable | Own unsubmitted design | Deny | Deny | Deny |
| Attach to booking | Own active session | Own design | Deny | Deny as customer actor | Deny |
| Change business option/rule catalogue | Deny | Deny | Deny unless another approved feature grants catalogue input | Allow under Manager authority | Technical settings only |

## 10. Error, retry and concurrency behavior

| Condition | Result | Mutation/provider behavior |
| --- | --- | --- |
| Invalid schema/field | `400 DESIGN_INPUT_INVALID` | No version created. |
| Unknown/inactive option | `422 DESIGN_OPTION_INVALID` | No version created. |
| Package incompatibility | `422 DESIGN_PACKAGE_INCOMPATIBLE` | No version created. |
| Branch incompatibility | `422 DESIGN_BRANCH_INCOMPATIBLE` | No booking attachment. Design remains editable. |
| Guest session expired | `401 DESIGN_SESSION_EXPIRED` | No design mutation. |
| Wrong ownership | `403 DESIGN_ACCESS_DENIED` | No data disclosure. |
| Stale version | `409 DESIGN_VERSION_CONFLICT` | Reload/reconcile; no partial write. |
| Duplicate idempotent command | Existing result or `409 IDEMPOTENCY_CONFLICT` | No second version. |
| Option/rule service unavailable | `503 DESIGN_RULES_UNAVAILABLE` | Preserve local input; explicit retry. |
| Booking attach failure | Typed booking integration error | Design remains available; no payment/booking side effect from Feature-012. |

Write retries reuse the original `Idempotency-Key`; clients must not generate a
new key for the same user action. Versioned commands use `If-Match-Version`.

## 11. Security, privacy, audit and retention

- Never trust client-supplied owner, role, price, difficulty or compatibility.
- Do not expose database IDs, raw session values, private Member identity or
  internal audit fields in public DTOs.
- Do not store raw secrets or payment data in design records.
- Guest session data is subject to the 20-minute inactivity expiry and approved
  cleanup policy.
- Member design history is retained for three years under the approved
  business-history policy.
- Save, evaluate, archive, attach, denied access, stale conflict and rule
  failures produce minimized audit evidence with actor scope, action, outcome,
  version and correlation ID.
- The feature does not retain or process Feature-004 raw image input.

## 12. Explicit non-goals

- Retail product customization.
- Custom-manufacturing order creation, acceptance, pricing or fulfilment.
- AI image analysis or AI price suggestion.
- Workshop package price changes or payment calculation.
- Capacity, branch schedule, session creation or ticket issuance.
- Public sharing or marketplace publication of Member designs.
- Staff/Manager manual review of every valid configured design.
- Hard deletion of design history.
- A new global identity, JWT or session system.

## 13. Acceptance scenarios

1. Given a Guest has selected a published package, when valid parameters are
   saved, then the server creates a version bound to the active opaque session.
2. Given a Guest session is inactive for 20 minutes, when the Guest evaluates or
   attaches the design, then the server returns `401 DESIGN_SESSION_EXPIRED`.
3. Given a Member saves a design twice, then two versions exist and the earlier
   version remains readable in Member history.
4. Given a blank design omits a required field, then the command returns
   `400 DESIGN_INPUT_INVALID` and creates no version.
5. Given an option is inactive or unsupported by the package, then the server
   returns a typed `422` error before persistence.
6. Given the selected branch does not support the design, then attachment is
   rejected and the design remains available for correction.
7. Given a design is attached and booking submission succeeds, then the design
   version is locked and the booking stores the approved snapshot.
8. Given booking submission fails, then no partial booking/payment effect is
   created by Feature-012 and the design remains retryable.
9. Given two concurrent saves use the same version, only one succeeds; the other
   receives `409 DESIGN_VERSION_CONFLICT`.
10. Given a client sends a fake estimate or difficulty, the server ignores or
    rejects it and persists only server-derived values.
11. Given a Member archives an unsubmitted design, history remains auditable and
    no physical deletion occurs.
12. Given Feature-004 returns candidate features, no automatic Feature-012 design
    or booking mutation occurs.
13. Given a customer selects materials, only Silver 925, Copper (`MAT-COPPER`), and
    Stainless Steel 316L (`MAT-STAINLESS-STEEL`) are selectable, while Gold variants
    are inactive and rejected by backend rule checks.
14. Given a customer selects a size between 6 and 29, the interface displays the
    perimeter in millimeters ($P = \text{Size} + 40\text{ mm}$) and verifies compatibility.
15. Given a customer moves the thickness slider (1.0 - 3.0mm) or band width slider
    (1.5 - 6.0mm), both the Top View and Side View SVG viewports redraw dynamically in
    real time to reflect the physical dimensions.
16. Given a customer selects multiple gemstones per type, steppers enforce `maxQuantity`
    constraints, and individual stones can be dragged along the ring circle ($R = 85\text{px}$)
    without ever escaping outside the circular band.

## 14. Blocker closure addendum

The logical decisions below are now fixed for design review:

1. Each design version is one immutable `ring_designs` row; `source_design_id`
   records lineage and no separate version table is required in V1.
2. Guest sessions are Feature-012-owned MySQL records with opaque reference or
   digest, `ACTIVE/EXPIRED/REVOKED` state and 20-minute inactivity expiry.
3. Booking uses synchronous `attachAndLockDesignForBooking(...)`; the design
   locks only when the booking transaction commits and rollback leaves it
   retryable.
4. Evaluation is delegated to a Manager-owned catalogue/rule facade. Feature-012
   stores safe input/output snapshots and `rules_version`, but no hard-coded
   numerical pricing or difficulty formula.
5. Member design history is retained for three years; archive is soft-delete.
6. Full implementation and integration verification completed across all 5 E2E
   test scenarios (`E2E-012-001` through `E2E-012-005` PASS 100%).

## 15. Frontend interaction contract from Figma

The approved-for-review visual baseline is a spacious two-column desktop
configurator and a stacked mobile configurator. The frontend must preserve these
observable behaviors:

- The preview updates immediately from local selected values; it must not wait
  for Save/Evaluate to redraw.
- Save/Evaluate remains the server-authoritative boundary. A local preview before
  validation is clearly labelled as preview and is not a validated design or
  payable price.
- **Dual View SVG Preview**:
  - **Top View (Mặt chính diện)**: Circular 2D band scaling with `thicknessMm`, rendering surface textures (hammered ball-peen, twisted, wavy, comfort), mounted gemstone markers with drag handles, and ring size watermark.
  - **Side View (Mặt cạnh / Tiết diện ngang)**: Cylindrical profile scaling with `bandWidthMm` (height) and `thicknessMm` (wall thickness), displaying prong mounts, stone silhouettes, and textured edge profiles.
- **Interactive Dimension Sliders**:
  - Thickness slider: 1.0mm - 3.0mm, step 0.1mm.
  - Band width slider: 1.5mm - 6.0mm, step 0.1mm.
  - Sliders update the preview instantly without network calls.
- **Multi-Gemstone & Drag-and-Drop Interaction**:
  - Gemstone cards provide stepper buttons `[-]` and `[+]` allowing multiple stones up to `maxQuantity`.
  - Individual gemstones rendered on Top View can be dragged along the ring circle band ($R = 85\text{px}$). Pointer angles are computed via $\text{atan2}$ to guarantee stones strictly follow the circumference and never stray outside the ring band.
- Material, gemstone, ring size and band-width controls show the active
  selection/value. Helper text stays adjacent to the control.
- The estimate is labelled `Indicative estimate`/`Tham khảo` and keeps the
  warning that package price and deposit are unchanged.
- Branch incompatibility offers correction actions while preserving the saved
  design and safe unsaved form values.
- Guest and Member share the customer UI; backend responses determine ownership,
  session validity and authorization.
- Loading, empty, validation, unavailable-service, session-expired and retry
  states must be represented without exposing internal IDs or session values.

Figma is a visual/layout source only. It does not authorize new API fields,
catalogue values, pricing rules or business transitions.
