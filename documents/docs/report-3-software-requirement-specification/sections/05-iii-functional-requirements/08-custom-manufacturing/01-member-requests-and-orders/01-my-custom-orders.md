#### 8.1.1 My Custom Orders

> **Terminology update (2026-11-22):** The navigation label is **Đơn hàng cá
> nhân**. Quote history uses **Phiên bản báo giá**. The list may show the
> owner-provided Feature-012 design snapshot without exposing internal IDs.

![](assets/screens/28-my-custom-orders.png)

This screen allows Members to:

- Browse two separate views: requests/quotes and custom orders.
- Browse only the Member's own custom requests, quotes and orders.
- See whether a quote is awaiting full payment, paid, expired or cancelled.
- Open a selected custom order's details.
- Start a new custom-manufacturing request.

The screen must not present an Add to Cart action. A ready quote is paid from
the selected custom-order detail page as one complete payment. Expired quotes
remain visible as history and may expose a request-new-quote action when the
business policy permits it.

| Field Name | Description |
| --- | --- |
| Content / business-state panel | Placeholder for the Member's own request or order data; actual fields depend on this screen's purpose. |
| Information | Generic illustrative input; it does not define an approved data format or business update. |
| Continue | Allows only an authorized action on the owning Member's request/order after the applicable checks. |
