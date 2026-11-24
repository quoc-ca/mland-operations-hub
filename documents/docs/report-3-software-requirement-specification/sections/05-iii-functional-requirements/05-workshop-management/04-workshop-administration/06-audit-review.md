#### 5.4.6 Audit Review

This screen allows Managers to:

- Read the audit log of workshop operations and of workshop settlement in two tabs.
- Filter each tab, with the filters kept in the page URL.
- Expand a row to see its permitted details and full reason.
- Load older rows page by page.

The screen is read-only, newest rows first, and viewing the log is not itself recorded. Without a "Từ" date it shows the last 7 days.

| Field Name | Description |
| --- | --- |
| Vận hành / Quyết toán | Tabs for operations events and settlement records; each tab has its own filters. |
| Operations filters | Hành động, Kết quả, Người thực hiện (ID), Ca (ID), Từ and Đến; "Từ" must not be after "Đến". |
| Settlement filters | Mã hành động, Loại đối tượng, Mã đối tượng, Người thực hiện (ID), Từ and Đến. |
| Bộ lọc / Lọc / Xóa bộ lọc | Shows the number of active filters, applies them, or clears them. |
| Operations columns | Thời gian, Người thực hiện ("Hệ thống" for system actions), Hành động, Kết quả, Ca, Mã tương quan and Chi tiết. |
| Settlement columns | Thời gian, Người thực hiện, Mã hành động, Đối tượng, Tham chiếu chứng từ and Lý do. |
| Kết quả | "Thành công", "Thất bại", "Bị từ chối" or "Đã gửi". |
| Details | Only permitted keys, with no customer email, phone or name; the reason is shown in full (up to 500 characters). |
| Tải thêm | Loads the next page of older rows. |
| Empty state | "Không có dòng audit nào khớp bộ lọc" with **Xóa bộ lọc**. |
