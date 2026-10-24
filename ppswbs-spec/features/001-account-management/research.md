<a id="fe-01"></a>

# FE-01 — Account Management

FE-01 gồm authentication, đăng xuất, hồ sơ, đổi email, quản trị tài khoản nội bộ, gán vai trò, khóa/mở khóa và xem quyền cố định. Đây là tài liệu nghiên cứu, chưa phải chức năng đã triển khai. Email xác minh/đặt lại mật khẩu do Firebase Auth xử lý; email quản trị tài khoản dẫn chiếu FE-07. Không thay code, API, database hoặc spec 001-account-management trong lần cập nhật này.

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

## Đăng xuất

### Luồng thành công

1. Người dùng chọn **Đăng xuất** trên thiết bị hiện tại; Frontend khóa thao tác gửi lặp trong khi xử lý.
2. Frontend gọi `signOut(auth)` để kết thúc đăng nhập Firebase trên trình duyệt và xóa trạng thái đăng nhập/dữ liệu riêng đang hiển thị.
3. Nếu website có phiên phía server, Frontend yêu cầu Backend kết thúc phiên đó; Backend xóa cookie/phiên hiện tại theo cơ chế bảo vệ của website.
4. Khi các bước cần thiết hoàn tất, Frontend chuyển về trang công khai. Mở lại chức năng cần đăng nhập phải đi qua kiểm tra quyền và đăng nhập lại nếu chưa có phiên hợp lệ.
5. Đây là đăng xuất thiết bị hiện tại, không thu hồi tất cả phiên của UID. Khác với đổi/đặt lại mật khẩu hoặc khóa tài khoản, các thiết bị khác không bị đăng xuất bởi thao tác này.

### Nhánh lỗi

1. Firebase hoặc Backend chưa xác nhận kết thúc phiên: thông báo phần chưa hoàn tất, cho thử lại; không báo đã kết thúc toàn bộ đăng nhập khi phiên server có thể còn hiệu lực.
2. Phiên đã hết hạn hoặc đã đăng xuất: xóa trạng thái cục bộ còn lại, hiển thị trang công khai; không yêu cầu người dùng đăng nhập chỉ để đăng xuất lần nữa.
3. Mất mạng: xóa dữ liệu riêng trên giao diện khi có thể nhưng không coi xóa giao diện là đã kết thúc phiên server. Khi kết nối lại, hoàn tất bước còn thiếu; Backend vẫn kiểm tra quyền khi có yêu cầu bảo vệ.

## Hồ sơ cá nhân

### Xem hồ sơ

1. Người dùng đã đăng nhập chọn **Hồ sơ cá nhân**; Frontend yêu cầu Backend lấy hồ sơ của chính người đang đăng nhập.
2. Backend xác thực danh tính, email đã xác minh và trạng thái tài khoản; xác định chủ hồ sơ theo UID đã kiểm tra, không theo mã người dùng/email do Frontend tự chọn.
3. Nếu email chưa xác minh, Backend chưa cho đọc/sửa hồ sơ; Frontend hướng dẫn hoàn tất bước xác minh đã có rồi thử lại. Điều này không thay quy tắc đăng nhập được phép trước xác minh trong luồng authentication.
4. Backend trả họ tên, số điện thoại và email của tài khoản. Nếu chưa có hồ sơ, trả các trường hồ sơ trống, không tự tạo dữ liệu chỉ vì khách mở trang.
5. Frontend hiển thị dữ liệu; email chỉ xem ở phần sửa hồ sơ thông thường. Nút **Đổi email** mở luồng riêng bên dưới nếu tài khoản đủ điều kiện.

### Sửa hồ sơ

1. Người dùng sửa họ tên và số điện thoại rồi bấm **Lưu**. Chưa thêm địa chỉ hoặc OTP điện thoại trong phạm vi này.
2. Frontend kiểm tra dữ liệu nhập và gửi các trường được phép sửa; không gửi thao tác đổi email, UID, vai trò hoặc trạng thái qua form hồ sơ.
3. Backend kiểm tra lại danh tính, email verified, trạng thái và chủ hồ sơ; kiểm tra họ tên/số điện thoại theo quy tắc hồ sơ, từ chối yêu cầu sửa trường không được phép.
4. Backend lưu hồ sơ và lịch sử cập nhật tối thiểu trong cùng giao dịch. Lưu thất bại giữ nguyên dữ liệu đã chấp nhận trước đó; không ghi tên/số điện thoại đầy đủ hoặc token vào log thông thường.
5. Backend trả kết quả đã lưu; Frontend hiển thị lại thông tin và thông báo thành công. Việc sửa số điện thoại liên hệ không biến số đó thành phương thức đăng nhập hoặc số đã xác minh.

