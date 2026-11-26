### 2.4 Import Products from Excel

| Use Case ID | UC-13 | Use Case Name | Import Products from Excel |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | Cloud Storage Service |

#### Description

As an authorized catalogue maintainer, I want to import draft products from an Excel template and attach their images so that I can create products in bulk under standard rules.

#### Trigger

A Staff or Manager with product-creation permission selects Import from Excel on the Product Management List.

#### Preconditions

1. Actor is signed in with an active Staff or Manager account and product-creation permission.
2. At least one active category exists.
3. At least one active branch is within Actor's scope.

#### Postconditions

1. On success, all imported products exist as Unpublished drafts with SKUs and branch stock, and are audited.
2. On failure or rejection, no product data is created and the rejected attempt is audited.
3. The uploaded Excel file content is not stored beyond the import result record.

#### Normal Flow

**Import Products from Excel**

1. Actor opens Import from Excel and selects active branches within scope.
2. Actor downloads the `.xlsx` template containing Group code, details, size, price, and branch quantity columns.
3. Actor fills in the template rows and uploads the `.xlsx` file.
4. System validates file type, size, row count, columns, and all row rules per UC-12 without creating data.
5. System displays a 60-minute preview with product/variant counts, duplicate-name warnings (MSG56), and validation summary (MSG31).
6. Actor confirms the import.
7. System revalidates the file against the latest data and active categories.
8. System creates all products atomically as Unpublished drafts with SKUs and branch stock, and logs an audit entry.
9. System displays the created product codes and SKUs (MSG34).

**Attach Images to Imported Products**

1. System offers to attach images to the newly imported products on the result screen.
2. Actor selects images, verifies auto-matched products by code or name, and edits alt text.
3. System validates image types, 5 MB limit, 10-image cap, and alt text before sending.
4. Actor selects Save; System uploads and saves images per product using UC-12 rules.
5. System confirms image save results for each product (MSG57).

#### Alternative Flows

8a. Confirmation is sent twice → System avoids duplicates and returns the completed import result.

A1a. Actor skips attaching images → Actor exits the result screen and adds images later on the product detail page.

#### Exception Flows

1a. Actor is unauthorized or session expired → System rejects the request at any step (MSG01, MSG02).

1b. Selected branch is outside Actor's scope → System rejects the request (MSG01).

4a. File is invalid, oversized, over 500 rows, or columns mismatch → System rejects the file (MSG30).

5a. Rows contain validation errors → System lists errors with row, column, value, and reason (MSG32), blocks Confirm, and offers a neutralized error report.

6a. Actor does not confirm → System creates no product, code, or SKU.

7a. Preview expired (> 60 minutes) or data changed → System rejects confirmation, creates nothing, and prompts revalidation (MSG33).

8b. Save or audit fails → System rolls back all products, SKUs, and audits and reports a safe error (MSG04).

A4a. Image upload fails for one product → Other products stay unaffected; System reports per-product results (MSG57) and allows retry.

#### Business Rules

GBR-17, BR-13-01, BR-13-02, BR-13-03, BR-13-04, BR-13-05, BR-13-06, BR-13-07

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG04, MSG30, MSG31, MSG32, MSG33, MSG34, MSG56, MSG57

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-17 | Excel import creates draft products under the same rules as manual creation, validates the whole file first, is all or nothing, takes quantities per selected branch within the actor's scope, and its preview expires after 60 minutes. |
| BR-13-01 | Import is another way of creating drafts: it uses the same validation, code and SKU rules and the same result as manual creation, and never submits, approves, prices or publishes a product. Import only creates new products; it does not add sizes to existing ones. |
| BR-13-02 | The file must be a valid macro-free `.xlsx`, at most 5,000,000 bytes and 500 data rows; only the first sheet is read, cells are read as data, and formulas and external links are not executed. |
| BR-13-03 | The template columns are fixed, with one quantity column per selected branch; rows with the same Group code must have the same name, description and category and together form one product of 1–25 distinct sizes. The selected branches must be within the actor's scope. |
| BR-13-04 | Numbers must match the manual-creation ranges exactly; values such as `12.5` or `1e3` are not rounded or converted. |
| BR-13-05 | The system validates the whole file and shows a preview before creating anything; the import is all or nothing, is revalidated on confirmation, and a preview expires after 60 minutes. A duplicate product name is only a warning. |
| BR-13-06 | Confirmation is idempotent, and each import is audited with the actor, file name, row, product and variant counts, created codes, time and result, without storing the file content. |
| BR-13-07 | The file contains no images. Attaching images after an import is only a way of entering them: each product uses the image operation of UC-12, a product that fails does not affect the others, and no product is submitted for approval automatically. |
