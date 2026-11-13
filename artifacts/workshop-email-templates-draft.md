# Nội dung email workshop (đã duyệt 12/11/2026)

Người dùng đã duyệt nội dung ngày 12/11/2026. Giữ tên file `workshop-email-templates-draft.md` để các liên kết hiện có tiếp tục hoạt động; nội dung không còn chờ duyệt. Chưa triển khai gửi email trong code. Biến trong `{…}` do hệ thống điền. Quy tắc chung cho mọi email:

- Gửi từ địa chỉ của shop, tiêu đề bắt đầu bằng `MLAND –`.
- Không ghi số tài khoản đầy đủ, không ghi email người khác đầy đủ (che dạng `ng***@gmail.com`).
- Link riêng (Magic Link, link nhập tài khoản) kèm câu "Liên kết này là riêng của bạn, đừng chia sẻ cho người khác."
- Thời hạn thanh toán ghi **10 phút**; không nhắc khoảng đệm 2 phút.
- Cuối email: tên shop, hotline, địa chỉ chi nhánh của vé (nếu có).

## 1. Xác nhận đặt chỗ (gửi người mua)

**Tiêu đề:** MLAND – Xác nhận đặt chỗ workshop {mãNhóm}

> Xin chào {tênNgườiMua},
>
> Đặt chỗ workshop của bạn đã được xác nhận.
>
> **Mã đặt chỗ:** {mãNhóm}
>
> | Vé | Gói | Lịch | Chi nhánh | Đã thanh toán lúc đặt | Còn lại khi checkout |
> |---|---|---|---|---|---|
> | {mãVé} | {tênGói} | {ngày, giờ từng buổi} | {chiNhánh} | {sốTiền} ({Thanh toán toàn bộ / Cọc / Miễn phí / Thanh toán tại quầy}) | {sốDư} |
>
> Tổng đã thanh toán: {tổngĐãThu}. {Nếu hoãn: "Bạn không cần thanh toán online; vui lòng thanh toán {sốTiền} tại quầy khi checkout."}
>
> Xuất trình mã vé hoặc QR khi check-in. Bạn có thể gửi từng vé cho người đi cùng trong trang đặt chỗ.
>
> **Lưu ý:** khoản đã thanh toán lúc đặt không được hoàn khi bạn tự hủy hoặc không tham dự. Hủy trước giờ bắt đầu buổi đầu tiên của vé ít nhất 24 giờ thì điểm thưởng đã dùng được trả lại.
>
> [Xem đặt chỗ và vé]

## 2. Gửi vé cho người đi cùng (gửi người nhận)

**Tiêu đề:** MLAND – {tênNgườiMua} gửi bạn vé workshop {tênGói}

> Xin chào,
>
> {tênNgườiMua (che email)} đã gửi bạn một vé workshop tại MLAND.
>
> **Mã vé:** {mãVé} · **Gói:** {tênGói} · **Lịch:** {ngày, giờ} · **Chi nhánh:** {chiNhánh, địa chỉ}
>
> Xuất trình QR dưới đây khi check-in. Muốn đổi lịch hoặc hủy vé, vui lòng liên hệ người đã gửi vé hoặc đến quầy MLAND.
>
> [QR] [Xem vé]

## 3. Vé đã được chuyển cho người khác (gửi người nhận cũ)

**Tiêu đề:** MLAND – Vé {mãVé} không còn dùng được với email này

> Vé {mãVé} ({tênGói}, {lịch}) đã được người mua chuyển cho người khác. Mã QR cũ đã hết hiệu lực. Nếu bạn nghĩ đây là nhầm lẫn, vui lòng liên hệ người mua hoặc MLAND.

## 4. Gửi lại vé

Dùng lại nội dung email 1 (người mua) hoặc email 2 (người nhận), tiêu đề thêm "(gửi lại)". QR giữ nguyên.

## 5. Hết thời gian giữ chỗ

**Tiêu đề:** MLAND – Đặt chỗ {mãNhóm} đã hết thời gian thanh toán

> Chỗ giữ cho đơn {mãNhóm} đã được nhả vì chưa nhận được thanh toán trong 10 phút. Nếu bạn đã bị trừ tiền, chúng tôi sẽ xử lý và liên hệ với bạn. Bạn có thể đặt lại tại [Đặt lại workshop].

