## 2. Product Management

This design set covers feature FE-02 Product Management (spec `ppswbs-spec/features/002-product-management`). The backend module is `features.productmanagement`, a Spring Modulith module layered as `controller → service → dao → entity`. Every management mutation is idempotent (`Idempotency-Key`), checks optimistic `expectedVersion`, takes pessimistic locks in the order category → product → variant, and writes an audit entry in the same transaction. The module integrates with Cloudinary (image storage), Apache POI (Excel import), FE-01 Account Management (actor and branch scope), FE-03 Workshop Operations (selling locations), FE-06 Loyalty & Promotion (promotion targets) and FE-08 Retail Order (sale context and stock).

### 2.1 Class Diagram

(Class diagram)

::: {.docx-table width="100%" columns="8%,32%,60%" repeat-header="false"}

|No|Class|Description|
|---|---|---|
|1|ManagementProductApiController|REST endpoints under `/api/v1/catalogue-management/products`: create, update, soft delete, replace images, submit and decide the initial review, publish or unpublish.|
|2|ManagementPriceApiController|Endpoints for size quantity, sale status and the price proposal lifecycle: propose, revise, submit, decide and apply.|
|3|ManagementFileApiController|Multipart endpoints for image upload and Excel import: download template, validate file, confirm batch, download error report.|
|4|ManagementCategoryApiController|Category list, create, rename, impact preview and activation.|
|5|PublicProductApiController|Unauthenticated catalogue reads: product list and detail, categories, product images.|
|6|CatalogueAuthorization|Maps the current actor (from FE-01 `ActorContext`) to a `Capability`. Manager has every capability; Staff has every capability except `PRODUCT_APPROVE` and `PRICE_APPROVE`.|
|7|CatalogueCommandExecutor|Runs each command in one transaction: replays an existing `CatalogueCommandReceipt`, executes the work, saves the receipt, and records 4xx rejections in a separate transaction.|
|8|CatalogueLockCoordinator|Takes `PESSIMISTIC_WRITE` locks in ascending id order (category → product → variant) and checks `expectedVersion`.|
|9|CatalogueAuditWriter|Writes `CatalogueAuditEvent` rows; success in the caller's transaction, rejection in a new transaction.|
|10|CreateProductService|Creates a draft product with its sizes, branch stock, initial review revision and one draft price proposal per size. `createDraft` is shared by manual creation and Excel import.|
|11|ProductReviewService|Submits a product for initial review and lets a Manager approve (with final prices) or reject the whole review atomically.|
|12|ProductPublicationService|Publishes or unpublishes a product after checking prerequisites: active category, at least one ready image, an effective approved review and an applied price for every size.|
|13|PriceProposalService|Creates, revises, submits and decides a price proposal. Enforces at most one pending proposal per size and the unchanged terms digest.|
|14|PriceApplicationService|Applies an approved proposal to the size price, writes an immutable `PriceApplication` and is idempotent per proposal.|
|15|ProductCategoryService|Category commands and queries; names are unique by normalized `nameKey`; categories are deactivated, never deleted.|
|16|CatalogueMediaService|Verifies image type and size, stages a `CatalogueMediaObject`, uploads through `MediaStoragePort` and marks it ready; re-verifies unavailable media on a schedule.|
|17|MediaStoragePort / CloudinaryMediaStorageAdapter|Outbound port for object storage and its Cloudinary implementation (authenticated upload, readability check, signed read, delete).|
|18|ProductImportService|Orchestrates Excel import: template generation, file validation into a `ProductImportBatch`, all-or-nothing confirmation and error report.|
|19|ProductImportParser / ProductImportValidator|Streams the first sheet with POI SAX under size and row limits, then validates rows, groups them by group code and collects every row error.|
|20|ManagementProductQueryService / PublicCatalogueQueryService|Read models that map entities to management and public DTOs.|
|21|CatalogueSalesFacade / CatalogueSalesService|Facade exported to FE-08 for locked sale context, accepted sale recording and media reference retention.|
|22|SellingLocationPort|SPI owned by FE-02 and implemented by FE-03 to list active selling branches.|
|23|LoyaltyProductTargetAdapter|Implements FE-06 `PromotionTargetPort` for the PRODUCT scope.|
|24|ProductRepository, ProductVariantRepository, PriceProposalRepository, … |Spring Data JPA repositories, including locking queries such as `lockAllByIdIn`.|
|25|Product|Product aggregate root: name, description, category, published flag, first publication time, soft delete and content version.|
|26|ProductVariant|One ring size of a product: SKU, remaining unsold quantity, sale status, applied price, applied and pending proposal pointers, applied sequence.|
|27|VariantBranchStock|Remaining quantity and sale status of a size at one branch.|
|28|ProductCategory|Product category with normalized unique name and active flag.|
|29|ProductReview|Revisioned initial review of product content with snapshot, state and effectiveness.|
|30|PriceProposal|Revisioned price proposal for a size: proposed and approved amount, state, terms digest and superseded proposal.|
|31|PriceApplication|Immutable record of an applied price change (old amount, new amount, actor, time).|
|32|ProductImage / CatalogueMediaObject|Product image slot (max 10) and the stored media object with its lifecycle state.|
|33|ProductImportBatch|Validated or rejected Excel batch with parsed payload, errors, 60-minute expiry and created products.|
|34|CatalogueCommandReceipt / CatalogueAuditEvent|Idempotency receipt per (actor, action, commandId) and operational audit event.|

