---
id: JOURNEY-PHASE-4
feature: 004-ring-design-configuration
journey_phase: 4
test_level: master_e2e
test_case_type: First-Time-User-Parametric-Design-Discovery
source_ref: spec.md#FE-04; ringdesignconfiguration; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/workshops/PKG-COUPLE-SILVER/design
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/workshops/PKG-COUPLE-SILVER/design
actor_role: MEMBER
actor_primary: MEMBER (Browser 1 - Chrome)
actor_secondary: null
browser_tool: browsermcp
environment: mock-prod
test_data:
  isolation_key: "e2e_phase4_parametric_ring"
  package_reference: "PKG-COUPLE-SILVER"
  base_model_reference: "MOD-WS-SILVER-CLASSIC"
  material_choice: "Bạc Ý 925"
  band_shape: "Tròn cổ điển"
  intended_size: 16
  gemstone_choice: "Đá CZ Xanh Lam 2mm"
  engraving_option: "Khắc chữ tiêu chuẩn"
  band_thickness_mm: 1.8
  band_width_mm: 3.5
  expected_weight_grams_range: [3.8, 4.3]
  expected_difficulty_range: [1.5, 3.0]
cleanup:
  required: false
  method: handoff-to-phase-5
---

# JOURNEY-PHASE-4: Khám phá Cấu hình Nhẫn Workshop, Mô phỏng 2D Trực quan, Thẩm định Kỹ thuật & Sổ tay Thiết kế Độc bản

**Phiên bản**: 3.0 (Parametric Ring Design & Wayfinding Discovery) | **Cập nhật**: 2026-11-17  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tâm thế kiểm thử (Mindset)**: Người dùng mới muốn tự tay phối màu, chọn phôi nhẫn, gắn đá và khắc tên trước khi đến workshop thực tế; chủ động dò tìm lối vào, thử nghiệm độ mượt của các thanh trượt tham số (Slider), đánh giá tính trực quan của bản vẽ mô phỏng 2D và kiểm tra báo cáo độ khó chế tác từ hệ thống.  
**Trình duyệt sử dụng**: **Browser 1 (Khách hàng / Client View - Chrome)**  
**Môi trường**: Mock Prod (`https://mland-operations-hub-git-develop-mland-team.vercel.app/`)

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng toàn diện năng lực Dẫn dắt (Wayfinding), Tính trực quan (Visual Affordance) và Trải nghiệm Tương tác (UX Interactivity) của tính năng Cấu hình Nhẫn Tham số Xưởng (Parametric Ring Configurator):
- Khách hàng Hội viên tiếp cận cổng cấu hình nhẫn từ thanh Header công khai hoặc liên kết chéo từ trang Workshop.
- Trải nghiệm khám phá Thư viện mẫu gợi ý theo gói (`/design/models`) với 12 mẫu phôi đa chất liệu (Bạc, Đồng, Thép).
- Thử nghiệm phối hợp các tham số kim hoàn thực tế tại xưởng ảo (`/design/configure`): Chất liệu (Bạc Ý 925), Dáng đai (Tròn cổ điển), Size ngón tay (Size 16), Đính đá CZ xanh lam, Khắc chữ laser cá nhân hóa và kéo thanh trượt Độ dày/Độ rộng bản nhẫn.
- Đo lường độ trễ phản hồi (Latency $\le 8s$) và độ rung giật (Screen Jitter $< 3$ fliks) khi kéo slider và cập nhật giá tức thì.
- Phân tích bản vẽ mô phỏng 2D trực quan (Mặt chính diện Top View, Mặt cắt cạnh Side View) và bảng chỉ số thẩm định kỹ thuật: Khối lượng ước tính, Độ khó gia công (thang đo 1-10) và Chi phí tham khảo.
- Lưu trữ thiết kế thành công vào Sổ tay thiết kế cá nhân (`/my/designs`) và kiểm tra tính liên tục của dữ liệu.

---

## 2. Phạm vi Kiểm thử (Scope Boundaries)