### Nhánh lỗi hồ sơ

1. Chưa đăng nhập, token/phiên hết hạn hoặc bị thu hồi: yêu cầu đăng nhập lại; không trả hồ sơ trước khi kiểm tra danh tính.
2. Chưa verified hoặc tài khoản bị khóa: từ chối truy cập theo điều kiện tương ứng; không cho sửa hồ sơ để bỏ qua trạng thái khóa.
3. Yêu cầu hồ sơ người khác hoặc sửa UID/email/vai trò/trạng thái: từ chối; ẩn trường trên giao diện không thay kiểm tra Backend.
4. Tên hoặc số điện thoại không hợp lệ: báo lỗi trường tương ứng, giữ nội dung nhập để sửa; chưa lưu thay đổi không hợp lệ.
5. Lỗi lưu hoặc mất phản hồi: không báo thành công khi chưa có kết quả. Cho tải lại hồ sơ để kiểm tra dữ liệu thực tế trước khi lưu lại; không suy rằng dữ liệu cũ đã mất.

## Đổi email đăng nhập

### Luồng thành công

1. Người dùng đã đăng nhập, email hiện tại đã xác minh và tài khoản được phép sử dụng mở hồ sơ, chọn **Đổi email**. Luồng này chỉ áp dụng khi tài khoản có phương thức email/mật khẩu; tài khoản chỉ dùng Google/Facebook chưa đổi email qua website.
2. Người dùng nhập email mới; Frontend kiểm tra trường bắt buộc, định dạng và khác email hiện tại. Backend không cập nhật email chỉ từ form này.
3. Frontend yêu cầu người dùng nhập mật khẩu hiện tại, tạo credential theo email Firebase hiện hành và gọi `reauthenticateWithCredential(user, credential)`. Mật khẩu không gửi tới Backend.
4. Tái xác thực thành công, Frontend gọi `verifyBeforeUpdateEmail(user, newEmail)` để Firebase gửi link xác minh tới email mới. Đây là thư hành động do Firebase xử lý, không phải yêu cầu SMTP nghiệp vụ FE-07.
5. Frontend thông báo cần mở thư xác minh. Khi chưa xác minh, email cũ vẫn có hiệu lực; chưa thay email Backend hoặc đánh dấu email mới đã xác minh.
6. Người dùng mở link và hoàn tất xác minh qua handler Firebase. Firebase kiểm tra link và thực hiện cập nhật email nếu hợp lệ; UID không đổi.
7. Người dùng quay lại website; Frontend tải lại người dùng Firebase và lấy ID Token mới. Nếu phiên không còn hợp lệ thì đăng nhập lại bằng email mới/mật khẩu hiện tại, rồi tiếp tục đồng bộ.
8. Backend xác thực danh tính, đối chiếu email hiện hành và trạng thái xác minh từ Firebase, cập nhật email của tài khoản theo UID đã kiểm tra. Không dùng email do trình duyệt tự khai hoặc token cũ làm nguồn ghi đè; không tạo/gộp tài khoản theo email.
9. Backend trả kết quả email đã đồng bộ; Frontend hiển thị email mới. Đổi email không sửa thông tin liên hệ đã chốt của đơn/booking cũ và không thay mật khẩu.

### Nhánh lỗi đổi email

