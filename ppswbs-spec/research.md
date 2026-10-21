# Research — Phân loại luồng theo Features

Phân loại theo 10 major features trong Report 1. Đây là cách tổ chức tài liệu, không phải xác nhận các tính năng đã được triển khai. Nội dung luồng hiện có được giữ lại; phần chưa có chỉ ghi rõ trạng thái, chưa bổ sung thiết kế mới.

Mỗi luồng được mô tả tại một nơi. Các điểm dùng chung được dẫn chiếu giữa feature; thanh toán không được tạo thành feature thứ 11.

<a id="fe-01"></a>

# FE-01 — Account Management

Đã có các luồng authentication bên dưới. Chưa mô tả riêng đăng xuất, xem/sửa hồ sơ, quản trị tài khoản và cấu hình vai trò/quyền. Email xác minh và đặt lại mật khẩu trong các luồng này do Firebase Auth xử lý; thông báo nghiệp vụ được phân loại tại FE-07.

## Đăng ký

### Đăng ký bằng email/mật khẩu — Luồng thành công

1. Khách vào landing page, chọn **Đăng ký**, rồi chọn đăng ký bằng email/mật khẩu.
2. Khách nhập email, mật khẩu và xác nhận mật khẩu; bấm **Đăng ký**.
3. Frontend kiểm tra trường bắt buộc, định dạng email, xác nhận mật khẩu và chính sách mật khẩu đã cấu hình trong Firebase. Khi đang gửi yêu cầu, khóa nút để tránh bấm lặp.
4. Frontend gọi `createUserWithEmailAndPassword(auth, email, password)`.
5. Firebase tạo danh tính và trả về thông tin người dùng. Frontend lấy ID Token từ người dùng vừa được tạo.
6. Frontend gọi `sendEmailVerification(user)` để gửi email xác minh ban đầu. Việc gửi email thất bại không hủy tài khoản đã tạo và không chặn bước Backend.
7. Frontend thực hiện **Xử lý Backend chung sau xác thực** ở phần Đăng nhập để tạo hoặc lấy Member theo UID.
8. Sau khi Backend chấp nhận, Frontend chuyển khách về landing page với trạng thái đã đăng nhập; nếu email chưa xác minh, hiển thị nhắc xác minh và nút yêu cầu gửi lại email.

### Đăng ký bằng Google/Facebook — Luồng thành công

1. Khách chọn **Đăng ký bằng Google** hoặc **Đăng ký bằng Facebook**.
2. Frontend gọi Firebase SDK với provider tương ứng, mở popup để khách xác thực và cấp quyền tại nhà cung cấp.
3. Firebase xử lý kết quả. Lần đầu đăng nhập bằng provider sẽ tạo danh tính Firebase; nếu danh tính đã tồn tại thì tiếp tục đăng nhập, không tạo tài khoản khác chỉ vì khách đi từ màn hình đăng ký.
4. Frontend lấy ID Token và thực hiện **Xử lý Backend chung sau xác thực** để tạo hoặc lấy Member theo UID.
5. Backend sử dụng email và trạng thái xác minh từ token đã kiểm tra. Nếu thiếu email hoặc email chưa xác minh, không coi tài khoản đã xác minh; việc bổ sung email được xử lý trong luồng hồ sơ khi cần.
6. Sau khi Backend chấp nhận, Frontend chuyển khách về landing page với trạng thái đã đăng nhập.

### Đăng ký — Nhánh lỗi

1. Thiếu dữ liệu, email sai định dạng, xác nhận mật khẩu không khớp hoặc mật khẩu không đạt chính sách: Frontend hiển thị lỗi tại trường tương ứng; Firebase vẫn là nơi quyết định tính hợp lệ của thông tin đăng ký.
2. Email đã được sử dụng: thông báo không thể đăng ký bằng thông tin này và hướng dẫn đăng nhập hoặc quên mật khẩu; không tự tạo hay gộp tài khoản khác.
3. Khách đóng hoặc hủy popup: quay lại màn hình đăng ký, cho chọn lại phương thức; không báo đăng ký thành công.
4. Popup bị chặn, provider chưa cấu hình hoặc lỗi nhà cung cấp: hướng dẫn cho phép popup hoặc thử lại/chọn phương thức khác; không tạo Member khi chưa có token hợp lệ.
5. Firebase báo xung đột phương thức đăng nhập: hướng dẫn khách dùng phương thức đã đăng ký. Chưa thực hiện liên kết tài khoản và không tự gộp theo email.
6. Gửi email xác minh thất bại: thông báo tài khoản đã được tạo nhưng chưa gửi được email; cho yêu cầu gửi lại, không chạy lại thao tác tạo tài khoản.
7. Firebase thành công nhưng Backend lỗi hoặc mất mạng: thông báo chưa hoàn tất đăng nhập website, giữ khả năng thử lại bước Backend với token còn hợp lệ; không gọi lại thao tác đăng ký Firebase và không tạo trùng Member.

