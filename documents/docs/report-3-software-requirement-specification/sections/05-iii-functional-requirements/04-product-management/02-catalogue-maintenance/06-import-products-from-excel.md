#### 4.2.6 Import Products from Excel

![](assets/screens/87-import-products-from-excel.png)

This screen allows Staff and Managers to:

- Select the branches that receive the imported stock.
- Download the Excel template generated for those branches, including the list of active categories.
- Upload a filled .xlsx file and validate it before anything is created.
- Review row-level errors, download an error report, or preview the products and sizes that will be created.
- Confirm the import to create the products as unpublished drafts, then attach images to them.

| Field Name | Description |
| --- | --- |
| Receiving branches (Chọn chi nhánh nhận hàng) | Checkboxes; the template has one quantity column per selected branch, and an empty cell means 0. |
| 1. Tải file mẫu | Downloads the template; it is enabled after at least one branch is selected. The Size column accepts one size, a range, a list or a combination; rows sharing a group code form one product. |
| 2. Chọn file và kiểm tra | **Chọn file .xlsx** and **Kiểm tra file** validate the file on the first sheet without creating data. |
| 3. Validation result (Kết quả kiểm tra) | State Hợp lệ, Có lỗi, Đã import or Hết hạn; an error table lists Dòng, Cột, Giá trị and Lỗi, with **Tải báo cáo lỗi** and **Kiểm tra file khác**. A same-name warning marks products that match existing names. |
| Xác nhận import | Requires confirmation of the number of products and sizes, then creates them as unpublished drafts with proposed prices. |
| Import result | Links to each created product's management details and **Import file khác**. |
| Attach images (Gắn ảnh cho các sản phẩm vừa import) | Select several images, assign each to a created product with alternative text, then **Lưu ảnh**. |