### 2.1. Trong phạm vi kiểm thử (In-Scope):
- Khả năng nhận diện lối vào tính năng từ Navbar ("Cấu hình nhẫn" $\rightarrow$ `/workshops/PKG-COUPLE-SILVER/design`).
- Bộ sưu tập mẫu nhẫn gợi ý theo gói workshop và trang lựa chọn điểm bắt đầu (Mẫu gợi ý vs Phôi trắng).
- Bộ công cụ cấu hình tham số: Radio button chọn chất liệu, Dáng đai, Lưới chọn Size tay (Size 6 đến 29), Thêm/Bớt đá quý, Checkbox khắc chữ, Slider điều chỉnh độ dày (1.0 - 3.0mm) và độ rộng (1.5 - 6.0mm).
- Độ mượt UI, layout shifts, không xuất hiện hiện tượng giật chớp màn hình ($\ge 3$ fliks).
- Màn hình kết quả thẩm định kỹ thuật (`/workshops/:packageReference/design/:designId`): Tỷ lệ bản vẽ 2D, thông số kỹ thuật, ghi chú độ khó chế tác.
- Chức năng xác nhận lưu trữ thiết kế (Dialog confirm) và hiển thị trong danh sách "💍 Mẫu nhẫn đã lưu" tại `/my/designs`.

### 2.2. Ngoài phạm vi kiểm thử (Out-of-Scope — Tuyệt đối không thực hiện):
- Không kiểm tra mô hình 3D WebGL/Three.js nặng nếu môi trường mock prod đang sử dụng bản vẽ phẳng SVG/Canvas 2D.
- Không thử nghiệm spam kéo slider hàng trăm lần/giây dạng DDoS làm nghẽn tiến trình render của trình duyệt.
- Không can thiệp mã nguồn backend/frontend khi gặp sai lệch tính toán khối lượng; chỉ ghi nhận hiện trạng và công thức hiển thị.

---

## 3. Tiền điều kiện & Dữ liệu Kiểm thử (Preconditions & Test Data)

- **Trình duyệt**: Browser 1 (Chrome), phiên đăng nhập của Hội viên (`minikie456@gmail.com`).
- **Gói Workshop liên kết**: `PKG-COUPLE-SILVER` (Workshop Làm Nhẫn Đôi Bạc Ý 925).
- **Mẫu tham chiếu cơ sở**: `MOD-WS-SILVER-CLASSIC` (Nhẫn Bạc Tròn Cổ Điển).
- **Bộ thông số cấu hình thử nghiệm**:
  - Chất liệu: `Bạc Ý 925 (Sterling Silver)` (Đơn giá cơ sở: 250.000 ₫).
  - Dáng đai: `Dáng tròn cổ điển (Classic Round)`.
  - Size tay: `Size 16 (56 mm)`.
  - Đá đính: `Đá CZ Xanh Lam 2mm (Sapphire Blue)` (+30.000 ₫).
  - Khắc chữ: Bật `Khắc chữ & biểu tượng tiêu chuẩn` (+80.000 ₫).
  - Độ dày đai nhẫn: `1.8 mm` (Tiêu chuẩn).
  - Độ rộng bề mặt: `3.5 mm` (Vừa vặn).
- **Ngưỡng thẩm định kỳ vọng (Expected Technical Specs)**:
  - Khối lượng ước tính: $\approx 4.02\text{ g}$.
  - Độ khó chế tác: $2.0 / 10$ (Mức: "Dễ").
  - Chi phí tham khảo: $\approx 372.400\text{ ₫}$.

---

## 4. Chu trình Khám phá & Dò tìm Lối đi (Discovery & Wayfinding Flow)

### Bước 1 (P4-S01): Dò tìm Lối vào Cấu hình Nhẫn từ Header & Trang chủ

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Mở trang chủ Mland Atelier tại `/`. Mắt người dùng tìm kiếm lối đi tạo nhẫn riêng.
  - Trên thanh điều hướng (Navbar): Có thấy link **"Cấu hình nhẫn"** (`/workshops/PKG-COUPLE-SILVER/design`) nằm ngay cạnh menu "Workshop" không?
  - Click vào liên kết "Cấu hình nhẫn".
  - Tiêu điểm mắt nhìn vào chính giữa màn hình (Center of Screen): Có thấy tiêu đề lớn *"Cấu hình mẫu nhẫn Workshop"* và 2 thẻ lựa chọn lớn:
    1. Thẻ 1: **"Mẫu gợi ý theo gói"** (kèm nút "Chọn mẫu này").
    2. Thẻ 2: **"Tự thiết kế từ phôi trắng"** (kèm nút "Bắt đầu cấu hình").