## Đăng nhập

### Đăng nhập bằng email/mật khẩu — Luồng thành công

1. Khách mở trang đăng nhập, nhập email và mật khẩu rồi bấm **Đăng nhập**.
2. Frontend kiểm tra trường bắt buộc và định dạng email, khóa nút trong khi xử lý.
3. Frontend gọi `signInWithEmailAndPassword(auth, email, password)`.
4. Firebase xác thực thông tin; Frontend lấy ID Token từ người dùng đã đăng nhập.
5. Frontend thực hiện **Xử lý Backend chung sau xác thực**. Không yêu cầu khách xác minh email chỉ để đăng nhập website.

### Đăng nhập bằng Google/Facebook — Luồng thành công

1. Khách chọn **Đăng nhập bằng Google** hoặc **Đăng nhập bằng Facebook**.
2. Frontend gọi Firebase SDK mở popup với provider tương ứng; khách chọn tài khoản và hoàn tất xác thực tại nhà cung cấp.
3. Firebase xử lý kết quả; Frontend lấy ID Token.
4. Frontend thực hiện **Xử lý Backend chung sau xác thực**. Nếu đây là lần đăng nhập đầu tiên, dùng cùng cơ chế tạo danh tính và Member của luồng đăng ký social.

### Xử lý Backend chung sau xác thực

1. Frontend gửi `PUT /api/v1/members/me` với header `Authorization: Bearer <ID_TOKEN>` để tạo hoặc lấy Member. Đây là điểm tích hợp đã có trong dự án; hành vi dưới đây là thiết kế mong muốn.
2. Backend dùng Firebase Admin SDK xác thực chữ ký, thời hạn và việc thu hồi token; từ chối danh tính không hợp lệ hoặc đã bị vô hiệu hóa. Với Java, việc kiểm tra thu hồi cần được bật khi xác thực ID Token.
3. Backend lấy UID, email và trạng thái xác minh từ token đã xác thực, rồi tìm Member theo UID.
4. Nếu chưa có Member, Backend tạo Member theo UID. Thao tác phải có thể gọi lại an toàn, kể cả khi yêu cầu trước đã tạo xong nhưng Frontend chưa nhận được phản hồi; ràng buộc duy nhất theo UID ngăn bản ghi trùng.
5. Nếu Member đã tồn tại, Backend giữ nguyên trạng thái quản trị và dữ liệu nghiệp vụ; không tự mở lại tài khoản bị khóa, không tự gộp tài khoản cùng email. Trạng thái chưa chấp nhận chính sách không chặn đăng nhập trong bản thiết kế này; điều kiện sử dụng từng nghiệp vụ được kiểm tra riêng.
6. Backend kiểm tra Member có được phép sử dụng tài khoản hay không, rồi trả thông tin Member và trạng thái email đã xác minh. Tài khoản chưa xác minh vẫn được đăng nhập.
7. Chỉ sau phản hồi chấp nhận của Backend, Frontend mới hiển thị đăng nhập website thành công và chuyển khách về `/` (landing page).
8. Với các yêu cầu tiếp theo, Frontend lấy token qua Firebase SDK; Backend tiếp tục kiểm tra token và quyền sử dụng tài khoản, không chỉ dựa vào trạng thái giao diện.

### Đăng nhập — Nhánh lỗi

1. Email/mật khẩu không hợp lệ: hiển thị thông báo chung “Email hoặc mật khẩu không đúng”, không phân biệt email không tồn tại với mật khẩu sai.
2. Tài khoản Firebase bị vô hiệu hóa hoặc Member bị khóa: từ chối truy cập và hướng dẫn liên hệ hỗ trợ; không tự tạo Member khác để bỏ qua trạng thái khóa.
3. Token hết hạn: Frontend thử lấy token mới qua SDK một lần rồi gửi lại. Nếu token bị thu hồi hoặc không thể lấy token hợp lệ, xóa trạng thái đăng nhập hiện tại và yêu cầu đăng nhập lại; không lặp vô hạn.
4. Hủy popup, popup bị chặn, lỗi provider hoặc xung đột phương thức: xử lý như các nhánh tương ứng trong Đăng ký; cho khách chọn lại phương thức.
5. Mất mạng hoặc Backend không khả dụng: thông báo chưa hoàn tất đăng nhập website và cho thử lại bước Backend; nếu token không còn hợp lệ thì yêu cầu đăng nhập lại. Không thông báo sai rằng mật khẩu không đúng và không tạo trùng Member.
6. Gửi quá nhiều yêu cầu: thông báo cần chờ rồi thử lại; không tự động gửi lặp liên tục.

