# 008 · Retail Order and Fulfillment — UX prototype

Prototype tĩnh để review FE-008. Đây không phải frontend production và không gọi API, VNPay hoặc GHTK thật.

## Chạy local

Từ thư mục gốc repository:

```powershell
python -m http.server 8000 --bind 127.0.0.1 --directory prototypes
```

Mở <http://127.0.0.1:8000/008-retail-order-and-fulfillment/>.

## Các màn hình

- Catalogue có chọn chi nhánh trước khi thêm sản phẩm.
- Cart nhóm sản phẩm theo chi nhánh đã ghi nhận lúc thêm.
- Grouped checkout: mỗi chi nhánh chọn pickup/GHTK riêng và hiển thị phí giao riêng, nhưng chỉ có một tổng thanh toán VNPay.
- Kết quả thanh toán mô phỏng và trạng thái chờ tạo vận đơn an toàn.
- Lịch sử đơn, chi tiết theo dõi GHTK và ngày giao dự kiến.
- Workspace Staff xử lý đơn trong đúng chi nhánh được phân công.

Dùng **Công cụ review** ở góc phải dưới để chuyển nhanh giữa các màn hình.

## Giới hạn

- Dữ liệu chỉ ở bộ nhớ tab; refresh trả về fixture ban đầu.
- Không có authentication, authorization, database, giữ tồn kho, payment verification, webhook hoặc request ra ngoài.
- Luồng GHTK tự động phản ánh FE-008 draft. Report 3 còn mô tả GHTK manual-only; phải đồng bộ trước implementation production.
- Production frontend cần React/Vite scaffold được phê duyệt và API backend theo OpenAPI FE-008. GHTK chỉ được gọi server-to-server.
