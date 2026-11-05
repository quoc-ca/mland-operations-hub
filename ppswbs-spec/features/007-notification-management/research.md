<a id="fe-07"></a>

# FE-07 — Notification Management

## Phạm vi và quy ước

1. Đây là thiết kế tài liệu, chưa phải chức năng đã triển khai. Chỉ có email; chưa có push, trung tâm thông báo, màn hình chỉnh HTML, API hoặc schema mới. Nội dung ngoài FE-07 giữ nguyên.
2. Firebase Auth gửi xác minh email và đặt lại mật khẩu theo FE-01. Backend gửi thư nghiệp vụ qua SMTP. Không đưa thư Firebase vào job SMTP hoặc giả lập trạng thái gửi của Firebase.
3. Mỗi mẫu có mã, phiên bản, trạng thái và điều kiện sử dụng. “Thiết kế cho luồng đã chốt” không đồng nghĩa đã chạy production. “Dự thảo — chưa kích hoạt” không được gửi cho đến khi chủ nghiệp vụ chốt sự kiện, người nhận, quyền và dữ liệu.
4. Các mã mẫu, tên biến và trạng thái dưới đây là định danh thiết kế, chưa phải API/enum/schema đã tồn tại. Không tự triển khai các quy trình refund, nhắc lịch hoặc quảng bá chỉ vì đã có bản thư.

## Danh mục mẫu thư

| Mã mẫu | Phiên bản | Trạng thái | Mục đích |
| --- | --- | --- | --- |
| AUTH_EMAIL_VERIFICATION | 1.0 | Tham chiếu Firebase Auth | Xác minh email |
| AUTH_PASSWORD_RESET | 1.0 | Tham chiếu Firebase Auth | Đặt lại mật khẩu |
| BOOKING_CONFIRMED | 1.0 | Thiết kế cho luồng đã chốt | Gộp booking, tiền cọc và vé QR |
| RETAIL_ORDER_CONFIRMED | 1.0 | Thiết kế cho luồng đã chốt | Đơn VNPay hoặc đơn 0 đồng |
| ACCOUNT_INVITATION | 1.0 | Dự thảo — chưa kích hoạt | Mời/kích hoạt tài khoản |
| ACCOUNT_LOCKED | 1.0 | Dự thảo — chưa kích hoạt | Khóa tài khoản |
| ACCOUNT_UNLOCKED | 1.0 | Dự thảo — chưa kích hoạt | Mở khóa tài khoản |
| ACCOUNT_ROLE_CHANGED | 1.0 | Dự thảo — chưa kích hoạt | Thay đổi vai trò |
| DESIGN_CLARIFICATION_REQUESTED | 1.0 | Dự thảo — chưa kích hoạt | Bổ sung thông tin thiết kế |
| DESIGN_REVIEW_RESULT | 1.0 | Dự thảo — chưa kích hoạt | Kết quả review thiết kế |
| APPROVAL_REQUESTED | 1.0 | Dự thảo — chưa kích hoạt | Yêu cầu Manager phê duyệt |
| APPROVAL_RESULT | 1.0 | Dự thảo — chưa kích hoạt | Kết quả phê duyệt nội bộ |
| CUSTOM_PAYMENT_REQUESTED | 1.0 | Dự thảo — chưa kích hoạt | Báo giá/yêu cầu thanh toán custom |
| CUSTOM_PAYMENT_CONFIRMED | 1.0 | Dự thảo — chưa kích hoạt | Xác nhận khoản thanh toán custom |
| REFUND_RESULT | 1.0 | Dự thảo — chưa kích hoạt | Kết quả refund |
| WORKSHOP_REMINDER | 1.0 | Dự thảo — chưa kích hoạt | Nhắc lịch workshop |
| PROMOTION_ANNOUNCEMENT | 1.0 | Dự thảo — chưa kích hoạt | Giới thiệu ưu đãi |

## Quy tắc nội dung dùng chung

1. Mỗi mẫu bên dưới kế thừa phiên bản và trạng thái trong danh mục. Phiên bản 1.0 của mẫu Firebase là phiên bản nội dung mong muốn của dự án, không phải tuyên bố kiểm soát phiên bản template Firebase.
2. `{{variable}}` là biến cần điền bằng dữ liệu hợp lệ; không gửi nguyên placeholder. Thiếu tên người nhận có thể dùng “Chào bạn,” thay cho “Chào {{recipientName}},”. Các dữ liệu bắt buộc khác thiếu thì chưa được gửi.
3. Tên người gửi, địa chỉ gửi, Reply-To và `{{supportContact}}` lấy từ cấu hình đã xác nhận; không tự đặt tên miền hoặc địa chỉ giả. Ngày giờ có múi giờ địa điểm, tiền định dạng VND theo dữ liệu nghiệp vụ.
4. Link chỉ tới chức năng đã có, dùng địa chỉ ứng dụng được cấu hình và kiểm tra quyền truy cập. Không tự tạo magic link mới hoặc coi biết mã đơn là đủ quyền xem. Các biến URL chưa quyết định endpoint hoặc màn hình mới.
5. HTML phải escape nội dung người dùng nhập và có bản chữ thuần tương ứng. Không đưa mật khẩu, thông tin thẻ, raw ID/refresh token hoặc ghi chú nội bộ nhạy cảm vào thư. Link hành động Firebase và vé QR chỉ dùng đúng mục đích/người nhận, không đưa vào log hay công cụ theo dõi công khai.
6. Nội dung dựa trên bản chụp kết quả nghiệp vụ đã chấp nhận; không lấy giá catalogue mới để tính lại đơn cũ. Mẫu mới tạo phiên bản mới; yêu cầu đã ghi giữ phiên bản của nó. Đổi phiên bản mẫu không tự tạo thêm thư cho sự kiện cũ.
7. Một sự kiện có thể tạo thư khách và nội bộ riêng; mỗi người nhận có yêu cầu riêng. Không CC/BCC danh sách nội bộ vào thư khách hoặc làm lộ địa chỉ giữa người nhận.
8. Không thêm email cập nhật giao nhận trong phạm vi gửi hiện tại. Cũng không mặc định gửi thư cho từng lần đăng nhập, thanh toán lỗi hoặc kết quả thanh toán chưa rõ.

