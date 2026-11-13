# Đề nghị gửi FE-001: lấy danh sách email Manager để gửi cảnh báo nội bộ

- **Ngày:** 2026-11-13
- **Từ:** Feature 003 Workshop Management, owner Hồ Hoàng Quốc
- **Gửi:** Phan Sơn, owner và Tech Lead FE-001 Account Management
- **Cần FE-001:** trả lời 3 câu ở mục 6, sau đó implement hoặc cho phép bên 003 implement phần ở mục 4.

---

## 1. Bối cảnh

Ở Feature 003, khách đặt một nhóm vé workshop và thanh toán qua VNPay. Chỗ được giữ trong 12 phút. Có trường hợp VNPay báo tiền đã về **sau** khi hết giữ chỗ, tức hệ thống đã thả chỗ ra.

Spec 003.1 (FR-040) quy định cách xử lý:

- **Tự động:** nếu cả 5 điều kiện còn đúng thì hệ thống tự xác nhận lại nhóm. Năm điều kiện là còn chỗ; buổi sớm nhất còn ít nhất 2 giờ; số tiền khớp; tiền về trong 60 phút; khách không có đặt chỗ trùng buổi.
- **Chờ Manager:** nếu có điều kiện không đạt, khoản tiền vào hàng chờ. Manager quyết định xác nhận chỗ hoặc hoàn 100% qua ngân hàng.

FR-040a bổ sung hai yêu cầu:

- Khách phải nhận kết quả trong **24 giờ** kể từ khi tiền về, hoặc trước giờ bắt đầu buổi sớm nhất nếu mốc đó đến trước.
- Khi khoản tiền vào hàng chờ, phải **cảnh báo Manager ngay**, cả trên màn hình quản lý lẫn qua email nội bộ.

Phần màn hình và hàng chờ đã xong ở 003. Còn thiếu email nội bộ, vì 003 không biết gửi cho ai.

## 2. Vì sao cần FE-001

- Danh sách Manager và email đăng nhập của họ chỉ có ở FE-001.
- Theo constitution và Spring Modulith, 003 không được đọc thẳng bảng, entity hay repository của FE-001. Mọi dữ liệu liên feature phải đi qua `AccountFacade`.
- `AccountFacade` hiện chỉ có các method tra cứu theo **một** tài khoản: `resolveAccount`, `getAccountStatus`, `getLocationScope`, `getTargetAccount`... Chưa có method nào trả về danh sách tài khoản theo role.

Vì vậy, muốn gửi cảnh báo cho đúng những người đang là Manager, FE-001 cần cung cấp thêm một method chỉ đọc.

## 3. Việc này có thật sự cần không? Các phương án đã cân nhắc

| Phương án | Có cần FE-001 | Nhận xét |
| --- | --- | --- |
| A. Ghi sẵn địa chỉ hộp thư chung trong file cấu hình của 003 | Không | Làm nhanh, nhưng danh sách không tự cập nhật khi thêm, khóa hoặc cho nghỉ một Manager; dễ quên cấu hình trên từng môi trường. |
| **B. Lấy danh sách Manager đang hoạt động từ FE-001 (đã chọn)** | Có, một method chỉ đọc | Luôn đúng theo tài khoản thật; không phải duy trì cấu hình người nhận. |
| C. Không gửi email, chỉ hiện trên màn hình | Không | Không đạt FR-040a. Manager không ngồi trước màn hình thì có thể lỡ mốc 24 giờ. |

Owner 003 đã chọn **B**. Nếu FE-001 không đồng ý, 003 sẽ quay về A. Tính năng vẫn chạy được, chỉ kém chính xác về người nhận.

## 4. Việc cần làm ở FE-001

### Method đề xuất

```java
/**
 * Provisional cross-feature contract for Workshop Booking 003.1 (FR-040a), created per FE-001 owner
 * confirmation; FE-001 owner to review upon branch merge.
 *
 * Login emails of every account with role MANAGER and status ACTIVE (ADMIN excluded),
 * de-duplicated and sorted. Empty list when there is none.
 * Callers use the list only inside their own transaction to address internal alerts;
 * it must not appear in events, audit records or logs.
 */
List<String> listActiveManagerEmails();
```

### Danh sách sửa

| # | File | Nội dung |
| --- | --- | --- |
| 1 | `ppswbs_backend/.../accountmanagement/facade/AccountFacade.java` | Thêm method ở trên. |
| 2 | `ppswbs_backend/.../accountmanagement/facade/AccountFacadeImpl.java` | Implement, chỉ đọc (`@Transactional(readOnly = true)`). |
| 3 | `ppswbs_backend/.../accountmanagement/dao/AccountRepository.java` | Một query lấy `loginEmail` theo `role = MANAGER` và `status = ACTIVE`. |
| 4 | Test của FE-001 | Trả đúng Manager ACTIVE; loại ADMIN, STAFF, MEMBER và Manager không ACTIVE; trả rỗng khi không có Manager. |
| 5 | `ppswbs-spec/features/001-account-management/contracts/interfaces.md` | Thêm method vào mục `accountmanagement::facade`, ghi consumer là 003.1 FR-040a. |

### Phạm vi ảnh hưởng

- Không đổi schema, không thêm migration.
- Không đổi API REST.
- Không đổi hành vi hiện có của FE-001.
- 56 file test đang dùng `AccountFacade` đều mock bằng Mockito, nên thêm method không làm chúng vỡ.

Khối lượng ước tính: 4 file code/test và 1 file tài liệu; phần logic đơn giản.

## 5. Dữ liệu cá nhân

Email Manager là PII của nhân viên. Bên 003 cam kết:

- Chỉ dùng danh sách trong transaction để tạo email cảnh báo.
- Không đưa danh sách vào event, audit hay log.
- Event `LatePaymentReviewQueued` chỉ chứa mã tham chiếu.
- Nội dung email cảnh báo không chứa dữ liệu của khách. Manager xem chi tiết trong màn hình quản lý.

## 6. Câu cần FE-001 trả lời

1. Đồng ý thêm `listActiveManagerEmails()` vào `AccountFacade` không? Có cần đổi tên, kiểu trả về hay mô tả không?
2. Ai làm phần ở mục 4?
   - **(a)** FE-001 tự làm.
   - **(b)** Bên 003 làm đúng theo mục 4, đánh dấu provisional, và FE-001 review khi merge. Đây là cách đã áp dụng cho `getTargetAccount`.
3. "Manager đang hoạt động" có cần loại thêm tài khoản đang chờ thu hồi phiên (`revocationPending = true`) hoặc chưa xác minh email (`emailVerifiedAt` trống) không?

## 7. Bên 003 làm gì trong lúc chờ

- Viết phần gửi cảnh báo trong `workshopbooking`, với nguồn người nhận đặt sau một interface nội bộ, và test bằng nguồn giả lập.
- Khi FE-001 trả lời, 003 chỉ cần thêm một adapter nhỏ gọi `AccountFacade.listActiveManagerEmails()`.
- FE-001 không cần làm gì thêm ngoài mục 4.

## 8. Phản hồi của FE-001 (2026-11-13)

Phan Sơn đồng ý và chọn **(a)**: bên 003 push code lên `quoc`, FE-001 pull về và tự implement mục 4. Sau khi `AccountFacade.listActiveManagerEmails()` có trong code, bên 003 thêm adapter cho `ManagerAlertRecipientSource` (hiện bean mặc định `PendingManagerAlertRecipientSource` trả danh sách rỗng). Câu 1 và 3 ở mục 6 chưa có trả lời riêng; mặc định theo đề xuất.
