# Các thay đổi cần thực hiện cho luồng workshop

Tài liệu ghi nhận quyết định mới để sửa spec và project. Chưa mô tả hành vi đã triển khai.

## Tổng hợp phạm vi thay đổi phần 003

Mục này tập hợp hướng nghiệp vụ từ cuộc trao đổi và các quyết định chi tiết bên dưới. Đây là danh sách đầu vào để sửa spec, chưa phải một spec hoàn chỉnh. Các chi tiết chưa được ghi đầy đủ được liệt kê riêng, không tự coi là đã được quyết định.

### 1. Giỏ nhiều vé và quản lý theo từng vé

- Cho phép thêm nhiều package/session và số người vào một giỏ. Một booking con tương ứng một vé cho một người; nhóm đặt chỗ liên kết các vé và giao dịch trả lúc đặt chung. Gói hai buổi vẫn là một vé có hai lịch tham dự.
- Nhập liên hệ chung, xác nhận giỏ một lần; kiểm tra và giữ tất cả chỗ trong một giao dịch; khách có 10 phút để thanh toán, hệ thống giữ chỗ 12 phút (xem mục "Thanh toán đến muộn sau khi hết giữ chỗ"). Nếu không đủ chỗ cho toàn bộ giỏ thì báo vé lỗi và không giữ một phần; giữ giỏ để khách sửa.
- Thanh toán chung một lần qua VNPay, phân bổ số tiền và ưu đãi xuống từng vé. Hết hạn chưa thanh toán thì nhả toàn bộ chỗ; giao dịch đến muộn xử lý theo mục "Thanh toán đến muộn sau khi hết giữ chỗ".
- Mỗi vé có QR, trạng thái tham dự, hóa đơn và xử lý hủy/đổi riêng. Không tự chuyển/hủy các vé khác chỉ vì cùng nhóm.

### 2. Gửi vé và xác minh người yêu cầu đổi

- Bổ sung lựa chọn gửi từng vé cho người nhận qua email sau khi đặt; không bắt buộc nhập thông tin mọi người để hoàn tất giỏ. Người đặt vẫn quản lý nhóm vé.
- Người nhận vé không tự có toàn bộ quyền quản lý của người mua. Có thể nhờ người mua đổi hoặc đến shop nhờ hỗ trợ; luồng xác minh email người nhận đã đăng ký và xác nhận của người mua cần được thể hiện rõ trong spec.
- Khi đổi làm tăng số phải trả, nội dung xác nhận của người mua phải nêu lịch mới và số tiền mới. Không coi việc sở hữu QR là đủ quyền chấp thuận thay đổi tài chính.
- Cần ghi đủ mẫu email mặc định gửi cho người mua, lựa chọn gửi riêng, gửi lại và thay email người nhận trước khi triển khai; tài liệu hiện chưa mô tả hết các chi tiết này.

### 3. Đổi lịch và gói nhiều buổi

- Tách đổi có báo trước ít nhất 24 giờ với hỗ trợ khách đến muộn nhưng yêu cầu trước khi ca cũ kết thúc. Giới hạn một lần đổi do khách yêu cầu cho mỗi vé, dùng chung cho hai loại; phạm vi đếm xem mục "Đổi lịch, điều kiện voucher và phân bổ ưu đãi".
- Giao diện chỉ hiển thị ca/phương án hợp lệ; kiểm tra lại khi xác nhận. Khách có thể thao tác online theo quyền hoặc nhờ nhân viên tại shop, không bắt buộc chỉ dùng web.
- Với hỗ trợ đến muộn, ca thay thế của buổi bị lỡ phải bắt đầu không muộn hơn thời điểm ca cũ kết thúc cộng 7 ngày. Nếu đổi buổi 1 kéo theo buổi 2, buổi 2 được ngoài mốc này nhưng phải đúng thứ tự/khoảng cách của package và còn chỗ.
- Chọn rõ các vé cần đổi. Nếu khách yêu cầu cả nhóm đi cùng thì kiểm tra toàn bộ các vé được chọn và đổi nguyên phương án, không tự chia nhóm.
- Kiểm tra cả capacity và quota nhận vé dời lịch theo số người/vé, tính sức chứa từ các kênh bán liên quan; một vé chuyển tới chiếm một chỗ. Quota không thay thế kiểm tra capacity.
- Đổi ra ngoài phạm vi voucher phải tính lại quyền lợi và công bố phần tăng tiền. Khoản tăng hợp lệ có thể ghi vào số dư để thu khi checkout, không bắt buộc thu ngay lúc đổi. Ngoại lệ do Mland xử lý theo chính sách riêng.
- Vé OTA theo chính sách và quy trình của OTA, không tự áp chính sách đổi vé trực tiếp Mland.
- Cần đối chiếu/ghi đủ quyền Staff/Manager, điều kiện tự động đổi, thông số quota và phạm vi quota cho từng loại đổi; không dùng các con số gợi ý trong trao đổi như cấu hình đã chốt nếu chưa có căn cứ.

### 4. Voucher, điểm và giá theo vé

- Một lượt voucher dùng cho một lần thanh toán/xác nhận nhóm. Kiểm tra điều kiện giỏ và điều kiện package/thời gian của từng vé; voucher áp dụng cho A/B không giảm vé C ngoài phạm vi.
- Tính ưu đãi theo loại phần trăm/số tiền và giới hạn của mã; lưu phần giảm phân bổ về từng vé để checkout, hủy, đổi và hoàn có số liệu nhất quán. Không chỉ lưu một số giảm ở cấp nhóm.
- Cho phép voucher giảm 100% và vé 0đ theo cấu hình hợp lệ. Không tự áp sàn giá vé 200.000đ hay trần giảm phần trăm cố định.
- Trần dùng điểm Member theo cấu hình điểm FE-06 được ghi nhận tại lúc chấp nhận giao dịch (Manager cấu hình, lớn hơn 0% đến 100%, mặc định 10%), tính trên tổng giá giỏ sau voucher và phân bổ theo tỷ lệ giá sau voucher của các vé. Vé đã về 0đ do voucher không dùng điểm.
- **Đã chốt (11/11/2026):** Giữ trần điểm cấu hình được tới 100%, nên điểm có thể làm giá một vé hoặc cả giỏ về 0đ. Vé về 0đ do điểm xử lý như vé miễn phí (không gọi VNPay, xác nhận theo nhóm, mở hóa đơn 0đ, checkout); tổng thu lúc đặt 1đ–4.999đ do điểm thì hoãn sang checkout. Hủy hợp lệ trả lại điểm đã dùng của vé; vắng mặt không trả.
- FE-06 cần sửa cho khớp (đã giao 006 sửa): `spec.md` dòng 224 đang ghi "workshop 0đ bị từ chối" và "trần điểm 10% không tự mở thành thanh toán toàn bộ bằng điểm" — đổi thành cho phép workshop 0đ do voucher hoặc điểm theo cấu hình trần.
- Hủy hợp lệ (từ 24 giờ trước buổi đầu của vé, xem mục "Khách tự hủy vé") vé nào trả phần điểm đã dùng của vé đó; hủy dưới 24 giờ hoặc vắng không trả. Hủy toàn bộ nhóm đủ điều kiện khôi phục đúng một lượt voucher; hủy một phần không khôi phục lượt, trừ khi Mland hủy (xem mục "Mland hủy hoặc dời ca"). Hoàn lượt voucher/điểm độc lập với hoàn tiền.
- Điểm thưởng và quyền đánh giá thuộc người đặt là Member; người đi cùng không cần tài khoản, Guest không có điểm. Tính quyền lợi theo từng vé đã checkout và hết khoảng sửa điểm danh 24 giờ, chống cộng lặp khi hóa đơn có nhiều khoản thanh toán.
- Công thức phân bổ/làm tròn và xử lý điều kiện voucher khi hủy/đổi đã chốt tại mục "Đổi lịch, điều kiện voucher và phân bổ ưu đãi"; giới hạn điểm theo cấu hình FE-06.

### 5. Khoản trả lúc đặt và vé miễn phí

Theo bản đề xuất cuối và các quyết định đã chốt, áp dụng theo giá cuối từng vé:

| Giá vé sau ưu đãi | Thu lúc đặt |
|---|---|
| 0đ | Xác nhận miễn phí, không gọi VNPay |
| 1đ–204.999đ | Thu toàn bộ giá vé |
| Từ 205.000đ | Thu cọc 200.000đ |

- **Đã chốt (11/11/2026):** Vé có giá cuối 1đ–4.999đ được chấp nhận; sàn 5.000đ chỉ áp cho tổng số tiền thực thu của giao dịch VNPay chung, không áp cho giá từng vé. Ví dụ giỏ có vé A 3.000đ và vé B 500.000đ là hợp lệ: thu 3.000đ + 200.000đ = 203.000đ trong một giao dịch. Giỏ có tổng thu lúc đặt từ 1đ đến 4.999đ được xử lý theo mục "Tổng thu lúc đặt dưới 5.000đ: hoãn sang checkout" bên dưới.
- Cộng khoản thu của từng vé thành giao dịch chung. Không tính cọc theo số buổi và không đổi lại thành 50%.
- Khách tự hủy/vắng mất toàn bộ khoản đã trả lúc đặt theo điều kiện đã công bố. Không áp hồi tố lên các vé cũ có điều kiện khác.
- Vé miễn phí vẫn có hóa đơn mở, tham dự và checkout; có thể phát sinh dịch vụ thêm. Giỏ vừa miễn phí vừa trả tiền phải chờ xác nhận thanh toán chung.

### 6. Thu tại quầy, hoàn tiền và sửa sai