## Mẫu tham chiếu Firebase Auth

### AUTH_EMAIL_VERIFICATION — Xác minh email

1. **Kích hoạt/người nhận:** đăng ký email/mật khẩu hoặc khách chủ động yêu cầu gửi/gửi lại xác minh; email Firebase đang yêu cầu xác minh theo FE-01.
2. **Điều kiện:** Firebase chấp nhận tài khoản/email và giới hạn gửi. Không tự gửi lại chỉ vì mở trang hoặc đăng nhập.
3. **Tiêu đề mong muốn:** `[Mland] Xác minh địa chỉ email của bạn`.
4. **Nút/link:** **Xác minh email** → `{{firebaseVerificationUrl}}` do Firebase tạo và kiểm tra.
5. **Không gửi:** yêu cầu không hợp lệ/bị giới hạn hoặc email đã xác minh không có thay đổi liên quan. Quyết định thực tế do Firebase kiểm soát.
6. **Giới hạn:** bản thư là nội dung mong muốn; chỉ tùy chỉnh trường và placeholder Firebase hỗ trợ, không khẳng định sửa được toàn bộ template.

> Chào bạn,
>
> Vui lòng xác minh địa chỉ email này để hoàn tất bước xác minh cho tài khoản Mland.
>
> **Xác minh email** — {{firebaseVerificationUrl}}
>
> Nếu bạn không thực hiện yêu cầu này, bạn có thể bỏ qua thư. Nếu link không còn hợp lệ, hãy quay lại Mland để yêu cầu email xác minh mới.
>
> Trân trọng, Mland

### AUTH_PASSWORD_RESET — Đặt lại mật khẩu

1. **Kích hoạt/người nhận:** khách chọn Quên mật khẩu; Firebase gửi tới email đủ điều kiện khôi phục theo FE-01.
2. **Điều kiện:** Firebase chấp nhận yêu cầu; website trả thông báo trung tính, không tiết lộ email có tài khoản hay không.
3. **Tiêu đề mong muốn:** `[Mland] Yêu cầu đặt lại mật khẩu`.
4. **Nút/link:** **Đặt lại mật khẩu** → `{{firebasePasswordResetUrl}}` do Firebase tạo và kiểm tra.
5. **Không gửi:** email không đủ điều kiện hoặc yêu cầu bị từ chối/giới hạn; không gửi SMTP thay thế để né Firebase.
6. **Giới hạn:** đối chiếu khả năng tùy chỉnh Firebase trước triển khai. Không tự cam kết thời hạn link hoặc tự sinh token.

> Chào bạn,
>
> Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Mland sử dụng địa chỉ email này.
>
> **Đặt lại mật khẩu** — {{firebasePasswordResetUrl}}
>
> Nếu bạn không yêu cầu, hãy bỏ qua thư này. Mật khẩu không thay đổi chỉ vì bạn nhận được email.
>
> Nếu link không còn hợp lệ, hãy mở mục Quên mật khẩu trên Mland để yêu cầu link mới.
>
> Trân trọng, Mland

## Mẫu cho luồng đã chốt

### BOOKING_CONFIRMED — Xác nhận booking, cọc và vé QR

1. **Kích hoạt/người nhận:** booking đã được xác nhận từ kết quả thanh toán hợp lệ và vé sẵn sàng; gửi một thư tới email liên hệ được module booking chấp nhận, không tự gửi tới mọi người tham gia.
2. **Điều kiện/dữ liệu:** có booking, gói, lịch/múi giờ, chi nhánh/địa chỉ, số người, nghĩa vụ tiền, cọc đã trả, số còn lại, vé hiện hành và link xem booking. Nếu booking đã xác nhận nhưng vé chưa sẵn sàng, ghi yêu cầu cùng sự kiện rồi chờ dữ liệu vé, chưa gửi thư thiếu vé hoặc tự tạo thư cọc riêng.
3. **Tiêu đề:** `[Mland] Xác nhận booking {{bookingCode}}`.
4. **Nút/link:** **Xem booking và vé** → `{{bookingUrl}}`, theo cơ chế truy cập booking hiện có. Nhúng QR bằng ảnh trong thư, không dùng dịch vụ QR công khai làm lộ vé; bản chữ thuần có link dự phòng, không in raw QR bí mật.
5. **Không gửi:** booking chưa xác nhận, chỉ có trình duyệt quay về VNPay, vé thu hồi, lịch đã bị thay thế hoặc dữ liệu không khớp. Thiếu dữ liệu không được đánh dấu gửi thành công.

