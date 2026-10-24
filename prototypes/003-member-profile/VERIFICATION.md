# Prototype 003 — verification theo spec đã sửa

Đây là bằng chứng cho prototype web/Figma, không phải nghiệm thu triển khai production.

## Web

Môi trường: Python server loopback 127.0.0.1:8000, Node 22.14.0, Playwright/Chromium headless trong cache máy; runtime không thêm dependency.

```powershell
# Kiểm tra model/copy
node prototypes/003-member-profile/review.test.mjs
# Kiểm tra browser khi server đã chạy
$env:PLAYWRIGHT_MODULE = 'C:\path\to\node_modules\playwright\index.mjs'
node prototypes/003-member-profile/review.test.mjs
```

Lần chạy thực tế dùng package trong npm cache `_npx/420ff84f11983ee5/node_modules/playwright/index.mjs`, cần quyền khởi chạy browser ngoài sandbox.

Đã qua:
- 240 tổ hợp: 24 tình huống × VI/EN × 320/390/768/1280/1440 px; không tràn ngang hoặc thiếu label.
- AC-01/05: đăng ký gửi email đầu tiên mô phỏng → xác minh → home → mở hồ sơ rỗng; xác minh thành công không ép thêm bước policy.
- AC-02: đăng nhập đã xác minh → home, chưa mở hồ sơ cho tới khi có hành động riêng.
- AC-03: đăng nhập chưa xác minh → verify; vào trang/recheck không gửi lại, chỉ nút resend tăng bộ đếm.
- AC-04: Member PENDING_POLICY_ACCEPTANCE xem/sửa/lưu; logout/signin vẫn về home, giữ trạng thái và dữ liệu trong bộ nhớ. Không có policy lookup/gate trong demo; chưa mô phỏng riêng dịch vụ/version policy vì 003 không đọc nguồn đó.
- AC-07: credential cũ → fresh signin → home, không xác minh email lặp lại.
- AC-06: các trạng thái từ chối không render hồ sơ; đăng nhập lại không mở khóa SUSPENDED; khởi tạo Member thành công về home.
- Unicode/code point/control characters, định dạng phone/rỗng, lỗi focus, lưu thất bại giữ draft, retry, escape nội dung HTML, đổi ngôn ngữ giữ draft, live region và reset.
- Không storage, cookie, request ngoài origin hoặc lỗi JavaScript. Bộ kiểm tra đã sửa để đọc context reviewer đóng bằng textContent thay vì innerText.

Review ảnh mới: home mobile/desktop, stale credential mobile và auth desktop; bố cục profile/validation giữ hướng thiết kế hiện có. Ảnh browser tại `local-notes/prototype-003/` là evidence ignored, không phải asset bàn giao.

## Figma

[Handoff](https://www.figma.com/design/kzde91ArBjpdTnSMHaI15a?node-id=11-2) và [index](figma-index.json) đã đồng bộ:
- 54 frame, 1.124 text layer, 214 component instance, không IMAGE fill.
- 24 liên kết điều hướng snapshot; Handoff liên kết đủ frame.
- Các frame policy đã thay bằng home/signin-unverified/profile-pending-policy/credential-stale. Bỏ component checkbox policy; giữ các token/component còn cần.
- Audit không còn nội dung policy gate cũ, không có node vượt bounds cha hay sai mã frame. Đã review ảnh home, credential-stale, auth sau sửa.
- Có ba page Foundations/Screens/Handoff. UI là layer chỉnh sửa được; Figma không thực thi credential hay lưu draft liên frame.

## Giới hạn

Backend, schema/migration, cấu hình production và ô task production không đổi. Chưa kiểm thử screen reader thật, Safari/Firefox hoặc usability với người dùng cuối; kiểm tra accessibility ở đây gồm label/live region/focus và bàn phím trong Chromium. Không suy ra an toàn auth, CSRF, audit/concurrency hay khả năng hoạt động không-JS của production từ demo.
