# Tóm tắt Đề xuất Review Nhanh — Feature-012 & Feature-009

**Ngày tạo**: 2026-11-26
**Cập nhật**: 2026-11-26
**Người cập nhật**: Phan Sơn
**Người review dự kiến**: Ngô Tiến Đạt (Tech Lead)
**Trạng thái**: `PROPOSED FOR REVIEW` — Chờ Tech Lead duyệt (bản duyệt cũ 2026-11-09 và visual 2026-11-22 không tự động áp dụng cho phần thay đổi mới).

---

## 1. Những điểm đã chốt (Đạt & Sơn thống nhất)

- **Phạm vi FE-09 (Admin Configuration):**
  - Chỉ cấu hình **độ khó thiết kế nhẫn** (Workshop Ring Difficulty), **không** làm phần tham số checkout của UC-62 trong đợt này.
  - Admin **chỉ được publish** sau khi chạy **Preview** thành công trên các mẫu chuẩn không chứa dữ liệu cá nhân (server tạo proof có hiệu lực 15 phút); gọi API publish trực tiếp không qua preview sẽ bị chặn.
  - Nếu chính sách mới làm bất kỳ **mẫu nhẫn đang publish (active model)** nào bị thiếu điểm hoặc vượt ngưỡng `TOO_RISKY`, toàn bộ bản publish sẽ bị từ chối.
- **Chất liệu, khối lượng & công thức giá (Feature-012):**
  - **Định mức 3.00 g & giá vật liệu cơ bản ($\le 3.00\text{ g}$):** Bạc 925 = **900.000 đ**, Đồng = **750.000 đ**, Thép 316L = **750.000 đ** (bỏ mô tả cũ "tối đa 4g").
  - **Vượt 3.00 g:** Tính phụ phí tham khảo **120.000 đ/g** chung cho **tất cả** chất liệu; **không** từ chối thiết kế và **không** tự động thu thêm tiền tại workshop (vận hành tự xác nhận thực tế).
- **Quy tắc hiển thị & gợi ý workshop:**
  - Option mới do Manager thêm nhưng Admin chưa chấm điểm: vẫn hiển thị nhưng **disabled** cho khách hàng (`DIFFICULTY_SCORE_PENDING`).
  - Mẫu Đồng / Thép 316L: vẫn xuất hiện ở danh mục chung (general discovery), nhưng **không** gán vào gói workshop Bạc và **không** gợi ý gói workshop không tương thích (chuyển sang tư vấn / danh mục chung).
  - Bạc S925 nếu được gợi ý **Silver Clay** phải hiển thị rõ lưu ý workshop dùng bạc **S999** (bản thiết kế lưu vẫn giữ S925).
  - **Wax Casting** (> 9 điểm, $\le 11$ điểm): hiển thị lưu ý cần 2 buổi cách nhau ít nhất 2 ngày làm việc của xưởng.
  - Điểm độ khó có thể vượt quá 10 (VD: `14.1`): UI khách hàng **không** hiển thị `/10`.
- **Lưu trữ & lịch sử:**
  - Audit publish chính sách giữ tối thiểu **5 năm**; policy version cũ còn được thiết kế tham chiếu thì giữ lâu hơn.
  - Thiết kế đã lưu trước đó giữ nguyên điểm, mức độ khó, gợi ý và `rules_version` cũ (không tự động tính lại khi Admin đổi policy).

---

## 2. Những gì đã thêm / sửa trong tài liệu (để review nhanh)

### A. Feature-009 (`009-administrative-parameter-configuration`) — Bộ tài liệu mới hoàn toàn
- **`spec.md` & `plan.md` & `tasks.md`**: Quy định màn hình `/admin/ring-difficulty` chỉ dành cho Admin active; tách biệt với quyền Manager (Manager sửa danh mục, Admin sửa điểm độ khó).
- **`design/architecture.md`, `workflows.md`, `ui-flow.md`**: Luồng Load → Edit → Preview (kèm bảng tác động 100% active models) → Publish (atomic + idempotency + preview proof 15 phút) → Xem/khôi phục lịch sử.
- **`approval.md`**: Bản ghi chờ Tech Lead duyệt.

### B. Feature-012 (`012-workshop-ring-design-configuration`) — Bổ sung & đồng bộ
- **`spec.md`**:
  - Chốt luồng **Member-only** (Guest chỉ xem & preview local, không gọi API lưu/tính; bỏ luồng gắn booking cũ).
  - Thêm mục **4.1 Admin-owned difficulty policy** và cập nhật ngưỡng gợi ý (mục 6), acceptance scenarios (mục 11: 12–15).
  - Giữ mục **12. Technical Projection for Admin & Audit (C7)** (`GET /api/v1/workshop-designs/{designId}/technical-projection`) đã có trên `develop`.
- **`formula.md`**:
  - Cập nhật **mục 5.3** theo công thức giá mới trên `develop` (`V204`: 900k/750k cho $\le 3\text{g}$, `120.000 đ/g` vượt mức cho mọi chất liệu) + giữ rõ tính chất advisory.
  - Cập nhật **mục 7** đúng thứ tự `if/else` trên FE (`>11` TOO_RISKY → `>9` Wax → `>5` đá Silver Clay → Signature).
  - Thêm **mục 9**: Chính sách độ khó do Admin quản lý (chỉ sửa số liệu/ngưỡng, không sửa biểu thức công thức).
- **`data-model.md`**:
  - Bổ sung mô hình dữ liệu cho policy versioning, preview proof, audit, cập nhật rule giá vật liệu mục 3 (#26) và đề xuất migration append-only (mục 8).
- **`contracts/admin-ring-difficulty.openapi.yaml`** *(mới)* & **`contracts/workshop-ring-design.openapi.yaml`** *(cập nhật)*: Hợp đồng API cho Admin policy và Member configurator.
- **`plan.md`, `tasks.md`, `research.md`, `frontend-handoff.md`, `design/*`**: Đồng bộ theo các thay đổi trên.

---

## 3. Cần Tech Lead (Đạt) review & chốt

1. Duyệt **Admin OpenAPI** (`admin-ring-difficulty.openapi.yaml`) và **Member OpenAPI** (`workshop-ring-design.openapi.yaml`).
2. Duyệt thiết kế bảng & migration đề xuất trong **`data-model.md` §8** (kiểm tra khớp với Flyway hiện tại của DB).
3. Duyệt **UI baseline bằng văn bản** cho màn `/admin/ring-difficulty` trong **`009/design/ui-flow.md`** (tái sử dụng shell Admin, không tạo Figma frame mới).
4. Kiểm tra lại ranh giới tích hợp **Feature-013** (tư vấn) và ghi quyết định vào **`012/approval.md`** + **`009/approval.md`**.
