## 2. System Messages

|#|Message Code|Message type|Context|Content|
|---|---|---|---|---|
|1|MSG01|Error|An actor without the current role or permission, or with another person's identifier, tries to read or change protected data, including a Staff member trying to publish, hide or show a whole product again (Product Management, Loyalty and Promotion Management).|You do not have permission to perform this action.|
|2|MSG02|Error|The session has expired or been revoked during a protected action.|Your session has expired. Please sign in again.|
|3|MSG03|Error|The record was changed by another user after the actor opened it.|This record has been changed by someone else. Reload to review the latest data before saving again.|
|4|MSG04|Error|A save fails or its response is lost, so the result is not confirmed.|The result could not be confirmed. Check the current status before trying again.|
|5|MSG05|Error|Data cannot be loaded because of a temporary failure.|The data could not be loaded. Please try again.|
|6|MSG06|Information|The catalogue search returns no product (UC-10).|No products match your criteria. Change or clear the filters.|
|7|MSG07|Error|The catalogue search criteria are invalid, such as a keyword over 200 characters, a negative price, a minimum above the maximum or an unknown category (UC-10).|The search criteria are invalid: {reason}.|
|8|MSG08|Error|A field fails validation when saving a product, category, program or point policy, or when a management search value is invalid, such as a category name search over 200 characters (UC-18).|{Field} is invalid: {rule}.|
|9|MSG09|Success|A draft product is created (UC-12).|Product {product code} was created as a draft with SKUs {SKU list}.|
|10|MSG10|Success|Product content, images, branch stock, quantity, sale status or category changes are saved (UC-12, UC-18). Shown at the top of the page and under the button used.|{What was saved}, e.g. "Quantity of size {size} changed: {old} → {new}." / "Quantities of {n} sizes changed." / "Category "{name}" saved."|
|11|MSG11|Error|An image breaks the type, size, count or alt-text rules (UC-12).|Images must be JPEG, PNG or WebP, at most 5,000,000 bytes each, up to 10 per product, with alt text of 1–200 characters.|
|12|MSG12|Error|An image cannot be stored (UC-12).|The image could not be uploaded. The previous images have been kept. Please try again.|
|13|MSG13|Error|The actor tries to change or remove a size, or change a SKU, after the product is saved; adding a new size is allowed (UC-12).|Existing sizes cannot be changed or removed. Only sizes 5–29 the product does not have yet can be added.|
|14|MSG14|Error|The new quantity of a size at a branch is lower than the quantity held by valid holds at that branch (UC-12).|The quantity cannot be lower than the {held quantity} units currently held.|
|15|MSG15|Confirmation|The actor selects Delete on a product (UC-12).|Delete product {product code}? It will be removed from the catalogue and cannot be restored.|
|16|MSG16|Success|A product is deleted (UC-12).|Product {product code} has been deleted.|
|17|MSG17|Error|An action targets a deleted product (UC-12 to UC-17).|This product has been deleted and can no longer be changed or sold.|
|18|MSG18|Error|A new product is submitted without a required prerequisite (UC-14).|The product cannot be submitted for approval: {missing prerequisite}.|
|19|MSG19|Error|A second price proposal is submitted for a size that already has a pending proposal (UC-14).|Size {size} already has a pending price proposal. Edit the current proposal instead.|
|20|MSG20|Success|A product, a price proposal or the proposals of several sizes are saved or submitted for approval (UC-14).|{What was submitted} and the next step, e.g. "Price of size {size} submitted. A Manager sees it under the "Prices to review" tab." / "Prices of {n} sizes submitted."|
|21|MSG21|Error|A rejection is confirmed without a valid reason (UC-15).|Enter a rejection reason of 1–2,000 characters.|
|22|MSG22|Success|The Manager records a decision, for one size, several sizes or a whole product review (UC-15).|Decision recorded: {Approved / Rejected} for {size / n sizes / the review}, and whether the price is applied yet, e.g. "Price {amount} approved for size {size}. It is not applied yet."|
|23|MSG23|Error|The proposal or decision is no longer pending or valid (UC-15, UC-16).|This item is no longer valid for this action. Reload to see the latest version.|
|24|MSG24|Success|An approved price is applied, later or right after the approval (UC-15, UC-16).|"Price {amount} applied to size {size}." / "Approved prices applied to {n} sizes." / "Price {amount} approved and applied for size {size}. The new selling price is in effect."|
|25|MSG25|Error|A product is published without meeting all conditions (UC-17).|The product cannot be published: {unmet condition}.|
|26|MSG26|Success|A product is published or unpublished, including through a Manager's Publish now (UC-17).|Product {product code} is now {Published / Unpublished}.|
|27|MSG27|Error|The actor removes the last usable image of a published product (UC-12).|The last image cannot be removed while the product is published.|
|28|MSG28|Error|A category name already exists, ignoring case (UC-18).|A category with this name already exists.|
|29|MSG29|Confirmation|The actor deactivates a category (UC-18).|Deactivating this category will hide {number} products from the public catalogue. Continue?|
|30|MSG30|Error|The uploaded import file is not accepted, including when its branch quantity columns do not match the selected branches (UC-13).|The file must be a macro-free .xlsx file of at most 5,000,000 bytes and 500 data rows, with the template columns in the original order and one quantity column per selected branch.|
|31|MSG31|Information|The import file has been validated (UC-13).|{products} products and {variants} variants are ready to import. {errors} errors found.|
|32|MSG32|Error|An import row is invalid (UC-13).|Row {row}, column {column}: value "{value}" is invalid. {reason}|
|33|MSG33|Error|The import preview is out of date or older than 60 minutes when the actor confirms (UC-13).|The data has changed since the preview. Please validate the file again.|
|34|MSG34|Success|The import is completed (UC-13).|{products} draft products were created: {product codes}.|
|35|MSG35|Information|A size is stopped from sale or has no available quantity at the selected branch, or at any branch when none is selected; its cell is dimmed and cannot be selected (UC-10, UC-11).|Sold out. / Not on sale.|
|36|MSG36|Error|The shared branch list is unavailable on the product detail page or the Quick add box; Add to Cart and Buy Now are disabled until a retry succeeds (UC-10, UC-11).|The branch list is temporarily unavailable. Please try again.|
|37|MSG37|Error|Add to Cart or Buy Now is selected without a size that can be bought, or without a branch; no request is sent (UC-10, UC-11).|Please select a size. / Please select a branch.|
|38|MSG38|Success|A voucher or automatic discount is created (UC-45, UC-46).|Program {name} has been created [with code {voucher code}].|
|39|MSG39|Error|A voucher code already exists after trimming and ignoring case (UC-46).|Voucher code {voucher code} already exists.|
|40|MSG40|Error|The program scope is invalid, including a workshop automatic discount without a package or with a branch, date or session (UC-45, UC-46).|The selected scope is invalid: {reason}.|
|41|MSG41|Error|The Manager tries to change the code or scope of an existing program (UC-45, UC-46).|The code and scope cannot be changed after the program has been created.|
|42|MSG42|Error|A total or per-Member limit is lower than the used plus held uses (UC-46).|The limit cannot be lower than {used} used plus {held} held uses.|
|43|MSG43|Success|A program is edited, paused or resumed (UC-45, UC-46).|Program {name} has been {updated / paused / resumed}. Changes apply to new transactions only.|
|44|MSG44|Warning|Products in a program's scope are hidden, stopped from sale or deleted, or a branch in a product voucher's scope is inactive (UC-45, UC-46).|Some products in this program's scope are hidden, stopped from sale or deleted.|
|45|MSG45|Information|A Guest selects Save on a voucher (UC-47).|Sign in to save this voucher.|
|46|MSG46|Success|A Member saves a voucher (UC-47).|The voucher has been saved to My vouchers.|
|47|MSG47|Information|A saved voucher has no published product in scope, or its uses or the Member's uses are all used or held; a public card or detail of a voucher whose uses are all used or held, or, for a voucher that must be saved, all used, held or claimed (UC-47).|No products currently apply. / Fully claimed. / All claimed. / You used all your uses.|
|48|MSG48|Error|A voucher cannot be applied to the order or booking, for example the code does not exist, the scope or branch does not match, the voucher is expired, paused or fully used, a voucher that must be saved has not been saved by the Member, or the automatic discount already covers all its lines so it gives no further discount (UC-48).|This voucher cannot be used: {reason}.|
|49|MSG49|Error|The Member tries to use points with a voucher that does not allow them (UC-44).|This voucher cannot be combined with loyalty points.|
|50|MSG50|Error|The number of points is outside the allowed range (UC-44).|You can use from {minimum} to {maximum} points for this transaction.|
|51|MSG51|Confirmation|The price, discount, points or limits changed before the order or booking was accepted (UC-44, UC-48).|The total has changed to {new total} VND. Please review and confirm again.|
|52|MSG52|Error|The workshop fee after discounts is negative because of invalid data; a fee of 0 VND is accepted (UC-48).|The amount after discounts cannot be negative. Please adjust the voucher or points.|
|53|MSG53|Success|The loyalty point policy is updated (UC-51).|The loyalty point policy has been updated. It applies to transactions accepted from now on.|
|54|MSG54|Error|A change of several sizes succeeds for some sizes only (UC-12, UC-14, UC-15, UC-16).|{done}/{total} sizes done. The other sizes were not saved; see the details under {section}. (Each failed size and its reason is listed under the button used.)|
|55|MSG55|Success|An item is added to the cart from the Quick add box on the product list (UC-10).|"{product name}" was added to your cart. View cart|
|56|MSG56|Warning|A new product, or a product in an import file, has the same name as an existing product (UC-12, UC-13). Saving or importing is still allowed.|{count} product(s) already have this name: {links}. / {count} product(s) in the file have the name of an existing product. Check them before confirming (the import is still allowed).|
|57|MSG57|Success / Error|Images are saved, or not saved, for the products just imported (UC-13).|Images saved for {count} products. / Images of {count} products were not saved; see the error on each product and save again.|
|58|MSG58|Information|A signed-in Staff, Manager or Admin views the Save option of a voucher; only Members have My vouchers (UC-47).|Only members can save vouchers.|
|59|MSG59|Information|The size conversion chart dialog has no size chart image (UC-10, UC-11).|No size chart image yet.|
|60|MSG60|Information|The product has no public customer review; five empty stars are shown and no score (UC-11).|No reviews yet|
|61|MSG61|Information|No category matches the status filter or name search in Category Management (UC-18).|No category matches the filter.|
|62|MSG62|Error|A size added to a product already has stock at that branch, or a new size of an Add branch request already exists on the product (UC-12).|This size is already at the branch or on the product. Reload to see the current sizes.|
|63|MSG63|Error|Sizes are added at a branch where the product is not sold (UC-12).|The product is not sold at this branch.|
|64|MSG64|Success|Sizes are added at a branch, with their prices kept in the initial review, sent for approval, or approved and applied (UC-12, UC-14, UC-15).|"Sizes {sizes} were added at {branch}." / "… and their prices sent for approval. They cannot be bought until a Manager approves and applies the price." / "… with their prices approved and applied." / "… The product review is a draft: submit it again to have the new prices approved."|
|65|MSG65|Information|A size of the product has no stock row at the branch tab being viewed (UC-12).|Not sold at this branch|
|66|MSG66|Information|The Product Management List is filtered by a branch where the product has been stopped from sale (UC-12).|Selling stopped at this branch|
|67|MSG67|Information|A status tab or price sub-filter of the Product Management List has no product (UC-12, UC-14, UC-15).|{Empty text of the tab}, e.g. "No draft." / "No product waits for review." / "No price proposal waits for a decision." / "No rejected price."|
|68|MSG68|Warning|A Manager's one-step Change price stops at a failed step; the earlier steps are kept (UC-14, UC-15, UC-16).|The change to {amount} for size {size} is not finished: the steps before the error are kept. Check the current state below and carry on.|
|69|MSG69|Information|The details of a public voucher are opened; it has no Save option (UC-47).|Public code: no need to save it, pick it at checkout.|
|70|MSG70|Information|A voucher card shows a voucher that must be saved before use (UC-47).|Save to use|
|71|MSG71|Error|A Member saves a voucher that must be saved, but no use is left to claim or the Member has already used their use (UC-47).|All claimed. / You have used your use of this code.|
|72|MSG72|Information|A product or workshop package price includes the best voucher, or a better voucher must be saved first (UC-47).|"Price after voucher {code}" / "Price after voucher {code} · at {branches}" / "Save code: {value} off"|
|73|MSG73|Warning|Sizes are added at a branch, but a later step for the price of a new size (submit, approve or apply) fails; the sizes and the steps already done are kept (UC-12, UC-14, UC-15).|"Sizes {sizes} were added, but the price of size {failed} is not finished. Carry on in the Price section."|
