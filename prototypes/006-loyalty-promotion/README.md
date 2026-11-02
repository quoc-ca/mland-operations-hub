# Prototype 006 — Loyalty & Promotion (FE-06)

Bản mẫu tương tác cho feature 006, cùng khung và phong cách **Style 3: Operations Hub — High-Density Console**
với [prototype 002](../002-product-management/README.md) (dùng lại `../002-product-management/styles.css`).
HTML/CSS/JavaScript thuần, không thư viện; dữ liệu lưu trong `localStorage` của trình duyệt.

> Đây là prototype để thử luồng màn hình, **không gọi backend** và không chứng minh backend hoạt động.
> Không có đăng nhập thật (vai trò đổi ở thanh trên). Checkout sản phẩm và đặt workshop chỉ là màn **đóng vai FE-08 / FE-03**
> để thử phần ưu đãi/điểm của FE-06; kết quả "đã thanh toán", "xác minh cọc", "hoàn thành", "hủy hợp lệ" là các nút giả lập
> thay cho bên sở hữu giao dịch — ở hệ thống thật chúng đến từ FE-08/FE-03 sau khi xác minh, không từ trình duyệt.
> Danh sách sản phẩm và gói workshop là dữ liệu mẫu đứng thay FE-02/FE-03.

## Cách mở

Script cần chạy qua HTTP. Từ thư mục gốc repo, dùng `jwebserver` có sẵn trong JDK 21:

```bash
jwebserver -d "D:\mland-operations-hub\mland-operations-hub\prototypes" -p 8765 -b 127.0.0.1
```

Rồi mở `http://127.0.0.1:8765/006-loyalty-promotion/index.html`. Nút **Khôi phục dữ liệu mẫu** ở sidebar đưa dữ liệu về ban đầu.

## Màn hình

| Nhóm | Màn | Vai trò |
| --- | --- | --- |
| Công khai | Ưu đãi (lọc Mua sản phẩm / Đặt workshop), chi tiết điều kiện, Lưu vào kho | Mọi vai trò; Khách bấm Lưu được hướng dẫn đăng nhập |
| Thành viên | Kho Voucher (tình trạng: đang hiệu lực, chưa bắt đầu, hết hạn, tạm dừng, "Hiện không có sản phẩm áp dụng") | Member |
| Thành viên | My Loyalty (số dư, đang giữ, khả dụng, quy đổi hiện hành, lịch sử 20 dòng/trang) | Member |
| Thành viên | Thanh toán sản phẩm (demo thay FE-08), Đặt workshop (demo thay FE-03), Giao dịch của tôi (kết quả nguồn giả lập) | Member |
| Quản lí | Chương trình ưu đãi (lọc, tạo voucher sản phẩm/workshop và giảm tự động, sửa, tạm dừng/mở lại, số liệu dùng, phiên bản, lịch sử) | Manager |
| Quản lí | Chính sách điểm (cập nhật có phiên bản, lịch sử các bản) | Manager |

Khách bị chặn 401, Staff và Admin bị chặn 403 ở mọi màn quản lí; chỉ Member dùng được màn của Member.

## Dữ liệu mẫu

- Member `member.mai`: 200 điểm, đã lưu `SP10`, `TET10` (hết hạn), `RUBY20` (sản phẩm chưa công khai).
- `SP10` giảm 10% tối đa 50.000đ, ngưỡng 500.000đ, cho kết hợp điểm; `LA50K` giảm 50.000đ cho size `SP000002-S10`, không kết hợp điểm;
  hai giảm tự động (5% nhẫn cưới tối đa 100.000đ, 30.000đ nhẫn bạc Lá); `WS100` giảm 100.000đ gói Tự làm nhẫn bạc, ngưỡng 500.000đ;
  `SET15` đang tạm dừng.
- Chính sách đầu: 10.000đ/điểm tích, 1 điểm = 100đ, tối thiểu 10 điểm/lần, trần 10% sau ưu đãi.

## Kịch bản gợi ý

