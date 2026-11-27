#### 6.3.2 Design Review Details

This screen allows Staff and Managers to:

- Inspect a design request: reference, version, request and review status, owner type, description, catalogue reference and reference images; when a saved design snapshot is present, open its visual design preview and technical specifications.
- Record a decision of accept, reject or request clarification, after a confirmation dialog. A written explanation of 1 to 2,000 characters is required for rejection or clarification and optional for acceptance.
- For an accepted request, prepare the full custom quote (total amount, payment deadline in hours and pricing note) in the same submission; the request then enters `QUOTE_PENDING` until a Manager approves it.
- As a Manager, approve a quote prepared by Staff so that the Member can pay it in full from My Custom Orders.

A request that is accepted or rejected is final and the decision form is hidden. Clarification keeps the request open for the customer's reply. A reviewer cannot review their own request. Accepting a request does not create a payment or reserve production.

| Field Name | Description |
| --- | --- |
| Header | "Thẩm duyệt Yêu cầu Thiết kế #{id}", reference, design version, request status, review status and the Member owner projection. |
| Final decision card | "Quyết định: Chấp thuận (ACCEPTED)" or "Từ chối (REJECTED)" with the reviewer's explanation. |
| Description and images | Submitted idea, catalogue reference and image gallery; an image can be enlarged. |
| **Xem chi tiết thiết kế 3D & Thông số** | Opens a read-only visual preview of the saved ring-design snapshot with material, shape, size, dimensions, gemstones and laser-engraving details when the snapshot is available. |
| Quyết định | Choice of "Chấp thuận (ACCEPTED)", "Từ chối (REJECTED)" or "Yêu cầu làm rõ thông tin (CLARIFICATION_REQUIRED)". |
| "Lý do / Hướng dẫn phản hồi cho hội viên" | Optional note for acceptance and required explanation for rejection or clarification, at most 2,000 characters with a counter. |
| **Duyệt gửi báo giá** / decision buttons | For acceptance, validates the quote amount and 24–72 hour payment deadline, then records the decision and prepares the quote together. Rejection and clarification keep their required explanation. |
| "Báo giá Gia công Tùy chỉnh (Custom Quote)" | Shown for accepted requests: "Tổng tiền báo giá (VNĐ)" (required, 1 to 1,000,000,000), "Thời hạn thanh toán (24 - 72 Giờ)" (default 72) and a pricing note. |
| **Duyệt gửi báo giá** | Sends the accepted decision and quote after confirmation; the section then shows the quote code, amount, expiry, note and "Chờ Quản lý (Manager) duyệt" or "Quản lý đã duyệt". |
| **Phê duyệt Báo giá (Manager Approve)** | Manager only, while the quote is not approved; approves it after confirmation. |
