# Tổng quan dự án

> **Snapshot Git-only — kiểm tra ngày 09/10/2026.** Đây là bản tóm tắt để điều hướng; Report 2 và Report 3 là nguồn scope và requirement chi tiết. Các bảng trong Report 1 còn một số nội dung lệch baseline hiện tại, được ghi rõ bên dưới.

| Thông tin dự án | Giá trị |
| --- | --- |
| Nhóm | SEP490_G22 |
| Tên tiếng Anh | Personalized Product Sales and Workshop Booking System |
| Trạng thái | Active project, under validation; chưa có baseline tiến độ được phê duyệt. |

## Nhìn nhanh

| Nội dung | Trạng thái hiện tại |
| --- | --- |
| Baseline sản phẩm V1 | Report 2 và Report 3 mô tả nền tảng vận hành cho ring atelier: workshop, Member retail, custom manufacturing, loyalty/voucher, quản trị và các tích hợp đã nêu. |
| Payment | VNPay là provider duy nhất của V1. Chỉ signed IPN hoặc signed QueryDR recovery hợp lệ khi hold còn hiệu lực mới xác nhận thanh toán; Return URL chỉ hiển thị. |
| Booking và hold | Link thanh toán hết hạn sau 10 phút; slot workshop, retail cart hoặc custom-order queue slot được giữ tối đa 15 phút. |
| Tích hợp chính | VNPay, Klook, Gemini, Google Maps/Places, SMTP, Google/Facebook SSO và cloud storage; GHTK chỉ là handoff thủ công do Staff ghi nhận. |
| Quản lý dự án | Lịch, effort, ngân sách, năng lực nhóm, mục tiêu đo lường và RACI chưa được điền theo baseline được duyệt. Không có % hoàn thành được xác nhận. |
| Mã nguồn sản phẩm | Repository hiện là nguồn tài liệu và tooling; chưa ghi nhận application runtime trong snapshot này. |

## Scope V1 theo Report 2 và Report 3

- **Workshop:** Guest và Member xem package/lịch và đặt workshop; Member có thể dùng thiết kế được hệ thống hỗ trợ hoặc gửi ảnh booking-design để phân tích. Mland là nguồn sự thật về capacity. Klook là đối tác đăng ký workshop; Klook phải lấy availability/hold từ Mland trước khi xác nhận. Hủy từ Klook không tự giải phóng capacity trong Mland.
- **Booking-design và AI:** Gemini hỗ trợ chat cơ bản, phân tích ảnh booking-design, và gợi ý giá cho package/product. Staff review request có giá trị ước tính đến 3.000.000 VND; Manager review request cao hơn. AI không quyết định custom order.
- **Custom manufacturing:** Chỉ Member tạo custom order, dùng ảnh tham khảo hoặc cấu hình thủ công được hỗ trợ, deadline tùy chọn và phương thức nhận hàng. Hệ thống tự chấp nhận khi deadline hợp lệ và queue còn capacity. Deposit bằng 50% giá wax package; khoản cuối là 50% còn lại cộng surcharge thực tế do tăng trọng lượng vật liệu hoặc phụ kiện mua ngoài.
- **Retail và loyalty:** Member có thể mua retail bằng cart, loyalty points và voucher. Cart được revalidate toàn bộ; checkout không tạo order một phần. Thanh toán đủ được xác minh trước khi trừ số lượng đã hold. Staff duy trì available-to-sell quantity thủ công; đây không phải inventory ledger.
- **Fulfilment:** Retail/custom order đã thanh toán nhận tại shop hoặc bàn giao thủ công cho GHTK. Staff ghi nhận pickup/handoff. V1 không gọi API GHTK, báo giá ship hay tracking.
- **Store information/reviews:** Google Maps Embed và Places chỉ đọc, hiển thị vị trí và rating/review công khai được phép kèm attribution/link bắt buộc. Nội dung Places không được lưu trong Mland; không thu review nội bộ hoặc gửi review lên Google.
- **Quản trị:** Staff quản lý catalogue/media/giá/publication và số lượng available-to-sell; Manager quản lý lịch workshop, capacity, vật liệu, ngày nghỉ, promotions/vouchers, dashboard và quyết định giá; Admin quản lý tài khoản, RBAC, tham số vận hành và credentials tích hợp đã duyệt.

## Payment, dữ liệu và giới hạn vận hành