> Chào {{recipientName}},
>
> Booking **{{bookingCode}}** của bạn đã được xác nhận.
>
> Workshop: {{packageName}}
> Thời gian: {{sessionTimeWithTimezone}}
> Địa điểm: {{branchName}} — {{branchAddress}}
> Số người tham gia: {{participantCount}}
>
> Tổng nghĩa vụ thanh toán của booking: {{bookingTotal}}
> Tiền cọc đã xác nhận: {{depositPaid}}
> Số tiền còn lại: {{remainingAmount}}
>
> Vui lòng sử dụng vé QR bên dưới khi check-in. Không chia sẻ vé với người không liên quan.
>
> [Ảnh QR của vé hiện hành được nhúng tại đây]
>
> **Xem booking và vé** — {{bookingUrl}}
>
> Nếu ảnh không hiển thị, bạn có thể mở link trên để xem vé. Số tiền còn lại do booking cung cấp; thư này không thay đổi điều kiện hoặc tự quyết định cách thu khoản còn lại.
>
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### RETAIL_ORDER_CONFIRMED — Xác nhận đơn bán lẻ

1. **Kích hoạt/người nhận:** module bán hàng đã chốt đơn từ VNPay hợp lệ hoặc tổng cuối cùng 0 đồng; gửi tới email thông báo đơn được nghiệp vụ chấp nhận, không tự suy email từ địa chỉ giao hàng.
2. **Điều kiện/dữ liệu:** đơn và tác động hàng/ưu đãi đã chốt; có bản chụp dòng hàng, tiền hàng, từng khoản giảm, phí giao, tổng tiền, cách nhận và link chi tiết. Hai biến thể bên dưới loại trừ nhau, cùng phiên bản 1.0.
3. **Tiêu đề:** `[Mland] Xác nhận đơn hàng {{orderCode}}`.
4. **Nút/link:** **Xem chi tiết đơn hàng** → `{{orderUrl}}`, Backend kiểm tra chủ đơn/quyền truy cập.
5. **Không gửi:** đơn mới tạo hoặc giữ hàng, chưa rõ thanh toán, hết hạn hay khoản tiền cần đối soát mà chưa chốt đơn; IPN lặp không tạo thêm thư.
6. **Quy tắc biến:** `{{productSummary}}` gồm tên, biến thể, số lượng, giá theo bản chụp; `{{discountBreakdown}}` tách voucher/điểm nếu có; `{{fulfilmentSummary}}` chỉ mô tả cách nhận đã chốt, không hứa ngày giao chưa xác nhận.

#### Biến thể đã thanh toán VNPay

> Chào {{recipientName}},
>
> Đơn hàng **{{orderCode}}** của bạn đã được xác nhận và đang chờ xử lý.
>
> Sản phẩm:
> {{productSummary}}
>
> Tiền hàng: {{subtotal}}
> Các khoản giảm: {{discountBreakdown}}
> Phí giao hàng: {{shippingFee}}
> Tổng thanh toán: {{payableTotal}}
>
> Đã xác nhận thanh toán {{paidAmount}} qua VNPay cho đơn hàng này.
> Phương thức nhận: {{fulfilmentSummary}}
>
> **Xem chi tiết đơn hàng** — {{orderUrl}}
>
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

#### Biến thể đơn 0 đồng

> Chào {{recipientName}},
>
> Đơn hàng **{{orderCode}}** của bạn đã được xác nhận và đang chờ xử lý.
>
> Sản phẩm:
> {{productSummary}}
>
> Tiền hàng: {{subtotal}}
> Các khoản giảm: {{discountBreakdown}}
> Phí giao hàng: {{shippingFee}}
> Tổng cần thanh toán: **0 đồng**.
>
> Đơn đã được xác nhận theo ưu đãi và điều kiện hợp lệ, không phát sinh giao dịch thanh toán qua VNPay.
> Phương thức nhận: {{fulfilmentSummary}}
>
> **Xem chi tiết đơn hàng** — {{orderUrl}}
>
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

## Mẫu dự thảo — Quản trị tài khoản

Các mẫu dưới đây kế thừa phiên bản 1.0, trạng thái **Dự thảo — chưa kích hoạt**. FE-01 cần chốt hành động quản trị, quyền và nguồn email nhận trước khi sử dụng. Không gửi mật khẩu ban đầu hoặc tự tạo quy trình kích hoạt mới.

### ACCOUNT_INVITATION — Mời/kích hoạt tài khoản

1. **Kích hoạt/người nhận dự kiến:** tài khoản do Admin tạo hợp lệ và quy trình mời đã được chấp thuận; gửi tới người được mời. Chờ chốt cơ chế mời, hiệu lực và trạng thái tài khoản.
2. **Điều kiện:** lời mời hiện hành, có hành động thiết lập truy cập được phép; không gửi mật khẩu.
3. **Tiêu đề:** `[Mland] Lời mời sử dụng tài khoản`.
4. **Nút/link:** **Xem lời mời** → `{{invitationUrl}}`, chỉ dùng khi chức năng được duyệt và tồn tại.
5. **Không gửi:** mẫu chưa kích hoạt, lời mời thu hồi/hết hiệu lực hoặc chưa có cơ chế truy cập an toàn.

> Chào {{recipientName}},
>
> Bạn được mời sử dụng tài khoản Mland cho {{invitationPurpose}}.
>
> **Xem lời mời** — {{invitationUrl}}
>
> Hãy kiểm tra thông tin và thực hiện hướng dẫn trong lời mời. Nếu bạn không mong đợi email này, vui lòng liên hệ hỗ trợ trước khi tiếp tục. Mland không yêu cầu trả lời email bằng mật khẩu.
>
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### ACCOUNT_LOCKED — Khóa tài khoản