1. Email trống/sai định dạng/giống email hiện tại: yêu cầu sửa hoặc kết thúc không thay đổi, chưa gửi yêu cầu xác minh mới.
2. Email mới trùng hoặc Firebase từ chối: không cập nhật email Backend, không gộp danh tính; hướng dẫn dùng email khác hoặc liên hệ hỗ trợ.
3. Mật khẩu sai, cần đăng nhập gần đây hoặc tái xác thực thất bại: dừng trước gửi xác minh, cho thử lại hoặc đăng nhập lại.
4. Gửi thư lỗi hoặc bị giới hạn: thông báo chưa hoàn tất, cho thử lại theo điều kiện Firebase; không khẳng định email đã được đổi.
5. Link hết hạn/không hợp lệ/đã dùng: tải lại trạng thái hiện hành. Nếu chưa đổi thì yêu cầu link mới; nếu đã đổi thì đồng bộ theo UID, không chạy lại thao tác tạo tài khoản.
6. Firebase đã đổi nhưng Backend lỗi: thông báo email Firebase đã thay đổi nhưng chưa xác nhận đồng bộ website; cho thử lại bước đồng bộ bằng danh tính hiện hành. Không yêu cầu đổi lại email cũ hoặc ghi đè bằng thông tin cũ.
7. Token cũ hoặc hai yêu cầu đồng bộ khác thời điểm: Backend đối chiếu trạng thái Firebase hiện hành trước khi lưu, không để yêu cầu cũ khôi phục email trước đó. Nếu không kiểm tra được thì chưa ghi thay đổi.
8. Mất phản hồi: Frontend tải lại danh tính và hồ sơ để xác định kết quả; không tự coi là chưa đổi hoặc gửi liên tục các link mới.

## Quản trị tài khoản — Danh sách và chi tiết

### Luồng thành công

1. Admin đăng nhập, mở **Quản lý tài khoản**. Backend kiểm tra quyền quản trị trước khi trả danh sách.
2. Admin tìm theo thông tin được phép và lọc trạng thái/vai trò; Backend chỉ trả dữ liệu thuộc phạm vi quản trị, không trả mật khẩu, token hoặc thông tin giao dịch không cần thiết.
3. Admin chọn tài khoản; Backend lấy chi tiết và các thao tác được phép theo trạng thái hiện tại. Giao diện tách riêng sửa thông tin, gán vai trò/phạm vi và khóa/mở khóa.
4. Member tự đăng ký theo luồng hiện có. Admin không tạo hộ Member; Admin có thể xem thông tin được phép và khóa/mở khóa Member trong phạm vi được cấp.
5. Admin chỉ tạo/sửa/gán vai trò cho tài khoản nội bộ Staff hoặc Manager trong luồng website này. Tài khoản Admin thiết lập riêng; website không cho tạo, sửa vai trò hoặc khóa/mở khóa tài khoản Admin.

### Nhánh lỗi

1. Không có quyền hoặc token/phiên không hợp lệ: từ chối, không trả danh sách/chi tiết trước kiểm tra quyền.
2. Không có kết quả tìm kiếm: hiển thị danh sách rỗng. Mã tài khoản không tồn tại: báo không tìm thấy, không trả tài khoản khác thay thế.
3. Lỗi tải dữ liệu: phân biệt lỗi với danh sách rỗng, cho thử lại. Biết mã tài khoản không tự có quyền xem hoặc sửa.

## Tạo tài khoản nội bộ

### Luồng thành công

