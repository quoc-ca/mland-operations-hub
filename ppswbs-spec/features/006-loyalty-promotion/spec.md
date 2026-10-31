# Feature Specification: Loyalty & Promotion Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-30
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Feature**: `006-loyalty-promotion` — FE-06  
**Feature Branch**: Không tạo hoặc chuyển nhánh; dự án không cấu hình hook tạo nhánh.  
**Status**: Approved — duyệt bởi Đặng Thị Khương ngày 2026-10-24, duyệt lại ngày 2026-10-26 sau các thay đổi: clarification 2026-10-26 (giảm tự động luôn kết hợp điểm; phạm vi sản phẩm ẩn/xóa; phạm vi size là SKU cụ thể và phải thuộc sản phẩm được chọn) và quyết định cọc workshop B (`deposit = ceil(post_promotion_amount / 2)`); đặc tả nghiệp vụ là baseline cho triển khai FE-06.  
**Input**: Dùng `$speckit-specify` để đặc tả FE-06 trong [research.md](../../research.md#fe-06), theo bộ thông số điểm và các quyết định 1–10 người dùng đã chốt ngày 2026-10-24.

Nguồn chính là FE-06 hiện tại và quyết định trực tiếp trong hội thoại. Feature gồm voucher sản phẩm, voucher workshop, giảm tự động sản phẩm và điểm dùng chung; không có hạng thành viên. Không dùng report/screen cũ để khôi phục phạm vi đã bỏ hoặc thay đổi quyết định đã chốt.

## Clarifications

### Session 2026-10-24

- Q: Ai cấu hình chính sách điểm (tỷ lệ tích điểm, giá trị quy đổi, số điểm tối thiểu, trần giảm bằng điểm)? → A: Manager. Admin chỉ quản lí tài khoản và cấp quyền, không xem/sửa chính sách điểm hay chương trình ưu đãi (chốt ngày 2026-10-24).

- Q: Manager có được mở lại chương trình ưu đãi đã tạm dừng để tiếp tục nhận giao dịch mới không? → A: Có, mở lại cùng chương trình/mã, giữ nguyên lịch sử và lượt đã dùng/đang giữ; giao dịch mới vẫn kiểm tra thời gian, phạm vi và hạn mức hiện hành. Mở lại không tự gia hạn hoặc đặt lại lượt.
- Q: Khi booking workshop đã dùng voucher bị hủy hợp lệ, báo cáo lượt đã dùng và tổng tiền đã giảm có loại booking đó khỏi số liệu tổng không? → A: Có, loại booking đã hủy hợp lệ khỏi lượt đã dùng và tổng tiền giảm hiện tại sau khi ghi nhận kết quả xử lí hủy; giữ nguyên lịch sử dùng và hoàn để tra cứu. Lượt đang giữ không tính là đã dùng; gửi lặp kết quả hủy không trừ số liệu thêm lần nữa.

### Session 2026-10-26

- Q: Giảm tự động sản phẩm có được kết hợp điểm không? → A: Luôn được kết hợp điểm; Manager không có lựa chọn tắt cho loại này. Lựa chọn cho/không kết hợp điểm chỉ có ở voucher. Quy tắc chọn giảm tự động theo khoản giảm lớn nhất giữ nguyên.
- Q: Phạm vi chương trình có được chọn sản phẩm chưa công khai/đang ẩn/đã xóa, và xử lí thế nào khi sản phẩm bị ẩn/xóa sau khi tạo? → A: Được chọn sản phẩm/size chưa xóa mềm (kể cả chưa công khai, đang ẩn, ngừng bán); sản phẩm đã xóa mềm bị từ chối. Sau khi tạo, sản phẩm bị ẩn/ngừng bán/xóa thì chương trình không tự dừng, dòng đó không đủ điều kiện; thẻ công khai chỉ liệt kê sản phẩm đang công khai và ẩn chương trình khi không còn sản phẩm công khai nào; Kho Voucher hiện trạng thái "Hiện không có sản phẩm áp dụng"; Manager được cảnh báo.
- Q: Phạm vi theo "size" là con số size trong bảng size (áp cho mọi nhẫn cùng size) hay một size cụ thể của một sản phẩm? Khi chọn cả sản phẩm và size thì size phải thuộc sản phẩm nào? → A: Size trong phạm vi là **một size cụ thể của một sản phẩm cụ thể (một SKU, ví dụ `SP000001-S09`)**, không phải con số size dùng chung cho mọi nhẫn; bảng size 5–29 cố định không đổi. Phạm vi sản phẩm và phạm vi size kết hợp bằng AND: nếu có chọn sản phẩm thì mỗi size được chọn phải thuộc một trong các sản phẩm đó, nếu không thì từ chối cấu hình; nếu chỉ chọn size (không chọn sản phẩm) thì mọi size hợp lệ. Không có loại phạm vi "mọi nhẫn cùng con số size".

### Session 2026-10-29

- Q: Gói workshop có nhiều phiên (ví dụ Silverclay 2 phiên, một phí cho cả gói) thì voucher giới hạn chi nhánh/ngày/phiên kiểm tra phiên nào? → A: **Mọi phiên khách đã chọn phải khớp** các giới hạn chi nhánh, ngày tham gia và phiên của voucher (nếu có); chỉ cần một phiên nằm ngoài phạm vi thì voucher bị từ chối kèm lý do. Gói phải nằm trong phạm vi gói của voucher (nếu có). Trần giảm vẫn áp một lần cho cả booking.
- Q: Phạm vi "phiên" của voucher dùng mã nào? → A: Dùng mã tham chiếu của phiên do Workshop Operations cấp (`workshop_sessions.reference`, UUID chữ thường), không dùng số thứ tự nội bộ. Gói và chi nhánh vẫn là ID số, ngày tham gia là `YYYY-MM-DD`.

### Session 2026-10-30

- Q: Member hủy đơn nhẫn **trước khi thanh toán** (đơn đã chấp nhận, voucher/điểm đang giữ) thì xử lý thế nào? → A: Giống hết hạn chưa thanh toán: giải phóng voucher/điểm đang giữ đúng một lần (trả điểm khả dụng và lượt giữ của Member và tổng chương trình), không cộng điểm, không ghi đã dùng. FE-08 chỉ báo khi đã xác nhận dưới khóa đơn rằng không có thanh toán đang xử lý hoặc đã ghi nhận. Dùng loại kết quả riêng `RETAIL_CANCELLED_BEFORE_PAYMENT` để lịch sử phân biệt với hết hạn. Đơn đã thanh toán không hủy theo đường này (trả hàng/hoàn tiền nằm ngoài phạm vi).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tạo chương trình ưu đãi (Priority: P1)

Là Manager, tôi muốn tạo voucher riêng cho sản phẩm hoặc workshop và giảm tự động cho sản phẩm để chủ động mở chương trình phù hợp.

**Why this priority**: Shop cần cấu hình được ưu đãi trước khi khách sử dụng.

**Independent Test**: Với sản phẩm/biến thể và gói/lịch workshop hợp lệ, tạo từng loại chương trình và đối chiếu điều kiện được lưu, chưa cần thanh toán thực tế.

**Acceptance Scenarios**:

1. **Given** Manager có quyền và phạm vi sản phẩm/biến thể hợp lệ, **When** tạo voucher với tên, mã, thời gian, kiểu/mức giảm, mức giảm tối đa, giá trị tối thiểu, tổng lượt, lượt mỗi Member và lựa chọn kết hợp điểm, **Then** lưu đủ điều kiện; voucher chỉ dùng cho sản phẩm và không giới hạn chi nhánh.
2. **Given** gói/lịch workshop hợp lệ, **When** tạo voucher với gói, chi nhánh, ngày/phiên, khoảng thời gian được đặt bằng mã, kiểu/mức giảm, trần giảm, phí gói tối thiểu, hạn mức và kết hợp điểm, **Then** lưu riêng cho workshop; thời gian đặt bằng mã phân biệt với ngày tham gia.
3. **Given** Manager tạo giảm tự động sản phẩm, **When** lưu phạm vi, thời gian, mức giảm và điều kiện hợp lệ, **Then** không cần mã nhập, không giới hạn chi nhánh và luôn cho kết hợp điểm (Manager không tắt được); không tạo giảm tự động workshop.
4. **Given** mã `TET10` đã được cấp, kể cả chương trình hết hạn/bị dừng, **When** tạo ` tet10 ` hoặc cấp lại cho phạm vi khác, **Then** từ chối sau khi bỏ khoảng trắng đầu/cuối và so sánh không phân biệt hoa/thường.
5. **Given** thiếu dữ liệu, bắt đầu không trước kết thúc, mức giảm tiền/trần không nguyên dương, tỷ lệ phần trăm ngoài khoảng lớn hơn 0 đến 100%, ngưỡng tiền âm/không nguyên, lượt không nguyên dương hoặc phạm vi không hợp lệ (gồm sản phẩm/biến thể không tồn tại, biến thể không thuộc sản phẩm hoặc sản phẩm đã xóa mềm; sản phẩm chưa công khai/đang ẩn/ngừng bán vẫn được chọn), **When** lưu, **Then** nêu lỗi tương ứng và không lưu chương trình thiếu điều kiện.
6. **Given** Guest, Member, Staff hoặc tài khoản không có quyền Manager hiện hành, **When** tạo/sửa/tạm dừng/mở lại chương trình, **Then** từ chối; biết mã hoặc liên kết không cấp thêm quyền.
7. **Given** chương trình ngày lễ, **When** tạo, **Then** vẫn thuộc sản phẩm hoặc workshop với thời gian riêng, không thành lớp voucher cộng thêm.
8. **Given** lưu lỗi hoặc mất phản hồi, **When** kiểm tra lại trước khi gửi lại, **Then** chỉ báo thành công khi có kết quả xác nhận và không tạo trùng từ thao tác đã ghi nhận.

### User Story 2 - Sửa, tạm dừng, mở lại và theo dõi chương trình (Priority: P1)

Là Manager, tôi muốn theo dõi sử dụng và sửa chương trình cho giao dịch mới, đồng thời giữ quyền lợi đã cam kết.

**Why this priority**: Shop cần kiểm soát ưu đãi mà không thay đổi giao dịch đã chấp nhận.

**Independent Test**: Dùng chương trình có lượt đã dùng/đang giữ, thử sửa hạn mức, tạm dừng, mở lại và tiếp tục giao dịch cũ.

**Acceptance Scenarios**:

1. **Given** Manager lọc danh sách theo sản phẩm/workshop và mở chi tiết, **When** xem, **Then** thấy đúng điều kiện, thời gian, tổng hạn mức, lượt đã dùng và tiền đã giảm hiện tại; không tính lượt đang giữ hoặc booking đã ghi nhận hủy hợp lệ vào lượt đã dùng/tổng tiền giảm, nhưng vẫn giữ lịch sử dùng và hoàn để tra cứu.
2. **Given** chương trình đã tạo, **When** đổi mã hoặc phạm vi sản phẩm/workshop, **Then** từ chối; sửa điều kiện được phép chỉ áp dụng cho giao dịch được chấp nhận sau đó.
3. **Given** đã dùng 6 lượt và đang giữ 2, **When** giảm tổng hạn mức xuống 7, **Then** từ chối; mức 8 hợp lệ nếu số liệu không đổi. Kiểm tra tương tự với hạn mức mỗi Member.
4. **Given** đơn/booking đã chấp nhận và còn thời hạn gốc, **When** chương trình sửa/tạm dừng/hết hạn, **Then** giữ khoản giảm, điểm dùng và chính sách đã ghi nhận; không hủy/gia hạn phần giữ hoặc tính lại giao dịch cũ.
5. **Given** mã đã dừng/hết hạn nhưng còn trong Kho Voucher, **When** dùng cho giao dịch mới, **Then** từ chối và nêu lý do.
6. **Given** số lượt thay đổi trong lúc Manager sửa, **When** lưu sẽ khiến hạn mức thấp hơn đã dùng cộng đang giữ hiện tại, **Then** từ chối, không xóa phần giữ để ép lưu.
7. **Given** sửa/tạm dừng/mở lại lỗi hoặc mất phản hồi, **When** tải lại, **Then** hiển thị kết quả xác nhận, không báo thành công chỉ từ thao tác bấm nút.
8. **Given** chương trình đang tạm dừng, **When** Manager có quyền mở lại, **Then** dùng lại chính chương trình/mã, giữ nguyên lịch sử, lượt đã dùng/đang giữ và điều kiện đã cấu hình. Giao dịch mới chỉ dùng được nếu còn hiệu lực, đúng phạm vi và đủ hạn mức; mở lại không tự gia hạn, reset lượt hoặc thay đổi quyền lợi của giao dịch cũ. Gửi lặp cùng thao tác không tạo thêm lần chuyển trạng thái.
9. **Given** chương trình có một booking đã chốt voucher giảm 100.000đ và chưa có giao dịch khác, **When** kết quả hủy hợp lệ của booking được xử lí thành công, **Then** báo cáo hiện tại còn 0 lượt đã dùng và 0đ tiền giảm; lịch sử dùng/hoàn gốc vẫn tra cứu được. Nhận lại cùng kết quả hủy không làm số liệu âm; việc chương trình tự hết hạn hoặc bị tạm dừng không loại các giao dịch đã chốt khác chưa bị hủy khỏi báo cáo.

### User Story 3 - Xem và lưu voucher (Priority: P2)

Là khách, tôi muốn hiểu điều kiện; khi là Member, tôi muốn lưu mã để tìm nhanh lúc mua sản phẩm hoặc đặt workshop.

**Why this priority**: Kho giúp tìm quyền lợi nhưng không bắt buộc trước khi nhập mã trực tiếp.

**Independent Test**: Xem thẻ/chi tiết hai phạm vi, đăng nhập để lưu rồi mở kho, chưa tạo đơn/booking.

**Acceptance Scenarios**:

1. **Given** ưu đãi tại trang sản phẩm, gói workshop hoặc trang ưu đãi, **When** đọc thẻ, **Then** thấy phạm vi Mua sản phẩm/Đặt workshop, mức giảm, trần giảm, giá trị tối thiểu, thời hạn và có cho kết hợp điểm không.
2. **Given** mở điều kiện, **When** đọc, **Then** thấy sản phẩm/biến thể hoặc gói, chi nhánh/ngày/phiên workshop nếu có, tổng hạn mức và lượt mỗi Member theo cấu hình. Thẻ/chi tiết công khai chỉ liệt kê sản phẩm trong phạm vi đang công khai; chương trình sản phẩm không còn sản phẩm nào công khai trong phạm vi không hiện ở danh sách ưu đãi công khai, còn trong Kho Voucher vẫn hiện kèm trạng thái "Hiện không có sản phẩm áp dụng".
3. **Given** Guest chọn Lưu, **When** chưa đăng nhập, **Then** được hướng dẫn đăng nhập; Member lưu thành công thì mã thuộc kho chính mình, lưu lặp không thêm quyền.
4. **Given** đã lưu mã, **When** xem kho/chọn ở checkout, **Then** thấy điều kiện hiện tại; lưu chưa giữ lượt/điểm, chưa tiêu hạn mức và không bảo đảm dùng được. Member vẫn nhập trực tiếp mà không lưu trước; nhập ` sp10 ` tra được cùng voucher `SP10` nếu mã đó tồn tại, rồi vẫn kiểm tra điều kiện giao dịch.
5. **Given** mã hết hạn/bị dừng/chưa đủ điều kiện, **When** xem/chọn, **Then** nêu tình trạng, không coi có trong kho là đủ điều kiện.
6. **Given** phiên hết hạn, yêu cầu đọc kho người khác hoặc lỗi tải/lưu, **When** tiếp tục, **Then** yêu cầu đăng nhập/thử lại hoặc từ chối theo quyền; không báo lưu thành công khi chưa xác nhận.

### User Story 4 - Dùng ưu đãi khi mua sản phẩm (Priority: P1)

Là Member, tôi muốn chọn một giảm tự động hoặc một voucher và chủ động dùng điểm để biết đúng số tiền phải trả.

**Why this priority**: Đây là hành trình sử dụng ưu đãi sản phẩm và kiểm soát khoản giảm.

**Independent Test**: Dùng giỏ mẫu, các chương trình và số dư điểm; đối chiếu phép tính, tạo đơn qua FE-08 và cung cấp kết quả thanh toán xác minh để chốt.

**Acceptance Scenarios**:

1. **Given** phần đủ điều kiện 600.000đ, dòng ngoài phạm vi 200.000đ và giao hàng 30.000đ, **When** dùng voucher giảm 10%, tối đa 50.000đ, ngưỡng 500.000đ, **Then** giảm 50.000đ; không tính dòng ngoài phạm vi/giao hàng vào điều kiện. Ngưỡng 700.000đ thì từ chối dù cả giỏ đạt 800.000đ.
2. **Given** phần đủ điều kiện có nhiều sản phẩm/đơn vị, **When** dùng mã cố định 50.000đ, **Then** chỉ giảm một lần và không vượt phần đủ điều kiện; phần đủ điều kiện 33.333đ giảm 7% thì giảm 2.333đ, không làm tròn lên.
3. **Given** nhiều giảm tự động hợp lệ, **When** tính, **Then** chọn khoản giảm lớn nhất, bằng nhau ưu tiên chương trình tạo trước. Chọn voucher thay thế thì bỏ giảm tự động, không cộng dồn.
4. **Given** sau voucher còn 750.000đ tiền hàng và Member có 200 điểm khả dụng, **When** dùng điểm, **Then** hiển thị tối đa 200 điểm, giảm 20.000đ; cộng giao hàng 30.000đ thì trả 760.000đ. Thanh toán đầy đủ được xác minh thì cơ sở tích là 730.000đ, được 73 điểm mới.
5. **Given** đủ điểm và được kết hợp, **When** giá trị sau ưu đãi là 9.900đ, **Then** tối đa 9 điểm nên không dùng được mức tối thiểu 10; giá trị 10.000đ thì dùng tối đa 10 điểm. Khách chọn không dùng hoặc số điểm nguyên từ mức tối thiểu tới tối đa.
6. **Given** voucher không cho kết hợp điểm (giảm tự động luôn cho kết hợp), **When** chọn, **Then** không cho dùng điểm và nêu lý do dù Member còn điểm.
7. **Given** đang thử mã/chọn điểm, **When** chưa xác nhận đơn, **Then** chưa giữ quyền lợi. Khi xác nhận, kiểm tra lại giá/điều kiện/số dư/hạn mức và chỉ chấp nhận đơn cùng phần giữ hợp lệ; thất bại không để giữ dở dang.
8. **Given** mã workshop hoặc mã sai/hết hạn/dừng/hết lượt/chưa đạt ngưỡng, **When** áp dụng, **Then** nêu lý do và cho chọn lại hoặc không dùng. Giá/điểm/hạn mức đổi trước chấp nhận thì cho xác nhận tổng mới, không tạo đơn khác tổng khách đã đồng ý.
9. **Given** FE-08 xác nhận thanh toán đầy đủ hoặc chấp nhận đơn 0đ hợp lệ, **When** chốt, **Then** chốt lượt/trừ điểm đúng một lần; đơn 0đ không chờ cổng thanh toán và không tích điểm từ phần đã giảm hết.
10. **Given** Staff điều phối nơi chuẩn bị hàng, **When** kiểm tra ưu đãi, **Then** không đổi khoản giảm vì chi nhánh. Biết mã giao dịch của người khác không cho dùng điểm/phần giữ của người đó.

### User Story 5 - Dùng ưu đãi khi đặt workshop (Priority: P1)

Là Member đặt trực tiếp website, tôi muốn dùng voucher workshop và điểm trước tính cọc, rồi trả phần còn lại theo số tiền đã xác nhận.

**Why this priority**: Workshop có nhiều lần thu nên cần tránh giảm/trừ điểm hai lần.

**Independent Test**: Dùng gói/lịch và chính sách cọc từ FE-03, đối chiếu phí sau giảm, cọc, phần còn lại và thời điểm chốt/cộng điểm.

**Acceptance Scenarios**:

1. **Given** voucher hợp lệ theo gói, chi nhánh, ngày/phiên, thời gian đặt, ngưỡng và hạn mức, **When** chọn, **Then** tối đa một voucher/booking; trần giảm cho cả booking không nhân số người.
2. **Given** phí gói 1.000.000đ, voucher giảm 100.000đ cho kết hợp, dùng 200 điểm, **When** tính, **Then** phí còn 880.000đ, cọc `ceil(880.000/2)` = 440.000đ và còn thu 440.000đ. Nếu phí sau giảm lẻ (ví dụ voucher cố định 15.001đ → 984.999đ) thì cọc = 492.500đ, còn lại 492.499đ; FE-06 không tính và không lưu cọc: chỉ giao `post_promotion_amount` cho 003.1, quy tắc cọc và số còn lại thuộc 003.1. Phí sau giảm 1đ vẫn hợp lệ (phí sau giảm dương); cọc và còn lại khi đó do 003.1 quyết định.
3. **Given** booking trên, **When** chọn điểm, **Then** trần theo phí sau voucher 900.000đ là 90.000đ/900 điểm, còn bị giới hạn bởi điểm khả dụng; không tính riêng trên cọc. Không được kết hợp hoặc dưới mức tối thiểu thì không cho dùng và nêu lý do.
4. **Given** xác nhận booking hợp lệ, **When** FE-03 chấp nhận, **Then** giữ lượt/điểm và ghi chính sách lúc đặt; xác minh cọc thành công mới chốt/trừ. Thu phần còn lại không giảm voucher hoặc trừ điểm lần nữa.
5. **Given** mới đặt/trả cọc/check-in hoặc đã trả đủ nhưng chưa hoàn thành, **When** xét điểm mới, **Then** chưa cộng; khi hoàn thành và trả đủ 880.000đ thì cộng một lần 88 điểm.
6. **Given** mã sản phẩm hoặc sai gói/chi nhánh/ngày/phiên/thời gian đặt, **When** áp dụng, **Then** từ chối kèm lý do. Hết lượt/thiếu điểm trước xác nhận thì tính lại và cho xác nhận phí/cọc mới.
7. **Given** phí sau giảm bằng 0/âm hoặc nghĩa vụ thanh toán không đạt FE-03, **When** xác nhận, **Then** từ chối, cho điều chỉnh; không tạo booking miễn phí hoặc giữ dở dang.
8. **Given** yêu cầu áp dụng ưu đãi workshop ngoài hành trình Member đặt trực tiếp website, **When** xử lí, **Then** không tự mở rộng quyền lợi này sang Guest hoặc đăng ký ngoại bộ.

### User Story 6 - Tích điểm và xem My Loyalty (Priority: P1)

Là Member, tôi muốn kiếm điểm từ cả hai dịch vụ và xem số dư, phần giữ, lịch sử để dùng cho lần sau.

**Why this priority**: Điểm chung khuyến khích quay lại cả hai dịch vụ.

**Independent Test**: Cung cấp giao dịch đủ/chưa đủ điều kiện từ FE-03/FE-08 rồi đối chiếu số dư/lịch sử của Member, không cần tạo chương trình mới.

**Acceptance Scenarios**:

1. **Given** sản phẩm của Member đã trả đủ được xác minh, **When** cộng theo chính sách đầu, **Then** tiền hàng sau ưu đãi/điểm, không gồm giao hàng, chia 10.000đ và làm tròn xuống; 9.999đ được 0 điểm, 10.000đ và 19.999đ đều 1 điểm, không chuyển tiền lẻ sang lần sau.
2. **Given** workshop hoàn thành và trả đủ, **When** cộng, **Then** tính một lần trên phí gói thực trả; không cộng cọc rồi cộng lại toàn phí. Booking hủy hoặc mới check-in không được cộng.
3. **Given** kiếm điểm từ sản phẩm, **When** đặt workshop, **Then** dùng được điểm đã có; điểm kiếm từ workshop cũng dùng mua sản phẩm. Điểm mới chỉ dùng sau cộng thành công, không giảm chính giao dịch sinh ra nó.
4. **Given** số dư 200 điểm, giữ 50, **When** mở My Loyalty, **Then** thấy số dư 200, giữ 50, khả dụng 150, giá trị quy đổi và lịch sử cộng/dùng/hoàn liên kết giao dịch; không xem dữ liệu Member khác.
5. **Given** điểm được cộng/hoàn, **When** thời gian trôi qua, **Then** không tự hết hạn, không xét hạng hoặc chia ví theo dịch vụ.
6. **Given** kết quả được gửi lại hoặc mất phản hồi, **When** kiểm tra/xử lí lại, **Then** chỉ cộng một lần/giao dịch, số dư/lịch sử nhất quán; thông báo trình duyệt chưa đủ căn cứ cấp điểm.
7. **Given** thiếu chính sách hợp lệ đã ghi nhận hoặc ghi điểm thất bại, **When** cấp điểm, **Then** chưa báo thành công, giữ khả năng xử lí lại không trùng và không thay bằng chính sách mới.
8. **Given** phiên hết hạn hoặc tải lỗi, **When** mở My Loyalty, **Then** yêu cầu đăng nhập/thử tải lại, không coi số dư cũ là hiện tại; sau cộng/trừ/giải phóng/hoàn tải lại được kết quả xác nhận.
9. **Given** lịch sử điểm, lượt voucher, nhật ký thay đổi hoặc kết quả thao tác, **When** xét lưu/dọn dữ liệu, **Then** giữ tối thiểu 5 năm và không dọn khi giao dịch chưa kết thúc hoặc còn được tham chiếu; không xóa số dư khả dụng hay cho tái sử dụng mã voucher vì hết thời hạn lưu.

### User Story 7 - Giải phóng và hoàn quyền lợi (Priority: P1)

Là Member, tôi muốn quyền lợi không bị giữ mất khi giao dịch hết hạn và được trả đúng khi hủy workshop hợp lệ.

**Why this priority**: Bảo toàn điểm/hạn mức là điều kiện để khách tin tưởng dùng ưu đãi.

**Independent Test**: Dùng giao dịch đang giữ/đã chốt và kết quả hủy xác nhận từ FE-03; thử hết hạn, hủy, gửi lặp và kết quả chưa rõ.

**Acceptance Scenarios**:

1. **Given** đơn/booking hết hạn, chưa có thanh toán thành công được chấp nhận, **When** giải phóng, **Then** trả điểm khả dụng và lượt đang giữ; không ghi là đã dùng hoặc cộng thêm khoản hoàn điểm.
2. **Given** quyền lợi đang giữ, **When** giao dịch khác đồng thời muốn dùng cùng điểm/tranh lượt cuối, **Then** không giữ/dùng vượt số dư hoặc hạn mức; giao dịch không giữ được phải nhận giới hạn mới để xác nhận lại.
3. **Given** FE-03 xác nhận hủy hợp lệ trước hoàn thành và quyền lợi mới đang giữ, **When** xử lí, **Then** chỉ giải phóng đúng một lần, không đồng thời hoàn như đã dùng.
4. **Given** booking hủy hợp lệ đã dùng 200 điểm, **When** hoàn, **Then** trả đúng 200 điểm khả dụng dù quy đổi đã đổi; lịch sử gắn booking/lần trừ gốc, không đổi thành tiền hoặc cộng điểm kiếm mới.
5. **Given** booking hủy hợp lệ đã chốt một lượt voucher, **When** hoàn, **Then** trả một lượt Member và một lượt tổng chương trình dù không dùng điểm; mã hết hạn/dừng không được gia hạn/kích hoạt lại.
6. **Given** kết quả hủy/hết hạn gửi lặp hoặc trước đó chỉ hoàn tất một phần, **When** xử lí lại, **Then** chỉ hoàn tất phần thiếu, không trả trùng, không vừa giải phóng vừa hoàn, không hoàn quyền lợi chưa từng dùng.
7. **Given** chỉ không đến, yêu cầu hủy chưa xác nhận hoặc hủy không hợp lệ, **When** xét hoàn, **Then** không tự trả điểm/lượt đã dùng hoặc cấp điểm mới; chờ kết quả xác nhận nếu chưa rõ.
8. **Given** workshop đã hoàn thành hoặc yêu cầu trả/hoàn sản phẩm, **When** xét phạm vi phiên bản này, **Then** không có hủy workshop đã hoàn thành, trả hàng/hoàn tiền sản phẩm, thu hồi điểm kiếm từ giao dịch hoàn tất hoặc số dư điểm âm.
9. **Given** thanh toán và hết hạn/hủy gần nhau, kết quả muộn/chưa rõ, **When** quyết định, **Then** dựa vào FE-03/FE-08 xác nhận, không vừa chốt vừa giải phóng cùng quyền lợi, không tự gia hạn/tạo giao dịch mới vì mất phản hồi.

### User Story 8 - Quản lí chính sách điểm (Priority: P1)

Là Manager, tôi muốn quản lí thông số chung của điểm thưởng và giữ chính sách đã cam kết cho giao dịch cũ.

**Why this priority**: Hai dịch vụ cần cùng chính sách, kể cả workshop diễn ra sau ngày đặt.

**Independent Test**: Thay đổi hợp lệ một thông số rồi đối chiếu booking đã chấp nhận với giao dịch mới.

**Acceptance Scenarios**:

1. **Given** cấu hình đầu, **When** Manager xem/quản lí, **Then** thông số chung là 10.000đ/điểm tích, 100đ/điểm quy đổi, tối thiểu 10 điểm/lần và trần 10% sau ưu đãi trước điểm.
2. **Given** tỷ lệ cộng/quy đổi không dương, lượng tối thiểu không nguyên dương hoặc trần ngoài khoảng lớn hơn 0 đến 100%, **When** lưu, **Then** nêu lỗi, từ chối và giữ cấu hình hợp lệ cũ; lưu lỗi không báo thành công.
3. **Given** Guest, Member, Staff, Admin hoặc tài khoản thiếu quyền Manager hiện hành, **When** đổi cấu hình điểm, **Then** từ chối; Admin không có quyền xem/sửa chính sách điểm.
4. **Given** booking đã chấp nhận với 10.000đ/điểm tích và 100đ/điểm quy đổi, **When** Manager đổi thông số trước ngày hoàn thành, **Then** booking giữ chính sách/cách tính ban đầu, giao dịch được chấp nhận sau đổi dùng chính sách mới; không tính lại khoản giảm/điểm đã giữ/tổng đã đồng ý.
5. **Given** lưu chưa xác nhận hoặc mất phản hồi, **When** kiểm tra lại, **Then** không báo cập nhật chỉ từ thao tác; giao dịch mới dùng một chính sách hợp lệ đã xác nhận.
6. **Given** hành trình quản lí chương trình, Kho Voucher, My Loyalty, chọn ưu đãi và cấu hình, **When** dùng giao diện web tách riêng, **Then** xem/gửi dữ liệu/nhận lỗi và hoàn thành hành trình cốt lõi với đủ loading/empty/validation/success/failure/recovery; thao tác trình duyệt không thay bằng chứng quyền/thanh toán.
7. **Given** Member/Manager vào FE-06, **When** xác thực và thao tác, **Then** dùng cơ chế chung FE-01/001, phiên theo contract FE-01/001, kiểm tra trạng thái/quyền hiện tại trên danh tính đã xác minh; phiên hết hạn/thu hồi bị từ chối, không kéo dài trượt. V1 không yêu cầu MFA hay tầng xác thực bổ sung cho vai trò nội bộ (theo 001, chốt 2026-10-27); FE-06 không tự thêm yêu cầu đó.

### Edge Cases

- Mã khác hoa/thường/khoảng trắng, mã phạm vi còn lại và mã hết hạn vẫn kiểm tra duy nhất; lưu mã chưa giữ lượt.
- Sản phẩm trong phạm vi chương trình bị ẩn, ngừng bán hoặc xóa mềm sau khi tạo: chương trình không tự tạm dừng và không đổi phạm vi; dòng đó không đủ điều kiện ở checkout, sản phẩm khác trong phạm vi vẫn dùng được; lịch sử/báo cáo giữ tham chiếu cũ. Chi tiết chương trình của Manager cảnh báo phạm vi đang ẩn/đã xóa để Manager tự quyết định tạm dừng.
- Giỏ trộn dòng đủ/không đủ điều kiện; ngưỡng vừa đủ/thiếu một đồng; giảm cố định vượt phần đủ điều kiện; phần trăm có phần lẻ không làm tổng âm.
- Nhiều giảm tự động bằng khoản giảm ưu tiên tạo trước; thứ tự phụ khi trùng cả thời điểm thuộc thiết kế plan, không thay quy tắc khoản giảm lớn nhất.
- Chọn 0 điểm là không dùng; chọn 1–9 điểm theo cấu hình đầu không hợp lệ. Khả dụng phải trừ mọi phần đang giữ, không chỉ đọc số dư tổng.
- Sản phẩm 0đ theo FE-08 không chờ cổng thanh toán; workshop 0đ bị từ chối. Trần điểm 10% không tự mở thành thanh toán toàn bộ bằng điểm.
- Trả đủ nhưng chưa hoàn thành, hoặc hoàn thành nhưng chưa trả đủ đều chưa tích; đủ hai điều kiện theo thứ tự nào cũng chỉ cộng một lần.
- Đổi chính sách/dừng/hết hạn sau chấp nhận không sửa quyền lợi giữ hợp lệ; đặt lại sau giải phóng phải kiểm tra điều kiện mới.
- Hạ hạn mức khi có giữ/chốt mới phải kiểm tra số liệu hiện tại, không hủy phần giữ để ép lưu.
- Mở lại chương trình đã tạm dừng không tạo mã mới, không reset lượt hoặc gia hạn; chương trình hết hạn/hết lượt vẫn không đủ điều kiện cho giao dịch mới cho tới khi điều kiện được cập nhật hợp lệ.
- Hủy booking hợp lệ loại phần dùng voucher của booking khỏi tổng hiện tại, không xóa lịch sử; gửi lặp không trừ báo cáo hai lần. Hết hạn/tạm dừng chương trình không tự xóa số liệu các giao dịch đã chốt chưa bị hủy.
- Hủy booking chỉ dùng voucher vẫn trả lượt, chỉ dùng điểm vẫn trả điểm; thử lại sau lỗi chỉ xử lí quyền lợi còn thiếu.
- Thông báo trình duyệt hoặc chưa nhận phản hồi không đủ căn cứ để chốt/cấp/hoàn hay kết luận thanh toán thất bại.

## Requirements *(mandatory)*

### Functional Requirements

**Quyền và phạm vi**

- **FR-001**: Hệ thống MUST kiểm tra quyền hiện hành từng thao tác: Manager quản lí chương trình và cấu hình điểm, Member đọc/dùng quyền lợi chính mình; Admin chỉ quản lí tài khoản/cấp quyền, không có quyền nghiệp vụ FE-06. Guest xem ưu đãi công khai, đăng nhập trước lưu; biết mã/đăng nhập không tự cấp quyền nội bộ.
- **FR-002**: Phạm vi MUST chỉ gồm voucher sản phẩm, voucher workshop cho Member đặt trực tiếp website, giảm tự động sản phẩm và điểm chung; không hạng thành viên, giảm tự động workshop hoặc lớp voucher ngày lễ cộng thêm. Sản phẩm không giới hạn chi nhánh; workshop có thể giới hạn chi nhánh tổ chức.

**Quản lí chương trình**

- **FR-003**: Manager MUST tạo được voucher sản phẩm với tên, mã, thời gian, sản phẩm/biến thể, kiểu/mức giảm, trần giảm, ngưỡng phần đủ điều kiện, tổng lượt, lượt mỗi Member và cho/không kết hợp điểm.
- **FR-004**: Manager MUST tạo được voucher workshop với tên/mã, gói/chi nhánh/ngày/phiên, thời gian đặt bằng mã, kiểu/mức giảm, trần giảm, phí gói tối thiểu, tổng lượt, lượt mỗi Member và kết hợp điểm; thời gian đặt phân biệt lịch tham gia.
- **FR-005**: Manager MUST tạo được giảm tự động sản phẩm theo phạm vi, thời gian, kiểu/mức giảm và điều kiện tương ứng, không cần mã hoặc giới hạn chi nhánh. Giảm tự động luôn cho kết hợp điểm; không có lựa chọn tắt kết hợp điểm cho loại này.
- **FR-006**: Hệ thống MUST bỏ khoảng trắng đầu/cuối và so sánh mã không phân biệt hoa/thường; mã duy nhất toàn hệ thống, không tái sử dụng. Không đổi mã hoặc phạm vi sản phẩm/workshop sau tạo.
- **FR-007**: Hệ thống MUST kiểm tra trường bắt buộc, bắt đầu trước kết thúc, mức giảm tiền/trần nguyên VND dương, tỷ lệ giảm lớn hơn 0 không quá 100%, ngưỡng tiền nguyên VND không âm, lượt tổng/mỗi Member nguyên dương và phạm vi hợp lệ. Phạm vi sản phẩm hợp lệ khi sản phẩm/biến thể tồn tại, biến thể thuộc sản phẩm và chưa bị xóa mềm; sản phẩm chưa công khai, đang ẩn hoặc size ngừng bán vẫn được chọn. Từ chối dữ liệu sai hoặc thiếu quyền. Không báo lưu thành công khi chưa xác nhận hoặc tạo trùng từ thao tác đã ghi nhận.
- **FR-008**: Manager MUST xem danh sách/chi tiết, lọc sản phẩm/workshop, xem điều kiện/thời gian/tổng hạn mức, lượt đã dùng và tổng tiền giảm hiện tại của chương trình. Không tính lượt đang giữ hoặc booking đã ghi nhận hủy hợp lệ vào số tổng; lịch sử dùng/hoàn được giữ để tra cứu. Xử lí hủy lặp không trừ số liệu thêm lần nữa; hết hạn/tạm dừng chương trình không loại giao dịch đã chốt chưa bị hủy khỏi báo cáo.
- **FR-009**: Manager MUST sửa điều kiện được phép cho giao dịch mới, tạm dừng và mở lại chương trình đã tạm dừng. Mở lại cùng chương trình/mã, giữ lịch sử và lượt đã dùng/đang giữ; không tự gia hạn hoặc reset lượt, giao dịch mới vẫn kiểm tra điều kiện hiện hành. Không giảm tổng hạn mức/lượt mỗi Member dưới đã dùng cộng đang giữ tương ứng theo số liệu hiện tại.
- **FR-010**: Giao dịch đã chấp nhận MUST giữ điều kiện/quyền lợi đã ghi nhận: khoản giảm, điểm dùng, quy đổi và chính sách tích. Sửa/dừng/hết hạn chương trình không tự hủy phần giữ hợp lệ hoặc gia hạn thời hạn gốc; giao dịch mới kiểm tra điều kiện hiện hành.

**Kho Voucher**

- **FR-011**: Thẻ MUST hiển thị phạm vi, mức/trần giảm, giá trị tối thiểu, thời hạn và kết hợp điểm; chi tiết có đối tượng áp dụng, chi nhánh/ngày/phiên workshop nếu có, tổng hạn mức và lượt mỗi Member theo cấu hình. Thẻ/chi tiết công khai chỉ liệt kê sản phẩm trong phạm vi đang công khai; chương trình sản phẩm không còn sản phẩm nào công khai trong phạm vi không hiện ở danh sách ưu đãi công khai, còn trong Kho Voucher vẫn hiện kèm trạng thái "Hiện không có sản phẩm áp dụng".
- **FR-012**: Member MUST lưu/xem kho cá nhân, chọn mã đã lưu hoặc nhập trực tiếp; lưu lặp không thêm quyền, lưu chưa giữ lượt/điểm hoặc bảo đảm mã hợp lệ.
- **FR-013**: Hệ thống MUST nêu lý do mã không tồn tại, sai phạm vi, hết hạn/dừng/hết lượt/chưa đủ điều kiện; cho chọn lại/không dùng. Mất phiên/lỗi tải/lưu phải cho đăng nhập/thử lại, không trình bày dữ liệu chưa xác nhận như hiện tại.

**Ưu đãi sản phẩm**

- **FR-014**: Cơ sở ngưỡng và tính giảm MUST chỉ gồm tiền dòng sản phẩm/biến thể thuộc phạm vi trước ưu đãi/điểm, không giao hàng. Giảm cố định một lần/đơn; phần trăm làm tròn xuống đến đồng; không vượt trần hoặc phần đủ điều kiện, không làm tiền hàng âm. Dòng sản phẩm thuộc phạm vi nhưng không mua được theo FE-02/FE-08 (ẩn, ngừng bán, đã xóa) không được tính vào phần đủ điều kiện.
- **FR-015**: Mỗi đơn MUST dùng tối đa một giảm tự động hoặc một voucher. Nhiều giảm tự động thì chọn khoản giảm lớn nhất, bằng nhau tạo trước; chọn voucher thay thế bỏ giảm tự động.
- **FR-016**: Checkout MUST hiển thị điểm khả dụng, số điểm tối đa/tiền giảm tương ứng, tiền hàng, giảm chương trình, giảm điểm, giao hàng và tổng; Member chủ động chọn, không tự trừ điểm.
- **FR-017**: Khi xác nhận, hệ thống MUST kiểm tra lại giá/điều kiện/số dư/hạn mức, ghi chính sách và chỉ chấp nhận đơn cùng phần giữ hợp lệ theo FE-08; không giữ dở dang hoặc tạo tổng khác khách xác nhận. Thử mã/mở checkout chưa giữ; thay đổi trước chấp nhận phải cho xác nhận lại.
- **FR-018**: Quyền lợi sản phẩm MUST chốt khi FE-08 xác nhận trả đủ thành công hoặc chấp nhận đơn 0đ hợp lệ; không chờ cổng cho 0đ hoặc chốt từ thông báo trình duyệt.

**Ưu đãi workshop**

- **FR-019**: Hệ thống MUST kiểm tra gói/chi nhánh/ngày/phiên, thời gian đặt, phí tối thiểu/hạn mức; tối đa một voucher/booking, trần cả booking không nhân số người. Với gói nhiều phiên, MUST kiểm tra mọi phiên đã chọn: một phiên ngoài phạm vi chi nhánh/ngày/phiên của voucher thì từ chối (làm rõ 2026-10-29).
- **FR-020**: Workshop MUST trừ voucher trên phí gói, tiếp đến điểm nếu được phép, để ra `post_promotion_amount` (nguyên VND). FE-03/003.1 tính `deposit = ceil(post_promotion_amount / 2)` và `remaining = post_promotion_amount - deposit`; FE-06 MUST NOT tự làm tròn theo quy tắc khác, và MUST chỉ gửi/snapshot `post_promotion_amount` khi booking được chấp nhận; cọc và còn lại do 003.1 tính và giữ (003.3 dùng giá trị frozen của 003, không tính lại voucher/điểm/cọc). Hiển thị phí đầu, từng khoản giảm, phí phải trả, cọc và còn lại. Thu phần còn lại không giảm/trừ lại. Phí sau giảm lẻ vẫn hợp lệ, không bị từ chối vì lẻ.
- **FR-021**: FE-03 chấp nhận booking thì MUST giữ lượt/điểm và ghi chính sách; xác minh cọc thành công mới chốt/trừ. Điểm/hạn mức/giá thay đổi trước chấp nhận phải tính lại và cho xác nhận phí/cọc.
- **FR-022**: Phiên bản này MUST từ chối phí workshop sau giảm bằng 0/âm hoặc nghĩa vụ thanh toán không hợp lệ theo FE-03; cho điều chỉnh, không tạo booking miễn phí/giữ dở dang.

**Điểm và cấu hình**

- **FR-023**: Member MUST có một số dư chung hai dịch vụ, điểm cộng/hoàn không hết hạn. Chỉ dùng khả dụng, không dùng điểm dự kiến của chính giao dịch; không chia ví/xét hạng.
- **FR-024**: Cấu hình khởi đầu MUST là 10.000đ đủ điều kiện/điểm tích, 100đ/điểm quy đổi, tối thiểu 10 điểm/lần và trần 10% sau chương trình trước điểm, chung hai dịch vụ. Sản phẩm không tính giao hàng; workshop tính phí gói, không riêng cọc.
- **FR-025**: Điểm dùng MUST nguyên, không vượt khả dụng hoặc phần nguyên làm tròn xuống của trần tiền giảm chia giá trị mỗi điểm; theo cấu hình đầu là `min(khả dụng, floor(10% × giá trị sau ưu đãi / 100đ))`. Dưới mức tối thiểu hoặc không được kết hợp thì chặn và nêu lý do; khách có thể không dùng hoặc chọn số nguyên trong giới hạn.
- **FR-026**: Manager MUST xem/cập nhật thông số với kiểm tra quyền/dữ liệu: tỷ lệ cộng/quy đổi dương, điểm tối thiểu nguyên dương, trần phần trăm lớn hơn 0 không quá 100%; cấu hình sai/lưu chưa xác nhận không thay chính sách hợp lệ. Kết hợp điểm lấy từ voucher Manager cấu hình; giảm tự động luôn cho kết hợp điểm.
- **FR-027**: Đổi cấu hình MUST chỉ áp dụng giao dịch được chấp nhận sau đó. Giao dịch cũ giữ thông số/cách tính lúc chấp nhận, kể cả workshop hoàn thành sau đổi; thiếu chính sách cũ không tự thay bằng mới.
- **FR-028**: Hệ thống MUST chỉ cấp điểm cho giao dịch thuộc Member: sản phẩm trả đủ được xác minh; workshop đồng thời hoàn thành và trả đủ. Đặt booking, cọc, chỉ check-in hoặc hủy trước hoàn thành chưa/không đủ điều kiện.
- **FR-029**: Cơ sở tích MUST là tiền hàng/phí gói thực trả sau ưu đãi/điểm, không giao hàng; làm tròn xuống điểm nguyên từng giao dịch, không cộng dồn tiền lẻ; workshop tính một lần phí thực trả, không cộng cọc rồi cộng lại cả phí.
- **FR-030**: Mỗi giao dịch đủ điều kiện MUST chỉ có một kết quả cấp điểm; gửi lặp/mất phản hồi/thử lại không cộng thêm. Thiếu chính sách hoặc lỗi ghi nhận thì chưa báo thành công, giữ khả năng xử lí lại.
- **FR-031**: My Loyalty MUST hiển thị số dư, phần giữ, khả dụng bằng số dư trừ giữ, quy đổi hiện hành và lịch sử cộng/dùng/hoàn có giao dịch/số điểm thay đổi; chỉ dữ liệu chính Member và tải lại được sau thay đổi xác nhận.

**Giữ, chốt, giải phóng và hoàn**

- **FR-032**: Điểm/lượt đang giữ MUST không dùng lại ở giao dịch khác; không giữ/dùng vượt số dư/hạn mức khi đồng thời hoặc tranh lượt. Chốt MUST đúng một lần theo kết quả nghiệp vụ xác minh.
- **FR-033**: Hết hạn, hoặc Member hủy đơn sản phẩm trước khi thanh toán (FE-08 xác nhận, làm rõ 2026-10-30), và chưa có thanh toán thành công được chấp nhận thì MUST giải phóng giữ đúng một lần, trả điểm khả dụng/lượt giữ, không ghi đã dùng/cộng thêm hoàn. Đặt lại kiểm tra điều kiện hiện hành, không gia hạn mã.
- **FR-034**: Hủy workshop MUST dựa trên FE-03 xác nhận hợp lệ trước hoàn thành, không chỉ nút Hủy. Mới giữ thì chỉ giải phóng; điểm đã trừ thì trả đúng lượng cũ, liên kết lần trừ gốc, không quy đổi lại/đổi tiền/cộng điểm kiếm mới.
- **FR-035**: Hủy hợp lệ voucher đã chốt MUST trả lượt Member và tổng chương trình dù không dùng điểm; không gia hạn/kích hoạt lại mã dừng/hết hạn. Không dùng quyền lợi nào thì không tạo hoàn quyền lợi đó.
- **FR-036**: Kết quả giải phóng/hoàn MUST phân biệt từng quyền lợi để thử lại an toàn; không trả trùng hoặc vừa giải phóng vừa hoàn. Kết quả trước thiếu phần nào chỉ hoàn tất phần đó, bảo toàn lịch sử.
- **FR-037**: Không đến, hủy không hợp lệ/chưa xác nhận hoặc đã hoàn thành MUST không tự hoàn quyền lợi đã dùng. Phiên bản này không trả hàng/hoàn tiền sản phẩm, hủy workshop đã hoàn thành, thu hồi điểm kiếm từ giao dịch hoàn tất hoặc số dư âm.
- **FR-038**: Khi thanh toán/hết hạn/hủy gần nhau hoặc kết quả muộn/chưa rõ, hệ thống MUST theo FE-03/FE-08 xác nhận, không vừa chốt vừa giải phóng, không cấp/hoàn từ trình duyệt hoặc tự tạo giao dịch/thời hạn mới do mất phản hồi.
- **FR-039**: Hành trình FE-06 cốt lõi MUST hoàn thành được từ giao diện web chạy tách riêng; hệ thống MUST NOT phụ thuộc giao diện do máy chủ dựng. Lỗi an toàn, cho sửa/đăng nhập/thử lại, không thông báo thành công trước xác nhận.
- **FR-040**: FE-06 MUST dùng chung cơ chế xác thực FE-01/001: phiên theo contract FE-01/001, danh tính đã xác minh, kiểm tra thu hồi/trạng thái/quyền hiện tại; không tự cấp quyền nội bộ từ phiên Member hoặc kéo dài phiên trượt. V1 không yêu cầu MFA hay tầng xác thực bổ sung (theo 001, chốt 2026-10-27): thao tác Manager chỉ cần bearer hợp lệ, tài khoản ACTIVE và vai trò MANAGER hiện hành.
- **FR-041**: Hệ thống MUST giữ lịch sử điểm/lượt voucher, nhật ký thay đổi và kết quả thao tác tối thiểu 5 năm; không dọn dữ liệu khi còn tham chiếu hoặc giao dịch chưa kết thúc. Thời hạn lưu không làm điểm hết hạn, mất bằng chứng chống cộng/trừ/hoàn trùng hoặc cho tái sử dụng mã voucher cũ.

### Key Entities *(include if feature involves data)*

- **Chương trình ưu đãi**: Tên, phạm vi, loại voucher/giảm tự động, thời gian, trạng thái, đối tượng áp dụng, kiểu/mức/trần giảm, ngưỡng, kết hợp điểm và thời điểm tạo; mã voucher duy nhất/cố định.
- **Hạn mức và lượt voucher**: Tổng/lượt Member, phần giữ/đã dùng/đã trả; gắn chương trình, Member và giao dịch gốc để không dùng/trả vượt.
- **Voucher đã lưu**: Quan hệ Member–voucher truy cập nhanh điều kiện hiện tại, không phải quyền giữ hạn mức.
- **Chính sách điểm**: Tỷ lệ tích/quy đổi, điểm tối thiểu, trần giảm/cách tính; thông số áp dụng được ghi nhận tại lúc chấp nhận giao dịch.
- **Tài khoản điểm**: Member, số dư, phần giữ, khả dụng; không chia dịch vụ hoặc hạng.
- **Lịch sử điểm/quyền lợi**: Cộng, giữ, chốt dùng, giải phóng, hoàn; lượng điểm/lượt, kết quả, giao dịch/lần dùng gốc để kiểm tra thử lại.
- **Quyền lợi giao dịch đã ghi nhận**: Liên kết đơn FE-08/booking FE-03, điều kiện, khoản giảm, điểm dùng, quy đổi, chính sách tích và thời hạn gốc; không tính lại theo cấu hình mới.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Trong US1–US2, 100% cấu hình hợp lệ lưu đúng phạm vi; mọi mã trùng/đổi phạm vi trái phép/hạn mức dưới đã dùng cộng giữ bị từ chối, không mất cam kết cũ. US2.8 giữ nguyên mã/quota/lịch sử khi mở lại; US2.9 trả đúng số tổng sau hủy và không mất lịch sử hoặc trừ lặp.
- **SC-002**: Mọi phép tính US4–US6 khớp đến đồng/điểm cho giảm, điểm tối đa, phí thực trả/cọc/còn lại và điểm tích; không tổng âm, cộng dồn sai hoặc cộng cọc hai lần.
- **SC-003**: Trong US3.1–US3.5, US4.4–US4.6 và US5.2–US5.3, 100% thông tin phạm vi, điều kiện kết hợp và điểm tối đa có trước xác nhận; khách chọn được phương án hợp lệ mà không cần hỏi nhân viên.
- **SC-004**: Trong US4.7–US4.9, US5.4–US5.7, US6.6 và US7, gửi lặp/mất phản hồi/tranh lượt không cộng/trừ/hoàn vượt một lần cho cùng quyền lợi hoặc vượt số dư/hạn mức.
- **SC-005**: 100% giao dịch cũ ở US2.4 và US8.4 giữ đúng chính sách ban đầu; giao dịch mới dùng điều kiện mới. Không workshop nào tích khi thiếu hoàn thành hoặc trả đủ.
- **SC-006**: Tất cả truy cập/thao tác trái quyền trong US1.6, US3.6, US4.10, US6.4, US8.3 và US8.7 bị từ chối; không xem/dùng quyền lợi Member khác hoặc tiếp tục thao tác với phiên hết hạn/thu hồi.
- **SC-007**: Trong US7.1–US7.7, điểm/lượt giải phóng hoặc hoàn đúng lượng gốc kể cả chỉ dùng một quyền lợi/mã hết hạn; điểm khả dụng/lịch sử nhất quán sau tải lại. Trong US6.9, dữ liệu dưới 5 năm, còn tham chiếu hoặc giao dịch chưa kết thúc không bị dọn.
- **SC-008**: Các hành trình cốt lõi US8.6 hoàn thành được từ giao diện web tách riêng; lỗi tải/lưu không gây thông báo thành công sai.

## Assumptions

### Nguồn và quyết định đã chốt

- A-001: [FE-06](../../research.md#fe-06) và hội thoại ngày 2026-10-24 là nguồn nghiệp vụ. Thông số điểm và mục 1–10 được người dùng xác nhận trước yêu cầu viết spec, không phải giả định mới của agent.
- A-002: Mức giảm/ngưỡng/hạn mức mỗi chương trình do Manager nhập, không cố định toàn hệ thống. Số liệu voucher trong kịch bản là dữ liệu kiểm thử; cọc workshop là `ceil(post_promotion_amount / 2)` do 003.1 quy định (quyết định B, đồng bộ với 003); bốn thông số điểm đầu là quyết định thật.
- A-003: Tiền VND, điểm nguyên; phép tính dùng chính sách ghi nhận cho giao dịch. Công thức với 10.000đ/100đ/10 điểm/10% là bộ khởi đầu, không cấm Manager đổi hợp lệ cho tương lai.
- A-004: Các kiểm tra dấu/miền giá trị tại FR-007 và FR-026 cụ thể hóa dữ liệu hợp lệ để nghiệm thu; không đặt thêm mức giảm thương mại, ngưỡng đơn hoặc hạn mức cố định cho Manager. Giới hạn độ dài nhập liệu và thiết kế lưu dữ liệu thuộc bước thiết kế tiếp theo.
- A-005: Khi lập plan ngày 2026-10-24, người dùng chốt lưu lịch sử điểm/lượt voucher, audit/kết quả thao tác tối thiểu 5 năm, không dọn dữ liệu còn tham chiếu hoặc giao dịch chưa kết thúc; điểm vẫn không hết hạn và mã cũ không tái sử dụng.
- A-006: Cùng phiên lập plan, người dùng chốt dùng chung hướng FE-01/001 và FE-02 cho Member/Manager FE-06: phiên theo contract FE-01/001, quyền/trạng thái mỗi thao tác và V1 không yêu cầu MFA hoặc tầng xác thực bổ sung theo 001 (làm rõ 2026-10-27).

- A-007: Thuật ngữ: "phí sau giảm" / "net fee" / `post_promotion_amount` là cùng một giá trị (phí gói sau voucher và điểm, trước khi tính cọc); "cọc" / "deposit" là cùng một khoản, do 003.1 tính từ giá trị này.

### Phụ thuộc và ranh giới

- D-001: FE-01/identity và Member Profile cung cấp danh tính xác minh, trạng thái/quyền và Member sở hữu giao dịch; FE-06 không chọn lại đăng nhập, mapping vai trò hoặc cấp quyền nội bộ.
- D-002: [FE-02](../../research.md#fe-02) cung cấp sản phẩm/biến thể/giá; FE-06 không tạo/sửa catalogue, duyệt giá, số lượng bán hoặc quản lí kho.
- D-003: [FE-08](../../research.md#fe-08) sở hữu giỏ/checkout/tạo đơn, số lượng, thanh toán/0đ, thời hạn, tra cứu và giao nhận. FE-06 cung cấp phép tính/quyền lợi, nhận kết quả xác minh; không chọn lại cổng, phí giao hoặc GHTK.
- D-004: [FE-03](../../research.md#fe-03) và [spec booking](../003-workshop-management/003.1-workshop-booking/spec.md) cung cấp gói/lịch/phí/cọc/thời hạn/kết quả thanh toán. Hủy hợp lệ, không đến, hoàn thành và trả đủ là kết quả FE-03 phải cung cấp. Spec 003 hiện chỉ booking/cọc/tra cứu và hoãn hậu xử lí; không coi hủy/hoàn thành đã triển khai hoặc được spec 003 bao phủ chỉ vì FE-06 cần.
- D-005: Điều kiện hủy và số tiền cọc hoàn thuộc FE-03; FE-06 chỉ giải phóng/hoàn điểm/lượt từ kết quả xác nhận, không tự thêm trả hàng/hoàn tiền sản phẩm hoặc hủy workshop hoàn thành.
- D-006: [FE-09](../../research.md#fe-09) cung cấp hành trình cấu hình chung cho Admin; chính sách điểm thuộc FE-06 và do Manager quản lí, không nằm trong cấu hình của Admin.
- D-007: Spec đặc tả nghiệp vụ. Plan sẽ xác định thiết kế tích hợp, biểu mẫu, dữ liệu và kiểm chứng theo constitution, tuân thủ retention FR-041/A-005 đã được xác nhận khi lập plan; không tự chọn công nghệ/provider hoặc thực hiện triển khai. Dependency chưa có không chặn review spec nhưng phải được hoàn thiện/kiểm chứng trước bật hành trình phụ thuộc.
- D-008: Không thêm gửi voucher tự động, phân nhóm theo hạng, đổi điểm lấy quà, chuyển điểm hoặc điều chỉnh điểm thủ công.

