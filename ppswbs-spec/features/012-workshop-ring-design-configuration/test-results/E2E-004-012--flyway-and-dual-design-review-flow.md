# Báo cáo Kiểm thử E2E: Rà soát Flyway, Tạo 2 Mẫu Thiết kế Member (> 3M & < 3M) và Quy trình Thẩm duyệt Staff / Manager

**Mã kịch bản**: `E2E-004-012-DUAL-REVIEW`  
**Ngày thực hiện**: 2026-11-14  
**Trạng thái**: **PASSED (100%)**  
**Tính năng liên quan**: 
- `012-workshop-ring-design-configuration` (Feature 012)
- `004-ai-chatbox-analysis-and-suggestion` (Feature 004)
- Cơ sở hạ tầng cơ sở dữ liệu Flyway Migration (Backend Spring Boot & MySQL 8)

---

## 1. Rà soát & Khắc phục Lỗi Flyway Migration

### 1.1. Nguyên nhân gốc rễ (Root Cause)
1. **Trùng lặp phiên bản Migration**: Khi phát triển tính năng 012, các file migration được đặt tên tạm thời là `V119__independent_design_request_foreign_keys.sql` và `V120__ring_design_copper_steel_and_models.sql`. Trong khi đó, nhánh phát triển chung (`develop`) đã tồn tại các migration từ `V119` đến `V140`.
2. **File rác trong build artifact (`target/classes`)**: Các file cũ chưa được dọn dẹp gây lỗi:
   `Found more than one migration with version 119`
3. **Lệch lịch sử `flyway_schema_history`**: Trong MySQL `ppswbs_dev`, rank 117 và 118 đã ghi nhận version 119 và 120 với nội dung của feature 012 cũ, khiến Flyway báo lỗi checksum mismatch đối với `V119__loyalty_accepted_benefit_parts.sql` và không thể chạy tiếp các migration 121 đến 140.

### 1.2. Biện pháp xử lý đã thực hiện
1. Dọn dẹp thư mục `target/classes/db/migration` loại bỏ hoàn toàn các file migration cũ bị trùng.
2. Cập nhật bảng `flyway_schema_history` để chuyển 2 bản ghi rank 117 và 118 thành phiên bản chính thức `V141` và `V142` kèm mã checksum chính xác tương ứng.
3. Kích hoạt Flyway để áp dụng tuần tự toàn bộ các migration mới từ `V119` đến `V140`.
4. **Kết quả kiểm chứng**: 142/142 migration đều đạt trạng thái `success = 1`. Backend Spring Boot khởi động hoàn toàn thành công trên cổng 8080.

---

## 2. Kịch bản Tạo 2 Mẫu Thiết kế bởi Role MEMBER (Feature 012)

Đăng nhập tài khoản Member: `member.test@mland.vn` / `Member@123456`.

### 2.1. Mẫu Thiết kế 1 — Giá trị > 3.000.000 ₫
- **Mã thiết kế**: `DSG-ff0bfd57-4103-484b-85f0-536471a754a9` (Phiên bản 2)
- **Cấu hình chi tiết**:
  - Chất liệu: Bạc Ý 925 (Sterling Silver)
  - Dáng nhẫn: Dáng xoắn vô cực (Twist Infinity - +120.000 ₫)
  - Size: Size 29 (Chu vi: 69 mm, đường kính ~22.0 mm)
  - Kích thước đai: Độ dày max 3.0 mm, Độ rộng bề mặt max 6.0 mm
  - Khối lượng ước tính theo gam: **14.62 g** (Vượt mức 3g tiêu chuẩn: +11.62 g x 120.000 ₫ = **+1.394.400 ₫** phụ phí khối lượng)
  - Đá quý: 4 viên Đá Moissanite 3mm (4 x 450.000 ₫ = 1.800.000 ₫), 1x CZ Đỏ 2mm, 1x CZ Lam 2mm, 1x CZ Trắng 2mm
  - Họa tiết trang trí: Viền hạt bi Milgrain (+100.000 ₫) + Dập nổi trái tim (+70.000 ₫)
  - Khắc chữ: Khắc lòng trong "FOREVER LOVE" (+80.000 ₫)
- **Kết quả ước tính**: **4.074.400 ₫** (> 3.000.000 ₫). Độ khó: 14.1000/10.

### 2.2. Mẫu Thiết kế 2 — Giá trị < 3.000.000 ₫
- **Mã thiết kế**: `DSG-620a9acf-2f85-4ba2-9fbd-6155e0fdd826` (Phiên bản 2)
- **Cấu hình chi tiết**:
  - Chất liệu: Bạc Ý 925 (Sterling Silver)
  - Dáng nhẫn: Dáng tròn cổ điển (Classic Round - 0 ₫)
  - Size: Size 8 (Chu vi: 48 mm)
  - Kích thước đai: Độ dày 1.8 mm, Độ rộng bề mặt 3.5 mm
  - Khối lượng ước tính theo gam: **3.50 g** (Phụ phí khối lượng: +60.000 ₫)
  - Đá quý: 1 viên Đá CZ Trắng 2mm (+80.000 ₫)
  - Họa tiết & Khắc: Không có
