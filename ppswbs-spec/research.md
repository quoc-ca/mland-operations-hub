# Research — Phân loại luồng theo Features

Phân loại theo 10 major features trong Report 1. Đây là cách tổ chức tài liệu, không phải xác nhận các tính năng đã được triển khai. Nội dung luồng hiện có được giữ lại; phần chưa có chỉ ghi rõ trạng thái, chưa bổ sung thiết kế mới.

Mỗi luồng được mô tả tại một nơi. FE-01 và FE-07 có research riêng trong thư mục features tương ứng; các phần chưa tách vẫn giữ ở đây. Các điểm dùng chung được dẫn chiếu giữa feature; thanh toán không được tạo thành feature thứ 11.

<a id="fe-01"></a>

# FE-01 — Account Management

Nội dung nghiên cứu đã chuyển sang [research riêng của FE-01](features/001-account-management/research.md). Các tài liệu hồ sơ Member có sẵn được giữ trong thư mục nhóm này; chưa mở rộng spec/plan/tasks sang toàn bộ FE-01.

<a id="fe-02"></a>

# FE-02 — Product Management

Theo Report 1, feature này gồm sản phẩm bán sẵn và việc mua sản phẩm với thanh toán trước. Giữ luồng mua hàng và thanh toán bán lẻ tại đây để đọc liên tục. Ưu đãi tham chiếu FE-06; thông báo tham chiếu FE-07; lựa chọn nhận hàng và chuyển giao đơn phối hợp FE-08. Chưa mô tả riêng quản lý catalogue, tìm kiếm/lọc và quản trị sản phẩm.

## Mua hàng

### Phạm vi và quy tắc chung

1. Đây là bản thiết kế độc lập, tham khảo trải nghiệm chọn hàng và checkout kiểu Shopee; chưa phản ánh đầy đủ code hoặc thay thế tài liệu yêu cầu hiện có. Phạm vi gồm chọn hàng, giỏ hàng, checkout, tạo đơn và xác nhận thanh toán; chưa đi sâu shop chuẩn bị hàng, giao nhận, hủy đơn hoặc refund.
2. Khác với tài liệu yêu cầu cũ: khách được mua ngay không qua giỏ, checkout chỉ các dòng tích chọn, hệ thống chọn nơi xử lý khi giao hàng và cho phép đơn 0 đồng hợp lệ.
3. Khách được xem danh mục, giá và chi tiết sản phẩm khi chưa đăng nhập. Trước khi thêm giỏ hoặc mua ngay, khách phải đăng nhập, xác minh email và có tài khoản được Backend cho phép sử dụng.
4. Frontend giữ lựa chọn sản phẩm, biến thể, số lượng và thao tác đang làm trong quá trình xác thực. Sau khi khách đăng nhập/xác minh email, tải lại danh tính Firebase và lấy token mới; Backend kiểm tra lại điều kiện trước khi tiếp tục. Không coi dữ liệu giữ trên trình duyệt là giá, tồn kho hoặc quyền đã được chấp nhận.
5. Backend là nơi quyết định giá, khả năng bán, lượng hàng khả dụng, ưu đãi và tổng tiền. Xem giỏ, thêm giỏ hoặc mở checkout chưa tạo đơn, giữ hàng, tiêu điểm hoặc sử dụng voucher.
6. Mua ngay và mua qua giỏ dùng chung quy trình checkout và tạo đơn. Trang checkout là trang xác nhận đặt hàng trước khi tạo đơn; trang chi tiết đơn dùng sau khi đã có đơn, kể cả đơn đang chờ thanh toán.

### Chọn sản phẩm