- VNPay là provider duy nhất. Xác minh chữ ký, transaction reference, amount và success status; xử lý duplicate event theo cách idempotent. QueryDR là kiểm tra khôi phục trạng thái thanh toán, không phải reconciliation.
- Payment link hết hạn sau 10 phút; hold liên quan hết sau 15 phút. Nếu hết hold mà chưa có bằng chứng thanh toán hợp lệ, hệ thống giải phóng tài nguyên. Bằng chứng đến muộn cần Staff xử lý thủ công và không tự kích hoạt fulfilment.
- Không lưu card data. VNPay transaction reference và business audit evidence giữ 5 năm; cần Finance/Legal xác nhận thời hạn này trước go-live.
- Gemini text prompt/response giữ tối đa 30 ngày; không lưu ảnh đầu vào AI sau xử lý. Ảnh custom manufacturing giữ đến pickup/GHTK handoff; request bị từ chối/rút và ảnh phân loại độc lập bị xóa sau xử lý. Dữ liệu người nhận/địa chỉ GHTK xóa hoặc ẩn danh không thể đảo ngược 30 ngày sau handoff.
- Terms và Privacy Policy chung phải bao quát xử lý ảnh, retention, Google attribution và dữ liệu giao hàng; Report 2 ghi rõ V1 không thêm checkbox consent riêng cho từng feature.
- V1 loại trừ warehouse management, inventory ledger/stock movement, Guest retail checkout, address book, shipping quote/tracking, delivery failure, return, cancellation/refund trong hệ thống, accounting và payment-reconciliation workflow.

## Giới hạn và điểm cần đối chiếu giữa report

- **Report 1 chưa đồng bộ hoàn toàn với Report 2–3.** Bảng Major Features của Report 1 còn mô tả AI valuation/automatic component extraction/chatbot, inventory tracking, delivery-provider workflow và Manager Dashboard rộng. Bảng Limitations lại nói loyalty deferred và VNPay/provider contract chưa chốt. Các điểm này mâu thuẫn với baseline chi tiết hiện tại của Report 2–3 (Gemini chỉ hỗ trợ các use case được liệt kê; loyalty/voucher thuộc V1; available-to-sell quantity được Staff duy trì thủ công; VNPay đã chốt; dashboard là executive revenue dashboard). Cần reconcile Report 1 với Report 2–3 tại nguồn canonical.
- Report 2 và Report 3 thống nhất các luồng V1 chính, nhưng chưa phải bằng chứng rằng hệ thống đã được triển khai hoặc kiểm thử.

## Trạng thái tài liệu

| Nguồn | Trạng thái theo nội dung hiện có |
| --- | --- |
| [Report 1](docs/report-1-project-introduction/front-matter.md) | Introduction/scope report; một số feature và limitation còn lệch với baseline chi tiết ở Report 2–3. |
| [Report 2](docs/report-2-project-management-plan/front-matter.md) | Scope, decision evidence và operating constraints V1 được ghi rõ; lịch, budget, capacity, objectives và RACI vẫn chưa được cung cấp. |
| [Report 3](docs/report-3-software-requirement-specification/front-matter.md) | Requirement baseline gồm actor, business flows, use cases, APIs, data retention và system rules. |
| [Report 4](docs/report-4-software-design-specification/front-matter.md) | Architecture/implementation design chưa được xác nhận trong snapshot này. |
| [Report 5](docs/report-5.0-test-documentation/front-matter.md) | Chưa dùng làm bằng chứng test result, environment hoặc sign-off cho sản phẩm. |

## Mã nguồn và vận hành tài liệu

- Repository có tooling Python cho tài liệu: generate DOCX, Git history/GitHub Issue snapshot, Google Workspace synchronization và unit tests. Đây không phải mã nguồn runtime của sản phẩm.
- Git-first source tài liệu/tracker là bề mặt chỉnh sửa; Google Docs/Sheets dùng để publish/review. Không đưa credentials, Drive ID hay dữ liệu khách vào snapshot.

## Baseline quản lý dự án còn thiếu

| Hạng mục | Trạng thái |
| --- | --- |
| Work packages, effort và due dates | Chưa điền trong Report 2. |
| Capacity planning | Chưa điền trong Report 2. |
| Planned/actual project objectives | Chưa điền trong Report 2. |
| Risk register và mitigation owners | Report 2 còn bảng mẫu trống; chưa có risk baseline được xác nhận ở đó. |
| Responsibility assignment/RACI | Chưa có baseline được duyệt. |
| Go-live prerequisites | Provider contracts/credentials, Finance/Legal xác nhận retention, Terms/Privacy Policy và các cấu hình vận hành phải sẵn sàng trước go-live theo Report 2. |

## Quy ước cập nhật snapshot

Snapshot chỉ đúng tại ngày kiểm tra ở đầu file. Cập nhật từ nguồn canonical khi có quyết định được duyệt hoặc thay đổi trong reports; giữ rõ giả định, constraint và mục chưa có baseline. Không dùng snapshot này để khẳng định tiến độ, triển khai hoặc kết quả test chưa có bằng chứng.
