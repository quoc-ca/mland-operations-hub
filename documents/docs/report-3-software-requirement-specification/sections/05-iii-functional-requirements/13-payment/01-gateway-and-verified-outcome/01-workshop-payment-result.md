#### 13.1.1 Workshop Payment Result

This screen allows Members and Guests to:

- Land here after VNPay at /workshop-booking/{groupCode}/payment-return and see the gateway response code and what it means; the booking group is confirmed only after the system receives and verifies the signed VNPay result (IPN), never from the browser return.
- As a signed-in Member, see their own booking group straight away; as a Guest, enter the email or phone number used for the booking and press Xem trạng thái nhóm.
- While the group is still held, see the time left to pay, pay again or cancel the hold; once the hold ends, see that the system is confirming the result.
- After a successful payment, see Thanh toán thành công and the group's tickets, which are also sent by email.
- Read an expired, cancelled or not-completed outcome, and rebook the same workshop after an expiry.

The page re-reads the group a few times while VNPay is confirming, and every 20 seconds while the tab is visible. A signed-in Staff or Manager sees a note to return to the settlement invoice instead of the lookup form.

| Field Name | Description |
| --- | --- |
| Title | Kết quả chuyển hướng thanh toán; Tiếp tục thanh toán when opened from the header notice to resume a payment, in which case no outcome box is shown. |
| Mã phản hồi cổng | VNPay response code from the return, or N/A; hidden once the group is confirmed. |
| Outcome | Thanh toán thành công, Cổng thanh toán báo thành công (waiting for verified IPN), Thanh toán chưa hoàn tất, Cổng thanh toán trả mã {code}, Thời hạn thanh toán đã kết thúc, Không nhận được phản hồi từ cổng, or a lookup or network error that asks not to create another payment. |
| Mã đặt chỗ | Booking group code from the address. |
| Guest lookup | Choice of Số điện thoại or Email, the value field and Xem trạng thái nhóm; an empty or invalid value shows a validation message. |
| Thời hạn thanh toán còn | Countdown while the group is pending payment; after it ends, a message that the result is being confirmed. |
| Group details | Group code, status, ticket count and amount paid at booking, package lines with ticket count and price per ticket, and Vé của nhóm. |
| Hold actions | Thanh toán {amount} qua VNPay and Hủy giữ chỗ (with a reason) while the group is held; Hủy giữ chỗ is replaced by a note while a payment attempt is live. |
| Confirmed actions | Gửi lại email xác nhận, and per-ticket recipient change, cancel and change for a confirmed group. |
| Expired / cancelled notice | States that the group's holds were returned; an expired group offers Đặt lại workshop. |
