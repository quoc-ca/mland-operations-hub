#### 3.4.1 Workshop Packages

{{r3-workshop-packages width=100% align=center}}

This screen allows Managers to:

- See all workshop packages with their state, price, ticket range and required sessions.
- Create a package as a draft and publish it when it is ready.
- Edit a package that is not retired; a change applies only to bookings made afterwards.
- Retire a published package, which stops new bookings and keeps existing bookings unchanged.

Staff can open the page, but creating, editing, publishing and retiring a package are Manager commands checked by the server.

| Field Name | Description |
| --- | --- |
| Tạo gói | Opens an empty form to create a draft package. |
| Package card | State ("Bản nháp", "Đang bán" or "Ngừng bán"), name, package id, description, Giá, Vé (min–max) and Ca. |
| Sửa / Ngừng bán | **Sửa** on any package that is not retired; **Ngừng bán** on a published package, after confirming "Ngừng bán gói?". |
| Tên / Mô tả hiển thị cho khách | Required name and customer-facing description. |
| Giá công khai (VND) | Public price per participant; must be greater than 0. |
| Số vé tối thiểu / Số vé tối đa | Ticket range per booking; the maximum must be at least the minimum. |
| Số ca bắt buộc | Number of sessions a booking must choose; at least one. |
| Khoảng cách tối thiểu giữa các khung giờ trong ngày / Số ngày làm việc của xưởng giữa các ca | Minimum slot gap between sessions on the same day and foundry working days required between sessions; non-negative whole numbers. |
| Yêu cầu cùng một chi nhánh | Whether all sessions of a booking must be at one branch. |
| Phát hành bản nháp | Shown for a draft being edited; publishes it so customers can find the package. |
| Tạo bản nháp / Lưu thay đổi / Hủy | Creates the draft, saves the edit, or closes the form. |