- Phần còn lại và dịch vụ thêm tại shop được trả bằng VNPay, tiền mặt, chuyển khoản trực tiếp; được chia nhiều khoản và kết hợp phương thức trên một hóa đơn.
- Staff có quyền thu tiền xác nhận tiền mặt; người có `workshop.payment.transfer.confirm` xác nhận chuyển khoản (xem mục "Phân quyền thu, hoàn và đối soát"); hệ thống xác minh VNPay. Manager xử lý sửa sai và đối soát.
- Sàn 5.000đ áp dụng cho từng khoản VNPay; tiền mặt không chịu sàn này. Khoản chưa xác minh chưa được trừ vào số dư. Chỉ hoàn tất khi đủ tiền và đủ điều kiện checkout.
- Giữ giá add-on có thu tiền từ 5.000đ theo quyết định hiện tại. Cho sửa/hủy dòng ghi sai có lịch sử, không coi là quyền tự hủy dịch vụ đã dùng của khách.
- Khoản hoàn đủ điều kiện do Mland, thu sai/thừa/trùng đi qua ngân hàng ngoài VNPay; Manager duyệt và xác nhận đối soát. Tổng hoàn không vượt khoản thực thu có thể hoàn; không hoàn giá trị voucher thành tiền.
- Chống thu/hoàn trùng, kiểm tra giao dịch đang chờ khi đổi phương thức, lưu đúng người trả tiền và tách báo cáo thu theo phương thức.

### 7. Phần dự án phải cập nhật và trạng thái

- Spec phần 003 về booking, tham dự, settlement/đổi lịch; liên kết hợp đồng khuyến mãi và payment dùng chung khi cần, không mở rộng thành thiết kế lại module bán hàng khác.
- Data model, API contract, giao diện khách/Staff/Manager, thông báo và email, phân quyền, nhật ký, đối soát và các kiểm thử tích hợp liên quan.
- Ưu tiên chính sách mới nhất: mục “Thanh toán kết hợp nhiều phương thức tại quầy” thay thế quy tắc VNPay-only tại quầy và cấm trả từng phần trong lịch sử phía dưới.
- **Đã lưu:** danh sách này và các quyết định chi tiết bên dưới. **Đồng bộ spec (12/11/2026):** 003.1 đã có FR-030–FR-043 (giỏ, vé con, phân bổ ưu đãi, mức thu lúc đặt, giữ chỗ 12/10 phút, thanh toán đến muộn, người nhận vé), data-model, contract dự kiến `contracts/workshop-booking-cart.openapi.yaml` và tasks Phase 8 (T089–T097). **Chưa làm:** spec 003.3 (hủy, đổi lịch, hoàn ngân hàng, thu tại quầy), spec 003.2 (sức chứa/tham dự theo vé, quota đổi lịch), sửa code.
- **Còn cần hoàn thiện trước khi code:** các chi tiết được đánh dấu ở mục gửi vé, đổi lịch và voucher; kiểm chứng trên merchant sandbox/ngân hàng shop khi cần. Đây là phần cần ghi chính xác hoặc kiểm chứng, không phải tự mở lại các quyết định người dùng đã chốt.

## Hoàn tiền trực tiếp qua ngân hàng, không dùng VNPay Refund

**Quyết định (11/11/2026):** Với vé workshop bán trực tiếp qua Mland, khoản hoàn được nhân viên chuyển khoản cho khách ngoài hệ thống. Hệ thống tính số tiền, lưu yêu cầu, theo dõi và đối soát; không gửi lệnh hoàn tiền qua API VNPay. Thanh toán cọc và các khoản thu qua VNPay vẫn là luồng riêng. Hoàn vé bán qua OTA tiếp tục theo chính sách và quy trình của OTA.

### Quy tắc nghiệp vụ cần phản ánh

- Khách tự hủy hoặc vắng không được hoàn khoản đã thu lúc đặt, bao gồm cả trường hợp thu đủ giá vé dưới 205.000đ (quyết định A bên dưới). Các ngoại lệ Mland phải hoàn tiền, khoản thu thừa sau checkout và các trường hợp hoàn đã được phê duyệt mới tạo yêu cầu hoàn.
- Với giao dịch cọc chung của giỏ, tính và lưu số tiền hoàn theo từng vé; tổng tiền hoàn đã thực hiện hoặc đang chờ không được vượt khoản đã thu và phân bổ cho vé đó. Không thay đổi các vé khác trong nhóm khi chỉ một vé được hoàn.
- Mặc định chuyển khoản cho người đã trả khoản tiền đang được hoàn: với cọc chung là người thanh toán cọc của nhóm, kể cả khi vé được gửi cho người khác; với khoản thu tại checkout là người đã trả khoản thu đó. Nếu chuyển cho người khác, phải có xác nhận của người trả tiền và lưu bằng chứng xác nhận.
- Manager duyệt lý do, số tiền và người nhận. Nhân viên thực hiện chuyển khoản ngân hàng ngoài hệ thống, ghi mã giao dịch và chứng từ. Chỉ Manager xác nhận `Đã hoàn` sau khi đối chiếu giao dịch ngân hàng; trước đó yêu cầu ở trạng thái `Chờ hoàn` hoặc `Chờ đối soát`.
- Lưu lịch sử không thể sửa âm thầm: vé và nhóm liên quan, số tiền, người thực hiện, người duyệt, người xác nhận đối soát, thời điểm, tham chiếu giao dịch ngân hàng và chứng từ được bảo vệ. Chống tạo hoặc xác nhận trùng cùng một khoản hoàn.
- Không áp ngưỡng thanh toán VNPay 5.000đ cho khoản hoàn qua ngân hàng. Cần kiểm tra riêng giới hạn chuyển khoản của ngân hàng shop dùng. Ngưỡng VNPay vẫn áp dụng cho từng lần **thu tiền** qua VNPay.
- Yêu cầu VNPay Refund cũ trả mã `99` phải được tra soát trạng thái trước khi chuyển khoản thủ công cho cùng khoản tiền, tránh hoàn hai lần.

### Thu thập tài khoản nhận hoàn

**Đã chốt (12/11/2026):** VNPay không trả số tài khoản của người thanh toán, nên mỗi yêu cầu hoàn cần thu thập tài khoản nhận từ khách.

- **Link riêng:** khi yêu cầu hoàn được tạo, hệ thống gửi cho người trả tiền một link riêng để nhập ngân hàng, số tài khoản và tên chủ tài khoản. Link dùng một lần, hết hạn sau 7 ngày, gửi lại được (gửi lại vô hiệu link cũ). Khách cũng có thể cung cấp tại quầy để Staff nhập; lưu người nhập, kênh và thời điểm.
- **Xác minh:** tên chủ tài khoản trùng người trả tiền thì tiếp tục; khác tên thì cần xác nhận của người trả tiền và lưu bằng chứng. Khi chuyển khoản, nhân viên đối chiếu tên người nhận do ngân hàng hiển thị; không khớp thì dừng và liên hệ lại khách.
- **Bảo mật:** mã hóa số tài khoản khi lưu; chỉ Manager duyệt và nhân viên thực hiện chuyển khoản xem đầy đủ, màn hình khác chỉ hiển thị dạng che (`****1234`). Không ghi số tài khoản đầy đủ vào email, log hoặc audit. Sau khi đối soát `Đã hoàn`, chỉ giữ dạng che.
- **Khách không phản hồi:** yêu cầu ở trạng thái `Chờ khách cung cấp tài khoản`; tự nhắc ngày 3 và ngày 7. Sau 30 ngày, Manager được đóng tạm với lý do "không liên hệ được"; khách liên hệ lại thì mở lại để hoàn. Không có hạn cuối làm mất quyền nhận hoàn.
- **Kiểm thử:** link hết hạn/dùng lại/gửi lại; tên tài khoản khác người trả tiền thiếu xác nhận; quyền xem số tài khoản đầy đủ; nhắc ngày 3/7; đóng tạm sau 30 ngày và mở lại.

### Việc cần sửa trong project

1. Cập nhật spec, chính sách folio, API contract và UI flow của Settlement: thay mô hình `VNPay Refund → QueryDR → verified` bằng `Manager duyệt → chuyển khoản ngân hàng → đối soát → xác nhận hoàn` cho các khoản hoàn trực tiếp của Mland.
2. Bổ sung dữ liệu và trạng thái cho yêu cầu hoàn thủ công, bằng chứng ngân hàng, quyền thao tác và nhật ký kiểm toán; không coi thao tác nhấn nút hoặc ảnh biên lai đơn lẻ là bằng chứng tiền đã chuyển thành công.
3. Sửa các luồng hủy do Mland, sửa thu thừa sau checkout và hoàn từng vé trong giỏ để chỉ áp dụng hậu quả tài chính/nghiệp vụ sau khi khoản hoàn được đối soát.
4. Ngừng gọi VNPay Refund và refund QueryDR từ các luồng này; giữ dữ liệu giao dịch VNPay gốc để chứng minh khoản đã thu. Không xóa lịch sử hoặc tự động chuyển các refund VNPay đang chờ sang hoàn thủ công.
5. Cập nhật thông báo cho khách và kiểm thử: đúng người nhận tiền, hoàn một vé trong nhóm, giới hạn tổng số đã thu, gửi lại lệnh, chứng từ sai/thiếu, đối soát thất bại và refund VNPay cũ chưa rõ trạng thái.

### Vị trí hiện đang xung đột với quyết định

- `ppswbs-spec/features/003-workshop-management/direct-booking-open-folio-policy.md` quy định hoàn qua VNPay và QueryDR.
- `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/{spec.md,data-model.md,tasks.md,contracts/workshop-settlement.openapi.yaml}` mô tả luồng refund theo provider.
- `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopRefundService.java` gửi và tra cứu refund qua VNPay.
- `documents/feature-003-vnpay-sandbox-handover-to-son.md` ghi nhận giao dịch refund trả mã `99` chưa được đối soát.

**Trạng thái:** Chưa sửa spec/code và chưa bật luồng mới.

## Giới hạn số tiền thu qua VNPay: tối thiểu 5.000đ

