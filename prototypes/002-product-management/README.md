# Prototype 002 — Product Management (FE-02)

Bản mẫu tương tác cho feature 002, theo phong cách **Style 3: Operations Hub — High-Density Console**
(`prototypes/style-3-dense-console/`). HTML/CSS/JavaScript thuần, không thư viện; dữ liệu lưu trong
`localStorage` của trình duyệt.

> Đây là prototype để thử luồng màn hình, **không gọi backend** và không chứng minh backend hoạt động.
> Không có đăng nhập thật (vai trò đổi ở thanh trên), không có giữ hàng FE-08 (số đang giữ luôn là 0),
> không có Cloudinary (ảnh lưu dạng data URL), không đọc file `.xlsx` (dán dữ liệu copy từ Excel).

## Cách mở

Script cần chạy qua HTTP (mở file trực tiếp thì trang trắng). Từ thư mục gốc repo, dùng `jwebserver` có sẵn trong JDK 21:

```bash
jwebserver -d "D:\mland-operations-hub\mland-operations-hub\prototypes" -p 8765 -b 127.0.0.1
```

Rồi mở `http://127.0.0.1:8765/002-product-management/index.html`. Nút **Khôi phục dữ liệu mẫu** ở sidebar đưa dữ liệu về ban đầu.

## Màn hình

| Nhóm | Màn | Vai trò |
| --- | --- | --- |
| Catalogue công khai | Danh sách (từ khóa, danh mục, khoảng giá, "chỉ size có thể mua", sắp xếp, 20/trang), chi tiết (ảnh, chọn size, giá theo size, nút Chọn mua chuyển sang FE-08) | Mọi vai trò, kể cả Khách |
| Quản trị | Danh sách sản phẩm (tìm theo tên/SP000123/SKU), tạo sản phẩm, chi tiết với các tab Tổng quan / Nội dung / Ảnh / Size, giá & lượng bán / Hồ sơ duyệt / Lịch sử | Staff, Manager |
| Quản trị | Chờ duyệt (hồ sơ sản phẩm mới, đề xuất giá) | Xem: Staff; quyết định: Manager |
| Quản trị | Danh mục (tạo, sửa, xem tác động, ngừng/kích hoạt lại), Import Excel (kiểm tra, xem trước, xác nhận, báo cáo lỗi) | Staff, Manager |

Khách bị chặn 401, Thành viên và Admin bị chặn 403 ở mọi màn quản trị.

## Kịch bản gợi ý

1. **Sản phẩm mới đến lúc bán**: Staff tạo sản phẩm → thêm ảnh → Hồ sơ duyệt → Gửi duyệt; đổi sang Manager → Chờ duyệt → Quyết định (có thể chỉnh giá cuối) → tab Size: Áp dụng mọi giá đã duyệt → Công khai → xem ở catalogue công khai.
2. **Sửa trước lần công khai đầu**: với "Nhẫn Ruby Halo" (đang chờ duyệt) sửa tên → hồ sơ về nháp, revision mới.
3. **Đổi giá sau khi công khai**: "Nhẫn cưới Vàng 18K Trơn" → tab Size → Đề xuất → Gửi duyệt → Manager quyết định → Áp dụng. Giá đang bán giữ nguyên trong lúc chờ; tạo đề xuất thứ hai cho cùng size bị chặn.
4. **Điều kiện công khai**: thử Công khai khi thiếu ảnh/giá để thấy danh sách thiếu đầy đủ; "Giả lập lỗi" ở tab Ảnh làm sản phẩm biến khỏi catalogue rồi "Khôi phục" để hiện lại.
5. **Ngừng bán / hết hàng**: ngừng bán một size → vẫn hiện nhãn ở trang công khai nhưng không chọn mua được; lọc "chỉ size có thể mua" kết hợp khoảng giá phải thỏa trên cùng một size.
6. **Danh mục**: tạo tên trùng khác hoa/thường (kể cả với danh mục đã ngừng) bị từ chối; ngừng danh mục để thấy sản phẩm bị loại khỏi catalogue nhưng cờ công khai giữ nguyên.
7. **Import**: Điền dữ liệu mẫu → Kiểm tra → Xác nhận; "Điền mẫu có lỗi" để xem bảng lỗi theo dòng/cột và tải báo cáo lỗi; ngừng một danh mục giữa hai bước để thấy `IMPORT_PREVIEW_STALE`; "Giả lập hết 60 phút" để thấy `IMPORT_BATCH_EXPIRED`.
8. **Xóa**: xóa mềm có hộp thoại xác nhận riêng; sau khi xóa mọi thao tác bị chặn với `PRODUCT_DELETED`.

## Tệp

- `domain.js`: mô phỏng luật nghiệp vụ và mã lỗi theo `ppswbs-spec/features/002-product-management/contracts/interfaces.md`.
- `app.js`: giao diện, VI/EN, đổi vai trò.
- `styles.css`: token và component lấy từ Style 3.
