# Kế hoạch phần việc của Quốc — 27/11/2026

Nguồn: khảo sát code nhánh `quoc` (chỉ đọc, chưa chạy ứng dụng hay test).
Cỡ việc: S ≤ 0,5 ngày · M 1–2 ngày · L 3 ngày trở lên (ước lượng).

## 0. Quyết định và câu hỏi mở

| Mục | Trạng thái |
| --- | --- |
| Quét vé (K) | **Đã chốt 27/11: webcam trên trang quản lý ca** (không làm trang quét trên điện thoại) |
| Tự cập nhật định kỳ (#17) | **Đã chốt 27/11: bỏ** |
| QR thật trên web và trong email (#15) | **Đã chốt 27/11: làm cả hai** |
| Đổi tên tab, đưa "Yêu cầu đổi vé" lên trước (#2, #3) | **Đã chốt 27/11** |
| Họ tên người yêu cầu và bỏ trường debug (#4, #5) | **Đã chốt 27/11** |
| Từ chối bắt buộc lý do, duyệt không cần (#6) | **Đã chốt 27/11** |
| Quyết toán tiền mặt (#9) | **Đã chốt 27/11: hiện sẵn số dự kiến và danh sách khoản thu**, không sửa spec |
| Tự đóng ca (#7) | **Đã chốt 27/11: sau giờ kết thúc 2 giờ**, có cờ bật/tắt, rà ca treo trước khi bật |
| Hoàn tác sau khi hết ca (#14) | **Đã chốt 27/11: Manager và Staff được phân ca** (đúng chi nhánh), trong 24 giờ sau khi đóng ca, bắt buộc lý do, ghi nhật ký |
| Màu nền riêng cho từng trạng thái ca (#8) | **Đã chốt 27/11** |
| Sắp xếp danh sách ca và tab trạng thái (#10, #11) | **Đã chốt 27/11**; tab "Đã kết thúc" mặc định lùi 7 ngày |
| Check-in bằng cách bấm vào vé (#12) | **Đã chốt 27/11** |
| Chốt sổ tự điền chi nhánh (#13) | **Đã chốt 27/11** |
| Ảnh đại diện gói workshop (#1) | **Đã chốt 27/11**: một ảnh mỗi gói, không bắt buộc; cấu hình Cloudinary đọc từ `.env.staging` |
| Staff hoàn tác check-in khi ca đang diễn ra | **Đã chốt 27/11: Manager và Staff được phân ca**, vé về "Chưa check-in", bắt buộc lý do, ghi nhật ký |

## 1. Việc cần sửa spec trước

| Yêu cầu hiện tại | Mâu thuẫn | Sửa thành |
| --- | --- | --- |
| 003.2 FR-006: finalize **MUST NOT** tự động lúc hết giờ | Tự đóng ca | Cho phép hệ thống finalize sau thời gian ân hạn, ghi audit actor hệ thống |
| 003.2 FR-002b, FR-008: chỉ Manager đảo/sửa điểm danh | Staff hoàn tác | Thêm Staff trong phạm vi, giữ cửa sổ 24 giờ |
| 003.1 FR-014b: QR là token "signed opaque" | Code dùng `QR-<UUID>` không ký | Ghi rõ chấp nhận token ngẫu nhiên không ký |
| 003.3: lý do duyệt đổi vé (chưa đối chiếu FR) | Duyệt không cần lý do | Chỉ bắt buộc khi từ chối |

## 2. Nội dung và cách làm

### A. Ảnh đại diện gói workshop — M
Hiện không có trường ảnh nào; khách thấy hình CSS giả.

1. Migration `V207__workshop_package_image.sql` (kiểm tra số trên nhánh khác): thêm cột `image_media_id` nullable.
2. BE: thêm vào `WorkshopPackage`, `PackageRequest`, `PackageResponse`. Tái dùng `MediaStoragePort` (Cloudinary) và `MediaReferenceService.attachOwnedMedia/release`.
3. `PublicMediaService` đang gắn với ảnh sản phẩm: mở rộng để phục vụ ảnh gói. Kiểm tra Manager có quyền `MEDIA_MANAGE`.
4. FE quản lý: ô tải ảnh + xem trước trong `WorkshopPackageManagementPage.jsx` (jpg/png/webp, ≤ 5 MB).
5. FE khách: `WorkshopListPage`, `WorkshopDetailPage`, thẻ gói ở `HomePage`. Không có ảnh thì giữ hình cũ.

Biến `CLOUDINARY_*` đọc từ file `.env.staging` trong thư mục Downloads của Quốc (ngoài repo). Không chép giá trị vào tài liệu, code hay commit.

### B. Tab đổi vé và hủy — S
- `shared/nav/navModel.js:101-111`: đưa `change-requests` lên trước `ticket-actions`; đổi nhãn thành **"Hỗ trợ đổi vé và hủy"** (EN: "Change & cancellation support").
- Đổi luôn tiêu đề trang trong `messages.ticket.js`; sửa thứ tự trong `OperationsShell.test.jsx`.

### C. Người yêu cầu và trường debug — S/M
- Hiện "Hội viên · ONLINE" vì BE không trả tên.
- BE: thêm `requesterName` vào `ChangeRequestDto`, lấy qua `AccountFacade.findAccountFullName` trong `TicketChangeRequestService.toDto`.
- Hiển thị: hội viên → họ tên; nhân viên tạo hộ → "Họ tên (Nhân viên)"; khách không tài khoản → "Khách vãng lai".
- Bỏ: mã yêu cầu và tooltip, `appliedChangeReference`, tooltip/UUID của ca, kênh thô.
- Dịch mã `blockedRules` bằng khóa `picker.rule.*` có sẵn. Bỏ dòng topline kỹ thuật ở `OpsPage.jsx:13`.
- Giữ mã vé để nhân viên đối chiếu.

### D. Lý do khi duyệt/từ chối — S
- BE: bỏ `@NotBlank` trên `reason`; service chỉ bắt buộc khi `REJECT`.
- Khi duyệt trống lý do, dùng chuỗi mặc định cho bản ghi cấp thêm lượt đổi (`applyApproved`).
- FE: nút Duyệt mở hộp xác nhận thường (không màu đỏ); nút Từ chối giữ ô lý do bắt buộc. Không làm đổi `ReasonDialog` ở màn khác.
- Cột DB đã nullable, không cần migration.

### E. Tự đóng ca và màu trạng thái — M
Quy tắc đề xuất: ca `IN_PROGRESS` và `now ≥ scheduledEndAt + 2 giờ` → hệ thống finalize.

1. Scheduler mới theo mẫu `WorkshopSessionStartScheduler` (mỗi ca một transaction).
2. Thêm đường gọi "hệ thống" vào `AttendanceFinalizationService` (hiện đòi actor); audit `SESSION_AUTO_FINALIZED`. Kiểm tra cột actor có cho null không.
3. Vé chưa check-in thành `NO_SHOW`; vẫn sửa được trong 24 giờ.
4. Bỏ qua ca đang yêu cầu hủy/đã hủy.
5. **Rủi ro:** lần deploy đầu sẽ đóng mọi ca cũ còn treo. Sau 24 giờ kết quả đó đi sang quyết toán, đóng hóa đơn vé no-show. Rà danh sách ca treo trước, có cờ bật/tắt.

Màu (áp cho cả danh sách và trang chi tiết): Nháp xám · Mở bán xanh dương · Tạm dừng bán vàng · Đang diễn ra xanh lá · Đã kết thúc tím nhạt · Chờ hủy cam · Đã hủy đỏ.

### F. Quyết toán tiền mặt — S (đã chốt)
Đã có sẵn: Staff xác nhận thu tiền mặt trên hóa đơn vé thì khoản đó tự cộng vào số dự kiến của chốt sổ (chi nhánh của vé, ngày thu; sau khi chốt thì sang ngày sau). Tiền mặt bán lẻ không cộng.

Làm thêm, không sửa spec; cần API trả danh sách khoản thu:
1. Mở tab chốt sổ là hiện sẵn số dự kiến.
2. Thêm danh sách khoản thu tiền mặt tạo nên số đó.
3. Nhân viên chỉ nhập số đếm thực tế và lý do chênh lệch.

### G. Sắp xếp và tab trạng thái — S
- Tab thay ô chọn: Tất cả · Đang diễn ra · Sắp diễn ra · Đã kết thúc · Đã hủy · Nháp (chỉ hiện khi có ca nháp), kèm số lượng. Sắp diễn ra = Mở bán + Tạm dừng bán.
- Sắp xếp trên FE trước khi phân trang: đang diễn ra (sắp hết giờ trước) → sắp diễn ra (gần nhất trước) → đã kết thúc (mới nhất trước) → đã hủy, nháp.
- Bộ lọc "từ ngày" mặc định hôm nay: ở tab "Đã kết thúc" mặc định lùi 7 ngày, vẫn đổi được.

### H. Check-in bằng cách bấm vào vé — S/M
- Hàng trong danh sách vé (`OperationsSessionPage.jsx:507-551`) bấm được.
- Chưa check-in → hộp xác nhận → gọi API check-in bằng mã vé. Đã check-in → xem giờ, hoàn tác. Vắng → sửa điểm danh.
- Giữ ô nhập mã vé làm dự phòng. Ngoài giờ mở check-in thì báo rõ lý do.

### I. Hoàn tác sau khi hết ca — M (đã chốt)
- BE: `AttendanceCorrectionService` cho Staff được phân ca ở cả hai luồng: đảo check-in khi ca đang diễn ra và sửa sau khi đóng ca (24 giờ); giữ lý do bắt buộc, audit.
- FE: nút hoàn tác trên từng vé, chỉ hiện khi được phép; quá hạn thì ghi rõ.
- Sửa lỗi liên quan (suy từ code, chưa chạy): khối ngoại lệ hiện nút Manager cho Staff, bấm vào có thể bị đẩy về trang chủ.
- Sau finalize chỉ đổi được giữa "Đã check-in" và "Vắng".

### J. Chốt sổ: tự điền chi nhánh — S
- `CashClosePage.jsx`: thay ô gõ ID bằng danh sách tên từ `GET /api/v1/workshop-locations` (chỉ trả chi nhánh trong phạm vi của Staff).
- Một chi nhánh thì chọn sẵn; nhiều thì chọn cái đầu, cho đổi.
- Sửa lỗi: đang đổi được chi nhánh sau khi đã xem số dự kiến. Đổi thì phải xóa số cũ.
- Bảng chờ duyệt hiện tên chi nhánh thay ID.

### K. QR thật và quét bằng webcam — M
Backend check-in bằng `qrToken` đã có.

| Bước | Cách làm |
| --- | --- |
| K1. Vé trên web | `GroupTicketList.jsx`: thay chuỗi mã bằng `qrcode.react` (đã cài), bấm để phóng to |
| K2. Vé trong email | Thêm ZXing ở BE; `MailMessage` hỗ trợ ảnh nhúng CID. Mail dạng `captured` chỉ lưu chữ |
| K3. Quét | Nút "Quét bằng camera" trên `OperationsSessionPage.jsx`, thư viện `@zxing/browser`, gọi `runCheckIn({ qrToken })`. Kết quả cỡ lớn, tiếng bíp, chống quét lặp vài giây, đóng khung thì tắt camera |
| K4. Không làm trắng trang | Sửa `useResource` để tải lại không unmount khung camera |
| K5. Bỏ cách cũ | Xóa ô "hoặc mã QR check-in"; giữ ô mã vé |

Thử ngày 27/11: webcam máy local, Edge, `localhost`, đọc QR từ điện thoại 4/4 lần khớp. Chưa thử check-in thật, khoảng cách, ánh sáng yếu.

Camera chỉ chạy trên `localhost` hoặc HTTPS. Khung trình duyệt trong app Claude chặn camera.

## 3. Thứ tự đề xuất
nTrạng thái 27/11: **đợt 1 đã code xong** (#2–6, #8, #10, #11, #13), chưa commit, chưa xem trên trình duyệt.

| Đợt | Mục | Lý do |
| --- | --- | --- |
| 1 | B, C, D, J, G, màu của E | Nhỏ, ít phụ thuộc |
| 2 | K, H | Giá trị demo cao nhất, ít đụng spec |
| 3 | E (tự đóng), I | Làm cùng nhau: tự đóng ca cần đường hoàn tác |
| 4 | A | Độc lập, có migration |
| 5 | F | Nhỏ |

Mỗi đợt: cập nhật spec bằng skill speckit (kiểm tra `.specify/feature.json` trước), đọc `AGENT.md` và `ERROR-LOG.md` tương ứng, chạy test hẹp, commit lên `quoc`. Khảo sát không thấy blocker thiết kế còn mở chặn trực tiếp các mục này.