1. Admin chọn **Tạo tài khoản nội bộ**, nhập họ tên, email, vai trò Staff/Manager và phạm vi cửa hàng. Staff có một cửa hàng; Manager có danh sách cửa hàng phụ trách.
2. Frontend kiểm tra dữ liệu bắt buộc. Backend kiểm tra quyền Admin, vai trò cố định được phép gán, cửa hàng hợp lệ, phạm vi không rỗng và trùng danh tính. Không cho chọn Admin hoặc Member tại đây.
3. Backend ghi nhận yêu cầu tạo để truy vết tiến độ và chống gửi lặp, rồi dùng Firebase Admin SDK tạo danh tính theo yêu cầu đã kiểm tra. Không yêu cầu Admin đặt hoặc gửi mật khẩu ban đầu cho nhân viên.
4. Backend liên kết UID Firebase vừa tạo với tài khoản nội bộ ở trạng thái **Chờ hoàn tất thiết lập**, lưu thông tin, vai trò và phạm vi đã xác nhận. Trạng thái này chưa cho phép dùng chức năng Staff/Manager.
5. Backend chuẩn bị link hành động Firebase để nhân viên tự thiết lập mật khẩu, theo cơ chế đặt lại/thiết lập mật khẩu Firebase được tích hợp. Không gửi mật khẩu hoặc tự tạo token khôi phục ngoài Firebase.
6. Method tạo tài khoản gọi method chuẩn bị thư mời: tập hợp người nhận, mẫu `ACCOUNT_INVITATION`, nội dung/link hợp lệ và tham chiếu yêu cầu. Method chuẩn bị tự lưu yêu cầu email cùng giao dịch hoàn tất ghi nhận tài khoản nội bộ ở Backend, theo luồng FE-07; không gọi SMTP trong request nghiệp vụ.
7. Backend commit và trả kết quả tài khoản đã tạo, còn chờ thiết lập; job FE-07 gửi email sau commit. Kết quả này không có nghĩa nhân viên đã nhận thư hoặc đã được phép truy cập vận hành.
8. Nhân viên mở link Firebase và tự đặt mật khẩu. Sau đó đăng nhập bằng email/mật khẩu, hoàn tất xác minh email theo luồng FE-01 nếu email chưa verified. Đặt mật khẩu qua link không được tự coi là đã xác minh email.
9. Backend kiểm tra UID khớp tài khoản nội bộ đang chờ, email đã xác minh, tình trạng thiết lập Firebase và trạng thái tài khoản/phạm vi còn hợp lệ trước khi hoàn tất thiết lập. Không dùng thông báo do trình duyệt tự khai làm bằng chứng đã đặt mật khẩu/verified.
10. Khi đủ điều kiện, Backend cho phép truy cập theo vai trò và phạm vi đã gán. Nếu chưa đủ, Frontend hướng dẫn bước thiết lập còn thiếu, không cho vào nghiệp vụ nội bộ.
11. Khi UID thuộc tài khoản nội bộ, phần xử lý Backend sau xác thực phải dùng bản ghi nội bộ đó, không tự tạo Member mặc định hoặc ghi đè vai trò Staff/Manager. Luồng tự tạo Member trong đăng nhập hiện có chỉ áp dụng cho danh tính khách chưa có tài khoản nội bộ.

### Nhánh lỗi và khôi phục

1. Thiếu tên/email, dữ liệu không hợp lệ, cửa hàng không hợp lệ hoặc vai trò ngoài Staff/Manager: báo lỗi, chưa tạo danh tính hoặc cấp quyền.
2. Email đã tồn tại ở Firebase hoặc Backend: báo trùng và dừng yêu cầu tạo mới. Không tự liên kết theo email, nâng quyền Member hoặc gán vai trò cho tài khoản tìm thấy; trường hợp đã tạo dở chỉ khôi phục khi có bằng chứng UID thuộc đúng yêu cầu tạo đang xử lý.
3. Firebase tạo thất bại: ghi kết quả bước đã làm, chưa báo hoàn tất hoặc gửi thư mời. Cho thử lại đúng yêu cầu sau khi xác định trạng thái, không tạo lặp.
4. Firebase đã tạo nhưng lưu Backend hoặc chuẩn bị link/yêu cầu thư lỗi: ghi nhận tiến độ để khôi phục theo UID và yêu cầu gốc; tài khoản chưa được vào vận hành. Không giả định rollback DB có thể rollback Firebase hoặc tự xóa danh tính không xác định được chủ sở hữu.
5. Mất phản hồi hoặc bấm tạo nhiều lần: tìm kết quả yêu cầu cũ và tiếp tục bước còn thiếu, không tạo thêm danh tính, tài khoản hoặc email mời cho cùng thao tác.
6. SMTP lỗi sau commit: giữ tài khoản chờ thiết lập và trạng thái gửi lỗi; gửi lại theo FE-07 sau khi kiểm tra link còn hiệu lực. Lỗi email không xóa tài khoản đã tạo.
7. Link thiết lập hết hạn/không hợp lệ: chưa cấp quyền vận hành; chuẩn bị link mới theo Firebase và quy trình gửi lại có quyền. Không gửi lại link đã hết hạn hoặc tự đổi mật khẩu nhân viên.
8. Nhân viên chưa đặt mật khẩu/chưa verified, tài khoản bị khóa hoặc phạm vi không còn hợp lệ: chưa cho truy cập nội bộ; hoàn tất thiết lập không vượt trạng thái khóa hiện tại.

