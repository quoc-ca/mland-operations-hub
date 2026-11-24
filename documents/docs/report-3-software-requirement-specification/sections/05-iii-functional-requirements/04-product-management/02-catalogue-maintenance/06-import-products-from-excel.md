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
| Choose the receiving branches | Checkboxes; the template has one quantity column per selected branch, and an empty cell means 0. |
| 1. Download the template | **Download template** is enabled after at least one branch is selected. The Size column accepts one size, a range, a list or a combination; rows sharing a group code form one product. |
| 2. Choose and check a file | **Choose .xlsx file** and **Check file** validate the file on the first sheet without creating data. |
| 3. Check result | State Valid, Has errors, Imported or Expired, with the time until which the check is valid (60 minutes); an error table lists Row, Column, Value and Problem, with **Download error report** and **Check another file**. A same-name warning marks products that match existing names. |
| Confirm import | Requires confirmation of the number of products and sizes, then creates them as unpublished drafts with proposed prices. |
| Import result | Links to each created product's management details and **Import another file**. |
| Add images to the imported products | Pick images on a product, or **Pick many images** and assign each to a created product with an image description, then save the images of all products. |