1. **Kích hoạt/người nhận dự kiến:** quyết định khóa đã commit; gửi chủ tài khoản. Chờ chốt trường hợp khóa nào cần thư và nguồn liên hệ hợp lệ.
2. **Điều kiện:** khóa còn áp dụng, lý do công khai được nghiệp vụ cung cấp; không dùng ghi chú điều tra nội bộ.
3. **Tiêu đề:** `[Mland] Thông báo trạng thái tài khoản`.
4. **Nút/link:** **Liên hệ hỗ trợ** → `{{supportUrl}}`, không buộc đăng nhập tài khoản đang khóa chỉ để đọc thông báo.
5. **Không gửi:** mẫu chưa kích hoạt, quyết định chưa commit hoặc đã bị thay thế.

> Chào {{recipientName}},
>
> Tài khoản Mland của bạn hiện bị khóa theo quyết định ghi nhận vào {{changedAt}}.
> Thông tin có thể cung cấp: {{publicReason}}.
>
> Nếu cần làm rõ hoặc cho rằng có nhầm lẫn, vui lòng liên hệ hỗ trợ. Email này không thay đổi các giao dịch trước đó và không phải thông báo hủy đơn.
>
> **Liên hệ hỗ trợ** — {{supportUrl}}
> Trân trọng, Mland

### ACCOUNT_UNLOCKED — Mở khóa tài khoản

1. **Kích hoạt/người nhận dự kiến:** mở khóa đã commit; gửi chủ tài khoản. Chờ chốt loại mở khóa và điều kiện sử dụng lại.
2. **Điều kiện:** trạng thái hiện hành phù hợp hướng dẫn đăng nhập; không suy rằng đơn hoặc quyền cũ đều được khôi phục.
3. **Tiêu đề:** `[Mland] Tài khoản đã được mở khóa`.
4. **Nút/link:** **Đăng nhập Mland** → `{{signInUrl}}`.
5. **Không gửi:** mẫu chưa kích hoạt, thao tác chưa thành công hoặc trạng thái đã thay đổi lại.

> Chào {{recipientName}},
>
> Quyết định mở khóa tài khoản Mland của bạn đã được ghi nhận vào {{changedAt}}.
>
> **Đăng nhập Mland** — {{signInUrl}}
>
> Hệ thống kiểm tra trạng thái và quyền hiện tại khi bạn truy cập. Email không tự khôi phục các yêu cầu hoặc giao dịch đã hết hiệu lực.
>
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### ACCOUNT_ROLE_CHANGED — Thay đổi vai trò

1. **Kích hoạt/người nhận dự kiến:** thay đổi vai trò đã commit; gửi chủ tài khoản. Chờ chốt quy tắc vai trò và các đối tượng cần thông báo.
2. **Điều kiện:** mô tả vai trò/phạm vi khớp kết quả hiện hành, không lộ thông tin người khác.
3. **Tiêu đề:** `[Mland] Cập nhật vai trò tài khoản`.
4. **Nút/link:** **Xem tài khoản** → `{{accountUrl}}`.
5. **Không gửi:** mẫu chưa kích hoạt, thay đổi bị từ chối hoặc đã thay thế.

> Chào {{recipientName}},
>
> Vai trò tài khoản Mland của bạn đã được cập nhật vào {{changedAt}}.
> Vai trò hiện tại: {{roleSummary}}.
> Phạm vi áp dụng: {{scopeSummary}}.
>
> **Xem tài khoản** — {{accountUrl}}
>
> Quyền thực tế được kiểm tra khi bạn thực hiện từng thao tác; email này không cấp thêm quyền truy cập.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

## Mẫu dự thảo — Thiết kế và phê duyệt

Các mẫu kế thừa phiên bản 1.0, trạng thái **Dự thảo — chưa kích hoạt**. FE-05 và chủ quy trình duyệt phải chốt mốc, phiên bản hồ sơ, người nhận và quyền. Không suy ra AI có thẩm quyền chấp nhận thiết kế hoặc duyệt giá.

### DESIGN_CLARIFICATION_REQUESTED — Bổ sung thông tin

1. **Kích hoạt/người nhận dự kiến:** người review có quyền ghi yêu cầu bổ sung; gửi chủ yêu cầu thiết kế. Chờ chốt trạng thái và cách phản hồi.
2. **Điều kiện:** yêu cầu còn hiệu lực, nội dung gửi khách đã được nghiệp vụ chấp nhận.
3. **Tiêu đề:** `[Mland] Cần bổ sung thông tin thiết kế {{designCode}}`.
4. **Nút/link:** **Xem yêu cầu bổ sung** → `{{designUrl}}`.
5. **Không gửi:** mẫu chưa kích hoạt, review chưa commit, yêu cầu bị rút hoặc thay thế.

> Chào {{recipientName}},
>
> Mland cần thêm thông tin để tiếp tục xem xét thiết kế **{{designCode}}**.
> Nội dung cần bổ sung:
> {{clarificationSummary}}
>
> **Xem yêu cầu bổ sung** — {{designUrl}}
>
> Vui lòng phản hồi theo hướng dẫn trong hồ sơ. Yêu cầu này chưa phải xác nhận khả thi, báo giá cuối hoặc chấp nhận sản xuất.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### DESIGN_REVIEW_RESULT — Kết quả review

1. **Kích hoạt/người nhận dự kiến:** vai trò có quyền ghi kết quả review; gửi chủ yêu cầu thiết kế. Chờ chốt các kết quả và bước tiếp theo.
2. **Điều kiện:** khớp phiên bản thiết kế đã review, có mô tả công khai và hướng dẫn hợp lệ.
3. **Tiêu đề:** `[Mland] Kết quả xem xét thiết kế {{designCode}}`.
4. **Nút/link:** **Xem kết quả thiết kế** → `{{designUrl}}`.
5. **Không gửi:** mẫu chưa kích hoạt, chỉ có gợi ý AI, chưa có quyết định hợp lệ hoặc phiên bản hết hiệu lực.