- **(b) Thử nghiệm & Kiểm tra Ngõ cụt (Dead-end Probing)**:
  - Kiểm tra xem 2 thẻ này có trạng thái hover rõ nét (đổi màu viền, nâng bóng đổ) để báo hiệu cho người dùng biết là có thể click được không?
  - Kiểm tra nếu người dùng chưa chọn gói nào thì hệ thống có tự động fallback về gói mặc định `PKG-COUPLE-SILVER` an toàn không.
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Bấm vào nút **"Chọn mẫu này"** tại thẻ *"Mẫu gợi ý theo gói"*.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu trang tải ra màn hình trắng, báo lỗi 404 hoặc không có nút đi tiếp.
  - **SOFT FAIL**: Nếu thẻ không có hiệu ứng hover hoặc ảnh minh họa gói bị mờ.

---

### Bước 2 (P4-S02): Khám phá Thư viện Mẫu Gợi ý (`/design/models`) & Lựa chọn Mẫu Phôi

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Chuyển hướng sang URL: `/workshops/PKG-COUPLE-SILVER/design/models`.
  - Màn hình hiển thị tiêu đề: *"Mẫu gợi ý theo gói"*, bên dưới là nút điều hướng *"← Quay lại chọn mẫu"*.
  - Quan sát bố cục lưới: Có hiển thị danh sách 12 mẫu phôi nhẫn đa dạng chất liệu (Bạc Tròn Cổ Điển, Bạc Xoắn Vô Cực, Bạc Lượn Sóng Đại Dương, Đồng Mộc Vintage, Thép Phay Xước...) không?
- **(b) Thử nghiệm & Kiểm tra Ngõ cụt (Dead-end Probing)**:
  - Thử cuộn duyệt các thẻ mẫu: Kiểm tra xem mỗi thẻ có ghi rõ tên mẫu, ảnh render và nút "Chọn mẫu này" không.
  - Kiểm tra nút *"← Quay lại chọn mẫu"*: Click thử xem có quay về trang bước 1 mà không làm mất trạng thái hay không.
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Tìm và bấm nút **"Chọn mẫu này"** tại thẻ đầu tiên: **"Mẫu nhẫn Bạc Tròn Cổ Điển"** (`MOD-WS-SILVER-CLASSIC`).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu danh sách mẫu bị trống rỗng hoặc bấm chọn không chuyển trang.
  - **SOFT FAIL**: Nếu thẻ hiển thị chữ quá sát viền (Spacing Density kém).

---

### Bước 3 (P4-S03): Tinh chỉnh Tham số Nhẫn & Kiểm tra Jitter (`/design/configure`)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Chuyển hướng sang `/workshops/PKG-COUPLE-SILVER/design/configure?mode=MODEL&modelRef=MOD-WS-SILVER-CLASSIC`.
  - Quan sát cấu trúc trang: Bố cục chia phân mục khoa học:
    - *Mục 1*: Chất liệu nhẫn (Bạc Ý 925, Đồng, Thép).
    - *Mục 2*: Dáng đai & Lưới chọn Size tay (Size 6 đến 29).
    - *Mục 3*: Đá quý đính kèm.
    - *Mục 4*: Họa tiết trang trí.
    - *Mục 5*: Khắc chữ & Hình ảnh cá nhân hóa.
    - *Mục 6*: Thanh trượt (Slider) tùy chỉnh Độ dày và Độ rộng bề mặt.
    - *Cột phải / Dưới cùng*: Hộp "Tóm tắt thông số cấu hình" với nút CTA chính **"Lưu & Thẩm định"**.