## 6. Tiền về sau khi hết giữ chỗ

**6a. Đã tự xác nhận lại:** dùng email 1, thêm dòng "Thanh toán của bạn đến sau thời hạn nhưng chỗ vẫn còn, đặt chỗ đã được xác nhận."

**6b. Đang xử lý:**

**Tiêu đề:** MLAND – Thanh toán cho đơn {mãNhóm} đến sau thời hạn

> Chúng tôi đã nhận {sốTiền} cho đơn {mãNhóm} sau khi thời gian giữ chỗ kết thúc. Nhân viên MLAND sẽ liên hệ với bạn trong vòng 24 giờ (hoặc trước giờ bắt đầu buổi workshop nếu buổi đó diễn ra sớm hơn) để xác nhận chỗ (nếu còn) hoặc hoàn lại toàn bộ số tiền qua chuyển khoản ngân hàng. Bạn không cần thanh toán lại.

**Đã chốt (12/11/2026):** mốc phản hồi 24 giờ, hoặc trước giờ bắt đầu buổi sớm nhất nếu sớm hơn. "Liên hệ" là khi khách nhận email kết quả: xác nhận chỗ (email 1/6a) hoặc yêu cầu tài khoản hoàn tiền (email 7).

## 7. Yêu cầu cung cấp tài khoản nhận hoàn (+ nhắc ngày 3, ngày 7)

**Tiêu đề:** MLAND – Cung cấp tài khoản để nhận hoàn {sốTiền} (đơn {mãNhóm})

> MLAND sẽ hoàn {sốTiền} cho vé {mãVé} với lý do: {lýDo}. Vui lòng nhập ngân hàng, số tài khoản và tên chủ tài khoản tại liên kết dưới đây trong 7 ngày. Vui lòng nhập tài khoản của chính người đã thanh toán và ghi đúng tên chủ tài khoản như ngân hàng hiển thị. MLAND đối chiếu tên trước khi chuyển; nếu không khớp, chúng tôi sẽ dừng lại và liên hệ với bạn.
>
> [Nhập tài khoản nhận hoàn]
>
> Bạn cũng có thể cung cấp tại quầy MLAND.

Email nhắc ngày 3 và ngày 7 dùng cùng nội dung, tiêu đề thêm "(nhắc lại)" và kèm liên kết mới (liên kết cũ hết hiệu lực).

**Cập nhật 13/11/2026 (owner 003 duyệt):** hệ thống không so tên chủ tài khoản với người thanh toán (không có nguồn tên hay số tài khoản thanh toán đáng tin). Manager duyệt số tiền, lý do và đơn hàng; người chuyển khoản so tên ngân hàng hiển thị cho số tài khoản với tên khách nhập, không khớp thì dừng và liên hệ khách. Staff vẫn có thể nhập tài khoản giúp khách tại quầy. Màn hình nhập tài khoản hiện chỉ dẫn như câu trong email trước các ô nhập.

## 8. Đã hoàn tiền

**Tiêu đề:** MLAND – Đã hoàn {sốTiền} cho đơn {mãNhóm}

> MLAND đã chuyển {sốTiền} vào tài khoản {ngânHàng} {****1234} ngày {ngày}. Mã giao dịch: {mãGiaoDịch}. Tùy ngân hàng, tiền có thể về trong 1–3 ngày làm việc.

## 9. MLAND hủy hoặc dời ca

**Tiêu đề (người mua):** MLAND – Buổi workshop {ngày} bị hủy, vui lòng chọn ca thay thế hoặc hoàn tiền

> Rất tiếc, buổi {tênGói} ngày {ngày, giờ} tại {chiNhánh} bị hủy vì {lýDo}. Các vé bị ảnh hưởng: {danhSáchVé}.
>
> Chúng tôi đề xuất ca thay thế: {ca}. Vui lòng trả lời trước {hạn = 24 giờ trước ca thay thế}:
>
> [Nhận ca thay thế] [Hoàn tiền]
>
> Nếu không trả lời trước hạn, MLAND sẽ hoàn 100% số tiền đã thanh toán cho các vé này. Điểm thưởng đã dùng và lượt voucher sẽ được trả lại.