> Chào {{recipientName}},
>
> Thiết kế **{{designCode}}**, phiên bản {{designVersion}}, đã có kết quả xem xét.
> Kết quả: {{publicReviewResult}}.
> Ghi chú dành cho bạn: {{publicReviewNote}}.
> Bước tiếp theo: {{nextActionSummary}}.
>
> **Xem kết quả thiết kế** — {{designUrl}}
>
> Kết quả áp dụng cho phiên bản nêu trên. Chỉ các điều khoản được xác nhận trong hồ sơ mới là căn cứ tiếp tục thực hiện.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### APPROVAL_REQUESTED — Yêu cầu phê duyệt nội bộ

1. **Kích hoạt/người nhận dự kiến:** hồ sơ được nộp vào quy trình duyệt; gửi riêng tới Manager được phân công và có quyền. Chờ chốt cách phân công, không gửi mọi Manager.
2. **Điều kiện:** đề xuất hiện hành, đúng phiên bản, đủ dữ liệu; chỉ đưa thông tin nội bộ cần thiết.
3. **Tiêu đề:** `[Mland — Nội bộ] Cần phê duyệt {{requestCode}}`.
4. **Nút/link:** **Mở hồ sơ phê duyệt** → `{{approvalUrl}}`, yêu cầu đăng nhập và kiểm tra quyền.
5. **Không gửi:** mẫu chưa kích hoạt, chưa xác định người duyệt, đề xuất đã rút/được xử lý hoặc người nhận mất quyền.

> Chào {{recipientName}},
>
> Hồ sơ **{{requestCode}}** đang chờ bạn xem xét theo phân công hiện tại.
> Loại yêu cầu: {{approvalType}}.
> Phiên bản đề xuất: {{proposalVersion}}.
> Tóm tắt: {{approvalSummary}}.
>
> **Mở hồ sơ phê duyệt** — {{approvalUrl}}
>
> Vui lòng xem dữ liệu hiện tại trước khi quyết định. Nhận hoặc mở email không có nghĩa đã phê duyệt; quyết định phải thực hiện trong hệ thống.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### APPROVAL_RESULT — Kết quả phê duyệt nội bộ

1. **Kích hoạt/người nhận dự kiến:** quyết định hợp lệ đã commit; gửi người nộp hoặc người xử lý tiếp theo quy trình. Chờ chốt danh sách nhận, không gửi bản nội bộ cho khách.
2. **Điều kiện:** đúng phiên bản, kết quả/ghi chú phù hợp quyền người nhận.
3. **Tiêu đề:** `[Mland — Nội bộ] Kết quả phê duyệt {{requestCode}}`.
4. **Nút/link:** **Xem quyết định** → `{{approvalUrl}}`.
5. **Không gửi:** mẫu chưa kích hoạt, quyết định chưa commit hoặc bị thay thế. Thông báo khách phải dùng mẫu khách với nội dung được công khai.

> Chào {{recipientName}},
>
> Hồ sơ **{{requestCode}}**, phiên bản {{proposalVersion}}, đã có quyết định phê duyệt.
> Kết quả: {{approvalResult}}.
> Ghi chú trong phạm vi bạn được xem: {{recipientVisibleNote}}.
> Bước tiếp theo: {{nextActionSummary}}.
>
> **Xem quyết định** — {{approvalUrl}}
>
> Không áp dụng quyết định này cho phiên bản khác. Các bước tài chính và xử lý tiếp vẫn phải được kiểm tra trong hệ thống.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

## Mẫu dự thảo — Nghĩa vụ thanh toán custom

Các mẫu kế thừa phiên bản 1.0, trạng thái **Dự thảo — chưa kích hoạt**. FE-05 phải chốt báo giá, nghĩa vụ cọc/thanh toán còn lại, hiệu lực và người nhận; không tự kế thừa tỷ lệ cọc workshop hay hạn 10/15 phút của đơn bán lẻ.

### CUSTOM_PAYMENT_REQUESTED — Báo giá/yêu cầu thanh toán

1. **Kích hoạt/người nhận dự kiến:** giá đúng phiên bản đã được duyệt và nghiệp vụ cho phép yêu cầu thanh toán; gửi chủ đơn custom. Chờ chốt các nghĩa vụ và bước khách chấp nhận.
2. **Điều kiện:** số tiền/hiệu lực được xác nhận và có trang xem điều khoản hợp lệ.
3. **Tiêu đề:** `[Mland] Thông tin thanh toán đơn đặt làm {{customOrderCode}}`.
4. **Nút/link:** **Xem báo giá và hướng dẫn** → `{{customOrderUrl}}`; kiểm tra điều kiện trên trang, không gắn link gateway đã hết hạn hoặc tạo giao dịch chỉ vì mở thư.
5. **Không gửi:** mẫu chưa kích hoạt, giá chưa duyệt, sai phiên bản, nghĩa vụ đã thanh toán/hết hiệu lực hoặc chưa đủ điều kiện gửi.

> Chào {{recipientName}},
>
> Đơn đặt làm **{{customOrderCode}}** có thông tin cần bạn xem xét.
> Phiên bản báo giá: {{quoteVersion}}.
> Khoản yêu cầu: {{paymentPurpose}}.
> Số tiền: {{requestedAmount}}.
> Điều kiện hiệu lực đã xác nhận: {{validitySummary}}.
>
> **Xem báo giá và hướng dẫn** — {{customOrderUrl}}
>
> Hãy kiểm tra điều khoản trước khi chấp nhận hoặc thanh toán. Email này không xác nhận bạn đã trả tiền hoặc đơn đã được đưa vào sản xuất.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### CUSTOM_PAYMENT_CONFIRMED — Xác nhận khoản thanh toán