- **(b) Thử nghiệm & Đo lường Trải nghiệm (UX Interaction Probing)**:
  - **Thao tác 1 (Chọn Size)**: Bấm chọn nút **"Size 16 (56 mm)"** $\rightarrow$ Kiểm tra nút có đổi sang trạng thái active (viền đen đậm / nền tương phản) ngay lập tức không.
  - **Thao tác 2 (Thêm Đá quý)**: Tại ô "Đá CZ Xanh Lam 2mm", bấm nút **"+ Thêm đá"** $\rightarrow$ Kiểm tra thông số tóm tắt có hiển thị thêm đá không.
  - **Thao tác 3 (Khắc chữ)**: Tích chọn checkbox *"Khắc chữ & biểu tượng tiêu chuẩn (Phí 1 lần: +80.000 đ)"*.
  - **Thao tác 4 (Kéo Slider - Kiểm tra Jitter & Latency)**:
    + Kéo slider *"Độ dày nhẫn mm"* về giá trị `1.8 mm`.
    + Kéo slider *"Độ rộng bề mặt mm"* về giá trị `3.5 mm`.
    + **Đo lường độ rung lắc (Flickering/Jitter)**: Khi kéo thanh trượt, các con số và giao diện có cập nhật mượt mà dưới $100\text{ms}$ không, hay màn hình bị chớp giật layout ($\ge 3$ fliks)?
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Sau khi kiểm tra toàn bộ thông số trong hộp tóm tắt đã phản ánh đúng các tùy chọn, bấm nút **"Lưu & Thẩm định"**.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu kéo slider gây crash React hoặc nút "Lưu & Thẩm định" bị đơ không phản hồi.
  - **SOFT FAIL**: Nếu slider hơi khó kéo bằng chuột trên laptop hoặc bước nhảy số quá thô.

---

### Bước 4 (P4-S04): Đánh giá Kết quả Thẩm định Kỹ thuật Thời gian thực (`/design/:designId`)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Hệ thống chuyển hướng sang URL dạng: `/workshops/PKG-COUPLE-SILVER/design/DSG-[uuid]`.
  - Tiêu điểm mắt nhìn vào chính giữa màn hình (Center of Screen):
    - Tiêu đề: *"Kết quả thẩm định thiết kế"*, mã thiết kế `DSG-...`, nhãn badge `Đã thẩm định`.
    - Dòng ghi chú màu xanh: *"ℹ️ Giá và độ khó tính toán mang tính chất tham khảo (Advisory)..."*.
- **(b) Kiểm tra Chi tiết & Tính Trực quan (Affordance & Density Probing)**:
  - **Kiểm tra Bản vẽ mô phỏng 2D**:
    + Thẻ 1: *Mặt chính diện (Top View)* — Hiển thị hình tròn nhẫn size 16 kèm thông số đường kính $\varnothing \sim 17.8\text{ mm}$, chu vi $56\text{ mm}$.
    + Thẻ 2: *Mặt cạnh / Tiết diện (Side View)* — Hiển thị mặt cắt đai nhẫn với bề rộng $3.5\text{ mm}$ và độ dày $1.8\text{ mm}$.
  - **Kiểm tra Bảng Chỉ số Kỹ thuật**:
    + *Chi phí tham khảo*: $\approx 372.400\text{ ₫}$.
    + *Khối lượng ước tính*: $\approx 4.02\text{ g}$.
    + *Độ khó chế tác*: $2.0 / 10$ kèm nhãn chữ *"Dễ"* và giải thích thang đo rõ ràng ($1-3$: Dễ, $>3-6$: Trung bình, $>6-9$: Khó).
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Quan sát 3 nút hành động ở cuối trang:
    1. "Khám phá các workshop"
    2. "Chỉnh sửa thiết kế"
    3. **"Lưu trữ mẫu thiết kế"** (Nút hành động mục tiêu).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu trang kết quả thẩm định bị 500 hoặc các trường chỉ số hiển thị `NaN` / `undefined`.
  - **SOFT FAIL**: Nếu hình vẽ mô phỏng 2D bị lệch tâm nhẹ.

---