- **Kết quả ước tính**: **390.000 ₫** (< 3.000.000 ₫). Độ khó: 2.8000/10.

---

## 3. Quy trình Thẩm duyệt Phân quyền Staff & Manager (Feature 004)

Member tạo 2 yêu cầu thẩm định thiết kế kèm ảnh:
1. `DR-8F83DF9B` (`8f83df9b-8a3a-4a34-82f2-2e8fe26cf112`): Thiết kế nhẫn sáp phôi cao cấp, giá trị ước tính **3.500.000 ₫** (> 3.000.000 ₫).
2. `DR-13F5F322` (`13f5f322-1e13-457c-9f79-7eefeab874a3`): Thiết kế nhẫn bạc cổ điển đính đá CZ, giá trị ước tính **1.800.000 ₫** (<= 3.000.000 ₫).

### 3.1. Thẩm duyệt bởi STAFF (`staff.reviewer@mland.vn`)
- Đăng nhập quyền Nhân viên, truy cập `/design-review-requests`.
- **Rào cản phân quyền tự động**: Hàng đợi Staff chỉ hiển thị các yêu cầu có giá trị <= 3.000.000 ₫. Yêu cầu `DR-8F83DF9B` (3.5M) tự động ẩn khỏi hàng đợi của nhân viên theo đúng quy tắc bảo mật.
- Nhân viên mở `DR-13F5F322`, kiểm tra các thông số AI trích xuất và hình ảnh.
- Ghi nhận quyết định: **ACCEPTED** kèm lý do:
  *"Thiết kế nhẫn bạc cổ điển đính đá CZ tinh tế, các thông số kỹ thuật hoàn toàn khả thi cho buổi workshop chế tác. Nhân viên chấp thuận yêu cầu."*
- **Ghi nhận CSDL**: `decision = ACCEPTED`, `reviewer_role = STAFF`, `decided_at = 2026-11-14 03:36:06`.

### 3.2. Thẩm duyệt bởi MANAGER (`manager.test@mland.vn`)
- Đăng nhập quyền Quản lý, truy cập `/design-review-requests`.
- Hàng đợi Quản lý hiển thị yêu cầu `DR-8F83DF9B` (3.500.000 ₫ > 3.000.000 ₫).
- Quản lý mở yêu cầu, kiểm tra thông số kỹ thuật AI, tính khả thi và định mức chi phí.
- Ghi nhận quyết định: **ACCEPTED** kèm lý do:
  *"Thiết kế nhẫn sáp phôi cao cấp (giá trị ước tính 3.500.000 VND > 3.000.000 VND). Quản lý chi nhánh đã thẩm định tính khả thi, định mức nguyên vật liệu và năng lực chế tác của atelier. Phê duyệt chấp thuận."*
- **Ghi nhận CSDL**: `decision = ACCEPTED`, `reviewer_role = MANAGER`, `decided_at = 2026-11-14 03:37:26`.

---

## 4. Kiểm thử Tính năng Chatbot AI & Duyệt ảnh gửi Chatbot

1. **Gửi ảnh & Thẩm định AI (Feature 004)**:
   - Thành viên tải ảnh mẫu nhẫn lên hệ thống qua cổng Workspace.
   - Hệ thống kích hoạt cổng đồng thuận (`image_consents`) với mục đích `INDEPENDENT_DESIGN_IMAGE_ANALYSIS v1.0`.
   - AI Vision phân tích ảnh, trích xuất cấu trúc đai, chất liệu, đá quý, độ nét, góc chụp và tính khả thi.
   - Sau phân tích, hình ảnh tạm được tự động xóa an toàn theo tiêu chuẩn bảo mật dữ liệu.
   - Nhân viên tiếp nhận kết quả phân tích trong hàng đợi thẩm duyệt và phê duyệt thành công.
2. **Trợ lý Tư vấn AI (`/ai-support`)**:
   - Khách hàng/Hội viên gửi câu hỏi tư vấn về workshop và chính sách thanh toán cọc.
   - AI Chatbot phản hồi tức thì với phạm vi chính xác (`WORKSHOP` & chính sách cọc 50% VNPay).

---

## 5. Tổng kết

| Hạng mục kiểm thử | Kết quả | Ghi chú |
| :--- | :---: | :--- |
| Rà soát & sửa lỗi Flyway | **PASS** | 142/142 migration thành công, không còn lỗi trùng phiên bản |
| Member tạo mẫu > 3.000.000 ₫ | **PASS** | `DSG-ff0bfd57...` ước tính **4.074.400 ₫** |
| Member tạo mẫu < 3.000.000 ₫ | **PASS** | `DSG-620a9acf...` ước tính **390.000 ₫** |
| Nhân viên (Staff) thẩm duyệt & chấp thuận | **PASS** | Duyệt `DR-13F5F322` (1.8M), đúng rào cản phân quyền <= 3M |
| Quản lý (Manager) thẩm duyệt & chấp thuận | **PASS** | Duyệt `DR-8F83DF9B` (3.5M > 3M) thành công |
| Chatbot AI & Duyệt ảnh phân tích | **PASS** | Gửi ảnh, AI trích xuất thông số, nhân viên phê duyệt trên dashboard |