1. **Kích hoạt/người nhận dự kiến:** nghiệp vụ ghi nhận khoản thanh toán custom hợp lệ; gửi chủ đơn. Chờ chốt điều kiện xác nhận/nhận thông báo của từng nghĩa vụ.
2. **Điều kiện:** số tiền, mục đích, tham chiếu và trạng thái tiếp theo đã xác nhận; không dựa vào trình duyệt quay về cổng.
3. **Tiêu đề:** `[Mland] Xác nhận khoản thanh toán {{customOrderCode}}`.
4. **Nút/link:** **Xem đơn đặt làm** → `{{customOrderUrl}}`.
5. **Không gửi:** mẫu chưa kích hoạt, giao dịch chưa rõ, sai tiền/tham chiếu hoặc cùng sự kiện đã được ghi nhận yêu cầu gửi.

> Chào {{recipientName}},
>
> Mland đã ghi nhận khoản thanh toán cho đơn đặt làm **{{customOrderCode}}**.
> Mục đích: {{paymentPurpose}}.
> Số tiền đã xác nhận: {{paidAmount}}.
> Thời điểm xác nhận: {{confirmedAt}}.
> Số tiền còn lại theo nghĩa vụ đã chốt: {{remainingAmount}}.
> Trạng thái tiếp theo: {{nextStateSummary}}.
>
> **Xem đơn đặt làm** — {{customOrderUrl}}
>
> Email chỉ xác nhận khoản nêu trên, không tự thay đổi các nghĩa vụ khác hoặc điều kiện sản xuất.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

## Mẫu dự thảo — Mở rộng chưa chốt

Các mẫu kế thừa phiên bản 1.0, trạng thái **Dự thảo — chưa kích hoạt**. Nội dung thư không phê duyệt chức năng mới hoặc cho phép tự lập job.

### REFUND_RESULT — Kết quả refund

1. **Kích hoạt/người nhận dự kiến:** kết quả refund đã được xác minh theo quy trình sẽ chốt; gửi người có quyền nhận thông tin giao dịch. Chờ chốt quy trình, thẩm quyền và danh sách kết quả.
2. **Điều kiện:** kết quả thực tế, tiền liên quan và mô tả được phép gửi; không suy từ việc mới nộp yêu cầu hoàn.
3. **Tiêu đề:** `[Mland] Kết quả xử lý yêu cầu hoàn tiền {{refundCode}}`.
4. **Nút/link:** **Xem kết quả yêu cầu** → `{{refundDetailsUrl}}`, chỉ có khi chức năng được duyệt và tồn tại.
5. **Không gửi:** mẫu chưa kích hoạt, kết quả chưa xác minh hoặc chưa có chức năng tra cứu hợp lệ.

> Chào {{recipientName}},
>
> Yêu cầu hoàn tiền **{{refundCode}}** liên quan {{transactionReference}} đã có kết quả xử lý.
> Kết quả đã xác nhận: {{verifiedRefundResult}}.
> Số tiền liên quan: {{refundAmount}}.
> Hướng dẫn tiếp theo: {{nextActionSummary}}.
>
> **Xem kết quả yêu cầu** — {{refundDetailsUrl}}
>
> Việc tiền hiển thị tại tài khoản nhận cần kiểm tra theo kết quả thực tế và hướng dẫn của đơn vị xử lý; thư không tự cam kết ngày tiền về.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### WORKSHOP_REMINDER — Nhắc lịch

1. **Kích hoạt/người nhận dự kiến:** booking còn hiệu lực tới mốc nhắc được duyệt; gửi email liên hệ booking. Chờ chốt mốc, số lần nhắc và điều kiện dừng; không tự đặt lịch 24 giờ trước.
2. **Điều kiện:** lịch/địa điểm hiện hành, booking hợp lệ, chưa gửi cho cùng mốc/phiên bản lịch.
3. **Tiêu đề:** `[Mland] Nhắc lịch workshop {{bookingCode}}`.
4. **Nút/link:** **Xem booking và vé** → `{{bookingUrl}}`, không tự cấp vé khác.
5. **Không gửi:** mẫu chưa kích hoạt, lịch đổi chưa xử lý, booking/vé không còn hiệu lực hoặc qua mốc được phép.

> Chào {{recipientName}},
>
> Mland nhắc bạn về lịch workshop của booking **{{bookingCode}}**.
> Workshop: {{packageName}}.
> Thời gian: {{sessionTimeWithTimezone}}.
> Địa điểm: {{branchName}} — {{branchAddress}}.
> Số người tham gia: {{participantCount}}.
>
> **Xem booking và vé** — {{bookingUrl}}
>
> Vui lòng kiểm tra thông tin và vé trước khi đến. Đây là thư nhắc lịch, không phải xác nhận booking hoặc yêu cầu thanh toán mới.
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

### PROMOTION_ANNOUNCEMENT — Thông báo ưu đãi