1. Khách vào landing page hoặc danh mục, chọn xem chi tiết sản phẩm.
2. Frontend hiển thị sản phẩm, các biến thể được cấu hình, giá hiện tại và tình trạng khả dụng do Backend cung cấp.
3. Khách chọn đầy đủ thuộc tính của một biến thể, nhập số lượng nguyên dương, rồi chọn **Mua ngay** hoặc **Thêm vào giỏ hàng**.
4. Frontend kiểm tra đã chọn đủ thuộc tính và số lượng hợp lệ. Backend kiểm tra lại sản phẩm còn được bán, biến thể tồn tại và lượng hàng khả dụng trước khi chấp nhận thao tác.
5. Nếu khách chưa đủ điều kiện xác thực, Frontend hướng dẫn đăng nhập/xác minh theo quy tắc chung, giữ lựa chọn để tiếp tục. Sau xác thực phải kiểm tra lại sản phẩm và lượng khả dụng, không tự đặt hàng.

### Mua ngay — Luồng thành công

1. Khách chọn biến thể, số lượng và bấm **Mua ngay**.
2. Frontend gửi lựa chọn để Backend kiểm tra điều kiện tài khoản, sản phẩm và lượng khả dụng.
3. Khi hợp lệ, Frontend mở checkout chỉ với sản phẩm, biến thể và số lượng vừa chọn, đánh dấu nguồn mua là Mua ngay.
4. Hệ thống không thêm sản phẩm vào giỏ, không xóa hoặc sửa các dòng đã có trong giỏ. Khách tiếp tục quy trình checkout chung bên dưới.

### Mua qua giỏ hàng — Luồng thành công

1. Khách chọn biến thể, số lượng và bấm **Thêm vào giỏ hàng**.
2. Backend kiểm tra tài khoản và lựa chọn; nếu giỏ đã có cùng sản phẩm/biến thể thì cộng số lượng trong giới hạn hợp lệ, nếu chưa có thì thêm dòng mới. Chỉ ghi nhận ý định mua, chưa giữ hàng hoặc giá.
3. Frontend thông báo thêm giỏ thành công. Khách có thể tiếp tục xem và thêm sản phẩm khác.
4. Khách mở giỏ; Backend chỉ trả giỏ thuộc tài khoản hiện tại cùng giá và tình trạng sản phẩm hiện hành.
5. Khách tăng/giảm số lượng hoặc xóa dòng. Backend kiểm tra và lưu thay đổi trước khi Frontend cập nhật kết quả; không hiển thị đã lưu khi chưa nhận xác nhận.
6. Khách tích chọn những dòng muốn mua. Các dòng chưa chọn vẫn ở trong giỏ và không được tính vào checkout.
7. Khách bấm **Tiếp tục thanh toán**. Nếu chưa chọn dòng nào, Frontend yêu cầu chọn ít nhất một dòng.
8. Backend kiểm tra lại đúng các dòng được chọn và quyền sở hữu giỏ. Frontend mở checkout với các dòng này; chưa loại bỏ chúng khỏi giỏ.

### Checkout — Thông tin nhận hàng

1. Backend lấy lại lựa chọn từ nguồn Mua ngay hoặc các dòng giỏ đã chọn, kiểm tra điều kiện tài khoản và hiển thị giá hiện tại. Frontend không gửi tổng tiền tự tính làm căn cứ tạo đơn.
2. Frontend hiển thị sản phẩm, biến thể, số lượng, đơn giá, thành tiền, thông tin người nhận, phương thức nhận, ưu đãi và tổng thanh toán.
3. Khách nhập hoặc chọn thông tin người nhận và chọn **Tự nhận tại cửa hàng** hoặc **Giao hàng qua GHTK**. Thông tin có sẵn chỉ là dữ liệu điền trước; khách cần kiểm tra lại.
4. Nếu tự nhận, khách cung cấp tên, số điện thoại và chọn cửa hàng còn hoạt động. Backend kiểm tra cửa hàng đó đủ hàng cho toàn bộ lựa chọn; phí giao hàng bằng 0.
5. Nếu giao GHTK, khách cung cấp tên, số điện thoại và địa chỉ giao đầy đủ theo dữ liệu địa chỉ tích hợp hỗ trợ. Backend chọn một cửa hàng còn hoạt động, đủ hàng cho toàn bộ lựa chọn theo thứ tự ưu tiên cửa hàng đã cấu hình.
6. Backend dùng nơi gửi, nơi nhận, trọng lượng và các thông tin gói hàng cần thiết để lấy báo giá GHTK; Frontend hiển thị phí và thông tin giao dự kiến nếu nhà cung cấp trả về. Phí không được giả định chỉ dựa trên khoảng cách.
7. Nếu không có một cửa hàng đáp ứng toàn bộ lựa chọn, yêu cầu khách sửa sản phẩm, số lượng hoặc phương thức nhận; không tự chia nhiều đơn hoặc tự chuyển một phần sang nơi khác.
8. Khi khách thay đổi địa chỉ, cửa hàng, cách nhận hoặc nội dung mua, Backend tính lại điều kiện giao và phí. Báo giá thay đổi phải được hiển thị trước khi khách xác nhận đặt hàng.

