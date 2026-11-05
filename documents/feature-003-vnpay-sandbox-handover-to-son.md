# Bàn giao tích hợp VNPay Sandbox cho Feature 003

**Người bàn giao:** Hồ Hoàng Quốc  
**Người nhận:** Sơn  
**Phạm vi:** Tích hợp thanh toán VNPay cho Workshop Booking, xử lý callback và xác minh QueryDR/refund trên Sandbox.  
**Ngày lập:** 31/10/2026

## Mục đích bàn giao

Sơn tiếp nhận việc điều tra và hoàn thiện tích hợp VNPay Sandbox. Luồng thanh toán và IPN đã có bằng chứng chạy thành công, nhưng các lệnh QueryDR/refund có lần trả `99 / Unexpected error`. Trạng thái refund của giao dịch đang tra soát chưa được xác nhận, vì vậy cần tra request cũ trước khi gửi thêm refund.

## 1. Trạng thái hiện tại

### Đã quan sát được

- Đã tạo payment attempt Workshop và mở trang thanh toán VNPay Sandbox.
- Một giao dịch Sandbox được VNPay ghi nhận thành công.
- IPN có chữ ký hợp lệ đã được nhận; booking `BK-VNP-1030172444` được cập nhật thành `CONFIRMED / DEPOSIT_PAID`.
- Merchant Admin hiển thị giao dịch thành công cho khoản 50.000 VND có transaction number `15720588`.
- Trong DB test, payment attempt nội bộ tương ứng với giao dịch `15720588` ở trạng thái `EXPIRED`. Đây là khoản có khả năng bị thu trùng với giao dịch khác đã xác nhận booking.
- Đã gửi request refund toàn phần cho giao dịch `15720588`; phản hồi nhận được là `vnp_ResponseCode=99`, `vnp_Message=Unexpected error`.

### Chưa xác nhận

- Chưa biết request refund đã được VNPay tiếp nhận hoặc xử lý chưa.
- Chưa xác định nguyên nhân cụ thể của phản hồi `99`.
- Chưa có đủ bằng chứng Sandbox để đánh dấu T005 hoàn thành.

**Không gửi lại refund cho giao dịch này trước khi tra được trạng thái request cũ.** Nếu refund đã được tiếp nhận mà gửi lại, có thể tạo yêu cầu trùng hoặc làm khó đối soát.

## 2. Giao dịch cần tra soát

Booking: `BK-VNP-1030172444`

| Trường                                            | Giá trị                                                      |
| ------------------------------------------------- | ------------------------------------------------------------ |
| Môi trường                                        | VNPay Sandbox                                                |
| TmnCode                                           | `F61WYCBQ`                                                   |
| Merchant reference / `vnp_TxnRef`                 | `DEP-BK-VNP-1030172444-1664c44c-93e4-4870-897e-5d92e0e3eba5` |
| VNPay transaction number gốc                      | `15720588`                                                   |
| Thời điểm tạo payment gốc / `vnp_TransactionDate` | `20261030181359`                                             |
| Số tiền payment gốc                               | 50.000 VND                                                   |
| Loại refund đã yêu cầu                            | Hoàn toàn phần, `vnp_TransactionType=02`                     |
| Số tiền refund trong đơn vị API                   | `5000000`                                                    |
| RequestId refund gần nhất được cung cấp           | `5f2d503922894472bf160227b33db64f`                           |
| Thời điểm tạo request                             | `31/10/2026 16:56:07 GMT+7`                                  |
| Phản hồi                                          | `vnp_ResponseCode=99`, `vnp_Message=Unexpected error`        |

Một payment khác của cùng booking có merchant reference kết thúc bằng `0ae16c8f-1ffb-486e-a1ca-d06dc460864a`, transaction number `15720603`, đã thành công và booking được xác nhận. Cần đối chiếu hai giao dịch trên Merchant Admin và DB; giao dịch `15720588` là khoản cần xác minh/refund vì payment attempt nội bộ của nó đã hết hạn.

## 3. Mã nguồn và luồng cần xem

Các lớp chính trong backend:

- `VnpayMerchantApiSigner` — tạo chữ ký HMAC cho QueryDR/refund.
- `VnpayMerchantOperationsAdapter` — dựng payload QueryDR/refund và điều phối thao tác merchant.
- `RestClientVnpayMerchantApiTransport` — gửi request JSON đến VNPay và đọc phản hồi.
- Luồng payment integration nhận IPN và cập nhật attempt/booking.

Endpoint tạo payment attempt:

```text
POST /api/v1/workshop-bookings/{bookingCode}/payment-attempts
```

