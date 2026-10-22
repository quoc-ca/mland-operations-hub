# Member Profile 003 — design handoff

[Figma Design](https://www.figma.com/design/kzde91ArBjpdTnSMHaI15a) · [Handoff](https://www.figma.com/design/kzde91ArBjpdTnSMHaI15a?node-id=11-2) · [Trang chủ minh họa](https://www.figma.com/design/kzde91ArBjpdTnSMHaI15a?node-id=6-242) · [Hồ sơ desktop](https://www.figma.com/design/kzde91ArBjpdTnSMHaI15a?node-id=7-335) · [Hồ sơ mobile](https://www.figma.com/design/kzde91ArBjpdTnSMHaI15a?node-id=7-389)

Danh mục URL đầy đủ: [figma-index.json](figma-index.json).

## Nguồn hiện hành

Người dùng yêu cầu đồng bộ prototype theo bản [spec](../../ppswbs-spec/features/001-account-management/spec.md), [plan](../../ppswbs-spec/features/001-account-management/plan.md), [data model](../../ppswbs-spec/features/001-account-management/data-model.md) và [OpenAPI](../../ppswbs-spec/features/001-account-management/contracts/member-profile.openapi.yaml) đã sửa: thành công về trang chủ, chỉ email chưa xác minh mới tiếp tục kích hoạt, policy không chặn 003.

Đợt này sửa web, Figma và tài liệu bàn giao; không triển khai backend hoặc tick T001–T008 nhờ prototype.

## Màn hình và requirement

| Tình huống | Nội dung | Nguồn |
| --- | --- | --- |
| signin, signin-unverified, signup, auth-loading, auth-error, auth-outage | Phân biệt đăng nhập đã/chưa xác minh, đăng ký và lỗi an toàn | FR-011, FR-016 |
| verify, verify-sent, verified | Xác minh còn dở, gửi lại theo yêu cầu, snapshot đã xác minh | FR-003, FR-011–012 |
| home | Chỉ minh họa đích / sau xác thực; không tự mở hồ sơ | FR-011, FR-015 |
| credential-stale | Đăng nhập lại để tạo phiên, không xác minh email lặp lại | FR-012 |
| profile-empty, profile, profile-pending-policy | GET chưa/đã có hồ sơ; Member chờ policy vẫn được xem/sửa | FR-003–004, FR-015 |
| profile-edit, profile-invalid, profile-saving, profile-success, profile-failure | Validation, lưu và giữ draft khi lỗi | FR-005–010 |
| expired, revoked, suspended, initialize, unavailable | Giữ các nhánh từ chối/khởi tạo/phục hồi | FR-003, FR-012–016 |

54 frame: 24 tình huống VI × desktop 1440/mobile 390, cộng sáu frame EN cho auth/home/profile. Ba page: Foundations, Screens, Handoff. Các frame policy cũ đã được thay ngay tại node cũ, nên URL vẫn tồn tại nhưng nội dung và mã tình huống đã đổi. Checkbox policy đã bỏ khỏi kit.

## Luồng và tương tác

- Đăng nhập đã xác minh → kiểm tra phiên/trạng thái → home.
- Đăng ký hoặc đăng nhập chưa xác minh → verify. Chỉ gửi lại theo yêu cầu. Khi reviewer mô phỏng xác minh thành công trong web, demo tự hoàn tất phiên → home.
- Credential không còn đủ điều kiện → signin → home; email vẫn đã xác minh.
- Từ home, người dùng chủ động mở hồ sơ. ACTIVE/PENDING_POLICY_ACCEPTANCE được dùng hồ sơ; SUSPENDED không tự mở khóa.
- Figma có liên kết điều hướng giữa các snapshot auth/home. Frame verified là minh họa trạng thái thành công, không thêm bước bắt buộc. Form và kết quả provider phải diễn tập trong web; snapshot Figma không duy trì một phiên/draft liên frame.

## Thiết kế và ranh giới production

Giữ hướng sáng, thanh lịch, form một cột, nhãn luôn hiện, lỗi cạnh field và focus rõ. Semantic token: paper #f8f7f4, surface #ffffff, ink #292d2b, muted #626963, accent #775f3e, champagne #eee5d6. Figma dùng Inter/Noto Serif; web dùng Arial/Georgia hệ thống để chạy offline, có khác biệt metrics chữ.

Email chỉ đọc, điện thoại tùy chọn/chưa xác minh. Không avatar, địa chỉ, đổi email/password, Google sign-in, booking hoặc thiết kế nhẫn. Màn home chỉ là điểm nối trong prototype, không mở rộng feature 003.

Production vẫn Spring Boot/Thymeleaf; profile form không cần JS sau Auth, có CSRF. Firebase SDK nằm trong ngoại lệ Auth hiện hành. Session 8 giờ, revocation, UID ownership, bootstrap, last-commit wins và atomic audit phải được triển khai/kiểm chứng ở backend. Không đưa reviewer/state giả vào production, không dùng policy-dependent entitlement chung để vô tình chặn 003.
