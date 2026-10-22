# Research — Phân loại luồng theo Features

Phân loại theo 10 major features trong Report 1. Đây là cách tổ chức tài liệu, không phải xác nhận các tính năng đã được triển khai. Mỗi feature ghi rõ nội dung đã mô tả, đề xuất và phần chưa chốt.

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

Feature này gồm quản lí nhẫn bán sẵn do Staff thực hiện và tra cứu catalogue công khai. Mua ngay, giỏ hàng, checkout, tạo đơn, thanh toán và giao nhận được mô tả tại [FE-08](#fe-08); voucher và điểm thưởng tại [FE-06](#fe-06). Các nội dung **Đề xuất** và tham số **chưa chốt** được ghi tại luồng liên quan.

## Quản lí catalogue sản phẩm

### Điều kiện và quy tắc chung

1. Chỉ Staff có tài khoản hoạt động và quyền quản lí sản phẩm được tạo/sửa sản phẩm, ảnh, biến thể, giá, danh mục và trạng thái bán. Backend kiểm tra vai trò/quyền tại từng thao tác; Guest/Member tra cứu catalogue; luồng mua hàng tham chiếu [FE-08](#fe-08).
2. Sản phẩm mới ở trạng thái **Chưa công khai**. Staff nhập/lưu trực tiếp giá bán; công khai sản phẩm là thao tác riêng.
3. Mỗi biến thể có tổ hợp kích thước/lựa chọn khắc được cấu hình trước, SKU tự sinh cố định, giá và số lượng cho phép bán riêng. Khách chỉ chọn biến thể đã có.
4. Mỗi biến thể dùng một số lượng còn cho phép bán **chung toàn hệ thống**, không chia theo chi nhánh. Staff cập nhật thủ công; số lượng khả dụng bằng lượng bán chung còn lại trừ phần đang tạm giữ. Staff tự điều phối nơi chuẩn bị hàng tại FE-08. Không làm quản lí kho, nhập/xuất, kiểm kê hay điều chuyển.
5. Backend ghi người thực hiện, thay đổi, thời điểm và kết quả của thao tác quản trị. Cập nhật thất bại không được lưu một phần; đơn và quyền giữ hợp lệ đã chấp nhận được bảo toàn theo điều khoản gốc.

### Xem danh sách và chi tiết quản trị — Luồng thành công

1. Staff có quyền mở **Quản lí sản phẩm**. Backend kiểm tra tài khoản và quyền trước khi trả dữ liệu nội bộ.
2. Frontend hiển thị mã tham chiếu sản phẩm, tên, danh mục, ảnh, trạng thái công khai và giá bán hiện tại. Chi tiết biến thể hiển thị trạng thái đang bán/ngừng bán và SKU do hệ thống tự sinh; SKU là trường chỉ đọc.
3. Staff tìm theo mã tham chiếu/tên sản phẩm hoặc SKU biến thể; lọc theo danh mục và trạng thái công khai. Kết quả tìm SKU chỉ rõ biến thể khớp trong sản phẩm. Tiêu chí lọc bổ sung và kích thước trang **chưa chốt**.
4. Người dùng mở một sản phẩm để xem mô tả, danh mục, thứ tự ảnh, biến thể, giá và số lượng cho phép bán. Frontend chỉ hiển thị các thao tác phù hợp với quyền hiện tại.
5. Staff chọn tạo mới, sửa thông tin/giá bán, quản lí ảnh/biến thể hoặc công khai/ngừng công khai theo các luồng bên dưới.

### Danh sách và chi tiết quản trị — Nhánh lỗi

1. Không có quyền hoặc tài khoản không còn được phép sử dụng: từ chối truy cập, không trả bản nháp hay thông tin giá nội bộ.
2. Không có sản phẩm hoặc không có kết quả theo bộ lọc: hiển thị trạng thái rỗng và cho sửa/xóa bộ lọc; không coi đây là lỗi hệ thống.
3. Lỗi tải hoặc sản phẩm không còn tồn tại: thông báo phù hợp, cho tải lại/quay về danh sách; không hiển thị dữ liệu cũ như trạng thái hiện tại đã xác nhận.

### Tạo sản phẩm — Luồng thành công

1. Staff có quyền mở danh sách sản phẩm và chọn **Tạo sản phẩm**.
2. Người dùng nhập tên, mô tả và chọn một danh mục đang hoạt động. Frontend loại bỏ khoảng trắng thừa để kiểm tra trường bắt buộc; Backend kiểm tra lại dữ liệu khi lưu.
3. Người dùng cấu hình các biến thể kích thước/lựa chọn khắc có sẵn, nhập giá bán và số lượng cho phép bán chung ban đầu cho từng biến thể. Giá là số nguyên VND dương; số lượng là số nguyên không âm. Backend tự sinh SKU duy nhất, cố định cho từng biến thể theo luồng SKU; người dùng không nhập hoặc sửa mã.
4. Người dùng có thể tải ảnh, nhập văn bản thay thế và sắp xếp thứ tự hiển thị theo luồng ảnh bên dưới. Bản nháp có thể chưa có ảnh; sản phẩm phải có ảnh sử dụng được trước khi công khai.
5. Frontend kiểm tra dữ liệu rồi gửi yêu cầu lưu; Backend kiểm tra quyền, danh mục, tổ hợp biến thể không trùng, dữ liệu giá/số lượng và các ảnh đã tải hợp lệ.
6. Backend lưu sản phẩm và các dữ liệu liên quan ở trạng thái **Chưa công khai**, ghi nhận audit rồi trả kết quả. Giá Staff nhập được lưu trực tiếp thành giá bán của từng biến thể.
7. Frontend mở chi tiết sản phẩm vừa tạo. Staff kiểm tra thông tin, ảnh, biến thể và giá bán đã lưu; khi đủ điều kiện thì chủ động chọn công khai sản phẩm.
8. Giới hạn độ dài tên/mô tả, số lượng biến thể và giới hạn giá/số lượng cụ thể **chưa chốt**.

### Tạo sản phẩm — Nhánh lỗi

1. Thiếu tên/mô tả, danh mục không hoạt động hoặc dữ liệu không hợp lệ: hiển thị lỗi tại trường tương ứng, giữ nội dung người dùng đã nhập để sửa.
2. Thiếu biến thể, trùng tổ hợp kích thước/lựa chọn khắc, giá không hợp lệ hoặc số lượng âm/không nguyên: chỉ rõ dòng lỗi, chưa tạo sản phẩm một phần.
3. Ảnh tải lên lỗi hoặc tham chiếu ảnh chưa hợp lệ: yêu cầu sửa/thử lại hoặc chủ động bỏ ảnh lỗi trước khi lưu bản nháp; không lưu liên kết ảnh hỏng.
4. Mất phản hồi sau khi gửi yêu cầu: kiểm tra kết quả đã lưu trước khi tạo lại; không tạo thêm sản phẩm chỉ vì chưa nhận được thông báo thành công.
5. Lỗi lưu hoặc audit: thông báo chưa hoàn tất, không ghi nhận sản phẩm ở trạng thái được tạo thành công một phần.

### Sửa thông tin sản phẩm — Luồng thành công

1. Staff có quyền mở chi tiết và chọn **Sửa**. Backend trả thông tin hiện tại để người dùng đối chiếu trước khi thay đổi.
2. Người dùng sửa các trường được phép như tên, mô tả, danh mục; thay đổi ảnh, biến thể hoặc giá đi theo các quy tắc riêng bên dưới.
3. Backend kiểm tra quyền, phiên bản dữ liệu và trạng thái hiện tại trước khi lưu. Nếu sản phẩm đang công khai, bản cập nhật phải tiếp tục đáp ứng điều kiện công khai. Giỏ hàng chưa tạo đơn dùng thông tin mới khi tải lại và được kiểm tra lại ở checkout; giá hoặc điều kiện thay đổi phải được khách xem và xác nhận trước khi đặt.
4. Backend lưu các thay đổi hợp lệ và audit; không sửa thông tin đã chụp của đơn hoặc điều khoản của quyền giữ đã được chấp nhận.
5. Frontend hiển thị kết quả đã lưu, gồm giá bán mới nếu Staff có sửa giá. Lưu thông tin/giá không thay cho thao tác công khai/ngừng công khai.

### Sửa sản phẩm — Nhánh lỗi

1. Người khác đã thay đổi dữ liệu: từ chối ghi đè bản cũ, tải lại dữ liệu mới để người dùng xem và xác nhận lại.
2. Danh mục đã ngừng hoạt động hoặc thay đổi làm sản phẩm công khai mất điều kiện hợp lệ: chỉ rõ lỗi, giữ trạng thái cũ; người dùng sửa dữ liệu hoặc chủ động ngừng công khai trước khi tiếp tục.
3. Không có quyền, sản phẩm không còn tồn tại, lưu/audit lỗi: từ chối an toàn, không lưu một phần và không báo đã cập nhật.

### Quản lí ảnh sản phẩm — Luồng thành công

1. Người dùng có quyền chọn thêm, thay thế, đổi thứ tự hoặc gỡ ảnh trong biểu mẫu tạo/sửa sản phẩm.
2. Frontend hiển thị ảnh xem trước, văn bản thay thế và thứ tự hiển thị; cho chọn định dạng ảnh được hệ thống hỗ trợ. Các định dạng cụ thể, số ảnh tối đa, dung lượng mỗi ảnh và giới hạn văn bản thay thế **chưa chốt**.
3. Backend kiểm tra ảnh, quyền truy cập và dữ liệu mô tả; lưu tệp qua dịch vụ lưu trữ ảnh được cấu hình, rồi gắn tham chiếu hợp lệ vào sản phẩm.
4. Backend kiểm tra thứ tự ảnh không trùng. Với sản phẩm công khai, sau thay đổi phải còn ảnh sử dụng được; muốn gỡ toàn bộ ảnh thì người dùng phải ngừng công khai trước.
5. Backend lưu danh sách ảnh và audit rồi trả kết quả. Gỡ tham chiếu ảnh catalogue không mặc định cho phép xóa tệp dùng chung hoặc ảnh còn phục vụ lịch sử giao dịch.

### Quản lí ảnh — Nhánh lỗi

1. Sai loại tệp, vượt giới hạn đã cấu hình, thiếu mô tả ảnh hoặc trùng thứ tự: hiển thị lỗi tương ứng, chưa lưu cập nhật.
2. Dịch vụ lưu trữ lỗi hoặc tệp chưa tải xong: giữ danh sách ảnh đang có, cho thử lại; không thay ảnh hợp lệ bằng liên kết lỗi.
3. Thao tác làm mất ảnh cuối cùng của sản phẩm công khai: từ chối cập nhật và hướng dẫn ngừng công khai hoặc thêm ảnh thay thế trước.

### Quản lí biến thể và số lượng cho phép bán — Luồng thành công

1. Staff có quyền mở danh sách biến thể của sản phẩm để thêm tổ hợp kích thước/lựa chọn khắc đã được hỗ trợ hoặc cập nhật dữ liệu được phép.
2. Backend kiểm tra tổ hợp không trùng và định danh ổn định. Biến thể đã được dùng trong giao dịch không được đổi lựa chọn làm thay đổi ý nghĩa của định danh cũ.
3. Staff nhập số lượng còn cho phép bán chung của từng biến thể theo thông tin shop xác nhận bên ngoài hệ thống. Backend chỉ chấp nhận số nguyên không âm và không thấp hơn phần đang giữ hợp lệ. Khi đơn được chốt, Backend trừ lượng đã mua khỏi số lượng chung đúng một lần; hết hạn chưa thanh toán chỉ giải phóng phần giữ.
4. Staff nhập/sửa giá bán trực tiếp theo luồng Nhập và cập nhật giá bán sản phẩm. Backend kiểm tra giá hợp lệ và dữ liệu mới nhất trước khi lưu; không sửa giá của đơn hoặc quyền giữ đã được chấp nhận.
5. Backend kiểm tra tính hợp lệ của sản phẩm sau thay đổi, lưu dữ liệu và audit rồi hiển thị kết quả. Biến thể mới chưa có giá bán hợp lệ phải được hoàn tất bước giá trước khi đáp ứng điều kiện công khai.

### Biến thể và số lượng cho phép bán — Nhánh lỗi

1. Tổ hợp bị trùng, dữ liệu không hợp lệ hoặc thay đổi lựa chọn của biến thể đã dùng: từ chối, hướng dẫn tạo tổ hợp mới hợp lệ thay vì sửa định danh cũ.
2. Số lượng nhập thấp hơn lượng đang giữ, hoặc lượng giữ thay đổi khi đang sửa: Backend trả tình trạng mới và yêu cầu kiểm tra lại; không hủy giữ chỗ để ép lưu số lượng.
3. Thay đổi khiến sản phẩm đang công khai không đủ điều kiện, chẳng hạn biến thể mới chưa có giá bán: từ chối bản cập nhật; người dùng hoàn tất dữ liệu qua quy trình phù hợp hoặc ngừng công khai trước khi cấu hình lại.
4. Lỗi lưu/audit: giữ dữ liệu trước đó, chưa báo cập nhật thành công và cho kiểm tra lại kết quả.

### Cấp và quản lí SKU biến thể — Luồng thành công

1. Khi tạo biến thể, Backend cấp SKU để Staff tra cứu và phân biệt các lựa chọn bán hàng.
2. Khi tạo biến thể, Backend tự sinh SKU duy nhất và cố định cho biến thể đó; Staff không nhập hoặc sửa mã. Định dạng và độ dài mã cụ thể **chưa chốt**.
3. Backend kiểm tra SKU không trùng với biến thể khác trên hệ thống. Không dùng chung một SKU cho nhiều tổ hợp; việc trùng tên sản phẩm không quyết định SKU.
4. Sau khi lưu thành công, Frontend hiển thị SKU chỉ đọc trong chi tiết quản trị và cho tìm kiếm theo SKU. Mã giữ nguyên từ khi cấp, kể cả khi biến thể chưa có giao dịch.
5. Đổi tên sản phẩm, đổi giá hoặc ngừng bán không tự sinh SKU mới. SKU không được tái cấp cho một biến thể khác chỉ vì biến thể cũ ngừng bán.

### SKU biến thể — Nhánh lỗi

1. Hệ thống chưa cấp được mã hợp lệ hoặc phát hiện mã trùng: không lưu biến thể với SKU thiếu/trùng; thông báo chưa hoàn tất để thử lại, không yêu cầu Staff nhập mã thay thế.
2. Yêu cầu cố sửa SKU đã được cấp hoặc gán SKU của biến thể khác: Backend từ chối, kể cả khi chưa có giao dịch; không chỉ dựa vào trường chỉ đọc trên giao diện.
3. Cấp mã hoặc lưu dữ liệu lỗi: không tạo biến thể thiếu mã hay tạo thêm biến thể khi thử lại; kiểm tra kết quả đã ghi nhận trước khi gửi lại.

## Nhập và cập nhật giá bán sản phẩm

### Lưu giá bán — Luồng thành công

1. Staff có tài khoản hoạt động và quyền quản lí sản phẩm nhập giá bán khi tạo biến thể, hoặc mở biến thể đã có và chọn sửa giá. Frontend hiển thị SKU và giá hiện tại để Staff xác định đúng đối tượng.
2. Staff nhập giá bán là số nguyên VND dương, xem lại rồi chọn **Lưu**. Giá nhập hợp lệ sau khi lưu thành công chính là giá bán của biến thể.
3. Backend kiểm tra vai trò Staff, quyền quản lí sản phẩm, đối tượng, dữ liệu mới nhất và giá hợp lệ; lưu giá cùng audit gồm người sửa, thời điểm và giá trước/sau.
4. Frontend hiển thị giá đã lưu. Với sản phẩm công khai và đủ điều kiện, lượt xem/checkout mới sử dụng giá mới; với sản phẩm chưa công khai, lưu giá không tự công khai sản phẩm.
5. Đơn và quyền giữ hợp lệ đã được chấp nhận trước đó giữ giá đã chốt trong thời hạn gốc. Giỏ chưa tạo đơn phải tải và kiểm tra lại giá hiện tại, cho khách xác nhận tổng tiền trước khi đặt.

### Lưu giá bán — Nhánh lỗi

1. Không có vai trò Staff/quyền quản lí sản phẩm hoặc chọn sai biến thể: từ chối, không thay đổi giá.
2. Giá không hợp lệ: hiển thị lỗi tại trường nhập và giữ giá cũ; các giới hạn giá bổ sung nếu có **chưa chốt**.
3. Dữ liệu đã bị người khác thay đổi: từ chối ghi đè, tải lại giá hiện tại để Staff xem và xác nhận lại.
4. Lỗi lưu/audit hoặc mất phản hồi: chưa báo đã cập nhật; kiểm tra kết quả đã ghi nhận trước khi thử lại, không ghi đè giá mới hơn bằng yêu cầu cũ.

## Công khai và ngừng công khai sản phẩm

### Công khai sản phẩm — Luồng thành công

1. Staff có quyền mở sản phẩm **Chưa công khai**, kiểm tra thông tin rồi chọn **Công khai**.
2. Backend kiểm tra quyền, danh mục đang hoạt động, thông tin hợp lệ, có ảnh sử dụng được, có biến thể và mọi biến thể được cấu hình đều có giá bán đã lưu hợp lệ.
3. Số lượng cho phép bán bằng 0 không tự chặn công khai: sản phẩm vẫn có thể được hiển thị với tình trạng chưa có hàng để mua.
4. Backend cập nhật trạng thái công khai và ghi audit. Khách được xem sản phẩm ở các lượt đọc catalogue tiếp theo khi các điều kiện công khai vẫn đạt.

### Ngừng công khai — Luồng thành công

1. Staff có quyền mở sản phẩm, chọn **Ngừng công khai** và xác nhận.
2. Backend kiểm tra trạng thái hiện tại, cập nhật và ghi audit. Sản phẩm không còn xuất hiện trong catalogue công khai và không được tạo quyền giữ mới ở checkout.
3. Giỏ hoặc màn hình cũ không cho phép vượt qua kiểm tra hiện tại. Quyền giữ hợp lệ đã được chấp nhận, giá của đơn và lịch sử mua được bảo toàn; không tự hủy đơn hay gia hạn thanh toán.
4. Staff có thể công khai lại sau khi sản phẩm đáp ứng điều kiện. Luồng xóa sản phẩm sẽ được bổ sung sau; lần này chỉ mô tả công khai/ngừng công khai.

### Công khai và ngừng công khai — Nhánh lỗi

1. Thiếu ảnh, danh mục không hoạt động, thiếu biến thể hoặc chưa có giá bán hợp lệ: không công khai, nêu rõ điều kiện cần hoàn tất.
2. Gửi lại thao tác khi sản phẩm đã ở trạng thái yêu cầu: trả trạng thái hiện tại, không thực hiện thêm một lần chuyển trạng thái.
3. Dữ liệu thay đổi trong lúc xác nhận, không có quyền hoặc lưu/audit lỗi: giữ trạng thái trước đó, cho tải lại và thử lại sau khi kiểm tra.

### Ngừng bán và mở bán lại từng biến thể — Luồng thành công

1. Staff có quyền mở sản phẩm, chọn đúng biến thể cần ngừng bán rồi xác nhận. Trạng thái này được quản lí riêng cho biến thể, không đổi cờ công khai của toàn sản phẩm.
2. Backend kiểm tra quyền và trạng thái hiện tại, ghi nhận biến thể ngừng bán cùng audit. Dù số lượng cho phép bán vẫn còn, biến thể đó không được thêm mới vào giỏ hoặc tạo quyền giữ mới ở checkout.
3. Các biến thể khác của cùng sản phẩm tiếp tục hoạt động theo điều kiện riêng. Không đặt số lượng về 0 để biểu diễn ngừng bán; số lượng bán và quyết định cho phép bán là hai thông tin khác nhau.
4. Biến thể ngừng bán vẫn hiển thị trên trang sản phẩm với nhãn **Ngừng bán**, không chọn mua được. Giỏ cũ phải kiểm tra lại và yêu cầu khách sửa lựa chọn trước khi đặt; không tự đổi sang biến thể khác.
5. Ngừng bán không hủy đơn hoặc quyền giữ hợp lệ đã được chấp nhận, không đổi giá đã chốt và không gia hạn thanh toán. Lịch sử vẫn tham chiếu đúng biến thể/SKU đã dùng.
6. Khi chọn **Mở bán lại**, Backend kiểm tra dữ liệu và giá bán hợp lệ trước khi lưu trạng thái. Biến thể chỉ mua được nếu sản phẩm công khai, danh mục hoạt động và còn lượng bán khả dụng; mở lại biến thể không tự công khai sản phẩm đang ẩn.

### Trạng thái biến thể — Nhánh lỗi

1. Thiếu quyền, chọn sai biến thể hoặc dữ liệu đã thay đổi: không lưu trạng thái mới, cho tải lại và xác nhận lại đúng đối tượng.
2. Mở bán lại nhưng dữ liệu/giá chưa hợp lệ: chỉ rõ điều kiện cần hoàn tất; không báo đã mở bán thành công.
3. Gửi lặp cùng trạng thái: trả kết quả hiện tại, không tạo thêm lần chuyển trạng thái hoặc trừ/khôi phục số lượng bán.
4. Lưu/audit lỗi hoặc mất phản hồi: kiểm tra kết quả đã ghi nhận trước khi thử lại; không hủy các giao dịch đã chấp nhận để xử lí lỗi quản trị.

## Quản lí danh mục sản phẩm

### Tạo, sửa và đổi trạng thái danh mục — Luồng thành công

1. Chỉ Staff có quyền quản lí sản phẩm được tạo, sửa, kích hoạt hoặc ngừng kích hoạt danh mục. Backend kiểm tra tài khoản hoạt động và quyền hiện tại trước mỗi thao tác.
2. Người có quyền mở danh sách danh mục, chọn tạo mới hoặc sửa, nhập tên/mô tả rồi xác nhận. Backend kiểm tra quyền, trường bắt buộc và quy tắc tên trước khi lưu cùng audit. Quy tắc duy nhất tên và giới hạn nhập liệu **chưa chốt**.
3. Khi ngừng kích hoạt danh mục, Frontend cho xem các sản phẩm bị ảnh hưởng trước khi xác nhận. Backend loại danh mục và sản phẩm liên quan khỏi các lượt tra cứu công khai mới; lịch sử sản phẩm, đơn và quyền giữ hợp lệ không bị viết lại.
4. Khi kích hoạt lại, chỉ sản phẩm còn công khai và đủ các điều kiện khác mới được hiển thị. Không tự công khai các sản phẩm người dùng đã chủ động ẩn.
5. **Đề xuất:** ngừng kích hoạt thay cho xóa danh mục đã được tham chiếu; chuyển sản phẩm sang danh mục khác qua luồng Sửa sản phẩm.

### Quản lí danh mục — Nhánh lỗi

1. Thiếu quyền, dữ liệu sai hoặc trùng tên theo quy tắc sau khi chốt: từ chối, chỉ rõ điều kiện cần sửa; không thay đổi danh mục một phần.
2. Danh mục thay đổi trong lúc thao tác hoặc lưu/audit lỗi: giữ dữ liệu đã xác nhận trước đó, yêu cầu tải lại/kiểm tra kết quả trước khi thử lại.

## Tra cứu sản phẩm công khai

### Xem, tìm kiếm và lọc catalogue — Luồng thành công

1. Guest hoặc Member mở catalogue mà không cần đăng nhập. Backend chỉ trả sản phẩm công khai, thuộc danh mục hoạt động và đáp ứng điều kiện nội dung/ảnh/giá.
2. Frontend hiển thị tên, mô tả tóm tắt, danh mục, ảnh sử dụng được, khoảng giá bán hiện tại của các biến thể và tình trạng còn được phép mua.
3. Khách nhập từ khóa hoặc lọc theo danh mục, khoảng giá và tình trạng khả dụng. Backend bỏ khoảng trắng thừa, tìm trong tên/mô tả không phân biệt hoa thường và kết hợp các bộ lọc bằng điều kiện cùng thỏa mãn.
4. Khoảng giá bao gồm hai đầu mút. Nếu lọc đồng thời giá và khả dụng, cùng một biến thể phải thỏa cả hai; không lấy giá của biến thể này ghép với tình trạng của biến thể khác.
5. Mặc định Backend sắp theo tên sản phẩm A–Z rồi định danh để thứ tự ổn định. Khách có thể đổi sang giá tăng dần, giá giảm dần hoặc mới nhất theo luồng bên dưới. Kích thước trang và giới hạn từ khóa cụ thể **chưa chốt**.
6. Khách sửa/xóa bộ lọc hoặc mở một sản phẩm để xem chi tiết. Tra cứu chưa tạo giỏ, đơn, giữ lượng bán, giữ voucher hay điểm.

### Khách chọn sắp xếp sản phẩm — Luồng thành công

1. Khách mở lựa chọn **Sắp xếp** trên catalogue: tên A–Z, giá tăng dần, giá giảm dần hoặc mới nhất. Mặc định là **Tên A–Z**.
2. Frontend giữ từ khóa và các bộ lọc đang chọn khi đổi cách sắp xếp; Backend kiểm tra lựa chọn thuộc danh sách được hỗ trợ.
3. Với tên A–Z, Backend sắp theo tên; **đề xuất:** với mới nhất, dùng thời điểm tạo sản phẩm, không dùng thời điểm sửa giá để làm sản phẩm thành mới.
4. Với sản phẩm có nhiều biến thể, dùng **giá thấp nhất của các biến thể đang hiển thị và thỏa bộ lọc** làm giá đại diện cho cả sắp xếp tăng/giảm. Frontend vẫn hiển thị khoảng giá; giá chính xác được xem khi chọn biến thể.
5. Backend dùng định danh sản phẩm làm tiêu chí phụ khi trùng tên/giá/thời điểm để thứ tự ổn định; trả kết quả và cách sắp xếp đang áp dụng để Frontend hiển thị.
6. Frontend hiển thị kết quả và giữ bộ lọc hiện tại; thay đổi cách sắp xếp không tạo đơn hoặc giữ số lượng bán.

### Sắp xếp sản phẩm — Nhánh lỗi

1. Tiêu chí không được hỗ trợ: nêu lỗi và cho chọn lại cách sắp xếp hợp lệ.
2. Lỗi tải kết quả: cho thử lại, giữ từ khóa/bộ lọc; không hiển thị nhầm thành không có sản phẩm.

### Xem chi tiết và chọn biến thể — Luồng thành công

1. Khách mở sản phẩm từ catalogue; Backend kiểm tra lại điều kiện công khai trước khi trả dữ liệu.
2. Frontend hiển thị thông tin, ảnh và các tổ hợp kích thước/lựa chọn khắc được cấu hình; chỉ hiển thị giá bán hiện tại của sản phẩm công khai, không lộ thông tin của sản phẩm chưa công khai.
3. Khách chọn một tổ hợp đã có. Backend trả đúng định danh biến thể, giá và tình trạng khả dụng hiện tại; không tự tạo biến thể từ nội dung khách nhập.
4. Nếu một biến thể hết lượng bán, Frontend đánh dấu biến thể đó chưa mua được; biến thể khác vẫn được xem và chọn theo tình trạng riêng. Sản phẩm vẫn hiển thị khi đủ điều kiện công khai dù mọi biến thể hiện không còn lượng bán.
5. Khách chọn **Mua ngay** hoặc **Thêm vào giỏ hàng** thì chuyển sang [FE-08 — Mua hàng](#fe-08), mang theo sản phẩm, biến thể và số lượng đã chọn. FE-08 kiểm tra đăng nhập/xác minh và điều kiện mua trước khi chấp nhận.

### Tra cứu và chọn biến thể — Nhánh lỗi

1. Bộ lọc không hợp lệ, giá âm, giá tối thiểu lớn hơn tối đa hoặc danh mục không được hỗ trợ: nêu lỗi, cho sửa; không thay đổi dữ liệu sản phẩm.
2. Không có kết quả: hiển thị thông báo và cho xóa/đổi bộ lọc. Lỗi tải dữ liệu phải được báo riêng, không hiển thị nhầm thành catalogue rỗng.
3. Sản phẩm ngừng công khai hoặc danh mục không còn hoạt động sau lần xem trước: không trả nội dung công khai mới và không cho dùng trang/giỏ cũ để bỏ qua kiểm tra checkout. Biến thể ngừng bán vẫn hiển thị với nhãn **Ngừng bán** và bị chặn mua mới dù còn số lượng, theo luồng trạng thái biến thể.
4. Mất toàn bộ ảnh sử dụng được: loại khỏi kết quả công khai mà không tự sửa cờ công khai hay lịch sử; khi ảnh khôi phục, chỉ hiển thị lại nếu các điều kiện khác vẫn đạt. Với lỗi tải ảnh tạm thời, hiển thị thông tin lỗi/thử lại phù hợp.
5. Tổ hợp không tồn tại hoặc nhập lựa chọn ngoài catalogue: yêu cầu khách chọn đúng biến thể Staff đã cấu hình; không tạo thêm biến thể từ dữ liệu khách nhập.

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

Feature này gồm quản lí chương trình voucher/giảm giá và điểm thưởng, nhằm khuyến khích Member quay lại mua sản phẩm và tham gia workshop. Luồng mua sản phẩm tham chiếu [FE-08](#fe-08), đặt workshop tham chiếu [FE-03](#fe-03), cấu hình tham số tham chiếu [FE-09](#fe-09). Nội dung dưới đây là thiết kế mong muốn; các quy tắc có nhãn **Đề xuất** còn cần quyết định. Các giá trị cụ thể về mức giảm, giá trị giao dịch tối thiểu, hạn mức lượt dùng, tỷ lệ cộng/quy đổi điểm, lượng điểm tối thiểu và trần giảm bằng điểm đều **chưa chốt**; Manager/Admin cấu hình theo phạm vi phụ trách sau khi thống nhất chính sách.

## Quản lí chương trình ưu đãi

### Phạm vi và quy tắc chung

1. Manager tạo, sửa, tạm dừng chương trình và theo dõi lượt sử dụng, số tiền đã giảm. Admin cấu hình chính sách điểm; Member xem, lưu và sử dụng quyền lợi của mình.
2. Voucher có hai phạm vi riêng: **sản phẩm** và **workshop**. Voucher sản phẩm không dùng cho workshop và ngược lại. Voucher workshop dành cho Member đặt trực tiếp trên website.
3. Voucher ngày lễ là chương trình có thời gian áp dụng riêng và vẫn thuộc phạm vi sản phẩm hoặc workshop; không phải một lớp voucher được cộng thêm vào giao dịch.
4. Với mua sản phẩm, mỗi đơn áp dụng **một chương trình giảm tự động hoặc một voucher**, sau đó có thể dùng thêm điểm nếu chính sách cho phép. **Đề xuất:** mỗi booking dùng một voucher; trần giảm không nhân theo số người tham gia.

### Tạo voucher sản phẩm — Luồng thành công

1. Manager mở phần quản lí ưu đãi, chọn **Tạo chương trình** và phạm vi **Sản phẩm**.
2. Manager nhập tên chương trình, mã voucher, thời gian sử dụng, sản phẩm và chi nhánh áp dụng.
3. Manager chọn giảm theo phần trăm hoặc số tiền cố định, nhập mức giảm, mức giảm tối đa và giá trị đơn tối thiểu.
4. Manager cấu hình tổng lượt sử dụng, số lượt mỗi Member và có cho phép dùng thêm điểm hay không.
5. Frontend kiểm tra trường bắt buộc và dữ liệu nhập; Backend kiểm tra quyền Manager, phạm vi và tính hợp lệ của các điều kiện trước khi lưu.
6. Backend lưu chương trình cùng các điều kiện đã xác nhận. Khi chương trình được đưa vào áp dụng và trong thời gian hiệu lực, hệ thống cho Member đủ điều kiện xem và chọn mã tại checkout sản phẩm.

### Tạo voucher workshop — Luồng thành công

1. Manager chọn **Tạo chương trình** và phạm vi **Workshop**, nhập tên chương trình và mã voucher.
2. Manager chọn gói workshop, chi nhánh, ngày và phiên áp dụng; xác định khoảng thời gian Member được đặt booking bằng mã. Thời gian được đặt bằng mã được phân biệt với ngày diễn ra workshop.
3. Manager chọn giảm theo phần trăm hoặc số tiền cố định, mức giảm tối đa và tổng phí gói tối thiểu của booking.
4. Manager cấu hình tổng lượt sử dụng, số lượt mỗi Member và có cho phép dùng thêm điểm hay không.
5. Frontend kiểm tra dữ liệu nhập; Backend kiểm tra quyền Manager, các gói/phiên được chọn và tính hợp lệ của điều kiện trước khi lưu.
6. Backend lưu chương trình. Khi chương trình có hiệu lực, Member đủ điều kiện được chọn mã lúc đặt workshop trực tiếp trên website; không hiển thị mã này như voucher dùng cho sản phẩm.

### Tạo chương trình giảm tự động cho sản phẩm — Luồng thành công

1. Manager chọn tạo chương trình giảm tự động, cấu hình phạm vi sản phẩm, chi nhánh, thời gian, mức giảm và các điều kiện áp dụng.
2. Manager xác định chương trình có cho phép dùng thêm điểm hay không rồi xác nhận lưu.
3. Backend kiểm tra quyền và điều kiện trước khi ghi nhận chương trình.
4. Tại checkout sản phẩm, Backend xác định chương trình tự động hợp lệ và trả khoản giảm để Member xem; Member không cần nhập mã.
5. Nếu Member chọn voucher thay cho giảm tự động, hệ thống tính lại tổng tiền với voucher đó; không cộng dồn cả hai khoản giảm.

### Tạo chương trình — Nhánh lỗi

1. Thiếu trường bắt buộc, thời gian không hợp lệ, mức giảm hoặc hạn mức sai: hiển thị lỗi tại trường tương ứng; chưa lưu chương trình.
2. Mã voucher đã được sử dụng cho chương trình khác hoặc phạm vi sản phẩm/gói/phiên không hợp lệ: yêu cầu Manager sửa lựa chọn trước khi lưu.
3. Tài khoản không có quyền quản lí chương trình: Backend từ chối thao tác; không chỉ ẩn nút trên giao diện.
4. Lỗi lưu hoặc mất phản hồi: thông báo chưa xác nhận được kết quả và cho kiểm tra lại danh sách chương trình trước khi gửi lại; không báo tạo thành công khi chưa có xác nhận.

### Sửa, tạm dừng và theo dõi chương trình

1. Manager mở danh sách, chọn một chương trình để xem điều kiện, thời gian, tổng lượt, số lượt đã dùng và số tiền đã giảm; có thể lọc theo phạm vi sản phẩm hoặc workshop.
2. Manager chọn **Sửa**, cập nhật điều kiện rồi xác nhận. Backend kiểm tra quyền và dữ liệu trước khi lưu, Frontend tải lại thông tin sau khi lưu thành công.
3. Các thay đổi chỉ dùng cho giao dịch được chấp nhận sau đó; không tính lại khoản giảm của đơn/booking đã được hệ thống chấp nhận.
4. Manager chọn **Tạm dừng** khi muốn ngừng nhận thêm giao dịch mới theo chương trình. Backend cập nhật trạng thái; mã đã lưu trong Kho Voucher không còn đủ điều kiện cho giao dịch mới khi chương trình bị dừng.
5. Tạm dừng không tự hủy quyền lợi đã chốt. Với lượt đang tạm giữ, xử lí theo phần Chốt và giải phóng quyền lợi bên dưới, không tự xóa phần đang giữ.
6. Nếu cập nhật lỗi hoặc mất phản hồi, giữ khả năng tải lại trạng thái để kiểm tra; không hiển thị đã sửa hoặc đã dừng khi chưa được Backend xác nhận.

## Kho Voucher

### Xem và lưu voucher — Luồng thành công

1. Khách xem ưu đãi tại trang sản phẩm, gói workshop hoặc trang ưu đãi. Trên thẻ voucher, Frontend hiển thị rõ phạm vi **Mua sản phẩm** hoặc **Đặt workshop**, mức giảm, mức giảm tối đa, giá trị tối thiểu, thời hạn và có cho phép dùng thêm điểm hay không.
2. Khách mở **Điều kiện sử dụng** để xem sản phẩm/gói workshop, chi nhánh, ngày/phiên áp dụng nếu có, tổng hạn mức lượt dùng và số lượt mỗi Member. Các thông tin này lấy từ cấu hình chương trình; các giá trị cụ thể hiện **chưa chốt**. Khách chọn **Lưu voucher**; nếu chưa đăng nhập, hệ thống hướng dẫn đăng nhập trước khi lưu vào tài khoản.
3. Backend kiểm tra Member và ghi nhận voucher trong **Kho Voucher** của Member đó; lưu lại cùng mã không tạo thêm quyền sử dụng.
4. Member mở Kho Voucher để xem các mã đã lưu và điều kiện hiện tại, hoặc chọn mã đã lưu khi checkout. Member vẫn có thể nhập mã trực tiếp mà không cần lưu trước.
5. Lưu voucher chưa giữ lượt, chưa tiêu dùng hạn mức và chưa bảo đảm dùng được. Backend kiểm tra lại điều kiện khi áp dụng và khi Member xác nhận giao dịch.

### Xem và lưu voucher — Nhánh lỗi

1. Voucher hết hạn, bị tạm dừng hoặc Member chưa đạt điều kiện: hiển thị rõ tình trạng; không coi việc mã đã có trong kho là đủ điều kiện sử dụng.
2. Phiên đăng nhập không còn hợp lệ: yêu cầu đăng nhập lại trước khi lưu hoặc xem kho cá nhân.
3. Mất mạng hoặc lỗi lưu: cho thử lại; chỉ hiển thị đã lưu sau khi Backend xác nhận.

## Áp dụng ưu đãi khi mua sản phẩm

### Checkout — Luồng thành công

1. Member tiếp tục checkout từ [FE-08](#fe-08). Backend lấy các dòng mua, giá, chi nhánh xử lí và thông tin Member để xác định ưu đãi hợp lệ.
2. Frontend hiển thị giảm tự động nếu có, các voucher sản phẩm phù hợp và số điểm khả dụng. Member chọn một voucher từ kho, nhập mã trực tiếp hoặc sử dụng giảm tự động.
3. Backend kiểm tra thời gian, trạng thái chương trình, phạm vi, giá trị tối thiểu, tổng lượt và lượt của Member; trả khoản giảm hợp lệ. Chỉ áp dụng một giảm tự động hoặc một voucher.
4. Backend tính số điểm tối đa được dùng cho giao dịch dựa trên điểm khả dụng, giá trị giao dịch và chính sách kết hợp ưu đãi, rồi trả kết quả cho Frontend. Frontend hiển thị điểm khả dụng, số điểm tối đa được dùng và số tiền giảm tương ứng để Member chủ động chọn dùng điểm trong giới hạn đó. Nếu ưu đãi không cho kết hợp điểm, Frontend không cho chọn dùng điểm và hiển thị lý do. **Đề xuất:** áp dụng lượng dùng tối thiểu và trần giảm bằng điểm do Admin cấu hình; các mức cụ thể **chưa chốt**.
5. Frontend hiển thị tiền hàng, giảm tự động/voucher, giảm bằng điểm, phí giao hàng và tổng cuối cùng để Member xem và xác nhận. **Đề xuất:** phí giao hàng không thuộc cơ sở cộng điểm.
6. Mở checkout hoặc thử mã chưa giữ lượt voucher hay điểm. Khi Member xác nhận đặt hàng, Backend kiểm tra lại điều kiện và tạo đơn cùng phần quyền lợi tạm giữ theo [FE-08](#fe-08).
7. Khi thanh toán đầy đủ được xác minh thành công, hệ thống chốt voucher, trừ điểm đã dùng và xử lí cộng điểm mới theo phần Điểm thưởng. Với đơn 0 đồng hợp lệ, chốt quyền lợi khi Backend xác nhận đơn theo nhánh tương ứng tại FE-08, không chờ thanh toán qua cổng.

### Checkout sản phẩm — Nhánh lỗi

1. Nhập voucher workshop tại checkout sản phẩm: từ chối mã và nêu rõ phạm vi áp dụng.
2. Mã không tồn tại, hết hạn, bị dừng, hết lượt hoặc không đạt điều kiện đơn: hiển thị lý do, cho chọn mã khác hoặc tiếp tục không dùng voucher.
3. Điểm khả dụng không đủ hoặc vượt giới hạn: Backend tính lại số điểm tối đa hợp lệ; Frontend cập nhật giới hạn và giá trị quy đổi, yêu cầu Member chọn lại và xác nhận tổng tiền mới.
4. Giá, ưu đãi, điểm hoặc hạn mức thay đổi trước khi tạo đơn: Backend yêu cầu xem lại báo giá; không tự tạo đơn với tổng tiền khác bản Member đã xác nhận.
5. Lỗi tạo đơn hoặc không giữ được quyền lợi: không để lại phần giữ dở dang; xử lí cùng bước tạo đơn tại FE-08. Mất phản hồi không được tự động tạo một đơn mới.

## Áp dụng ưu đãi khi đặt workshop

### Đặt workshop — Luồng thành công

1. Member chọn gói workshop, chi nhánh, ngày, phiên và thông tin booking theo [FE-03](#fe-03), rồi mở bước xác nhận đặt chỗ trên website.
2. Frontend hiển thị các voucher workshop phù hợp. Member chọn mã đã lưu hoặc nhập mã trực tiếp.
3. Backend kiểm tra phạm vi workshop, thời gian được đặt bằng mã, gói/ngày/phiên, tổng phí gói tối thiểu và hạn mức sử dụng; trả mức giảm hợp lệ. **Đề xuất:** mỗi booking chỉ dùng một voucher, trần giảm không nhân theo số người.
4. Voucher được trừ trên tổng phí gói trước khi tính cọc. Nếu chính sách cho phép dùng thêm điểm, Backend tính số điểm tối đa hợp lệ theo điểm khả dụng, giá trị booking và giới hạn chính sách. Frontend hiển thị điểm khả dụng, số điểm tối đa được dùng và số tiền giảm tương ứng để Member lựa chọn; nếu voucher không cho kết hợp điểm thì không cho chọn dùng điểm và nêu lý do. **Đề xuất:** trừ giá trị quy đổi của điểm đã chọn sau voucher và trước khi tính cọc; lượng dùng tối thiểu và trần giảm bằng điểm cụ thể **chưa chốt**.
5. Frontend hiển thị phí gói ban đầu, giảm voucher, giảm bằng điểm, phí gói sau giảm, tiền cọc và phần còn phải thu để Member xác nhận. Tiền cọc được tính theo chính sách booking trên phí gói sau giảm; tỷ lệ cọc cụ thể trong luồng này **chưa chốt**.
6. Khi Member xác nhận booking, Backend kiểm tra lại điều kiện và tạm giữ lượt voucher, điểm cần dùng cùng việc ghi nhận booking. **Đề xuất:** xác minh cọc thành công thì chốt lượt voucher và trừ điểm.
7. Khi thu phần còn lại, hệ thống dùng số tiền đã ghi nhận của booking; không áp dụng lại voucher hay trừ lại điểm. **Đề xuất:** chỉ cộng điểm mới sau khi Member đã tham gia xong và thanh toán đủ, không cộng khi mới tạo booking hoặc mới trả cọc.

### Đặt workshop — Nhánh lỗi

1. Nhập voucher sản phẩm: từ chối mã và hướng dẫn chọn voucher workshop.
2. Gói, chi nhánh, ngày/phiên, thời gian đặt hoặc giá trị tối thiểu không phù hợp: thông báo điều kiện chưa đạt, chưa áp dụng voucher.
3. Voucher hết lượt hoặc điểm không còn đủ trước lúc xác nhận: Backend cập nhật ưu đãi hợp lệ và số điểm tối đa được dùng; Frontend yêu cầu chọn lại, tính lại phí gói/cọc và cho Member xác nhận lại.
4. Booking hết hạn trước khi thanh toán được chấp nhận: giải phóng lượt voucher và điểm đang giữ; không ghi nhận là đã sử dụng.
5. **Đề xuất:** trường hợp hủy, hoàn tiền hoặc không đến sau khi đã chốt quyền lợi cần quy tắc riêng về hoàn voucher, hoàn điểm đã dùng và điều chỉnh điểm đã ghi nhận; chưa tự coi các trường hợp này giống hết hạn chưa thanh toán.

## Điểm thưởng

### Phạm vi và cấu hình chính sách

1. Member có một số dư điểm dùng chung: được kiếm điểm từ mua sản phẩm và workshop, rồi dùng điểm để giảm tiền cho một trong hai dịch vụ. Điểm không chia thành ví sản phẩm và ví workshop.
2. Điểm chỉ dùng được sau khi đã cộng thành công; không dùng điểm dự kiến kiếm từ giao dịch để giảm chính giao dịch đó. **Điểm chưa hết hạn.**
3. Admin cấu hình tỷ lệ cộng điểm và giá trị quy đổi. **Đề xuất:** ban đầu dùng cùng tỷ lệ cộng điểm cho cả hai dịch vụ; cấu hình lượng dùng tối thiểu, trần giảm bằng điểm và điều kiện kết hợp ưu đãi. Tỷ lệ cộng điểm, giá trị quy đổi mỗi điểm, lượng dùng tối thiểu, mức trần và cách làm tròn đều **chưa chốt**.
4. Backend kiểm tra quyền Admin và tính hợp lệ của cấu hình trước khi lưu. Nếu dữ liệu sai hoặc lưu lỗi, yêu cầu sửa/thử lại; không báo đã cập nhật chính sách khi chưa có xác nhận.
5. Luồng dùng điểm tại checkout được mô tả ở phần mua sản phẩm và đặt workshop; việc giữ/chốt điểm được mô tả ở phần Chốt và giải phóng quyền lợi bên dưới.

### Cộng điểm từ giao dịch — Luồng thành công

1. Backend nhận kết quả nghiệp vụ đã được xác minh: với sản phẩm là thanh toán đầy đủ thành công; **Đề xuất:** với workshop là đã tham gia xong và thanh toán đủ.
2. Backend kiểm tra giao dịch thuộc Member và chưa được cộng điểm. Workshop mới trả cọc chưa đủ điều kiện theo đề xuất này.
3. **Đề xuất:** Backend tính điểm trên tiền thực trả sau các khoản giảm và điểm đã dùng, không gồm phí giao hàng. Workshop tính một lần trên phí gói thực trả; không cộng riêng tiền cọc rồi cộng lại toàn bộ phí.
4. Backend áp dụng chính sách tương ứng để tính số điểm được cộng, ghi lịch sử gắn với giao dịch và cập nhật số dư Member. Tỷ lệ tính điểm và cách làm tròn cụ thể **chưa chốt**; chỉ áp dụng sau khi chính sách đã được thống nhất và cấu hình hợp lệ.
5. Khi xử lí lại cùng một kết quả, Backend trả trạng thái đã ghi nhận; không cộng thêm điểm lần nữa.

### Xem điểm và lịch sử — Luồng thành công

1. Member đăng nhập, mở **My Loyalty**.
2. Backend trả số dư điểm, phần đang tạm giữ, điểm khả dụng và lịch sử cộng/dùng điểm của chính Member đó.
3. Frontend hiển thị giá trị quy đổi theo chính sách hiện hành; mỗi mục lịch sử cho biết giao dịch liên quan và số điểm thay đổi.
4. Sau khi phát sinh cộng điểm, chốt dùng điểm hoặc giải phóng phần đang giữ, Member có thể tải lại để xem số liệu đã được Backend xác nhận.

### Điểm thưởng — Nhánh lỗi

1. Giao dịch chưa đủ điều kiện hoặc workshop mới trả cọc: chưa cộng điểm; không hiển thị điểm dự kiến như điểm khả dụng.
2. Chưa có chính sách hợp lệ hoặc lỗi ghi nhận điểm: chưa báo cộng thành công; giữ khả năng xử lí lại giao dịch mà không cộng trùng.
3. Mất phản hồi khi cộng/trừ điểm: kiểm tra kết quả đã ghi nhận trước khi thử lại, không thực hiện thêm một lần chỉ vì Frontend chưa nhận được kết quả.
4. Phiên đăng nhập hết hạn hoặc tải My Loyalty lỗi: yêu cầu đăng nhập lại hoặc thử tải lại; không hiển thị số dư cũ như số điểm hiện tại đã xác nhận.

## Chốt và giải phóng quyền lợi

### Chốt lượt voucher và điểm — Luồng thành công

1. Khi Member xác nhận đặt hàng/booking, Backend tạm giữ lượt voucher và điểm cần dùng cùng thao tác ghi nhận giao dịch. Điểm đang giữ không còn khả dụng cho giao dịch khác.
2. Với sản phẩm, khi thanh toán được xác minh thành công, Backend chốt lượt voucher và trừ điểm đang giữ đúng một lần. Đơn 0 đồng hợp lệ được chốt khi Backend xác nhận đơn theo FE-08.
3. **Đề xuất:** với workshop, khi cọc được xác minh thành công, Backend chốt lượt voucher và trừ điểm đang giữ; đợi hoàn thành và trả đủ mới cộng điểm mới.
4. Backend ghi kết quả sử dụng theo giao dịch để xử lí lại an toàn; gửi lại cùng kết quả thanh toán không tiêu dùng thêm lượt hoặc trừ thêm điểm.

### Hết hạn khi chưa thanh toán được chấp nhận

1. Backend xác định đơn/booking đã hết hạn và chưa có kết quả thanh toán thành công được chấp nhận theo luồng nghiệp vụ tương ứng.
2. Backend giải phóng lượt voucher và điểm đang giữ, trả điểm về khả dụng; không ghi nhận đã dùng voucher hoặc đã tiêu điểm.
3. Giải phóng không gia hạn chương trình. Nếu Member đặt lại, Backend kiểm tra điều kiện hiện hành; voucher có thể đã hết hạn hoặc hết lượt.
4. Việc giải phóng phần chưa dùng không đồng nghĩa hoàn quyền lợi đã chốt. **Đề xuất:** quy tắc hoàn sau hủy/hoàn tiền/không đến cần được xác định riêng.

### Chốt và giải phóng — Nhánh lỗi

1. Thanh toán và xử lí hết hạn diễn ra gần nhau: Backend kiểm tra trạng thái giao dịch trước khi cập nhật; không vừa chốt sử dụng vừa trả lại cùng lượt voucher/điểm.
2. Kết quả thanh toán chưa rõ hoặc đến muộn: xử lí theo luồng thanh toán của FE-08/FE-03; không tự cộng điểm hoặc chốt ưu đãi chỉ từ thông báo trên trình duyệt.
3. Lỗi lưu hoặc mất phản hồi: kiểm tra trạng thái đã ghi nhận trước khi xử lí lại; không trừ, cộng hoặc giải phóng thêm lần nữa.

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

Feature này mô tả mua ngay, giỏ hàng, checkout, tạo đơn, thanh toán retail, tra cứu đơn và giao nhận. Dữ liệu sản phẩm/biến thể do Staff quản lí tại [FE-02](#fe-02); voucher, giảm tự động và điểm thưởng theo [FE-06](#fe-06); thông báo theo [FE-07](#fe-07). Số lượng bán dùng chung toàn hệ thống; Staff tự điều phối nơi chuẩn bị hàng, không kiểm tra số lượng theo từng chi nhánh để tự chọn nơi xử lí. Phần tích hợp GHTK và luồng chuẩn bị/bàn giao chi tiết giữ để hoàn thiện riêng như ghi chú bên dưới.

## Mua hàng

### Điều kiện và quy tắc chung

1. Khách được xem catalogue khi chưa đăng nhập. Trước khi thêm giỏ hoặc mua ngay, khách phải đăng nhập, xác minh email và có tài khoản được phép sử dụng theo [FE-01](#fe-01).
2. Frontend giữ lựa chọn sản phẩm, biến thể và số lượng trong quá trình xác thực; sau đó Backend kiểm tra lại điều kiện trước khi tiếp tục, không tự đặt hàng.
3. Backend xác định giá hiện tại, lượng bán khả dụng, thông tin giao và tổng tiền; ưu đãi được kiểm tra theo FE-06. Dữ liệu trên trình duyệt không thay cho kết quả kiểm tra này.
4. Mua ngay và mua qua giỏ dùng chung checkout. Xem catalogue, giỏ hoặc checkout chưa tạo đơn hay giữ số lượng/quyền lợi; trang chi tiết đơn được dùng sau khi đơn đã tạo.

### Tiếp nhận lựa chọn mua từ catalogue

1. Khách xem/chọn sản phẩm tại [FE-02](#fe-02), rồi chuyển sang luồng mua hàng với sản phẩm, biến thể và số lượng đã chọn.
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

1. Backend lấy lại lựa chọn từ nguồn Mua ngay hoặc các dòng giỏ đã chọn, kiểm tra tài khoản, giá hiện tại và số lượng bán chung còn khả dụng của từng biến thể.
2. Khách chọn cách nhận và cung cấp thông tin theo phần **Thông tin nhận hàng và GHTK** bên dưới. Backend kiểm tra thông tin nhận, phương thức phục vụ và khoản phí giao nhận nếu có.
3. Frontend hiển thị sản phẩm, biến thể, số lượng, đơn giá, thông tin nhận hàng và phí đã được xác nhận. Thay đổi thông tin ảnh hưởng đến phí phải được tính và hiển thị lại trước khi khách đặt hàng.
4. Số lượng được kiểm tra trên nguồn bán chung, không kiểm tra từng chi nhánh đủ hàng hay tự chọn nơi chuẩn bị hàng theo số lượng. Staff tự điều phối nơi chuẩn bị khi xử lí đơn.

### Checkout — Voucher, điểm thưởng và tổng tiền

1. Khách chọn ưu đãi và điểm tại checkout theo [FE-06 — Áp dụng ưu đãi khi mua sản phẩm](#fe-06). FE-06 quyết định điều kiện hợp lệ và khoản giảm.
2. Frontend hiển thị các khoản giảm từ kết quả FE-06, phí giao nhận đã xác nhận và tổng cuối cùng, rồi cho khách tiếp tục tạo đơn.

### Tạo đơn — Kiểm tra và ghi nhận chung

1. Frontend gửi yêu cầu xác nhận lựa chọn cùng dấu hiệu nhận diện yêu cầu để Backend chống tạo trùng; khóa nút trong lúc chờ. Backend không chỉ dựa vào việc khóa nút để chống gửi lặp.
2. Backend kiểm tra lại danh tính, email đã xác minh, trạng thái tài khoản, quyền sở hữu các dòng giỏ nếu có, sản phẩm/biến thể, số lượng bán chung, giá, thông tin/phí giao nhận đã xác nhận và ưu đãi theo FE-06.
3. Nếu báo giá hoặc điều kiện khác bản khách đã xem, Backend yêu cầu xem lại và xác nhận mới; chưa tạo đơn với điều khoản thay đổi.
4. Khi hợp lệ, Backend ghi nhận một đơn cùng bản chụp nguồn mua, các dòng mua, biến thể, số lượng, giá, người nhận, thông tin/cách nhận, ưu đãi, phí và tổng tiền đã xác nhận. Nơi chuẩn bị hàng do Staff điều phối sau ở bước xử lí giao nhận. Thay đổi hồ sơ, catalogue hay giỏ sau đó không sửa các thông tin đã chấp nhận của đơn.
5. Backend tạo đơn, giữ số lượng từ nguồn bán chung của đúng biến thể và phối hợp tạm giữ voucher/điểm theo [FE-06](#fe-06) trong cùng thao tác toàn bộ hoặc không có gì. Nếu không giữ đủ sản phẩm/quyền lợi thì từ chối, không tạo đơn được chấp nhận một phần.
6. Gửi lại cùng yêu cầu và cùng nội dung trả về đơn đã tạo cùng thời hạn gốc; nếu dùng lại dấu hiệu nhận diện với nội dung khác thì từ chối và yêu cầu xem lại. Mất phản hồi không phải lý do tự tạo đơn mới.
7. Với tổng dương đủ điều kiện theo cấu hình tích hợp VNPay, đơn chuyển **Chờ thanh toán**, ghi thời điểm tạo đơn, hạn link sau 10 phút và hạn giữ hàng/ưu đãi sau 15 phút; tiếp tục mục Thanh toán.
8. Với tổng cuối cùng bằng 0 hợp lệ, xử lý theo nhánh đơn 0 đồng bên dưới; tổng âm hoặc số tiền dương không đạt điều kiện tích hợp phải được sửa trước khi chấp nhận đơn.

### Đơn 0 đồng — Xác nhận trực tiếp

1. Backend kiểm tra tổng cuối cùng, bao gồm phí giao hàng, thực sự bằng 0 theo chính sách hợp lệ; tiền hàng bằng 0 nhưng còn phí giao thì vẫn là đơn cần thanh toán.
2. Backend xác nhận đơn, chốt số lượng bán và quyền lợi theo [FE-06](#fe-06) đúng một lần trong cùng thao tác; chuyển **Đã xác nhận — Chờ xử lý**.
3. Hệ thống không tạo yêu cầu hoặc giao dịch VNPay, không ghi nhận khách đã chuyển tiền qua cổng thanh toán.
4. Nếu mua qua giỏ, Backend dọn đúng lượng đã mua theo quy tắc dọn giỏ trong mục Thanh toán; nếu Mua ngay thì giữ nguyên giỏ.
5. Frontend hiển thị chi tiết đơn 0 đồng đã xác nhận; hệ thống gửi email xác nhận. Gửi email lỗi không đảo kết quả xác nhận đơn.

### Mua hàng và checkout — Nhánh lỗi

1. Sản phẩm ngừng bán, thiếu thuộc tính, biến thể không hợp lệ, số lượng không phải số nguyên dương hoặc hết hàng: chỉ rõ dòng và nguyên nhân để khách sửa; không tự đổi biến thể, giảm số lượng hoặc đặt các dòng còn lại.
2. Quyền truy cập không hợp lệ hoặc tài khoản bị khóa: từ chối thao tác bảo vệ; không tiết lộ giỏ hay đơn của tài khoản khác. Nếu cần đăng nhập/xác minh lại thì giữ lựa chọn để tiếp tục sau khi đủ điều kiện và kiểm tra lại dữ liệu.
3. Giỏ rỗng, chưa tích chọn hoặc dòng đã bị sửa/xóa ở nơi khác: tải dữ liệu hiện tại, yêu cầu chọn và xác nhận lại; không lấy toàn bộ giỏ thay cho lựa chọn cũ.
4. Thông tin nhận hàng thiếu hoặc không hợp lệ: hiển thị lỗi tại trường tương ứng, chưa tạo đơn.
5. Số lượng bán chung không đủ: chỉ rõ biến thể cần sửa số lượng hoặc bỏ chọn; không tự đổi biến thể hay chấp nhận một phần đơn.
6. Thông tin/cách nhận hoặc báo phí chưa hợp lệ: hiển thị lý do, giữ lựa chọn để khách sửa/thử lại; không tự coi phí chưa xác định là bằng 0.
7. Voucher hoặc điểm không hợp lệ, hết lượt, không đủ số dư hoặc thiếu chính sách: báo rõ, giữ lựa chọn để khách sửa. Chỉ tiếp tục không ưu đãi khi khách chủ động chấp nhận báo giá mới.
8. Mất mạng hoặc lỗi lưu giỏ: phân biệt thao tác chưa được xác nhận với giỏ rỗng; cho tải lại/thử lại mà không tự cộng số lượng lần nữa khi kết quả lưu trước chưa rõ.
9. Bấm Đặt hàng lặp hoặc mất phản hồi tạo đơn: Frontend tra lại kết quả yêu cầu cũ; Backend trả lại đơn đã tạo nếu có. Không tạo thêm đơn, giữ thêm hàng hoặc kéo dài thời hạn vì thử lại.

## Thông tin nhận hàng và GHTK

### Chọn cách nhận và xác nhận phí

1. Tại bước checkout của luồng Mua hàng, khách chọn **Tự nhận tại cửa hàng** hoặc **Giao hàng qua GHTK**, nhập/chọn thông tin người nhận và kiểm tra lại trước khi xác nhận.
2. Nếu tự nhận, khách cung cấp tên, số điện thoại và chọn cửa hàng đang hoạt động; phí giao hàng bằng 0. Cửa hàng nhận là lựa chọn giao nhận, không phải căn cứ kiểm tra số lượng bán theo chi nhánh.
3. Nếu giao GHTK, khách cung cấp tên, số điện thoại và địa chỉ đầy đủ theo dữ liệu tích hợp hỗ trợ. FE-08 phụ trách lấy báo giá bằng thông tin nơi gửi, nơi nhận, trọng lượng và dữ liệu gói hàng cần thiết; không giả định phí chỉ theo khoảng cách.
4. Frontend hiển thị khoản phí được xác nhận và thông tin dự kiến nếu nhà cung cấp trả về. Khi địa chỉ, cách nhận hoặc nội dung mua thay đổi, FE-08 tính lại phí để khách xem trước khi tạo đơn; bước Tạo đơn sử dụng kết quả này để chốt tổng thanh toán.
5. Tích hợp GHTK giữ ở phạm vi FE-08 để hoàn thiện riêng. Cách xác định nơi gửi dùng báo phí trước khi Staff điều phối nơi chuẩn bị hàng và cách cung cấp đầu vào gói hàng **chưa chốt**; không tự suy ra nơi gửi từ số lượng theo chi nhánh.
6. Sau khi đơn được xác nhận, Staff tự điều phối nơi chuẩn bị và tiếp tục xử lí giao nhận tại FE-08; không ảnh hưởng nguồn số lượng bán chung hoặc tự đổi khoản tiền khách đã chấp nhận. Luồng chuẩn bị/bàn giao chi tiết sẽ bổ sung riêng.

### Thông tin nhận hàng và phí — Nhánh lỗi

1. Thiếu/sai thông tin hoặc cửa hàng nhận không hoạt động: chỉ rõ điều kiện chưa đạt, cho khách sửa; chưa tạo đơn với thông tin không hợp lệ.
2. Không báo được phí, ngoài vùng phục vụ hoặc chưa đủ đầu vào tích hợp: giữ thông tin khách đã nhập, cho thử lại/sửa hoặc chọn tự nhận; không tự cho phí giao bằng 0.
3. Phí thay đổi trước khi đặt: trả thông tin mới về checkout để khách xác nhận lại; không tự tạo đơn với tổng tiền khác bản đã xem.

Thanh toán VNPay dưới đây mô tả trường hợp mua sản phẩm bán sẵn. Nghĩa vụ thanh toán của booking workshop được mô tả tại FE-03; không mặc định dùng nguyên quy tắc thanh toán bán lẻ.

## Thanh toán

### Điều kiện và thời hạn

1. Luồng VNPay áp dụng cho đơn Chờ thanh toán có tổng dương hợp lệ, thuộc khách hiện tại và còn thời hạn. Đơn 0 đồng đi theo nhánh xác nhận trực tiếp ở mục Mua hàng.
2. Link thanh toán và mọi lần thanh toán lại hết hạn sau 10 phút tính từ lúc tạo đơn; giữ hàng và quyền dùng ưu đãi hết hạn sau 15 phút tính từ cùng thời điểm. Không gia hạn khi tải lại trang, bấm lại hoặc tạo lần thanh toán khác.
3. Tạm giữ chưa chốt giao dịch. Khi thanh toán được xác nhận thành công, Backend phối hợp chốt quyền lợi và cộng điểm theo [FE-06](#fe-06); hết hạn chưa thanh toán thì phối hợp giải phóng phần đang giữ theo cùng feature.
4. Backend quản lý trạng thái đơn và từng lần thanh toán riêng. Mỗi lần có tham chiếu VNPay riêng; không coi nút hủy hoặc trang trình duyệt quay về là bằng chứng đủ để kết luận kết quả giao dịch.

### Thanh toán VNPay — Luồng thành công

1. Sau khi tạo đơn hợp lệ, Backend ghi nhận lần thanh toán và tạo URL VNPay chứa tham chiếu, số tiền đúng của đơn, thời hạn gốc và thông tin kiểm tra toàn vẹn theo tích hợp.
2. Backend trả mã đơn và URL; Frontend chuyển khách sang VNPay. Nếu tạo URL lỗi sau khi đơn đã được ghi nhận, hiển thị đúng đơn Chờ thanh toán để khôi phục, không tự tạo lại đơn.
3. Khách chọn ngân hàng, thẻ hoặc phương thức VNPay đang hỗ trợ theo cấu hình tích hợp, rồi hoàn tất thanh toán trên cổng.
4. VNPay gửi kết quả về Backend qua IPN và chuyển trình duyệt về website. Hai luồng có thể đến khác thứ tự; Backend có thể truy vấn kết quả qua API VNPay để đối soát khi cần.
5. Backend kiểm tra chữ ký, thông tin merchant, tham chiếu, số tiền, mã kết quả/trạng thái giao dịch và giao dịch đã xử lý. Chỉ chấp nhận bằng chứng IPN hoặc kết quả truy vấn đã xác thực; dữ liệu trình duyệt quay về không tự đánh dấu đơn đã thanh toán.
6. Khi thanh toán thành công được xác minh và tài nguyên còn được giữ hợp lệ, Backend chốt số lượng bán, quyền lợi theo FE-06, ghi nhận thanh toán và chuyển đơn **Đã thanh toán — Chờ xử lý** trong cùng thao tác. Kết quả thành công được dùng để cộng điểm theo FE-06; xử lí lặp không chốt hoặc cộng thêm lần nữa.
7. Nếu kết quả đến sau hạn link nhưng trước hạn giữ, Backend đối chiếu thời điểm thanh toán hợp lệ với hạn link gốc trước khi chốt; nếu có bất thường thì chuyển đối soát, không chỉ dựa vào thời điểm nhận IPN để kết luận.
8. Với đơn mua qua giỏ, Backend chỉ loại bỏ lượng đã mua thuộc các dòng checkout, dựa trên nguồn và phiên bản dòng đã ghi nhận. Giữ các dòng chưa mua và lượng khách thêm sau; không tạo lại dòng khách đã xóa hoặc trừ nhầm dòng thay thế. Nếu dòng đã đổi theo cách không thể đối chiếu an toàn, giữ dòng đó để khách xem lại thay vì đoán số lượng cần xóa. Dọn giỏ chỉ áp dụng một lần và lỗi dọn giỏ không đảo giao dịch đã thành công.
9. Với đơn Mua ngay, hệ thống không dọn hoặc thay đổi giỏ, kể cả giỏ đang chứa cùng biến thể.
10. Frontend lấy trạng thái đơn từ Backend và hiển thị trang chi tiết cùng kết quả thanh toán. Hệ thống gửi email xác nhận; lỗi gửi thư được xử lý lại riêng, không yêu cầu khách thanh toán lại.

### Chưa rõ kết quả hoặc khách chưa quay về

1. Nếu trình duyệt quay về trước khi Backend có xác nhận, Frontend hiển thị **Đang xác nhận thanh toán** và lấy lại trạng thái từ Backend; không báo thành công chỉ dựa vào URL.
2. Backend tiếp nhận IPN hoặc truy vấn VNPay để xác định kết quả. Chưa cho tạo lần thanh toán mới khi lần trước còn chưa rõ, tránh khách trả tiền hai lần.
3. Nếu khách đóng trang, mất mạng hoặc không quay lại website, Backend vẫn xử lí thông báo hợp lệ. Khách mở lại đơn tại **Đơn hàng của tôi** để xem trạng thái do Backend xác nhận.
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
3. Nếu xác định đơn chưa được thanh toán, Backend chuyển đơn **Hết hạn thanh toán**, giải phóng số lượng đang giữ và phối hợp giải phóng voucher/điểm theo FE-06 đúng một lần; không dọn giỏ cho đơn hết hạn.
4. Nếu đã xác nhận thanh toán hợp lệ, xử lý theo luồng thành công và không giải phóng tài nguyên của đơn đã chốt.
5. Nếu chưa xác định được do dịch vụ thanh toán lỗi hoặc kết quả không nhất quán, ghi nhận **Cần đối soát**, tạm ngừng thanh toán mới và chuyển xử lý vận hành; không tự kết luận chưa thanh toán hoặc giải phóng tài nguyên khi chưa đủ bằng chứng. Đây là ngoại lệ cần theo dõi, không tạo lại thời hạn 15 phút mới.

### Thông báo bất thường hoặc thanh toán đến muộn

1. Chữ ký không hợp lệ, sai merchant, sai số tiền hoặc tham chiếu không khớp: Backend không dùng thông báo đó để đánh dấu đơn đã thanh toán; ghi nhận an toàn để kiểm tra và trả phản hồi phù hợp cho cổng.
2. IPN lặp hoặc nhận lại cùng kết quả truy vấn: trả kết quả xử lý đã ghi nhận, không trừ hàng/điểm, tiêu voucher, dọn giỏ hoặc xác nhận đơn thêm lần nữa.
3. Thanh toán thành công được xác thực nhưng tài nguyên đã giải phóng, đơn đã hết hạn hoặc thời điểm thanh toán ngoài điều kiện cho phép: ghi nhận khoản tiền thực nhận và chuyển **Cần xử lý thanh toán**. Không tự phục hồi đơn hoặc bán vượt tồn.
4. Có nhiều giao dịch thực sự thành công cho cùng đơn: chỉ chốt đơn một lần, ghi riêng khoản tiền trả trùng và chuyển xử lý thanh toán; không nhầm với việc nhận lặp một thông báo cho cùng giao dịch.
5. Nếu đơn đã được chốt thành công trước đó, giữ nguyên kết quả chốt và đánh dấu sự cố thanh toán kèm theo để xử lý; không đưa đơn đã thành công về Chờ thanh toán.
6. Những khoản cần xử lý được chuyển sang quy trình đối soát/Refund sau này. Không tự hoàn tiền, bỏ qua khoản tiền nhận được hoặc yêu cầu khách trả thêm trong luồng này.

## Đơn hàng của tôi

### Xem danh sách và chi tiết — Luồng thành công

1. Member đăng nhập, mở **Đơn hàng của tôi**. Backend kiểm tra tài khoản và chỉ trả các đơn thuộc Member hiện tại.
2. Frontend hiển thị mã đơn, thời điểm đặt, tổng tiền và trạng thái. Member chọn một đơn để mở chi tiết.
3. Backend kiểm tra lại quyền sở hữu và trả bản chụp sản phẩm/biến thể, số lượng, giá, khoản giảm, phí, tổng tiền, thông tin nhận cùng trạng thái đơn/thanh toán. Không tính lại đơn cũ bằng giá catalogue hay chính sách ưu đãi hiện tại.
4. Nếu đơn còn đủ điều kiện thanh toán lại, Frontend hiển thị **Thanh toán lại** và tiếp tục phần **Thanh toán thất bại, hủy trên cổng và thanh toán lại** trong feature này. Backend vẫn kiểm tra trạng thái, kết quả lần trước và thời hạn gốc trước khi cho thanh toán; không tạo đơn mới hoặc gia hạn.
5. Đơn đã xác nhận, hết hạn hoặc đang chờ xác định kết quả được hiển thị đúng tình trạng để khách theo dõi; chỉ cho thao tác phù hợp với trạng thái Backend đã xác nhận.

### Xem đơn — Nhánh lỗi

1. Phiên đăng nhập không hợp lệ: hướng dẫn đăng nhập lại. Đơn không tồn tại hoặc không thuộc Member: từ chối truy cập, không tiết lộ thông tin đơn của người khác.
2. Lỗi tải danh sách/chi tiết: cho tải lại; không hiển thị lỗi tải như danh sách đơn rỗng.
3. Trạng thái đổi hoặc thời hạn kết thúc trước khi bấm thanh toán lại: tải trạng thái mới và áp dụng nhánh tại phần Thanh toán; không tạo thêm lần thanh toán khi giao dịch trước chưa rõ kết quả.

## Refund

Chưa mô tả luồng. Giữ đây là mục nghiên cứu tiếp theo liên quan đơn hàng và điều chỉnh tài chính; không coi refund là chức năng đã được report chấp thuận. Luồng thanh toán hiện chỉ chuyển các khoản bất thường sang bước đối soát/Refund để xử lý sau.

<a id="fe-09"></a>

# FE-09 — Administrative Parameter Configuration

Chưa mô tả luồng cấu hình tham số, cấu hình tích hợp và hỗ trợ kỹ thuật. Các tham số nghiệp vụ cần được chủ feature tương ứng xác định; chưa bổ sung tham số cho thuật toán thiết kế nhẫn đang được thiết kế lại.

<a id="fe-10"></a>

# FE-10 — Manager Dashboard

Chưa mô tả luồng dashboard và báo cáo tổng hợp.