IPN endpoint đã đăng ký trong VNPay SIT:

```text
https://affront-copier-barrier.ngrok-free.dev/api/v1/integrations/payments/vnpay/events
```

Ngrok free URL có thể thay đổi sau khi khởi động lại tunnel. Nếu cần thử IPN, hãy so sánh forwarding URL hiện tại với URL đang đăng ký trong SIT trước.

## 4. Những gì đã đối chiếu trong hợp đồng VNPay

- QueryDR/refund dùng endpoint Sandbox `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`.
- Refund toàn phần dùng `vnp_TransactionType=02`.
- Số tiền API tính theo đơn vị nhân 100: 50.000 VND tương ứng `5000000`.
- Refund cần transaction number của payment gốc và `vnp_TransactionDate` lấy theo `vnp_CreateDate` lúc tạo payment URL ban đầu.
- Thứ tự trường trong chuỗi canonical của `VnpayMerchantApiSigner` đã được rà soát và khớp cấu trúc trong tài liệu QueryDR/refund.
- Tài liệu mô tả một số trường là `Numeric`, nhưng chưa làm rõ JSON phải mã hóa các trường đó thành số hay chuỗi số. Transport hiện gửi các giá trị map thành JSON string.
- Cần kiểm tra request thực tế về chuỗi ký, cách serialize JSON, IP outbound và định dạng `vnp_OrderInfo`/`vnp_CreateBy`.
- Mã `99` là lỗi tổng quát, không xác định trường gây lỗi. Cần nhờ VNPay tra log theo RequestId để có kết luận phía provider.

Tài liệu chính thức:

- [Đặc tả API QueryDR và refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html)
- [Bảng mã lỗi VNPay](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/)
- [Liên hệ hỗ trợ kỹ thuật Sandbox](https://sandbox.vnpayment.vn/apis/contact/)

## 5. Các bước Sơn cần thực hiện

1. Tra cứu refund trên Merchant Admin theo transaction gốc `15720588` hoặc merchant reference ở mục 2.
2. Nếu trạng thái chưa rõ, nhờ bộ phận kỹ thuật VNPay tra request theo RequestId `5f2d503922894472bf160227b33db64f` và thời điểm `31/10/2026 16:56:07 GMT+7`.
3. Yêu cầu VNPay xác nhận:
   - Request đã đến hệ thống chưa?
   - Request có được tiếp nhận hoặc xử lý không?
   - Có refund nào đã ghi nhận cho transaction `15720588` không?
   - Nguyên nhân cụ thể của `99 / Unexpected error` là gì?
   - TmnCode/tài khoản Sandbox này có được bật QueryDR và refund API không?
4. Kiểm tra request/response thực tế trong log backend. Ghi HTTP status và response body nguyên gốc, endpoint, RequestId, thời điểm và các giá trị không bí mật. Không log secret hoặc token.
5. Đối chiếu payload theo tài liệu: kiểu dữ liệu JSON, chuỗi ký, GMT+7, `vnp_IpAddr`, `vnp_TransactionNo`, `vnp_TransactionDate`, `vnp_CreateBy` và `vnp_OrderInfo`.
6. Không gửi refund mới cho transaction `15720588` cho đến khi VNPay xác nhận request cũ chưa được tiếp nhận/xử lý.
7. Sau khi tra soát, lưu kết quả đã che dữ liệu nhạy cảm và chạy kiểm chứng phù hợp. Chỉ đánh dấu T005 khi evidence đáp ứng acceptance của task.

## 6. Bằng chứng cần lưu

- Ảnh Merchant Admin thể hiện giao dịch `15720588` và kết quả refund sau khi được tra soát.
- Ảnh hoặc log đã che dữ liệu nhạy cảm của phản hồi API refund `99`.
- Email đã gửi VNPay và câu trả lời của bộ phận kỹ thuật.
- Kết quả đối chiếu booking/payment attempt trong DB test.
- Kết quả test code sau khi sửa, gồm tên test/lệnh và kết quả thật.

## 7. Bảo mật và phạm vi môi trường

- Chỉ thao tác với Sandbox và dữ liệu test.
- Không đưa `VNPAY_HASH_SECRET`, mật khẩu Merchant Admin/SIT, auth token hoặc giá trị `vnp_SecureHash` vào email, tài liệu bàn giao, ảnh chụp hay log chia sẻ.
- Nếu Sơn cần chạy tích hợp, chia sẻ cấu hình bí mật qua kênh bảo mật riêng; không commit bí mật vào repository.
- Không xóa/reset database hoặc thay đổi dữ liệu giao dịch hiện có chỉ để làm sạch kết quả test.