### Bước 5 (P4-S05): Lưu trữ Mẫu Thiết kế & Quản lý Sổ tay Cá nhân (`/my/designs`)

- **(a) Thao tác Lưu trữ & Bắt Hội thoại Xác nhận (Dialog Confirmation)**:
  - Bấm vào nút **"Lưu trữ mẫu thiết kế"**.
  - Một hộp thoại xác nhận gốc (Confirm dialog) xuất hiện: *"Bạn có chắc chắn muốn lưu trữ mẫu thiết kế này không?"*.
  - Bấm nút **OK / Chấp nhận (Dialog Accept)**.
  - Quan sát trạng thái trên màn hình: Nhãn badge chuyển từ `Đã thẩm định` sang **`Đã lưu trữ`**.
- **(b) Dò tìm Lối vào Sổ tay Thiết kế (Wayfinding to Saved Designs)**:
  - Người dùng muốn xem lại mẫu đã lưu: Bấm mở menu tài khoản cá nhân ở góc trên bên phải (Tùy chọn tài khoản).
  - Tìm và bấm vào dòng: **"💍 Mẫu nhẫn đã lưu"** (Điều hướng tới `/my/designs`).
- **(c) Kiểm chứng Dữ liệu & Tính Toàn vẹn (Integrity Verification)**:
  - Tại trang `/my/designs` (Lịch sử thiết kế nhẫn của bạn):
    - Tab *"💍 Mẫu nhẫn Workshop"* đang được chọn.
    - Thẻ thiết kế vừa tạo xuất hiện trên đầu danh sách với đúng mã `DSG-...`, trạng thái `Đã lưu trữ`.
    - Thông tin tóm tắt hiển thị chuẩn xác: *Gói: PKG-COUPLE-SILVER · Chất liệu: Bạc Ý 925 · Dáng: Tròn cổ điển · Size: 16 · Chi phí tham khảo: 372.400 đ*.
    - Thử kiểm tra có đầy đủ 2 nút: **"Xem chi tiết →"** và **"Chỉnh sửa"**.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu sau khi lưu mà danh sách `/my/designs` trống trơn (mất dữ liệu thiết kế).
  - **SOFT FAIL**: Nếu thẻ thiết kế không hiển thị ảnh thumbnail thu nhỏ.

---

## 5. Ma trận Các bước Thực thi Nghiệp vụ (Business Step Matrix)

| Bước | Actor | Thao tác chính | Dữ liệu đầu vào | Kết quả mong đợi | Mức dừng |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **P4-S01** | MEMBER | Dò tìm lối vào từ Header | Click link "Cấu hình nhẫn" trên Navbar | Mở trang `/workshops/PKG-COUPLE-SILVER/design`, thấy 2 thẻ bắt đầu | HARD STOP nếu 404 |
| **P4-S02** | MEMBER | Khám phá thư viện mẫu | Click "Chọn mẫu này" tại thẻ gợi ý | Mở `/design/models`, hiển thị lưới 12 mẫu nhẫn đa dạng | HARD STOP nếu trống |
| **P4-S03** | MEMBER | Tùy biến tham số xưởng | Size 16, Bạc 925, Đá CZ, Khắc chữ, Kéo Slider dày 1.8mm/rộng 3.5mm | Form phản hồi mượt, Jitter < 3, giá cộng dồn chuẩn xác | HARD STOP nếu crash |
| **P4-S04** | MEMBER | Đánh giá kết quả thẩm định | Bấm "Lưu & Thẩm định" | Mở `/design/:id`, hiển thị 2 bản vẽ 2D, Khối lượng 4.02g, Độ khó 2.0/10 | HARD STOP nếu NaN |
| **P4-S05** | MEMBER | Lưu sổ tay & Kiểm tra lịch sử | Bấm "Lưu trữ mẫu", Accept dialog, mở `/my/designs` | Thiết kế lưu thành công, hiển thị đầy đủ thông số tại Sổ tay cá nhân | HARD STOP nếu mất data |

---