## Sửa thông tin tài khoản nội bộ

### Luồng thành công

1. Admin chọn tài khoản Staff/Manager được phép quản lý, mở **Sửa thông tin** và chỉnh họ tên/số điện thoại.
2. Frontend gửi các trường này; Backend kiểm tra quyền, tài khoản mục tiêu và dữ liệu. Không chấp nhận thay UID, email đăng nhập, vai trò, cửa hàng hoặc trạng thái trong yêu cầu sửa thông tin.
3. Backend lưu thông tin và ghi lịch sử tối thiểu cùng giao dịch; Frontend hiển thị kết quả sau phản hồi thành công.
4. Muốn đổi email, chủ tài khoản đi luồng Đổi email; muốn đổi vai trò/phạm vi hoặc khóa, Admin dùng thao tác riêng. Không tự đổi quyền vì sửa hồ sơ.

### Nhánh lỗi

1. Sai quyền, tài khoản không tồn tại/không thuộc phạm vi nội bộ được sửa, hoặc dữ liệu không hợp lệ: từ chối và giữ dữ liệu trước đó.
2. Lỗi lưu: rollback thay đổi chưa hoàn tất. Mất phản hồi: tải lại chi tiết xác định dữ liệu đã lưu trước khi thử lại.
3. Admin hủy form: bỏ thay đổi chưa lưu; không phát sinh thao tác gán vai trò hoặc thông báo khóa/mở khóa.

## Gán hoặc đổi vai trò và phạm vi cửa hàng

### Luồng thành công

1. Admin chọn tài khoản nội bộ Staff/Manager và mở thao tác **Gán vai trò/phạm vi**. Backend trả vai trò và phạm vi hiện hành.
2. Admin chọn một vai trò Staff hoặc Manager, không gán đồng thời nhiều vai trò. Staff phải có một cửa hàng; Manager phải có danh sách cửa hàng phụ trách hợp lệ.
3. Frontend hiển thị vai trò/phạm vi trước và sau để Admin kiểm tra, xác nhận. Thay cửa hàng cũng là thay đổi quyền cần xác nhận, không sửa lẫn trong form thông tin cá nhân.
4. Backend kiểm tra lại quyền Admin, tài khoản mục tiêu, vai trò/phạm vi và trạng thái hiện hành trước khi lưu; không tin danh sách quyền Frontend gửi.
5. Backend lưu vai trò/phạm vi mới và lịch sử gồm người thực hiện, thời điểm, trước/sau. Method nghiệp vụ gọi method chuẩn bị thư `ACCOUNT_ROLE_CHANGED`; method tự tập hợp dữ liệu và lưu yêu cầu cùng giao dịch Backend, job gửi sau commit theo FE-07.
6. Backend trả kết quả; các yêu cầu nghiệp vụ tiếp theo kiểm tra quyền từ Backend với vai trò/phạm vi mới. Phiên đang đăng nhập không được giữ quyền cũ chỉ vì giao diện hoặc token cũ còn tên vai trò đó.
7. Gán vai trò không đổi UID, mật khẩu hoặc trạng thái khóa. Đổi vai trò cho tài khoản đang khóa/chờ thiết lập không tự kích hoạt tài khoản.

### Nhánh lỗi

1. Cố gán Admin, tạo vai trò mới, gán nhiều vai trò hoặc thao tác trên tài khoản Admin/Member qua luồng nội bộ: từ chối. Chưa có chuyển Member thành nhân viên qua màn hình này.
2. Staff thiếu/có nhiều cửa hàng, Manager có danh sách rỗng hoặc cửa hàng không hợp lệ: báo lỗi và giữ phạm vi cũ.
3. Không có quyền, hủy xác nhận hoặc lưu lỗi: không thay đổi vai trò/phạm vi. Nếu dữ liệu đã thay đổi so với lúc mở form thì yêu cầu xem lại trước khi xác nhận tiếp.
4. SMTP lỗi: giữ thay đổi đã commit, xử lý gửi lại riêng theo FE-07. Nếu mất phản hồi thao tác, tải lại dữ liệu trước khi gửi lại; cùng thay đổi không tạo lặp yêu cầu email.