### Xác minh email khi chức năng yêu cầu

1. Khách đã đăng nhập truy cập chức năng có điều kiện email đã xác minh; Backend kiểm tra điều kiện này trước khi thực hiện nghiệp vụ.
2. Nếu chưa đạt, Backend từ chối thao tác nghiệp vụ và Frontend hướng dẫn xác minh email. Khách vẫn giữ trạng thái đăng nhập; không thực hiện nghiệp vụ trước rồi mới kiểm tra.
3. Với tài khoản có email nhưng chưa xác minh, khách chủ động chọn **Gửi email xác minh** hoặc **Gửi lại**; Frontend gọi `sendEmailVerification(user)`. Không tự gửi lại liên tục khi mở trang hoặc đăng nhập.
4. Khách mở link xác minh và hoàn tất theo hướng dẫn của Firebase. Nếu link không hợp lệ, hết hạn hoặc đã dùng, kiểm tra lại trạng thái; nếu vẫn chưa xác minh thì cho yêu cầu link mới.
5. Khi khách quay lại website, Frontend gọi `reload(user)` và lấy ID Token mới bằng `getIdToken(user, true)`, rồi gửi lại cho Backend kiểm tra. Không dùng cờ do trình duyệt tự đặt để cấp quyền.
6. Backend chỉ cho tiếp tục nghiệp vụ khi thông tin đã xác thực thể hiện email đã xác minh. Nếu tài khoản social không cung cấp email, hướng dẫn bổ sung email trong luồng hồ sơ trước; không tự đánh dấu đã xác minh.
7. Nếu gửi mail lỗi, bị giới hạn tần suất hoặc mạng lỗi, thông báo để khách thử lại sau; giữ nguyên trạng thái đăng nhập và chưa xác minh.

## Đổi mật khẩu

### Điều kiện và luồng thành công

1. Điều kiện: khách đang đăng nhập, tài khoản được phép sử dụng và đã có phương thức email/mật khẩu. Tài khoản chỉ dùng Google/Facebook được hướng dẫn quản lý mật khẩu tại nhà cung cấp.
2. Khách mở phần bảo mật tài khoản, chọn **Đổi mật khẩu**, nhập mật khẩu hiện tại, mật khẩu mới và xác nhận mật khẩu mới.
3. Frontend kiểm tra trường bắt buộc, xác nhận khớp và chính sách mật khẩu Firebase; khóa nút trong khi xử lý.
4. Frontend tạo credential bằng `EmailAuthProvider.credential(user.email, currentPassword)`, rồi gọi `reauthenticateWithCredential(user, credential)` để xác nhận lại danh tính.
5. Sau khi tái xác thực thành công, Frontend gọi `updatePassword(user, newPassword)`. Firebase cập nhật mật khẩu; UID và dữ liệu Member không thay đổi.
6. Hệ thống hoàn tất việc vô hiệu hóa các phiên cũ. Backend chịu trách nhiệm bảo đảm token/phiên cũ bị từ chối, kiểm tra thu hồi và thực hiện thu hồi qua Firebase Admin khi cần; không coi việc làm mới token là đã đăng xuất mọi thiết bị.
7. Frontend đăng xuất Firebase tại thiết bị hiện tại và xóa trạng thái đăng nhập website. Nếu hệ thống có phiên phía server thì Backend cũng phải kết thúc phiên đó.
8. Frontend thông báo đã đổi mật khẩu và chuyển về trang đăng nhập. Trên thiết bị khác, phiên cũ bị từ chối ở yêu cầu tiếp theo và giao diện yêu cầu đăng nhập lại; không cam kết giao diện mọi thiết bị đổi ngay tức thì.
9. Khách đăng nhập bằng mật khẩu mới; thực hiện lại bước kiểm tra Backend như luồng đăng nhập thông thường.

### Đổi mật khẩu — Nhánh lỗi