:::

### 2.2 Sequence Diagram — Upload Product Image and Publish Product

(Sequence diagram)

The image is verified (JPEG, PNG or WebP, at most 5 MB) and staged as a `CatalogueMediaObject` before it is uploaded to Cloudinary through `MediaStoragePort`, outside any transaction or lock. If Cloudinary is unavailable the object stays `STAGED` and the client retries with the same idempotency key; on success the object becomes `READY`. Replacing the product images checks at most 10 images, unique positions and that every media object is `READY` and owned by the uploader. Publishing locks the product and checks the prerequisites: active category, at least one ready image, an effective approved review for a product never published before, and an applied price for every size. Missing prerequisites return `PUBLICATION_PREREQUISITES_MISSING`.

### 2.3 Sequence Diagram — Submit and Approve Product Review

(Sequence diagram)

Before the first publication, Staff submit the product for an initial review. The service captures a content snapshot, moves the review and every draft size proposal to `PENDING`, and points each size to its pending proposal. A Manager decides the whole review at once: approval sets the final price of every size, rejection requires a reason. A Staff caller is refused with `403`. A review that is no longer current, or whose content changed after submission, returns `REVIEW_NOT_CURRENT`. After the first publication, content changes are saved directly and do not need this review.

### 2.4 Sequence Diagram — Propose, Approve and Apply a Price

(Sequence diagram)

Staff create a draft proposal for a size and submit it; the size then points to the pending proposal, which blocks a second pending proposal. A Manager approves, optionally changing the amount, or rejects with a reason; a Staff caller is refused with `403`. Applying the approved price is a separate command available to Staff and Manager: it checks that the proposal is still effective, approved and based on the same terms and applied sequence, updates `ProductVariant.appliedPrice`, and writes a `PriceApplication`. Repeating the command for the same proposal returns `changed=false`. Public catalogue reads and FE-08 checkout read the applied price through `CatalogueSalesFacade`.

### 2.5 Sequence Diagram — Import Products from Excel

(Sequence diagram)

The flow has two commands. **Validate** parses the uploaded `.xlsx` and stores a `ProductImportBatch` that is either `VALIDATED` (with a preview payload) or `REJECTED` (with every row error); it never creates products. File-level failures (wrong format, macro, more than 5 MB or 500 rows) are rejected before a batch exists and are audited as rejections. **Confirm** locks the batch, rechecks expiry, version and category activity, and then calls `CreateProductService.createDraft` for every product group inside one transaction, so the import is all-or-nothing. A category deactivated after the preview makes the batch stale (`IMPORT_PREVIEW_STALE`). Imported products are drafts without images, so the user adds images before submitting them for review.

### 2.6 Sequence Diagram — Manage Product Categories

(Sequence diagram)

Staff and Manager list, create, rename and deactivate categories. A category name must be unique system-wide after normalization (`DUPLICATE_CATEGORY_NAME`), including inactive categories. Rename and activation changes use `expectedVersion` and return `STALE_VERSION` on a mismatch. Before deactivation, the user sees the impact (the number of products in the category). Deactivation removes the category's products from the public catalogue but does not change the products themselves; categories are never deleted.

### 2.7 Sequence Diagram — Browse Product Catalogue

(Sequence diagram)

Guests and Members read active categories and a paged product list without signing in. `PublicCatalogueQueryService` first selects the ids of eligible products (published, not deleted, active category, ready image), then loads them with their sizes and builds the price range and cover image for each product.

### 2.8 Sequence Diagram — View Product Details

(Sequence diagram)

The detail endpoint returns a product only while it is public; otherwise it returns `404`. The response lists each size with its applied price, remaining quantity and sale status. Images are streamed through the backend from Cloudinary using a signed URL, with `Cache-Control: no-store`, so the storage address is never exposed to the browser.