## Khóa và mở khóa tài khoản

### Luồng thành công

1. Admin chọn tài khoản Member/Staff/Manager được phép quản lý; Backend trả trạng thái hiện hành. Không cho tác động tài khoản Admin trong luồng này.
2. Admin chọn **Khóa** hoặc **Mở khóa**, nhập lý do và xác nhận tác động. Frontend không tự thay trạng thái chỉ khi bấm chọn.
3. Backend kiểm tra quyền, mục tiêu và chuyển trạng thái hợp lệ. Nếu tài khoản đã ở trạng thái yêu cầu, trả hiện trạng, không tạo thêm thay đổi hoặc email giống nhau.
4. Backend lưu quyết định khóa/mở khóa và lịch sử người thực hiện, lý do, thời điểm. Method nghiệp vụ gọi method chuẩn bị email `ACCOUNT_LOCKED` hoặc `ACCOUNT_UNLOCKED`, tự tập hợp dữ liệu được phép công khai và lưu yêu cầu cùng giao dịch; job gửi sau commit.
5. Khi khóa, Backend từ chối yêu cầu bảo vệ tiếp theo dựa vào trạng thái nội bộ và thực hiện thu hồi các phiên cũ qua cơ chế Firebase/phiên đang sử dụng. Không chờ gửi email hoặc thu hồi Firebase thành công mới áp dụng việc chặn phía Backend.
6. Mở khóa chỉ gỡ hạn chế do quyết định khóa: vẫn giữ điều kiện xác minh/thiết lập còn thiếu và quyền đã gán. Các phiên đã thu hồi không tự có hiệu lực lại; người dùng cần đăng nhập lại.
7. Backend trả kết quả đã lưu; Frontend hiển thị đúng trạng thái. Khóa/mở khóa không tự hủy/khôi phục đơn, booking, khoản thanh toán hoặc giao dịch đã hết hiệu lực.

### Nhánh lỗi

1. Thiếu lý do, không có quyền, mục tiêu không tồn tại, là Admin hoặc chuyển trạng thái không hợp lệ: từ chối trước khi lưu, giữ trạng thái đã chấp nhận.
2. Lỗi ghi trạng thái/lịch sử/yêu cầu thư trước commit: rollback giao dịch Backend; không gửi thư báo đã khóa/mở khóa khi chưa lưu được.
3. Đã khóa Backend nhưng thu hồi phiên Firebase lỗi: giữ trạng thái khóa và chặn yêu cầu tiếp theo, ghi bước thu hồi chưa xong để xử lý lại; không báo đã thu hồi hết khi chưa có bằng chứng.
4. Mở khóa khi việc thu hồi phiên của quyết định khóa trước còn chưa rõ: xác định và hoàn tất bước còn thiếu trước khi báo phục hồi sử dụng bình thường, tránh bước thu hồi cũ vô hiệu hóa phiên mới.
5. Mất phản hồi thao tác: kiểm tra trạng thái đã lưu trước khi thử lại. SMTP lỗi sau commit không đảo quyết định khóa/mở khóa; email gửi lại theo FE-07.

## Xem vai trò và quyền cố định

1. Admin mở phần **Vai trò và quyền**; Backend kiểm tra quyền xem và trả các vai trò/quyền được định nghĩa sẵn.
2. Frontend hiển thị chức năng và phạm vi của từng vai trò để Admin hiểu trước khi gán. Chưa cho tạo vai trò, thêm quyền hoặc sửa ma trận quyền trên giao diện.
3. Khi thực hiện nghiệp vụ, Backend kiểm tra đồng thời danh tính, trạng thái tài khoản, vai trò, phạm vi cửa hàng và điều kiện của hành động. Staff không truy cập nghiệp vụ cửa hàng khác; Manager không vượt danh sách phụ trách.
4. Admin có quyền kỹ thuật/quản trị tài khoản theo phạm vi được giao, không tự có quyền xem doanh thu, duyệt giá hoặc thay đổi kết quả thanh toán. Quyền nghiệp vụ cụ thể giữ theo feature/report, không do người dùng nhập khi tạo tài khoản.
5. Không có quyền xem thì từ chối; lỗi tải danh mục quyền phải hiển thị lỗi, không coi danh sách rỗng là không còn quyền. Vai trò không được nhận diện không tự được cấp quyền mặc định.
6. Đây là phạm vi đã chốt hẹp hơn màn hình Role/Permission Configuration trong report: chỉ xem quyền cố định và gán Staff/Manager, không chỉnh chính sách quyền động. Chưa sửa report hoặc cấu hình RBAC trong code.

