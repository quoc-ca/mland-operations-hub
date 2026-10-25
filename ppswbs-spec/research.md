# Research — Phân loại luồng theo Features

Phân loại theo 10 major features trong Report 1. Đây là cách tổ chức tài liệu, không phải xác nhận các tính năng đã được triển khai. Mỗi feature ghi rõ nội dung đã mô tả, đề xuất và phần chưa chốt.

Mỗi luồng được mô tả tại một nơi. FE-01 và FE-07 có research riêng trong thư mục features tương ứng; các phần chưa tách vẫn giữ ở đây. Các điểm dùng chung được dẫn chiếu giữa feature; thanh toán không được tạo thành feature thứ 11.

<a id="fe-01"></a>

# FE-01 — Account Management

Nội dung nghiên cứu đã chuyển sang [research riêng của FE-01](features/001-account-management/research.md). Các tài liệu hồ sơ Member có sẵn được giữ trong thư mục nhóm này; chưa mở rộng spec/plan/tasks sang toàn bộ FE-01.

<a id="fe-02"></a>

# FE-02 — Product Management

Feature này gồm quản lí nhẫn bán sẵn, Manager phê duyệt sản phẩm/giá và tra cứu catalogue công khai. Luồng sản phẩm mới là **Staff tạo bản nháp → gửi Manager duyệt → áp dụng giá đã duyệt → công khai sản phẩm**. Mua ngay, giỏ hàng, checkout, tạo đơn, thanh toán và giao nhận được mô tả tại [FE-08](#fe-08); voucher và điểm thưởng tại [FE-06](#fe-06). Các nội dung **Đề xuất** và tham số **chưa chốt** được ghi tại luồng liên quan.

Các quyết định bổ sung đã chốt ngày 2026-10-24: mã vai trò MEMBER/STAFF/MANAGER/ADMIN, Manager không tự đồng nhất OWNER; Admin quản lí tài khoản, vai trò, trạng thái khóa tài khoản, RBAC, tham số vận hành và credential tích hợp đã được duyệt (gồm cấu hình Cloudinary), không quản lí sản phẩm, danh mục, giá hay quyết định duyệt; khách đăng ký không tự nhận quyền. Danh mục mới mặc định hoạt động; size lúc tạo mặc định được phép bán nhưng chỉ mua được khi sản phẩm đủ điều kiện công khai. Lý do từ chối duyệt bắt buộc, tối đa 2.000 ký tự. Người dùng đã đồng ý dùng chung cơ chế xác thực theo contract của FE-01 (Account Management 001): API dùng bearer đã xác minh, kiểm tra thu hồi/trạng thái; FE-02 kiểm tra thêm quyền nội bộ trên từng thao tác, giữ luồng Member. MFA đặc quyền và recovery do identity/FE-01 sở hữu; phương thức cụ thể chưa được chọn từ FE-02.

## Quản lí catalogue sản phẩm

### Điều kiện và quy tắc chung

1. Staff và Manager có tài khoản hoạt động và quyền quản lí sản phẩm tương ứng được tạo/sửa sản phẩm, ảnh, biến thể, đề xuất giá, quản lí trạng thái công khai và xóa sản phẩm. Các bước bên dưới mô tả Staff thực hiện quản lí catalogue; Manager có quyền tương ứng cũng được thực hiện. Riêng quyết định duyệt sản phẩm và giá thuộc Manager, Staff không được tự duyệt. Manager được tự duyệt sản phẩm và đề xuất giá do chính mình tạo, vẫn phải xác nhận quyết định và ghi audit. Backend kiểm tra vai trò/quyền tại từng thao tác; Guest/Member tra cứu catalogue; luồng mua hàng tham chiếu [FE-08](#fe-08).
2. Sản phẩm mới ở trạng thái **Chưa công khai**. Staff nhập **giá đề xuất**, không lưu trực tiếp thành giá bán. Trạng thái duyệt hồ sơ, trạng thái duyệt/áp dụng giá và trạng thái công khai được theo dõi riêng. Manager duyệt không tự áp dụng giá hay công khai; người có quyền phải áp dụng đúng giá đã duyệt rồi chủ động công khai.
3. Mỗi biến thể tương ứng với **một size nhẫn** của sản phẩm, có SKU tự sinh cố định, giá và số lượng cho phép bán riêng; không có lựa chọn khắc. Size nhẫn là số nguyên từ **5 đến 29**, chọn theo bảng size bên dưới; không nhận size ngoài khoảng hoặc size tự nhập. Khi tạo sản phẩm, người dùng chọn các size thực tế có bán, không bắt buộc đủ 25 size; mỗi size chỉ có một biến thể trong cùng sản phẩm. Sau khi lưu sản phẩm, không có thao tác thêm size hoặc đổi size đã cấu hình; chỉ cập nhật giá, số lượng và trạng thái bán của biến thể đã có. Khách chỉ chọn size đã cấu hình.
4. Mỗi biến thể dùng một số lượng còn cho phép bán **chung toàn hệ thống**, không chia theo chi nhánh. Staff cập nhật thủ công; **số lượng khả dụng** bằng lượng bán chung còn lại trừ phần đang tạm giữ hợp lệ. **Có thể mua** khi đồng thời: số lượng khả dụng lớn hơn 0, biến thể đang bán, sản phẩm chưa bị xóa và đang công khai, danh mục hoạt động, các điều kiện công khai và giá đã duyệt/áp dụng vẫn hợp lệ. Biến thể ngừng bán không thể mua dù còn số lượng; sản phẩm có thể mua khi có ít nhất một biến thể có thể mua. Staff tự điều phối nơi chuẩn bị hàng tại FE-08. Không làm quản lí kho, nhập/xuất, kiểm kê hay điều chuyển.
5. Backend ghi người thực hiện, thay đổi, thời điểm và kết quả của thao tác quản trị. Cập nhật thất bại không được lưu một phần; đơn và quyền giữ hợp lệ đã chấp nhận được bảo toàn theo điều khoản gốc.
6. Giới hạn tên/mô tả và ảnh tại luồng tạo/quản lí ảnh áp dụng cho cả tạo lẫn sửa. Sửa thông tin, ảnh hoặc giá đề xuất của sản phẩm mới làm mất hiệu lực phiên bản duyệt theo luồng Sửa thông tin sản phẩm; cập nhật số lượng không tự thay đổi giá đã duyệt. Các lượt đọc công khai chỉ trả giá đang áp dụng đã được Manager duyệt, không lộ bản nháp, giá đề xuất hay giá đã duyệt nhưng chưa áp dụng.
7. Sản phẩm đã xóa bị loại khỏi danh sách quản trị thông thường và mọi lượt đọc công khai; Backend từ chối sửa, gửi/duyệt hồ sơ, áp dụng giá, công khai hoặc mở bán lại dù truy cập bằng mã hay trang cũ. Các thao tác xử lí giao dịch đã chấp nhận và tra cứu lịch sử theo quyền vẫn được bảo toàn.

### Xem danh sách và chi tiết quản trị — Luồng thành công

1. Staff có quyền mở **Quản lí sản phẩm**. Backend kiểm tra tài khoản và quyền trước khi trả dữ liệu nội bộ.
2. Frontend hiển thị mã tham chiếu sản phẩm, tên, danh mục, ảnh và trạng thái công khai; phân biệt trạng thái duyệt hồ sơ, mã đề xuất, giá đề xuất, giá đã duyệt và giá bán đang áp dụng. Sản phẩm mới chưa áp dụng giá không được hiển thị giá đề xuất như giá bán. Chi tiết biến thể hiển thị trạng thái đang bán/ngừng bán và SKU do hệ thống tự sinh; SKU là trường chỉ đọc.
3. Staff tìm theo mã tham chiếu/tên sản phẩm hoặc SKU biến thể; lọc theo danh mục và trạng thái công khai. Kết quả tìm SKU chỉ rõ biến thể khớp trong sản phẩm. Mỗi trang có 20 bản ghi; từ khóa sau trim tối đa 200 ký tự. Tiêu chí lọc bổ sung ngoài danh mục/trạng thái chưa nằm trong phạm vi.
4. Người dùng mở một sản phẩm để xem mô tả, danh mục, thứ tự ảnh, biến thể, giá và số lượng cho phép bán. Frontend chỉ hiển thị các thao tác phù hợp với quyền hiện tại.
5. Staff chọn tạo mới, sửa thông tin, quản lí ảnh/biến thể, đề xuất giá, gửi duyệt, áp dụng giá đã duyệt, công khai/ngừng công khai hoặc xóa theo quyền. Manager có quyền mở hồ sơ chờ duyệt và quyết định theo luồng phê duyệt bên dưới.

### Danh sách và chi tiết quản trị — Nhánh lỗi

1. Không có quyền hoặc tài khoản không còn được phép sử dụng: từ chối truy cập, không trả bản nháp hay thông tin giá nội bộ.
2. Không có sản phẩm hoặc không có kết quả theo bộ lọc: hiển thị trạng thái rỗng và cho sửa/xóa bộ lọc; không coi đây là lỗi hệ thống.
3. Lỗi tải hoặc sản phẩm không còn tồn tại: thông báo phù hợp, cho tải lại/quay về danh sách; không hiển thị dữ liệu cũ như trạng thái hiện tại đã xác nhận.

### Tạo sản phẩm — Luồng thành công

1. Staff có quyền mở danh sách sản phẩm và chọn **Tạo sản phẩm**.
2. Người dùng nhập tên dài 1–200 ký tự, mô tả dài 1–5.000 ký tự sau khi loại bỏ khoảng trắng đầu/cuối và chọn một danh mục đang hoạt động. Frontend kiểm tra trường bắt buộc; Backend kiểm tra lại dữ liệu khi lưu.
3. Người dùng chọn ít nhất một size từ bảng 5–29, không chọn trùng size; mỗi size tạo thành một biến thể. Người dùng nhập giá đề xuất và số lượng cho phép bán chung ban đầu cho từng biến thể. Giá đề xuất là số nguyên VND dương; số lượng là số nguyên không âm. Backend tự sinh SKU duy nhất, cố định cho từng biến thể theo luồng SKU; người dùng không nhập hoặc sửa mã.
4. Người dùng có thể tải ảnh, nhập văn bản thay thế và sắp xếp thứ tự hiển thị theo luồng ảnh bên dưới. Bản nháp có thể chưa có ảnh; sản phẩm phải có ảnh sử dụng được trước khi công khai.
5. Frontend kiểm tra dữ liệu rồi gửi yêu cầu lưu; Backend kiểm tra quyền, danh mục, size hợp lệ và không trùng trong cùng sản phẩm, dữ liệu giá/số lượng và các ảnh đã tải hợp lệ.
6. Backend lưu sản phẩm và các dữ liệu liên quan ở trạng thái **Chưa công khai**, hồ sơ duyệt ở trạng thái **Nháp**, ghi nhận audit rồi trả kết quả. Giá Staff nhập chỉ là giá đề xuất cho từng biến thể, chưa được áp dụng để bán.
7. Frontend mở chi tiết sản phẩm vừa tạo. Staff kiểm tra thông tin, ảnh, biến thể và giá đề xuất rồi chọn **Gửi duyệt** theo luồng bên dưới; chưa được công khai trước khi Manager duyệt và giá đã duyệt được áp dụng.
8. Mỗi sản phẩm có từ 1 đến 25 biến thể tương ứng các size đã chọn. Giới hạn giá/số lượng bổ sung ngoài các điều kiện trên **chưa chốt**.

### Tạo sản phẩm — Nhánh lỗi

1. Thiếu tên/mô tả, danh mục không hoạt động hoặc dữ liệu không hợp lệ: hiển thị lỗi tại trường tương ứng, giữ nội dung người dùng đã nhập để sửa.
2. Chưa chọn size, size không phải số nguyên từ 5–29, trùng size, giá không hợp lệ hoặc số lượng âm/không nguyên: chỉ rõ dòng lỗi, chưa tạo sản phẩm một phần. Dữ liệu yêu cầu lựa chọn khắc không được chấp nhận.
3. Ảnh tải lên lỗi hoặc tham chiếu ảnh chưa hợp lệ: yêu cầu sửa/thử lại hoặc chủ động bỏ ảnh lỗi trước khi lưu bản nháp; không lưu liên kết ảnh hỏng.
4. Mất phản hồi sau khi gửi yêu cầu: kiểm tra kết quả đã lưu trước khi tạo lại; không tạo thêm sản phẩm chỉ vì chưa nhận được thông báo thành công.
5. Lỗi lưu hoặc audit: thông báo chưa hoàn tất, không ghi nhận sản phẩm ở trạng thái được tạo thành công một phần.

### Sửa thông tin sản phẩm — Luồng thành công

1. Staff có quyền mở chi tiết và chọn **Sửa**. Backend trả thông tin hiện tại để người dùng đối chiếu trước khi thay đổi.
2. Người dùng sửa các trường được phép như tên, mô tả, danh mục; thay đổi ảnh, biến thể hoặc giá đi theo các quy tắc riêng bên dưới.
3. Backend kiểm tra quyền, phiên bản dữ liệu và trạng thái hiện tại trước khi lưu. Nếu sản phẩm đang công khai, bản cập nhật phải tiếp tục đáp ứng điều kiện công khai. Giỏ hàng chưa tạo đơn dùng thông tin mới khi tải lại và được kiểm tra lại ở checkout; giá hoặc điều kiện thay đổi phải được khách xem và xác nhận trước khi đặt.
4. Backend lưu các thay đổi hợp lệ và audit; không sửa thông tin đã chụp của đơn hoặc điều khoản của quyền giữ đã được chấp nhận.
5. Frontend hiển thị kết quả đã lưu; nếu sửa giá thì hiển thị đề xuất riêng, giữ nguyên giá bán đang áp dụng đến khi đề xuất được Manager duyệt và được áp dụng. Lưu thông tin/đề xuất không thay cho thao tác công khai/ngừng công khai. Với hồ sơ sản phẩm mới chưa từng công khai, sửa nội dung/ảnh hoặc giá đề xuất đã gửi hay đã được duyệt làm phiên bản duyệt cũ không còn hiệu lực; phải gửi Manager duyệt lại trước lần công khai đầu tiên. Với sản phẩm đã từng công khai, sửa tên, mô tả, ảnh hoặc danh mục được lưu trực tiếp nếu hợp lệ, không phải gửi duyệt lại nội dung; quy tắc này vẫn áp dụng khi sản phẩm đang tạm ẩn. Nếu sản phẩm đang công khai, cập nhật phải duy trì các điều kiện công khai. Thay đổi giá luôn đi qua quy trình duyệt giá; không bổ sung hoặc đổi size của sản phẩm đã lưu.

### Sửa sản phẩm — Nhánh lỗi

1. Người khác đã thay đổi dữ liệu: từ chối ghi đè bản cũ, tải lại dữ liệu mới để người dùng xem và xác nhận lại.
2. Danh mục đã ngừng hoạt động hoặc thay đổi làm sản phẩm công khai mất điều kiện hợp lệ: chỉ rõ lỗi, giữ trạng thái cũ; người dùng sửa dữ liệu hoặc chủ động ngừng công khai trước khi tiếp tục.
3. Không có quyền, sản phẩm không còn tồn tại, lưu/audit lỗi: từ chối an toàn, không lưu một phần và không báo đã cập nhật.

### Quản lí ảnh sản phẩm — Luồng thành công

1. Người dùng có quyền chọn thêm, thay thế, đổi thứ tự hoặc gỡ ảnh trong biểu mẫu tạo/sửa sản phẩm.
2. Frontend hiển thị ảnh xem trước, văn bản thay thế và thứ tự hiển thị; chấp nhận tối đa 10 ảnh JPEG/PNG/WebP, mỗi ảnh không quá 5.000.000 bytes, văn bản thay thế dài 1–200 ký tự. Backend kiểm tra lại các giới hạn này khi lưu.
3. Backend kiểm tra ảnh, quyền truy cập và dữ liệu mô tả; lưu tệp qua dịch vụ lưu trữ ảnh được cấu hình, rồi gắn tham chiếu hợp lệ vào sản phẩm.
4. Backend kiểm tra thứ tự ảnh không trùng. Với sản phẩm công khai, sau thay đổi phải còn ảnh sử dụng được; muốn gỡ toàn bộ ảnh thì người dùng phải ngừng công khai trước.
5. Backend lưu danh sách ảnh và audit rồi trả kết quả. Gỡ tham chiếu ảnh catalogue không mặc định cho phép xóa tệp dùng chung hoặc ảnh còn phục vụ lịch sử giao dịch.

### Quản lí ảnh — Nhánh lỗi

1. Sai loại tệp, vượt giới hạn đã cấu hình, thiếu mô tả ảnh hoặc trùng thứ tự: hiển thị lỗi tương ứng, chưa lưu cập nhật.
2. Dịch vụ lưu trữ lỗi hoặc tệp chưa tải xong: giữ danh sách ảnh đang có, cho thử lại; không thay ảnh hợp lệ bằng liên kết lỗi.
3. Thao tác làm mất ảnh cuối cùng của sản phẩm công khai: từ chối cập nhật và hướng dẫn ngừng công khai hoặc thêm ảnh thay thế trước.

### Lưu trữ và dọn ảnh sản phẩm

1. Ảnh được lưu qua **Cloudinary**, nhà cung cấp Cloud Storage Service được người dùng xác nhận ngày 2026-10-24. Ảnh nháp và ảnh lịch sử vẫn phải được bảo vệ theo quyền truy cập; chọn provider không làm thay đổi điều kiện công khai hoặc chính sách giữ/dọn ảnh.
2. Ảnh tải lên chưa từng gắn vào sản phẩm đủ 24 giờ được dọn nếu không còn sử dụng. Ảnh đã gỡ đủ 30 ngày chỉ được dọn nếu không còn sản phẩm, hồ sơ duyệt, đơn hoặc lịch sử tham chiếu.
3. Ảnh phục vụ lịch sử được giữ theo bản ghi liên quan; xóa mềm sản phẩm không tự xóa ảnh này. Dọn ảnh phải kiểm tra lại tham chiếu để không tranh chấp với thao tác gắn ảnh.
4. Audit nghiệp vụ giữ 5 năm theo Report 3; bản ghi kết quả thao tác tối thiểu để xử lí gửi lặp giữ 5 năm, không lưu toàn bộ request hoặc token.

### Bảng size nhẫn tham chiếu

Size từ 5–29 và các số đo dưới đây được ghi theo ảnh bảng size người dùng cung cấp, giữ nguyên số liệu nguồn. Đường kính và chu vi dùng để tham khảo chọn size; hệ thống lưu/chọn mã size nguyên, không tự suy ra biến thể mới từ số đo.

| Size | Đường kính (mm) | Chu vi (mm) |
| --- | --- | --- |
| 5 | 14.7 | 45 |
| 6 | 14.8 | 46 |
| 7 | 15 | 47–48 |
| 8 | 15.3 | 49 |
| 9 | 15.8 | 49.6 |
| 10 | 15.9 | 51 |
| 11 | 16.1 | 52 |
| 12 | 16.5 | 53 |
| 13 | 16.7 | 54 |
| 14 | 16.9 | 55 |
| 15 | 17.3 | 56 |
| 16 | 17.9 | 57 |
| 17 | 18 | 58 |
| 18 | 18.2 | 59 |
| 19 | 19.1 | 60 |
| 20 | 19.4 | 62 |
| 21 | 19.8 | 62.2 |
| 22 | 20.2 | 63 |
| 23 | 20.3 | 63.8 |
| 24 | 20.6 | 65 |
| 25 | 21 | 66 |
| 26 | 21.4 | 67 |
| 27 | 21.6 | 67.9 |
| 28 | 22 | 68.8 |
| 29 | 22.3 | 70.1 |

### Quản lí biến thể và số lượng cho phép bán — Luồng thành công

1. Staff có quyền mở danh sách các size đã cấu hình khi tạo sản phẩm để cập nhật số lượng, đề xuất giá hoặc đổi trạng thái đang bán/ngừng bán; không có thao tác thêm size hay lựa chọn khắc.
2. Backend giữ nguyên danh sách size, định danh biến thể và SKU từ khi tạo. Không cho đổi size của biến thể hoặc bổ sung biến thể size mới vào sản phẩm đã lưu, kể cả khi chưa có giao dịch.
3. Staff nhập số lượng còn cho phép bán chung của từng biến thể theo thông tin shop xác nhận bên ngoài hệ thống. Backend chỉ chấp nhận số nguyên không âm và không thấp hơn phần đang giữ hợp lệ. Khi đơn được chốt, Backend trừ lượng đã mua khỏi số lượng chung đúng một lần; hết hạn chưa thanh toán chỉ giải phóng phần giữ.
4. Staff nhập/sửa giá đề xuất theo luồng Đề xuất, phê duyệt và áp dụng giá sản phẩm. Backend kiểm tra giá hợp lệ và dữ liệu mới nhất trước khi lưu đề xuất; không đổi giá bán đang áp dụng, giá của đơn hoặc quyền giữ đã được chấp nhận.
5. Backend kiểm tra tính hợp lệ của sản phẩm sau thay đổi, lưu dữ liệu và audit rồi hiển thị kết quả. Các biến thể đã tạo phải hoàn tất bước Manager duyệt giá và áp dụng giá trước lần công khai đầu tiên; cập nhật số lượng không yêu cầu ẩn sản phẩm hay duyệt lại giá.

### Biến thể và số lượng cho phép bán — Nhánh lỗi

1. Yêu cầu thêm size, đổi size, thêm lựa chọn khắc hoặc dữ liệu không hợp lệ: từ chối, giữ nguyên danh sách biến thể đã lưu; không chỉ dựa vào việc ẩn thao tác trên giao diện.
2. Số lượng nhập thấp hơn lượng đang giữ, hoặc lượng giữ thay đổi khi đang sửa: Backend trả tình trạng mới và yêu cầu kiểm tra lại; không hủy giữ chỗ để ép lưu số lượng.
3. Thay đổi khiến sản phẩm đang công khai không đủ điều kiện: từ chối bản cập nhật, nêu điều kiện cần sửa. Việc ngừng công khai không mở quyền thêm hoặc đổi size; đề xuất giá mới không làm mất giá đang áp dụng hợp lệ trong thời gian chờ duyệt.
4. Lỗi lưu/audit: giữ dữ liệu trước đó, chưa báo cập nhật thành công và cho kiểm tra lại kết quả.

### Cấp và quản lí SKU biến thể — Luồng thành công

1. Khi tạo sản phẩm, Backend cấp SKU cho từng size đã chọn. SKU là mã hàng để phân biệt chính xác một sản phẩm ở một size, giúp Staff tra cứu và đối chiếu đúng biến thể trong đơn hàng; không phải mã riêng cho từng chiếc nhẫn vật lí.
2. Khi tạo biến thể, Backend tự sinh SKU duy nhất và cố định theo định dạng **`SP{mã sản phẩm}-S{size}`**; Staff không nhập hoặc sửa mã. Phần mã sản phẩm là số duy nhất do hệ thống cấp một lần cho sản phẩm, thêm số 0 bên trái để có tối thiểu 6 chữ số; nếu dài hơn 6 chữ số thì giữ nguyên toàn bộ, không cắt mã. Phần size luôn có 2 chữ số từ `05` đến `29`. Ví dụ sản phẩm có mã số `123`, size 5 có SKU `SP000123-S05`, size 12 có SKU `SP000123-S12`. Dùng chữ `SP`, `S` viết hoa và dấu gạch nối như mẫu; không lấy tên hoặc danh mục sản phẩm để sinh mã.
3. Backend kiểm tra SKU không trùng với biến thể khác trên hệ thống. Hai size của cùng mẫu nhẫn có SKU khác nhau; hai sản phẩm khác nhau cùng size cũng có SKU khác nhau. Nhiều chiếc của cùng sản phẩm và cùng size dùng chung một SKU, phân biệt bằng số lượng; việc trùng tên sản phẩm không quyết định SKU.
4. Sau khi lưu thành công, Frontend hiển thị SKU chỉ đọc trong chi tiết quản trị và cho tìm kiếm theo SKU. Mã giữ nguyên từ khi cấp, kể cả khi biến thể chưa có giao dịch.
5. Đổi tên sản phẩm, đổi danh mục, đổi giá hoặc ngừng bán không tự sinh mã sản phẩm hay SKU mới. Mã sản phẩm và SKU không được tái cấp cho sản phẩm/biến thể khác khi ngừng bán hoặc xóa mềm.

### SKU biến thể — Nhánh lỗi

1. Hệ thống chưa cấp được mã hợp lệ, mã sai định dạng/không khớp sản phẩm và size, hoặc phát hiện mã trùng: không lưu biến thể với SKU thiếu/sai/trùng; thông báo chưa hoàn tất để thử lại, không yêu cầu Staff nhập mã thay thế.
2. Yêu cầu cố sửa SKU đã được cấp hoặc gán SKU của biến thể khác: Backend từ chối, kể cả khi chưa có giao dịch; không chỉ dựa vào trường chỉ đọc trên giao diện.
3. Cấp mã hoặc lưu dữ liệu lỗi: không tạo biến thể thiếu mã hay tạo thêm biến thể khi thử lại; kiểm tra kết quả đã ghi nhận trước khi gửi lại.

## Gửi và phê duyệt sản phẩm mới

### Staff gửi sản phẩm — Luồng thành công

1. Staff mở sản phẩm chưa công khai, hoàn tất thông tin, ít nhất một ảnh sử dụng được, các biến thể, số lượng và giá đề xuất rồi chọn **Gửi duyệt**.
2. Backend kiểm tra quyền, sản phẩm chưa bị xóa, phiên bản mới nhất, danh mục hoạt động và dữ liệu hồ sơ. Backend lưu phiên bản hồ sơ cùng các đề xuất giá đúng sản phẩm/biến thể, chuyển sang **Chờ duyệt** và ghi audit.
3. Frontend hiển thị mã hồ sơ, phiên bản và trạng thái chờ Manager xử lí. Sản phẩm vẫn chưa công khai; giá đề xuất chưa phải giá bán.
4. Staff có thể xem tiến độ. Nếu cần sửa nội dung đã gửi, hệ thống làm phiên bản chờ duyệt cũ mất hiệu lực, đưa hồ sơ về **Nháp** và yêu cầu gửi lại sau khi sửa; không cho Manager duyệt nhầm dữ liệu cũ.

### Manager duyệt sản phẩm — Luồng thành công

1. Manager có tài khoản hoạt động và quyền phê duyệt mở hồ sơ **Chờ duyệt**, xem đúng phiên bản thông tin, ảnh, danh mục, biến thể, số lượng và giá đề xuất. Hồ sơ có thể do Staff hoặc chính Manager đó tạo; không yêu cầu một Manager khác duyệt.
2. Manager kiểm tra hồ sơ và quyết định giá từng biến thể theo luồng phê duyệt giá bên dưới. Manager có thể chấp nhận hoặc điều chỉnh giá đề xuất trước khi xác nhận; AI nếu được dùng tại FE-04 chỉ hỗ trợ quyết định.
3. Manager chọn **Duyệt**. Backend kiểm tra lại quyền, phiên bản hồ sơ, trạng thái sản phẩm và tính hợp lệ của các giá cuối cùng; ghi nhận hồ sơ **Đã duyệt**, giá đã duyệt từng biến thể, người duyệt, thời điểm và audit.
4. Frontend hiển thị kết quả duyệt. Sản phẩm vẫn **Chưa công khai**; người có quyền tiếp tục áp dụng các giá đã duyệt rồi thực hiện **Công khai** riêng.

### Gửi và duyệt sản phẩm — Nhánh lỗi

1. Thiếu quyền, hồ sơ thiếu dữ liệu/ảnh, danh mục không hoạt động hoặc sản phẩm đã bị xóa: từ chối thao tác, nêu điều kiện cần sửa; không tự duyệt hay công khai.
2. Manager chọn **Từ chối**, ghi lý do: lưu kết quả **Bị từ chối** cùng audit; Staff xem lý do, sửa thành phiên bản mới và gửi lại. Không áp dụng các giá của hồ sơ bị từ chối.
3. Hồ sơ đã thay đổi, đã có quyết định hoặc phiên bản gửi không còn hiệu lực: không ghi đè quyết định hay duyệt dữ liệu cũ; tải trạng thái mới để người dùng kiểm tra.
4. Manager hủy thao tác trước khi xác nhận: không lưu quyết định, hồ sơ vẫn chờ duyệt. Lỗi lưu/audit phải giữ trạng thái trước đó, không ghi nhận một phần hồ sơ/giá là đã duyệt.
5. Mất phản hồi hoặc gửi lặp: tra kết quả theo hồ sơ/phiên bản trước khi thử lại; không tạo hồ sơ trùng hoặc thêm quyết định cho cùng phiên bản đã xử lí.

## Đề xuất, phê duyệt và áp dụng giá sản phẩm

### Staff lập đề xuất giá — Luồng thành công

1. Staff có tài khoản hoạt động và quyền quản lí sản phẩm nhập giá đề xuất khi tạo biến thể, hoặc mở biến thể đã có và chọn **Đề xuất giá**. Frontend hiển thị sản phẩm, SKU, giá đang áp dụng và giá đề xuất riêng để xác định đúng đối tượng.
2. Staff nhập giá đề xuất là số nguyên VND dương, xem lại rồi chọn **Lưu nháp** hoặc **Gửi duyệt**. Các giới hạn giá bổ sung nếu có **chưa chốt**.
3. Backend kiểm tra quyền, sản phẩm chưa bị xóa, biến thể, phiên bản và dữ liệu giá; lưu mã đề xuất riêng, đối tượng/điều khoản đề xuất, số tiền và trạng thái cùng audit. Với sản phẩm mới, các đề xuất được gửi cùng phiên bản hồ sơ sản phẩm; không phải gửi trùng hai lần.
4. Frontend hiển thị trạng thái **Nháp**, **Chờ duyệt**, **Đã duyệt** hoặc **Bị từ chối** của đề xuất và trạng thái đã/chưa áp dụng riêng. Lưu hay gửi đề xuất không thay đổi giá bán hoặc trạng thái công khai hiện tại.

### Manager quyết định giá — Luồng thành công

1. Manager có quyền mở đề xuất chờ duyệt, xem đúng sản phẩm/biến thể, phiên bản, giá đang áp dụng, giá đề xuất và các đầu vào/quy tắc định giá liên quan. Với sản phẩm mới, bước này nằm trong phiên duyệt hồ sơ sản phẩm.
2. Manager xem gợi ý AI nếu sử dụng luồng tại FE-04; gợi ý chỉ mang tính tham khảo. AI không khả dụng không ngăn Manager quyết định giá thủ công dựa trên đầu vào nghiệp vụ hợp lệ.
3. Manager chấp nhận giá đề xuất, điều chỉnh thành giá cuối cùng hợp lệ hoặc từ chối kèm lý do, rồi xác nhận quyết định.
4. Backend kiểm tra vai trò Manager, quyền duyệt, trạng thái/phiên bản và giá cuối cùng; lưu quyết định, số tiền đã duyệt, đối tượng/điều khoản tương ứng, đầu vào quyết định, người duyệt, thời điểm và audit. Quyết định duyệt chưa thay đổi giá đang áp dụng hay tự công khai sản phẩm.
5. Staff xem kết quả; nếu bị từ chối thì sửa thành phiên bản đề xuất mới và gửi lại, không sửa quyết định đã lưu.

### Áp dụng giá đã duyệt — Luồng thành công

1. Staff hoặc Manager có quyền áp dụng giá mở đúng biến thể, đối chiếu giá hiện tại và giá Manager đã duyệt rồi chọn **Áp dụng giá**.
2. Backend kiểm tra quyền, sản phẩm chưa bị xóa, đề xuất đã được duyệt và còn hiệu lực với đúng đối tượng/điều khoản, dữ liệu mới nhất và số tiền khớp nguyên giá đã duyệt. Với sản phẩm mới, hồ sơ sản phẩm cũng phải đã được Manager duyệt và còn hiệu lực. Người áp dụng không được sửa số tiền ngay tại bước này.
3. Backend lưu giá bán mới, tham chiếu quyết định duyệt và dấu vết áp dụng cùng audit gồm người thực hiện, thời điểm, giá trước/sau. Yêu cầu áp dụng đề xuất cũ không được ghi đè một giá mới hơn đã được áp dụng.
4. Frontend hiển thị giá đã áp dụng. Với sản phẩm công khai và đủ điều kiện, lượt xem/checkout mới sử dụng giá mới; với sản phẩm chưa công khai, áp dụng giá không tự công khai sản phẩm.
5. Đơn và quyền giữ hợp lệ đã được chấp nhận trước đó giữ giá đã chốt trong thời hạn gốc. Giỏ chưa tạo đơn phải tải và kiểm tra lại giá hiện tại, cho khách xác nhận tổng tiền trước khi đặt.

### Đề xuất, duyệt và áp dụng giá — Nhánh lỗi

1. Không có quyền tương ứng, Staff tự gọi thao tác duyệt, chọn sai biến thể hoặc sản phẩm đã bị xóa: từ chối, không thay đổi đề xuất/quyết định/giá đang áp dụng.
2. Giá không hợp lệ: hiển thị lỗi tại trường nhập, giữ dữ liệu đã xác nhận trước đó; không lưu hoặc áp dụng giá không hợp lệ.
3. Đề xuất chưa được duyệt, bị từ chối, đã mất hiệu lực, sai đối tượng/điều khoản hoặc số tiền khác giá đã duyệt: không áp dụng; yêu cầu lập/gửi lại đề xuất phù hợp để Manager quyết định.
4. Dữ liệu đã thay đổi khi đang sửa, duyệt hoặc áp dụng: từ chối yêu cầu cũ, tải lại thông tin để người có quyền xem và xác nhận lại. Sửa đề xuất đã gửi phải tạo phiên bản mới, không kế thừa phê duyệt cũ cho số tiền/điều khoản khác.
5. Lỗi lưu/audit hoặc mất phản hồi: không báo hoàn tất khi chưa xác nhận; kiểm tra kết quả trước khi thử lại, không lưu một phần quyết định/giá hoặc ghi đè giá mới hơn bằng yêu cầu cũ.
6. Gửi lặp thao tác đã thành công: trả kết quả hiện tại của cùng yêu cầu, không duyệt hay áp dụng thêm một lần.

## Công khai và ngừng công khai sản phẩm

### Công khai sản phẩm — Luồng thành công

1. Staff hoặc Manager có quyền mở sản phẩm **Chưa công khai**, kiểm tra thông tin và kết quả Manager duyệt rồi chọn **Công khai**.
2. Backend kiểm tra quyền, sản phẩm chưa bị xóa, hồ sơ sản phẩm mới đã được Manager duyệt và còn hiệu lực, danh mục đang hoạt động, thông tin hợp lệ, có ít nhất một ảnh sử dụng được, có biến thể và mọi biến thể được cấu hình đều có giá đã được Manager duyệt và đã áp dụng. Không dùng giá đề xuất hoặc giá đã duyệt nhưng chưa áp dụng để vượt qua kiểm tra.
3. Số lượng cho phép bán bằng 0 không tự chặn công khai: sản phẩm vẫn có thể được hiển thị với tình trạng chưa có hàng để mua.
4. Backend cập nhật trạng thái công khai và ghi audit. Khách được xem sản phẩm ở các lượt đọc catalogue tiếp theo khi các điều kiện công khai vẫn đạt.

### Ngừng công khai — Luồng thành công

1. Staff có quyền mở sản phẩm, chọn **Ngừng công khai** và xác nhận.
2. Backend kiểm tra trạng thái hiện tại, cập nhật và ghi audit. Sản phẩm không còn xuất hiện trong catalogue công khai và không được tạo quyền giữ mới ở checkout.
3. Giỏ hoặc màn hình cũ không cho phép vượt qua kiểm tra hiện tại. Quyền giữ hợp lệ đã được chấp nhận, giá của đơn và lịch sử mua được bảo toàn; không tự hủy đơn hay gia hạn thanh toán.
4. Staff hoặc Manager có quyền có thể công khai lại sau khi sản phẩm đáp ứng điều kiện và chưa bị xóa. Ngừng công khai giữ sản phẩm trong danh sách quản trị để tiếp tục chỉnh sửa/công khai lại; xóa sản phẩm thực hiện theo luồng riêng bên dưới.

### Công khai và ngừng công khai — Nhánh lỗi

1. Thiếu ảnh, danh mục không hoạt động, thiếu biến thể, hồ sơ sản phẩm mới chưa được duyệt hợp lệ hoặc có biến thể chưa áp dụng giá đã được Manager duyệt: không công khai, nêu rõ điều kiện cần hoàn tất.
2. Gửi lại thao tác khi sản phẩm đã ở trạng thái yêu cầu: trả trạng thái hiện tại, không thực hiện thêm một lần chuyển trạng thái.
3. Sản phẩm đã bị xóa, dữ liệu thay đổi trong lúc xác nhận, không có quyền hoặc lưu/audit lỗi: từ chối thao tác, giữ trạng thái trước đó, cho tải lại và thử lại sau khi kiểm tra.

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

## Xóa sản phẩm

### Xóa sản phẩm — Luồng thành công

1. Staff hoặc Manager có quyền xóa mở sản phẩm, chọn **Xóa sản phẩm**. Có thể xóa sản phẩm nháp, chờ duyệt, bị từ chối, đã duyệt, đang công khai hoặc đang ẩn; xóa là thao tác riêng với ngừng công khai.
2. Frontend hiển thị đúng mã/tên sản phẩm và cảnh báo sản phẩm sẽ rời catalogue, danh sách quản trị thông thường, không thể sửa hay công khai lại; yêu cầu người dùng xác nhận. FE-02 không cung cấp luồng khôi phục sản phẩm đã xóa hoặc xử lí xóa nhầm.
3. Backend kiểm tra lại quyền, phiên bản và trạng thái hiện tại; thực hiện **xóa mềm**, lưu người xóa/thời điểm và audit. Trong cùng thao tác, sản phẩm bị loại khỏi các lượt đọc công khai, bị chặn thêm mới vào giỏ/tạo quyền giữ mới và các hồ sơ/đề xuất chưa áp dụng không còn được duyệt hoặc áp dụng. Không tự giải phóng lượng giữ hợp lệ.
4. Backend giữ định danh sản phẩm/biến thể, SKU, quyết định duyệt, dấu vết giá và các tham chiếu cần cho đối soát, đơn hàng, quyền giữ hợp lệ và lịch sử. Không xóa vật lí hay tái cấp SKU; không xóa tệp ảnh dùng chung hoặc ảnh còn phục vụ lịch sử giao dịch.
5. Đơn đã chấp nhận và quyền giữ còn hợp lệ tiếp tục thanh toán/xử lí theo FE-08 với điều khoản và thời hạn gốc. Xóa không tự hủy đơn, đổi giá, gia hạn hoặc ngăn xử lí kết quả thanh toán hợp lệ của giao dịch đã chấp nhận.
6. Frontend quay lại danh sách quản trị và bỏ sản phẩm đã xóa khỏi kết quả thông thường. Trang/giỏ cũ phải kiểm tra lại; khách được thông báo sản phẩm không còn bán và phải sửa lựa chọn trước checkout mới, không tự đổi sang sản phẩm khác.

### Xóa sản phẩm — Nhánh lỗi

1. Người dùng hủy xác nhận: không thay đổi dữ liệu hay trạng thái sản phẩm.
2. Thiếu quyền hoặc không tìm thấy sản phẩm: từ chối an toàn, không tiết lộ dữ liệu ngoài quyền và không báo xóa thành công.
3. Sản phẩm đã thay đổi trong lúc xác nhận: từ chối yêu cầu cũ, tải lại để người dùng kiểm tra trước khi xác nhận lại. Backend kiểm tra trạng thái xóa cùng lúc với duyệt/áp dụng giá/công khai/tạo quyền giữ, không cho yêu cầu chạy đồng thời khôi phục sản phẩm đã xóa hoặc tạo quyền giữ mới sau khi xóa.
4. Gửi lặp yêu cầu đã xóa thành công: trả kết quả đã xóa theo quyền, không xóa thêm dữ liệu, không trừ/hoàn số lượng và không làm mất lịch sử.
5. Lỗi lưu/audit: giữ trạng thái trước thao tác, không để sản phẩm bị xóa một phần. Mất phản hồi thì kiểm tra kết quả đã ghi nhận trước khi thử lại; không suy đoán thao tác chưa thực hiện.

## Quản lí danh mục sản phẩm

### Tạo, sửa và đổi trạng thái danh mục — Luồng thành công

1. Chỉ Staff có quyền quản lí sản phẩm được tạo, sửa, kích hoạt hoặc ngừng kích hoạt danh mục. Backend kiểm tra tài khoản hoạt động và quyền hiện tại trước mỗi thao tác.
2. Người có quyền mở danh sách danh mục, chọn tạo mới hoặc sửa, nhập tên/mô tả rồi xác nhận. Backend kiểm tra quyền, trường bắt buộc và quy tắc tên trước khi lưu cùng audit. Tên sau trim dài 1–200 ký tự, duy nhất toàn hệ thống kể cả danh mục không hoạt động khi so khớp không phân biệt hoa/thường; mô tả tùy chọn tối đa 5.000 ký tự. Danh mục mới mặc định hoạt động.
3. Khi ngừng kích hoạt danh mục, Frontend cho xem các sản phẩm bị ảnh hưởng trước khi xác nhận. Backend loại danh mục và sản phẩm liên quan khỏi các lượt tra cứu công khai mới; lịch sử sản phẩm, đơn và quyền giữ hợp lệ không bị viết lại.
4. Khi kích hoạt lại, chỉ sản phẩm còn công khai và đủ các điều kiện khác mới được hiển thị. Không tự công khai các sản phẩm người dùng đã chủ động ẩn.
5. Ngừng kích hoạt thay cho xóa danh mục đã được tham chiếu; chuyển sản phẩm sang danh mục khác qua luồng Sửa sản phẩm.

### Quản lí danh mục — Nhánh lỗi

1. Thiếu quyền, dữ liệu sai hoặc trùng tên theo quy tắc đã chốt: từ chối, chỉ rõ điều kiện cần sửa; không thay đổi danh mục một phần.
2. Danh mục thay đổi trong lúc thao tác hoặc lưu/audit lỗi: giữ dữ liệu đã xác nhận trước đó, yêu cầu tải lại/kiểm tra kết quả trước khi thử lại.

## Tra cứu sản phẩm công khai

### Xem, tìm kiếm và lọc catalogue — Luồng thành công

1. Guest hoặc Member mở catalogue mà không cần đăng nhập. Backend chỉ trả sản phẩm công khai, thuộc danh mục hoạt động và đáp ứng điều kiện nội dung/ảnh/giá.
2. Frontend hiển thị tên, mô tả tóm tắt, danh mục, ảnh sử dụng được, khoảng giá bán hiện tại của các biến thể và tình trạng **Có thể mua** khi ít nhất một biến thể đáp ứng quy tắc chung. Nếu không có biến thể nào có thể mua, sản phẩm vẫn hiển thị khi đủ điều kiện công khai nhưng không được gắn nhãn có thể mua.
3. Khách nhập từ khóa hoặc lọc theo danh mục, khoảng giá và tình trạng **Có thể mua**. Backend bỏ khoảng trắng thừa, tìm trong tên/mô tả không phân biệt hoa thường và kết hợp các bộ lọc bằng điều kiện cùng thỏa mãn. Khi bật bộ lọc **Có thể mua**, chỉ trả sản phẩm có ít nhất một biến thể thỏa đầy đủ điều kiện mua; không chỉ kiểm tra số lượng khả dụng lớn hơn 0.
4. Khoảng giá bao gồm hai đầu mút. Nếu lọc đồng thời giá và **Có thể mua**, cùng một biến thể phải có giá trong khoảng và đáp ứng đầy đủ điều kiện mua; không lấy giá của biến thể ngừng bán ghép với tình trạng có thể mua của biến thể khác.
5. Mặc định Backend sắp theo tên sản phẩm A–Z rồi định danh để thứ tự ổn định. Khách có thể đổi sang giá tăng dần, giá giảm dần hoặc mới nhất theo luồng bên dưới. Mỗi trang có 20 sản phẩm, từ khóa sau trim tối đa 200 ký tự.
6. Khách sửa/xóa bộ lọc hoặc mở một sản phẩm để xem chi tiết. Tra cứu chưa tạo giỏ, đơn, giữ lượng bán, giữ voucher hay điểm.

### Khách chọn sắp xếp sản phẩm — Luồng thành công

1. Khách mở lựa chọn **Sắp xếp** trên catalogue: tên A–Z, giá tăng dần, giá giảm dần hoặc mới nhất. Mặc định là **Tên A–Z**.
2. Frontend giữ từ khóa và các bộ lọc đang chọn khi đổi cách sắp xếp; Backend kiểm tra lựa chọn thuộc danh sách được hỗ trợ.
3. Với tên A–Z, Backend sắp theo tên; với mới nhất, dùng thời điểm tạo sản phẩm, không dùng thời điểm sửa giá để làm sản phẩm thành mới.
4. Với sản phẩm có nhiều biến thể, dùng **giá thấp nhất của các biến thể đang hiển thị và thỏa bộ lọc** làm giá đại diện cho cả sắp xếp tăng/giảm. Frontend vẫn hiển thị khoảng giá; giá chính xác được xem khi chọn biến thể.
5. Backend dùng định danh sản phẩm làm tiêu chí phụ khi trùng tên/giá/thời điểm để thứ tự ổn định; trả kết quả và cách sắp xếp đang áp dụng để Frontend hiển thị.
6. Frontend hiển thị kết quả và giữ bộ lọc hiện tại; thay đổi cách sắp xếp không tạo đơn hoặc giữ số lượng bán.

### Sắp xếp sản phẩm — Nhánh lỗi

1. Tiêu chí không được hỗ trợ: nêu lỗi và cho chọn lại cách sắp xếp hợp lệ.
2. Lỗi tải kết quả: cho thử lại, giữ từ khóa/bộ lọc; không hiển thị nhầm thành không có sản phẩm.

### Xem chi tiết và chọn biến thể — Luồng thành công

1. Khách mở sản phẩm từ catalogue; Backend kiểm tra lại điều kiện công khai trước khi trả dữ liệu.
2. Frontend hiển thị thông tin, ảnh và các size đã cấu hình; không có lựa chọn khắc. Chỉ hiển thị giá bán hiện tại của sản phẩm công khai, không lộ thông tin của sản phẩm chưa công khai.
3. Khách chọn một size đã cấu hình. Backend trả đúng định danh biến thể, giá và tình trạng khả dụng hiện tại; không tự tạo biến thể từ nội dung khách nhập.
4. Frontend xác định khả năng mua theo đầy đủ quy tắc chung: biến thể hết số lượng khả dụng được đánh dấu chưa mua được; biến thể ngừng bán hiển thị nhãn **Ngừng bán** và bị chặn mua dù còn số lượng. Các biến thể khác vẫn được xem và chọn theo tình trạng riêng. Sản phẩm vẫn hiển thị khi đủ điều kiện công khai dù không còn biến thể nào có thể mua, nhưng bị loại khỏi kết quả khi bật bộ lọc **Có thể mua**.
5. Khách chọn **Mua ngay** hoặc **Thêm vào giỏ hàng** thì chuyển sang [FE-08 — Mua hàng](#fe-08), mang theo sản phẩm, biến thể và số lượng đã chọn. FE-08 kiểm tra đăng nhập/xác minh và điều kiện mua trước khi chấp nhận.

### Tra cứu và chọn biến thể — Nhánh lỗi

1. Bộ lọc không hợp lệ, giá âm, giá tối thiểu lớn hơn tối đa hoặc danh mục không được hỗ trợ: nêu lỗi, cho sửa; không thay đổi dữ liệu sản phẩm.
2. Không có kết quả: hiển thị thông báo và cho xóa/đổi bộ lọc. Lỗi tải dữ liệu phải được báo riêng, không hiển thị nhầm thành catalogue rỗng.
3. Sản phẩm ngừng công khai hoặc danh mục không còn hoạt động sau lần xem trước: không trả nội dung công khai mới và không cho dùng trang/giỏ cũ để bỏ qua kiểm tra checkout. Biến thể ngừng bán vẫn hiển thị với nhãn **Ngừng bán** và bị chặn mua mới dù còn số lượng, theo luồng trạng thái biến thể.
4. Mất toàn bộ ảnh sử dụng được: loại khỏi kết quả công khai mà không tự sửa cờ công khai hay lịch sử; khi ảnh khôi phục, chỉ hiển thị lại nếu các điều kiện khác vẫn đạt. Với lỗi tải ảnh tạm thời, hiển thị thông tin lỗi/thử lại phù hợp.
5. Size không được cấu hình cho sản phẩm hoặc nhập lựa chọn ngoài catalogue: yêu cầu khách chọn đúng size đã có; không tạo thêm biến thể từ dữ liệu khách nhập.

<a id="fe-03"></a>

# FE-03 — Workshop Management

## Phạm vi và trạng thái tài liệu

FE-03 mô tả nghiệp vụ workshop từ cấu hình vận hành, công khai package, đặt
chỗ, thanh toán cọc, tham gia workshop đến các ngoại lệ sau xác nhận. Đây là
bản đồ nghiệp vụ của feature, không phải tuyên bố rằng tất cả các luồng đã được
triển khai.

Phần **đã có phạm vi V1** được xác định trong
`features/003-workshop-management/003.1-workshop-booking`: khách xem package, tạo booking chờ thanh toán,
thanh toán cọc, nhận xác nhận/QR/Magic Link và tra cứu booking. Cấu hình lịch,
check-in, completion/no-show, huỷ sau xác nhận, đổi lịch, refund và đồng bộ
kênh ngoài chưa thuộc implementation scope của feature 002. Các phần này được
ghi rõ là **Đề xuất** hoặc **Cần quyết định** bên dưới.

## Vai trò và ranh giới trách nhiệm

| Vai trò/hệ thống | Trách nhiệm trong FE-03 |
| --- | --- |
| Guest | Xem package, tạo và tra cứu booking bằng chứng liên hệ hợp lệ, thanh toán cọc. |
| Member | Thực hiện các quyền của Guest; xem booking thuộc tài khoản và có thể đặt cho người khác bằng contact snapshot. |
| Staff | Vận hành session, xử lý check-in hoặc ngoại lệ khi được cấp quyền; mọi thao tác vận hành cần audit. |
| Manager/Owner | Cấu hình package, lịch, sức chứa, phân công Staff và phê duyệt chính sách/ngoại lệ thuộc thẩm quyền. |
| Payment Gateway | Thực hiện OTP hoặc bước xác thực thanh toán; Mland chỉ xử lý kết quả đã xác thực. |
| External channel | Nhận dữ liệu đồng bộ theo contract riêng; không được ghi đè booking/capacity do Mland quản lý. |

## Cấu hình workshop, lịch và sức chứa — Đề xuất

Manager cần quản lý package được mở bán theo branch, session, sức chứa tối đa,
giới hạn số ticket mỗi lần đặt, trạng thái mở/đóng và Staff đủ điều kiện phụ
trách. Đây là workflow vận hành riêng, không phải quyền của khách đang đặt chỗ.

- Thay đổi sức chứa không được làm tổng số ghế nhỏ hơn participant của các
  booking đã xác nhận và capacity hold còn hiệu lực.
- Đóng package/session chỉ chặn booking mới; không tự huỷ, đổi lịch hay thay
  đổi giá trị booking đã xác nhận.
- Tạo session, lịch lặp, ngày nghỉ, đổi branch và tiêu chí Staff đủ điều kiện
  cần được đặc tả bằng feature riêng trước khi triển khai.
- Mland không dùng `check_in_closes_at` cho workshop làm nhẫn: khách đến muộn
  vẫn có thể check-in đến khi Staff/Manager finalize attendance của session.
  Thời điểm khách check-in chỉ ghi nhận đã đến, không cho phép bắt đầu làm sớm
  hay chiếm chỗ làm trước session; việc sắp xếp chỗ chờ/chỗ làm do Staff điều
  phối tại chỗ. Nếu cần một `check_in_opens_at`, nó chỉ là mốc hướng dẫn vận
  hành/thông báo, không phải điều kiện chặn check-in. Thay đổi cấu hình phải
  được audit và không được làm thay đổi lịch session, capacity hoặc booking đã
  xác nhận.
- Package, material/design option và branch/session phải được kiểm tra lại tại
  thời điểm submit, không chỉ dựa vào dữ liệu frontend đã hiển thị.

### Chính sách giá theo người — Quyết định V1 cập nhật 2026-10-25

Quyết định của **Hồ Hoàng Quốc** cho Workshop Booking V1: package chỉ bán theo
`PER_PERSON`. Mỗi package có một `price_per_participant` hợp lệ; hệ thống tính
`booking_total = participant_count × price_per_participant`. Ví dụ giá
750.000 VND/người, 4 người thì tổng booking là 3.000.000 VND.

Package vẫn phải có giới hạn participant tối thiểu/tối đa. Tiền cọc được tính
từ **tổng booking đã chốt**, không chỉ từ giá niêm yết ban đầu của package. Ví
dụ cọc 50% cho booking 3.000.000 VND là 1.500.000 VND.

`BASE_PLUS_EXTRA`, `base_price`, `included_participant_count`, và
`extra_person_price` không thuộc V1. Trường hợp hiếm nhiều người cùng làm một
sản phẩm được Staff xử lý thủ công ngoài booking flow V1; không được suy diễn
thành pricing mode hoặc customer-facing add-on.

#### Chú thích — suất làm và vé check-in

**Workshop item/suất làm** là một sản phẩm hoặc kết quả làm ra, ví dụ một chiếc
nhẫn. **Attendance ticket** là quyền của một người vào session và check-in bằng
QR. Trong V1, mỗi participant mua một suất theo giá package/người và nhận một
attendance ticket/QR; nhiều participant tương ứng nhiều suất và nhiều QR.

Một booking là **đơn thương mại/thanh toán** của người đặt cho đúng một package
hoặc course; V1 không có giỏ hàng hay nhiều package độc lập trong một booking.
Một package có thể có một hoặc nhiều session bắt buộc (ví dụ Silverclay hai
buổi). Capacity, check-in và no-show tính theo attendance ticket/session; giá,
cọc và khoản còn lại tính theo package booking đó. Phụ phí phát sinh sau
workshop gắn với booking/package và có thể liên kết ticket/người liên quan để
truy vết.

Đợt OTA đầu chỉ mở package theo giá mỗi participant. OTA không nhận hoặc đồng
bộ group-pricing hay phụ phí người cùng làm.

## Khám phá package và tạo booking — Đã có phạm vi V1

Guest hoặc Member chỉ thấy package được công khai và đang cho phép đặt. Chi tiết
package hiển thị giá hiện hành, thời lượng, giới hạn người tham gia, lựa chọn
material/design được hỗ trợ và điểm bắt đầu booking.

Khách chọn đúng một package/course cho mỗi booking; không có giỏ hàng hay chọn
nhiều package độc lập trong một booking. Package có thể công bố một hoặc nhiều
session bắt buộc, và cùng số participant sẽ được giữ chỗ trong từng session đó.
Hệ thống phát một attendance ticket cho mỗi người tham gia. Một booking chỉ có
một contact và một lần thanh toán cọc. Guest bắt buộc có email và số điện thoại;
Member được điền sẵn hai trường này nhưng có thể sửa khi đặt cho người khác.
Booking phải lưu contact snapshot, không phụ thuộc các thay đổi hồ sơ sau đó.

Sau khi kiểm tra package, session, sức chứa và dữ liệu bắt buộc trong cùng một
ngữ cảnh giao dịch, hệ thống tạo:

- booking `PENDING_PAYMENT` với trạng thái tài chính `UNPAID`;
- package đã chọn, các session bắt buộc của package, attendance ticket theo
  session, và giá snapshot theo participant;
- một invoice cọc bằng 50% tổng giá workshop cơ bản đã chốt;
- capacity hold tối đa 15 phút cho từng ticket tại session tương ứng.

Sức chứa của từng session được tính theo ticket đã xác nhận và hold còn hiệu lực
ở chính session đó, không tính theo số booking/order. Nếu bất kỳ ticket nào có
session ngừng mở bán, hết chỗ hoặc cạnh tranh sức chứa, hệ thống không được xác
nhận một phần booking mà không có policy tách đơn rõ ràng.

## Thanh toán cọc, xác nhận và tra cứu — Đã có phạm vi V1

Payment link có thời hạn tối đa 10 phút. Browser redirect không phải bằng chứng
để xác nhận booking: Backend chỉ chuyển sang `CONFIRMED` khi nhận kết quả gateway
có chữ ký hợp lệ, đúng booking reference, amount/currency và chưa được xử lý.

Khi cọc được xác nhận, hệ thống đặt trạng thái tài chính `DEPOSIT_PAID`, chốt
capacity của từng ticket/session, phát một QR ticket riêng cho từng participant
đã mua; mỗi QR không chứa PII hoặc dữ liệu thanh toán. Hệ thống cũng tạo Magic
Link đọc booking và notification intent. Payment thất bại/hủy giữ booking ở
`PENDING_PAYMENT` trong lúc hold còn hiệu lực để khách retry. Khi hold hết hạn,
booking chuyển `EXPIRED` và không được tiếp tục thanh toán.

Guest tra cứu bằng booking code cộng với email hoặc số điện thoại đã lưu; Member
chỉ được xem booking thuộc sở hữu của mình. Booking `PENDING_PAYMENT` hiển thị
thời gian hold còn lại và hành động thanh toán lại; booking `CONFIRMED` hiển thị
danh sách ticket/QR được nhóm theo session, không có payment countdown. Email
thất bại không được đảo trạng thái `CONFIRMED`; hệ thống lưu notification
retry/follow-up.

`CANCELLED` trong V1 chỉ là việc Guest/Member chủ động giải phóng hold khi
booking còn `PENDING_PAYMENT`; không phát QR, không refund và không phải hủy
booking đã xác nhận.

## Thanh toán phần còn lại và phụ phí — Đề xuất đã chọn

Mland chỉ thu cọc 50% của tổng giá package đã chốt khi đặt. Khách được check-in bình thường
khi attendance ticket hợp lệ thuộc booking `CONFIRMED` có cọc `DEPOSIT_PAID`;
không yêu cầu toàn bộ booking `FULLY_PAID`, vì phụ phí vật liệu/dịch vụ chỉ có
thể biết sau khi workshop item hoàn thành session của nó.

Sau **mỗi session của workshop item**, Staff/Manager chốt phụ phí của item đó.
Hệ thống tạo khoản cần thu cho item gồm phần giá workshop cơ bản còn lại và các
phụ phí đã chốt của item; phụ phí có thể ghi rõ ticket/người liên quan nhưng
không biến QR thành đơn vị giá. **Luồng mặc định:** Staff đưa bill và thu tại
quầy ngay sau session qua phương thức được shop cho phép (tiền mặt, POS hoặc
QR/gateway); người trả có thể là bất kỳ đại diện nào của nhóm, không bắt buộc là
booking contact. Khi payment được xác nhận, workshop item đã thanh toán đủ;
booking chỉ `FULLY_PAID` khi mọi workshop item đã thanh toán đủ hoặc được xử lý
ngoại lệ có audit.

Phụ phí cần được lưu thành các dòng charge riêng, có ticket liên quan, mô tả,
số tiền, currency, người chốt, thời điểm chốt và audit; không sửa giá workshop
cơ bản đã snapshot. Mỗi dòng đi qua ít nhất `PENDING_ASSESSMENT` (chưa biết/chưa
chốt) → `ASSESSED` (đã chốt số tiền) → `PAID` (đã được thu trong khoản cuối).

**Policy đã chọn:** Mland không cho nợ mặc định. Khách thanh toán bill cuối tại
quầy ngay sau workshop; sản phẩm hoàn thiện của workshop item chỉ được giao sau
khi khoản cần thu đã thanh toán. Chỉ khi Manager phê duyệt ngoại lệ (ví dụ người
đại diện không thể thanh toán tại quầy), hệ thống mới gửi payment link trả sau
và phải lưu lý do, actor, hạn thanh toán, số dư còn phải trả và audit. Link có
hạn **24 giờ** và hệ thống gửi một reminder trước khi hết hạn. Khi link hết hạn,
hệ thống không tự tạo link mới; shop tiếp tục giữ sản phẩm, còn Manager quyết
định liên hệ hoặc cấp link mới với audit. Không tự hủy sản phẩm, chuyển sở hữu
hoặc xóa công nợ. Sau khi bill đã chốt, Staff không sửa trực tiếp dòng charge
hoặc tiền đã thu; sai sót phải dùng dòng điều chỉnh/void có lý do và audit.
Không được suy ra rằng booking `CONFIRMED` đã thanh toán toàn bộ.

## Check-in, attendance, completion và no-show — Đề xuất

Mỗi participant có một QR ticket riêng, nhưng feature 002 chưa có luồng tiêu
thụ ticket. Feature tiếp theo nên cho Staff quét từng QR, xác minh ticket còn
hiệu lực, đúng session/branch và chưa được dùng, rồi ghi nhận attendance của
participant đó. Hệ thống cần chống quét lặp, lưu Staff/thời điểm/nguồn thao tác
và áp dụng ba tầng xác minh sau:

1. Scan QR ticket là luồng chính và chỉ check-in ticket mà QR xác định.
2. Khi QR không quét được, Staff nhập `ticket_code` riêng để check-in đúng
   ticket; thao tác thủ công được audit.
3. `booking_code` chỉ cho phép tra cứu booking/ticket set, không tự check-in
   ticket nào. Nếu cần override vì không có QR hoặc ticket code, Staff/Manager
   phải chọn ticket chưa dùng, nhập lý do và để lại audit record.

### Phân quyền check-in và override — Đề xuất đã chọn

Áp dụng phân quyền hai tầng:

- Staff có quyền `workshop.checkin` được scan QR hoặc nhập `ticket_code` để
  check-in ticket hợp lệ theo quy tắc tự động.
- Manager, hoặc Staff được cấp riêng quyền `workshop.checkin.override`, mới
  được xử lý ngoại lệ như khách chỉ có `booking_code` hoặc đến nhầm session.
- Override phải chọn một ticket chưa dùng, lưu actor, thời điểm, ticket/booking
  liên quan, lý do chuẩn hóa, ghi chú ngắn và correlation ID.
- Ticket đã check-in hoặc đã revoke không được override thành check-in lần nữa;
  chúng phải đi qua quy trình xử lý vận hành riêng.

### Thời điểm check-in — Đề xuất đã chọn

Workshop làm nhẫn không có `check_in_closes_at`: Staff check-in ticket hợp lệ
khi khách đến, dù đến sớm hay muộn, cho đến khi attendance của session được
finalize. Check-in xác nhận khách đã đến; nó không tự cho phép khách bắt đầu làm
sớm hoặc giữ chỗ làm. Staff điều phối chỗ chờ và thời điểm bắt đầu theo vận hành
tại chỗ.

`check_in_opens_at` là tùy chọn, chỉ dùng để hiển thị/hướng dẫn thời điểm nên
đến hoặc hỗ trợ vận hành; không chặn check-in. Sau khi Staff/Manager finalize
attendance, ticket chưa check-in chuyển `NO_SHOW`; ticket check-in muộn sau đó
chỉ được sửa bởi người có quyền hậu kiểm kèm audit. Manager thay đổi mốc hướng
dẫn theo session phải lưu actor/thời điểm/lý do và không tự thay đổi attendance
hiện có.

### Chốt attendance và no-show — Đề xuất đã chọn

Sau khi **mỗi session** kết thúc, Staff/Manager thực hiện hành động **Finalize
Attendance** cho session đó: hệ thống hiển thị ticket đã check-in và ticket chưa
check-in của session để người vận hành xem lại trước khi chốt. Khi chốt, ticket
chưa check-in của session chuyển thành `NO_SHOW`. Không dùng `COMPLETED` của cả
booking để thay cho attendance, vì các ticket trong cùng booking có thể thuộc
các session khác nhau.

Hệ thống không tự chuyển ticket sang `NO_SHOW` chỉ vì giờ bắt đầu session đã qua.
Sau khi attendance đã chốt, chỉ Manager hoặc người có quyền sửa hậu kiểm mới
được thay đổi kết quả; mọi chỉnh sửa cần actor, lý do, giá trị trước/sau và audit
record. Chính sách tài chính/quyền lợi cho `NO_SHOW` vẫn thuộc feature riêng cần
quyết định.

`CHECKED_IN`, completion và `NO_SHOW` là trạng thái theo ticket/session. Số
participant thực tế của một session là số ticket đã check-in, không phải con số
Staff nhập tự do. Việc chuyển ticket sang `NO_SHOW` phải dựa trên mốc thời gian
và grace period được phê duyệt, không tự động xảy ra chỉ vì chưa có thao tác
check-in. Các transition này được giữ sẵn nhưng chưa là transition triển khai
trong V1.

### Mô hình ticket cá nhân — Đề xuất đã chọn

Một order/booking có một người đặt và một giao dịch thanh toán, nhưng sau xác
nhận tạo `N` ticket, với `N = participant_count`. Mỗi ticket có ID/QR opaque,
trạng thái riêng và tối đa một lần check-in. Một participant đến muộn vẫn có thể
quét ticket của mình mà không ảnh hưởng các participant khác trong cùng booking.

Mô hình này yêu cầu thay thiết kế một ticket-per-booking hiện có của feature 002
thành quan hệ một booking-nhiều tickets/participants. Các artefact cần cập nhật
khi feature attendance được đặc tả là `002` hoặc feature kế nhiệm, data model,
OpenAPI, migration, notification template và test check-in/idempotency.

### Quyền sở hữu và phân phối ticket — Đề xuất đã chọn

V1 dùng policy `BOOKING_CONTACT_RECEIVES_ALL_TICKETS`. Người đặt là contact của
booking, nhận email xác nhận, Magic Link và toàn bộ `N` ticket/QR của booking;
người đặt tự chia ticket cho những người đi cùng. Form đặt chỗ chỉ yêu cầu thông
tin contact của người đặt và `participant_count`; không yêu cầu, suy diễn hay lưu
tên/email của từng participant. Mỗi ticket vẫn độc lập khi check-in bằng QR hoặc
`ticket_code`, dù ticket chưa được gán cho một danh tính participant.

Mở rộng sau V1 có thể thêm policy `EACH_PARTICIPANT_RECEIVES_TICKET`: người đặt
nhập tên/email cho từng ticket, hệ thống gửi ticket/claim link cho từng người và
cần workflow đổi người nhận hoặc chuyển ticket. Đây là feature riêng vì phát
sinh thu thập PII, consent, delivery failure, quyền sửa dữ liệu participant và
audit; không được ngầm triển khai trong V1.

## Hủy sau xác nhận, đổi lịch và refund — Chốt cuối phiên

Luồng này tách hoàn toàn khỏi việc release hold trước thanh toán. Chính sách sẽ
được **chốt trong phiên hoàn thiện FE-03 này, sau các vấn đề vận hành còn lại**;
feature triển khai có thể vẫn được tách riêng. Cho đến khi chốt, V1 hiện chỉ hỗ
trợ `CANCELLED` để release hold trước thanh toán. Cần quyết định ít nhất:

### Đơn vị áp dụng policy — Đề xuất đã chọn

Áp dụng cho booking trực tiếp Mland:

- **Không có self-service đổi lịch:** khách không tự đổi session trên website.
  Nếu cần đổi lịch, khách liên hệ shop; Manager hoặc người được cấp quyền đánh
  giá yêu cầu theo policy và capacity. Shop chỉ đổi một workshop item sang
  session còn chỗ của **cùng package, cùng cấu hình item và cùng số người tham
  gia**; không tự đổi sang package/thiết kế khác. Nếu chấp thuận, hệ thống
  chuyển toàn bộ ticket của item sang session mới trong một thao tác nguyên tử,
  chuyển phần cọc đã phân bổ cho item, revoke QR cũ và phát QR mới. Các workshop
  item khác trong cùng booking không thay đổi; mọi quyết định phải lưu actor, lý
  do và audit.
- **Giá khi đổi lịch thủ công:** nếu giá workshop cơ bản hiện hành của session
  mới cao hơn giá snapshot ban đầu, chênh lệch được thêm vào khoản còn phải thu
  của workshop item và thu theo luồng thanh toán cuối. Nếu giá mới thấp hơn hoặc
  bằng giá snapshot ban đầu, Mland V1 giữ nguyên giá cũ và không hoàn/credit
  phần chênh lệch. Muốn đổi sang package/thiết kế khác hoặc hoàn phần chênh là
  Manager exception, không phải luồng chuẩn.
- **Refund nghiệp vụ V1 chỉ theo toàn bộ booking:** không có self-service
  cancellation/refund riêng một workshop item, ticket hay người tham gia. Một
  yêu cầu refund toàn bộ chỉ đủ điều kiện khi mọi item/ticket trong booking chưa
  check-in và đều còn trong hạn policy. Sau khi một item đã được dùng hoặc quá
  hạn, khách không dùng luồng hủy/refund thông thường; Manager chỉ xử lý ngoại
  lệ có audit.
- Policy này không áp dụng cho OTA. Booking OTA đi theo điều kiện hủy/refund của
  channel; Mland chỉ đồng bộ kết quả và xử lý ticket/capacity tương ứng.

### Policy hủy/refund booking trực tiếp Mland — Đề xuất đã chọn

Khách có thể gửi yêu cầu hủy/refund **toàn bộ booking** rồi đặt booking mới nếu
muốn đổi kế hoạch; không có hủy/refund tự phục vụ từng workshop item hoặc ticket.
Booking chỉ hợp lệ để áp policy khi mọi item/ticket chưa check-in, chưa diễn ra
và chưa giao sản phẩm. Mốc thời gian luôn tính theo giờ bắt đầu sớm nhất trong
các workshop item của booking, dùng timezone của branch/session và policy
snapshot tại lúc đặt.

| Thời điểm khách yêu cầu so với session sớm nhất | Số tiền hoàn |
| --- | --- |
| Từ 72 giờ trở lên | Hoàn 100% cọc khách đã thực trả |
| Từ 24 đến dưới 72 giờ | Hoàn 50% cọc khách đã thực trả |
| Dưới 24 giờ, no-show hoặc đã có ticket check-in | Không hoàn |
| Mland hủy toàn bộ booking/không thể cung cấp workshop đã xác nhận | Hoàn 100% mọi khoản khách đã thực trả |

“Refund toàn bộ booking” nghĩa là hủy toàn bộ dịch vụ trong booking; số tiền
refund có thể là 100%, 50% hoặc 0% của cọc theo bảng trên. Đây **không** là
partial refund theo item/ticket. Phụ phí sau workshop không phát sinh trong một
hủy hợp lệ vì chưa có workshop item nào hoàn thành.

Khi yêu cầu hủy đạt điều kiện, hệ thống snapshot policy và số tiền phải hoàn,
tạo refund qua payment gateway rồi giữ `REFUND_PENDING`. Khi gateway xác nhận
kết quả, hệ thống chuyển `REFUNDED`, revoke QR chưa dùng, giải phóng capacity của
các session chưa diễn ra và gửi notification. Nếu gateway lỗi, booking không
được khôi phục để sử dụng; Staff/Manager đối soát refund lỗi theo audit.

Manager chỉ xử lý ngoại lệ với actor, lý do, số tiền thực hoàn, tham chiếu gateway
và audit.

Nếu **Mland chủ động hủy một workshop item** trong booking có nhiều session,
shop ưu tiên đề nghị session thay thế tương đương. Nếu không có session phù hợp
hoặc khách từ chối, Manager được hoàn mọi khoản khách đã thực trả được phân bổ
cho workshop item bị hủy; revoke ticket của item và giải phóng capacity tương
ứng. Đây là partial refund kỹ thuật **chỉ do Mland không thể cung cấp dịch vụ**,
không tạo quyền khách tự hủy/refund riêng item. Refund record phải có
`scope = WORKSHOP_ITEM`, liên kết item và gateway reference; không chuyển cả
booking sang `REFUNDED` khi các item khác vẫn giữ nguyên.

## Đồng bộ kênh ngoài — Cần quyết định

Booking `CONFIRMED` có thể tạo handoff record cho một feature đồng bộ sau này.
Mland vẫn là nguồn sự thật về booking và capacity. Contract sau này phải xác định
identity mapping, idempotency, retry, reconciliation, dữ liệu được phép chia sẻ
và cách xử lý remote failure; không tự áp dụng cancellation hoặc capacity từ
kênh ngoài khi chưa có policy/contract.

### Nguồn sự thật về session và capacity — Đề xuất đã chọn

Mland là nguồn sự thật duy nhất cho package/session được mở bán, sức chứa và số
chỗ còn lại. Manager chỉ tạo hoặc thay đổi các giá trị này tại Mland. Klook và
GetYourGuide chỉ nhận availability đã được Mland công bố, rồi gửi yêu cầu booking
vào Mland để Mland kiểm tra/trừ capacity nguyên tử. Không cho phép thay đổi
capacity trực tiếp tại OTA tự ghi đè dữ liệu Mland; bất kỳ khác biệt nào phải đi
qua quy trình đối soát và thao tác có quyền tại Mland.

### Kiến trúc tích hợp booking OTA thời gian thực — Đề xuất đã chọn

Mland dùng một core nội bộ duy nhất cho package/session, kiểm tra/trừ capacity,
booking, ticket và audit. Klook và GetYourGuide có **adapter riêng** để chuyển
đổi API, authentication, schema, voucher/ticket reference và quy tắc callback
của từng bên; không nhét khác biệt của OTA vào domain core hoặc buộc hai OTA có
cùng contract kỹ thuật.

Khi OTA gửi yêu cầu reserve/booking, adapter phải chuyển external booking
reference và idempotency key vào core Mland. Core kiểm tra capacity nguyên tử,
tạo đúng một channel booking và ticket theo số participant, rồi trả kết quả xác
nhận hoặc từ chối rõ ràng cho adapter/OTA. Gửi lặp cùng idempotency key phải trả
kết quả đã có, không trừ thêm capacity, tạo booking hay ticket trùng.

Webhook/callback về cancellation, refund và thay đổi booking đi qua adapter,
được xác thực và lưu external event reference trước khi xử lý. Thông báo/gửi
dữ liệu ra OTA dùng outbox có retry và backoff; lỗi tạm thời không được khiến
Mland tạo lại booking hoặc tự đổi capacity. Lỗi hết retry, mapping không khớp
hoặc khác biệt trạng thái phải vào hàng đợi đối soát để người có quyền xử lý,
với actor, lý do và liên kết booking/event đầy đủ. Không khắc phục bằng cách tạo
một booking Mland mới thủ công khi chưa xác minh external reference.

Mỗi adapter chỉ được go-live sau test end-to-end: availability/capacity, create
booking lặp, failure/retry, cancel/refund callback, ticket delivery và scan
từng ticket tại Mland. Secret/authentication không ghi vào log hay ledger; chỉ
lưu mã tham chiếu và metadata tối thiểu cần đối soát.

### Phạm vi package mở bán OTA — Đề xuất đã chọn

Đợt tích hợp OTA đầu chỉ publish package theo giá mỗi participant. Group-size
pricing hoặc phụ phí người cùng làm không thuộc V1; nếu được đề xuất sau này,
Mland phải thiết kế và xác thực riêng theo từng channel trước khi bật bán.

### Giá theo channel và điều kiện publish — Đề xuất đã chọn

Mland lưu giá riêng cho mỗi `package/channel`: giá website, `retail_price` khách
trả tại OTA, `target_net_amount` Mland muốn nhận, currency, phiên bản quy tắc
commission/fee và thời hạn hiệu lực. Mỗi OTA booking phải snapshot các giá trị
này cùng commission/fee thực tế và payout thực tế để đối soát lịch sử; không dùng
một trường tổng tiền chung cho mọi channel.

Với GetYourGuide, Manager cấu hình `retail_price` tại Mland và đồng bộ qua Price
over API khi integration được cấp quyền, hoặc nhập đúng giá này trên Supplier
Portal khi dùng cấu hình thủ công. Với Klook, Manager có thể chuẩn bị retail price
và target net nhưng package giữ `PENDING_COMMERCIAL_CONFIRMATION` và không được
publish đến khi contract/onboarding xác nhận giá API là retail hay net, commission,
thuế/phí, currency/FX và payout terms. Không suy ra retail price chỉ từ một tỷ lệ
commission danh nghĩa.

### Dịch vụ kèm theo OTA — Ghi nhận, chưa triển khai

Với booking từ **bất kỳ OTA nào**, chỉ dịch vụ **khắc laser** được bao gồm miễn
phí trong offer OTA hiện tại. Mọi material/design option, dịch vụ hoặc nâng cấp
khác vẫn là phụ phí theo bảng giá Mland tại thời điểm áp dụng; không được suy ra
chúng đã nằm trong `retail_price` OTA.

**Policy đã chọn:** Staff phải báo giá và nhận xác nhận của khách **trước** khi
dùng/nâng cấp lựa chọn có phí. Mland tạo bill phụ phí riêng tại quầy, liên kết
OTA booking/ticket reference và thu trực tiếp bằng phương thức Mland cho phép.
Bill phải snapshot hạng mục, giá, currency, thời điểm/xác nhận của khách, Staff
và payment reference; khắc laser được ghi là hạng mục 0 đồng nếu cần hiển thị.

Khoản này là doanh thu trực tiếp của Mland, không sửa `retail_price`, tiền khách
đã trả OTA, commission hay payout/settlement OTA; cũng không gọi refund qua OTA.
Không dùng luồng cọc của Mland cho booking OTA. Nếu khách chưa thanh toán phụ
phí đã chọn, shop chỉ giao phần nâng cấp/sản phẩm liên quan sau khi bill riêng
được thanh toán, trừ Manager exception có audit.

### Điều kiện ticket để mở bán OTA — Đề xuất đã chọn

Một package/session chỉ được bật bán trên Klook hoặc GetYourGuide sau khi test
end-to-end xác nhận rằng booking `N` participant tạo và OTA giao đúng `N` QR/ticket
riêng do Mland phát; Staff phải quét từng QR trên Mland. Không mở bán một option
nếu channel chỉ giao một voucher/order code chung thay cho ticket-per-person.

Voucher/order code chung chỉ là fallback sự cố sau go-live: người có quyền
`workshop.checkin.override` tra cứu channel booking, chọn đúng ticket active chưa
check-in theo số người thực tế đến, nêu lý do và lưu audit. Nó không được tự động
check-in toàn bộ booking và không phải luồng check-in hay điều kiện go-live mặc
định.

### Dữ liệu khách từ OTA — Đề xuất đã chọn

Mland chỉ lưu dữ liệu tối thiểu cần thiết để vận hành channel booking: channel,
external booking reference, package/option/session mapping, participant count,
ticket mapping, trạng thái và contact được OTA/contract cho phép dùng khi thật sự
cần hỗ trợ vận hành. Không tự tạo Member Mland, Magic Link, participant profile
hoặc dùng contact OTA cho newsletter, CRM/remarketing hay mục đích marketing.

Nếu sau này cần tên/email từng participant cho waiver, kiểm tra tuổi, chứng nhận
hay giao ticket riêng, phải tạo feature participant-registration riêng với
purpose/consent, quyền truy cập, retention, sửa/xóa và audit rõ ràng. Không suy
ra quyền thu/lưu các dữ liệu đó chỉ vì OTA có gửi chúng.

### Chính sách hủy/refund theo kênh — Đề xuất đã chọn

Tách hai lớp chính sách, không ép chúng phải giống nhau:

1. **Chính sách trực tiếp của Mland:** áp dụng duy nhất cho booking được tạo và
   thanh toán trên website Mland. Mland có thể cấu hình số tiền hoặc tỷ lệ hoàn
   khác nhau theo các mốc thời gian của session và thực hiện refund qua gateway
   của Mland.
2. **Chính sách OTA:** áp dụng cho booking tạo trên Klook hoặc GetYourGuide.
   Khách thanh toán và nhận refund từ OTA theo policy đã công bố trên trang
   OTA/voucher; Mland không gọi refund qua gateway của mình cho khoản tiền OTA
   đã thu. Policy này vẫn phải được Mland xác nhận khi onboarding/publish, vì
   nó quyết định quyền hủy, capacity và số tiền net Mland được đối soát.

Mỗi booking phải snapshot `sales_channel`, mã/phiên bản policy đã hiển thị,
cutoff và timezone tại thời điểm xác nhận. Không dùng policy đang hiệu lực ở
hiện tại để quyết định một booking cũ.

Mland V1 không có nghiệp vụ partial refund theo item/ticket: khách chỉ yêu cầu
hủy/refund toàn bộ booking, không hoàn riêng từng ticket hoặc participant. Số
tiền refund của toàn bộ booking vẫn có thể là một tỷ lệ cọc theo policy thời
gian. Với OTA, yêu cầu hủy và kết quả hoàn là các sự kiện riêng. Trong bản ghi
**tiền khách trả cho OTA** (có `sales_channel`), Mland ghi nhận tối thiểu `PAID_ON_CHANNEL`,
`REFUND_REQUESTED`, `PARTIALLY_REFUNDED` hoặc `REFUNDED`, số tiền OTA đã hoàn
và external reference.
`PARTIALLY_REFUNDED` nghĩa là OTA thực tế đã hoàn một phần tiền khách đã trả
OTA; `REFUNDED` nghĩa là OTA đã hoàn toàn bộ khoản đó. Đây không phải trạng thái
tổng của booking, cũng không mở workflow Mland tự tạo partial refund. Không suy
từ trạng thái service rằng khách đã được hoàn tiền. Chỉ khi cancellation được
OTA xác nhận thì Mland mới revoke ticket và giải phóng capacity, tránh bán lại
chỗ trong khi yêu cầu hoàn vẫn đang chờ xử lý.

Báo cáo/đối soát OTA phải tách tiền khách trả OTA, số OTA hoàn khách,
commission/fee, `target_net_amount`, payout dự kiến và payout thực nhận. Nội
dung confirmation/voucher luôn hiển thị policy của đúng channel đặt chỗ, không
đưa bảng refund trực tiếp của Mland cho khách OTA.

### Công nợ OTA và sổ đối soát bất biến — Đề xuất đã chọn

Tách trạng thái **tiền khách trả OTA** ở trên khỏi trạng thái **OTA phải thanh
toán cho Mland**. Mỗi booking OTA (hoặc từng dòng payout được OTA trả về) có
vòng đời công nợ:

```text
NOT_ELIGIBLE → ACCRUED → INVOICED → PAYOUT_PENDING → PAID_OUT
```

- `NOT_ELIGIBLE`: chưa đủ điều kiện ghi nhận công nợ theo contract, ví dụ còn
  trong thời hạn cancellation/no-show hoặc service chưa diễn ra;
- `ACCRUED`: Mland ước tính được khoản net phải nhận từ booking, dựa vào snapshot
  giá và commission/fee đã biết;
- `INVOICED`: khoản đó đã nằm trong invoice/statement của OTA;
- `PAYOUT_PENDING`: invoice/statement đã chốt nhưng Mland chưa nhận tiền;
- `PAID_OUT`: Mland đã đối chiếu được tiền OTA thực nhận với payout reference.

Không ghi đè số tiền đã ghi nhận. Mỗi gross sale, commission/fee, customer
refund, payout received, chênh lệch FX hay điều chỉnh phải là một dòng ledger
bất biến, có số tiền, currency, thời điểm, external reference, lý do và liên
kết tới booking/invoice/payout liên quan. Các loại bút toán tối thiểu gồm
`EXPECTED_NET`, `CHANNEL_COMMISSION`, `PAYOUT_RECEIVED`, `REFUND_REVERSAL`,
`CANCELLATION_COMPENSATION`, `FX_VARIANCE` và `MANUAL_ADJUSTMENT`.

`ADJUSTED` và `REVERSED` là tính chất/loại bút toán mới để điều chỉnh hoặc đảo
một bút toán cũ, không sửa trực tiếp lịch sử cũng không là một trạng thái chung
thay cho payout. `DISPUTED` là hồ sơ tranh chấp tách riêng, liên kết các bút
toán và giữ trạng thái xử lý của tranh chấp; nó không tự kết luận rằng OTA đã
hoặc chưa trả tiền. Báo cáo gross sales, net expected, payout received, refund
và công nợ phải được tổng hợp từ ledger này.

## State model

Các transition hiện có trong V1:

```text
PENDING_PAYMENT → CONFIRMED
PENDING_PAYMENT → EXPIRED
PENDING_PAYMENT → CANCELLED  (chỉ release hold trước thanh toán)
```

Các transition dự kiến cho feature sau:

```text
Ticket hợp lệ → CHECKED_IN → COMPLETED
Ticket hợp lệ → NO_SHOW
Ticket/booking → cancellation/refund workflow theo policy
```

### Diễn giải ngắn các trạng thái

Tên trạng thái chỉ có nghĩa trong **phạm vi** ghi dưới đây; ví dụ `REFUNDED`
của payment trực tiếp khác với `REFUNDED` của khoản tiền khách trả OTA. Các
trạng thái check-in, cancellation/refund sau xác nhận và OTA đều là đề xuất cho
feature sau, chưa phải transition triển khai của V1.

| Phạm vi | Trạng thái | Nghĩa ngắn gọn |
| --- | --- | --- |
| Booking | `PENDING_PAYMENT` | Đang giữ chỗ, chờ xác nhận thanh toán. |
| Booking | `CONFIRMED` | Đã nhận thanh toán cần thiết để xác nhận; vé/QR đã được cấp theo V1. |
| Booking | `EXPIRED` | Hết hạn giữ chỗ khi chưa thanh toán hợp lệ. |
| Booking | `CANCELLED` | V1: khách hủy booking đang chờ thanh toán để nhả hold; không phải hủy sau xác nhận. |
| Ticket attendance | `CHECKED_IN` | Ticket này đã được Staff xác nhận đến đúng session. |
| Ticket attendance | `COMPLETED` | Session của ticket đã kết thúc và attendance của ticket đã được chốt. |
| Ticket attendance | `NO_SHOW` | Ticket chưa check-in khi attendance được người có quyền chốt. |
| Payment trực tiếp | `UNPAID` | Chưa có khoản thanh toán trực tiếp hợp lệ. |
| Payment trực tiếp | `DEPOSIT_PAID` | Đã xác nhận thanh toán cọc; phần còn lại chưa được xử lý trong V1. |
| Payment trực tiếp | `FULLY_PAID` | Booking đã thu đủ cọc, phần giá cơ bản còn lại và mọi phụ phí của tất cả workshop item. |
| Payment trực tiếp | `REFUND_PENDING` | Refund trực tiếp đã được yêu cầu/duyệt, đang chờ kết quả gateway. |
| Payment trực tiếp | `REFUNDED` | Gateway đã xác nhận hoàn đúng số tiền refund của toàn bộ booking theo policy. |
| Payment attempt | `FAILED` | Một lần thử thanh toán thất bại; không tự đổi trạng thái booking. |
| Dòng phụ phí theo workshop item | `PENDING_ASSESSMENT` | Phụ phí có thể phát sinh nhưng chưa được Staff/Manager chốt. |
| Dòng phụ phí theo workshop item | `ASSESSED` | Phụ phí đã được chốt số tiền, chưa được thu. |
| Dòng phụ phí theo workshop item | `PAID` | Phụ phí đã được thu trong khoản thanh toán sau session của item. |
| Tiền khách trả OTA | `PAID_ON_CHANNEL` | OTA đã thu tiền khách cho booking. |
| Tiền khách trả OTA | `REFUND_REQUESTED` | Yêu cầu hoàn qua OTA đang chờ OTA xử lý/xác nhận. |
| Tiền khách trả OTA | `PARTIALLY_REFUNDED` | OTA thực tế đã hoàn một phần tiền khách trả; chỉ để ghi nhận/đối soát, không mở partial refund Mland V1. |
| Tiền khách trả OTA | `REFUNDED` | OTA thực tế đã hoàn toàn bộ tiền khách trả. |
| Công nợ OTA với Mland | `NOT_ELIGIBLE` | Chưa đủ điều kiện ghi nhận khoản OTA phải trả Mland. |
| Công nợ OTA với Mland | `ACCRUED` | Đã tính được khoản net Mland dự kiến nhận. |
| Công nợ OTA với Mland | `INVOICED` | Khoản phải thu đã có trong invoice/statement OTA. |
| Công nợ OTA với Mland | `PAYOUT_PENDING` | Statement đã chốt nhưng Mland chưa nhận tiền. |
| Công nợ OTA với Mland | `PAID_OUT` | Mland đã nhận và đối chiếu được payout OTA. |
| Hồ sơ tranh chấp | `DISPUTED` | Có tranh chấp cần theo dõi riêng; không tự kết luận refund hay payout. |

<a id="fe-04"></a>

# FE-04 — AI Chatbox Analysis & Suggestion

## Phạm vi và trạng thái nghiên cứu

FE-04 hiện được đặc tả trong
[`features/004-ai-chatbox-analysis-and-suggestion`](features/004-ai-chatbox-analysis-and-suggestion/).
Phần này là bản tổng hợp nghiên cứu và đối chiếu context; không tuyên bố rằng
backend đã được triển khai. Nguồn chi tiết hiện hành là `spec.md`, OpenAPI
contract, `data-model.md`, `plan.md`, `tasks.md` và các checklist của Feature-004.

Feature-04 gồm ba use case tách biệt:

1. **Basic AI Support:** Guest và Member hỏi về catalogue sản phẩm, workshop,
   thông tin cửa hàng và FAQ đã được duyệt.
2. **Booking-design image analysis:** chỉ Member được gửi ảnh trong một workshop
   booking hợp lệ, sau khi đồng ý consent riêng; kết quả luôn được Staff hoặc
   Manager review.
3. **Advisory price suggestion:** chỉ Manager yêu cầu AI gợi ý giá cho workshop
   package mới hoặc retail product mới; Manager quyết định giá cuối và catalogue
   workflow mới được phép apply/publish giá.

AI là provider hỗ trợ, không phải actor có quyền nghiệp vụ. AI không được xác nhận
booking, payment, feasibility, review decision, custom-manufacturing order hoặc
final price.

## Baseline đã chốt

### Actor và quyền

| Actor | Quyền trong FE-04 |
| --- | --- |
| Guest | Chỉ dùng Basic AI Support với public context; không được upload booking-design image. |
| Member | Dùng Basic AI Support; gửi ảnh booking-design của booking thuộc chính mình sau khi có consent; chỉ xem projection của request của mình. |
| Staff | Review request trong branch/delegation scope với estimated value không vượt quá 3.000.000 VND; không yêu cầu hoặc xem price suggestion nội bộ theo mặc định. |
| Manager | Review request trên 3.000.000 VND hoặc cần Manager authority; yêu cầu và quyết định price suggestion cho package/product mới. |
| Admin | Quản lý cấu hình kỹ thuật và có thể xem technical/audit evidence khi được cấp quyền; không thay Manager quyết định nghiệp vụ hoặc publish giá. |
| AI Provider | Trả text support, image quality/candidate features hoặc advisory price suggestion; không được mutate Mland business state. |
| Cloud Storage | Lưu tạm booking-design image và xử lý xóa theo retention rule; không sở hữu quyết định nghiệp vụ. |

### Booking context và image consent

Thứ tự booking được giữ là:

```text
Workshop Package
→ Location and Session
→ Booking Information
→ Design Path
→ Booking Summary
→ Payment
```

Image path chỉ mở cho Member có package, location, session, booking ownership,
design path và design version hợp lệ. Consent phải là consent riêng cho mục đích
booking-design image analysis, có version, purpose, Member identity, timestamp và
affirmative action. General Terms/Privacy không thay thế evidence này.

Ảnh booking-design chỉ được lưu tạm thời, gửi provider sau khi validation thành
công và phải bị xóa sau processing ở cả success/failure path. Ảnh của
custom-manufacturing là luồng riêng, có retention riêng và không được gộp vào
Feature-04.

### AI boundary

Basic AI Support chỉ nhận public context đã được allowlist. Không gửi Firebase
token, provider credential, password, payment data, private customer data hoặc raw
operational data.

Đối với image analysis, AI chỉ có thể trả:

- Image quality.
- Candidate ring shape/material/component.
- Candidate design attributes.
- Confidence/uncertainty metadata nếu provider hỗ trợ.

Đối với price suggestion, AI chỉ trả advisory suggestion cho package/product mới.
Suggestion không được tự động thay đổi hoặc publish giá. Price suggestion nội bộ
chỉ Manager có quyền trên proposal được xem; Admin chỉ xem technical/audit evidence
nếu được cấp quyền; Staff, Guest và Member không xem projection này.

### Human review và state

Mọi booking-design request hợp lệ đều cần human decision:

```text
analysis: PENDING → PROCESSING → SUCCEEDED / FAILED
review:   NEED_REVIEW → ACCEPTED / REJECTED / CLARIFICATION_REQUIRED
```

AI không auto-accept hoặc auto-reject. Staff xử lý request ở mức không quá
3.000.000 VND trong scope; Manager xử lý request trên ngưỡng hoặc có Manager
authority. Reviewer không được review request do mình tạo hoặc request có conflict
of interest. Material design change tạo version mới và không tái sử dụng decision cũ.

`CLARIFICATION_REQUIRED` không phải là rejection tự động; Member được quay lại
current design context và phải tạo version mới nếu thay đổi material design.

### Input security và rate limiting

Provider request phải được tạo từ normalized allowlisted command model, không forward
raw request body. Input phải qua schema/type/length/size/ownership/business-context
validation trước provider call. Image phải kiểm tra MIME type, file signature, size,
dimensions, malware policy, metadata và ownership.

User input là untrusted data, không phải system instruction. Prompt injection hoặc
request cố đổi role, policy, output schema, pricing rule hay review decision phải bị
reject trước provider call với các error code tương ứng:

```text
AI_INPUT_INVALID
AI_PROMPT_INJECTION_DETECTED
AI_CONTEXT_NOT_ALLOWED
AI_OUTPUT_POLICY_VIOLATION
```

Provider output phải được schema/policy validate; output chứa final approval, final
price, publication command, hidden instruction hoặc data leakage không được persist
và không được tạo business mutation.

Mỗi authenticated Member được tối đa **3 image-submission attempts trong rolling
window 5 phút**, tính theo Member ID. Attempt sau khi identity đã xác thực vẫn được
tính dù bị reject vì consent, context hoặc file không hợp lệ. Attempt thứ tư trả
`429 IMAGE_RATE_LIMITED` và `Retry-After`, không storage, không Gemini call, không
tạo request. Duplicate retry với cùng idempotency key không tiêu tốn thêm quota;
quota phải được check/reserve atomically.

## API và data ownership

Các endpoint của Feature-04 gồm:

```text
POST /api/v1/ai-support/messages
POST /api/v1/design-requests
GET  /api/v1/design-requests/{id}
GET  /api/v1/design-review-requests
POST /api/v1/design-review-requests/{id}/decisions
POST /api/v1/price-proposals/{id}/ai-suggestions
```

Feature-04 sở hữu provider interaction evidence, consent, temporary image reference,
analysis projection, review linkage và price suggestion evidence. Workshop Booking
sở hữu booking/session/payment; Catalogue sở hữu apply/publish price; Custom
Manufacturing sở hữu custom-order acceptance/pricing/retention. Không dùng Feature-04
để ghi trực tiếp repository của module khác.

Các logical records cần duy trì gồm AI text interaction, BookingDesignRequest,
ImageConsent, TemporaryImageReference, AnalysisResult, HumanReviewDecision và
PriceSuggestion. Technical analysis status phải tách khỏi business review status.

## Kết quả đối chiếu với context mới

### Điểm đã đồng bộ

1. `spec-general.md`, Report 3 actors/use cases/context, Feature-004 `spec.md` và
   OpenAPI đều thống nhất Member-only image flow, consent riêng, human review và
   advisory AI output.
2. Custom-manufacturing reference image và booking-design image đã được mô tả là
   hai loại dữ liệu/retention khác nhau.
3. Role nghiệp vụ hiện dùng `Guest`, `Member`, `Staff`, `Manager`, `Admin`; không
   tự ánh xạ Manager thành `OWNER` hoặc Admin thành một physical role code khi
   chưa có quyết định identity mapping.
4. Permission Matrix và Validation Matrix trong Feature-004 đã quy định actor,
   scope, thứ tự validation, error code và side effect trước/provider sau.
5. Luồng price suggestion đã được giới hạn cho package/product mới, không dùng cho
   custom-manufacturing quotation, invoice hoặc payment amount.

### Mâu thuẫn hoặc điểm cần đồng bộ tiếp

1. **API design request còn gộp configured design choice.**
   `ppswbs-spec/APIs.md` hiện mô tả `POST /api/v1/design-requests` có thể submit
   booking-design image *hoặc validated configured design choice*, trong khi
   Feature-004 `spec.md` và OpenAPI đang đặc tả endpoint này cho Member image
   request có consent. Cần quyết định tách configured design submission sang
   owning design/booking facade hoặc mở rộng contract của Feature-004; không được
   để request body và permission rule hiểu theo hai nghĩa.

2. **Screen context còn mô tả consent chưa thống nhất.**
   `ppswbs-spec/screen.md` có đoạn nói image-specific consent trong UC30 chưa
   thống nhất và không tự thêm consent screen, nhưng Feature-004 và Report 3 đã
   chốt consent form riêng trước storage/provider call. Đoạn screen cũ cần được
   cập nhật hoặc đánh dấu obsolete trước khi dùng làm source cho UI/spec.

3. **Một số screen text vẫn có thể hiểu là AI tự đánh giá khả năng.**
   `screen.md` mô tả các kết quả “có thể tự làm / cần tư vấn / không thể tự làm” và
   cập nhật estimate khi đổi options. Cách diễn đạt này chỉ hợp lệ nếu đó là
   candidate/system estimate context để Staff/Manager xem; không được hiểu là AI
   auto-reject hoặc quyết định feasibility. Cần ghi rõ nguồn decision là human
   review và candidate result không phải terminal business state.

4. **Tên use case price còn gây hiểu nhầm.**
   Report 3 vẫn dùng tên `Approve Custom Design Pricing`, dù nội dung hiện chỉ áp
   dụng cho workshop package/product mới và không pricing custom-manufacturing.
   Nên đổi tên thành `Request and Decide Advisory Catalogue Price Suggestion` hoặc
   đánh dấu tên cũ là legacy để tránh suy luận rằng AI pricing custom order.

5. **Configured design và image analysis có ranh giới persistence chưa hoàn toàn
   rõ.** `DB.md` xác định `ring_designs` không chứa Member image request, AI output
   hoặc review status và custom request lifecycle độc lập. Điều này phù hợp với
   Feature-004 nếu analysis/request/result dùng record riêng; physical table,
   FK/version mapping và facade ownership vẫn phải được chốt trước migration.

6. **Ngưỡng review phụ thuộc estimate source chưa hoàn tất.** Feature-004 đã chốt
   Staff `≤ 3.000.000 VND`, Manager `> 3.000.000 VND` hoặc Manager authority,
   nhưng nguồn/phiên bản estimate, rounding, unknown estimate và routing khi
   không tính được vẫn cần được Catalogue/Design Review owner xác nhận. Unknown
   estimate không được route về AI auto-decision; phải đi Manager hoặc
   `CLARIFICATION_REQUIRED`.

7. **Rate-limit và prompt-injection mới nằm chủ yếu ở Feature-004.** General spec,
   Report 3 và các screen artifact chưa mô tả đầy đủ giới hạn 3 ảnh/5 phút,
   atomic reservation, `Retry-After` và các error code security. Khi đồng bộ UI,
   API gateway và test plan, phải dùng Feature-004 matrix làm nguồn tham chiếu,
   không tự đặt limit khác ở frontend.

## Kết luận nghiên cứu và việc cần làm tiếp

Feature-004 đã đủ baseline để tiếp tục hoàn thiện implementation design, nhưng chưa
nên coi là code-ready tuyệt đối cho đến khi xử lý các điểm trên. Việc ưu tiên là:

1. Chốt endpoint ownership cho configured design choice.
2. Đồng bộ `screen.md` với consent form bắt buộc và human-review boundary.
3. Đổi/ghi chú tên use case price để tách package/product pricing khỏi custom order.
4. Chốt physical persistence mapping cho booking-design request, consent, analysis
   result, review version và temporary image reference.
5. Chốt estimate source/routing cho unknown estimate và kiểm chứng matrix-to-API.

Các mục trên là dependency của `plan.md`, `data-model.md`, migration design và
implementation tasks; không được giải quyết bằng cách suy ra hành vi từ code hoặc
browser response.

<a id="fe-05"></a>

# FE-05 — Personalized Design & Quote Workflow

Chưa mô tả luồng thiết kế cá nhân hóa, review, báo giá và đơn đặt làm.

<a id="fe-06"></a>

# FE-06 — Loyalty & Promotion Management

Feature này gồm quản lí chương trình voucher/giảm giá và điểm thưởng, nhằm khuyến khích Member quay lại mua sản phẩm và tham gia workshop. Luồng mua sản phẩm tham chiếu [FE-08](#fe-08), đặt workshop tham chiếu [FE-03](#fe-03), cấu hình tham số tham chiếu [FE-09](#fe-09). Nội dung dưới đây là thiết kế nghiệp vụ đã thống nhất để làm spec, chưa phải chức năng đã triển khai. Mức giảm, giá trị giao dịch tối thiểu và hạn mức lượt dùng là dữ liệu Manager cấu hình cho từng chương trình, không phải thông số cố định cần chốt chung. Bộ thông số điểm và cách tính đã chốt được mô tả tại phần Điểm thưởng; Admin quản lí cấu hình chính sách điểm. Tỷ lệ cọc, điều kiện hủy và thời hạn thanh toán tham chiếu FE-03/FE-08 theo loại giao dịch.

## Quản lí chương trình ưu đãi

### Phạm vi và quy tắc chung

1. Manager tạo, sửa, tạm dừng chương trình, theo dõi lượt sử dụng, số tiền đã giảm và cấu hình chính sách điểm (tỷ lệ tích điểm, giá trị quy đổi, số điểm tối thiểu mỗi lần dùng, trần giảm bằng điểm). Admin không cấu hình chính sách điểm hay chương trình ưu đãi; Admin chỉ quản lí tài khoản và cấp quyền. Member xem, lưu và sử dụng quyền lợi của mình.
2. Voucher có hai phạm vi riêng: **sản phẩm** và **workshop**. Voucher sản phẩm không dùng cho workshop và ngược lại. Voucher workshop dành cho Member đặt trực tiếp trên website.
3. Voucher ngày lễ là chương trình có thời gian áp dụng riêng và vẫn thuộc phạm vi sản phẩm hoặc workshop; không phải một lớp voucher được cộng thêm vào giao dịch.
4. Với mua sản phẩm, mỗi đơn áp dụng **một chương trình giảm tự động hoặc một voucher**, sau đó có thể dùng thêm điểm nếu chính sách cho phép. Mỗi booking dùng tối đa một voucher; trần giảm áp dụng cho cả booking, không nhân theo số người tham gia.
5. Chương trình ưu đãi sản phẩm chưa giới hạn theo chi nhánh trong phiên bản này; Staff điều phối nơi chuẩn bị hàng theo FE-08. Voucher workshop có thể giới hạn theo chi nhánh tổ chức do Manager cấu hình.
6. Mã voucher không phân biệt chữ hoa/thường, bỏ khoảng trắng đầu/cuối khi tạo và tra cứu. Mã là duy nhất toàn hệ thống, không tái sử dụng mã cũ kể cả khi chương trình đã hết hạn hoặc bị dừng. Sau khi tạo không đổi mã hoặc phạm vi sản phẩm/workshop.
7. Với ưu đãi sản phẩm, cơ sở kiểm tra giá trị tối thiểu và tính giảm là tổng tiền các dòng sản phẩm/biến thể thuộc phạm vi chương trình, trước khi trừ ưu đãi và điểm, không gồm phí giao hàng. Các dòng không thuộc phạm vi không được tính vào cơ sở này. Giảm số tiền cố định một lần/đơn, không nhân theo số lượng sản phẩm; giảm phần trăm làm tròn xuống đến đồng. Khoản giảm không vượt mức giảm tối đa đã cấu hình hoặc giá trị phần đủ điều kiện, không làm tiền hàng âm.

### Tạo voucher sản phẩm — Luồng thành công

1. Manager mở phần quản lí ưu đãi, chọn **Tạo chương trình** và phạm vi **Sản phẩm**.
2. Manager nhập tên chương trình, mã voucher, thời gian sử dụng và phạm vi sản phẩm/biến thể áp dụng; không cấu hình giới hạn chi nhánh cho sản phẩm.
3. Manager chọn giảm theo phần trăm hoặc số tiền cố định, nhập mức giảm, mức giảm tối đa và giá trị tối thiểu của phần sản phẩm/biến thể đủ điều kiện theo quy tắc chung.
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

1. Manager chọn tạo chương trình giảm tự động, cấu hình phạm vi sản phẩm/biến thể, thời gian, mức giảm và các điều kiện áp dụng theo quy tắc chung; không giới hạn chi nhánh.
2. Manager xác định chương trình có cho phép dùng thêm điểm hay không rồi xác nhận lưu.
3. Backend kiểm tra quyền và điều kiện trước khi ghi nhận chương trình.
4. Tại checkout sản phẩm, Backend tính riêng khoản giảm của từng chương trình tự động hợp lệ, chọn một chương trình có khoản giảm lớn nhất; nếu bằng nhau, ưu tiên chương trình tạo trước. Backend trả chương trình được chọn và khoản giảm để Member xem; Member không cần nhập mã.
5. Nếu Member chọn voucher thay cho giảm tự động, hệ thống tính lại tổng tiền với voucher đó; không cộng dồn cả hai khoản giảm.

### Tạo chương trình — Nhánh lỗi

1. Thiếu trường bắt buộc, thời gian không hợp lệ, mức giảm hoặc hạn mức sai: hiển thị lỗi tại trường tương ứng; chưa lưu chương trình.
2. Mã voucher sau khi bỏ khoảng trắng đầu/cuối và so sánh không phân biệt hoa/thường trùng mã đã cấp, hoặc phạm vi sản phẩm/biến thể/gói/phiên không hợp lệ: yêu cầu Manager sửa lựa chọn trước khi lưu; không cho tái sử dụng mã cũ.
3. Tài khoản không có quyền quản lí chương trình: Backend từ chối thao tác; không chỉ ẩn nút trên giao diện.
4. Lỗi lưu hoặc mất phản hồi: thông báo chưa xác nhận được kết quả và cho kiểm tra lại danh sách chương trình trước khi gửi lại; không báo tạo thành công khi chưa có xác nhận.

### Sửa, tạm dừng và theo dõi chương trình

1. Manager mở danh sách, chọn một chương trình để xem điều kiện, thời gian, tổng lượt, số lượt đã dùng và số tiền đã giảm; có thể lọc theo phạm vi sản phẩm hoặc workshop.
2. Manager chọn **Sửa**, cập nhật điều kiện rồi xác nhận; không đổi mã hoặc phạm vi sản phẩm/workshop. Backend kiểm tra quyền và dữ liệu trước khi lưu. Tổng hạn mức không được thấp hơn tổng lượt đã dùng cộng lượt đang giữ; hạn mức mỗi Member không được thấp hơn lượt đã dùng cộng lượt đang giữ của bất kỳ Member nào. Nếu vi phạm, từ chối thay đổi và nêu lý do; Frontend tải lại thông tin sau khi lưu thành công.
3. Các thay đổi chỉ dùng cho giao dịch được chấp nhận sau đó. Đơn/booking đã được hệ thống chấp nhận giữ khoản giảm, số điểm dùng, giá trị quy đổi và tỷ lệ tích điểm đã ghi nhận; không tính lại theo chính sách mới.
4. Manager chọn **Tạm dừng** khi muốn ngừng nhận thêm giao dịch mới theo chương trình. Backend cập nhật trạng thái; mã đã lưu trong Kho Voucher không còn đủ điều kiện cho giao dịch mới khi chương trình bị dừng.
5. Tạm dừng hoặc hết hạn chương trình không tự hủy quyền lợi đã chốt hay đang giữ hợp lệ. Giao dịch đã được chấp nhận tiếp tục dùng quyền lợi đã ghi nhận trong thời hạn thanh toán gốc; không gia hạn phần giữ. Xử lí hết hạn giao dịch theo phần Chốt và giải phóng quyền lợi bên dưới.
6. Nếu cập nhật lỗi hoặc mất phản hồi, giữ khả năng tải lại trạng thái để kiểm tra; không hiển thị đã sửa hoặc đã dừng khi chưa được Backend xác nhận.

## Kho Voucher

### Xem và lưu voucher — Luồng thành công

1. Khách xem ưu đãi tại trang sản phẩm, gói workshop hoặc trang ưu đãi. Trên thẻ voucher, Frontend hiển thị rõ phạm vi **Mua sản phẩm** hoặc **Đặt workshop**, mức giảm, mức giảm tối đa, giá trị tối thiểu, thời hạn và có cho phép dùng thêm điểm hay không.
2. Khách mở **Điều kiện sử dụng** để xem sản phẩm/biến thể hoặc gói workshop, chi nhánh workshop, ngày/phiên áp dụng nếu có, tổng hạn mức lượt dùng và số lượt mỗi Member. Các thông tin này lấy từ cấu hình của từng chương trình. Khách chọn **Lưu voucher**; nếu chưa đăng nhập, hệ thống hướng dẫn đăng nhập trước khi lưu vào tài khoản.
3. Backend kiểm tra Member và ghi nhận voucher trong **Kho Voucher** của Member đó; lưu lại cùng mã không tạo thêm quyền sử dụng.
4. Member mở Kho Voucher để xem các mã đã lưu và điều kiện hiện tại, hoặc chọn mã đã lưu khi checkout. Member vẫn có thể nhập mã trực tiếp mà không cần lưu trước.
5. Lưu voucher chưa giữ lượt, chưa tiêu dùng hạn mức và chưa bảo đảm dùng được. Backend kiểm tra lại điều kiện khi áp dụng và khi Member xác nhận giao dịch.

### Xem và lưu voucher — Nhánh lỗi

1. Voucher hết hạn, bị tạm dừng hoặc Member chưa đạt điều kiện: hiển thị rõ tình trạng; không coi việc mã đã có trong kho là đủ điều kiện sử dụng.
2. Phiên đăng nhập không còn hợp lệ: yêu cầu đăng nhập lại trước khi lưu hoặc xem kho cá nhân.
3. Mất mạng hoặc lỗi lưu: cho thử lại; chỉ hiển thị đã lưu sau khi Backend xác nhận.

## Áp dụng ưu đãi khi mua sản phẩm

### Checkout — Luồng thành công

1. Member tiếp tục checkout từ [FE-08](#fe-08). Backend lấy các dòng mua, giá và thông tin Member để xác định ưu đãi hợp lệ; không dùng chi nhánh xử lí làm điều kiện ưu đãi sản phẩm.
2. Frontend hiển thị giảm tự động nếu có, các voucher sản phẩm phù hợp và số điểm khả dụng. Member chọn một voucher từ kho, nhập mã trực tiếp hoặc sử dụng giảm tự động.
3. Backend kiểm tra thời gian, trạng thái chương trình, phạm vi sản phẩm/biến thể, giá trị tối thiểu trên phần đủ điều kiện, tổng lượt và lượt của Member; trả khoản giảm hợp lệ theo quy tắc chung. Chỉ áp dụng một giảm tự động hoặc một voucher; khi có nhiều giảm tự động hợp lệ, chọn theo khoản giảm lớn nhất và thời điểm tạo như luồng quản lí chương trình.
4. Backend tính số điểm tối đa được dùng cho giao dịch dựa trên điểm khả dụng và chính sách kết hợp ưu đãi, với trần giảm bằng điểm là 10% tiền hàng sau giảm tự động/voucher, trước khi trừ điểm và không gồm phí giao hàng. Mỗi điểm giảm 100đ, dùng tối thiểu 10 điểm/lần. Frontend hiển thị điểm khả dụng, số điểm tối đa được dùng và số tiền giảm tương ứng để Member chủ động chọn dùng điểm trong giới hạn đó. Nếu ưu đãi không cho kết hợp điểm hoặc số điểm tối đa nhỏ hơn mức tối thiểu, Frontend không cho chọn dùng điểm và hiển thị lý do.
5. Frontend hiển thị tiền hàng, giảm tự động/voucher, giảm bằng điểm, phí giao hàng và tổng cuối cùng để Member xem và xác nhận. Phí giao hàng không thuộc cơ sở cộng điểm hoặc trần giảm bằng điểm.
6. Mở checkout hoặc thử mã chưa giữ lượt voucher hay điểm. Khi Member xác nhận đặt hàng, Backend kiểm tra lại điều kiện và tạo đơn cùng phần quyền lợi tạm giữ theo [FE-08](#fe-08), ghi nhận điều kiện ưu đãi, khoản giảm, điểm dùng, giá trị quy đổi và tỷ lệ tích điểm áp dụng cho đơn.
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
3. Backend kiểm tra phạm vi workshop, thời gian được đặt bằng mã, chi nhánh, gói/ngày/phiên, tổng phí gói tối thiểu và hạn mức sử dụng; trả mức giảm hợp lệ. Mỗi booking dùng tối đa một voucher, trần giảm áp dụng cho cả booking và không nhân theo số người.
4. Voucher được trừ trên tổng phí gói trước khi tính cọc. Nếu chính sách cho phép dùng thêm điểm, Backend tính số điểm tối đa hợp lệ theo điểm khả dụng, với trần giảm bằng điểm là 10% phí gói sau voucher, trước khi trừ điểm. Mỗi điểm giảm 100đ, dùng tối thiểu 10 điểm/lần. Frontend hiển thị điểm khả dụng, số điểm tối đa được dùng và số tiền giảm tương ứng để Member lựa chọn; nếu voucher không cho kết hợp điểm hoặc số điểm tối đa nhỏ hơn mức tối thiểu thì không cho chọn dùng điểm và nêu lý do. Giá trị quy đổi của điểm đã chọn được trừ sau voucher và trước khi tính cọc.
5. Frontend hiển thị phí gói ban đầu, giảm voucher, giảm bằng điểm, phí gói sau giảm, tiền cọc và phần còn phải thu để Member xác nhận. Tiền cọc được tính theo chính sách booking tại FE-03 trên phí gói sau voucher và điểm. Phiên bản đầu chưa hỗ trợ workshop miễn phí: phí gói sau giảm phải lớn hơn 0; nghĩa vụ cọc/thanh toán phải đáp ứng điều kiện thanh toán của FE-03.
6. Khi Member xác nhận booking, Backend kiểm tra lại điều kiện và tạm giữ lượt voucher, điểm cần dùng cùng việc ghi nhận booking, lưu điều kiện ưu đãi, khoản giảm, điểm dùng, giá trị quy đổi và tỷ lệ tích điểm áp dụng. Xác minh cọc thành công thì chốt lượt voucher và trừ điểm.
7. Khi thu phần còn lại, hệ thống dùng số tiền đã ghi nhận của booking; không áp dụng lại voucher hay trừ lại điểm. Chỉ cộng điểm mới sau khi Member đã hoàn thành workshop và thanh toán đủ; tạo booking, trả cọc hoặc mới check-in chưa được cộng điểm.

### Đặt workshop — Nhánh lỗi

1. Nhập voucher sản phẩm: từ chối mã và hướng dẫn chọn voucher workshop.
2. Gói, chi nhánh, ngày/phiên, thời gian đặt hoặc giá trị tối thiểu không phù hợp: thông báo điều kiện chưa đạt, chưa áp dụng voucher.
3. Voucher hết lượt hoặc điểm không còn đủ trước lúc xác nhận: Backend cập nhật ưu đãi hợp lệ và số điểm tối đa được dùng; Frontend yêu cầu chọn lại, tính lại phí gói/cọc và cho Member xác nhận lại.
4. Booking hết hạn trước khi thanh toán được chấp nhận: giải phóng lượt voucher và điểm đang giữ; không ghi nhận là đã sử dụng.
5. Khi booking được hủy hợp lệ trước khi hoàn thành workshop theo FE-03, xử lí điểm và lượt voucher theo phần **Hoàn điểm và lượt voucher khi hủy workshop** bên dưới. Không đến mà không có kết quả hủy hợp lệ thì không tự hoàn điểm hoặc lượt voucher đã dùng và không cộng điểm mới.
6. Phí gói sau giảm bằng 0 hoặc âm, hoặc nghĩa vụ thanh toán không đáp ứng điều kiện FE-03: từ chối xác nhận, nêu lý do và cho Member điều chỉnh ưu đãi/booking; không tạo booking miễn phí hoặc để lại phần quyền lợi đang giữ dở dang.

## Điểm thưởng

### Phạm vi và cấu hình chính sách

1. Member có một số dư điểm dùng chung: được kiếm điểm từ mua sản phẩm và workshop, rồi dùng điểm để giảm tiền cho một trong hai dịch vụ. Điểm không chia thành ví sản phẩm và ví workshop.
2. Điểm chỉ dùng được sau khi đã cộng thành công; không dùng điểm dự kiến kiếm từ giao dịch để giảm chính giao dịch đó. **Điểm chưa hết hạn.**
3. Bộ thông số khởi đầu đã chốt, dùng chung cho sản phẩm và workshop: mỗi 10.000đ thực trả đủ điều kiện được 1 điểm; mỗi điểm quy đổi thành 100đ giảm giá; dùng tối thiểu 10 điểm/lần; giảm bằng điểm tối đa 10% giá trị sau giảm tự động/voucher, trước khi trừ điểm. Với sản phẩm, cơ sở tính không gồm phí giao hàng; với workshop, cơ sở tính là phí gói của booking, không phải riêng tiền cọc. Admin quản lí các thông số này trong cấu hình; việc có cho kết hợp điểm với ưu đãi hay không lấy từ chương trình tương ứng.
4. Chỉ cộng và dùng điểm nguyên. Điểm được cộng bằng phần nguyên làm tròn xuống của tiền thực trả đủ điều kiện chia 10.000đ, tính riêng từng giao dịch; không cộng dồn phần tiền lẻ chưa đủ đổi thành điểm sang giao dịch sau. Số điểm tối đa được dùng là số nhỏ hơn giữa điểm khả dụng và phần nguyên làm tròn xuống của 10% giá trị sau ưu đãi chia 100đ. Khi được phép kết hợp, Member có thể chọn số điểm nguyên từ 10 đến mức tối đa; nếu mức tối đa dưới 10 thì chưa được dùng điểm. Trần này chỉ giới hạn lượng điểm được dùng, không phải tỷ lệ tích điểm.
5. Backend kiểm tra quyền Manager và tính hợp lệ của cấu hình trước khi lưu. Thay đổi chỉ áp dụng cho giao dịch được chấp nhận sau đó; đơn/booking đã chấp nhận giữ tỷ lệ tích điểm, giá trị quy đổi và quyền lợi đã ghi nhận. Nếu dữ liệu sai hoặc lưu lỗi, yêu cầu sửa/thử lại; không báo đã cập nhật chính sách khi chưa có xác nhận.
6. Luồng dùng điểm tại checkout được mô tả ở phần mua sản phẩm và đặt workshop; việc giữ/chốt điểm được mô tả ở phần Chốt và giải phóng quyền lợi bên dưới.

### Cộng điểm từ giao dịch — Luồng thành công

1. Backend nhận kết quả nghiệp vụ đã được xác minh: với sản phẩm là thanh toán đầy đủ thành công; với workshop là đã hoàn thành và thanh toán đủ. Phiên bản này không có luồng trả hàng/hoàn tiền sản phẩm hoặc hủy workshop đã hoàn thành, nên không có nghiệp vụ thu hồi điểm đã kiếm từ các giao dịch hoàn tất đó.
2. Backend kiểm tra giao dịch thuộc Member và chưa được cộng điểm. Workshop mới tạo booking, mới trả cọc hoặc mới check-in chưa đủ điều kiện; booking hủy trước khi hoàn thành không được cộng điểm.
3. Backend tính điểm trên tiền thực trả sau các khoản giảm và điểm đã dùng, không gồm phí giao hàng. Workshop tính một lần trên phí gói thực trả; không cộng riêng tiền cọc rồi cộng lại toàn bộ phí.
4. Backend dùng tỷ lệ tích điểm đã ghi nhận khi giao dịch được chấp nhận, với bộ thông số khởi đầu là 10.000đ đủ điều kiện được 1 điểm, làm tròn xuống theo từng giao dịch; ghi lịch sử gắn với giao dịch và cập nhật số dư Member. Workshop hoàn thành sau khi Admin đổi tỷ lệ vẫn dùng tỷ lệ đã ghi nhận lúc đặt, không dùng cấu hình mới để tính lại. Chỉ xử lí khi chính sách đã ghi nhận hợp lệ.
5. Khi xử lí lại cùng một kết quả, Backend trả trạng thái đã ghi nhận; không cộng thêm điểm lần nữa.

### Xem điểm và lịch sử — Luồng thành công

1. Member đăng nhập, mở **My Loyalty**.
2. Backend trả số dư điểm, phần đang tạm giữ, điểm khả dụng và lịch sử cộng/dùng/hoàn điểm của chính Member đó.
3. Frontend hiển thị giá trị quy đổi theo chính sách hiện hành; mỗi mục lịch sử cho biết giao dịch liên quan và số điểm thay đổi.
4. Sau khi phát sinh cộng điểm, chốt dùng điểm, giải phóng phần đang giữ hoặc hoàn điểm, Member có thể tải lại để xem số liệu đã được Backend xác nhận.

### Điểm thưởng — Nhánh lỗi

1. Giao dịch chưa đủ điều kiện hoặc workshop mới trả cọc: chưa cộng điểm; không hiển thị điểm dự kiến như điểm khả dụng.
2. Thiếu chính sách hợp lệ đã ghi nhận của giao dịch hoặc lỗi ghi nhận điểm: chưa báo cộng thành công; giữ khả năng xử lí lại giao dịch mà không cộng trùng, không tự lấy cấu hình mới thay thế chính sách bị thiếu.
3. Mất phản hồi khi cộng/trừ điểm: kiểm tra kết quả đã ghi nhận trước khi thử lại, không thực hiện thêm một lần chỉ vì Frontend chưa nhận được kết quả.
4. Phiên đăng nhập hết hạn hoặc tải My Loyalty lỗi: yêu cầu đăng nhập lại hoặc thử tải lại; không hiển thị số dư cũ như số điểm hiện tại đã xác nhận.

## Chốt và giải phóng quyền lợi

### Chốt lượt voucher và điểm — Luồng thành công

1. Khi Member xác nhận đặt hàng/booking, Backend tạm giữ lượt voucher và điểm cần dùng cùng thao tác ghi nhận giao dịch, lưu chính sách và quyền lợi áp dụng. Điểm đang giữ không còn khả dụng cho giao dịch khác. Khi chương trình hoặc cấu hình điểm thay đổi, giao dịch đã được chấp nhận tiếp tục theo chính sách đã ghi nhận trong thời hạn gốc, kể cả khi chương trình bị dừng hoặc hết hạn.
2. Với sản phẩm, khi thanh toán được xác minh thành công, Backend chốt lượt voucher và trừ điểm đang giữ đúng một lần. Đơn 0 đồng hợp lệ được chốt khi Backend xác nhận đơn theo FE-08.
3. Với workshop, khi cọc được xác minh thành công, Backend chốt lượt voucher và trừ điểm đang giữ. Điểm mới chỉ được cộng khi workshop đã hoàn thành và thanh toán đủ.
4. Backend ghi kết quả sử dụng theo giao dịch để xử lí lại an toàn; gửi lại cùng kết quả thanh toán không tiêu dùng thêm lượt hoặc trừ thêm điểm.

### Hết hạn khi chưa thanh toán được chấp nhận

1. Backend xác định đơn/booking đã hết hạn và chưa có kết quả thanh toán thành công được chấp nhận theo luồng nghiệp vụ tương ứng.
2. Backend giải phóng lượt voucher và điểm đang giữ, trả điểm về khả dụng; không ghi nhận đã dùng voucher hoặc đã tiêu điểm.
3. Giải phóng không gia hạn chương trình. Nếu Member đặt lại, Backend kiểm tra điều kiện hiện hành; voucher có thể đã hết hạn hoặc hết lượt.
4. Giải phóng quyền lợi đang giữ và hoàn quyền lợi đã dùng là hai thao tác khác nhau; hủy workshop hợp lệ được xử lí theo phần bên dưới.

### Hoàn điểm và lượt voucher khi hủy workshop — Luồng thành công

1. FE-03 kiểm tra điều kiện và xác nhận kết quả hủy booking hợp lệ trước khi hoàn thành workshop. FE-06 nhận kết quả đã được xác nhận; yêu cầu bấm Hủy trên giao diện chưa phải căn cứ hoàn điểm hoặc lượt voucher. Điều kiện hủy và việc hoàn tiền cọc thuộc FE-03.
2. Backend kiểm tra booking thuộc Member, quyền sử dụng điểm đã ghi nhận và kết quả xử lí trước đó. Nếu điểm mới tạm giữ, giải phóng phần giữ để điểm khả dụng trở lại; không ghi thêm một khoản cộng hoàn điểm.
3. Nếu điểm đã được trừ để giảm phí booking, Backend trả lại đúng số điểm đã trừ cho Member, ghi lịch sử hoàn điểm gắn booking và lần dùng điểm ban đầu. Không tính lại theo tỷ lệ quy đổi hiện tại, không đổi điểm thành tiền hoàn và không cộng điểm kiếm mới cho booking bị hủy.
4. Điểm hoàn trở lại số dư khả dụng và không hết hạn như chính sách điểm hiện tại. Hệ thống không xử lí số dư âm hoặc thu hồi điểm mới đã tiêu ở giao dịch khác, vì booking bị hủy chưa đủ điều kiện kiếm điểm và các giao dịch hoàn tất không có luồng hoàn trong phiên bản này.
5. Với voucher mới tạm giữ, Backend giải phóng lượt đang giữ. Với voucher đã chốt, Backend trả lại đúng lượt sử dụng của Member và lượt trong tổng hạn mức của chương trình, ghi nhận gắn với booking và lần dùng ban đầu. Không gia hạn hoặc kích hoạt lại chương trình; mã đã hết hạn hoặc bị dừng vẫn không dùng được cho giao dịch mới. Việc hoàn lượt độc lập với booking có dùng điểm hay không.
6. Backend ghi nhận kết quả hoàn điểm và lượt voucher đúng một lần cho từng quyền lợi; nhận lại cùng kết quả hủy không trả thêm lần nữa. Nếu booking không dùng điểm hoặc voucher thì không tạo khoản hoàn tương ứng; lượt/điểm mới tạm giữ chỉ được giải phóng, không đồng thời ghi thêm khoản hoàn đã dùng.

### Hoàn điểm và lượt voucher khi hủy workshop — Nhánh lỗi

1. Booking đã hoàn thành, hủy không hợp lệ hoặc chỉ không đến mà chưa có kết quả hủy hợp lệ: không tự hoàn điểm hoặc lượt voucher. Nếu kết quả hủy chưa rõ, chờ FE-03 xác nhận thay vì suy đoán từ trạng thái giao diện.
2. Kết quả xử lí đã cho thấy điểm hoặc lượt voucher được giải phóng/hoàn trước đó: trả trạng thái hiện có cho quyền lợi đó, không trả lần nữa và không vừa giải phóng vừa hoàn cùng quyền lợi.
3. Lỗi lưu hoặc mất phản hồi: chưa báo hoàn thành; kiểm tra kết quả từng quyền lợi đã ghi nhận trước khi xử lí lại để hoàn tất phần còn thiếu, không hoàn trùng phần đã xong. Bảo toàn lịch sử điểm, lượt voucher và trạng thái booking, không sửa giao dịch hoàn tất để tạo khoản hoàn.

### Chốt và giải phóng — Nhánh lỗi

1. Thanh toán và xử lí hết hạn diễn ra gần nhau: Backend kiểm tra trạng thái giao dịch trước khi cập nhật; không vừa chốt sử dụng vừa trả lại cùng lượt voucher/điểm.
2. Kết quả thanh toán chưa rõ hoặc đến muộn: xử lí theo luồng thanh toán của FE-08/FE-03; không tự cộng điểm hoặc chốt ưu đãi chỉ từ thông báo trên trình duyệt.
3. Lỗi lưu hoặc mất phản hồi: kiểm tra trạng thái đã ghi nhận trước khi xử lí lại; không trừ, cộng hoặc giải phóng thêm lần nữa.

<a id="fe-07"></a>

# FE-07 — Notification Management

Nội dung nghiên cứu đã chuyển sang [research riêng của FE-07](features/007-notification-management/research.md). Chưa tạo spec/plan/tasks mới hoặc phân tích thêm feature.

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
