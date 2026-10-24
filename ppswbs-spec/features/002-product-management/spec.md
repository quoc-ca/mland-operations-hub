# Feature Specification: Product Management

**Date created**: 2026-10-23
**Date updated**: 2026-10-24
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Feature**: `002-product-management` — FE-02  
**Feature Branch**: Không tạo nhánh mới; không có hook tạo nhánh được cấu hình.  
**Status**: Draft — đặc tả nghiệp vụ để review và lập kế hoạch, chưa phải phê duyệt triển khai.  
**Input**: Viết spec cho FE-02 Product Management trong [research.md](../../research.md#fe-02), bao gồm các quyết định người dùng đã chốt trong hội thoại.

Nguồn chính là FE-02 hiện tại và các quyết định trực tiếp của người dùng. Report 3 cung cấp baseline Browse Product Catalogue và Manage Products. Các khác biệt đã chốt cho feature này gồm: chỉ phân biệt biến thể theo size 5–29, không có lựa chọn khắc, không thêm/đổi size sau khi lưu, Manager được tự duyệt, sửa thông tin trực tiếp sau lần công khai đầu tiên, xóa mềm không có khôi phục và định dạng SKU đã xác định. Không dùng Report 3 cũ để khôi phục hành vi đã bị loại bỏ.

## Clarifications

### Session 2026-10-23

- Q: Khi một size đang có đề xuất giá chờ Manager duyệt, nếu Staff gửi thêm đề xuất giá khác cho cùng size thì hệ thống nên xử lí thế nào? → A: Chặn tạo đề xuất thứ hai; Staff có thể sửa đề xuất hiện tại thành phiên bản mới rồi gửi lại, phiên bản cũ mất hiệu lực. Mỗi size chỉ có một phiên bản đề xuất Chờ duyệt hiện hành.
- Q: Tên danh mục sản phẩm có cần duy nhất trên toàn hệ thống, kể cả danh mục đã ngừng hoạt động không? → A: Có; không cho trùng tên trên toàn bộ danh mục, kể cả đã ngừng hoạt động, sau khi bỏ khoảng trắng đầu/cuối và so khớp không phân biệt hoa/thường.

### Session 2026-10-24

- Q: Thống nhất các vai trò nội bộ như thế nào? → A: Người dùng đồng ý dùng MEMBER, STAFF, MANAGER, ADMIN; Manager là vai trò riêng, không tự đồng nhất OWNER; Admin tạo/cấp quyền tài khoản nội bộ, đăng ký khách không tự nhận quyền nội bộ. Mland kiểm tra quyền từ dữ liệu của mình sau xác thực.
- Q: Dịch vụ lưu ảnh sản phẩm là gì? → A: Người dùng chọn Cloud Storage Service và xác nhận nhà cung cấp cụ thể là Cloudinary ngày 2026-10-24.
- Q: Chính sách giữ/dọn ảnh và kết quả thao tác thế nào? → A: Đồng ý ảnh chưa gắn đủ 24 giờ được dọn nếu không còn sử dụng; ảnh đã gỡ đủ 30 ngày chỉ dọn khi không còn sản phẩm/hồ sơ/đơn/lịch sử tham chiếu; giữ ảnh lịch sử theo bản ghi liên quan. Kết quả thao tác tối thiểu giữ 5 năm; business audit 5 năm kế thừa Report 3.
- Q: Các mặc định nhập liệu và hiển thị có được chốt không? → A: Đồng ý tên danh mục 1–200 ký tự, mô tả tùy chọn tối đa 5.000, từ khóa tối đa 200, 20 sản phẩm/trang, mới nhất theo createdAt, lý do từ chối bắt buộc tối đa 2.000, mỗi ảnh tối đa 5.000.000 bytes, danh mục mới active=true và size mới saleEnabled=true nhưng chưa mua được trước công khai hợp lệ.

- Q: FE-02 có dùng chung cơ chế xác thực theo Member Profile 001 không? → A: Có. Người dùng đồng ý kế thừa Firebase bearer (Authorization: Bearer) cho mọi REST API, kể cả frontend React + Vite, phiên nội bộ cố định 8 giờ tính từ auth_time của Firebase ID token, không gia hạn trượt; hết hạn thì API trả 401 và React yêu cầu đăng nhập lại qua Firebase SDK, kiểm tra thu hồi/trạng thái qua identity owner; kiểm tra thêm quyền Staff/Manager trước từng thao tác. Giữ hành trình Member và điều hướng đăng nhập thành công về `/`. MFA đặc quyền và recovery thuộc identity/FE-01; chưa chọn Authenticator hoặc phương thức cụ thể từ FE-02.

[Spec 001](../001-account-management/spec.md) là nguồn thiết kế cơ chế xác thực dùng chung. Quyết định trên mở rộng chính sách phiên 8 giờ cho nội bộ trong phạm vi FE-02, không biến Member bootstrap/cookie thành quyền Staff/Manager và không sửa luồng 001. Cookie web và CSRF của 001 không áp dụng cho FE-02 vì UI là React SPA gọi REST; nếu identity owner chọn credential transport khác thì cập nhật theo contract dùng chung. Thiết kế/triển khai MFA chung là dependency FE-01 cần hoàn thiện trước bật quản trị.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tạo và tra cứu sản phẩm nháp (Priority: P1)

Là Staff hoặc Manager có quyền quản lí sản phẩm, tôi muốn tạo một mẫu nhẫn với các size thực tế có bán và tìm lại sản phẩm để hoàn thiện trước khi gửi duyệt.

**Why this priority**: Hồ sơ sản phẩm và định danh size là đầu vào cho toàn bộ vòng đời catalogue.

**Independent Test**: Dùng một danh mục hoạt động và tài khoản có quyền, tạo sản phẩm với size 5 và 12; kiểm tra bản nháp, giá đề xuất, số lượng, SKU và tìm kiếm quản trị mà chưa cần công khai hay mua hàng.

**Acceptance Scenarios**:

1. **Given** tài khoản hoạt động có quyền tạo, **When** lưu tên/mô tả hợp lệ, danh mục hoạt động, size 5 và 12 với giá đề xuất nguyên VND dương và số lượng nguyên không âm, **Then** tạo một sản phẩm Chưa công khai, hồ sơ Nháp và hai biến thể; chưa có giá bán áp dụng.
2. **Given** mã số sản phẩm được cấp là 123, **When** tạo các biến thể trên, **Then** SKU là `SP000123-S05` và `SP000123-S12`, chỉ đọc, duy nhất và không do người dùng nhập.
3. **Given** đang tạo bản nháp chưa có ảnh, **When** dữ liệu còn lại hợp lệ, **Then** được lưu nhưng chưa đủ điều kiện gửi duyệt/công khai.
4. **Given** tên/mô tả trống hoặc vượt giới hạn, size ngoài 5–29, size không nguyên, trùng size, giá sai hoặc số lượng âm/không nguyên, **When** lưu, **Then** chỉ rõ trường/dòng lỗi, giữ nội dung đã nhập và không tạo sản phẩm một phần.
5. **Given** có sản phẩm nháp, **When** tìm theo mã/tên hoặc SKU và lọc theo danh mục/trạng thái công khai, **Then** trả đúng sản phẩm, chỉ rõ size khớp SKU và phân biệt giá đề xuất, giá đã duyệt, giá đang áp dụng.
6. **Given** lưu thất bại hoặc mất phản hồi, **When** kiểm tra kết quả và thử lại cùng yêu cầu, **Then** không có sản phẩm/biến thể trùng, SKU thiếu hoặc thông báo thành công khi chưa xác nhận kết quả.
7. **Given** hơn 20 sản phẩm khớp tiêu chí, **When** xem danh sách quản trị và chuyển trang, **Then** mặc định 20 bản ghi/trang, giữ tiêu chí và thứ tự xác định; không hiện sản phẩm đã xóa trong danh sách thông thường.
8. **Given** Guest/Member, tài khoản không hoạt động hoặc thiếu quyền tương ứng, **When** đọc dữ liệu quản trị hay gửi thao tác thay đổi, **Then** không được cấp dữ liệu nội bộ/quyền thay đổi; yêu cầu bị từ chối an toàn.
9. **Given** khách đăng ký hoặc đăng nhập social lần đầu, **When** hoàn tất luồng Member, **Then** không tự nhận STAFF/MANAGER/ADMIN; tài khoản nội bộ chỉ có quyền do Admin cấp và backend xác nhận bằng các mã đã chốt, không suy MANAGER từ OWNER.
10. **Given** Staff/Manager có quyền dùng quản trị, **When** xác thực và truy cập FE-02, **Then** mọi REST API (kể cả frontend React) dùng bearer đã xác minh, phiên nội bộ cố định 8 giờ tính từ auth_time với kiểm tra thu hồi/trạng thái; từng thao tác vẫn kiểm tra quyền hiện tại. Phiên hết hạn/thu hồi bị từ chối, session hợp lệ không buộc xác minh email lại và cookie Member không tự cấp quyền nội bộ. Khi identity chưa xác nhận đạt yêu cầu xác thực đặc quyền, không được vào quản trị.

### User Story 2 - Gửi và phê duyệt sản phẩm mới (Priority: P1)

Là Staff, tôi muốn gửi hồ sơ hoàn chỉnh để Manager duyệt; là Manager, tôi muốn xem đúng phiên bản và quyết định sản phẩm cùng giá của từng size.

**Why this priority**: Sản phẩm mới cần có quyết định nghiệp vụ hợp lệ trước khi tiếp tục công khai lần đầu.

**Independent Test**: Chuẩn bị một bản nháp hoàn chỉnh, thực hiện gửi, duyệt/từ chối và sửa/gửi lại; kiểm tra quyết định theo phiên bản mà chưa cần áp dụng giá hoặc mở catalogue.

**Acceptance Scenarios**:

1. **Given** sản phẩm mới đủ thông tin, danh mục hoạt động, ít nhất một ảnh sử dụng được và giá đề xuất cho mọi size, **When** người có quyền Gửi duyệt, **Then** lưu một phiên bản hồ sơ Chờ duyệt cùng đề xuất giá đúng size; vẫn Chưa công khai.
2. **Given** hồ sơ Chờ duyệt còn hiệu lực, **When** Manager duyệt với các giá cuối cùng hợp lệ, **Then** ghi hồ sơ Đã duyệt, các giá được duyệt, người/thời điểm quyết định; chưa áp dụng giá hoặc công khai.
3. **Given** sản phẩm/đề xuất do chính Manager tạo, **When** Manager có quyền xác nhận duyệt, **Then** được tự duyệt với cùng kiểm tra và audit, không bắt buộc Manager thứ hai.
4. **Given** Manager từ chối và nhập lý do, **When** xác nhận, **Then** lưu Bị từ chối và lý do; Staff được sửa thành phiên bản mới/gửi lại, các giá bị từ chối không được áp dụng.
5. **Given** sản phẩm chưa từng công khai đã gửi hoặc được duyệt, **When** sửa nội dung, ảnh hoặc giá đề xuất, **Then** phiên bản cũ mất hiệu lực cho việc công khai, phải gửi duyệt lại; quyết định lịch sử không bị viết lại.
6. **Given** Staff cố tự duyệt, hồ sơ thiếu ảnh, sản phẩm đã xóa hoặc phiên bản đã thay đổi/được xử lí, **When** gửi hay quyết định, **Then** từ chối mà không ghi đè quyết định hay duyệt dữ liệu cũ.
7. **Given** Manager hủy trước xác nhận hoặc lưu quyết định thất bại, **When** thao tác kết thúc, **Then** không tạo quyết định một phần; gửi lặp cùng yêu cầu không tạo thêm hồ sơ/quyết định.

### User Story 3 - Áp dụng giá và công khai lần đầu (Priority: P1)

Là người quản lí catalogue có quyền, tôi muốn áp dụng đúng giá Manager duyệt rồi chủ động công khai để khách xem giá hợp lệ.

**Why this priority**: Quyết định giá, thời điểm áp dụng và công khai phải tách biệt để tránh bán theo giá chưa được chấp nhận.

**Independent Test**: Dùng sản phẩm đã duyệt, áp dụng giá các size và công khai; đối chiếu thông tin khách thấy, bao gồm sản phẩm có số lượng bằng 0.

**Acceptance Scenarios**:

1. **Given** hồ sơ và đề xuất giá được duyệt, **When** Staff/Manager có quyền áp dụng đúng số tiền cho đúng size/điều khoản, **Then** giá trở thành giá bán, có dấu vết quyết định và áp dụng; vẫn chưa công khai.
2. **Given** giá chưa duyệt, bị từ chối, mất hiệu lực, sai size hoặc khác số tiền đã duyệt, **When** áp dụng, **Then** từ chối và giữ giá trước đó.
3. **Given** hồ sơ mới đã duyệt còn hiệu lực, danh mục hoạt động, ít nhất một ảnh dùng được và tất cả size đã áp dụng giá duyệt, **When** người có quyền Công khai, **Then** sản phẩm xuất hiện ở lượt đọc công khai tiếp theo.
4. **Given** một size có số lượng 0 nhưng mọi điều kiện công khai khác đạt, **When** công khai, **Then** không bị chặn chỉ vì hết hàng; khả năng mua được hiển thị riêng.
5. **Given** thiếu điều kiện công khai hoặc tài khoản mất quyền, **When** công khai, **Then** giữ trạng thái trước đó và chỉ rõ điều kiện không đạt trong phạm vi được phép xem.
6. **Given** cùng yêu cầu áp dụng/công khai đã thành công, **When** gửi lại, **Then** trả kết quả hiện tại, không tạo thêm lần áp dụng/chuyển trạng thái; yêu cầu cũ không ghi đè giá mới hơn.

### User Story 4 - Cập nhật nội dung, ảnh và số lượng (Priority: P1)

Là Staff/Manager có quyền, tôi muốn cập nhật thông tin và số lượng của size đã có mà không làm thay đổi hàng khách đã đặt hoặc tạo thêm size.

**Why this priority**: Shop cần duy trì dữ liệu bán hằng ngày và bảo toàn các cam kết đã chấp nhận.

**Independent Test**: Dùng sản phẩm đã từng công khai với một quyền giữ hợp lệ; kiểm tra sửa nội dung/ảnh, giới hạn số lượng, dữ liệu cũ và danh sách size cố định.

**Acceptance Scenarios**:

1. **Given** sản phẩm đã từng công khai, **When** lưu tên, mô tả, ảnh hoặc danh mục hợp lệ, **Then** cập nhật trực tiếp không cần duyệt lại nội dung, kể cả đang ẩn; nếu đang công khai phải giữ đủ điều kiện công khai.
2. **Given** còn cho phép bán 10, trong đó giữ hợp lệ 2, **When** xem size đó, **Then** khả dụng là 8; cập nhật xuống 1 bị từ chối, xuống 2 được chấp nhận nếu dữ liệu giữ không đổi.
3. **Given** lượng giữ hoặc dữ liệu sản phẩm thay đổi khi đang sửa, **When** lưu dữ liệu cũ, **Then** từ chối ghi đè, cho xem dữ liệu mới/xác nhận lại, không hủy giữ để ép lưu.
4. **Given** sản phẩm đã lưu, kể cả Nháp/ẩn/chưa có giao dịch, **When** yêu cầu thêm size, đổi size, sửa SKU hoặc gửi lựa chọn khắc, **Then** từ chối; chỉ cập nhật giá theo quy trình riêng, số lượng và trạng thái bán của size đã có.
5. **Given** thêm/thay/sắp xếp/gỡ ảnh hợp lệ, **When** lưu, **Then** giữ ảnh có vị trí riêng và alt text; gỡ ảnh cuối cùng của sản phẩm đang công khai bị từ chối.
6. **Given** ảnh sai định dạng, vượt giới hạn, thiếu alt text, trùng vị trí hoặc lưu ảnh thất bại, **When** lưu, **Then** chỉ rõ lỗi, không thay ảnh hợp lệ bằng liên kết hỏng hoặc xóa tệp dùng chung/lịch sử.
7. **Given** đơn/quyền giữ hợp lệ đã chấp nhận, **When** sửa thông tin, số lượng hoặc ảnh, **Then** không sửa thông tin giao dịch đã chốt, thời hạn giữ hoặc giá đã chấp nhận.
8. **Given** frontend React + Vite tách riêng gọi REST API, **When** thực hiện xem/tìm/lọc hoặc các thao tác quản trị cốt lõi trong US1–US8, **Then** hoàn thành được hành trình hợp lệ, nhận lỗi nhập liệu/quyền phù hợp qua typed error và có thể sửa/thử lại; mỗi màn hình có loading, empty, validation, success, failure và recovery.
9. **Given** ảnh chưa gắn quá 24 giờ hoặc ảnh đã gỡ không còn tham chiếu quá 30 ngày, **When** dọn ảnh, **Then** chỉ xóa object đủ điều kiện, không xóa ảnh còn được sản phẩm/hồ sơ/đơn/lịch sử sử dụng; attach đồng thời không được trỏ tới object đang bị xóa. Xóa mềm sản phẩm không tự làm ảnh lịch sử đủ điều kiện dọn.

### User Story 5 - Thay đổi giá cho size đã có (Priority: P1)

Là Staff, tôi muốn đề xuất giá mới cho một size; là Manager, tôi muốn quyết định giá trước khi người có quyền áp dụng cho lượt mua mới.

**Why this priority**: Thay đổi giá luôn cần Manager nhưng không được làm mất giá bán hợp lệ đang dùng.

**Independent Test**: Dùng sản phẩm đang công khai, đề xuất giá mới, duyệt/từ chối rồi áp dụng; so sánh catalogue mới với giá quyền giữ đã có.

**Acceptance Scenarios**:

1. **Given** size có giá đang áp dụng, **When** lưu nháp/gửi giá đề xuất mới, **Then** hiển thị mã/trạng thái đề xuất riêng, giữ nguyên giá bán và công khai hiện tại.
2. **Given** đề xuất Chờ duyệt, **When** Manager chấp nhận, điều chỉnh giá cuối cùng hợp lệ hoặc từ chối kèm lý do, **Then** lưu quyết định, đối tượng/điều khoản, đầu vào, người/thời điểm; chưa tự áp dụng.
3. **Given** Manager không dùng được gợi ý AI, **When** có đầu vào nghiệp vụ hợp lệ, **Then** vẫn quyết định thủ công được; AI không thay đổi giá hoặc công khai.
4. **Given** quyết định giá còn hiệu lực, **When** người có quyền áp dụng, **Then** lượt xem/checkout mới dùng giá mới khi đủ điều kiện; quyền giữ hợp lệ và đơn đã chấp nhận vẫn giữ giá/thời hạn gốc.
5. **Given** một size đang có đề xuất Chờ duyệt, **When** tạo/gửi thêm đề xuất độc lập cho cùng size, **Then** từ chối và hướng dẫn sửa đề xuất hiện hành; sửa/gửi lại tạo phiên bản mới, phiên bản cũ mất hiệu lực, chỉ một phiên bản còn Chờ duyệt. Yêu cầu áp dụng đề xuất cũ sau giá mới hơn không được ghi đè giá mới hơn.
6. **Given** công bố/áp dụng giá và command đã commit, **When** kiểm tra policy lưu dữ liệu, **Then** business audit được giữ 5 năm từ sự kiện và command result tối thiểu được giữ 5 năm từ commit, không chứa toàn bộ request/token; xóa mềm sản phẩm không xóa sớm các bằng chứng này.

### User Story 6 - Ẩn, ngừng bán và xóa sản phẩm (Priority: P1)

Là Staff/Manager có quyền, tôi muốn dừng mua mới ở cấp sản phẩm hoặc size, hoặc xóa mềm sản phẩm khỏi danh sách làm việc, đồng thời giữ giao dịch và lịch sử đã chấp nhận.

**Why this priority**: Shop phải dừng bán kịp thời mà không phá vỡ đơn đang xử lí.

**Independent Test**: Dùng sản phẩm hai size với quyền giữ hợp lệ; lần lượt ngừng bán size, ẩn toàn sản phẩm, công khai lại rồi xóa mềm, kiểm tra mua mới và giao dịch cũ riêng biệt.

**Acceptance Scenarios**:

1. **Given** một size còn số lượng, **When** Ngừng bán, **Then** size vẫn hiện nhãn nhưng không thêm mới vào giỏ/tạo giữ mới; số lượng không về 0 và size khác không đổi trạng thái.
2. **Given** size đã ngừng bán, **When** mở bán lại với dữ liệu/giá hợp lệ, **Then** chỉ mua được nếu đồng thời còn số lượng và sản phẩm đủ điều kiện; không tự công khai sản phẩm ẩn.
3. **Given** sản phẩm đang công khai, **When** ngừng công khai, **Then** không còn đọc công khai/giữ mới, vẫn có trong quản trị và có thể công khai lại khi đủ điều kiện.
4. **Given** sản phẩm ở trạng thái chưa xóa bất kỳ, **When** người có quyền Xóa và xác nhận, **Then** xóa mềm, rời catalogue/danh sách quản trị thông thường, chặn sửa/gửi duyệt/duyệt/áp dụng giá/công khai/mua mới; không có khôi phục.
5. **Given** đang xác nhận xóa, **When** hủy hoặc phiên bản thay đổi, **Then** không xóa bằng dữ liệu cũ; nếu đã xóa thành công, gửi lặp chỉ trả trạng thái đã xóa theo quyền.
6. **Given** đơn/quyền giữ hợp lệ trước khi ẩn, ngừng bán hoặc xóa, **When** giao dịch tiếp tục theo FE-08, **Then** giữ giá/thời hạn/định danh/lịch sử và vẫn xử lí thanh toán hợp lệ, không tự hủy, hoàn/trừ lượng bán hoặc gia hạn.
7. **Given** xóa đồng thời với công khai, duyệt, áp dụng giá hoặc tạo giữ, **When** xác định kết quả, **Then** không hành động nào làm sống lại sản phẩm đã xóa hoặc tạo giữ mới sau khi xóa có hiệu lực.

### User Story 7 - Quản lí danh mục (Priority: P2)

Là Staff có quyền quản lí sản phẩm, tôi muốn tạo/sửa và kích hoạt/ngừng kích hoạt danh mục để tổ chức catalogue.

**Why this priority**: Danh mục hỗ trợ tìm kiếm và quyết định sản phẩm được phép hiển thị công khai.

**Independent Test**: Dùng danh mục có sản phẩm công khai và sản phẩm ẩn, đổi trạng thái danh mục; kiểm tra catalogue và lịch sử.

**Acceptance Scenarios**:

1. **Given** Staff có quyền, **When** tạo/sửa danh mục hợp lệ, **Then** lưu tên/mô tả và audit; dữ liệu sai, trùng tên hoặc yêu cầu cũ bị từ chối không lưu một phần.
2. **Given** danh mục có sản phẩm liên quan, **When** ngừng kích hoạt, **Then** xem ảnh hưởng trước xác nhận; sau xác nhận loại danh mục/sản phẩm liên quan khỏi đọc công khai/mua mới.
3. **Given** kích hoạt lại danh mục, **When** đọc catalogue, **Then** chỉ sản phẩm còn công khai, chưa xóa, đủ điều kiện khác xuất hiện; không tự công khai sản phẩm ẩn.
4. **Given** danh mục đã được tham chiếu, **When** cần ngừng sử dụng, **Then** dùng ngừng kích hoạt hoặc chuyển sản phẩm sang danh mục khác, không xóa lịch sử tham chiếu.
5. **Given** danh mục tên có khoảng trắng đầu/cuối, tên trùng khác hoa/thường hoặc tên/mô tả ở các biên A-001, **When** tạo/sửa, **Then** trim tên và chấp nhận/từ chối đúng giới hạn; danh mục không hoạt động vẫn tham gia kiểm tra trùng tên.

### User Story 8 - Tra cứu catalogue và chọn size (Priority: P1)

Là Guest hoặc Member, tôi muốn tìm, lọc, sắp xếp và xem đúng giá/khả năng mua của size đã có trước khi tiếp tục sang FE-08.

**Why this priority**: Khách cần thông tin chính xác và không thấy giá/dữ liệu quản trị.

**Independent Test**: Chuẩn bị sản phẩm nháp, công khai, ẩn, xóa, hết hàng, size ngừng bán và danh mục không hoạt động; truy cập không đăng nhập để kiểm tra catalogue.

**Acceptance Scenarios**:

1. **Given** Guest không đăng nhập, **When** mở catalogue/chi tiết, **Then** chỉ thấy sản phẩm công khai đủ điều kiện, ảnh dùng được và giá đang áp dụng đã duyệt; không thấy nháp, giá đề xuất/chưa áp dụng hoặc hồ sơ giữ nội bộ.
2. **Given** từ khóa có khoảng trắng đầu/cuối và khác hoa/thường, **When** tìm, **Then** khớp chuỗi con trong tên/mô tả; danh mục, giá và Có thể mua được lọc đồng thời.
3. **Given** size 12 có lượng bán 10, giữ hợp lệ 2 và đang bán, **When** xem, **Then** khả dụng là 8; chuyển Ngừng bán vẫn có lượng 8 nhưng không Có thể mua.
4. **Given** size 12 giá 100.000 VND ngừng bán, size 13 giá 200.000 VND mua được, **When** lọc Có thể mua và giá tối đa 150.000 VND, **Then** không trả sản phẩm bằng cách ghép giá size 12 với trạng thái size 13.
5. **Given** không có size nào mua được nhưng sản phẩm đủ điều kiện công khai, **When** không lọc Có thể mua, **Then** vẫn hiện sản phẩm; khi bật lọc thì bị loại.
6. **Given** chọn tên, giá tăng/giảm hoặc mới nhất, **When** xem kết quả, **Then** giữ từ khóa/bộ lọc, dùng đúng giá đại diện và thứ tự phụ ổn định; mặc định tên A–Z rồi định danh.
7. **Given** chọn size đã có, **When** xem chi tiết, **Then** thấy giá VND chính xác và khả năng mua của đúng size; size ngoài cấu hình bị từ chối, không có khắc hay tạo size mới.
8. **Given** giá âm, min lớn hơn max, danh mục không hỗ trợ/không hoạt động hoặc sắp xếp không hỗ trợ, **When** tìm/lọc, **Then** chỉ rõ tiêu chí lỗi để sửa, không đổi sản phẩm.
9. **Given** không có kết quả, lỗi tải hoặc mất toàn bộ ảnh dùng được, **When** đọc, **Then** phân biệt rỗng với lỗi; mất ảnh loại sản phẩm khỏi công khai không đổi cờ công khai/lịch sử, khôi phục hiển thị khi điều kiện hợp lệ trở lại.
10. **Given** khách chọn Mua ngay/Thêm vào giỏ, **When** đi tiếp, **Then** chuyển sản phẩm, biến thể và số lượng sang FE-08 để kiểm tra lại quyền/điều kiện; tra cứu không tạo giỏ, đơn, giữ hàng hoặc bảo đảm giá cũ.
11. **Given** từ khóa vượt 200 ký tự sau trim hoặc hơn 20 kết quả khớp, **When** tra cứu, **Then** báo lỗi từ khóa quá dài hoặc chia trang mặc định 20 bản ghi, giữ tiêu chí và thứ tự xác định theo A-002.

### Edge Cases

- Biên 0/1/200/201 ký tự tên, 0/1/5.000/5.001 ký tự mô tả, size 4/5/29/30, size không nguyên/trùng, giá 0/âm/không nguyên và số lượng âm/không nguyên.
- SKU với mã sản phẩm hơn 6 chữ số không được cắt hay va chạm; không tái cấp sau xóa mềm.
- Hồ sơ, giá và công khai là ba trạng thái riêng: đã duyệt chưa áp dụng không cho công khai; đã áp dụng nhưng chưa Công khai không cho khách thấy.
- Sửa trước lần công khai đầu làm mất hiệu lực duyệt; sản phẩm đã từng công khai đang ẩn không bị coi lại là mới.
- Mất ảnh bên ngoài không tự hủy cờ công khai; cố gỡ ảnh cuối khi đang công khai phải bị từ chối.
- Hai người sửa/duyệt/áp dụng đồng thời, lượng giữ thay đổi lúc sửa số lượng, yêu cầu cũ sau xóa không được làm mất dữ liệu/khôi phục sản phẩm.
- Giá cũ trong giỏ/trang trước không bảo đảm mua; giá của quyền giữ hợp lệ giữ nguyên trong thời hạn gốc.
- Mất phản hồi không đồng nghĩa chưa lưu; thử lại không tạo trùng sản phẩm, SKU, hồ sơ, quyết định, áp dụng hoặc chuyển trạng thái.
- Ngừng bán/xóa không thay cho hoàn kho, hủy giao dịch hoặc xóa lịch sử; FE-08 sở hữu kết quả giao dịch và giải phóng/chốt giữ.

## Requirements *(mandatory)*

### Functional Requirements

#### Phạm vi, quyền và dữ liệu sản phẩm

- **FR-001**: Tra cứu công khai MUST không yêu cầu đăng nhập. Mọi thao tác quản trị MUST kiểm tra danh tính, tài khoản hoạt động, vai trò và quyền hiện tại trước khi trả dữ liệu nội bộ hoặc thay đổi trạng thái; Guest/Member không có quyền quản trị sản phẩm. FE-02 MUST dùng chung cơ chế xác thực theo 001 qua identity owner: bearer đã xác minh cho API, cookie web cố định 8 giờ không gia hạn trượt, kiểm tra thu hồi/trạng thái và CSRF cho thao tác web. Phiên Member không tự cấp quyền nội bộ; quản trị MUST chỉ chấp nhận kết quả xác thực đạt yêu cầu đặc quyền do identity/FE-01 cung cấp, không tự triển khai luồng login/MFA riêng.
- **FR-002**: Staff và Manager có quyền tương ứng MUST được quản lí sản phẩm, ảnh, giá đề xuất, số lượng, trạng thái và xóa. Mã vai trò được chốt là MEMBER, STAFF, MANAGER, ADMIN; MANAGER là vai trò riêng, không tự ánh xạ OWNER. Admin tạo/cấp quyền tài khoản nội bộ qua FE-01; đăng ký khách không tự cấp quyền Staff/Manager/Admin. Chỉ Manager có quyền quyết định duyệt sản phẩm/giá; Manager MUST được tự duyệt sản phẩm/đề xuất của mình. Danh mục theo FR-029 dành cho Staff có quyền. Vai trò kỹ thuật Admin không tự cấp quyền quyết định nghiệp vụ.
- **FR-003**: Người quản lí MUST xem danh sách/chi tiết và tìm theo mã sản phẩm, tên hoặc SKU, lọc theo danh mục/trạng thái công khai. Kết quả SKU MUST chỉ rõ size khớp; giao diện MUST phân biệt hồ sơ, đề xuất giá, giá đã duyệt, giá đang áp dụng và công khai.
- **FR-004**: Tên MUST dài 1–200 ký tự, mô tả 1–5.000 ký tự sau bỏ khoảng trắng đầu/cuối; sản phẩm MUST có một danh mục hoạt động lúc tạo/chọn danh mục mới. Giá đề xuất/giá cuối cùng MUST là số nguyên VND dương, số lượng MUST là số nguyên không âm.
- **FR-005**: Khi tạo, người dùng MUST chọn 1–25 size nguyên khác nhau trong 5–29, mỗi size một biến thể; không bắt buộc đủ 25 size. Sau lưu, hệ thống MUST từ chối thêm/đổi size, kể cả nháp/ẩn/chưa có giao dịch. Không có lựa chọn khắc hoặc biến thể khách tự tạo.
- **FR-006**: Hệ thống MUST cấp mã sản phẩm số duy nhất, ổn định và SKU `SP{mã sản phẩm}-S{size}`. Mã sản phẩm đệm 0 bên trái tới tối thiểu 6 chữ số, dài hơn giữ nguyên; size 2 chữ số `05`–`29`. SKU MUST chỉ đọc, duy nhất, không đổi khi sửa tên/danh mục/giá/trạng thái, không tái cấp sau xóa. Nhiều chiếc cùng sản phẩm/size dùng chung SKU và được biểu diễn bằng số lượng.
- **FR-007**: Tạo hợp lệ MUST lưu toàn bộ sản phẩm, size, giá đề xuất, số lượng ở trạng thái Chưa công khai, hồ sơ Nháp; không tự duyệt/áp dụng/công khai. Size tạo mới mặc định được phép bán nhưng chưa mua được trước khi sản phẩm đủ điều kiện công khai. Bản nháp được chưa có ảnh. Tạo/lưu lỗi MUST không sinh sản phẩm hoặc biến thể thiếu dữ liệu một phần.
- **FR-008**: Quản lí ảnh MUST dùng Cloudinary, hỗ trợ thêm/thay/đổi thứ tự/gỡ, tối đa 10 JPEG/PNG/WebP, mỗi ảnh không quá 5.000.000 bytes, alt text 1–200 ký tự, vị trí không trùng. MUST kiểm tra giới hạn, tham chiếu hợp lệ và quyền; lỗi ảnh giữ ảnh cũ, không lưu liên kết hỏng hoặc xóa ảnh dùng chung/lịch sử. Ảnh chưa từng gắn đủ 24 giờ và không còn sử dụng được dọn; ảnh đã gỡ đủ 30 ngày chỉ được dọn nếu không còn sản phẩm/hồ sơ/đơn/lịch sử tham chiếu. Ảnh lịch sử giữ theo bản ghi liên quan; không dùng xóa mềm sản phẩm để bỏ qua điều kiện này.

#### Hồ sơ duyệt, quyết định giá và công khai

- **FR-009**: Gửi duyệt mới MUST kiểm tra phiên bản, danh mục hoạt động, thông tin, ít nhất một ảnh dùng được và giá đề xuất mọi size; lưu một hồ sơ Chờ duyệt gắn các đề xuất đúng đối tượng/điều khoản, không bắt gửi hai lần cùng nội dung.
- **FR-010**: Quyết định MUST chỉ áp dụng cho hồ sơ Chờ duyệt còn hiệu lực. Manager MUST xem thông tin và giá từng size, chấp nhận/điều chỉnh giá cuối cùng hoặc từ chối có lý do bắt buộc 1–2.000 ký tự sau trim; lưu người/thời điểm, phiên bản và đầu vào quyết định. Hủy trước xác nhận không tạo quyết định.
- **FR-011**: Trước lần công khai đầu, sửa nội dung, ảnh hoặc giá đề xuất đã gửi/duyệt MUST làm phiên bản đó không còn hiệu lực cho công khai, yêu cầu sửa/gửi duyệt lại; không sửa quyết định lịch sử. Hồ sơ Bị từ chối được sửa/gửi phiên bản mới, giá bị từ chối không được áp dụng.
- **FR-012**: Sau khi đã từng công khai, tên, mô tả, ảnh, danh mục MUST được cập nhật trực tiếp khi hợp lệ, kể cả đang ẩn, không duyệt lại nội dung. Nếu đang công khai, MUST duy trì điều kiện công khai; size cố định, giá vẫn theo FR-013–FR-016.
- **FR-013**: Staff/Manager có quyền MUST lập giá đề xuất từng size và lưu Nháp/Gửi duyệt. MUST giữ mã đề xuất, phiên bản, sản phẩm/size, điều khoản, số tiền, người gửi, trạng thái riêng; không dùng giá đề xuất làm giá bán. Mỗi size MUST chỉ có một phiên bản đề xuất Chờ duyệt hiện hành, kể cả đề xuất gửi cùng hồ sơ sản phẩm mới. Khi đã có đề xuất Chờ duyệt, MUST chặn tạo/gửi đề xuất độc lập thứ hai cho cùng size; người có quyền sửa đề xuất hiện hành thành phiên bản mới rồi gửi lại, làm phiên bản cũ mất hiệu lực. Yêu cầu đồng thời MUST không tạo hai phiên bản Chờ duyệt hiện hành cho cùng size.
- **FR-014**: Manager MUST quyết định giá cuối cùng hợp lệ, chấp nhận/điều chỉnh hoặc từ chối có lý do, kể cả đề xuất của mình. Gợi ý AI thuộc FE-04 chỉ tham khảo; AI không quyết định và không có gợi ý không chặn quyết định thủ công hợp lệ. Sửa đề xuất cần phiên bản mới, không kế thừa duyệt cũ.
- **FR-015**: Áp dụng giá MUST là thao tác riêng của Staff/Manager có quyền, chỉ chấp nhận giá đã duyệt còn hiệu lực, đúng size/điều khoản và đúng nguyên số tiền. Với sản phẩm mới, hồ sơ cũng phải đã duyệt còn hiệu lực. Không cho yêu cầu cũ ghi đè giá mới hơn hoặc sửa số tiền lúc áp dụng.
- **FR-016**: Áp dụng MUST ghi giá trước/sau, quyết định và người/thời điểm áp dụng. Giá mới chỉ dùng cho xem/checkout mới khi đủ điều kiện; không đổi giá/thời hạn của đơn hoặc quyền giữ hợp lệ đã chấp nhận. Lưu/gửi/duyệt/áp dụng MUST không tự công khai hoặc làm mất giá đang áp dụng hợp lệ khi chờ duyệt giá mới.
- **FR-017**: Công khai MUST là thao tác riêng, yêu cầu quyền hiện tại, chưa xóa, hồ sơ mới đã duyệt còn hiệu lực, danh mục hoạt động, nội dung hợp lệ, ít nhất một ảnh dùng được và tất cả size có giá đã duyệt/áp dụng hợp lệ. Số lượng 0 không tự chặn. Giá đề xuất/đã duyệt chưa áp dụng không đạt điều kiện.
- **FR-018**: Ngừng công khai MUST chặn đọc công khai/giữ mới nhưng giữ trong quản trị. Công khai lại chỉ khi đủ điều kiện, không coi sản phẩm đã từng công khai là mới chỉ vì đang ẩn. Gửi lặp cùng trạng thái MUST không tạo thêm chuyển trạng thái.

#### Số lượng, khả năng mua và xóa

- **FR-019**: Mỗi size MUST dùng lượng còn cho phép bán chung toàn hệ thống, không chia chi nhánh. Staff/Manager có quyền cập nhật thủ công; số mới MUST không nhỏ hơn phần đang giữ hợp lệ. Số khả dụng bằng lượng còn lại trừ giữ hợp lệ, xét dữ liệu giữ mới nhất khi lưu.
- **FR-020**: Một biến thể MUST chỉ Có thể mua khi lượng khả dụng lớn hơn 0, đang bán, sản phẩm chưa xóa/đang công khai, danh mục hoạt động và điều kiện nội dung/ảnh/giá duyệt đã áp dụng hợp lệ. Sản phẩm Có thể mua khi ít nhất một size thỏa tất cả; chỉ còn số lượng là chưa đủ.
- **FR-021**: Staff/Manager có quyền MUST ngừng bán/mở bán lại size đã có độc lập với cờ công khai. Ngừng bán MUST giữ số lượng, hiện nhãn và chặn thêm giỏ/giữ mới size đó, không ảnh hưởng size khác. Mở bán lại MUST kiểm tra dữ liệu/giá, không tự công khai.
- **FR-022**: Xóa MUST là thao tác xác nhận riêng của người có quyền, áp dụng mọi trạng thái chưa xóa; hủy không thay đổi dữ liệu. MUST xóa mềm, ghi người/thời điểm và loại khỏi catalogue/danh sách quản trị thông thường. Không xóa vật lí hoặc khôi phục trong feature này.
- **FR-023**: Đã xóa MUST không được sửa, gửi/duyệt hồ sơ, áp dụng giá, công khai, mở bán lại, thêm giỏ/giữ mới kể cả qua mã/trang cũ. Hồ sơ/đề xuất chưa áp dụng không được tiếp tục duyệt/áp dụng; giữ SKU và quyết định lịch sử.
- **FR-024**: Ẩn/ngừng bán/đổi giá/xóa MUST không hủy giữ hợp lệ, sửa điều khoản/giá/thời hạn đơn đã chấp nhận, ngăn xác nhận thanh toán hợp lệ của giao dịch cũ, tự trừ/hoàn số lượng hoặc xóa lịch sử/ảnh cần thiết. FE-08 chốt lượng mua đúng một lần khi giao dịch được chấp nhận và giải phóng giữ khi hết hạn theo quy trình của nó.
- **FR-025**: Yêu cầu đồng thời với xóa MUST không làm sống lại sản phẩm hoặc tạo giữ mới sau xóa có hiệu lực. Gửi lặp xóa thành công MUST chỉ trả trạng thái theo quyền, không xóa thêm/điều chỉnh số lượng.

#### Tính nhất quán và danh mục

- **FR-026**: Mọi thay đổi quản trị MUST kiểm tra dữ liệu mới nhất và từ chối ghi đè dữ liệu cũ, cho xem lại/xác nhận lại; không ghi một phần sản phẩm, ảnh, quyết định, giá hoặc trạng thái khi lưu/audit thất bại.
- **FR-027**: Mỗi lần tạo/sửa/gửi duyệt/quyết định/áp dụng/công khai/ẩn/ngừng bán/xóa/quản lí danh mục MUST có dấu vết người, đối tượng/phiên bản, thay đổi, thời điểm và kết quả thực; thao tác bị từ chối ghi theo chính sách audit. Business audit MUST giữ 5 năm từ thời điểm sự kiện, kế thừa Report 3. Không hiển thị/ghi bí mật xác thực hoặc thông tin nhạy cảm không cần thiết.
- **FR-028**: Mất phản hồi MUST cho kiểm tra trạng thái đã lưu trước thử lại, không coi là chắc chắn thất bại. Thử lại cùng yêu cầu MUST không tạo trùng sản phẩm, SKU, hồ sơ, quyết định, áp dụng hoặc chuyển trạng thái; lỗi tải MUST khác dữ liệu rỗng. Bản ghi kết quả thao tác tối thiểu MUST giữ 5 năm từ khi commit, không chứa toàn bộ request/token; hết thời hạn này không cho replay dữ liệu cũ để hồi sinh sản phẩm hoặc giao dịch.
- **FR-029**: Staff có quyền MUST tạo/sửa danh mục với tên bắt buộc 1–200 ký tự và mô tả tùy chọn tối đa 5.000 ký tự sau trim, kích hoạt/ngừng kích hoạt; kiểm tra tài khoản, dữ liệu, phiên bản và lưu audit. Tên MUST duy nhất trên toàn hệ thống, kể cả danh mục đã ngừng hoạt động, sau khi bỏ khoảng trắng đầu/cuối và so khớp không phân biệt hoa/thường; từ chối tên trùng khi tạo hoặc đổi tên. Danh mục mới mặc định hoạt động.
- **FR-030**: Trước ngừng kích hoạt danh mục MUST cho xem sản phẩm bị ảnh hưởng. Danh mục không hoạt động và sản phẩm liên quan MUST bị loại khỏi công khai/mua mới; kích hoạt lại chỉ khôi phục hiển thị sản phẩm còn công khai, chưa xóa, đủ điều kiện. Danh mục đã tham chiếu dùng ngừng kích hoạt thay xóa, không viết lại lịch sử/giữ hợp lệ.

#### Catalogue công khai

- **FR-031**: Catalogue/chi tiết MUST chỉ trả sản phẩm công khai chưa xóa, danh mục hoạt động, đủ nội dung/ảnh/giá; không lộ nháp, hồ sơ/giá nội bộ hoặc dữ liệu giữ. Kiểm tra lại mỗi lượt đọc, trang cũ không cấp quyền mua.
- **FR-032**: Tìm kiếm MUST trim đầu/cuối, khớp chuỗi con không phân biệt hoa/thường trong tên/mô tả; danh mục, giá bao gồm hai đầu mút và Có thể mua MUST kết hợp cùng thỏa mãn. MUST báo lỗi tiêu chí không hỗ trợ, giá âm, min lớn hơn max hoặc danh mục không hợp lệ.
- **FR-033**: Có thể mua MUST chỉ trả sản phẩm có ít nhất một size thỏa FR-020. Lọc giá cùng Có thể mua MUST có cùng một size thỏa cả hai, không ghép giá size ngừng bán với khả năng mua size khác.
- **FR-034**: Hiển thị MUST có tên/tóm tắt, danh mục, ảnh, khoảng giá VND đang áp dụng hoặc một giá nếu bằng nhau; chọn size cho giá/khả năng mua chính xác. Size ngừng bán vẫn hiện nhãn, không chọn mua; hết hàng không tự mất hiển thị khi không bật Có thể mua.
- **FR-035**: MUST mặc định tên A–Z rồi định danh; hỗ trợ giá tăng/giảm và mới nhất. Sắp theo giá MUST dùng giá nhỏ nhất của size đang hiển thị/thỏa bộ lọc cho cả hai chiều; bằng nhau dùng định danh phụ. MUST giữ từ khóa/bộ lọc, giới hạn từ khóa 200 ký tự sau trim và phân trang 20 bản ghi/trang. Mới nhất MUST theo thời điểm tạo, không theo sửa giá hoặc công khai lại.
- **FR-036**: Mất toàn bộ ảnh dùng được MUST loại khỏi đọc công khai không đổi cờ công khai/lịch sử; khi ảnh và điều kiện khác khôi phục thì hiện lại. Lỗi ảnh/tải tạm thời MUST có hướng dẫn an toàn/thử lại, khác catalogue rỗng hoặc không khớp.
- **FR-037**: Khách MUST chỉ chọn size đã cấu hình, được chuyển sản phẩm/biến thể/số lượng sang FE-08 khi chọn mua. FE-02 MUST không tạo giỏ, đơn, thanh toán, giữ số lượng, voucher hoặc điểm từ tra cứu; FE-08 kiểm tra đăng nhập/điều kiện và xác nhận thay đổi giá trước giao dịch mới.
- **FR-038**: Các hành trình cốt lõi xem/tìm/lọc và tạo/sửa/gửi duyệt/quyết định/công khai/ẩn/xóa MUST hoàn thành được từ frontend React + Vite tách riêng chỉ qua REST API có version; backend MUST NOT render UI. Mỗi màn hình MUST có trạng thái loading, empty, validation, success, failure và recovery xác định; không mở rộng ngoại lệ xác thực của dự án sang FE-02.

### Key Entities *(include if feature involves data)*

- **Product**: Mẫu nhẫn bán sẵn, mã số ổn định, tên/mô tả, danh mục, ảnh, cờ công khai, dấu đã từng công khai và xóa mềm; không cấu hình khắc.
- **Product Size Variant**: Một sản phẩm tại size 5–29, SKU ổn định, giá áp dụng, lượng còn cho phép bán chung và đang bán/ngừng bán; danh sách size cố định sau tạo.
- **Category**: Tên/mô tả, trạng thái hoạt động, ảnh hưởng hiển thị nhưng không sửa lịch sử giao dịch.
- **Product Media**: Ảnh, alt text, vị trí duy nhất trong sản phẩm; giữ quan hệ sử dụng còn cần cho dữ liệu chung/lịch sử.
- **Product Review**: Phiên bản hồ sơ, trạng thái Nháp/Chờ duyệt/Đã duyệt/Bị từ chối, hiệu lực, người gửi/quyết định, lý do, thời điểm; không viết lại phiên bản lịch sử.
- **Price Proposal and Decision**: Sản phẩm/size, điều khoản/phiên bản, giá đề xuất/Manager quyết định, trạng thái, lý do/đầu vào, người/thời điểm; áp dụng tách khỏi duyệt.
- **Price Application**: Liên kết quyết định còn hiệu lực với giá bán, giá trước/sau, người/thời điểm áp dụng; không đổi giá giao dịch cũ.
- **Availability Context**: Giữ hợp lệ do FE-08 cung cấp cùng lượng còn bán để xác định khả dụng; FE-02 không tạo vòng đời giữ riêng.
- **Catalogue Audit**: Người, thao tác, đối tượng/phiên bản, thay đổi, thời điểm/kết quả, không chứa bí mật xác thực hoặc dữ liệu thừa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% kịch bản tạo–gửi–duyệt–áp dụng–công khai hợp lệ hoàn thành được; 0 sản phẩm mới xuất hiện trước duyệt/áp dụng đầy đủ, bao gồm Manager tự duyệt và sản phẩm hết hàng.
- **SC-002**: Trong tập nghiệm thu quyền, dữ liệu cũ và xóa đồng thời, 100% yêu cầu không hợp lệ bị từ chối an toàn; 0 quyết định sai phiên bản, sản phẩm sống lại hoặc giữ mới sau xóa.
- **SC-003**: Với 25 size 5–29 và mã sản phẩm dưới/trên 6 chữ số, 100% SKU đúng định dạng/duy nhất; 0 yêu cầu thêm/đổi size, khắc hoặc sửa SKU sau lưu được chấp nhận.
- **SC-004**: 100% kịch bản đổi giá/ẩn/ngừng bán/đổi danh mục/xóa giữ nguyên giá, thời hạn và định danh giao dịch hợp lệ đã chấp nhận; gửi lại cùng yêu cầu không tạo trùng hoặc điều chỉnh lượng lần hai.
- **SC-005**: Trong tập size hết hàng/ngừng bán/nhiều giá, 100% kết quả lọc giá/Có thể mua có một size thỏa cả hai; 0 giá đề xuất hoặc nội dung nháp lộ công khai.
- **SC-006**: Mọi nhóm biên nhập liệu/ảnh/hồ sơ/danh mục tại US1–US8 có kết quả xác định, giữ dữ liệu cũ khi lỗi và cho tiếp tục sửa/thử lại; 100% hành trình cốt lõi hoàn thành được từ frontend React qua REST với loading/empty/validation/success/failure/recovery xác định.
- **SC-007**: Mỗi FR-001–FR-038 được ánh xạ ít nhất một kịch bản nghiệm thu trong checklist chất lượng. Các chỉ số trên là tiêu chí cần xác minh khi triển khai, không phải tuyên bố đã vượt kiểm thử.

## Assumptions

### Phạm vi và quyết định nguồn

- Sở hữu nhẫn bán sẵn, quản trị catalogue và tra cứu công khai. Không triển khai kho/nhập-xuất/kiểm kê/điều chuyển, tồn theo chi nhánh, booking, thiết kế cá nhân hóa/custom, đơn hàng, checkout, thanh toán, giao nhận, voucher hoặc điểm.
- Mua ngay/Thêm giỏ chỉ chuyển sang FE-08; FE-08 sở hữu giỏ, quyền Member, checkout, giữ/chốt/giải phóng lượng và xử lí giao dịch cũ sau xóa. Không đặt thời hạn giữ hoặc quy tắc thanh toán mới trong FE-02.
- Bảng đường kính/chu vi tham chiếu lấy nguyên từ ảnh người dùng cung cấp tại [FE-02 research](../../research.md#fe-02); chọn bằng mã size nguyên, không tính lại số đo để tạo size.
- Sửa trực tiếp sau công khai, Manager tự duyệt, không thêm size/khắc và xóa mềm không khôi phục là các quyết định đã chốt trong phiên; tài liệu Draft không làm mất các quyết định đó, cũng không phê duyệt code/plan chưa có.

### Các mặc định đã được người dùng chốt ngày 2026-10-24

- **A-001 — Danh mục**: Unique toàn hệ thống sau trim/case-insensitive kể cả inactive, tên 1–200 ký tự, mô tả tùy chọn tối đa 5.000, danh mục mới active=true. Các quy tắc này đã được chốt trong Clarifications; không có xóa danh mục đã tham chiếu.
- **A-002 — Tìm kiếm/sắp xếp**: Mới nhất theo thời điểm tạo, không theo sửa giá/công khai lại; từ khóa tối đa 200 ký tự sau trim; danh sách công khai/quản trị 20 bản ghi/trang với thứ tự xác định. Đã chốt, không bổ sung bộ lọc/sắp xếp khác.
- Giới hạn giá/số lượng bổ sung ngoài nguyên VND dương/số lượng nguyên không âm chưa phê chuẩn. Không tự đặt trần kinh doanh; miền số biểu diễn, thông báo vượt miền và cách đo ký tự cần ghi rõ tại plan/contracts trước triển khai, không làm tròn/cắt/tràn âm thầm.

### Phụ thuộc và ranh giới lập kế hoạch

- Tuân thủ [Constitution 3.0.0](../../.specify/memory/constitution.md). Đây là hành vi nghiệp vụ; kiến trúc, hợp đồng giao tiếp, dữ liệu vật lí và cách bảo đảm xử lí đồng thời thuộc bước plan.
- Cần danh tính, quyền nội bộ và trạng thái tài khoản đã xác minh. Mã MEMBER/STAFF/MANAGER/ADMIN, dùng chung cơ chế xác thực theo 001 và phiên nội bộ cố định 8 giờ đã chốt. Chi tiết model tài khoản, facade phiên/quyền và phương thức MFA/recovery thuộc identity/FE-01; không tự map OWNER, tự chọn TOTP hoặc bỏ yêu cầu xác thực đặc quyền. Dependency thiết kế/triển khai MFA chung cần hoàn thiện trước bật quản trị; không phải câu hỏi mở lại quyết định dùng chung cơ chế của FE-02.
- Cloudinary đã được chọn; adapter/cấu hình và thông tin giữ hợp lệ từ FE-08 là dependency triển khai. Retention ảnh/audit/receipt đã chốt ở FR-008/027/028. Không kích hoạt billing hoặc tạo tài nguyên provider từ tác vụ tài liệu. Không phụ thuộc FE-07 để hoàn thành duyệt trong quản trị.
- FE-04 có thể cho gợi ý giá nhưng không bắt buộc để duyệt thủ công; không sửa spec FE-04 hoặc Member Profile trong tác vụ này.
- Tải, tốc độ, khả dụng và môi trường sản xuất chưa baselined, không suy ra từ preview. Sẵn sàng review/lập plan không đồng nghĩa các phụ thuộc hoặc vận hành đã sẵn sàng triển khai.
