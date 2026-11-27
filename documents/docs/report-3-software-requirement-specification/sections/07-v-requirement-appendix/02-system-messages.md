## 2. System Messages

::: {.docx-table width="100%" columns="5%,11%,16%,33%,35%" repeat-header="true"}

| # | Message code | Message Type | Context | Content |
| --- | --- | --- | --- | --- |
| 1 | MSG01 | In line | An actor without the current role or permission, or with another person's identifier, tries to read or change protected data, including a Staff member trying to publish, hide or show a whole product again (Product Management, Loyalty and Promotion Management). | You do not have permission to perform this action. |
| 2 | MSG02 | In line | The session has expired or been revoked during a protected action. | Your session has expired. Please sign in again. |
| 3 | MSG03 | In line | The record was changed by another user after the actor opened it. | This record has been changed by someone else. Reload to review the latest data before saving again. |
| 4 | MSG04 | In line | A save fails or its response is lost, so the result is not confirmed. | The result could not be confirmed. Check the current status before trying again. |
| 5 | MSG05 | In line | Data cannot be loaded because of a temporary failure. | The data could not be loaded. Please try again. |
| 6 | MSG06 | In line | The catalogue search returns no product. | No products match your criteria. Change or clear the filters. |
| 7 | MSG07 | In red, under the text box | The catalogue search criteria are invalid, such as a keyword over 200 characters, a negative price, a minimum above the maximum or an unknown category. | The search criteria are invalid: {reason}. |
| 8 | MSG08 | In red, under the text box | A field fails validation when saving a product, category, program or point policy, or when a management search value is invalid, such as a category name search over 200 characters. | {Field} is invalid: {rule}. |
| 9 | MSG09 | In line | A draft product is created. | Product {product code} was created as a draft with SKUs {SKU list}. |
| 10 | MSG10 | In line | Product content, images, branch stock, quantity, sale status or category changes are saved. Shown at the top of the page and under the button used. | {What was saved}, e.g. "Quantity of size {size} changed: {old} → {new}." / "Quantities of {n} sizes changed." / "Category "{name}" saved." |
| 11 | MSG11 | In line | An image breaks the type, size, count or alt-text rules. | Images must be JPEG, PNG or WebP, at most 5,000,000 bytes each, up to 10 per product, with alt text of 1–200 characters. |
| 12 | MSG12 | In line | An image cannot be stored. | The image could not be uploaded. The previous images have been kept. Please try again. |
| 13 | MSG13 | In line | The actor tries to change or remove a size, or change a SKU, after the product is saved; adding a new size is allowed. | Existing sizes cannot be changed or removed. Only sizes 5–29 the product does not have yet can be added. |
| 14 | MSG14 | In red, under the text box | The new quantity of a size at a branch is lower than the quantity held by valid holds at that branch. | The quantity cannot be lower than the {held quantity} units currently held. |
| 15 | MSG15 | Dialog | The actor selects Delete on a product. | Delete product {product code}? It will be removed from the catalogue and cannot be restored. |
| 16 | MSG16 | In line | A product is deleted. | Product {product code} has been deleted. |
| 17 | MSG17 | In line | An action targets a deleted product. | This product has been deleted and can no longer be changed or sold. |
| 18 | MSG18 | In line | A new product is submitted without a required prerequisite. | The product cannot be submitted for approval: {missing prerequisite}. |
| 19 | MSG19 | In line | A second price proposal is submitted for a size that already has a pending proposal. | Size {size} already has a pending price proposal. Edit the current proposal instead. |
| 20 | MSG20 | In line | A product, a price proposal or the proposals of several sizes are saved or submitted for approval. | {What was submitted} and the next step, e.g. "Price of size {size} submitted. A Manager sees it under the "Prices to review" tab." / "Prices of {n} sizes submitted." |
| 21 | MSG21 | In red, under the text box | A rejection is confirmed without a valid reason. | Enter a rejection reason of 1–2,000 characters. |
| 22 | MSG22 | In line | The Manager records a decision, for one size, several sizes or a whole product review. | Decision recorded: {Approved / Rejected} for {size / n sizes / the review}, and whether the price is applied yet, e.g. "Price {amount} approved for size {size}. It is not applied yet." |
| 23 | MSG23 | In line | The proposal or decision is no longer pending or valid. | This item is no longer valid for this action. Reload to see the latest version. |
| 24 | MSG24 | In line | An approved price is applied, later or right after the approval. | "Price {amount} applied to size {size}." / "Approved prices applied to {n} sizes." / "Price {amount} approved and applied for size {size}. The new selling price is in effect." |
| 25 | MSG25 | In line | A product is published without meeting all conditions. | The product cannot be published: {unmet condition}. |
| 26 | MSG26 | In line | A product is published or unpublished, including through a Manager's Publish now. | Product {product code} is now {Published / Unpublished}. |
| 27 | MSG27 | In line | The actor removes the last usable image of a published product. | The last image cannot be removed while the product is published. |
| 28 | MSG28 | In line | A category name already exists, ignoring case. | A category with this name already exists. |
| 29 | MSG29 | Dialog | The actor deactivates a category. | Deactivating this category will hide {number} products from the public catalogue. Continue? |
| 30 | MSG30 | In line | The uploaded import file is not accepted, including when its branch quantity columns do not match the selected branches. | The file must be a macro-free .xlsx file of at most 5,000,000 bytes and 500 data rows, with the template columns in the original order and one quantity column per selected branch. |
| 31 | MSG31 | In line | The import file has been validated. | {products} products and {variants} variants are ready to import. {errors} errors found. |
| 32 | MSG32 | In line | An import row is invalid. | Row {row}, column {column}: value "{value}" is invalid. {reason} |
| 33 | MSG33 | In line | The import preview is out of date or older than 60 minutes when the actor confirms. | The data has changed since the preview. Please validate the file again. |
| 34 | MSG34 | In line | The import is completed. | {products} draft products were created: {product codes}. |
| 35 | MSG35 | In line | A size is stopped from sale or has no available quantity at the selected branch, or at any branch when none is selected; its cell is dimmed and cannot be selected. | Sold out. / Not on sale. |
| 36 | MSG36 | In line | The shared branch list is unavailable on the product detail page or the Quick add box; Add to Cart and Buy Now are disabled until a retry succeeds. | The branch list is temporarily unavailable. Please try again. |
| 37 | MSG37 | In line | Add to Cart or Buy Now is selected without a size that can be bought, or without a branch; no request is sent. | Please select a size. / Please select a branch. |
| 38 | MSG38 | In line | A voucher or automatic discount is created. | Program {name} has been created [with code {voucher code}]. |
| 39 | MSG39 | In line | A voucher code already exists after trimming and ignoring case. | Voucher code {voucher code} already exists. |
| 40 | MSG40 | In red, under the text box | The program scope is invalid, including a workshop automatic discount without a package or with a branch, date or session. | The selected scope is invalid: {reason}. |
| 41 | MSG41 | In line | A total or per-Member limit is lower than the used plus held uses; for a voucher that must be saved, the total limit is also compared with the saved but unused uses. | The limit cannot be lower than {used} used plus {held} held uses. |
| 42 | MSG42 | In line | A program is edited, paused or resumed. | Program {name} has been {updated / paused / resumed}. Changes apply to new transactions only. |
| 43 | MSG43 | In line | Products in a program's scope are hidden, stopped from sale or deleted, or a branch in a product voucher's scope is inactive. | Some products in this program's scope are hidden, stopped from sale or deleted, or a branch in its scope is no longer active. |
| 44 | MSG44 | In line | A Guest selects Save on a voucher. | Sign in to save this voucher. |
| 45 | MSG45 | In line | A Member saves a voucher. | The voucher has been saved to My Saved Vouchers. |
| 46 | MSG46 | In line | A saved voucher has no published product or package in scope, or its uses or the Member's uses are all used or held; a public card or detail of a voucher whose uses are all used or held, or, for a voucher that must be saved, all used, held or claimed. | No product or package left. / Fully claimed. / All claimed. / You used all your uses. |
| 47 | MSG47 | In line | A voucher cannot be applied to the order or booking, for example the code does not exist, the scope or branch does not match, the voucher is expired, paused or fully used, a voucher that must be saved has not been saved by the Member, or the automatic discount already covers all its lines so it gives no further discount. | This voucher cannot be used: {reason}. |
| 48 | MSG48 | In line | The Member tries to use points with a voucher that does not allow them. | This voucher cannot be combined with loyalty points. |
| 49 | MSG49 | In line | The number of points is outside the allowed range. | You can use from {minimum} to {maximum} points for this transaction. |
| 50 | MSG50 | In line | The price, discount, points or limits changed before the order or booking was accepted. | The total has changed to {new total} VND. Please review and confirm again. |
| 51 | MSG51 | In line | The workshop fee after discounts is negative because of invalid data; a fee of 0 VND is accepted. | The amount after discounts cannot be negative. Please adjust the voucher or points. |
| 52 | MSG52 | In line | The loyalty point policy is updated. | The loyalty point policy has been updated. It applies to transactions accepted from now on. |
| 53 | MSG53 | In line | A change of several sizes succeeds for some sizes only. | {done}/{total} sizes done. The other sizes were not saved; see the details under {section}. (Each failed size and its reason is listed under the button used.) |
| 54 | MSG54 | In line | An item is added to the cart from the Quick add box on the product list. | "{product name}" was added to your cart. View cart |
| 55 | MSG55 | In line | A new product, or a product in an import file, has the same name as an existing product. Saving or importing is still allowed. | {count} product(s) already have this name: {links}. / {count} product(s) in the file have the name of an existing product. Check them before confirming (the import is still allowed). |
| 56 | MSG56 | In line | Images are saved, or not saved, for the products just imported. | Images saved for {count} products. / Images of {count} products were not saved; see the error on each product and save again. |
| 57 | MSG57 | In line | A signed-in Staff, Manager, Admin or Carrier views the Save option of a voucher; only Members have My Saved Vouchers. | Only members can save vouchers. |
| 58 | MSG58 | In line | The size conversion chart dialog has no size chart image. | No size chart image yet. |
| 59 | MSG59 | In line | The product has no public customer review; five empty stars are shown and no score. | No reviews yet |
| 60 | MSG60 | In line | No category matches the status filter or name search in Category Management. | No category matches the filter. |
| 61 | MSG61 | In line | A size added to a product already has stock at that branch, or a new size of an Add branch request already exists on the product. | This size is already at the branch or on the product. Reload to see the current sizes. |
| 62 | MSG62 | In line | Sizes are added at a branch where the product is not sold. | The product is not sold at this branch. |
| 63 | MSG63 | In line | Sizes are added at a branch, with their prices kept in the initial review, sent for approval, or approved and applied. | "Sizes {sizes} were added at {branch}." / "… and their prices sent for approval. They cannot be bought until a Manager approves and applies the price." / "… with their prices approved and applied." / "… The product review is a draft: submit it again to have the new prices approved." |
| 64 | MSG64 | In line | The Product Management List is filtered by a branch where the product has been stopped from sale. | Selling stopped at this branch |
| 65 | MSG65 | In line | A status tab or price sub-filter of the Product Management List has no product. | {Empty text of the tab}, e.g. "No draft." / "No product waits for review." / "No price proposal waits for a decision." / "No rejected price." |
| 66 | MSG66 | In line | A Manager's one-step Change price stops at a failed step; the earlier steps are kept. | The change to {amount} for size {size} is not finished: the steps before the error are kept. Check the current state below and carry on. |
| 67 | MSG67 | In line | The details of a public voucher are opened; it has no Save option. | Public code: no need to save it, pick it at checkout. |
| 68 | MSG68 | In line | A voucher card shows a voucher that must be saved before use. | Save to use |
| 69 | MSG69 | In line | A Member saves a voucher that must be saved, but no use is left to claim or the Member has already used their use. | All claimed. / You have used your use of this code. |
| 70 | MSG70 | In line | A product or workshop package price includes the best voucher, or a better voucher must be saved first. | "Price after voucher {code}" / "Price after voucher {code} · at {branches}" / "Save code: {value} off" |
| 71 | MSG71 | In line | Sizes are added at a branch, but a later step for the price of a new size (submit, approve or apply) fails; the sizes and the steps already done are kept. | "Sizes {sizes} were added, but the price of size {failed} is not finished. Carry on in the Price section." |
| 72 | MSG72 | In line | A cart quantity is not a whole number from 1 to 99, or is above the quantity left at the branch. | The quantity must be from 1 to 99. / The branch has only {availableQuantity} of this item left. |
| 73 | MSG73 | In line | The Member presses Mua hàng with no cart line ticked. | Select at least one item to buy. |
| 74 | MSG74 | Toast message | Mua lại could not put some rings of a past order back in the cart. | Could not add {items} again: not enough stock or no longer sold. The other items are already ticked. |
| 75 | MSG75 | In line | GHTK is chosen at checkout without a delivery address, or GHTK does not deliver to the address. | Add and choose a delivery address before using GHTK. / GHTK does not deliver to this address yet. Choose store pickup or another address. |
| 76 | MSG76 | In line | The lines chosen for checkout are no longer in the cart. | The items you chose are no longer in the cart. |
| 77 | MSG77 | In line | A retail order is placed, for VNPay or for cash at the counter. | Order placed, opening the VNPay payment page… / Order placed. Please go to {branch} before {deadline} and pay cash on pickup. |
| 78 | MSG78 | In line | The order is placed but the VNPay page does not open. | The order is placed but the VNPay page did not open. Press pay to try again, or pay later from My orders. |
| 79 | MSG79 | Toast message | The VNPay payment of a retail order is verified. | Payment successful. Mland is preparing your order. |
| 80 | MSG80 | In line | The hold of an unpaid retail order has ended. | The hold has ended. If VNPay does not confirm a payment, the order will cancel itself and give back the voucher and points. |
| 81 | MSG81 | Toast message | Staff marks a pickup order ready, hands it to the customer or records the counter cash. | Order {code} is packed and waits for the customer. / Order {code} handed to the customer. / Order {code}: {payableAmount} collected, order completed. |
| 82 | MSG82 | In line | The dashboard or one of its parts cannot be loaded. | The dashboard could not be loaded. You can try again. / The trend charts could not be loaded. / The best sellers could not be loaded. |
| 83 | MSG83 | In line | The Manager chooses an invalid dashboard filter, such as a period entirely in the future. | Invalid filter (for example a period entirely in the future). Keeping the last valid result. |
| 84 | MSG84 | In line | Some dashboard figures cannot be read. | Figures are incomplete. Items marked "—" have no data; they are not zero. |
| 85 | MSG85 | In line | The chosen period has no data for a dashboard part. | No verified transactions in this period. / No product was sold in this period. / No workshop ticket was confirmed in this period. |
| 86 | MSG86 | In red, under the text box | The email or password of a sign-in, registration or reset form is missing or invalid. | {Field error}, e.g. Please enter your email address. / The email address is not in a valid format. / The password must be at least 6 characters. |
| 87 | MSG87 | In line | Firebase rejects the email and password at sign-in. | Incorrect email or password. |
| 88 | MSG88 | In line | Firebase blocks sign-in after too many failed attempts. | Too many invalid requests. Please try again in a moment. |
| 89 | MSG89 | In line | A Google sign-in or a registration uses an email already registered with a password. | This email is already registered with a password. Please sign in with your original email and password. |
| 90 | MSG90 | In line | The account is suspended, or the identity is not eligible (for example an email reserved by a pending invitation). | Your account is suspended or not eligible. |
| 91 | MSG91 | In line | The account service cannot be reached during sign-in or registration. | The account service is temporarily unavailable. Please try again. |
| 92 | MSG92 | In line | A password reset request is accepted or silently fails. | If the email exists in the system, a password reset message has been sent. |
| 93 | MSG93 | In red, under the text box | The password confirmation on the Register page does not match. | The confirmation password does not match. |
| 94 | MSG94 | In line | Firebase rejects the registration password as too weak. | The password is too weak. Use at least 6 characters. |
| 95 | MSG95 | In line | The verified identity carries no valid email, so no account is created. | Your sign-in method did not provide an email address. Please use another sign-in method. |
| 96 | MSG96 | In line | The Change Password form has a missing field, a short new password or a mismatched confirmation. | {Error}, e.g. Please enter your current password. / The new password must be at least 6 characters. / The confirmation password does not match. |
| 97 | MSG97 | In line | The password has been changed. | Password changed successfully. |
| 98 | MSG98 | In line | Re-authentication fails because the current password is wrong. | The current password is incorrect. |
| 99 | MSG99 | In line | Firebase fails to update the password for another reason. | Could not change the password. Please try again. |
| 100 | MSG100 | In line | The own profile has been saved. | Profile information updated successfully. |
| 101 | MSG101 | In line | The Profile page finds that the login email is not verified. | Your email is not verified yet! Some features require a verified email. Please check your inbox. |
| 102 | MSG102 | In line | Saving the profile is disabled until the email is verified. | Verify your email before saving changes. |
| 103 | MSG103 | In line | The verification status cannot be reloaded from Firebase or the platform. | Could not check the email verification status. |
| 104 | MSG104 | In line | A verification email has been sent to the requested new login email. | A confirmation email was sent to {email}. Your current email stays unchanged until verification is complete. |
| 105 | MSG105 | In line | The requested new login email belongs to another account. | This email address is already used by another account. |
| 106 | MSG106 | In line | A Google-only account opens the email change section. | Your account signs in through an external provider (Google). Please update the email address directly in your Google account. |
| 107 | MSG107 | In line | A shipping address is added, updated, deleted or made the default. | {Result}, e.g. Delivery address added. / Address updated. / Address deleted. / Default address set. |
| 108 | MSG108 | In line | The Member tries to save an eleventh address. | You have reached the limit of 10 delivery addresses. |
| 109 | MSG109 | In line | The Member tries to delete the default address while other addresses exist. | The default address cannot be deleted while other addresses exist. Set another address as the default first. |
| 110 | MSG110 | In red, under the text box | The recipient phone is not a 10-digit Vietnamese mobile number. | Invalid phone number (must be a 10-digit Vietnamese mobile number, e.g. 0912345678). |
| 111 | MSG111 | In red, under the text box | The full name or phone on the Profile form is invalid. | {Field error}, e.g. Full name is required. / Full name must not exceed 100 characters. / The phone number must be 7 to 30 characters. |
| 112 | MSG112 | In line | The profile save fails on the server. | Could not update the profile. |
| 113 | MSG113 | In line | A Staff or Manager account has been created by the Admin. | Created successfully! Status: Pending setup. The invitation has been queued for delivery and its link is valid for 48 hours. |
| 114 | MSG114 | In line | The Admin account search returns no account. | No accounts match the current filters. |
| 115 | MSG115 | In line | The Admin has saved the profile of an internal account. | The account profile was updated. |
| 116 | MSG116 | In line | The Admin has saved a role and location assignment. | The role and location assignment was updated. The server confirmed the change and it is now in effect. |
| 117 | MSG117 | In line | The Admin saves an empty full name or a wrong number of locations on the account detail page. | {Error}, e.g. Full name is required. / Select at least 1 location. / A Staff member must be assigned to exactly 1 location. |
| 118 | MSG118 | Dialog | The Admin selects Suspend on an account. | After confirmation the account becomes SUSPENDED and its sign-in session is revoked. The user will no longer be able to access the system. |
| 119 | MSG119 | In line | An account has been suspended. | The account has been suspended (SUSPENDED). |
| 120 | MSG120 | Dialog | The Admin confirms a suspension without a reason. | A suspension reason is required. |
| 121 | MSG121 | In line | A suspended account has been restored. | The account was restored. The user needs to sign in again. |
| 122 | MSG122 | Dialog | The Admin tries to restore an account whose session revocation is not confirmed. | The account cannot be restored while the Firebase session revocation is still in progress. |
| 123 | MSG123 | In line | The Admin opens the detail page of an Admin account. | The ADMIN account is protected and cannot be suspended or edited directly. |
| 124 | MSG124 | In line | The requested account does not exist. | Account #{id} was not found. |
| 125 | MSG125 | In line | The Admin selects Resend invitation. | Resending the invitation immediately invalidates the previous link and any manually copied content. A new 48-hour period starts now. |
| 126 | MSG126 | In line | A setup invitation has been resent. | The account setup invitation was resent. |
| 127 | MSG127 | In line | A resend is requested within five minutes of the previous invitation. | Please wait at least 5 minutes between invitation resends. |
| 128 | MSG128 | In line | A Carrier account has been created as Active. | The Carrier partner (GHTK) account was activated directly. The initial password is write-only and cannot be viewed again. Hand the sign-in details to the partner directly. |
| 129 | MSG129 | In red, under the text box | A field of the Create internal account form is missing or invalid. | {Field error}, e.g. Full name is required. / The email address is not in a valid format. / The password must be at least 6 characters. / The phone number must be 7 to 30 characters. / Select at least one work location. |
| 130 | MSG130 | In line | The new account email belongs to an existing Staff or Manager account. | This email already belongs to an existing STAFF or MANAGER account (ACC-{id}). Go to its administration page to view and update it. |
| 131 | MSG131 | In line | The new account email belongs to an Admin or Carrier account. | This email is linked to an ADMIN or CARRIER role and cannot be replaced here. |
| 132 | MSG132 | Dialog | The Admin tries to replace a Member while a data source is sample or unavailable. | The data source is DEMO/UNAVAILABLE or no Member replacement fence exists; the account cannot be replaced and the email cannot be released. |
| 133 | MSG133 | Dialog | The new account email exists only as a Firebase identity. | Email {email} exists in Firebase but has no Mland account. Confirm deleting this orphan Firebase identity before creating the staff account. |
| 134 | MSG134 | In line | The email collision check fails. | The email collision could not be checked. |
| 135 | MSG135 | In line | A creation retry key is reused with different details. | This request was already submitted with different details. Start a new creation. |
| 136 | MSG136 | In line | The internal account cannot be created. | Could not create the internal account. |
| 137 | MSG137 | Dialog | The Admin opens the manual copy of a failed invitation email. | Extracting the invitation content is audited. Manual copying does not change the delivery state (still FAILED) and does not prove that the message reached the recipient. |
| 138 | MSG138 | In line | A product page is opened for a product that does not exist, is deleted, hidden or no longer eligible for public view. | This product does not exist or is no longer public |
| 139 | MSG139 | Dialog | Staff selects Submit for review on a never-published product. | Submitting freezes the current content, images, sizes and proposed prices for a Manager to decide. |
| 140 | MSG140 | In line | A Manager creates a product and keeps it unpublished; submission, approval and price application all succeed. | Product "{name}" was created with its prices applied. It is not published: choose Publish now under Publication when ready. |
| 141 | MSG141 | In line | A Staff member with no assigned branch tries to create a product or change stock. | No branch is assigned to your account yet. Ask an Admin to assign one, then try again. |
| 142 | MSG142 | In line | A branch chosen for stock is inactive or does not exist. | The branch does not exist or is no longer active. |
| 143 | MSG143 | In line | A selling branch is added to a product already sold there. | The product is already sold at this branch. |
| 144 | MSG144 | In line | A Manager's create screen saves the product but a later submission, approval or apply step fails. | Product "{name}" was saved but not fully applied. Choose Publish now under Publication to carry on. |
| 145 | MSG145 | In line | A Manager opens Publication of a never-published product whose review a Staff member submitted and is still pending. | A Staff member submitted this review. Decide it under Initial review (every size's price can be seen and adjusted) before publishing. |
| 146 | MSG146 | Toast message | A package line is added to the workshop cart. | Added to your cart |
| 147 | MSG147 | In line | The chosen session set or participant count does not fit the package rules. | Please choose all {count} workshop session(s). / The minimum number of tickets is {count}. / The chosen sessions only have room for {count} ticket(s). / The session selection is not valid under the package rules (number of sessions, location or time gap). |
| 148 | MSG148 | In line | The package is no longer sold at the chosen branch when the cart is quoted or held. | This package is no longer sold at the branch you chose. No seats were held; the details you entered are kept. Please choose another branch. |
| 149 | MSG149 | In line | One or more cart lines no longer have enough seats when the cart is held. | These lines no longer have enough seats: {lines}. No seats were held; you can fix the cart and quote again. |
| 150 | MSG150 | In line | The quote used to create the group has expired, was already used or has changed. | The quote has expired. Please request a new quote. |
| 151 | MSG151 | In red, under the text box | The contact email or phone number is invalid. | Invalid email (for example: name@example.com). / Invalid phone number for {country} (+{dial}). |
| 152 | MSG152 | In line | A service needed to quote or hold the cart is unavailable. | The system cannot process the request right now. Your cart and the details you entered are kept; please try again later. |
| 153 | MSG153 | In line | A Guest asks for a voucher or points. | Vouchers and points only apply to a signed-in Member account. |
| 154 | MSG154 | In line | The quote collects a booking-time payment online. | The payment made at booking is not refunded if you cancel yourself or do not attend. |
| 155 | MSG155 | In line | The group collects less than 5,000 VND online. | No online payment needed; pay {amount} at the counter at checkout |
| 156 | MSG156 | In line | The seats of the group are held and wait for payment. | Seats held for {count} ticket(s). Time left to pay: {minutes} min {seconds} sec |
| 157 | MSG157 | In line | The VNPay payment cannot be created, or the payment window has closed. | Payment could not be made |
| 158 | MSG158 | In line | The actor returns from VNPay without completing the payment. | Payment not completed. The payment was not made. You can pay again or cancel the hold within the payment deadline. |
| 159 | MSG159 | In line | The browser returns from VNPay with success before the signed result is verified. | VNPay reports success. MLAND confirms the booking group only after it receives verified IPN evidence. |
| 160 | MSG160 | In line | The verified payment confirms the group. | Payment successful. Your booking is confirmed. The tickets are listed below and also sent by email. |
| 161 | MSG161 | In line | The payment window has ended and the system still waits for the VNPay result. | Waiting for the payment result. The payment deadline has passed. The system is waiting for the payment gateway to confirm the result, in case you have just paid. This page updates by itself. |
| 162 | MSG162 | In line | The hold ends without a verified payment. | The payment deadline ended before the system received a valid payment, so all seats held for the group have been released. |
| 163 | MSG163 | In line | A Guest looks up a group with both or neither contact value. | Please provide exactly one of the two: the Email OR the Phone number used for the booking. |
| 164 | MSG164 | In line | The Magic Link is unknown, expired or revoked. | The Magic Link does not exist or has expired. / The Magic Link has been revoked or changed. You can look up the booking group on the Lookup page with the group code and the email or phone number used for the booking. |
| 165 | MSG165 | In line | No booking group matches the code and the contact value. | No booking group matches the code and contact details provided. |
| 166 | MSG166 | In line | Too many lookup, release or resend attempts for one booking code. | Too many attempts for this booking group. Try again later. |
| 167 | MSG167 | In line | The buyer released the hold of the group. | This booking group has been cancelled and all held seats have been released. |
| 168 | MSG168 | In line | The buyer tries to release a hold while a payment attempt is live. | A payment is already in progress for this group, so the hold cannot be released. The hold ends by itself when the time runs out. |
| 169 | MSG169 | In line | The confirmation resend is accepted. | The request to resend the confirmation email was received. The email goes to the address used for the booking. |
| 170 | MSG170 | In line | The hourly confirmation resend limit is reached. | You have reached the resend limit for this hour. Please try again later. |
| 171 | MSG171 | In line | The buyer verification code is wrong, locked or expired. | The OTP is wrong. You still have attempts left. / Wrong code entered 5 times. Please request a new code. / The verification code has expired or was replaced by a new one. Please get a new code. |
| 172 | MSG172 | In line | The ticket is sent or resent to the recipient email. | The ticket was sent to the recipient email. |
| 173 | MSG173 | In line | The ticket has used all 3 online recipient changes. | This ticket has used all 3 online recipient changes. Please contact our staff at the store. |
| 174 | MSG174 | In line | The ticket is not confirmed or is already checked in. | The ticket is not confirmed yet, so it cannot be sent to someone else. / The ticket is already checked in, so the recipient cannot be changed. |
| 175 | MSG175 | In line | Someone other than the buyer tries to change the recipient. | Only the buyer (or staff after verifying the buyer) can change the ticket recipient. |
| 176 | MSG176 | In line | One or more tickets are cancelled. | Ticket {code} cancelled. Valid cancellation: {points} point(s) returned. / Late cancellation: no points are returned. / The group voucher has been restored. / The group voucher has expired, so it was not restored. |
| 177 | MSG177 | In line | The buyer opens the cancellation of a ticket. | There are 24 hours or more before the first session, so this is a valid cancellation: the points used on the ticket are returned. / There are less than 24 hours before the first session, so this is a late cancellation: the seat is released but points are not returned. |
| 178 | MSG178 | In line | A selected ticket cannot be cancelled, so none is cancelled. | Could not cancel the ticket right now. / Blocked tickets: {codes} |
| 179 | MSG179 | Dialog | Staff or a Manager confirms a counter cancellation. | {count} ticket(s) will be cancelled all or none. This cannot be undone. Any refund goes to the bank account the payer provides. |
| 180 | MSG180 | In line | No replacement offer exists for the ticket, or the actor may not see it. | We could not find an offer for this ticket. |
| 181 | MSG181 | In line | The chosen replacement session is refused. | That session has no seat left or is no longer sold at that location. Choose another session or ask for a refund. / That session is not one of the offered sessions. / The time to answer has passed. The ticket is refunded. |
| 182 | MSG182 | Dialog | The buyer sends an answer to the Mland offer. | Confirm your answer. The ticket will be refunded to your bank account. / Your ticket moves to the chosen session at {place}. Your answer cannot be changed afterwards. |
| 183 | MSG183 | In line | The answer to the Mland offer is recorded, or the offer was already answered. | Your new session is booked. / Your refund request was recorded. / Your ticket was moved to the replacement session. / Your ticket is being refunded. |
| 184 | MSG184 | In line | The refund-account link was used, replaced or has expired. | This link was already used, replaced or has expired. Ask the shop to send you a new link. |
| 185 | MSG185 | In line | The refund-account link is unknown or incomplete. | This link is not valid. Open the link from your refund email again. / This link is incomplete. Open the link from your refund email again. |
| 186 | MSG186 | In line | The payer's refund account is saved through the link. | Thank you. Your bank account was received. We will transfer the refund after it is approved and email you when it is done. |
| 187 | MSG187 | In line | Staff or a Manager records the refund account at the counter. | Account recorded. |
| 188 | MSG188 | In line | The folio of the ticket is not found for the Member or the Guest proof. | We could not find this ticket for your account. / We could not find a ticket with this code and contact. |
| 189 | MSG189 | In line | A ticket change is applied. | Schedule changed for ticket {code}. / The price increase of {amount} is added to the balance due at check-in. |
| 190 | MSG190 | In line | A ticket change is queued for a Staff or Manager decision. | The schedule change request was sent to a manager for approval. We will email you the result. |
| 191 | MSG191 | In line | The chosen replacement raises the ticket price. | This change raises the ticket price by {amount} and it is added to the balance due. Do you agree? |
| 192 | MSG192 | In line | A ticket change is refused. | The session you chose just ran out of seats. Please choose another session. / The session you chose has no reschedule quota left. / The new sessions do not fit the package rules. Please choose again. / This package is no longer sold at the branch you chose. Your ticket is unchanged and no change was used up. / The deadline to change the schedule has passed: you can only change until the end of the 7th day after the session ended (Vietnam time). / This ticket has used its one schedule change. Please contact our staff. / This ticket has a schedule change request waiting for manager approval. / The ticket folio is closed, so the schedule cannot be changed. |
| 193 | MSG193 | In line | A customer tries to change a session that has already started. | Session {sessions} has started, so it cannot be changed online. Please contact the {branch} shop for help. |
| 194 | MSG194 | In line | No replacement session is available for the ticket. | No session can replace this one right now. |
| 195 | MSG195 | In line | A normal check-in by ticket code or QR code is recorded. | Check-in recorded. / QR check-in recorded. |
| 196 | MSG196 | In line | A ticket that is already checked in at this session is scanned again. | Ticket was already checked in at {when}. |
| 197 | MSG197 | In line | A normal check-in is attempted before the check-in opening time. | Check-in opens at {when}. |
| 198 | MSG198 | In line | The ticket is cancelled, revoked, refunded, unknown or not valid for this session. | This cannot be done for the ticket: it is cancelled or no longer valid, does not belong to this session, or is already checked in. |
| 199 | MSG199 | In line | The session state no longer accepts check-in, such as finalized or cancelled. | The session state is not valid for this action (the session is finalized or cancelled). |
| 200 | MSG200 | In line | A booking-code lookup finds no valid ticket for the current session. | No valid ticket was found for this booking code in the current session. |
| 201 | MSG201 | In line | A ticket resend has been accepted. | Ticket resend requested. |
| 202 | MSG202 | In line | A fourth resend for the booking within 15 minutes is refused. | Tickets were already resent 3 times in 15 minutes for this booking; try again in {seconds} seconds. If the customer still has not received the ticket, use Override check-in with the reason TICKET_REDELIVERY_UNAVAILABLE. |
| 203 | MSG203 | In line | The booking has no Active unused ticket to resend, or the booking refuses the resend. | This booking has no valid unused ticket to resend. / The booking system refused to resend the tickets of this booking. No ticket was sent. |
| 204 | MSG204 | In line | A check-in override is recorded. | Check-in override recorded. |
| 205 | MSG205 | Dialog | The actor selects Finalize attendance. | Finalize attendance of this session? {checked}/{total} ticket(s) checked in, {rest} not. No further check-in after finalization. |
| 206 | MSG206 | In line | The session attendance is finalized. | Attendance finalized. |
| 207 | MSG207 | In line | A Manager reverses a check-in or corrects final attendance. | Check-in reversal recorded. / Attendance correction recorded. |
| 208 | MSG208 | In line | Add-ons or a variable charge are added to the folio. | Add-on added. / {count} add-ons added. / Charge added. |
| 209 | MSG209 | In line | A charge is added before a session of the ticket has started. | The add-on or charge cannot be added because the session of this ticket has not started. Move the session to In progress, then add it again. |
| 210 | MSG210 | In line | A charge is added to a finalized or closed folio. | The folio of this ticket is closed (finalized), so no more add-on or charge can be added. |
| 211 | MSG211 | In line | A charge is sent without the customer agreement confirmed. | The customer must agree before an add-on is added: tick the customer agreed box. |
| 212 | MSG212 | In line | Staff acts on a ticket folio without an assignment to the ticket's session. | You are not assigned to the session of this ticket. Ask a Manager to assign you to it, then try again. |
| 213 | MSG213 | In line | The charge is below 5,000 VND, or the add-on is no longer sold for the package and location. | The charge must be at least 5,000 VND, and the add-on must still be sold for this package and location. |
| 214 | MSG214 | In line | No add-on is sold for the ticket's package and location. | No add-on is available for this package at this location. |
| 215 | MSG215 | In line | A cash or bank-transfer collection is recorded; the folio then shows its new status, Open or Finalized. | Collection recorded. |
| 216 | MSG216 | In line | A VNPay checkout is created for a folio part. | VNPay checkout created. Valid until {time}. The part counts only after VNPay's signed confirmation. Customer scans with the phone camera to open the payment page on their own phone. |
| 217 | MSG217 | In line | VNPay confirms the folio part. | VNPay payment succeeded. |
| 218 | MSG218 | In line | A VNPay part, or the remainder after it, would be under 5,000 VND. | A VNPay part must be at least 5,000 VND. Collect this amount by cash or bank transfer. / The remainder after this part would be under 5,000 VND, so it cannot be collected by VNPay; it must be collected by cash or bank transfer. |
| 219 | MSG219 | In line | A collection is recorded while a VNPay part of the folio is pending. | A new collection cannot be recorded because the folio still has a VNPay part pending verification. Wait for VNPay to confirm it, or have a Manager mark it as failed, then collect by another method. |
| 220 | MSG220 | In line | A cash close is submitted. | Cash book closed. |
| 221 | MSG221 | In line | A cash close has been submitted, with or without a difference. | Cash recorded from now on belongs to the next working day{, even while this difference waits for the Manager's confirmation}. The figures above are the server's. |
| 222 | MSG222 | In line | A Staff member views a close whose difference awaits confirmation. | A Manager must confirm this difference before the day is closed. |
| 223 | MSG223 | In line | A cash close with a difference is submitted without a reason. | Enter a reason for the cash difference. |
| 224 | MSG224 | Dialog | A Manager selects Confirm difference. | Confirm the cash-close difference. The day closes with this difference on record. This is audited. |
| 225 | MSG225 | In line | A Manager confirmed a cash-close difference. | Difference confirmed; the day is closed. |
| 226 | MSG226 | In line | A cash close is submitted for a future working day. | A cash book cannot be closed for a future working day. |
| 227 | MSG227 | In line | A cash close is submitted for a branch and day that already has one. | The cash book of this branch and working day is already closed. |
| 228 | MSG228 | In line | A change request is approved or rejected. | Change approved and applied. / Change rejected. |
| 229 | MSG229 | Dialog | The actor selects Approve or Reject on a change request. | Approve this change: Ticket {code}. Approving applies the change now; the capacity and quota checks still run on the server. / Reject this change: Ticket {code}. Rejecting keeps the ticket on its current schedule. |
| 230 | MSG230 | In line | The change request queue has no request for the chosen status. | No change requests found. |
| 231 | MSG231 | In line | The request has expired or was already decided. | This request has expired or was already decided. Reload the queue. |
| 232 | MSG232 | In line | The approved change cannot be applied any more. | The change cannot be applied: {reason}. The request stays pending. |
| 233 | MSG233 | In line | A Manager confirmed a pending bank-transfer collection. | Transfer confirmed. |
| 234 | MSG234 | In line | A Manager marked a pending collection as not paid. | Collection marked as not paid. |
| 235 | MSG235 | In red, under the text box | A Manager confirms a settlement action without entering a reason. | A reason is required. |
| 236 | MSG236 | In line | A Manager approved a handover before payment verification. | Handover allowed and audited. The balance below is still owed. |
| 237 | MSG237 | In line | A Manager recorded or closed a debt on a ticket. | Debt {recorded/closed}. |
| 238 | MSG238 | In line | The debt amount is 0 or less, or exceeds the balance due. | The debt amount must be above 0 and not exceed the balance due. |
| 239 | MSG239 | In line | A Manager tries to close a debt on a ticket without a recorded debt. | This ticket has no recorded debt. |
| 240 | MSG240 | In line | A Manager voided a folio line or a collection. | {Line/Collection} voided. |
| 241 | MSG241 | In line | A Manager tries to void cash counted into a submitted cash close. | This cash belongs to a closed cash day and cannot be voided. Record a cash reversal on the current working day instead. |
| 242 | MSG242 | In line | A Manager reversed a cash entry of a closed cash day. | Cash entry reversed: {amount} recorded on working day {date}. The original collection stays visible. |
| 243 | MSG243 | In line | A Manager corrected an overcharge on a finalized folio. | Folio correction {ref} created ({status}). A bank refund request was opened {refundId}. |
| 244 | MSG244 | In line | A folio action is refused by its current state or amount limits. | The action cannot be completed: {reason}. |
| 245 | MSG245 | In line | A Manager granted an extra change allowance. | One extra change granted for {ticketCode} ({ref}). The change itself is still to be made. |
| 246 | MSG246 | In line | The ticket, collection or booking group code does not exist. | No {record} matches this code. |
| 247 | MSG247 | In line | A refund request transition or account-link action succeeded. | Refund {outcome}. |
| 248 | MSG248 | In line | A Manager closes a request as unreachable before 30 days have passed. | This request can be closed as unreachable only 30 days after it was opened. |
| 249 | MSG249 | In red, under the text box | The receipt image is not JPEG, PNG or WebP, exceeds 5,000,000 bytes, or cannot be stored. | The receipt image is not accepted: {reason}. Choose another image, or record the transfer with the bank reference only. |
| 250 | MSG250 | In line | The refund-account key replacement finished. | Key replaced. {resetCount} requests returned to pending account, {sentCount} new links sent, {failedCount} failed. Each request has an audit record. |
| 251 | MSG251 | In line | A Manager decided a late payment. | {Late payment confirmed./100% refund request opened.} |
| 252 | MSG252 | In line | A Manager tries to confirm a late payment whose conditions do not allow it. | This late payment cannot be confirmed: {failedConditions}. Open a full refund instead. |
| 253 | MSG253 | In line | The refund request for a late payment could not be opened. | The refund request could not be opened. Please try again. |
| 254 | MSG254 | In line | Booking-time payments cannot be read for the reconciliation list. | The booking-time payments could not be read right now, so no partial list is shown. Try again, or filter without booking-time payments. |
| 255 | MSG255 | In line | No collection matches the reconciliation filters. | No transfer, VNPay or booking-time collection for these filters. |
| 256 | MSG256 | In line | A Manager recorded or voided an absorbed undercharge. | Absorbed undercharge {recorded/voided}. |
| 257 | MSG257 | In line | A Manager revoked or rotated a group's Magic Link. | Magic Link {revoked/rotated} for booking {groupCode}. |
| 258 | MSG258 | In line | A Manager acts on the Magic Link of a group that is not confirmed. | Only a confirmed booking group has a Magic Link. |
| 259 | MSG259 | In red, under the text box | The audit filters are invalid, such as From later than To. | "From" must be earlier than or equal to "To". |
| 260 | MSG260 | In line | No audit record matches the filters. | No audit rows match the filters. Remove some filters or widen the time range. |
| 261 | MSG261 | In line | The AI question is empty or longer than 2,000 characters. | The question must be 1–2,000 characters. |
| 262 | MSG262 | In line | The daily AI prompt allowance is used up. | You have used all your AI prompts for today. Prompts reset at 00:00. |
| 263 | MSG263 | In line | The AI question contains unsafe instructions or asks for payment, credentials or custom orders. | The AI assistant only answers public information and cannot handle this request. |
| 264 | MSG264 | In line | The AI provider fails and no answer can be produced. | The AI service is temporarily unavailable. Please try again later. |
| 265 | MSG265 | In line | The Guest AI session is invalid or expired. | Your guest AI session is invalid or has expired. |
| 266 | MSG266 | In line | A Guest opens the custom design request page. | Please sign in to create a custom design request. |
| 267 | MSG267 | In line | The request has no description, image or saved design. | Please enter a description, add a reference image or attach a saved design. |
| 268 | MSG268 | In red, under the text box | An image breaks the type, size, count or resolution rules, checked in the browser or by the server. | Image {file name} is invalid: {rule}. Use up to 5 JPEG, PNG or WebP images, each at most 5 MB and 4096×4096 px. |
| 269 | MSG269 | In line | An image is too blurry for review. | Image {file name} is too blurry. Please choose a sharper image. |
| 270 | MSG270 | In red, under the text box | Images are added without accepting the image consent terms. | You must accept the image use terms before submitting images. |
| 271 | MSG271 | In line | The Member exceeds 3 image submissions in 5 minutes. | You have sent images too often. Please wait {seconds} seconds and try again. |
| 272 | MSG272 | In line | The attached saved design is not owned, archived or unavailable. | The attached saved design is invalid or can no longer be used. |
| 273 | MSG273 | In line | The design request is stored. | Your request has been sent and is waiting for Staff review. |
| 274 | MSG274 | In red, under the text box | The review explanation is empty or longer than 2,000 characters. | The explanation must be 1–2,000 characters. |
| 275 | MSG275 | In line | The design request already has a final decision. | This request has already been decided and cannot be changed. |
| 276 | MSG276 | In line | The review decision is recorded. | The review decision has been recorded. |
| 277 | MSG277 | In red, under the text box | The quote amount is not 1–1,000,000,000 VND or the payment period is not 24–72 hours. | {Field} is invalid: the amount must be 1–1,000,000,000 VND and the payment period 24–72 hours. |
| 278 | MSG278 | In line | A quote is submitted. | Quote {quote reference} of {amount} VND has been sent to the Manager for approval. |
| 279 | MSG279 | In line | A quote command is refused because the request is not Accepted, the order is paid, or the quote is not the latest pending one. | The quote cannot be changed: {reason}. |
| 280 | MSG280 | In line | The Manager approves the quote. | Quote {quote reference} has been approved. The order is waiting for the Member's full payment. |
| 281 | MSG281 | In line | The quote deadline has passed when the Member tries to pay. | This quote has expired. Please request a new quote. |
| 282 | MSG282 | In line | The quote is not approved yet or the order is already paid. | This order cannot be paid: {reason}. |
| 283 | MSG283 | In line | No VNPay payment link is received or the payment start fails. | The VNPay payment page could not be opened. Please try again. |
| 284 | MSG284 | In line | The Member requests a new quote while a valid quote exists. | This order still has a valid quote, so a new quote cannot be requested. |
| 285 | MSG285 | In line | The cart already has 50 lines, or the added quantity would make the size exceed 99 units. | The cart cannot hold more than 50 lines or more than 99 units of one size. |
| 286 | MSG286 | Dialog | The Member selects Hủy đơn on an unpaid order. | Cancel order {order}? The items go back to your cart. / Cancel order {order}? The items go back to your cart; the voucher and points are given back. |
| 287 | MSG287 | Toast message | The Member comes back from VNPay without completing the payment. | Payment is not complete. The order and its discounts are held until {deadline}; press Pay to continue. |
| 288 | MSG288 | Toast message | The Member is back from VNPay and the order is not yet paid; shown while rechecking and after the rechecks end. | Confirming the payment with VNPay… / No payment result from VNPay yet. The order will update itself; reload the page in a few minutes. |
| 289 | MSG289 | In line | Staff confirms counter cash after the 72-hour pickup deadline has passed. | The pickup deadline has passed; the order is no longer held. |
| 290 | MSG290 | Dialog | Staff or a Manager selects Đã chuẩn bị xong on a GHTK order. | Move shipment {trackingNumber} to "{status}"? |
| 291 | MSG291 | Toast message | A GHTK shipment has been moved to its next status by Staff or a Manager. | Shipment {trackingNumber}: {status}. |
| 292 | MSG292 | Dialog | Staff or a Manager selects the next stage of a custom order. | Change the status of order {reference}? Current status: {current}. New status: {new}. |
| 293 | MSG293 | In line | A custom order has been moved to its next stage. | Order {reference} has been moved to "{status}". |
| 294 | MSG294 | In line | A custom order stage change is refused because the order is not fully paid or is not at the previous stage. | The order has not been fully paid. / The order can only move to the next step. |
| 295 | MSG295 | In line | The Manager reconciliation page has no transaction or no cash to show. | There is no transaction to reconcile on this page. / No transaction matches the filters on this page. / No cash was collected on this day. |
| 296 | MSG296 | In line | A non-Carrier account opens the GHTK shipment pages. | This account is not an active GHTK partner account. |
| 297 | MSG297 | In line | A Carrier records a courier step that the shipment's current status does not allow. | Only a shipment the shop has packed can be picked up. / Only a shipment being delivered can be marked delivered. |
| 298 | MSG298 | In line | A GHTK shipment tab has no shipment, or the search matches none. | There is no shipment in this tab. / No matching shipment found. |
| 299 | MSG299 | In line | A Manager creates a workshop location. | Location created. |
| 300 | MSG300 | In line | A location is created without a code or a name, or edited with an empty name. | Location code and name are required. |
| 301 | MSG301 | In line | The code of a new location is already used by another location. | Location code {code} already exists. |
| 302 | MSG302 | In line | A Manager saves a change to a location. | Location updated. |
| 303 | MSG303 | In line | A Manager deactivates a location that a session not yet finalized or cancelled still uses. | This location cannot be deactivated while a session that has not ended still uses it. Wait for those sessions to end or cancel them first. |
| 304 | MSG304 | In line | A Manager saves a location edit without changing any value. | Change the name, address or status before saving. |
| 305 | MSG305 | In red, under the text box | A package field is invalid when creating or editing a package. | Enter a package name. / Enter a customer-safe description. / Use a non-negative whole number. / Public price must be positive. / Maximum must be at least minimum. / At least one session is required. |
| 306 | MSG306 | In line | A package draft is created, or a package is updated, published or retired. | Draft created. Publish it when the package is ready. / Package updated. / Package published. / Package retired. |
| 307 | MSG307 | Dialog | A Manager selects Retire on a published package. | Retire package? This stops new bookings. Existing booking snapshots are preserved. |
| 308 | MSG308 | In line | A package or add-on transition is not allowed, or a retired package or add-on is edited. | This package or add-on cannot move to that state, and a retired one cannot be edited. |
| 309 | MSG309 | Toast message | A Manager turns a package on or off at a location in the availability matrix. | Sales turned {on / off}: {package} at {location}. |
| 310 | MSG310 | In line | An add-on is saved without a package, a location, a description or a valid price. | Select a workshop package and a location, then enter a description and a valid VND price. |
| 311 | MSG311 | In line | An add-on draft is created, updated, activated or retired. | Add-on draft created. Activate it when it is ready. / Add-on updated. / Add-on moved to {state}. |
| 312 | MSG312 | In line | A Manager creates one session, or generates sessions in bulk. | Session created in DRAFT. / {created} sessions created; {skipped} skipped as duplicates. |
| 313 | MSG313 | In line | Session or bulk generation input is missing or invalid. | Location, slot, date and positive capacity are required. / Location, at least one slot, a date range and at least one weekday are required. / Capacity must be a whole number of at least 1. / The start date must not be after the end date. |
| 314 | MSG314 | In line | The session start is not in the future or is more than 90 days ahead. | The workshop session must start in the future and no more than 90 days ahead. |
| 315 | MSG315 | In line | A session already exists for the location, date and slot. | A session already exists for location {code}, date {date}, and slot {slot}. |
| 316 | MSG316 | In line | A session is updated or its state changes. | Session updated. / Session state updated. / Session moved to {state}. |
| 317 | MSG317 | In line | The new capacity is below confirmed tickets plus unexpired holds. | The new capacity is below the tickets and payment holds already committed to this session. Enter a larger capacity. |
| 318 | MSG318 | Dialog | A Manager requests cancellation of a session. | Cancel this session? {total} ticket(s) are affected. The session stops selling and cannot be restored. |
| 319 | MSG319 | In line | A session cancellation is accepted, directly or by handoff to settlement. | Cancellation request submitted. |
| 320 | MSG320 | In line | A cancellation, check-in reversal or attendance correction has no recognised reason code, or lacks the required note. | Enter a valid reason code, and a note when the chosen reason requires one. |
| 321 | MSG321 | In line | A Manager sets, closes or restores the reschedule quota, or omits the reason. | Reschedule quota updated. / Session closed to customer changes. / Reschedule quota restored to the default. / A reason is required to change the reschedule quota. |
| 322 | MSG322 | In line | A session state transition or cancellation is not allowed from the current state. | The session cannot move to {state} from its current state. Reload the page to see the latest state of the session. |
| 323 | MSG323 | In line | The check-in opening time is changed after the session started, or is not a valid time. | Check-in opening time cannot be changed once the session is in progress or finalized. / Enter a valid check-in opening time. |
| 324 | MSG324 | In line | A Manager assigns a Staff member to a session. | Staff assignment saved. |
| 325 | MSG325 | In line | A Manager removes a Staff assignment. | Staff assignment removed. |
| 326 | MSG326 | Dialog | A Manager removes the last Staff assignment of a session whose check-in has opened. | Remove the last Staff member of this session? Check-in has opened. After removal the session has no Staff in charge, but a Manager can still check in. |
| 327 | MSG327 | In line | Staff is assigned to a session in a state that does not allow it. | Staff cannot be assigned to a session in its current state. Reload the page to see the latest state of the session. |
| 328 | MSG328 | In red, under the text box | The chosen account cannot be assigned to the session. | Account not found. Check the account ID. / This account is not a Staff member. / This Staff account is not active. / This Staff member does not belong to the branch of the session. |
| 329 | MSG329 | In line | A Manager creates, edits, activates or deactivates a time slot. | Slot created. / Slot updated. / Slot activated. / Slot deactivated. |
| 330 | MSG330 | In line | A Manager saves a slot edit without changing any value. | Change the label or the time before saving. |
| 331 | MSG331 | In line | A slot label or time is missing or invalid. | Enter a slot label. / Enter a start time and an end time. / The start time must be before the end time on the same day. |
| 332 | MSG332 | In line | A slot would overlap an active slot of the branch. | This time overlaps the active slot {label} ({start} - {end}) of the branch. |
| 333 | MSG333 | In line | The selected branch has no time slot. | This branch has no slot yet. |
| 334 | MSG334 | In line | A Manager records an attendance correction or a check-in reversal. | Attendance correction recorded. / Check-in reversal recorded. |
| 335 | MSG335 | In line | A correction or reversal is not allowed in the current session or ticket state. | This action is not allowed in the current state of the session or ticket. Reload the page to see the latest state. |
| 336 | MSG336 | In line | The 24-hour correction window of the ticket has closed. | The correction window closed 24 hours after the last session of this ticket was finalized. |
| 337 | MSG337 | In line | The ticket is not found, not active or not entitled to the session. | The ticket was not found, is not active or does not belong to this session. |
| 338 | MSG338 | In line | A voucher's Conditions page is opened but the voucher does not exist or is no longer public. | This offer does not exist or is no longer public |
| 339 | MSG339 | In line | The Voucher hub has no voucher that must be saved for the chosen scope. | There are no vouchers to save right now. |
| 340 | MSG340 | In line | The Member opens My vouchers with no saved voucher. | You have not saved any voucher. |
| 341 | MSG341 | In line | The Member's point history has no entry. | You have no point transactions yet. |
| 342 | MSG342 | In line | No promotion programme matches the scope or status filter. | No programme matches the filters. |
| 343 | MSG343 | Dialog | The Manager selects Pause or Resume on a programme. | Pause: Customers cannot use it on new orders. Accepted orders, history and usage are kept. / Resume: The programme can be used again within its period. |
| 344 | MSG344 | In red, under the text box | The Manager saves the edit form without changing any value. | Nothing has changed yet. |
| 345 | MSG345 | In line | A draft template version is created or updated. | Draft version v{version} has been saved. |
| 346 | MSG346 | In line | The actor tries to change a template version that is not a draft. | Only a draft version can be changed. Version v{version} is {status}. |
| 347 | MSG347 | In line | A template version is activated. | Version v{version} is now active. New emails will use it. |
| 348 | MSG348 | In red, under the text box | A template draft has no subject or no body. | The subject is required. / Enter the HTML or plain-text content. |
| 349 | MSG349 | In line | A template draft uses placeholders that are not registered for the template. | These placeholders are not allowed: {list}. |
| 350 | MSG350 | In line | No email request matches the filters. | No email request matches the filters. |
| 351 | MSG351 | In line | A resend is queued. | The resend has been queued (new request #{id}). |
| 352 | MSG352 | In line | A resend, cancellation or redelivery is confirmed without a reason, or a resend reason has fewer than 10 characters. | Enter a reason for this action. A resend reason needs at least 10 characters. |
| 353 | MSG353 | In line | The request state does not allow the chosen resend or cancellation. | This request is {state} and cannot be {action}. |
| 354 | MSG354 | In line | A waiting email request is cancelled. | The email request has been cancelled. |
| 355 | MSG355 | In line | A redelivery with a renewed link or ticket is queued. | The redelivery with a new link has been queued (new request #{id}). |
| 356 | MSG356 | In line | A generic resend, redelivery or cancellation is attempted on an internal account invitation. | Invitation emails cannot be resent here. Use the invitation resend in account administration. |
| 357 | MSG357 | Dialog | The Admin starts a resend of an unknown-outcome email. | This email may already have been delivered. Resending may send the customer a duplicate email. |
| 358 | MSG358 | In line | The Admin confirms an unknown-outcome resend without accepting the duplicate risk. | Confirm that you accept the duplicate-delivery risk before continuing. |
| 359 | MSG359 | In line | A ring difficulty policy version is published. | Version {version} has been published. |
| 360 | MSG360 | In red, under the text box | A ring difficulty field is invalid. | Enter a non-negative number with at most 4 decimals. / Cutoffs must increase: Easy < Medium < Hard < Very hard. / The Wax Casting cutoff must be below the Very hard cutoff. / Enter a whole number from 0 to 9. / Duplicate option code. |
| 361 | MSG361 | In line | The preview or publication finds active ring models that would be blocked. | {count} models would be blocked. The policy cannot be published. |
| 362 | MSG362 | In line | The preview proof is missing, expired or no longer matches the draft. | The preview has expired or no longer matches. Run the preview again. |
| 363 | MSG363 | In line | The effective policy or catalogue changed after the Admin loaded it. | The policy or catalogue has just changed. Your draft is kept. Reload the latest version and preview again. |
| 364 | MSG364 | In line | No review or ring design matches the reference given for a technical record. | No record matches reference {reference}. |
| 365 | MSG365 | In line | There is no workshop session today for the chosen branch scope. | There are no sessions today. |
| 366 | MSG366 | In line | There is no refused attempt or package availability change in the last 24 hours. | Nothing new in the last 24 hours. |
| 367 | MSG367 | In line | One audit source of the recent events cannot be read. | Part of the data is missing: one event source could not be read. |
| 368 | MSG368 | In line | The public review list, or the list after a filter, has no public review. | No public reviews yet. |
| 369 | MSG369 | In line | A review does not exist or is not public for the actor. | The requested review was not found. |
| 370 | MSG370 | In line | A Member's new review is saved and published. | Review published publicly. |
| 371 | MSG371 | In line | A Member's review edit is saved. | Review updated. |
| 372 | MSG372 | In line | The selected source is not a checked-in workshop ticket or a picked-up or delivered order line of the Member. | This source is not eligible for review. Only checked-in workshops and picked-up or delivered orders can be reviewed. |
| 373 | MSG373 | In line | The source already has a review. | This source already has a review. Each transaction can only be reviewed once. |
| 374 | MSG374 | In line | The rating, text, photos or text safety check fail validation. | Review input is invalid: {reason}. |
| 375 | MSG375 | In line | The Member has no source waiting for a review. | Nothing is waiting for your review. |
| 376 | MSG376 | In line | Some reviews of a multi-line retail order could not be sent. | Some reviews could not be sent. The remaining products are still below so you can try again. |
| 377 | MSG377 | In line | A review is hidden, restored or removed. | Review visibility updated. |
| 378 | MSG378 | In line | The moderation reason is missing, shorter than 3 or longer than 1,000 characters. | Enter a reason of 3–1,000 characters. |
| 379 | MSG379 | In line | A Manager reply is created or edited. | Reply saved. |
| 380 | MSG380 | In line | The reply text is empty or longer than 2,000 characters. | Enter a reply of 1–2,000 characters. |
| 381 | MSG381 | In line | A reply is created for a review that already has one. | This review already has an official reply from a Manager. |
| 382 | MSG382 | In line | A design version is saved and evaluated. | Design version {version} has been saved. Estimated price {price}, difficulty {level}. |
| 383 | MSG383 | In line | An option still has no published difficulty score. | This option is waiting for a difficulty score and cannot be selected yet. |
| 384 | MSG384 | In line | The Member reaches the gemstone limit. | Maximum aggregate limit of 10 gemstones reached. |
| 385 | MSG385 | In line | The Member reaches an engraving limit. | Maximum 2 text and symbol engraving placements and 2 laser images reached. |
| 386 | MSG386 | In line | Outer-band engraving is chosen while gemstones are mounted on the outer surface. | Outer band engraving is not allowed when gemstones are mounted on the outer surface. |
| 387 | MSG387 | In line | A selected option is invalid, unavailable or a limit is exceeded on save. | The ring configuration is invalid or some selected options are no longer available: {reasons}. |
| 388 | MSG388 | In line | The Member saves a design that is archived. | This design is archived and cannot be changed. |
| 389 | MSG389 | In line | The rule evaluation service is unavailable on save. | The pricing and rule evaluation is temporarily unavailable. Please try again later. |
| 390 | MSG390 | In line | The design is above the too-risky cutoff. | This design is too risky for a self-made workshop. Contact us for consultation or a custom design. |
| 391 | MSG391 | In line | No workshop package matches the design material. | No self-made workshop package is available for this material yet. |
| 392 | MSG392 | In line | A consultation message is empty or only spaces. | The message cannot be empty. |
| 393 | MSG393 | In line | A consultation message is longer than 2,000 characters. | The message exceeds the 2,000 character limit (current: {count}). |
| 394 | MSG394 | In line | A Member sent more than 30 messages within 60 seconds. | Rate limit reached. Please wait {seconds}s before sending again. |
| 395 | MSG395 | In line | A message is sent to a closed conversation. | This conversation is closed. Reopen it to send more messages. |
| 396 | MSG396 | In line | The selected design is unknown, not owned, archived, withdrawn or no longer current. | The design context does not exist, has been archived or cancelled, or does not belong to you. |
| 397 | MSG397 | In line | The Member has no design or design request to attach. | You have no active designs or requests available for consultation. |
| 398 | MSG398 | Dialog | A participant asks to close a conversation. | Are you sure you want to close this consultation? |
| 399 | MSG399 | Dialog | A participant asks to reopen a closed conversation. | Are you sure you want to reopen this consultation? |
| 400 | MSG400 | Dialog | A Member asks to remove a closed conversation from the list. | This conversation will only be hidden from your list. The store will retain the history. Continue? |
| 401 | MSG401 | In line | A Member tries to remove an open conversation. | Please close the conversation before removing it. |
| 402 | MSG402 | In line | A Member confirms a new consultation without choosing a design or general consultation. | Select Design Context |
| 403 | MSG403 | In line | Staff or a Manager claims a conversation. | Conversation claimed successfully. |
| 404 | MSG404 | In line | A claim targets a conversation already held by another consultant. | The conversation has already been claimed by another staff member. |
| 405 | MSG405 | In line | A Staff member opens a conversation that has no claimant. | You need to claim this conversation before sending a reply. |
| 406 | MSG406 | In line | Staff or a Manager releases a conversation. | Conversation released successfully. |
| 407 | MSG407 | In line | A Staff member tries to release a conversation claimed by someone else. | You are not the current claimant of this conversation. |
| 408 | MSG408 | In line | The consultation list has no conversation. | No conversations found. |
| 409 | MSG409 | In line | A Manager assigns a conversation to a branch. | Branch assigned successfully. |
| 410 | MSG410 | In line | The selected branch does not exist or is inactive. | The branch does not exist or is not active. |

:::