**Người nhận vé:** chỉ thông báo buổi bị hủy, "người mua sẽ chọn ca thay thế hoặc hoàn tiền".

## 10. Xác nhận đổi lịch

**Tiêu đề:** MLAND – Đã đổi lịch vé {mãVé}

> Vé {mãVé} đã chuyển từ {lịchCũ} sang {lịchMới} tại {chiNhánh}. {Nếu tăng tiền: "Do lịch mới nằm ngoài phạm vi voucher, số tiền cần thanh toán thêm là {sốTăng}, sẽ thu khi checkout."} Đây là lần đổi duy nhất của vé này.

Trước khi xác nhận, màn hình (không phải email) hiển thị lịch mới và số tiền mới để người mua đồng ý.

## 11. Xác nhận hủy vé

**Tiêu đề:** MLAND – Đã hủy vé {mãVé}

> Vé {mãVé} ({tênGói}, {lịch}) đã được hủy lúc {thờiĐiểm}. {Nếu ≥ 24 giờ: "{sốĐiểm} điểm thưởng đã được trả lại."} {Nếu < 24 giờ: "Do hủy trong vòng 24 giờ trước giờ bắt đầu, điểm thưởng đã dùng không được trả lại."} Khoản đã thanh toán lúc đặt không được hoàn theo điều kiện đã công bố khi đặt.

## 12. Mã xác minh người mua (đã duyệt 13/11/2026)

Gửi tới email liên hệ ban đầu của đặt chỗ, không bao giờ gửi tới email người nhận vé. Dùng khi khách vãng lai (hoặc nhân viên tại quầy thay khách) đổi người nhận vé (003.1 FR-041).

**Tiêu đề:** MLAND – Mã xác minh đặt chỗ {mãNhóm}

> Xin chào,
>
> Mã xác minh để quản lý người nhận vé của đặt chỗ {mãNhóm} là:
>
> **{mãOTP}**
>
> Mã có hiệu lực trong 10 phút và chỉ dùng được một lần. Mã này là riêng của bạn, đừng chia sẻ cho người khác; nhân viên MLAND chỉ hỏi mã khi bạn đang ở quầy.
>
> Nếu bạn không yêu cầu mã, hãy bỏ qua email này; đặt chỗ của bạn không thay đổi.

## 13. Cảnh báo Manager: tiền về sau khi hết giữ chỗ (NHÁP 13/11/2026 — chờ owner 003 duyệt nội dung)

Email nội bộ, gửi ngay khi một late-payment review vào hàng chờ Manager (003.1 FR-040a). Không ghi email, số điện thoại hay tên của khách, không ghi dữ liệu thanh toán; Manager xem chi tiết trong màn hình quản lý.

**Tiêu đề:** MLAND – [Cần xử lý trước {hạnPhảnHồi}] Tiền về sau hạn, đơn {mãNhóm}

> Đơn {mãNhóm} vừa nhận {sốTiền} sau khi thời gian giữ chỗ kết thúc và hệ thống không tự xác nhận lại được.
>
> **Lý do:** {danhSáchĐiềuKiệnKhôngĐạt}
> **Buổi sớm nhất:** {ngày, giờ buổi sớm nhất} tại {chiNhánh}
> **Hạn gửi kết quả cho khách:** {hạnPhảnHồi} (24 giờ kể từ khi tiền về, hoặc trước giờ bắt đầu buổi sớm nhất nếu sớm hơn)
>
> {Nếu được phép xác nhận: "Có thể xác nhận chỗ nếu khách vẫn muốn tham gia và còn chỗ tại thời điểm duyệt."} {Nếu không: "Chỉ có thể hoàn tiền 100% qua chuyển khoản ngân hàng."}
>
> [Mở hàng chờ xử lý]

Tên điều kiện hiển thị: CAPACITY = "Hết chỗ", START_TIME = "Còn dưới 2 giờ trước buổi đầu", AMOUNT = "Số tiền không khớp giá đã ghi nhận", ARRIVAL_WINDOW = "Tiền về sau hơn 60 phút", DUPLICATE_BOOKING = "Khách đã có đặt chỗ khác trùng buổi".