### 5.1. Browser Action Steps
```bash
# Step 1: Dò tìm Lối vào Cấu hình từ Header & Trang chủ
goto "https://mland-operations-hub-git-develop-mland-team.vercel.app/"
wait 2000
click getByRole('link', { name: 'Cấu hình nhẫn' }).first()
wait 2000

# Step 2: Khám phá Thư viện Mẫu Gợi ý
click getByRole('button', { name: 'Chọn mẫu này' }).first()
wait 2000
click getByRole('button', { name: 'Chọn mẫu này' }).first()
wait 2000

# Step 3: Tinh chỉnh Tham số Nhẫn & Kéo Slider
click getByRole('button', { name: 'Size 16 (56 mm)' })
wait 500
click getByRole('button', { name: '+ Thêm đá' }).nth(1)
wait 500
check getByRole('checkbox', { name: /Khắc chữ & biểu tượng tiêu chuẩn/i })
wait 500
click getByRole('button', { name: 'Lưu & Thẩm định' })
wait 3000

# Step 4: Đánh giá Kết quả Thẩm định & Lưu trữ
click getByRole('button', { name: 'Lưu trữ mẫu thiết kế' })
dialog accept
wait 2000

# Step 5: Kiểm tra Lịch sử Sổ tay Cá nhân
goto "https://mland-operations-hub-git-develop-mland-team.vercel.app/my/designs"
wait 2000
snapshot -i
```

### 5.2. Expected Assertions
```bash
# Assertion Step 1: Trang Cấu hình nhẫn hiển thị thành công
get url
get text getByRole('heading', { level: 1 })
find "Cấu hình mẫu nhẫn Workshop"

# Assertion Step 2: Mở kho mẫu gợi ý
get url
find "Mẫu nhẫn Bạc Tròn Cổ Điển"

# Assertion Step 3 & 4: Mở trang kết quả thẩm định
get url
get text getByRole('heading', { level: 1 })
find "Chi phí tham khảo ước tính"
find "Độ khó chế tác"
find "Đã lưu trữ"

# Assertion Step 5: Mẫu thiết kế xuất hiện trong Sổ tay cá nhân
get url
find "DSG-"
find "PKG-COUPLE-SILVER"
find "Bạc Ý 925"
```

---

## 6. Tiêu chuẩn Đo lường Trải nghiệm Con người (Human UX Criteria Checklist)

- [ ] **Default Eye Focus**: Người dùng khi vào trang cấu hình có nhìn thấy ngay các lựa chọn then chốt ở trung tâm không? Bản vẽ mô phỏng 2D có đặt ở vị trí thuận mắt nhất không?
- [ ] **Response Latency**: Thời gian hệ thống tính toán lại khối lượng và đơn giá khi kéo slider có đạt $\le 100\text{ms}$ không? Thời gian nộp thẩm định có $\le 2\text{ giây}$ không?
- [ ] **Screen Flickering / Jitter**: Quá trình thay đổi chất liệu, kiểu đai hay kéo thanh trượt có làm giật chớp layout ($\ge 3$ fliks) không?
- [ ] **Typography & Spacing Density**: Các nút bấm chọn size (từ size 6 đến 29) có đủ khoảng cách bấm (touch target $\ge 40\text{px}$) không, hay bị dí sát nhau gây bấm nhầm?
- [ ] **Visual Color & Styling Overload**: Màu sắc mô phỏng bạc, đồng, thép có chân thực, sang trọng không? Độ tương phản nhãn chữ có đạt chuẩn dễ đọc WCAG AA không?

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-25 23:55:00
- Executed by: Ngô Tiến Đạt
- Browser tool: Chrome DevTools / browsermcp
- Failure step: None
- Actual result: Parametric ring configuration completed for PKG-COUPLE-SILVER. Material Bạc Ý 925, shape Tròn cổ điển, size 16, thickness 1.8mm, width 3.5mm evaluated successfully with estimated weight 4.02g and difficulty 2.0/10. Design saved, archived with modal confirmation, and visible in /my/designs. BUG-E2E-0016 and BUG-E2E-0017 verified resolved and CLOSED.
- Bug reference: BUG-E2E-0016 (CLOSED), BUG-E2E-0017 (CLOSED)
<!-- EXECUTION:END -->
