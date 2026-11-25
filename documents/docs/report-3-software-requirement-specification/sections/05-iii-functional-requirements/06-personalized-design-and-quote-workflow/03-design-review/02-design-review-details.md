#### 6.3.2 Design Review Details

This screen allows Staff and Managers to:

- Inspect a design request: reference, version, request and review status, owner type, description, catalogue reference and reference images.
- Record a decision of accept, reject or request clarification, always with a written explanation of 1 to 2,000 characters, after a confirmation dialog.
- For an accepted request, prepare the full custom quote (total amount, payment deadline in hours and pricing note); a quote prepared by Staff waits for a Manager, while a quote prepared by a Manager is approved at once.
- As a Manager, approve a quote prepared by Staff so that the Member can pay it in full from My Custom Orders.

A request that is accepted or rejected is final and the decision form is hidden. Clarification keeps the request open for the customer's reply. A reviewer cannot review their own request. Accepting a request does not create a payment or reserve production.

| Field Name | Description |
| --- | --- |
| Header | "Thẩm duyệt Yêu cầu Thiết kế #{id}", reference, design version, request status, review status and "Hội viên" ("Khách vãng lai" only on older records). |
| Final decision card | "Quyết định: Chấp thuận (ACCEPTED)" or "Từ chối (REJECTED)" with the reviewer's explanation. |
| Description and images | Submitted idea, catalogue reference and image gallery; an image can be enlarged. |
| Quyết định | Choice of "Chấp thuận (ACCEPTED)", "Từ chối (REJECTED)" or "Yêu cầu làm rõ thông tin (CLARIFICATION_REQUIRED)". |
| "Lý do / Hướng dẫn phản hồi cho hội viên" | Required explanation shown to the customer, at most 2,000 characters with a counter. |
| **Gửi quyết định** | Opens the confirmation dialog; disabled while the explanation is empty. |
| "Báo giá Gia công Tùy chỉnh (Custom Quote)" | Shown for accepted requests: "Tổng tiền báo giá (VNĐ)" (required, 1 to 1,000,000,000), "Thời hạn thanh toán (24 - 72 Giờ)" (default 72) and a pricing note. |
| **Gửi báo giá cho Manager duyệt** | Sends the quote after confirmation; the section then shows the quote code, amount, expiry, note and "Chờ Quản lý (Manager) duyệt" or "Quản lý đã duyệt". |
| **Phê duyệt Báo giá (Manager Approve)** | Manager only, while the quote is not approved; approves it after confirmation. |