1. Mật khẩu hiện tại sai: dừng ở bước tái xác thực, báo lỗi và cho nhập lại; chưa cập nhật mật khẩu.
2. Mật khẩu mới không đạt chính sách hoặc xác nhận không khớp: hiển thị lỗi tương ứng, chưa gửi cập nhật; nếu Firebase từ chối thì hiển thị hướng dẫn sửa.
3. Firebase yêu cầu đăng nhập gần đây hoặc phiên không còn hợp lệ: yêu cầu xác thực lại; nếu không thể tiếp tục thì đưa khách về đăng nhập.
4. Mất mạng trước khi gửi cập nhật: cho thử lại. Nếu mất phản hồi sau khi đã gửi, thông báo chưa xác định được kết quả và hướng dẫn đăng nhập bằng mật khẩu mới; nếu không được thì thử mật khẩu cũ hoặc dùng quên mật khẩu, không khẳng định cập nhật đã thất bại.
5. Mật khẩu đã đổi nhưng bước kết thúc phiên lỗi: thông báo rõ mật khẩu đã đổi nhưng chưa xác nhận kết thúc mọi phiên; đăng xuất thiết bị hiện tại nếu có thể, cho thử lại bước kết thúc phiên an toàn và hướng dẫn đăng nhập bằng mật khẩu mới. Không chạy lại toàn bộ thao tác đổi mật khẩu hoặc báo thành công toàn bộ khi chưa hoàn tất.
6. Tài khoản chỉ dùng Google/Facebook: không hiển thị biểu mẫu yêu cầu mật khẩu hiện tại của website; hướng dẫn đổi hoặc khôi phục mật khẩu tại nhà cung cấp.

## Quên mật khẩu

### Luồng thành công

1. Khách tại trang đăng nhập chọn **Quên mật khẩu**, nhập email và bấm **Gửi yêu cầu**.
2. Frontend kiểm tra trường bắt buộc và định dạng email, khóa nút khi đang xử lý rồi gọi `sendPasswordResetEmail(auth, email)`.
3. Với tài khoản có phương thức email/mật khẩu hợp lệ, Firebase xử lý gửi email chứa link đặt lại mật khẩu. Bật cơ chế chống dò email của Firebase và không hiển thị lỗi phân biệt tài khoản có tồn tại hay không.
4. Frontend hiển thị thông báo trung tính: “Nếu địa chỉ email này có tài khoản hỗ trợ đặt lại mật khẩu, bạn sẽ nhận được email hướng dẫn. Vui lòng kiểm tra cả thư mục spam.” Không khẳng định email đã được giao đến hộp thư.
5. Khách mở link; trang đặt lại mật khẩu do Firebase cung cấp kiểm tra mã và hướng dẫn nhập mật khẩu mới, xác nhận theo giao diện của trang.
6. Firebase kiểm tra link và chính sách mật khẩu, cập nhật mật khẩu nếu hợp lệ và tự động xử lý thu hồi token cũ khi đặt lại mật khẩu.
7. Backend phải kiểm tra thu hồi trên các yêu cầu cần đăng nhập để từ chối phiên cũ; các thiết bị khác được yêu cầu đăng nhập lại khi gửi yêu cầu tiếp theo. Không cần đồng bộ mật khẩu về database.
8. Khách quay lại website và đăng nhập bằng mật khẩu mới; Frontend và Backend thực hiện luồng đăng nhập đầy đủ, không tự coi việc mở link là đăng nhập thành công.

### Quên mật khẩu — Nhánh lỗi

1. Email trống hoặc sai định dạng: hiển thị lỗi tại trường nhập, chưa gọi Firebase.
2. Email không tồn tại hoặc không có phương thức mật khẩu: vẫn dùng thông báo trung tính; không tiết lộ phương thức đăng nhập của email cụ thể. Trang có hướng dẫn chung rằng khách chỉ dùng Google/Facebook nên đăng nhập hoặc khôi phục mật khẩu tại nhà cung cấp.
3. Mất mạng hoặc dịch vụ gửi yêu cầu lỗi: thông báo chưa thể xác nhận yêu cầu, cho thử lại; không suy diễn rằng email không có tài khoản.
4. Gửi quá nhiều yêu cầu: yêu cầu khách chờ trước khi thử lại, không tự gửi lặp. Nếu chưa thấy thư, hướng dẫn kiểm tra spam và yêu cầu gửi lại khi được phép.
5. Link không hợp lệ, hết hạn hoặc đã dùng: hướng dẫn quay lại **Quên mật khẩu** để xin link mới; không cho cập nhật mật khẩu bằng link đó.
6. Mật khẩu mới không đạt chính sách: trang Firebase hiển thị lỗi để khách sửa; chưa thông báo đặt lại thành công.
7. Mất phản hồi khi xác nhận mật khẩu mới: không khẳng định mật khẩu chưa đổi; hướng dẫn thử đăng nhập bằng mật khẩu mới hoặc xin link khác nếu không thể đăng nhập.

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