1. **Giảm tự động lớn nhất (US4.3)**: mở Thanh toán sản phẩm với giỏ mặc định → chọn "Giảm tự động tốt nhất" → 5% của 2.500.000đ bị trần 100.000đ, thắng 30.000đ.
2. **Voucher + điểm (US4.1, US4.4)**: nhập ` sp10 ` (chữ thường, có khoảng trắng) → chọn "Dùng voucher" → "Dùng tối đa" điểm. Dòng ngoài phạm vi và giao hàng không tính vào ngưỡng; điểm dự kiến tính trên tiền hàng sau ưu đãi/điểm, không gồm giao hàng.
3. **Không kết hợp điểm (US4.6)**: dùng `LA50K` với size `SP000002-S10` → ô điểm biến mất kèm lý do.
4. **Xác nhận → chốt → tích điểm (US4.9, US6.6)**: Xác nhận đặt hàng → Giao dịch của tôi → "FE-08: đã thanh toán đủ" → My Loyalty thấy Giữ, Dùng, Cộng điểm. Bấm "Gửi lặp đã thanh toán" không cộng thêm; "hết hạn" sau khi đã trả bị từ chối.
5. **Workshop (US5.2)**: Đặt workshop, gói Tự làm nhẫn bạc, mã `ws100`, 200 điểm → phí sau ưu đãi 880.000đ, cọc 440.000đ, còn 440.000đ. Đổi sang 2 người để thấy trần voucher áp một lần cho cả booking.
6. **Hủy workshop hợp lệ (US7.4, US7.5)**: xác minh cọc → hủy hợp lệ → điểm trả đúng 200 (liên kết lần trừ gốc), lượt voucher trả lại; hủy lần nữa không đổi gì. Hủy khi mới giữ thì chỉ giải phóng.
7. **Hạn mức (US2.3)**: Manager mở `LA50K`, cho vài giao dịch giữ/chốt rồi hạ tổng lượt dưới đã dùng + đang giữ → `QUOTA_LIMIT_CONFLICT`.
8. **Mã duy nhất (US1.4)**: tạo voucher mã ` tet10 ` → `CODE_EXISTS` dù `TET10` đã hết hạn. Chọn size không thuộc sản phẩm đã chọn → bị từ chối.
9. **Tạm dừng/mở lại (US2.8)**: cùng mã, giữ lượt, không gia hạn; giao dịch đã chấp nhận giữ nguyên quyền lợi.
10. **Chính sách điểm (US8.4)**: đổi 10.000đ thành 20.000đ/điểm; giao dịch đã chấp nhận trước đó vẫn ghi "rev 1" và tích theo bản cũ.

## Giả lập sự cố

Nút **🧪 Giả lập sự cố** trên thanh trên mở một dải cài đặt. Mỗi sự cố chỉ xảy ra **một lần** ở thao tác phù hợp tiếp theo rồi tự về
"Bình thường". Thao tác bị từ chối luôn giữ nguyên dữ liệu đã nhập; lỗi hiện mã lỗi kèm `correlationId`.

| Cài đặt | Thử ở đâu | Kết quả mong đợi |
| --- | --- | --- |
| Mạng chậm | Bất kỳ đâu | Khung chờ khi mở màn; "Đang xử lý…" khi ghi; bấm đúp chỉ gửi một lần |
| 401 hết phiên | Lưu chương trình/chính sách, lưu voucher, xác nhận đơn… | Hộp thoại đăng nhập lại; dữ liệu đã nhập vẫn còn |
| 503 phụ thuộc | Tạo chương trình; mở Ưu đãi hoặc Kho Voucher | `DEPENDENCY_UNAVAILABLE`, nút **Thử lại**; không hiện dữ liệu cũ như hiện tại |
| Mất phản hồi | Bất kỳ thao tác ghi nào | Lệnh đã ghi nhưng màn hình không biết; **Kiểm tra kết quả** hoặc **Gửi lại cùng khóa** (không tạo bản trùng, không chuyển trạng thái hai lần) |
| 409 Manager khác sửa | Sửa/tạm dừng/mở lại chương trình, lưu chính sách | `STALE_VERSION` với **Tải bản mới** / **Giữ bản của tôi** |
| Đổi trước xác nhận | Xác nhận đơn hoặc booking | Có dùng điểm: điểm bị đơn khác giữ → `POINTS_LIMIT_CHANGED`, chọn lại. Không dùng điểm: chính sách có bản mới → `QUOTE_CHANGED`, hộp thoại tổng mới để đồng ý hoặc chọn lại |

## Giới hạn

- Không phải bằng chứng cho task nào trong `ppswbs-spec/features/006-loyalty-promotion/tasks.md`; các màn React thật (T030, T034, T040,
  T047, T055, T061, T070) vẫn chờ nền frontend (T077, T088/T089 của 002).
- Thẻ workshop hiển thị phạm vi như cấu hình (chưa có provider workshop công khai).
- Không mô phỏng đồng thời nhiều phiên thật, thời hạn giữ thật (15/30 phút chỉ hiển thị) hay đối soát kết quả muộn của FE-08/FE-03.

## Tệp

- `domain.js`: mô phỏng luật nghiệp vụ và mã lỗi theo `ppswbs-spec/features/006-loyalty-promotion/contracts/interfaces.md`.
- `app.js`: giao diện, VI/EN, đổi vai trò, giả lập sự cố.
- `styles.css`: thành phần riêng của 006, nạp sau stylesheet của 002.