1. **Kích hoạt/người nhận dự kiến:** chiến dịch được duyệt, danh sách phù hợp lựa chọn nhận quảng bá; gửi riêng từng người. Chờ chốt consent, hủy nhận, phạm vi và lịch chiến dịch.
2. **Điều kiện:** điều khoản còn hiệu lực, có cơ chế hủy nhận hợp lệ; không coi email giao dịch là mặc định đồng ý quảng bá.
3. **Tiêu đề:** `[Mland] {{campaignTitle}}`.
4. **Nút/link:** **Xem điều kiện ưu đãi** → `{{campaignUrl}}`; **Ngừng nhận email ưu đãi** → `{{unsubscribeUrl}}`, chỉ có khi chức năng đã chốt.
5. **Không gửi:** mẫu chưa kích hoạt, thiếu điều kiện nhận, đã hủy nhận, hết hiệu lực hoặc thiếu cơ chế hủy nhận.

> Chào {{recipientName}},
>
> Mland giới thiệu chương trình **{{campaignTitle}}**.
> Nội dung: {{approvedCampaignSummary}}.
> Thời gian áp dụng: {{campaignPeriod}}.
> Điều kiện chính: {{eligibilitySummary}}.
>
> **Xem điều kiện ưu đãi** — {{campaignUrl}}
>
> Ưu đãi chỉ áp dụng khi đáp ứng điều kiện hiện hành; email không bảo đảm giữ lượt hoặc tự áp voucher vào đơn.
>
> Bạn nhận thư theo lựa chọn nhận email ưu đãi đã ghi nhận.
> **Ngừng nhận email ưu đãi** — {{unsubscribeUrl}}
>
> Cần hỗ trợ? {{supportContact}}
> Trân trọng, Mland

## Luồng chính — Module tạo yêu cầu gửi email

1. Method nghiệp vụ chính xử lý hành động cần thông báo, ví dụ xác nhận booking. Khi kết quả và dữ liệu cần cho thư đã sẵn sàng, method gọi `prepareEmailBooking(bookingId)`; đây là tên minh họa cho method chuẩn bị thư.
2. `prepareEmailBooking()` tự tập hợp dữ liệu cần thiết: email người nhận, mã template/phiên bản, các giá trị điền vào mẫu, dữ liệu vé/QR nếu cần và mã tham chiếu sự kiện nghiệp vụ. Method nghiệp vụ chính không phải tự ghép nội dung email.
3. `prepareEmailBooking()` kiểm tra đúng người nhận, đúng mẫu và đủ dữ liệu bắt buộc. Nếu chưa đủ hoặc không hợp lệ, method báo lỗi để bên gọi xử lý, không lưu một yêu cầu thiếu dữ liệu rồi để job tự tìm bổ sung.
4. `prepareEmailBooking()` tự lưu yêu cầu vào bảng yêu cầu email ở trạng thái **Chờ gửi**, kèm dữ liệu đã chuẩn bị. Method kết thúc sau khi ghi nhận yêu cầu, chưa gửi SMTP. Cùng sự kiện, mẫu và người nhận không tạo lặp yêu cầu.
5. Method nghiệp vụ chính tiếp tục và commit kết quả nghiệp vụ cùng yêu cầu email trong một giao dịch. `prepareEmailBooking()` không tự commit riêng; nếu giao dịch rollback thì cả hai không được lưu, job không có yêu cầu để gửi.
6. Sau commit, method nghiệp vụ chính trả kết quả mà không chờ SMTP. Trách nhiệm chuẩn bị và lưu yêu cầu email đã hoàn tất; việc ghép mẫu, gửi và cập nhật kết quả do job thực hiện sau đó.

## Luồng chính — Job gửi email

1. Job chạy định kỳ, tìm yêu cầu **Chờ gửi** và **Chờ gửi lại** đã tới thời điểm gửi tiếp.
2. Job nhận từng yêu cầu và đánh dấu **Đang gửi**, bảo đảm không có hai lượt cùng xử lý một yêu cầu.
3. Job đọc người nhận, mã template/phiên bản và dữ liệu đã lưu; kiểm tra dữ liệu rồi ghép vào template để tạo thư. Nếu có QR, job dựng ảnh nhúng từ dữ liệu đã được chuẩn bị. Job không tự tìm lại người nhận, truy dữ liệu booking/đơn, tính lại giá hoặc chạy lại nghiệp vụ.
4. Job gửi thư qua SMTP và ghi nhận lần thử. Việc gọi SMTP diễn ra sau khi giao dịch nghiệp vụ đã commit, không giữ giao dịch đó mở để chờ gửi.
5. SMTP xác nhận tiếp nhận thì job lưu **Đã gửi**, thời điểm và số lần thử. Trạng thái này chỉ xác nhận SMTP đã nhận, chưa chứng minh thư đã tới hộp thư hoặc được đọc.
6. Nếu lỗi, job lưu nguyên nhân và trạng thái theo phần xử lý lỗi bên dưới; tới hạn thì xử lý gửi lại nếu đủ điều kiện. Lỗi gửi không làm chạy lại hoặc đảo kết quả nghiệp vụ đã commit.

## Trạng thái xử lý

| Trạng thái | Ý nghĩa |
| --- | --- |
| Chờ gửi | Yêu cầu đã commit, chưa được job nhận xử lý |
| Đang gửi | Một lượt job đang xử lý yêu cầu |
| Đã gửi | SMTP đã xác nhận tiếp nhận; chưa có kết luận về hộp thư hoặc việc đọc |
| Chờ gửi lại | Lỗi tạm thời, xác định chưa gửi được và còn lượt thử; chờ thời điểm gửi tiếp |
| Gửi thất bại | Lỗi cần sửa hoặc đã hết số lần thử |
| Chưa rõ kết quả | Không biết SMTP đã nhận thư hay chưa; không tự gửi lại |

