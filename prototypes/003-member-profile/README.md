# 003 · Member Profile — prototype

Prototype tĩnh để duyệt UX theo [spec 003 đã sửa](../../ppswbs-spec/features/003-member-profile/spec.md). Không có authentication, email, API, cookie hay database thật.

## Chạy

Từ repo root:

```powershell
python -m http.server 8000 --bind 127.0.0.1 --directory prototypes
```

Mở <http://127.0.0.1:8000/003-member-profile/>. Dùng HTTP server vì JavaScript là ES modules, không mở bằng `file://`.

Mặc định hiển thị hồ sơ mẫu. **Công cụ review** chọn 24 tình huống; **Đặt lại demo** trả về fixture tiếng Việt. Nút VI/EN giữ draft đang sửa. Công cụ reviewer hiển thị trạng thái Member riêng khỏi giao diện sản phẩm.

## Diễn tập luồng

- [Đăng nhập đã xác minh](http://127.0.0.1:8000/003-member-profile/?scene=signin): dùng mật khẩu giả → điểm đến trang chủ → chủ động chọn mở hồ sơ.
- [Đăng ký](http://127.0.0.1:8000/003-member-profile/?scene=signup): tạo tài khoản giả → thông báo email đầu tiên → bước xác minh. Khi đang ở bước này, reviewer chọn **Email · đã xác minh** để mô phỏng kết quả Firebase: demo tự hoàn tất phiên và về trang chủ, không có policy gate.
- [Đăng nhập chưa xác minh](http://127.0.0.1:8000/003-member-profile/?scene=signin-unverified): đăng nhập → xác minh; vào trang hoặc kiểm tra lại không tự gửi email. Chỉ nút gửi lại tăng bộ đếm gửi mô phỏng.
- [Credential đã cũ](http://127.0.0.1:8000/003-member-profile/?scene=credential-stale): email vẫn đã xác minh; đăng nhập lại → trang chủ, không xác minh email lặp lại.
- [Member chờ policy](http://127.0.0.1:8000/003-member-profile/?scene=profile-pending-policy): xem/sửa/lưu hồ sơ bình thường. Đăng xuất rồi đăng nhập lại vẫn giữ trạng thái PENDING_POLICY_ACCEPTANCE và dữ liệu đã lưu trong bộ nhớ; không tự chuyển ACTIVE.
- [Hồ sơ EN](http://127.0.0.1:8000/003-member-profile/?scene=profile&lang=en).

Màn **Trang chủ · /** chỉ minh họa đích chuyển hướng; không phải thiết kế trang chủ đầy đủ. Nút mở hồ sơ là hành động riêng. Frame/tình huống `verified` mở độc lập là ảnh chụp trạng thái thành công, không phải bước bắt buộc sau đăng nhập. Đăng nhập đã xác minh không đi qua nó.

Kết quả xác minh còn pending sẽ giữ ở bước xác minh. Tình huống lỗi lưu thất bại một lần rồi cho phép thử lại. Đăng nhập lại từ màn bị khóa không tự mở khóa. Reviewer đổi tình huống độc lập sẽ nạp fixture mới, trừ kết quả xác minh khi đang hoàn thành đăng ký.

## Giới hạn

- Chỉ dùng email/mật khẩu giả. Danh tính luôn là fixture `linh@example.com`, không lấy email nhập để chọn tài khoản. Password chỉ nằm trong form và được xóa khi submit/chuyển màn hình.
- Dữ liệu chỉ ở bộ nhớ tab; refresh/reset/đổi tình huống độc lập sẽ thay fixture. Không storage, analytics hoặc request gửi dữ liệu.
- Không có policy page/gate, checkbox chấp thuận hoặc thiết kế điểm nhắc policy ở feature khác.
- Form hồ sơ production phải hoạt động bằng Thymeleaf không cần JS sau Auth, có CSRF và kiểm tra backend. Demo cần JS để mô phỏng; không chứng minh session, quyền, transaction/audit hay concurrency thật.
- Auth thành công được biểu diễn bằng màn đích `home` trong demo, không điều hướng server thật sang root của HTTP server.

## Bàn giao

`app.js`: render/luồng; `model.js`: fixture/validation/trạng thái; `copy.js`: VI/EN; `reviewer.js`: công cụ reviewer; `styles.css`: token/responsive.

[Figma và ánh xạ màn hình](FIGMA-HANDOFF.md) · [Bằng chứng kiểm tra](VERIFICATION.md).
