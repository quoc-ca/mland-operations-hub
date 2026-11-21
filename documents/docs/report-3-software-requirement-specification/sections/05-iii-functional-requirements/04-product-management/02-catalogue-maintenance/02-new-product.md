#### 4.2.2 New Product

![](assets/screens/48-new-product.png)

This screen allows Staff and Managers to:

- Enter the product name, active category and description, with a warning when products with the same name already exist.
- Select the branches that sell the product.
- Choose sizes individually or as a range, and enter a price and a quantity per branch for each size.
- Add up to 10 images with alternative text and order them.
- As Staff, create an unpublished draft whose prices remain proposals for Manager review.
- As a Manager, create the product with its prices applied immediately, either unpublished or published at once.

| Field Name | Description |
| --- | --- |
| Product name (Tên sản phẩm) | Required text input. |
| Category (Danh mục) | Required selection of an active category; when none exists, a link opens Product Categories. |
| Description (Mô tả) | Required multi-line text. |
| Selling branches (Chi nhánh bán) | Checkboxes for branches; the price is shared by all branches while quantities are entered per branch. |
| Sizes (Size, giá đề xuất và số lượng) | Size buttons plus Từ size / Đến size, **Chọn khoảng**, **Chọn tất cả** and **Bỏ chọn**. The size list is fixed once the product is created. |
| Price per size | "Giá đề xuất (₫)" for Staff; "Giá bán (₫)" for a Manager, applied on creation without a review step. |
| Quantity per branch | Nonnegative integer quantity of each size at each selected branch. |
| Images (Ảnh) | **Thêm ảnh (n/10)**, alternative text, move up/down and **Bỏ ảnh**. |
| After creation (Sau khi tạo) | Manager only: "Chưa công khai" or "Công khai ngay"; publishing requires at least one image. |
| Tạo sản phẩm / Hủy | Creates the product and opens its management details, or abandons the form. |