## Liên hệ với FE-07 và giới hạn nghiên cứu

1. Tạo tài khoản, khóa, mở khóa và đổi vai trò dùng các mã mẫu đã có: `ACCOUNT_INVITATION`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`, `ACCOUNT_ROLE_CHANGED`. FE-01 xác định lúc cần thư/người nhận/dữ liệu; method chuẩn bị lưu yêu cầu, job FE-07 chỉ dựng và gửi.
2. Chỉ chuẩn bị email khi sự kiện đã đủ điều kiện và được ghi nhận cùng giao dịch Backend tương ứng. Firebase không tham gia giao dịch DB; thao tác tạo danh tính, tạo link hoặc thu hồi phiên có thể thất bại riêng và phải lưu tiến độ/khôi phục theo từng bước.
3. Bốn bản thư hiện có trong FE-07 giữ nguyên, kể cả nhãn dự thảo; phần FE-01 này là căn cứ bổ sung điều kiện sử dụng để đối chiếu/đồng bộ khi triển khai sau, chưa tự kích hoạt chúng trong code hoặc sửa FE-07.
4. Xác minh, đặt lại mật khẩu và xác minh email mới dùng cơ chế Firebase đã chấp nhận; không tạo thêm thư SMTP thay thế việc xác minh hoặc tự đánh dấu verified từ thông báo gửi.
5. Tài liệu chỉ hoàn thiện nghiên cứu luồng; không tự sửa spec 001-account-management, schema, API, ma trận quyền hoặc hiện trạng triển khai.

## Tình huống kiểm tra các luồng bổ sung

1. Đăng xuất chỉ thiết bị hiện tại; phiên server chưa kết thúc không được báo hoàn tất toàn bộ; thiết bị khác không bị thu hồi do đăng xuất thông thường.
2. Hồ sơ chưa verified, token hết hạn/thu hồi, tài khoản bị khóa hoặc yêu cầu hồ sơ người khác đều bị từ chối; hồ sơ chưa có trả trường trống, lưu lỗi giữ dữ liệu cũ.
3. Sửa hồ sơ không đổi UID/email/vai trò/trạng thái; số điện thoại liên hệ không tự trở thành danh tính đã xác minh.
4. Đổi email chưa xác minh giữ email cũ; email trùng/link hết hạn/tái xác thực lỗi được xử lý đúng; Firebase đã đổi nhưng Backend lỗi có thể đồng bộ lại theo UID mà không ghi đè bằng token cũ.
5. Tạo nội bộ gửi lặp hoặc lỗi riêng Firebase/Backend không tạo trùng/cấp nhầm quyền; nhân viên chưa đặt mật khẩu/chưa verified hoặc tài khoản khóa chưa được dùng nghiệp vụ nội bộ.
6. Staff sai cửa hàng, Manager ngoài phạm vi, vai trò do Frontend tự khai hoặc phiên giữ quyền cũ đều không vượt kiểm tra Backend.
7. Admin cố tạo/gán Admin, sửa vai trò hoặc khóa/mở khóa Admin bị từ chối; các thao tác tạo tài khoản không tạo hộ Member hoặc tự chuyển Member thành Staff.
8. Khóa chặn ngay ở kiểm tra Backend dù thu hồi Firebase lỗi; mở khóa không phục hồi phiên/giao dịch cũ hoặc bỏ qua thiết lập còn thiếu.
9. Thay đổi tài khoản và yêu cầu thư commit cùng DB; lỗi SMTP không đảo kết quả; gửi lại thư không chạy lại tạo tài khoản, gán vai trò hoặc khóa/mở khóa.

