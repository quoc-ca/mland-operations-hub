#### 3.2.6 Import Products from Excel

![](assets/screens/live/52-import-products-from-excel.png)

This screen allows Staff and Managers to:

- Select the branches that receive the imported stock.
- Download the Excel template generated for those branches, including the list of active categories.
- Upload a filled .xlsx file and validate it before anything is created.
- Review row-level errors, download an error report, or preview the products and sizes that will be created.
- Confirm the import to create the products as unpublished drafts, then attach images to them.

The page opens from **Bulk import** on Product Management List. Staff can choose only their assigned branches. A file may hold at most 500 data rows.

**Field Description**

| Field Name | Description |
| --- | --- |
| Choose the receiving branches | Checkboxes, none ticked by default unless only one branch is available. The template has one quantity column per selected branch, named after the branch; an empty cell means 0. |
| 1. Download the template | **Download template** is enabled after at least one branch is selected. The Size column accepts one size (12), a range (12-20), a list (12,14,16) or a combination; rows sharing a group code form one product. |
| 2. Choose and check a file | **Choose .xlsx file** (only .xlsx, at most 5,000,000 bytes) and **Check file** ("Checking…") validate the file on the first sheet without creating data. |
| 3. Check result | State Valid, Has errors, Imported or Expired, with the file name and the number of rows, products and sizes. |
| Row errors | When the file has errors: a table of Row, Column, Value and Problem, with **Download error report** and **Check another file**. |
| Preview | When the file is valid: "This check is valid until {time}" (60 minutes after the check) and each product with its group code and sizes. A same-name warning marks products that match existing names. |
| Confirm import | Opens a confirmation of the number of products and sizes, then creates them as unpublished drafts with proposed prices. The data is checked again on confirmation. |
| Import result | "{count} draft products created from the file.", a link to each created product's management details and **Import another file**. |
| Add images to the imported products | Pick images on a product, or **Pick many images** and assign each to a created product with an image description, then **Save images of {count} products**. |