**Quyết định (11/11/2026):** Ghi nhận giới hạn 5.000đ cho mỗi giao dịch thu tiền qua VNPay, theo lựa chọn của người dùng và phản hồi được cung cấp trong cuộc trao đổi. Đây là ngưỡng số tiền thực thu sau ưu đãi và sau khi xác định phần phải trả ở lần thanh toán đó.

### Quy tắc nghiệp vụ cần phản ánh

- Số tiền cần thu bằng 0đ: không tạo giao dịch VNPay; xử lý xác nhận/hoàn tất theo luồng không cần thanh toán. Giữ trường hợp vé được giảm hết giá về 0đ đã thống nhất.
- Số tiền cần thu từ 1đ đến 4.999đ: không tạo giao dịch VNPay. Với khoản thu lúc đặt của giỏ, hoãn sang checkout theo mục "Tổng thu lúc đặt dưới 5.000đ: hoãn sang checkout"; với số dư tại quầy, thu bằng tiền mặt/chuyển khoản. Hiển thị cách thu ngay khi tính giá.
- Số tiền cần thu từ 5.000đ: đạt điều kiện số tiền tối thiểu; vẫn phải qua các kiểm tra thanh toán khác.
- Kiểm tra tổng cọc của cả giỏ khi trả cọc chung và số dư cần thu của từng vé khi checkout riêng. Giá của một vé dưới 5.000đ không làm giao dịch chung không hợp lệ nếu tổng số tiền thực thu đạt ngưỡng (đã chốt: giỏ vé A 3.000đ + vé B 500.000đ hợp lệ). Sàn giá add-on 5.000đ là quy tắc riêng, vẫn giữ.
- Khi tính giá đặt chỗ, kiểm tra cả khoản thu lúc đặt và số dư checkout dự kiến. Bản đề xuất cuối xử lý vé giá 203.000đ bằng cách thu đủ 203.000đ lúc đặt, còn lại 0đ, theo quyết định A bên dưới. Các dịch vụ thêm hoặc điều chỉnh sau đặt vẫn cần kiểm tra riêng số tiền phải thu.
- Tính lại báo giá khi khách thay voucher, số điểm hoặc giỏ. Không tự tăng khoản phải trả lên 5.000đ, miễn phần lẻ hoặc âm thầm thay đổi quyền lợi voucher để vượt kiểm tra.
- Không áp ngưỡng này cho khoản hoàn ngân hàng ngoài hệ thống. Không ghi nhận các phương án bội số 5.000đ, trần voucher 69% hoặc sàn giá sau giảm 205.000đ như quyết định đã chọn.

### Việc cần sửa trong project

1. Bổ sung giới hạn tại bước báo giá/xác nhận của Booking và Settlement, đồng thời kiểm tra lại phía backend ngay trước khi tạo giao dịch VNPay.
2. Hiển thị lỗi rõ ràng ở màn hình voucher/điểm, giỏ và checkout để khách biết khoản cần thanh toán chưa đạt 5.000đ; tránh chuyển khách sang VNPay rồi mới báo lỗi.
3. Cập nhật spec và kiểm thử các mốc 0đ, 1đ, 4.999đ, 5.000đ; giỏ có vé giá nhỏ nhưng tổng cọc đủ ngưỡng; vé để lại số dư checkout 3.000đ; và trường hợp giảm hết giá vé.
4. Khi thiết kế chi tiết quy tắc tạo giá/voucher/điểm, đối chiếu với giới hạn trên và cách phân bổ từng vé; chưa tự suy ra một trần phần trăm giảm giá chung cho mọi package.