## Xử lý lỗi và gửi lại tự động

1. **Thiếu template/dữ liệu, mẫu chưa được phép dùng, người nhận không hợp lệ hoặc cấu hình sai:** chuyển **Gửi thất bại**, ghi nguyên nhân để sửa. Không tự truy bảng nghiệp vụ để bù dữ liệu và không đánh dấu Đã gửi.
2. **Lỗi tạm thời và xác định SMTP chưa nhận:** chuyển **Chờ gửi lại**. Tổng cộng tối đa **3 lần gọi SMTP**, gồm lần đầu; lỗi lần đầu chờ **1 phút**, lỗi lần hai chờ **5 phút**, tính từ lúc ghi nhận lỗi tương ứng. Lỗi lần ba chuyển **Gửi thất bại**.
3. **Không biết SMTP đã nhận chưa:** chuyển **Chưa rõ kết quả**, không tự gửi lại. Trường hợp này gồm mất phản hồi sau khi có thể đã chuyển thư hoặc SMTP đã nhận nhưng lưu kết quả thất bại; không tự kết luận chưa gửi.
4. **Ứng dụng khởi động lại:** giữ trạng thái, số lần thử và thời điểm gửi tiếp trong DB. Tiếp tục yêu cầu chờ; yêu cầu Đang gửi bị bỏ dở cần kiểm tra trước khi nhận lại, nếu có thể đã gọi SMTP thì coi là Chưa rõ kết quả.
5. **Tránh gửi trùng:** cùng sự kiện, template và người nhận không tự tạo nhiều yêu cầu; job không xử lý đồng thời một yêu cầu và không gửi lại yêu cầu Đã gửi. Tuy vậy SMTP không bảo đảm thư chỉ đến hộp thư đúng một lần khi kết quả gửi bị mất.
6. **Tác động nghiệp vụ:** lỗi gửi sau commit không đảo kết quả booking, đơn hàng hoặc thanh toán. Việc sửa/gửi lại email không thực hiện lại hành động nghiệp vụ.

## Gửi lại thủ công

1. Người có quyền hỗ trợ thông báo kiểm tra yêu cầu, trạng thái, lỗi gần nhất và nguyên nhân đã được sửa. Chỉ xem dữ liệu chẩn đoán cần thiết, không công khai bí mật hoặc toàn bộ nội dung thư.
2. Với **Chưa rõ kết quả**, kiểm tra bằng chứng nhà cung cấp trước. Nếu không xác minh được, phải ghi rõ việc chấp nhận nguy cơ thư trùng trước khi chủ động gửi lại.
3. Ghi lý do, người thực hiện và tạo lượt gửi lại liên kết yêu cầu gốc; giữ lịch sử cũ. Không tự sửa người nhận hoặc lấy dữ liệu mới từ bảng nghiệp vụ. Nếu cần sửa dữ liệu, module sở hữu phải cung cấp lại dữ liệu đúng qua quy trình được phép, có truy vết.
4. Không chạy lượt mới khi yêu cầu gốc hoặc lượt liên quan còn đang gửi/chờ gửi lại. Lượt gửi lại áp dụng cùng giới hạn 3 lần và lịch chờ 1/5 phút; hết lượt không tự sinh tiếp lượt thủ công.
5. Gửi lại chỉ thực hiện công việc email, không tạo lại đơn, booking hoặc khoản thanh toán. Phần này mô tả quy trình, chưa bổ sung màn hình hay API quản trị.

## Hiện trạng và kiểm tra

1. Code hiện có job và mẫu xác nhận booking dùng SMTP; chưa phải cơ chế gửi chung đã hoàn tất. Thiết kế ở đây yêu cầu module lưu đủ dữ liệu và job chỉ dựng/gửi thư, nên cách code booking đang truy dữ liệu riêng cần được đối chiếu khi triển khai sau này.
2. Kiểm tra luồng thành công: module lưu đủ dữ liệu, job ghép đúng mẫu và gửi đúng người; SMTP nhận thì ghi Đã gửi. Xác minh/reset Firebase vẫn đi luồng FE-01, không vào job SMTP này.
3. Kiểm tra rollback và thiếu dữ liệu: giao dịch thất bại không có thư để gửi; job phát hiện yêu cầu lỗi không gửi và không báo thành công. Job không đọc bảng booking/đơn để dựng lại nội dung.
4. Kiểm tra lỗi và retry: lỗi tạm thời thử tối đa 3 lần theo lịch 1/5 phút, lỗi cấu hình dừng, kết quả chưa rõ không tự gửi lại; khởi động lại không đặt lại bộ đếm.
5. Kiểm tra chống trùng và vận hành: sự kiện lặp hoặc hai lượt job không tự gửi đồng thời; gửi lại thủ công có quyền, lý do và lịch sử; email lỗi không đảo nghiệp vụ. Chưa sửa code, API hoặc database trong lần cập nhật tài liệu này.

## Tài liệu tham khảo FE-07

1. [Spring Framework — Email và JavaMailSender](https://docs.spring.io/spring-framework/reference/integration/email.html).
2. [Thymeleaf — Gửi email với Spring](https://www.thymeleaf.org/doc/articles/springmail.html).
3. [SMTP — RFC 5321: tiếp nhận thư và rủi ro timeout/gửi trùng](https://www.rfc-editor.org/rfc/rfc5321.html).
4. [Firebase — Quản lý người dùng và email authentication](https://firebase.google.com/docs/auth/web/manage-users).
5. [Firebase — Tùy chỉnh email quản lý tài khoản](https://support.google.com/firebase/answer/7000714?hl=en).

