#### 6.3.2 Design Review Details

{{r3-06-design-review-details-1 width=100% align=center}}

{{r3-06-design-review-details-2 width=100% align=center}}

**Description:** Displays one design request's full details and allows Staff and Managers to record a review decision with an explanation, prepare a custom quote for accepted requests, and (for Managers) approve quotes.

::: {.docx-table width="100%" columns="50%,50%"}

| Field Name | Description |
| --- | --- |
| Header | Displays request ID, reference code, version, request/review status, and Member owner info. |
| Final decision card | "Quyết định: Chấp thuận (ACCEPTED)" or "Từ chối (REJECTED)" with reviewer's explanation. |
| Description and images | Submitted idea, catalogue reference, and enlargeable reference images. |
| **Xem chi tiết thiết kế & Thông số** | Opens a read-only visual preview of the saved ring-design snapshot with material, shape, size, dimensions, gemstones, and laser engraving details when available. |
| Quyết định | Choice of Accepted, Rejected, or Clarification required (hidden once finalized). |
| Lý do / Hướng dẫn phản hồi | Optional note for acceptance, required explanation (1–2,000 characters) for rejection or clarification. |
| **Duyệt gửi báo giá** / Báo giá Gia công Tùy chỉnh | For accepted requests: sets total amount (VNĐ, 1 to 1,000,000,000), payment deadline (24–72 hours), and pricing note, then records the decision and prepares quote together (`QUOTE_PENDING`). |
| **Phê duyệt Báo giá (Manager Approve)** | Manager only; approves a Staff-prepared quote after confirmation, advancing the order to `FULL_PAYMENT_PENDING` (`QUOTE_READY`). |

:::
