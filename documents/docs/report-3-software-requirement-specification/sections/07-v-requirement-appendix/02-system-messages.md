## 2. System Messages

|#|Message Code|Message type|Context|Content|
|---|---|---|---|---|
|1|MSG01|Error|An actor without the current role or permission, or with another person's identifier, tries to read or change protected data (Product Management, Loyalty and Promotion Management).|You do not have permission to perform this action.|
|2|MSG02|Error|The session has expired or been revoked during a protected action.|Your session has expired. Please sign in again.|
|3|MSG03|Error|The record was changed by another user after the actor opened it.|This record has been changed by someone else. Reload to review the latest data before saving again.|
|4|MSG04|Error|A save fails or its response is lost, so the result is not confirmed.|The result could not be confirmed. Check the current status before trying again.|
|5|MSG05|Error|Data cannot be loaded because of a temporary failure.|The data could not be loaded. Please try again.|
|6|MSG06|Information|The catalogue search returns no product (UC-10).|No products match your criteria. Change or clear the filters.|
|7|MSG07|Error|The catalogue search criteria are invalid, such as a keyword over 200 characters, a negative price, a minimum above the maximum or an unknown category (UC-10).|The search criteria are invalid: {reason}.|
|8|MSG08|Error|A field fails validation when saving a product, category, program or point policy.|{Field} is invalid: {rule}.|
|9|MSG09|Success|A draft product is created (UC-12).|Product {product code} was created as a draft with SKUs {SKU list}.|
|10|MSG10|Success|Product, quantity, sale status or category changes are saved (UC-12, UC-18).|Your changes have been saved.|
|11|MSG11|Error|An image breaks the type, size, count or alt-text rules (UC-12).|Images must be JPEG, PNG or WebP, at most 5,000,000 bytes each, up to 10 per product, with alt text of 1–200 characters.|
|12|MSG12|Error|An image cannot be stored (UC-12).|The image could not be uploaded. The previous images have been kept. Please try again.|
|13|MSG13|Error|The actor tries to add or change sizes after the product is saved (UC-12).|Sizes cannot be added or changed after the product has been saved.|
|14|MSG14|Error|The new quantity is lower than the quantity held by valid holds (UC-12).|The quantity cannot be lower than the {held quantity} units currently held.|
|15|MSG15|Confirmation|The actor selects Delete on a product (UC-12).|Delete product {product code}? It will be removed from the catalogue and cannot be restored.|
|16|MSG16|Success|A product is deleted (UC-12).|Product {product code} has been deleted.|
|17|MSG17|Error|An action targets a deleted product (UC-12 to UC-17).|This product has been deleted and can no longer be changed or sold.|
|18|MSG18|Error|A new product is submitted without a required prerequisite (UC-14).|The product cannot be submitted for approval: {missing prerequisite}.|
|19|MSG19|Error|A second price proposal is submitted for a size that already has a pending proposal (UC-14).|Size {size} already has a pending price proposal. Edit the current proposal instead.|
|20|MSG20|Success|A product or price proposal is submitted for approval (UC-14).|Submitted for Manager approval.|
|21|MSG21|Error|A rejection is confirmed without a valid reason (UC-15).|Enter a rejection reason of 1–2,000 characters.|
|22|MSG22|Success|The Manager records a decision (UC-15).|Decision recorded: {Approved / Adjusted / Rejected}.|
|23|MSG23|Error|The proposal or decision is no longer pending or valid (UC-15, UC-16).|This item is no longer valid for this action. Reload to see the latest version.|
|24|MSG24|Success|An approved price is applied (UC-16).|The new price of {SKU} has been applied: {old price} → {new price} VND.|
|25|MSG25|Error|A product is published without meeting all conditions (UC-17).|The product cannot be published: {unmet condition}.|
|26|MSG26|Success|A product is published or unpublished (UC-17).|Product {product code} is now {Published / Unpublished}.|
|27|MSG27|Error|The actor removes the last usable image of a published product (UC-12).|The last image cannot be removed while the product is published.|
|28|MSG28|Error|A category name already exists, ignoring case (UC-18).|A category with this name already exists.|
|29|MSG29|Confirmation|The actor deactivates a category (UC-18).|Deactivating this category will hide {number} products from the public catalogue. Continue?|
|30|MSG30|Error|The uploaded import file is not accepted (UC-13).|The file must be a macro-free .xlsx file of at most 5,000,000 bytes and 500 data rows, with the template columns in the original order.|
|31|MSG31|Information|The import file has been validated (UC-13).|{products} products and {variants} variants are ready to import. {errors} errors found.|
|32|MSG32|Error|An import row is invalid (UC-13).|Row {row}, column {column}: value "{value}" is invalid. {reason}|
|33|MSG33|Error|The import preview is out of date when the actor confirms (UC-13).|The data has changed since the preview. Please validate the file again.|
|34|MSG34|Success|The import is completed (UC-13).|{products} draft products were created: {product codes}.|
|35|MSG35|Warning|The selected size is stopped from sale or out of stock (UC-11).|This size is not available for purchase.|
|36|MSG36|Error|The branch source is unavailable on the product detail page (UC-11).|The branch list is temporarily unavailable. Please try again.|
|37|MSG37|Error|Add to cart is selected without a size or branch (UC-11).|Please select a size and a branch before adding to the cart.|
|38|MSG38|Success|A voucher or automatic discount is created (UC-45, UC-46).|Program {name} has been created{ with code {voucher code}}.|
|39|MSG39|Error|A voucher code already exists after trimming and ignoring case (UC-46).|Voucher code {voucher code} already exists.|
|40|MSG40|Error|The program scope is invalid (UC-45, UC-46).|The selected scope is invalid: {reason}.|
|41|MSG41|Error|The Manager tries to change the code or scope of an existing program (UC-45, UC-46).|The code and scope cannot be changed after the program has been created.|
|42|MSG42|Error|A total or per-Member limit is lower than the used plus held uses (UC-46).|The limit cannot be lower than {used} used plus {held} held uses.|
|43|MSG43|Success|A program is edited, paused or resumed (UC-45, UC-46).|Program {name} has been {updated / paused / resumed}. Changes apply to new transactions only.|
|44|MSG44|Warning|Products in a program's scope are hidden, stopped from sale or deleted (UC-45, UC-46).|Some products in this program's scope are hidden, stopped from sale or deleted.|
|45|MSG45|Information|A Guest selects Save on a voucher (UC-47).|Sign in to save this voucher.|
|46|MSG46|Success|A Member saves a voucher (UC-47).|The voucher has been saved to your Voucher Wallet.|
|47|MSG47|Information|A saved product voucher has no published product in scope (UC-47).|No products currently apply.|
|48|MSG48|Error|A voucher cannot be applied to the order or booking (UC-48).|This voucher cannot be used: {reason}.|
|49|MSG49|Error|The Member tries to use points with a voucher that does not allow them (UC-44).|This voucher cannot be combined with loyalty points.|
|50|MSG50|Error|The number of points is outside the allowed range (UC-44).|You can use from {minimum} to {maximum} points for this transaction.|
|51|MSG51|Confirmation|The price, discount, points or limits changed before the order or booking was accepted (UC-44, UC-48).|The total has changed to {new total} VND. Please review and confirm again.|
|52|MSG52|Error|The workshop fee after discounts is 0 or less (UC-48).|The fee after discounts must be greater than 0. Please adjust the voucher or points.|
|53|MSG53|Success|The loyalty point policy is updated (UC-51).|The loyalty point policy has been updated. It applies to transactions accepted from now on.|
