#### 11.1.2 Price AI Suggestions

This screen allows Managers to:

- Open the AI price advice for one price proposal at `/price-proposals/{proposalId}/ai-suggestions`.
- Request an AI price suggestion when none exists, or request a fresh one.
- Read the suggested price and the AI's pricing rationale, marked "Cố vấn nội bộ" (internal advisory).
- Record their own decision: accept the AI price, adjust it, reject it or set the price fully by hand.

The suggestion is advisory only: it does not change any catalogue price, and an official listed price must still go through catalogue management. Only a Manager may request a suggestion or record a decision. An Admin sees a technical audit view with prices and decisions hidden; Staff, Members and Guests are refused. This catalogue-pricing capability is separate from Feature-004 human Design Request review.

| Field Name | Description |
| --- | --- |
| Header | "Tư vấn Giá AI cho Đề xuất Mới" with the proposal ID and target type. |
| Yêu cầu AI gợi ý giá | Shown when the proposal has no suggestion yet ("Chưa có gợi ý giá AI cho đề xuất này."); a refresh button of the same name appears under an existing suggestion. |
| Suggested price | "Giá đề xuất bởi AI: {price}" in VND. |
| Pricing rationale | "Cơ sở phân tích giá: {text}" when the AI returns an explanation. |
| Quyết định giá | ACCEPTED, ADJUSTED, REJECTED or MANUAL_FALLBACK. |
| Giá chốt cuối cùng (₫) | Shown for ACCEPTED (empty means the AI price) and ADJUSTED (required, greater than 0). The server also requires a final price for MANUAL_FALLBACK, but the form does not show this field for that choice. |
| Ghi chú quyết định | Optional note or reason for the decision. |
| Lưu quyết định giá | Saves the decision; the card then shows the recorded decision, final price and time instead of the form. |
| Catalogue note | States that the decision is an internal basis only and that listing goes through catalogue management. |
| Admin audit view | Suggestion ID, status, provider correlation ID, and created and decided times; commercial data is hidden. |
