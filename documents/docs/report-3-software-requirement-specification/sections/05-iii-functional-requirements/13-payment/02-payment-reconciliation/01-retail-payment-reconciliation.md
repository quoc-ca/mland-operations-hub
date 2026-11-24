#### 13.2.1 Retail Payment Reconciliation

This screen allows Managers to:

- View, read-only, the retail VNPay payments that need manual handling: payments whose result could not be determined, and payments that arrived late or after the order was no longer valid (e.g. after it was cancelled or expired).
- Search the current page by checkout, payment reference or VNPay transaction code, and filter it by reason.
- Switch to Tiền mặt theo ngày to see the retail cash that Staff confirmed at the counter for one branch and one day in Vietnam time.
- Refresh either list; the page settles, refunds or releases nothing.

Only a Manager can open this page (/manager/retail-payment-reconciliations); the server limits the data to the Manager's permitted branches.

| Field Name | Description |
| --- | --- |
| Tabs | Đối soát VNPay (default) and Tiền mặt theo ngày. |
| Tìm mã đơn hoặc giao dịch / Lý do | Text search and reason filter (Tất cả lý do, Chưa rõ kết quả thanh toán, Thanh toán muộn hoặc đơn không hợp lệ); both apply only to the page shown. |
| VNPay table | Checkout, Mã thanh toán, Mã VNPay (Chưa có when none), Số tiền, Lý do and Ghi nhận lúc. |
| Empty messages | Chưa có giao dịch cần đối soát trên trang này, or Không có giao dịch phù hợp với bộ lọc trên trang này. |
| Trang trước / Trang tiếp | Moves between pages of 50 records. |
| Chi nhánh / Ngày | Branch picker and a business date no later than today. |
| Daily cash total | Number of collections and the total applied amount for the branch and day; workshop cash and the till count are not included. |
| Cash table | Thời gian, Đơn, Số tiền, Khách đưa, Tiền thối and Staff for each collection; Chưa có khoản tiền mặt nào trong ngày này when empty. |
| Làm mới | Reloads the current list. |
