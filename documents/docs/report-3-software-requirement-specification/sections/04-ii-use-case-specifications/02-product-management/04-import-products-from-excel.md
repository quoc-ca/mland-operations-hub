### 2.4 Import Products from Excel

#### Primary Actors

Staff, Manager with product-creation permission.

#### Secondary Actors

None.

#### Description

As an authorized catalogue maintainer, I want to create many draft products at once from an Excel template so that I do not have to enter each product and size by hand, while the same rules as manual creation still apply.

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Staff or Manager with product-creation permission.
2. At least one active category exists.

#### Normal Flow

**Import Products from Excel**

1. The actor opens Product Management and selects Import from Excel.
2. The actor downloads the template. Its columns are, in order: Group code, Product name, Description, Category, Size, Proposed price (VND) and Quantity.
3. The actor fills in the template. Rows with the same Group code form one product and must share the same name, description and category. The Size cell holds one size, a range such as `12-20`, a list such as `12,14,16`, or a combination such as `5-9,12`; every size in the cell uses that row's proposed price and quantity.
4. The actor uploads the `.xlsx` file.
5. The system checks the file type, size, row count and columns, then validates every row with the same rules as UC-12 Manage Products, without creating data or using product codes or SKUs.
6. The system shows a preview with the number of products, the number of variants and the list of errors (MSG31).
7. The actor confirms the import.
8. The system revalidates the file against the latest data, including whether each category is still active.
9. The system creates all products in one operation, each as Unpublished with a Draft review record, assigns product codes and SKUs, and records an audit entry.
10. The system lists the created product codes and SKUs (MSG34).

#### Alternative Flows

**Steps 1–9 — Actor is not authorized or the session has expired**

The system rejects the request at any step (MSG01, MSG02). Admin, Guest and Member cannot import.

**Step 5 — File is not accepted**

The system rejects a file that is not a valid macro-free `.xlsx`, is larger than 5,000,000 bytes, has more than 500 data rows, or has missing, extra or reordered columns (MSG30). Only the first sheet is read.

**Step 6 — Rows contain errors**

Each error shows the row number, column name, value and reason (MSG32), and the actor can download an error report. Values that start with `=`, `+`, `-` or `@` are neutralized in the report. The actor fixes the file and resumes at Step 4; Confirm is not available while errors remain.

**Step 7 — Actor does not confirm**

Nothing is created and no product code or SKU is used.

**Step 8 — Preview is out of date**

If the data has changed since the preview, for example a category was deactivated, the system rejects the confirmation and asks the actor to validate the file again (MSG33).

**Step 9 — Save or audit failure**

The system creates no product, variant, SKU or audit record (all or nothing) and reports a safe error (MSG04).

**Step 9 — Confirmation is sent twice**

The system does not create duplicates and returns the result of the import that already completed.

#### Postconditions

- On success, every product in the file exists as an Unpublished draft with its SKUs, and the import is audited.
- On failure or rejection, no product data is created; the rejected attempt is audited.
- The original file is not stored beyond the import result record.

#### Business Rules

BR-13-01, BR-13-02, BR-13-03, BR-13-04, BR-13-05, BR-13-06

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-13-01 | Import is another way of creating drafts: it uses the same validation, code and SKU rules and the same result as manual creation, and never submits, approves, prices or publishes a product. |
| BR-13-02 | The file must be a valid macro-free `.xlsx`, at most 5,000,000 bytes and 500 data rows; only the first sheet is read, cells are read as data, and formulas and external links are not executed. |
| BR-13-03 | The template columns are fixed; rows with the same Group code must have the same name, description and category and together form one product of 1–25 distinct sizes. |
| BR-13-04 | Numbers must match the manual-creation ranges exactly; values such as `12.5` or `1e3` are not rounded or converted. |
| BR-13-05 | The system validates the whole file and shows a preview before creating anything; the import is all or nothing and is revalidated on confirmation. |
| BR-13-06 | Confirmation is idempotent, and each import is audited with the actor, file name, row, product and variant counts, created codes, time and result, without storing the file content. |