**Nguồn:** [FAQ chính thức của VNPay](https://sandbox.vnpayment.vn/apis/docs/faqs/). Kiểm tra tài liệu ngày 11/11/2026; thử nghiệm ngưỡng trên merchant sandbox của dự án vẫn chưa hoàn tất.

**Trạng thái:** Đã ghi nhận giới hạn cần triển khai; chưa sửa spec/code.

## Quyết định A: không hoàn khoản đã trả lúc đặt khi khách tự hủy hoặc vắng

**Đã chốt với người dùng (11/11/2026):** Chọn A cho trường hợp vé có giá sau ưu đãi từ 200.001đ đến 204.999đ được thu đủ lúc đặt theo bản `VNPAY_BANCUOI.docx`. Toàn bộ khoản đã thu lúc đặt không hoàn khi khách tự hủy hoặc không tham dự; không tách phần vượt 200.000đ để hoàn lại.

- Ví dụ vé giá cuối 203.000đ: thu đủ 203.000đ lúc đặt; checkout phần giá vé còn 0đ. Nếu khách tự hủy hoặc vắng, khoản 203.000đ đã thu không hoàn.
- Quy tắc này thống nhất với việc giữ toàn bộ khoản thu lúc đặt của các vé giá thấp hơn và giữ cọc 200.000đ đối với vé từ 205.000đ trở lên. Vé 0đ không có khoản tiền để hoàn.
- Các ngoại lệ hoàn do Mland và các khoản thu sai/trùng vẫn theo chính sách xử lý riêng; khi phải hoàn thì chuyển khoản ngân hàng và đối soát như đã chốt.
- Trước khi khách xác nhận đặt, giao diện phải phân biệt `Thanh toán toàn bộ` và `Cọc`, đồng thời công bố rằng khoản trả lúc đặt không được hoàn khi khách tự hủy hoặc vắng. Không chỉ ghi “mất cọc 200.000đ” cho vé đã thu đủ 203.000đ.
- Lưu số tiền thực thu và điều kiện không hoàn của từng vé tại thời điểm đặt để xử lý hủy nhất quán. Không áp hồi tố điều kiện mới lên booking cũ đã xác nhận theo điều kiện khác.
- Bổ sung kiểm thử các giá cuối 200.000đ, 200.001đ, 204.999đ và 205.000đ: số thu lúc đặt, số dư checkout, hủy/vắng và ngoại lệ hoàn do Mland.

**Trạng thái:** Đã ghi nhận quyết định A; chưa sửa spec/code.

## Giỏ nhiều vé: tính khoản thu theo từng vé và xác nhận toàn bộ nhóm

**Đã thống nhất (11/11/2026):** Mỗi booking con tương ứng một vé cho một người. Áp dụng công thức thu lúc đặt của bản cuối cho từng vé sau khi phân bổ ưu đãi, rồi cộng thành một khoản thanh toán chung. Gói nhiều buổi vẫn là một vé, không nhân số tiền cọc theo số buổi.

| Vé | Giá sau ưu đãi | Thu lúc đặt | Phần giá vé còn lại |
|---|---:|---:|---:|
| A | 0đ | 0đ | 0đ |
| B | 203.000đ | 203.000đ | 0đ |
| C | 500.000đ | 200.000đ | 300.000đ |
| Tổng | 703.000đ | 403.000đ | 300.000đ |

- Với ví dụ trên, khách trả một lần 403.000đ; hệ thống xác nhận cả ba vé sau khi khoản thanh toán chung được xác minh và các điều kiện giữ chỗ còn hợp lệ.
- Giỏ có cả vé miễn phí và vé phải thanh toán: giữ chỗ chung, vé miễn phí cũng chờ kết quả thanh toán của nhóm. Không xác nhận vé miễn phí trước hoặc tách nhóm ngầm.
- Giỏ hoàn toàn miễn phí: sau khi kiểm tra đủ điều kiện và đủ chỗ cho tất cả vé, xác nhận toàn bộ nhóm, chốt ưu đãi và ghi nhận lịch sử trong giao dịch nội bộ; không gọi VNPay. Gọi lặp không tạo thêm vé hoặc trừ chỗ/ưu đãi lần nữa.
- Nhóm hết thời gian giữ chỗ mà chưa có thanh toán được xác minh thì hủy giữ chỗ của toàn bộ nhóm, bao gồm vé miễn phí. Tiền đến sau khi hết giữ chỗ đi theo mục "Thanh toán đến muộn sau khi hết giữ chỗ": chỉ xác nhận lại khi đủ điều kiện kiểm tra lại, không khôi phục phần giữ chỗ cũ.
- Khi thanh toán chung được xác nhận, lưu phân bổ số thực thu về từng vé để xử lý hủy, checkout và hoàn tiền đúng phần của vé đó.

**Trạng thái:** Đã ghi nhận vấn đề 2; chưa sửa spec/code.

## Vé miễn phí vẫn có hóa đơn mở và checkout

**Đã thống nhất (11/11/2026):** Khi vé miễn phí được xác nhận theo quy tắc của nhóm, tạo hóa đơn mở với giá vé 0đ, số đã thu 0đ và số dư 0đ. Vé tiếp tục được check-in, điểm danh, ghi dịch vụ thêm và checkout theo quy trình workshop.

- Không tự hoàn tất hóa đơn ngay lúc đặt chỉ vì giá vé bằng 0đ. Hóa đơn ở trạng thái mở để ghi nhận dịch vụ thêm đã được khách đồng ý.
- Khi đã đủ điều kiện checkout: nếu số dư bằng 0đ, hoàn tất hóa đơn nội bộ; nếu có khoản phát sinh phải trả, thu và xác minh khoản thanh toán trước khi hoàn tất. Ví dụ vé miễn phí thêm dịch vụ 50.000đ thì khách trả 50.000đ qua VNPay.
- Xác nhận booking miễn phí, mở hóa đơn và hoàn tất checkout 0đ phải chống xử lý lặp; không tạo giao dịch VNPay hoặc bằng chứng thu tiền giả để đi qua các bước này.
- Sửa `DirectBookingFolioOpeningService`: hiện không mở hóa đơn khi không có thanh toán cọc đã xác minh hoặc tổng cọc đã xác minh bằng 0. Chỉ bổ sung ngoại lệ cho booking miễn phí đã được xác nhận hợp lệ; vé có thu tiền vẫn phải có bằng chứng thanh toán đúng quy định.
- Kiểm thử vé 0đ không có dịch vụ thêm, có dịch vụ thêm, gọi lặp, và vé 0đ trong giỏ phải chờ thanh toán chung trước khi mở hóa đơn.

**Trạng thái:** Đã ghi nhận vấn đề 3. **Cập nhật 12/11/2026:** đã làm phần booking đơn lẻ 0đ (chưa có giỏ): 003.1 FR-007a1 xác nhận booking 0đ trong giao dịch nhận quote, không gọi VNPay, không tạo payment receipt, settle quyền lợi Loyalty cùng giao dịch; `DirectBookingFolioOpeningService` mở folio 0đ cho booking đã xác nhận có giá và cọc bằng 0. Phần giỏ nhiều vé (vé 0đ chờ thanh toán chung) chưa làm.

## Hóa đơn cuối: thu toàn bộ số dư và xử lý ngoại lệ dưới 5.000đ

> **Cập nhật thay thế:** Quyết định “Thanh toán kết hợp nhiều phương thức tại quầy” ở cuối tài liệu thay thế giới hạn VNPay-only tại quầy, cấm thu từng phần và chỉ cho Manager thu ngân hàng trong ngoại lệ dưới 5.000đ của mục này. Nội dung dưới đây giữ làm lịch sử; khi sửa spec/code phải dùng quyết định mới. Quy tắc giá add-on và lịch sử sửa sai vẫn giữ, trừ khi được thay đổi riêng.

**Đã thống nhất (11/11/2026):** Luồng thu thông thường của workshop vẫn qua VNPay. Người dùng đồng ý ngoại lệ thu chuyển khoản ngân hàng ngoài VNPay do Manager xử lý khi dữ liệu cũ hoặc điều chỉnh khoản thu sai làm số tiền đúng còn phải thu nằm trong khoảng 1đ–4.999đ. Quyết định này chỉ mở ngoại lệ nêu trên, không mở phương thức thu ngoài VNPay cho checkout thông thường.

### Luồng thông thường

- Mỗi dịch vụ thêm có thu tiền phải từ 5.000đ; áp dụng cho giá danh mục và khoản dịch vụ phát sinh được nhập tại shop. Khách phải đồng ý trước khi ghi nhận khoản thu.
- Khi checkout, thu toàn bộ số dư trong một lần, không cho thu từng phần. Số dư 0đ thì hoàn tất nội bộ khi đủ điều kiện checkout; số dư từ 5.000đ thì thu qua VNPay và chỉ hoàn tất sau khi xác minh.
- “Hủy dịch vụ thêm” trong quyết định này là hủy nguyên dòng ghi nhận sai hoặc trùng, không phải quyền tự hủy dịch vụ đã sử dụng của khách. Manager được thực hiện khi hóa đơn chưa hoàn tất, bắt buộc ghi lý do và giữ chứng từ gốc cùng lịch sử sửa sai.
- Với phần giá vé còn lại bằng 0đ hoặc từ 5.000đ, các dòng add-on có giá từ 5.000đ và chưa thu từng phần, việc hủy nguyên dòng sai vẫn giữ số dư bằng 0đ hoặc từ 5.000đ.
- Hóa đơn đã hoàn tất áp dụng luồng sửa thu thừa và hoàn tiền riêng; không xóa hoặc sửa trực tiếp chứng từ cũ.

### Ngoại lệ thu chuyển khoản ngân hàng ngoài VNPay

- Chỉ Manager được xử lý khi có căn cứ dữ liệu cũ hoặc sửa khoản thu sai, số dư đúng lớn hơn 0đ và nhỏ hơn 5.000đ. Không dùng ngoại lệ để tiếp nhận dịch vụ mới có giá dưới sàn hoặc chia nhỏ khoản thu thông thường.
- Giữ đúng số tiền khách phải trả; không tự miễn phần lẻ, nâng số phải trả lên 5.000đ hoặc giữ số tiền tính sai để vượt ngưỡng.
- Khách chuyển khoản đúng số dư vào tài khoản ngân hàng của shop. Manager đối chiếu tiền thực nhận, mã giao dịch, số tiền và hóa đơn; lưu lý do ngoại lệ, tham chiếu/chứng từ ngân hàng, người xác nhận và thời điểm.
- Chỉ sau khi đã đối soát khoản thu và thỏa điều kiện checkout mới ghi nhận hóa đơn đã thanh toán đủ và hoàn tất. Chứng từ do khách cung cấp đơn lẻ không thay thế việc kiểm tra tiền thực nhận.
- Khoản thu phải có nguồn chứng minh riêng cho chuyển khoản ngân hàng được xác nhận thủ công; không giả lập IPN/QueryDR hoặc gắn nhãn giao dịch VNPay. Ngăn dùng cùng một khoản tiền ngân hàng để xác nhận thu trùng hoặc xác nhận cho nhiều hóa đơn vượt số thực nhận.
- Giới hạn chuyển khoản nhỏ của ngân hàng shop dùng phải được kiểm chứng trước khi bật ngoại lệ. Nếu chưa thể chuyển đúng số tiền thì giữ trạng thái chờ xử lý, không đánh dấu đã thu hoặc tự miễn tiền.
- Đây là luồng thu tiền riêng với luồng hoàn tiền ngân hàng đã chốt; cập nhật spec 003.3 và điều kiện bằng chứng thanh toán để công nhận đúng phạm vi ngoại lệ này.

### Việc cần kiểm thử

- Add-on 4.999đ bị từ chối và 5.000đ được chấp nhận; từ chối trả từng phần số dư.
- Ví dụ vé đã trả hết, thêm hai dịch vụ 10.000đ và 5.000đ: tổng thu 15.000đ; hủy dòng sai 10.000đ còn 5.000đ; hủy cả hai còn 0đ.
- Khoản đúng còn phải thu 3.000đ do sửa sai được Manager xác nhận sau đối soát; Staff không tự xác nhận ngoại lệ; thiếu bằng chứng hoặc chưa nhận tiền thì chưa hoàn tất.
- Xác nhận lặp và tái sử dụng tham chiếu chuyển khoản không ghi nhận tiền hai lần; mọi khoản thu và sửa sai đều giữ lịch sử.

**Trạng thái:** Đã ghi nhận vấn đề 4; chưa sửa spec/code.

## Hủy vé miễn phí và khôi phục ưu đãi

**Đã thống nhất (11/11/2026):** Vé miễn phí áp dụng cùng chính sách hủy và khôi phục ưu đãi của nhóm đặt vé. Một lượt voucher thuộc lần thanh toán/xác nhận chung của nhóm, không phải một lượt riêng cho mỗi vé.

- Hủy một vé miễn phí trong khi nhóm còn vé khác chưa hủy: chưa khôi phục lượt voucher. Phần giá vé bằng 0đ và chưa thu tiền không tạo khoản hoàn tiền.
- Hủy toàn bộ nhóm đủ điều kiện theo chính sách đã chốt: khôi phục một lượt voucher đã dùng cho nhóm, không nhân số lượt theo số vé. Áp dụng cả nhóm hoàn toàn miễn phí và nhóm có cả vé miễn phí lẫn vé trả tiền.
- Vé miễn phí nhưng khách vắng không làm khôi phục voucher. Không coi vắng mặt là hủy hợp lệ để trả ưu đãi.
- Vé đã được voucher giảm hết về 0đ không sử dụng điểm, nên không có điểm đã dùng trên vé đó để trả lại. Điểm thực tế đã phân bổ cho các vé khác vẫn xử lý theo chính sách hủy từng vé đã chốt.
- Hủy do Mland áp dụng chính sách ngoại lệ đã thống nhất. Không quy đổi giá trị voucher thành tiền hoàn. Nếu vé miễn phí có khoản dịch vụ thêm đã thực thu, khoản đó được xét riêng theo chính sách tương ứng; không suy ra rằng mọi khoản thu liên quan đến vé miễn phí đều không thể hoàn.
- Việc khôi phục voucher phải chống xử lý lặp: nhiều yêu cầu hủy đồng thời hoặc xử lý lại sự kiện hủy cuối cùng chỉ khôi phục tối đa một lần cho lượt đã dùng. Lưu liên kết giữa nhóm, lượt sử dụng, lý do và lần khôi phục.

**Ví dụ:** Nhóm có hai vé miễn phí dùng chung một lượt voucher. Hủy vé thứ nhất chưa trả lượt; hủy tiếp vé thứ hai và toàn bộ nhóm đủ điều kiện thì trả đúng một lượt. Nếu vé thứ hai là vắng mặt thì không coi nhóm đã hủy toàn bộ hợp lệ.

### Việc cần kiểm thử

- Nhóm hai vé miễn phí: hủy một vé chưa khôi phục, hủy toàn bộ đủ điều kiện khôi phục một lượt.
- Nhóm có cả vé miễn phí và vé trả tiền: không khôi phục sớm khi mới hủy vé miễn phí; phân biệt hoàn lượt voucher với hoàn tiền đã thu.
- Vé miễn phí vắng mặt không trả voucher; vé giảm về 0đ bằng voucher không tạo giao dịch trả điểm giả.
- Hủy đồng thời, gửi lại yêu cầu hoặc xử lý lại sự kiện không khôi phục trùng lượt voucher.

**Trạng thái:** Đã ghi nhận vấn đề 5. Cả năm vấn đề bổ sung trong đợt rà soát phần 003 của bản đề xuất cuối đã được chốt; chưa sửa spec/code.

## Tổng thu lúc đặt dưới 5.000đ: hoãn sang checkout

**Đã chốt (11/11/2026):** Nếu tổng khoản thu lúc đặt của cả giỏ từ 1đ đến 4.999đ, không tạo giao dịch VNPay và không chặn đặt chỗ. Khoản thu lúc đặt được coi là 0đ; toàn bộ số tiền đó chuyển vào số dư checkout và thu tại quầy bằng phương thức phù hợp.

- Xét theo tổng cả giỏ, không theo từng vé. Giỏ vé A 3.000đ + vé B 500.000đ vẫn thu 203.000đ qua VNPay; không tách 3.000đ của vé A ra để hoãn. Chỉ hoãn khi tổng thu lúc đặt của cả giỏ nằm trong 1đ–4.999đ (ví dụ chỉ có vé A 3.000đ, hoặc vé A cùng vé 0đ).
- Xác nhận như giỏ miễn phí: kiểm tra đủ điều kiện và đủ chỗ cho tất cả vé, xác nhận cả nhóm, chốt voucher/điểm và ghi lịch sử trong một giao dịch nội bộ; chống gọi lặp tạo thêm vé hoặc trừ chỗ/ưu đãi lần nữa.
- Mở hóa đơn với số đã thu lúc đặt 0đ và số dư bằng giá vé sau ưu đãi (ví dụ 3.000đ). Sửa `DirectBookingFolioOpeningService` theo điều kiện tổng quát: mở hóa đơn khi khoản phải thu lúc đặt của booking đã xác nhận hợp lệ bằng 0đ (do giá 0đ hoặc do hoãn dưới 5.000đ); vé có khoản thu lúc đặt từ 5.000đ vẫn cần bằng chứng VNPay đã xác minh.
- Khi checkout, thu số dư bằng tiền mặt hoặc chuyển khoản trực tiếp; nếu dịch vụ thêm làm số dư từ 5.000đ thì có thể thu qua VNPay theo mục thanh toán kết hợp.
- Giao diện trước khi xác nhận hiển thị rõ "Không cần thanh toán online, thanh toán [số tiền] tại quầy khi checkout". Không hiển thị nhãn `Cọc`/`Thanh toán toàn bộ` hoặc điều kiện "khoản trả lúc đặt không hoàn" cho giỏ này.
- Hủy/vắng: không có khoản đã thu để giữ lại hoặc hoàn. Vé vắng mặt thì đóng hóa đơn, không ghi nợ hay đòi phần giá vé chưa thu. Voucher/điểm theo quy tắc hủy/vắng đã chốt; vắng mặt không trả voucher.
- Đổi lịch làm tăng giá (kể cả sang mức thông thường phải cọc): không bắt trả bổ sung qua VNPay lúc đổi; phần tăng ghi vào số dư checkout theo quy tắc đổi lịch đã chốt.
- Lưu trên từng vé: khoản thu lúc đặt 0đ, lý do `hoãn dưới ngưỡng VNPay` và số dư phải thu, để hủy/checkout/báo cáo nhất quán.

### Việc cần kiểm thử

- Giỏ 3.000đ + vé 0đ: xác nhận không gọi VNPay, hóa đơn số dư 3.000đ.
- Giỏ 3.000đ + 500.000đ: vẫn thu 203.000đ qua VNPay.
- Tổng thu lúc đặt 4.999đ được hoãn; 5.000đ đi VNPay.
- Thu 3.000đ tiền mặt khi checkout; vé hoãn bị vắng thì đóng hóa đơn không ghi nợ.
- Gọi xác nhận lặp không tạo thêm vé, không trừ chỗ hoặc voucher/điểm hai lần.

**Trạng thái:** Đã ghi nhận; chưa sửa spec/code. Điểm có thể làm giá về 0đ khi Manager cấu hình trần đủ cao (xem mục 4).

## Thanh toán đến muộn sau khi hết giữ chỗ

**Đã chốt (12/11/2026):** Thêm khoảng đệm giữa hạn link VNPay và hạn giữ chỗ để giảm giao dịch đến muộn. Giao dịch vẫn đến sau khi hết giữ chỗ thì hệ thống tự xác nhận lại nhóm khi đủ điều kiện; các trường hợp còn lại chuyển Manager xử lý, hoàn tiền qua ngân hàng ngoài VNPay theo luồng hoàn thủ công đã chốt.

### Khoảng đệm thời hạn

- **Đã chốt (12/11/2026), thay mốc giữ chỗ 10 phút trước đó:** link thanh toán 10 phút, giữ chỗ 12 phút.
- Cả nhóm dùng chung một `holdExpiresAt` = 12 phút từ lúc giữ chỗ cả giỏ thành công. Sửa giỏ, tạo lại hoặc thử lại thanh toán không gia hạn.
- `vnp_ExpireDate` = `holdExpiresAt` − 2 phút (link hết hạn ở phút 10). QueryDR chạy ở phút 10 và 11; kết quả chờ/chưa rõ không gia hạn giữ chỗ.

### Hiển thị cho khách

- Khách chỉ thấy hạn của link thanh toán: đồng hồ đếm ngược 10 phút và câu "Vui lòng hoàn tất thanh toán trong 10 phút". Màn hình giỏ, xác nhận và email dùng cùng con số 10 phút; không hiển thị khoảng đệm 2 phút.
- Hết đồng hồ: không báo thất bại ngay mà chuyển sang "Đang xác nhận kết quả thanh toán…" trong khi hệ thống chạy QueryDR, đến `holdExpiresAt` thì báo kết quả cuối (thành công, hoặc hết hạn kèm nút đặt lại).

### Tự động xác nhận lại

Khi có bằng chứng VNPay hợp lệ (IPN có chữ ký hoặc QueryDR khớp số tiền, mã giao dịch) cho nhóm đã hết giữ chỗ, hệ thống tự xác nhận lại nhóm chỉ khi thỏa **tất cả**:

1. Mọi vé trong nhóm còn chỗ theo capacity và quota kênh bán; giữ chỗ cả nhóm trong một giao dịch nguyên tử, thiếu một vé thì không xác nhận phần nào.
2. Chưa ca nào của nhóm bắt đầu và còn ít nhất 2 giờ đến giờ bắt đầu sớm nhất.
3. Tính lại giá, voucher và điểm theo chính sách đã ghi nhận ra đúng số tiền đã thu; voucher còn lượt/còn hạn, điểm còn đủ.
4. Bằng chứng thanh toán đến trong vòng 60 phút sau `holdExpiresAt`.
5. Người đặt chưa có booking khác đang hiệu lực cho cùng ca (tránh trường hợp khách đã đặt lại giỏ mới).

Đủ điều kiện: xác nhận nhóm, chốt voucher/điểm, phát vé/QR, mở hóa đơn, gửi email như booking thường, ghi audit lý do `xác nhận thanh toán đến muộn`. Chống xử lý lặp khi IPN và QueryDR cùng về hoặc sự kiện được gửi lại. Không xác nhận một phần nhóm.

### Chuyển Manager xử lý

- Không đạt một trong các điều kiện trên: lưu bằng chứng thanh toán, lý do không đạt và chuyển Manager; gửi email báo người trả tiền rằng giao dịch đến sau hạn và đang được xử lý.
- Manager chọn một trong hai:
  - Xác nhận thủ công, chỉ khi không đạt điều kiện 4 hoặc 5 và người đặt xác nhận vẫn muốn tham dự; vẫn phải thỏa điều kiện 1–3 tại thời điểm xác nhận.
  - Hoàn 100% số tiền đã về qua ngân hàng theo luồng hoàn thủ công (thu thập tài khoản, Manager duyệt, chuyển khoản, đối soát, xác nhận `Đã hoàn`).
- Điều kiện 1–3 là điều kiện cứng; Manager không được bỏ qua. Không chuyển tiền của giao dịch cũ sang booking mới; khách muốn đặt lại thì tạo booking mới và thanh toán mới.

### Phần cần sửa

- Spec 003.1 phiên 31/10/2026 đang cấm Manager xác nhận/khôi phục booking, giữ lại chỗ hoặc phát vé cho thanh toán đến muộn, , `vnp_ExpireDate` đang bằng `holdExpiresAt`, giữ chỗ 10 phút và QueryDR ở phút 8/9: sửa theo quyết định này (link 10 phút, giữ chỗ 12 phút, QueryDR phút 10/11).
- FE-08 chỉ quy định chuyển xem xét thủ công và không tự mở lại phần giữ hàng; workshop khác FE-08 ở điểm tự xác nhận lại có kiểm tra đầy đủ. Không sửa FE-08 trong phạm vi này.

### Việc cần kiểm thử

- Tiền về ở phút 11 (trong hạn giữ chỗ) xác nhận bình thường; link hết hạn ở phút 10; khách thấy màn hình "đang xác nhận" từ phút 10 đến 12.
- Tiền về sau hết giữ chỗ, đủ 5 điều kiện: tự xác nhận đúng một lần, kể cả khi IPN và QueryDR cùng về.
- Thiếu chỗ một vé, ca còn dưới 2 giờ, voucher hết lượt, tiền về sau 60 phút, khách đã có booking khác cùng ca: chuyển Manager, không xác nhận.
- Manager xác nhận thủ công khi chỉ vướng điều kiện 4/5; bị chặn khi vướng điều kiện 1–3; hoàn qua ngân hàng không gọi VNPay Refund.

**Trạng thái:** Đã ghi nhận; chưa sửa spec/code.

## Mland hủy hoặc dời ca

**Đã chốt (12/11/2026):** Chuyển chính sách Mland hủy ca của spec 003.3 (đề xuất ca thay thế trước, không được thì hoàn 100% khoản đã thu; chính sách chụp lại lúc xác nhận) từ phạm vi cả booking sang từng vé trong nhóm.

- **Phạm vi:** chỉ các vé có lịch trong ca bị hủy. Vé gói hai buổi bị hủy một buổi thì xử lý cả vé. Các vé khác trong nhóm không bị ảnh hưởng.
- **Đề xuất ca thay thế:** đề xuất ca tương đương cho các vé bị ảnh hưởng; lần đổi này không tính vào giới hạn một lần đổi của khách và không thu thêm tiền. Vẫn kiểm tra capacity/quota và thứ tự/khoảng cách buổi của package như đổi lịch thường.
- **Hạn trả lời:** người mua trả lời trước giờ bắt đầu ca thay thế 24 giờ. Quá hạn không trả lời thì mặc định hoàn tiền; không tự đổi ca khi chưa có đồng ý của người mua.
- **Hoàn tiền:** người mua từ chối hoặc không có ca thay thế thì hoàn 100% các khoản đã thu được phân bổ cho vé đó (khoản trả lúc đặt và các khoản tại quầy), kể cả khi vé đã tham dự buổi trước của gói; qua ngân hàng theo luồng hoàn thủ công và thu thập tài khoản đã chốt.
- **Điểm:** trả lại điểm đã dùng của từng vé bị Mland hủy.
- **Voucher:** Mland hủy (toàn bộ hoặc một phần nhóm) thì trả lại lượt voucher của nhóm, vì lỗi thuộc về Mland. Mỗi lượt đã dùng chỉ được khôi phục tối đa một lần: nếu đã trả do Mland hủy một phần thì khi các vé còn lại bị hủy sau đó không trả thêm lượt. Phần giảm đã phân bổ cho các vé còn lại vẫn giữ nguyên. Không quy đổi giá trị voucher thành tiền; mã đã hết hạn/dừng không được gia hạn.
- **Thông báo:** gửi email cho người mua kèm ca thay thế và lựa chọn hoàn tiền. Người nhận vé (nếu vé đã được gửi riêng) được thông báo ca bị hủy; chỉ người mua chọn đổi ca hoặc hoàn tiền.
- **Kiểm thử:** hủy một ca ảnh hưởng một phần nhóm; vé hai buổi bị hủy một buổi; đổi ca thay thế không tính lần đổi; quá hạn trả lời thì hoàn; trả điểm theo vé; trả lượt voucher khi Mland hủy một phần và không trả lần hai khi các vé còn lại bị hủy.

**Trạng thái:** Đã ghi nhận; chưa sửa spec/code.

## Khách tự hủy vé

**Đã chốt (12/11/2026):** Chuyển chính sách khách tự hủy của spec 003.3 (FR-014/015: hủy khi chưa buổi nào bắt đầu và chưa dùng vé, không hoàn khoản đã trả lúc đặt) sang hủy theo từng vé, và dùng mốc 24 giờ để quyết định trả quyền lợi.

- **Hủy theo vé:** người mua chọn các vé cần hủy. Người nhận vé không tự hủy; có thể nhờ người mua hoặc đến shop theo luồng xác minh đã chốt. Mỗi vé được hủy khi vé chưa dùng và buổi đầu tiên của vé chưa bắt đầu. Hủy vé này không tự hủy các vé khác trong nhóm.
- **Không có hạn chót để hủy:** khách được hủy đến trước giờ bắt đầu buổi đầu tiên của vé. Hủy làm nhả chỗ của mọi buổi chưa diễn ra của vé để bán lại.
- **Tiền:** khoản đã trả lúc đặt của vé không hoàn (theo quyết định A), bất kể thời điểm hủy. Vé 0đ hoặc vé được hoãn thu dưới 5.000đ thì đóng hóa đơn, không ghi nợ.
- **Mốc 24 giờ cho quyền lợi** (tính đến giờ bắt đầu buổi đầu tiên của vé, cùng mốc với đổi lịch có báo trước):
  - Hủy trước từ 24 giờ trở lên là **hủy hợp lệ**: trả lại điểm đã dùng của vé.
  - Hủy khi còn dưới 24 giờ: vẫn hủy và nhả chỗ, nhưng quyền lợi xử lý như vắng mặt: không trả điểm, không tính là hủy hợp lệ để khôi phục voucher.
  - Voucher: chỉ khôi phục một lượt khi mọi vé trong nhóm đều đã hủy hợp lệ (từ 24 giờ trở lên) hoặc do Mland hủy; chỉ cần một vé hủy dưới 24 giờ hoặc vắng mặt thì không khôi phục.
- **Hủy và vắng mặt:** hủy là có báo trước giờ bắt đầu và nhả chỗ; vắng mặt là không báo, Staff ghi `NO_SHOW` sau ca. Cả hai không hoàn khoản đã trả lúc đặt.
- **Lưu trữ:** mỗi vé hủy lưu thời điểm yêu cầu, giờ bắt đầu buổi đầu, kết quả đủ/không đủ mốc 24 giờ, điểm/voucher được trả, người thao tác và kênh (online/tại shop). Chống xử lý lặp khi gửi lại yêu cầu.
- **Kiểm thử:** hủy một vé trong nhóm; hủy đúng mốc 24 giờ 00 phút và 23 giờ 59 phút; hủy sau khi buổi đầu đã bắt đầu bị từ chối; người nhận vé tự hủy bị từ chối; cả nhóm hủy hợp lệ khôi phục một lượt voucher, có một vé hủy dưới 24 giờ thì không; vé 0đ/vé hoãn thu bị hủy không ghi nợ; gửi lại yêu cầu không trả điểm hai lần.

**Trạng thái:** Đã ghi nhận; chưa sửa spec/code.

## Đổi lịch, điều kiện voucher và phân bổ ưu đãi

**Đã chốt (12/11/2026).**

### Giá vé khi đổi lịch

- Đổi lịch giữ nguyên package và giá package đã chốt lúc đặt (spec 003.3 FR-019). Giá vé sau đổi = giá package đã chốt − phần voucher của vé (nếu ca mới vẫn trong phạm vi voucher) − giá trị điểm đã dùng của vé.
- Đổi lịch không làm giá giảm nên không phát sinh hoàn chênh lệch. Đổi sang ca có voucher áp dụng được nhưng lúc đặt chưa dùng voucher thì không áp voucher mới.
- Ca mới ngoài phạm vi voucher (gói, ngày, ca, chi nhánh): vé mất phần giảm đã phân bổ; phần tăng được công bố trước khi xác nhận và ghi vào số dư checkout. Điểm đã dùng giữ nguyên.

### Điều kiện voucher khi hủy/đổi một phần

- Điều kiện cấp giỏ (ngưỡng tối thiểu, số người) chỉ kiểm tra lúc xác nhận nhóm. Hủy hoặc đổi một phần sau đó không tính lại và không thu hồi phần giảm của các vé còn lại.
- Điều kiện cấp vé (gói, ngày, ca, chi nhánh) được kiểm tra lại khi đổi lịch vé đó, theo mục trên.

### Thuật toán phân bổ và làm tròn

- Tiền là số nguyên VND. Phân bổ theo phương pháp phần dư lớn nhất: mỗi vé nhận phần làm tròn xuống theo tỷ lệ; phần dư còn lại cộng lần lượt 1 đơn vị cho các vé có phần lẻ lớn nhất; phần lẻ bằng nhau thì ưu tiên vé giá cao hơn, rồi thứ tự vé trong giỏ.
- **Voucher:** phân bổ phần giảm của nhóm cho các vé trong phạm vi voucher theo tỷ lệ giá vé. Phần giảm của một vé không vượt giá vé đó.
- **Điểm:** phân bổ theo số điểm nguyên, cùng thuật toán, theo tỷ lệ giá sau voucher; tiền giảm của vé = số điểm × giá trị điểm theo chính sách đã ghi nhận (mặc định 100đ). Vé đã về 0đ do voucher có trọng số 0.
- Khoản thu lúc đặt (thu đủ/cọc) của từng vé tính sau khi đã phân bổ voucher và điểm. Tổng phân bổ luôn bằng đúng tổng giảm của nhóm; kết quả lưu trên từng vé và không tính lại về sau.
- Ví dụ: giảm 100.000đ cho ba vé 300.000đ/300.000đ/400.000đ → 30.000đ/30.000đ/40.000đ. Ba vé cùng giá → 33.334đ/33.333đ/33.333đ (phần dư 1đ cho vé đầu tiên trong giỏ).

### Giới hạn số lần đổi

- Mỗi vé được 1 lần đổi do khách yêu cầu. Một lần đổi có thể dời buổi 1, buổi 2 hoặc cả hai trong cùng một thao tác.
- Đổi có báo trước (≥ 24 giờ) và hỗ trợ khách đến muộn dùng chung lượt này: vé đã đổi trước thì không được hỗ trợ đến muộn và ngược lại.
- Đổi cả nhóm cùng lúc: mỗi vé trong nhóm tính 1 lần. Đổi do Mland hủy/dời ca không tính.
- Manager được cấp thêm lượt đổi ngoài giới hạn khi có lý do, ghi audit; vẫn phải còn chỗ, đúng thứ tự/khoảng cách buổi của package và các kiểm tra đổi lịch khác.

### Việc cần kiểm thử

- Đổi trong phạm vi voucher giữ giá; đổi ra ngoài phạm vi tăng đúng phần giảm của vé và ghi vào số dư; đổi vào phạm vi voucher khi chưa dùng voucher không giảm.
- Hủy một vé làm giỏ dưới ngưỡng voucher: các vé còn lại giữ phần giảm.
- Phân bổ voucher/điểm có phần dư, phần lẻ bằng nhau, vé 0đ do voucher không nhận điểm; tổng phân bổ khớp tổng giảm.
- Vé hai buổi đổi cả hai buổi một lần; đổi lần hai bị từ chối; đổi trước rồi đến muộn không được hỗ trợ; đổi do Mland không tính lượt; Manager cấp thêm lượt có audit.

**Trạng thái:** Đã ghi nhận; chưa sửa spec/code.

## Vận hành tại quầy

### Checkout và hóa đơn còn nợ

**Đã chốt (12/11/2026):**

- Cổng checkout là điểm giao sản phẩm: chỉ giao sản phẩm workshop sau khi hóa đơn đã trả đủ và hoàn tất checkout. Hệ thống không cho hoàn tất checkout khi chưa đủ tiền; màn hình giao sản phẩm hiển thị rõ trạng thái hóa đơn.
- **Thứ tự ưu tiên phương thức tại quầy:** (1) VNPay QR — hệ thống tự xác minh qua IPN/QueryDR; (2) tiền mặt — Staff xác nhận tiền thực nhận; (3) chuyển khoản trực tiếp vào tài khoản shop — chỉ nhận khi có người có `workshop.payment.transfer.confirm` kiểm tra được tiền vào ngay lúc đó (kể cả xác nhận từ xa qua app ngân hàng). Giao diện checkout hiển thị phương thức theo thứ tự này.
- Khoản chưa được xác minh (VNPay đang chờ kết quả, hoặc khách đã chuyển khoản nhưng chưa ai kiểm tra được): hóa đơn ở `Chờ xác minh thanh toán`, lưu mã giao dịch khách cung cấp. Hệ thống không cho hoàn tất checkout và không cho giao sản phẩm; ảnh chuyển khoản của khách không đủ. Khách chờ xác minh, hoặc về trước và nhận sản phẩm sau; khi khoản được xác nhận đủ, hệ thống tự hoàn tất checkout.
- Giao sản phẩm trước khi xác minh là ngoại lệ: chỉ Manager được duyệt, bắt buộc lý do và audit; Staff không tự quyết.
- Còn nợ thực sự là ngoại lệ: chỉ Manager được ghi `Còn nợ`, bắt buộc lý do và audit. Không có nhắc nợ tự động, hạn tự đóng hay chặn đặt vé mới; Manager theo dõi qua báo cáo và đóng hóa đơn thủ công (thu đủ hoặc quyết định bỏ khoản nợ, có lý do).
- Điểm thưởng và quyền đánh giá chỉ tính khi vé đã checkout; hóa đơn đóng với khoản nợ bỏ qua không cộng điểm.

### Chốt sổ tiền mặt cuối ngày

**Đã chốt (12/11/2026):**

- Mỗi chi nhánh chốt sổ tiền mặt một lần mỗi ngày làm việc (giờ Việt Nam), khi đóng cửa, do Staff hoặc Manager có mặt tại chi nhánh thực hiện. Khoản tiền mặt thu sau khi đã chốt sổ tính vào ngày làm việc kế tiếp.
- Hệ thống tính tổng tiền mặt đã xác nhận trong ngày của chi nhánh (trừ tiền trả lại khách); người chốt nhập số tiền đếm thực tế. Khớp thì ghi nhận; lệch thì bắt buộc lý do và Manager xác nhận phần chênh.
- Không chốt giữa ca trong bản đầu; bàn giao tiền giữa các ca làm ngoài hệ thống. Mỗi khoản thu vẫn lưu người thu để truy vết.
- Sau khi chốt sổ, không sửa khoản tiền mặt của ngày đó; ghi sai thì sửa qua luồng sửa sai của Manager có lịch sử.
- Chuyển khoản và VNPay đối soát theo báo cáo riêng với sao kê/cổng thanh toán, không thuộc chốt sổ tiền mặt.

### Việc cần kiểm thử

- Checkout bị chặn khi còn số dư; `Chờ xác minh thanh toán` tự hoàn tất khi khoản chờ được xác nhận; chỉ Manager ghi `Còn nợ` và đóng hóa đơn nợ.
- Chốt sổ khớp và lệch; khoản thu sau khi chốt vào ngày kế tiếp; không sửa khoản tiền mặt đã chốt sổ ngoài luồng sửa sai.

### Nhiều chi nhánh

**Đã chốt (12/11/2026):**

- Một giỏ được chứa vé của nhiều chi nhánh và thanh toán chung một lần qua VNPay. Mỗi vé gắn chi nhánh của ca; hóa đơn, tham dự và checkout thực hiện tại chi nhánh đó.
- Voucher giới hạn chi nhánh chỉ giảm cho vé ở chi nhánh hợp lệ (theo FE-06).
- Staff chỉ thao tác trên vé của chi nhánh được gán; Manager thao tác mọi chi nhánh theo phân quyền hiện có.
- Tài khoản ngân hàng nhận chuyển khoản cấu hình theo chi nhánh, mặc định dùng chung một tài khoản. Khoản thu chuyển khoản ghi tài khoản nhận; yêu cầu hoàn ghi tài khoản chuyển đi do Manager chọn.

### Dữ liệu cũ

**Đã xác nhận (12/11/2026):** Hệ thống chỉ có dữ liệu dev/test, chưa có booking hoặc giao dịch thật. Không cần chuyển đổi dữ liệu booking cũ; thực hiện migration schema và reset/seed lại dữ liệu test. Quy tắc "không áp hồi tố lên booking cũ" vẫn giữ cho các thay đổi chính sách sau khi có dữ liệu thật.

### Việc cần kiểm thử (bổ sung)

- Giỏ nhiều chi nhánh thanh toán một lần; Staff chi nhánh A không thao tác vé chi nhánh B; voucher chi nhánh A không giảm vé chi nhánh B; tài khoản nhận theo chi nhánh.

### Phân quyền thu, hoàn và đối soát

**Đã chốt (12/11/2026):** Tách hai quyền liên quan tới tài khoản ngân hàng của shop, thay cho cách gọi chung "người có quyền kiểm tra tiền vào":

- `workshop.payment.transfer.confirm`: xác nhận tiền chuyển khoản của khách đã vào tài khoản shop (tiền vào). Manager có sẵn; Manager cấp thêm được cho Staff giữ app/sao kê ngân hàng.
- `workshop.refund.execute`: thực hiện chuyển khoản hoàn cho khách và ghi mã giao dịch/chứng từ (tiền ra). Manager có sẵn; Manager cấp thêm được cho Staff nếu shop muốn (ví dụ kế toán).
- Mặc định chưa cấp cho Staff nào; khi chưa cấp, chỉ Manager làm các việc này. Quyền cấp theo từng người, không tự cấp theo ca trực. Cấp/thu hồi quyền có audit.
- Quyền `transfer.confirm` không gắn với vị trí: người có quyền xác nhận từ xa sau khi thấy tiền vào trên app/sao kê ngân hàng. Khi không ai kiểm tra được, Staff mời khách thanh toán VNPay hoặc tiền mặt; nếu khách đã chuyển khoản thì ghi `Chờ xác minh thanh toán` kèm mã giao dịch và không giao sản phẩm cho tới khi xác nhận (trừ ngoại lệ Manager duyệt, xem mục "Checkout và hóa đơn còn nợ").
- **Hướng mở rộng (chưa làm ở bản đầu):** xác minh chuyển khoản trực tiếp tự động qua dịch vụ báo biến động số dư. Xem mục "Hướng mở rộng: tự động xác minh chuyển khoản".
- Duyệt yêu cầu hoàn và xác nhận `Đã hoàn` chỉ Manager. Một Manager được vừa duyệt vừa xác nhận `Đã hoàn` cùng một yêu cầu; mỗi bước lưu người thực hiện, thời điểm và chứng từ.

| Thao tác | Staff | Staff có `transfer.confirm` | Staff có `refund.execute` | Manager |
|---|:-:|:-:|:-:|:-:|
| Thu và xác nhận tiền mặt | ✓ | ✓ | ✓ | ✓ |
| Tạo thanh toán VNPay tại quầy | ✓ | ✓ | ✓ | ✓ |
| Xác nhận chuyển khoản đã vào tài khoản | | ✓ | | ✓ |
| Chốt sổ tiền mặt cuối ngày | ✓ | ✓ | ✓ | ✓ |
| Xác nhận chênh lệch chốt sổ | | | | ✓ |
| Ghi `Chờ xác minh thanh toán` | ✓ | ✓ | ✓ | ✓ |
| Ghi `Còn nợ`, đóng hóa đơn nợ | | | | ✓ |
| Sửa/hủy dòng ghi sai | | | | ✓ |
| Duyệt yêu cầu hoàn | | | | ✓ |
| Chuyển khoản hoàn và ghi chứng từ | | | ✓ | ✓ |
| Xác nhận `Đã hoàn` sau đối soát | | | | ✓ |
| Đổi lịch cho khách trong giới hạn | ✓ | ✓ | ✓ | ✓ |
| Cấp thêm lượt đổi | | | | ✓ |
| Xác nhận thủ công thanh toán đến muộn | | | | ✓ |
| Cấp/thu hồi hai quyền trên cho Staff | | | | ✓ |

Thao tác của Staff giới hạn trong chi nhánh được gán. Server kiểm tra quyền tại thời điểm thao tác; thiếu quyền thì từ chối không tác dụng phụ và ghi audit tối thiểu.

- **Kiểm thử:** Staff không có quyền xác nhận chuyển khoản bị từ chối; Staff có `transfer.confirm` không chuyển khoản hoàn được và ngược lại; Staff khác chi nhánh bị từ chối; thu hồi quyền có hiệu lực ngay; Manager vừa duyệt vừa xác nhận `Đã hoàn` có đủ lịch sử.

### Đổi email người nhận vé và QR

**Đã chốt (12/11/2026):**

- Đổi email người nhận: thu hồi QR cũ ngay và tạo QR mới gửi tới email mới; người mua vẫn xem được vé mới. QR đã thu hồi không check-in được.
- Gửi lại vé cho cùng email: giữ nguyên QR (giống quy tắc gửi lại Magic Link hiện có).
- Không đổi người nhận sau khi vé đã check-in. Mỗi vé đổi người nhận tối đa 3 lần; quá giới hạn thì đến shop nhờ Staff hỗ trợ.
- Chỉ người mua, hoặc Staff tại shop sau khi xác minh người mua, được đổi email người nhận. Lưu email cũ/mới (dạng che trong audit), người thao tác, kênh và thời điểm.
- **Kiểm thử:** QR cũ bị từ chối sau khi đổi; gửi lại cùng email giữ QR; đổi lần thứ 4 online bị từ chối; vé đã check-in không đổi được; người nhận vé tự đổi bị từ chối.

**Trạng thái:** Đã ghi nhận điểm 1–6 của nhóm vận hành; chưa sửa spec/code.

## Hướng mở rộng: tự động xác minh chuyển khoản

**Ghi nhận (12/11/2026):** Hướng mở rộng, không thuộc phạm vi bản đầu. Bản đầu vẫn xác minh chuyển khoản trực tiếp thủ công bởi người có `workshop.payment.transfer.confirm`.

### Cách hoạt động

- Dùng một dịch vụ trung gian báo biến động số dư (ví dụ SePay, Casso, VietQR callback). Dịch vụ theo dõi tài khoản nhận tiền của shop và gửi webhook về hệ thống khi có tiền vào, kèm mã giao dịch ngân hàng, số tiền, thời điểm và nội dung chuyển khoản.
- Chỉ cần ngân hàng nhận tiền của shop được dịch vụ hỗ trợ. Khách chuyển từ ngân hàng hoặc ví nào cũng được (qua liên ngân hàng). Hệ thống chỉ tích hợp với một dịch vụ trung gian, không kết nối API trực tiếp tới từng ngân hàng.
- Hóa đơn tạo mã VietQR có sẵn số tiền và nội dung chứa mã hóa đơn. Khi webhook về, hệ thống khớp mã hóa đơn và số tiền rồi tự xác nhận khoản thu như bằng chứng VNPay.
- Nhiều chi nhánh có tài khoản riêng thì liên kết từng tài khoản vào cùng dịch vụ.

### Quy tắc khi triển khai

- Xác thực webhook (token hoặc chữ ký); chống trùng theo mã giao dịch ngân hàng; webhook gửi lại không ghi nhận tiền hai lần.
- Không khớp được hóa đơn (sai nội dung, sai số tiền, hóa đơn đã đóng) thì không tự xác nhận; lưu khoản tiền vào danh sách chờ người có quyền xử lý hoặc chuyển luồng thu thừa/hoàn.
- Khoản tự xác minh có nguồn bằng chứng riêng (chuyển khoản qua dịch vụ trung gian), không gắn nhãn VNPay.
- Giữ luồng xác nhận thủ công làm phương án dự phòng khi dịch vụ gián đoạn.

### Cần đánh giá trước khi chọn dịch vụ

- Ngân hàng và loại tài khoản (cá nhân/doanh nghiệp) shop đang dùng để nhận tiền. Theo tài liệu các dịch vụ (kiểm tra 12/11/2026): VietinBank, MB Bank, ACB, TPBank có kết nối API qua SePay; VietinBank và BIDV (VA Billing) qua Casso; BIDV/Vietcombank chủ yếu qua tài khoản ảo cho doanh nghiệp. Danh sách cần xác nhận lại với nhà cung cấp.
- Chi phí, độ ổn định (kết nối API ổn định hơn chuyển tiếp SMS), và tác động phụ (ví dụ liên kết VietinBank qua Casso tắt SMS biến động số dư).
- Bảo mật: cấp cho bên thứ ba quyền đọc giao dịch tài khoản shop; phạm vi dữ liệu được chia sẻ và lưu trữ.

## Thanh toán kết hợp nhiều phương thức tại quầy

**Đã thống nhất (11/11/2026):** Khoản trả lúc đặt vé, bao gồm cọc hoặc khoản thu toàn bộ theo công thức đã chốt, vẫn qua VNPay nếu lớn hơn 0đ. Phần còn lại và dịch vụ thêm tại shop được thanh toán bằng VNPay, tiền mặt hoặc chuyển khoản trực tiếp vào tài khoản shop; được kết hợp nhiều phương thức trên cùng một hóa đơn. Giỏ miễn phí vẫn xác nhận nội bộ, không tạo giao dịch VNPay 0đ.

### Phạm vi và quyền xác nhận

- VNPay: hệ thống xác minh thanh toán qua bằng chứng hợp lệ từ cổng thanh toán; Staff không tự đánh dấu giao dịch VNPay thành công.
- Tiền mặt: Staff có quyền thu tiền xác nhận tiền thực nhận, lưu người thu và thời điểm. Nếu khách đưa dư, lưu tiền khách đưa và tiền trả lại; chỉ ghi phần thực dùng thanh toán vào hóa đơn.
- Chuyển khoản trực tiếp: người được phân quyền và có khả năng kiểm tra tiền vào tài khoản shop xác nhận. Ảnh chuyển khoản của khách không tự đủ điều kiện ghi nhận đã thu.
- Manager xử lý sửa sai và đối soát. Không yêu cầu Manager duyệt từng khoản tiền mặt thông thường.
- Hoàn tiền đủ điều kiện vẫn qua ngân hàng ngoài VNPay theo chính sách đã chốt, kể cả khoản thu ban đầu bằng tiền mặt. Mở thêm phương thức thu không mở thêm quyền được hoàn.

### Ghi nhận các khoản thu và số dư

- Một hóa đơn có nhiều khoản thanh toán, mỗi khoản ghi phương thức, số tiền, trạng thái, tham chiếu riêng, người trả/người xác nhận khi phù hợp và thời điểm. Các khoản tại quầy gắn đúng hóa đơn từng vé, không chỉ gắn với nhóm đặt chỗ.
- Số còn phải thu bằng tổng tiền hợp lệ của hóa đơn trừ phần trả lúc đặt đã phân bổ cho vé và các khoản tại quầy đã xác nhận. Khoản đang chờ xác minh chưa được tính là đã thu; điều chỉnh/hoàn phải theo luồng có lịch sử riêng để không trừ hoặc cộng hai lần.
- Cho phép trả từng phần trong quá trình checkout, không đồng nghĩa cho hoàn tất khi còn nợ. Chỉ hoàn tất hóa đơn khi đã đủ tiền, không còn giao dịch chờ cần xử lý và thỏa các điều kiện checkout khác.
- Ví dụ còn phải thu 350.000đ: tiền mặt 100.000đ, chuyển khoản 150.000đ và VNPay 100.000đ. Sau hai khoản đầu được xác nhận còn 100.000đ; chỉ khi khoản VNPay cuối được xác minh mới đủ tiền để hoàn tất.
- Sàn VNPay 5.000đ áp dụng cho từng giao dịch VNPay. Không áp sàn này cho tiền mặt; chuyển khoản trực tiếp theo khả năng của ngân hàng shop sử dụng. Số dư 1đ–4.999đ không được tạo giao dịch VNPay nhưng có thể thu đúng bằng phương thức tại quầy phù hợp, không cần viện dẫn ngoại lệ dữ liệu cũ.
- Mỗi lần chọn phương thức phải hiển thị số dư và các khoản đang chờ. Nếu chia tiền khiến phần còn lại dưới 5.000đ, hiển thị rõ phần đó phải dùng phương thức khác phù hợp hoặc thay đổi cách chia trước khi thu; không tự tăng tiền hoặc miễn phần lẻ.
- Công thức thu lúc đặt, voucher/điểm và giá tối thiểu add-on đã chốt được giữ nguyên. Việc bỏ sàn giá add-on không được suy ra từ quyết định mở phương thức thanh toán.

### Ngăn thu trùng và đối soát

- Khi tạo khoản thu, kiểm tra đồng thời các khoản đã thu và khoản đang chờ để không phân bổ cùng một số dư cho nhiều giao dịch. Khóa/cập nhật nguyên tử và chống xử lý lặp khi nhiều nhân viên thao tác hoặc có callback đồng thời.
- Đổi phương thức cho phần tiền đang chờ VNPay phải đối soát kết quả giao dịch đó trước; đóng màn hình hay ẩn QR không chứng minh giao dịch thất bại. Khoản có kết quả chưa rõ phải được giữ để xử lý, không tự ghi thành chưa thanh toán rồi thu lại cùng phần tiền.
- Nếu tiền thực tế vẫn về trùng hoặc vượt số phải thu, lưu đầy đủ khoản thực thu và chuyển Manager xử lý khoản dư/hoàn đủ điều kiện. Không bỏ qua bằng chứng tiền về, không hoàn tất hay cộng quyền lợi lần thứ hai.
- Ngăn ghi nhận lại một giao dịch ngân hàng hoặc một yêu cầu xác nhận tiền mặt; giữ chứng từ gốc và lịch sử điều chỉnh. Báo cáo tổng thu theo phương thức, khoản chờ xác minh và sai lệch để Manager đối soát.

### Phần project cần sửa và kiểm thử

- Sửa spec 003.3, hợp đồng API và mô hình bằng chứng thanh toán để chấp nhận khoản thu tại quầy được xác nhận đúng quyền, bên cạnh bằng chứng VNPay. Không giả lập IPN/QueryDR cho khoản tiền mặt hoặc ngân hàng.
- Bổ sung giao diện chia số tiền theo phương thức, xác nhận tiền mặt, chờ/xác nhận chuyển khoản và hiển thị số dư. Điều chỉnh điều kiện hoàn tất, thông báo và tính quyền lợi để chỉ xử lý một lần khi đủ điều kiện.
- Kiểm thử kết hợp cả ba phương thức, tiền mặt có tiền thừa, chuyển khoản chưa xác minh, số dư dưới 5.000đ, VNPay đúng 5.000đ, thao tác đồng thời, callback trễ, thu trùng, xác nhận lặp và phân quyền.
- Thay các kiểm thử cũ “từ chối trả từng phần” và “chỉ Manager thu ngân hàng dưới 5.000đ trong ngoại lệ” bằng các quy tắc mới ở mục này; giữ kiểm thử điều kiện giá add-on và sửa sai có lịch sử.

**Trạng thái:** Đã ghi nhận thay đổi được người dùng chốt; chưa sửa spec/code.