### Checkout — Voucher, điểm thưởng và tổng tiền

Luồng này được phân loại vào [FE-06 — Loyalty & Promotion Management](#fe-06). Sau khi xem và xác nhận báo giá, tiếp tục bước Tạo đơn bên dưới.

### Tạo đơn — Kiểm tra và ghi nhận chung

1. Frontend gửi yêu cầu xác nhận lựa chọn cùng dấu hiệu nhận diện yêu cầu để Backend chống tạo trùng; khóa nút trong lúc chờ. Backend không chỉ dựa vào việc khóa nút để chống gửi lặp.
2. Backend kiểm tra lại danh tính, email đã xác minh, trạng thái tài khoản, quyền sở hữu các dòng giỏ nếu có, sản phẩm/biến thể, số lượng, giá, nơi xử lý, báo giá giao và ưu đãi.
3. Nếu báo giá hoặc điều kiện khác bản khách đã xem, Backend yêu cầu xem lại và xác nhận mới; chưa tạo đơn với điều khoản thay đổi.
4. Khi hợp lệ, Backend ghi nhận một đơn cùng bản chụp nguồn mua, các dòng mua, biến thể, số lượng, giá, người nhận, địa chỉ/cửa hàng nhận, nơi xử lý, ưu đãi, chính sách áp dụng và tổng tiền. Thay đổi hồ sơ, catalogue hay giỏ sau đó không sửa các thông tin đã chấp nhận của đơn.
5. Backend tạo đơn và giữ hàng đúng biến thể tại nơi xử lý cùng quyền dùng voucher/điểm theo nguyên tắc toàn bộ hoặc không có gì. Nếu tranh mua sản phẩm cuối hoặc ưu đãi hết lượt thì từ chối an toàn, không tạo đơn được chấp nhận một phần.
6. Gửi lại cùng yêu cầu và cùng nội dung trả về đơn đã tạo cùng thời hạn gốc; nếu dùng lại dấu hiệu nhận diện với nội dung khác thì từ chối và yêu cầu xem lại. Mất phản hồi không phải lý do tự tạo đơn mới.
7. Với tổng dương đủ điều kiện theo cấu hình tích hợp VNPay, đơn chuyển **Chờ thanh toán**, ghi thời điểm tạo đơn, hạn link sau 10 phút và hạn giữ hàng/ưu đãi sau 15 phút; tiếp tục mục Thanh toán.
8. Với tổng cuối cùng bằng 0 hợp lệ, xử lý theo nhánh đơn 0 đồng bên dưới; tổng âm hoặc số tiền dương không đạt điều kiện tích hợp phải được sửa trước khi chấp nhận đơn.

### Đơn 0 đồng — Xác nhận trực tiếp

1. Backend kiểm tra tổng cuối cùng, bao gồm phí giao hàng, thực sự bằng 0 theo chính sách hợp lệ; tiền hàng bằng 0 nhưng còn phí giao thì vẫn là đơn cần thanh toán.
2. Backend xác nhận đơn, chốt hàng và tiêu dùng voucher/điểm đúng một lần trong cùng thao tác toàn bộ hoặc không có gì; chuyển **Đã xác nhận — Chờ xử lý**.
3. Hệ thống không tạo yêu cầu hoặc giao dịch VNPay, không ghi nhận khách đã chuyển tiền qua cổng thanh toán.
4. Nếu mua qua giỏ, Backend dọn đúng lượng đã mua theo quy tắc dọn giỏ trong mục Thanh toán; nếu Mua ngay thì giữ nguyên giỏ.
5. Frontend hiển thị chi tiết đơn 0 đồng đã xác nhận; hệ thống gửi email xác nhận. Gửi email lỗi không đảo kết quả xác nhận đơn.

### Mua hàng và checkout — Nhánh lỗi

1. Sản phẩm ngừng bán, thiếu thuộc tính, biến thể không hợp lệ, số lượng không phải số nguyên dương hoặc hết hàng: chỉ rõ dòng và nguyên nhân để khách sửa; không tự đổi biến thể, giảm số lượng hoặc đặt các dòng còn lại.
2. Quyền truy cập không hợp lệ hoặc tài khoản bị khóa: từ chối thao tác bảo vệ; không tiết lộ giỏ hay đơn của tài khoản khác. Nếu cần đăng nhập/xác minh lại thì giữ lựa chọn để tiếp tục sau khi đủ điều kiện và kiểm tra lại dữ liệu.
3. Giỏ rỗng, chưa tích chọn hoặc dòng đã bị sửa/xóa ở nơi khác: tải dữ liệu hiện tại, yêu cầu chọn và xác nhận lại; không lấy toàn bộ giỏ thay cho lựa chọn cũ.
4. Thông tin nhận hàng thiếu hoặc không hợp lệ: hiển thị lỗi tại trường tương ứng, chưa tạo đơn.
5. Cửa hàng ngừng hoạt động hoặc không đủ hàng: yêu cầu đổi cửa hàng/cách nhận hoặc sửa lựa chọn; không tự chấp nhận một phần đơn.
6. GHTK không báo được phí, ngoài vùng phục vụ hoặc dữ liệu gói hàng chưa đủ: giữ thông tin khách đã nhập, cho sửa hoặc thử lại/chọn tự nhận; không tự cho phí giao bằng 0.
7. Voucher hoặc điểm không hợp lệ, hết lượt, không đủ số dư hoặc thiếu chính sách: báo rõ, giữ lựa chọn để khách sửa. Chỉ tiếp tục không ưu đãi khi khách chủ động chấp nhận báo giá mới.
8. Mất mạng hoặc lỗi lưu giỏ: phân biệt thao tác chưa được xác nhận với giỏ rỗng; cho tải lại/thử lại mà không tự cộng số lượng lần nữa khi kết quả lưu trước chưa rõ.
9. Bấm Đặt hàng lặp hoặc mất phản hồi tạo đơn: Frontend tra lại kết quả yêu cầu cũ; Backend trả lại đơn đã tạo nếu có. Không tạo thêm đơn, giữ thêm hàng hoặc kéo dài thời hạn vì thử lại.

Thanh toán VNPay dưới đây là phần tích hợp dùng chung: tại FE-02 mô tả trường hợp đơn bán lẻ; FE-03 và FE-05 cần luồng riêng cho nghĩa vụ thanh toán của booking/đơn custom, không mặc định dùng nguyên quy tắc bán lẻ.

## Thanh toán

### Điều kiện và thời hạn

1. Luồng VNPay áp dụng cho đơn Chờ thanh toán có tổng dương hợp lệ, thuộc khách hiện tại và còn thời hạn. Đơn 0 đồng đi theo nhánh xác nhận trực tiếp ở mục Mua hàng.
2. Link thanh toán và mọi lần thanh toán lại hết hạn sau 10 phút tính từ lúc tạo đơn; giữ hàng và quyền dùng ưu đãi hết hạn sau 15 phút tính từ cùng thời điểm. Không gia hạn khi tải lại trang, bấm lại hoặc tạo lần thanh toán khác.
3. Giữ hàng/ưu đãi chưa đồng nghĩa đã bán hàng, trừ điểm hoặc dùng hết lượt voucher. Các tác động đó chỉ được chốt một lần sau khi xác nhận thanh toán hợp lệ.
4. Backend quản lý trạng thái đơn và từng lần thanh toán riêng. Mỗi lần có tham chiếu VNPay riêng; không coi nút hủy hoặc trang trình duyệt quay về là bằng chứng đủ để kết luận kết quả giao dịch.

### Thanh toán VNPay — Luồng thành công

1. Sau khi tạo đơn hợp lệ, Backend ghi nhận lần thanh toán và tạo URL VNPay chứa tham chiếu, số tiền đúng của đơn, thời hạn gốc và thông tin kiểm tra toàn vẹn theo tích hợp.
2. Backend trả mã đơn và URL; Frontend chuyển khách sang VNPay. Nếu tạo URL lỗi sau khi đơn đã được ghi nhận, hiển thị đúng đơn Chờ thanh toán để khôi phục, không tự tạo lại đơn.
3. Khách chọn ngân hàng, thẻ hoặc phương thức VNPay đang hỗ trợ theo cấu hình tích hợp, rồi hoàn tất thanh toán trên cổng.
4. VNPay gửi kết quả về Backend qua IPN và chuyển trình duyệt về website. Hai luồng có thể đến khác thứ tự; Backend có thể truy vấn kết quả qua API VNPay để đối soát khi cần.
5. Backend kiểm tra chữ ký, thông tin merchant, tham chiếu, số tiền, mã kết quả/trạng thái giao dịch và giao dịch đã xử lý. Chỉ chấp nhận bằng chứng IPN hoặc kết quả truy vấn đã xác thực; dữ liệu trình duyệt quay về không tự đánh dấu đơn đã thanh toán.
6. Khi xác nhận thành công và tài nguyên của đơn còn được giữ hợp lệ, Backend chốt lượng hàng, dùng voucher/điểm đã giữ, ghi nhận thanh toán và chuyển đơn **Đã thanh toán — Chờ xử lý** theo nguyên tắc toàn bộ hoặc không có gì. Thông báo lặp không gây chốt thêm lần nữa.
7. Nếu kết quả đến sau hạn link nhưng trước hạn giữ, Backend đối chiếu thời điểm thanh toán hợp lệ với hạn link gốc trước khi chốt; nếu có bất thường thì chuyển đối soát, không chỉ dựa vào thời điểm nhận IPN để kết luận.
8. Với đơn mua qua giỏ, Backend chỉ loại bỏ lượng đã mua thuộc các dòng checkout, dựa trên nguồn và phiên bản dòng đã ghi nhận. Giữ các dòng chưa mua và lượng khách thêm sau; không tạo lại dòng khách đã xóa hoặc trừ nhầm dòng thay thế. Nếu dòng đã đổi theo cách không thể đối chiếu an toàn, giữ dòng đó để khách xem lại thay vì đoán số lượng cần xóa. Dọn giỏ chỉ áp dụng một lần và lỗi dọn giỏ không đảo giao dịch đã thành công.
9. Với đơn Mua ngay, hệ thống không dọn hoặc thay đổi giỏ, kể cả giỏ đang chứa cùng biến thể.
10. Frontend lấy trạng thái đơn từ Backend và hiển thị trang chi tiết cùng kết quả thanh toán. Hệ thống gửi email xác nhận; lỗi gửi thư được xử lý lại riêng, không yêu cầu khách thanh toán lại.

### Chưa rõ kết quả hoặc khách chưa quay về

1. Nếu trình duyệt quay về trước khi Backend có xác nhận, Frontend hiển thị **Đang xác nhận thanh toán** và lấy lại trạng thái từ Backend; không báo thành công chỉ dựa vào URL.
2. Backend tiếp nhận IPN hoặc truy vấn VNPay để xác định kết quả. Chưa cho tạo lần thanh toán mới khi lần trước còn chưa rõ, tránh khách trả tiền hai lần.
3. Nếu khách đóng trang, mất mạng hoặc không quay lại website, Backend vẫn xử lý thông báo hợp lệ. Khách mở lại chi tiết đơn sẽ thấy trạng thái do Backend xác nhận.
4. Nếu xác định giao dịch thành công, tiếp tục chốt đơn theo luồng thành công; nếu xác định thất bại hoặc chưa thanh toán thì áp dụng nhánh thanh toán lại/hết hạn.

### Thanh toán thất bại, hủy trên cổng và thanh toán lại

1. Khách hủy trên VNPay hoặc giao dịch lỗi: Frontend hiển thị kết quả Backend đã xác nhận; nếu chưa xác định được thì giữ **Đang xác nhận thanh toán**. Hủy một lần thanh toán không tự hủy đơn.
2. Khi Backend xác nhận lần trước thất bại hoặc đã kết thúc mà chưa thanh toán, khách được chọn **Thanh toán lại** cho cùng đơn nếu chưa hết hạn link gốc và tài nguyên còn được giữ.
3. Backend kiểm tra lại quyền truy cập, trạng thái đơn, tài nguyên giữ và kết quả lần trước, rồi tạo một lần thanh toán với tham chiếu mới. Không tạo đơn mới, không đổi giá/ưu đãi đã chốt và không gia hạn link hoặc giữ hàng.
4. Nếu đã thanh toán, Backend trả kết quả hiện có thay vì tạo thêm lần thanh toán. Nếu lần trước còn chưa rõ, yêu cầu chờ đối soát.
5. Nếu đã hết 10 phút từ lúc tạo đơn, không cho tạo lần thanh toán mới. Khách chỉ có thể tạo checkout mới sau khi tình trạng đơn cũ được làm rõ, với kiểm tra và báo giá mới đầy đủ.

### Hết hạn giữ hàng và ưu đãi

1. Khi link hết hạn sau 10 phút, Frontend thông báo không thể tiếp tục thanh toán mới bằng đơn này; Backend vẫn tiếp nhận và xác minh kết quả giao dịch đã phát sinh.
2. Đến hạn giữ sau 15 phút, Backend đối soát các lần thanh toán và phối hợp xử lý hết hạn với xử lý xác nhận thanh toán để tránh cùng lúc vừa chốt vừa giải phóng hàng/ưu đãi.
3. Nếu xác định đơn chưa được thanh toán, Backend chuyển đơn **Hết hạn thanh toán**, giải phóng hàng và quyền dùng voucher/điểm đúng một lần. Không tiêu điểm, dùng lượt voucher hoặc dọn giỏ cho đơn hết hạn.
4. Nếu đã xác nhận thanh toán hợp lệ, xử lý theo luồng thành công và không giải phóng tài nguyên của đơn đã chốt.
5. Nếu chưa xác định được do dịch vụ thanh toán lỗi hoặc kết quả không nhất quán, ghi nhận **Cần đối soát**, tạm ngừng thanh toán mới và chuyển xử lý vận hành; không tự kết luận chưa thanh toán hoặc giải phóng tài nguyên khi chưa đủ bằng chứng. Đây là ngoại lệ cần theo dõi, không tạo lại thời hạn 15 phút mới.

### Thông báo bất thường hoặc thanh toán đến muộn

1. Chữ ký không hợp lệ, sai merchant, sai số tiền hoặc tham chiếu không khớp: Backend không dùng thông báo đó để đánh dấu đơn đã thanh toán; ghi nhận an toàn để kiểm tra và trả phản hồi phù hợp cho cổng.
2. IPN lặp hoặc nhận lại cùng kết quả truy vấn: trả kết quả xử lý đã ghi nhận, không trừ hàng/điểm, tiêu voucher, dọn giỏ hoặc xác nhận đơn thêm lần nữa.
3. Thanh toán thành công được xác thực nhưng tài nguyên đã giải phóng, đơn đã hết hạn hoặc thời điểm thanh toán ngoài điều kiện cho phép: ghi nhận khoản tiền thực nhận và chuyển **Cần xử lý thanh toán**. Không tự phục hồi đơn hoặc bán vượt tồn.
4. Có nhiều giao dịch thực sự thành công cho cùng đơn: chỉ chốt đơn một lần, ghi riêng khoản tiền trả trùng và chuyển xử lý thanh toán; không nhầm với việc nhận lặp một thông báo cho cùng giao dịch.
5. Nếu đơn đã được chốt thành công trước đó, giữ nguyên kết quả chốt và đánh dấu sự cố thanh toán kèm theo để xử lý; không đưa đơn đã thành công về Chờ thanh toán.
6. Những khoản cần xử lý được chuyển sang quy trình đối soát/Refund sau này. Không tự hoàn tiền, bỏ qua khoản tiền nhận được hoặc yêu cầu khách trả thêm trong luồng này.

### Tình huống cần kiểm tra khi triển khai

1. Mua ngay không thay đổi giỏ; mua qua giỏ chỉ lấy các dòng tích chọn; khách phải đủ điều kiện đăng nhập và xác minh trước khi thêm giỏ hoặc mua ngay.
2. Tự nhận đúng cửa hàng đủ hàng và phí 0; giao GHTK báo đúng phí; không có một nơi đủ toàn bộ hàng thì không tự chia đơn.
3. Không ưu đãi, một voucher, chỉ điểm, voucher kết hợp điểm theo chính sách; tổng 0 đồng xác nhận trực tiếp, còn phí giao thì vẫn cần VNPay.
4. Hai khách tranh sản phẩm cuối hoặc ưu đãi cuối; điều kiện thay đổi trước khi đặt; lỗi một dòng không tạo đơn được chấp nhận một phần.
5. Bấm Đặt hàng lặp, mất phản hồi sau khi tạo đơn, tạo URL thanh toán lỗi và gửi lại cùng dấu hiệu nhận diện với nội dung khác.
6. Trình duyệt quay về trước IPN, IPN đến trước trình duyệt, khách không quay lại, IPN lặp, chữ ký/số tiền sai và nhiều khoản thanh toán thực sự thành công.
7. Thất bại đã xác nhận được thanh toán lại cùng đơn trước hạn gốc; trạng thái chưa rõ không được thanh toán lại; quá 10 phút không tạo lần mới; quá 15 phút xử lý hết hạn không tranh chấp với chốt đơn.
8. Kết quả đến muộn sau khi giải phóng tài nguyên và lỗi đối soát không tự phục hồi đơn, bán vượt tồn hoặc bỏ sót tiền đã nhận.
9. Dọn giỏ sau thành công giữ dòng khác, lượng thêm sau và thao tác sửa/xóa sau checkout; xử lý lặp không dọn hai lần. Lỗi email hoặc dọn giỏ không đảo thanh toán đã xác nhận.

### Tài liệu tham khảo mua hàng và thanh toán

1. [VNPay — Tích hợp thanh toán, IPN và Return URL](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
2. [VNPay — API truy vấn kết quả giao dịch](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).
3. [GHTK — API tính phí giao hàng](https://api.ghtk.vn/docs/submit-order/calculate-shipping-fee/).

<a id="fe-03"></a>

# FE-03 — Workshop Management

Chưa có nội dung chi tiết. Mục Booking trước đây được đặt tại feature này.

## Booking

Chưa mô tả luồng.

<a id="fe-04"></a>

# FE-04 — AI Chatbox Analysis & Suggestion

Chưa mô tả luồng AI/chat, consent ảnh và phân tích/gợi ý.

<a id="fe-05"></a>

# FE-05 — Personalized Design & Quote Workflow

Chưa mô tả luồng thiết kế cá nhân hóa, review, báo giá và đơn đặt làm.

<a id="fe-06"></a>

# FE-06 — Loyalty & Promotion Management

Đã có luồng sử dụng ưu đãi tại checkout bên dưới, được chuyển từ mục Mua hàng. Luồng bắt đầu từ checkout ở FE-02 và quay về bước Tạo đơn sau khi khách xác nhận báo giá. Chưa mô tả quản lý chương trình, hạng thành viên, tích điểm và lịch sử điểm.

## Checkout — Voucher, điểm thưởng và tổng tiền

1. Khách chọn không dùng ưu đãi hoặc dùng tối đa một voucher giảm tiền hàng. Chưa hỗ trợ voucher vận chuyển riêng trong luồng này.
2. Backend kiểm tra voucher theo chính sách đã cấu hình: hiệu lực, phạm vi sản phẩm, điều kiện đơn, giới hạn sử dụng và điều kiện kết hợp. Không tự đặt mức giảm hoặc giới hạn khi thiếu chính sách.
3. Sau khi tính voucher, khách có thể chọn dùng loyalty point. Backend kiểm tra số điểm khả dụng và chính sách quy đổi/giới hạn, áp dụng điểm trên phần tiền hàng còn lại; chỉ kết hợp khi chính sách cho phép.
4. Backend tính: **Tổng thanh toán = Tiền hàng − Giảm voucher − Giảm bằng điểm + Phí giao hàng**. Các khoản giảm không được làm tiền hàng âm; voucher tiền hàng và điểm không giảm phí giao trong phạm vi này.
5. Frontend hiển thị rõ tiền hàng, từng khoản giảm, phí giao và tổng cuối cùng bằng VND. Nếu không dùng ưu đãi thì vẫn được tiếp tục với báo giá hợp lệ.
6. Voucher và điểm mới được kiểm tra khi báo giá, chưa bị sử dụng. Việc giữ quyền sử dụng chỉ diễn ra khi tạo đơn; chỉ tiêu dùng khi đơn được xác nhận thanh toán hoặc xác nhận hợp lệ với tổng 0 đồng.
7. Đổi sản phẩm, số lượng, nơi nhận hoặc ưu đãi phải tính lại báo giá và điều kiện liên quan. Nếu giá, phí hoặc quyền lợi thay đổi, Frontend yêu cầu khách xem lại và xác nhận; không âm thầm bỏ ưu đãi để đặt theo giá khác.
8. Khách kiểm tra toàn bộ báo giá và bấm **Đặt hàng**. Với tổng dương, checkout thông báo sẽ thanh toán trực tuyến qua VNPay; với tổng 0 đồng, thông báo đơn sẽ được xác nhận trực tiếp. Lần này không có COD hoặc trả tiền tại cửa hàng.

<a id="fe-07"></a>

# FE-07 — Notification Management

Nội dung nghiên cứu đã chuyển sang [research riêng của FE-07](features/007-notification-management/research.md). Chưa tạo spec/plan/tasks mới hoặc phân tích thêm feature.

<a id="fe-08"></a>

# FE-08 — Order Management & Fulfillment

Phần chọn tự nhận/giao GHTK và thông tin nơi xử lý được giữ trong checkout ở FE-02 để không tách đứt luồng mua hàng. Chưa có luồng riêng cho chuẩn bị hàng, nhận tại cửa hàng và ghi nhận bàn giao. Phần thu tiền/đối soát được tham chiếu tại Thanh toán trong FE-02.

## Refund

Chưa mô tả luồng. Giữ đây là mục nghiên cứu tiếp theo liên quan đơn hàng và điều chỉnh tài chính; không coi refund là chức năng đã được report chấp thuận. Luồng thanh toán hiện chỉ chuyển các khoản bất thường sang bước đối soát/Refund để xử lý sau.

<a id="fe-09"></a>

# FE-09 — Administrative Parameter Configuration

Chưa mô tả luồng cấu hình tham số, cấu hình tích hợp và hỗ trợ kỹ thuật. Các tham số nghiệp vụ cần được chủ feature tương ứng xác định; chưa bổ sung tham số cho thuật toán thiết kế nhẫn đang được thiết kế lại.

<a id="fe-10"></a>

# FE-10 — Manager Dashboard

Chưa mô tả luồng dashboard và báo cáo tổng hợp.
