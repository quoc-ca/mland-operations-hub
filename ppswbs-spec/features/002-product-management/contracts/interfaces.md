# Interface Contracts: Product Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-29 (`readSaleFacts` cho giỏ hàng FE-08 theo xác nhận của owner FE-08)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Approved — duyệt bởi Đặng Thị Khương ngày 2026-10-24; contract là baseline, provider/identity/FE-08 prerequisites chưa có implementation.   Phần bổ sung import Excel (US9) được duyệt bởi Đặng Thị Khương ngày 2026-10-24. Các cập nhật ngày 2026-10-25 và 2026-10-26 (sửa lỗi chữ, Manager quản lí danh mục, phạm vi ưu đãi FE-06, V1 không MFA theo 001, T102, kết quả speckit-analyze) được Đặng Thị Khương duyệt ngày 2026-10-26.
**REST contract**: [OpenAPI 3.0.3](product-management.openapi.json). Trường, URL và errors ở đây phải khớp OpenAPI trước khi code; đây không phải endpoint đã hoạt động.

## Identity and capability boundary

Identity owner cung cấp qua facade identity của 001 (tên module do 001 chốt, đề xuất `accountmanagement`) một actor value gồm opaque actorId, verified identity reference, accountActive và capability result hiện hành. Catalogue không đọc members entity, không log raw token và không suy role từ Firebase/email. Staff/Manager là nhãn nghiệp vụ, không tự chuyển sang OWNER hoặc mã role khác.

Ngày 2026-10-24 người dùng đã chốt MEMBER/STAFF/MANAGER/ADMIN, MANAGER là vai trò riêng, Admin quản lí tài khoản, vai trò, trạng thái khóa, RBAC, tham số vận hành và credential tích hợp, nhưng ADMIN không có capability catalogue nào ở dưới (không quản lí sản phẩm, danh mục, giá, số lượng, công khai hay duyệt); đăng ký khách không cấp quyền nội bộ. Người dùng cũng đồng ý hướng A: dùng chung primitive xác thực theo [spec 001](../../001-account-management/spec.md) qua identity owner, bearer cho mọi REST API, revocation/trạng thái và thành công về `/`. API không dùng cookie thay bearer (001 FR-001); contract xác thực cho frontend React do 001 chốt. Không yêu cầu xác minh email lại trên session hợp lệ. Đây là quyết định FE-02, chưa phải RBAC/session đã triển khai; V1 không yêu cầu MFA cho vai trò nội bộ (chốt 2026-10-26 theo 001).

Không dùng trạng thái tài khoản Member hoặc Member bootstrap làm quyền nội bộ. Route/cookie của thiết kế Member Profile cũ trong 001 đã bị spec 001 (2026-10-25) thay thế; không dùng chúng làm bằng chứng xác thực hoặc hoàn tất MFA. Identity owner cung cấp contract dùng chung theo 001; productmanagement chỉ tiêu thụ actor/capability (và kết quả xác thực bổ sung nếu 001 có quy định). Không tạo trang đăng nhập/MFA riêng trong productmanagement hoặc ép người dùng Member xác minh email lại.

| Action | Required business authority | Denial |
| --- | --- | --- |
| Public catalogue/detail/media | Public eligibility, không cần identity | 404 nếu hidden/deleted/ineligible; 503 nếu dependency chưa xác minh được. |
| Catalogue management read/create/edit/media/quantity/status/delete | Staff hoặc Manager active có quyền action tương ứng | 401 thiếu/invalid identity, 403 thiếu quyền/inactive. |
| Product/price decision | Manager active có quyền approve | Staff bị 403; Manager tự tạo/tự duyệt được. |
| Price application/publication | Staff hoặc Manager active có quyền action tương ứng | Không coi approval là permission để bất kỳ người nào apply/publish. |
| Category management | Staff hoặc Manager active có quyền category | Admin/Member/Guest bị 401/403; Manager được quản lí danh mục như Staff (chốt 2026-10-26). |

Capability names đề xuất: catalogue.read-internal, product.create, product.edit, product.submit, product.approve, product.publish, product.delete, media.manage, variant.quantity, variant.sale-status, price.propose, price.approve, price.apply, category.manage. Đây là action vocabulary của hợp đồng, không phải các DB role/capability đã được provision.

API management chỉ nhận backend-verified bearer identity; cookie không thay bearer. Frontend React gửi cùng bearer; mỗi lệnh ghi mang commandId và expectedVersion trong DTO. Hết phiên hoặc credential không hợp lệ trả 401 AUTHENTICATION_REQUIRED; hành vi đăng nhập lại/recovery do 001 định nghĩa. CORS theo origin của frontend, không vô hiệu hóa toàn cục. Chưa có session/capability facade thật nên đường quản trị không được enable bằng fallback permitAll. Lỗi session không tự điều hướng sang hồ sơ/chính sách Member; identity owner phải cung cấp đích đăng nhập nội bộ cố định, không nhận arbitrary returnTo.

## API conventions

- Prefix công khai `/api/v1/products`, `/api/v1/product-categories`, `/api/v1/product-media`; quản trị `/api/v1/catalogue-management/...`. Tên tài nguyên plural kebab-case.
- ID và amount/quantity là JSON decimal string. Amount 1–19 chữ số dương, quantity `0` hoặc 1–19 chữ số dương, không dấu `+`, dấu thập phân, exponent hay leading zero. IDs nằm trong positive signed BIGINT. Size là integer 5–29. Timestamps UTC ISO-8601, expectedVersion là nonnegative int64. DTO reject unknown/missing/wrong-type fields, không âm thầm ignore khắc hoặc SKU client tự đặt.
- Mọi mutation có header `Idempotency-Key` UUID. Form web có hidden commandId tương đương. Receipt scope actor/action/key, request digest gồm target, expectedVersion và payload chuẩn hóa; cùng key khác payload 409 IDEMPOTENCY_KEY_REUSED.
- ExpectedVersion nằm trong body cho update/action; DELETE dùng query expectedVersion. Creation không có existing version. Role/approved amount chỉ nhận ở đúng Manager decision DTO, không nhận trong product update.
- JSON mutation thành công trả resource/action result đã xác nhận. Replay cùng key trả lại kết quả commit gốc trong quyền hiện tại. State no-op hợp lệ trả changed=false; deleted không được public lại bằng old key. Receipt lookup chỉ cho đúng actor hiện có quyền, không lộ request payload.
- Management response và mọi error no-store; public read không bảo đảm giá/stock cho giao dịch mới, mặc định không cache trong thiết kế này cho đến khi có cache policy được duyệt.
- A-001/A-002 và quy ước “5 MB” = 5.000.000 bytes đã được người dùng chốt ngày 2026-10-24; không lấy multipart mặc định framework làm giới hạn nghiệp vụ. Miền kỹ thuật của data-model vẫn là thiết kế draft, không tự phê duyệt trần kinh doanh. Request media tối đa 5.100.000 bytes gồm multipart overhead là giới hạn vận chuyển đề xuất; một file/request, ảnh gắn sản phẩm tối đa 10.

## Resource and mutation semantics

| Route suffix under management | Contract |
| --- | --- |
| GET/POST products | List theo q/category/published/page; tạo name, description, categoryId, variants[{size, proposedPrice, quantity}], images[{mediaId, altText, position}]. Không price bán trực tiếp. |
| GET/PUT/DELETE products/{productId} | Detail; PUT chỉ name/description/categoryId + expectedVersion; DELETE xác nhận riêng, soft delete và bảo toàn history. |
| PUT products/{productId}/images | Replace cả image-reference list đã upload/READY, expectedVersion; same image count/order/alt constraints, không upload dưới product lock. |
| POST media-uploads | Multipart đúng một file; MIME + signature/decodability phải phù hợp JPEG/PNG/WebP, byte limit và actor ownership. Trả mediaId/state; không trả object secret hoặc public URL của draft. |
| POST products/{productId}/review-submissions | expectedVersion; tạo current immutable review và pending proposals; không tạo vòng gửi giá lần hai. |
| POST products/{productId}/review-decisions | reviewId, reviewRevision, contentVersion, decision APPROVE/REJECT, reason, prices[{variantId, proposalId, approvedPrice}]. APPROVE cần đầy đủ mọi size đúng snapshot; REJECT cần reason và không duyệt một phần. |
| PUT products/{productId}/publication | expectedVersion + published boolean; validate initial review và mọi applied price; không check stock>0 làm publication condition. |
| PUT variants/{variantId}/quantity | expectedVersion + quantity; source hold guard giữ xuyên suốt compare/save/audit. Không cancel hold để giảm lượng. |
| PUT variants/{variantId}/sale-status | expectedVersion + saleEnabled; giữ nguyên quantity/size/SKU/publication. |
| POST variants/{variantId}/price-proposals | expectedVersion + proposedPrice; tạo draft, chặn nếu size đã có current PENDING. |
| POST price-proposals/{proposalId}/revisions | expectedVersion + proposedPrice; tạo revision mới, supersede revision cũ và clear pending pointer, không sửa quyết định lịch sử. |
| POST price-proposals/{proposalId}/submissions | expectedVersion; submit current draft dưới variant lock, một pending/size. |
| POST price-proposals/{proposalId}/decisions | expectedVersion, decision APPROVE/REJECT, approvedPrice/reason tương ứng; Manager only. Với initial product review phải đi review-decisions để whole-review atomic, không approve riêng lẻ rồi coi hồ sơ đã duyệt. |
| POST price-proposals/{proposalId}/applications | expectedVersion; body không nhận số tiền mới. Check đúng approved revision/baseAppliedSequence; initial review phải valid. |
| GET product-imports/template | `downloadProductImportTemplate`: trả `.xlsx` mẫu (sheet "Sản phẩm" với cột Mã nhóm, Tên sản phẩm, Mô tả, Danh mục, Size, Giá đề xuất (VND), Số lượng; sheet hướng dẫn; sheet danh mục đang hoạt động). Cần `product.create`. |
| POST product-imports | `validateProductImport`: multipart đúng một file `.xlsx`; kiểm tra MIME + chữ ký ZIP/OOXML, không macro (`.xlsm`/vbaProject), ≤ 5.000.000 bytes (request ≤ 5.100.000), ≤ 500 dòng dữ liệu, chỉ sheet đầu. Trả 201 `ProductImportBatch` state VALIDATED (kèm `preview`) hoặc REJECTED (kèm `errors[{row,column,value,code,message}]`, mọi lỗi, tối đa 4.000 phần tử). Lỗi dòng không phải HTTP lỗi; lỗi mức file là 400/413/415. Không tạo product, product id hoặc SKU. Cần `Idempotency-Key`; cùng key/cùng digest (file SHA-256) trả lại batch cũ. |
| GET product-imports/{batchId}, GET product-imports/{batchId}/error-report | `getProductImport` đọc trạng thái/kết quả (kiểm tra sau mất phản hồi); `downloadProductImportErrorReport` tải `.xlsx` lỗi (ô bắt đầu bằng `=`,`+`,`-`,`@` được vô hiệu hóa). Chỉ actor tạo batch và còn `product.create`; batch người khác trả 404 `IMPORT_BATCH_NOT_FOUND`; batch quá hạn trả 409 `IMPORT_BATCH_EXPIRED` cho `error-report` (errors đã bị xóa). `template` là đoạn đường dẫn cố định, `batchId` luôn là UUID nên không va chạm. |
| POST product-imports/{batchId}/confirmations | `confirmProductImport`: body `expectedVersion` của batch + `Idempotency-Key`. Chỉ batch VALIDATED chưa hết hạn (60 phút). Tái kiểm tra toàn bộ payload với dữ liệu mới nhất rồi tạo mọi sản phẩm bằng đúng `CreateProductService` trong **một** transaction cùng audit + receipt; trả `ProductImportBatch` state IMPORTED với `created[{groupKey, productId, skus}]`. Có lỗi/lỗi lưu/lỗi audit → không tạo gì. Gửi lặp cùng key trả kết quả đã commit. |
| GET/POST categories, PUT categories/{categoryId} | List/create/update name/description với unique normalized name toàn hệ thống. |
| GET categories/{categoryId}/impact | Số lượng và danh sách phân trang các sản phẩm bị ảnh hưởng; active/version hiện hành. Không public dữ liệu quản trị. |
| PUT categories/{categoryId}/activation | expectedVersion + active; phản ánh kết quả sau xác nhận impact. Không xóa product publication flag/history. |
| GET command-receipts/{commandId} | Chỉ actor sở hữu, có quyền hiện tại; query action bắt buộc để đúng receipt scope. Chưa có receipt trả 404 COMMAND_RESULT_NOT_FOUND, không khẳng định command chắc chắn chưa được xử lí. |

**Quy tắc import Excel (FR-039–FR-045)**: dùng capability `product.create` (không thêm capability mới); Admin/Guest/Member bị 403/401 ở mọi operation import. Hàng tiêu đề cố định ở dòng 1, đúng 7 cột như mẫu (không cột lạ/thiếu; thứ tự cột phải đúng mẫu); dòng trống hoàn toàn ở cuối bị bỏ qua, dòng trống xen giữa là lỗi. Ô công thức, ô gộp, hyperlink và giá trị lỗi Excel bị báo lỗi (không đọc giá trị cache). Ô số: số thật phải là số nguyên không vượt 15 chữ số có nghĩa; ô văn bản chỉ nhận chuỗi chữ số đúng quy tắc Amount/Quantity (không dấu phân tách, không `+`, thập phân, mũ, leading zero) — còn lại báo `INVALID_NUMBER`. Size nhận số nguyên 5–29: ô số có giá trị nguyên (ví dụ 5, Excel lưu nội bộ là 5.0) được chấp nhận; ô văn bản `5.0` hoặc ô số có phần thập phân khác 0 bị từ chối. Danh mục so khớp tên đã trim, không phân biệt hoa/thường với danh mục đang hoạt động (sai → `CATEGORY_NOT_ACTIVE_OR_UNKNOWN`). Nhóm theo `Mã nhóm` (trim, phân biệt hoa/thường, ≤ 100 ký tự); các dòng cùng nhóm phải trùng tên/mô tả/danh mục (`GROUP_FIELD_MISMATCH`), size không trùng (`DUPLICATE_SIZE_IN_GROUP`), ≤ 25 size/nhóm. Mã lỗi dòng: `REQUIRED`, `TOO_LONG`, `INVALID_NUMBER`, `OUT_OF_RANGE`, `CATEGORY_NOT_ACTIVE_OR_UNKNOWN`, `GROUP_FIELD_MISMATCH`, `DUPLICATE_SIZE_IN_GROUP`, `TOO_MANY_SIZES`, `FORMULA_NOT_SUPPORTED`, `EMPTY_ROW_IN_DATA`. Tên sản phẩm không yêu cầu duy nhất nên không có lỗi trùng tên. Một file không chứa ảnh/SKU/trạng thái/giá bán.

Decision reason đã được chốt tối đa 2.000 ký tự, bắt buộc không rỗng sau trim khi REJECT; contract đề xuất đếm code point. Không nhận số tiền ở REJECT. APPROVE product cần prices của mọi variant đúng snapshot, không duplicate; Manager có thể điều chỉnh approvedPrice hợp lệ. Danh sách variant bất biến nên update product không có variants. saleEnabled=true cho size mới và active=true cho danh mục mới đã được chốt, chưa cấp quyền mua cho product unpublished.

## Error contract

Giữ shape common.ErrorResponse: code, message an toàn, correlationId, timestamp, fieldErrors[{field,message}] tùy chọn. Không dùng message Member-policy cũ cho lỗi Staff/Manager. Mọi tình huống phải giữ dữ liệu trước đó khi mutation không được chấp nhận.

| HTTP | Codes | Behavior |
| --- | --- | --- |
| 400 | VALIDATION_ERROR, UNSUPPORTED_FILTER, INVALID_SORT, UNKNOWN_FIELD, IMPORT_FILE_INVALID (hỏng/không phải OOXML/có macro/header sai-thiếu cột/sheet đầu trống), IMPORT_ROW_LIMIT_EXCEEDED (> 500 dòng) | Trường/dòng lỗi; không query/ghi với tiêu chí sai. |
| 401 | AUTHENTICATION_REQUIRED, INVALID_CREDENTIAL | Không tạo phiên/quyền từ credential lỗi. |
| 403 | CATALOGUE_ACCESS_DENIED | Không tiết lộ protected resource. |
| 404 | PRODUCT_NOT_FOUND, VARIANT_NOT_FOUND, CATEGORY_NOT_FOUND, MEDIA_NOT_FOUND, PROPOSAL_NOT_FOUND, COMMAND_RESULT_NOT_FOUND, IMPORT_BATCH_NOT_FOUND | Public hidden/deleted giống không có; không reveal draft. |
| 409 | STALE_VERSION, DUPLICATE_CATEGORY_NAME, PENDING_PRICE_PROPOSAL_EXISTS, REVIEW_NOT_CURRENT, PRICE_NOT_APPROVED, PRICE_TERMS_CHANGED, PRICE_APPLICATION_STALE, PUBLICATION_PREREQUISITES_MISSING, QUANTITY_BELOW_HELD, SIZE_LIST_IMMUTABLE, PRODUCT_DELETED, IDEMPOTENCY_KEY_REUSED, IMPORT_HAS_ERRORS (xác nhận batch REJECTED), IMPORT_BATCH_EXPIRED, IMPORT_PREVIEW_STALE (tái kiểm tra khi xác nhận thấy dữ liệu đổi; batch chuyển REJECTED, không tạo gì) | Tải latest trong quyền hiện tại, sửa/xác nhận lại; không ghi đè/hủy giao dịch cũ. |
| 413 | MEDIA_TOO_LARGE, IMPORT_FILE_TOO_LARGE | Không attach object một phần / không tạo batch; file quá giới hạn bị từ chối. |
| 415 | MEDIA_TYPE_UNSUPPORTED, IMPORT_FILE_TYPE_UNSUPPORTED | Không nhận định dạng ảnh khác / chỉ nhận `.xlsx`. |
| 503 | IDENTITY_DEPENDENCY_UNAVAILABLE, AVAILABILITY_DEPENDENCY_UNAVAILABLE, MEDIA_DEPENDENCY_UNAVAILABLE, CATALOGUE_SAVE_UNAVAILABLE, CATALOGUE_BUSY | Lỗi tạm thời riêng, không trả empty/held=0; cho kiểm tra receipt/thử lại cùng key. |

## Frontend journeys (React + Vite, REST-only)

Frontend là ứng dụng React + Vite tách riêng, render phía client và chỉ gọi các REST operation ở trên. Backend không render UI hoặc HTML form. Client route, thư viện routing/state và cơ chế i18n do kế hoạch frontend chốt, không chọn ở đây; `ppswbs_frontend/` hiện chưa khởi tạo.

| Màn hình | REST operations | Hành vi UI bắt buộc |
| --- | --- | --- |
| Danh sách / chi tiết công khai | `GET /api/v1/products`, `GET /api/v1/product-categories` | Public; GET không tạo cart/hold. Không phát minh route checkout của FE-08 nếu chưa có contract riêng; chọn mua chuyển sang FE-08 qua handoff đã có contract. |
| Danh sách / tạo / chi tiết sản phẩm quản trị | GET/POST `products`, GET/PUT/DELETE `products/{productId}` | Mỗi lệnh ghi có commandId + expectedVersion; Manager được tự duyệt. Xóa qua hộp thoại xác nhận riêng. |
| Ảnh sản phẩm | POST `media-uploads`, PUT `products/{productId}/images` | Tải multipart một file rồi attach bằng mediaId; không dùng URL tùy ý; hiển thị tiến trình, lỗi và thử lại cùng key. |
| Gửi duyệt / quyết định | POST `review-submissions`, POST `review-decisions` | Hiển thị snapshot đúng; REJECT cần lý do; không duyệt một phần. |
| Công khai / ẩn / lượng bán / ngừng bán | PUT `publication`, PUT `variants/{variantId}/quantity`, sale-status operation | Proposed/approved/applied amounts và state hiển thị riêng. |
| Đề xuất giá | price-proposal operations (revise/submit/decide/apply) | Chỉ apply đúng giá đã duyệt; không có amount field ở apply. |
| Danh mục | category operations và impact | Tên duy nhất, xác nhận tác động, không mất lịch sử. |
| Import sản phẩm từ Excel | `product-imports/template`, `POST product-imports`, `GET product-imports/{batchId}`, `error-report`, `confirmations` | Tải mẫu → chọn file (kiểm tra phía client chỉ là gợi ý: đuôi `.xlsx`, dung lượng) → xem kết quả kiểm tra: bảng lỗi theo dòng/cột + nút tải báo cáo lỗi, hoặc bản xem trước số sản phẩm/biến thể → hộp thoại xác nhận riêng → kết quả liệt kê mã sản phẩm/SKU với liên kết sang chi tiết. Sinh commandId cho từng bước; sau mất phản hồi gọi `getProductImport` trước khi thử lại cùng key; batch hết hạn hoặc 409 `IMPORT_PREVIEW_STALE` yêu cầu tải/kiểm tra lại. Nhắc rõ sản phẩm import chưa có ảnh và phải thêm ảnh trước khi gửi duyệt. |

Hành vi chung: mỗi màn hình có loading, empty (khác dependency error), validation (lỗi gắn đúng trường, giữ dữ liệu đã nhập), success và failure/recovery. 409 STALE_VERSION tải latest và yêu cầu xác nhận lại; 503 cho kiểm tra receipt/thử lại cùng commandId; lỗi hiển thị correlation ID. Nhãn/input có tên rõ, hỗ trợ vi/en theo cơ chế i18n frontend, không tự tuyên bố đạt một accessibility standard chưa phê chuẩn. Ẩn nút hoặc route guard phía client không thay authorization phía server.

## Module facade contracts

### CatalogueSalesFacade — only named facade export

- `withLockedSaleContext(variantIds, work)`: FE-08 consumer đã xác minh vào category/product/variant gates theo sorted IDs, kiểm tra đủ public/sale/price prerequisites, trả immutable facts (productId, variantId, sku, size, appliedPrice, currency, remainingUnsold, versions). Callback trong cùng local transaction, sau đó FE-08 adapter khóa guard nguồn trước check availability/create hold. Không trả entities hoặc để lock scope kết thúc trước insert hold.
- `withLockedHistoricalContext(acceptedTransactionRef, variantIds, work)`: Không đòi product còn public hoặc chưa deleted, nhưng chỉ cho settlement/release của giao dịch đã chấp nhận, identity/reference do trusted FE-08 consumer xác minh; không cấp quyền new hold. Dùng cùng lock order.
- `recordAcceptedSale(operationRef, variantId, quantity)`: Internal FE-08 call trong guarded transaction, nhận operationRef duy nhất; chốt remaining đúng một lần cùng xử lí hold của FE-08. Không phải public REST, không nhận operationRef từ Guest. Validation quantity >=0 sau settlement xét snapshot post-settlement cùng transaction; rollback cả hai phía nếu lỗi.

Không dùng facade kiểu getEntity/setField và không cho frontend gọi method nội bộ này để né quyền. FE-08 phải hoàn thiện boundary invocation/authentication của consumer trước integration; facade chỉ nhận records/scalars và callback nghiệp vụ giới hạn.

**Kiểu Java đã có trong `productmanagement::facade` (2026-10-27, phía 002; adapter FE-08 chưa có)**:

- `CatalogueSalesFacade`: `withLockedSaleContext(Collection<Long> variantIds, Function<SaleContext, T> work)`, `withLockedHistoricalContext(String acceptedTransactionRef, Collection<Long> variantIds, Function<SaleContext, T> work)`, `recordAcceptedSale(UUID operationRef, long variantId, BigInteger quantity) → AcceptedSaleResult`, `retainMediaReferences(String ownerRecordRef, Collection<UUID> mediaIds)`, `releaseMediaReferences(String ownerRecordRef, Collection<UUID> mediaIds)`.
- Record: `SaleContext(asOf, acceptedTransactionRef, variants)`, `SaleVariantFact(productId, productName, variantId, sku, size, appliedPrice, currency, remainingUnsold, saleEnabled, productDeleted, publiclyEligible, productVersion, productContentVersion, variantVersion, appliedSequence, imageMediaIds)`, `HeldSnapshot(variantId, heldQuantity, sourceRevision)`, `AcceptedSaleResult(operationRef, variantId, quantity, remainingAfter, variantVersion, replayed)`.
- Lỗi: `CatalogueSalesException` với `reason()` ∈ `INVALID_REQUEST`, `NOT_FOUND`, `NOT_FOR_SALE`, `INSUFFICIENT_QUANTITY`, `OPERATION_CONFLICT`, `MEDIA_NOT_AVAILABLE`, `NO_LOCKED_CONTEXT`, `DEPENDENCY_UNAVAILABLE`, `BUSY` và `variantIds()` (các size liên quan). Mọi `CatalogueSalesException` làm transaction của bên gọi rollback-only; exception do callback của FE-08 ném ra được giữ nguyên.
- Chi tiết triển khai do 002 chọn (contract trước đó chưa ghi): mọi method `MANDATORY` (gọi ngoài transaction bị từ chối); bên gọi nên dùng READ COMMITTED như lệnh catalogue. `operationRef` là UUID do FE-08 sinh; chống trừ hai lần bằng `catalogue_command_receipts` (actor `readyringsales`, action `sale.accepted`), gửi lại cùng size/số lượng trả kết quả cũ (`replayed=true`), khác size/số lượng → `OPERATION_CONFLICT`. `recordAcceptedSale` chỉ chạy khi size đã được khóa bởi một context đang mở trên cùng thread (`NO_LOCKED_CONTEXT` nếu không). Media của FE-08 ghi registry với `owner_module = readyringsales`, `ownerRecordRef` 1–128 ký tự.
- `readSaleFacts(Collection<Long> variantIds) → List<CartSaleFact>` (thêm 2026-10-29 theo xác nhận của Bùi Trọng Thịnh, owner FE-08): chỉ đọc, không khóa catalogue, không lấy guard nguồn, chỉ dùng cho thêm vào giỏ/xem giỏ. `CartSaleFact(variantId, productId, productName, sku, size, appliedPrice, currency, saleEligible, availableQuantity)`, mỗi ID khác nhau một phần tử, thứ tự variant ID tăng dần. `saleEligible` = sản phẩm đủ điều kiện công khai (đã công khai, chưa xóa, danh mục hoạt động, có ảnh dùng được, mọi size có giá áp dụng) và size đang bán. `availableQuantity` = còn lại − đang giữ (qua `AvailabilityGuardPort.readSnapshots`), không âm; size không đủ điều kiện trả `saleEligible=false`, `availableQuantity=0` chứ không báo lỗi. Gọi được có hoặc không có transaction (tự mở transaction chỉ đọc). Lỗi: rỗng/null → `INVALID_REQUEST`; size không tồn tại → `NOT_FOUND` kèm `variantIds()`; nguồn FE-08 thiếu/lỗi → `DEPENDENCY_UNAVAILABLE`. Kết quả chỉ để hiển thị: checkout accept vẫn bắt buộc `withLockedSaleContext` (kiểm tra lại dưới khóa, `NOT_FOR_SALE` nếu đã đổi).

### AvailabilityGuardPort — productmanagement owns SPI, FE-08 implements

- `readSnapshots(variantIds, asOf)`: Batch exact active held quantity + source revision theo variant; không cache và không thay unknown bằng 0. Bộ chưa có hold trả 0 chỉ khi nguồn thật xác minh thành công.
- `withGuard(variantIds, asOf, work)`: Join transaction bắt buộc; giữ guard row ổn định trên từng variant kể cả khi chưa có hold, đọc snapshot hợp lệ và giữ khóa xuyên callback/commit. Không trả lazy entity, không mở REQUIRES_NEW tách source khỏi quantity save.
- FE-08 tạo/chốt/giải phóng giữ cũng phải dùng guard trên. asOf là thời điểm UTC của operation, cùng deadline semantics FE-08; event late/uncertain không được productmanagement tự giải thích hoặc hủy giữ.
- Adapter readyringsales có compile dependency vào productmanagement::facade, không gọi ngược productmanagement khi đã giữ source guard. Productmanagement không import readyringsales và không sao chép hold lifecycle. Thiếu adapter trả typed 503; test-only fixture phải được gắn rõ, không enable trong runtime thường.
- Kiểu Java (2026-10-27): `Map<Long, HeldSnapshot> readSnapshots(Collection<Long> variantIds, Instant asOf)` và `<T> T withGuard(Collection<Long> variantIds, Instant asOf, Function<Map<Long, HeldSnapshot>, T> work)`. 002 truyền ID theo thứ tự tăng dần; kết quả phải có đúng một snapshot cho mỗi ID yêu cầu, thiếu/thừa/sai ID bị coi là không xác minh được (503). Nguồn không đọc được nên ném `CatalogueSalesException` `DEPENDENCY_UNAVAILABLE`. Binding phía 002 là `CatalogueAvailabilityService` (cũng là `VariantHoldReader` runtime).

- **Adapter thật (2026-10-30)**: FE-08 cài `readyringsales/service/RetailAvailabilityGuardService` (develop `b0e7796`): guard row tạo khi cần bằng upsert rồi khóa bi quan theo ID tăng dần; `readSnapshots` cộng các hold có trạng thái `HELD`, trả 0 khi size chưa từng có hold; `sourceRevision` là version của guard row, đổi mỗi lần tạo/chốt/nhả/hết hạn hold. Hold quá hạn vẫn được tính cho tới khi FE-08 chuyển sang `EXPIRED` (FE-08 sở hữu hết hạn; 002 không suy từ deadline, nên không bán vượt). Accept checkout của FE-08 (`RetailCheckoutAcceptanceService`) gọi `withLockedSaleContext` → `retainMediaReferences` → `withGuard` → tạo hold → reserve loyalty; giỏ hàng gọi `readSaleFacts` (`CatalogueRetailAdapter`). Từ develop `82d7a7d` (2026-10-31): thanh toán (`SimulatedRetailPaymentOutcomeService`, profile `retail-payment-sim`, chỉ bằng chứng cổng thanh toán là mô phỏng) và đơn 0đ đi `withLockedHistoricalContext` → chốt hold trong `withGuard` → `recordAcceptedSale` (operationRef UUID cố định theo checkout + size); hủy trước thanh toán (`RetailCheckoutCancellationService`) đi `withLockedHistoricalContext` → nhả hold. Chưa có: `releaseMediaReferences` (khi hết thời hạn lưu dòng đơn) và luồng hết hạn chưa thanh toán.

### PromotionTargetPort — FE-06 owns SPI, productmanagement implements

- Port và boundary types thuộc `loyaltypromotion::facade` ([FE-06 interfaces](../../006-loyalty-promotion/contracts/interfaces.md)). productmanagement triển khai bằng `LoyaltyProductTargetAdapter` trong `service/` cho dimension PRODUCT/VARIANT.
- Phụ thuộc được phép: `productmanagement → loyaltypromotion::facade`. Không import service/entity của FE-06; FE-06 không gọi ngược productmanagement.
- Adapter trả immutable facts/guard cho mỗi product/variant: tồn tại, đã xóa mềm, đang công khai, đang bán. Cấu hình chương trình chỉ bị từ chối khi target không tồn tại, variant không thuộc product hoặc đã xóa mềm; sản phẩm chưa công khai/đang ẩn/ngừng bán vẫn được chọn (spec Clarification 2026-10-26). Khả năng áp dụng ưu đãi lúc checkout do FE-06/FE-08 quyết định; không trả entity và không gọi remote dưới khóa. Thiếu dữ liệu hoặc lỗi phụ thuộc trả lỗi an toàn để FE-06 trả DEPENDENCY_UNAVAILABLE.

### MediaStoragePort — productmanagement service boundary

- Cloudinary đã được người dùng chọn. MediaStoragePort vẫn độc lập provider; adapter dùng signed backend upload, resource_type=image/type=authenticated, deterministic public_id và overwrite=false. Cloudinary cloud_name/api_key/api_secret lấy từ môi trường; SDK version pin sau kiểm tra tương thích, chưa thêm dependency từ tác vụ tài liệu.
- `putIfAbsent(commandKey, verifiedContent)`: Object được bảo vệ theo deterministic key + checksum, retry cùng key/content không sinh thêm object; key khác nội dung báo conflict. Reserve media/command và checksum trước remote call, reconcile asset đã tồn tại sau timeout; chưa xác minh được đúng object thì không đánh READY. Không dựa riêng vào overwrite=false để khẳng định nội dung giống nhau.
- `verifyReadable(objectKey)`, `open(objectKey)`: Trả trạng thái/read stream an toàn, không expose credential; phân biệt unavailable với missing. Backend lấy signed authenticated URL từ metadata tin cậy và chỉ dùng nội bộ để mở stream; application/web kiểm tra quyền trước mỗi lần phục vụ bytes qua same-origin endpoint, no-store, không redirect/lộ provider URL. Object vẫn authenticated sau publish; không dựa vào CDN invalidation để thi hành unpublish/delete.
- Không xóa ngay trên detach/product-delete. Policy đã chốt: ảnh chưa từng gắn đủ 24 giờ không còn sử dụng, ảnh đã gỡ đủ 30 ngày không còn tham chiếu mới đủ điều kiện dọn; ảnh lịch sử giữ theo bản ghi liên quan. Cleanup phải khóa media, kiểm tra registry tham chiếu, đặt DELETE_PENDING và commit trước remote delete; attach/retain từ chối state đang xóa. `deleteIfExists(objectKey)` là idempotent: provider missing được coi đã xóa, transient failure giữ DELETE_PENDING để thử lại, không tự đưa về READY. Không gọi provider dưới khóa DB.
- CatalogueSalesFacade cung cấp `retainMediaReferences(ownerRecordRef, mediaIds)` cho FE-08 trong transaction tạo snapshot, và release chỉ qua owning record đã đủ điều kiện hết giữ. Registry thuộc productmanagement, tham chiếu unknown/unverifiable chặn cleanup, không mặc định không có tham chiếu. Product/review cũng ghi registry qua application cùng transaction. Xóa mềm product không release historical refs.
- Business audit giữ 5 năm từ sự kiện theo Report 3; command receipt tối thiểu giữ 5 năm từ commit theo quyết định người dùng. Chỉ giữ kết quả/metadata cần thiết, không full request/token. Authentication/security log riêng vẫn thuộc chính sách identity owner. Provider timeout/failure không đổi productmanagement state đã commit; metadata READY đòi xác minh thành công.

## Contract dependencies and evidence limits

Trong [blocker-report](../blocker-report.md), G-01 đã chốt hướng xác thực dùng chung/roles, G-03 Cloudinary và G-04 retention đã chốt. V1 không yêu cầu MFA cho vai trò nội bộ (chốt 2026-10-26 theo 001); facade identity của 001 cần triển khai trước enable quản trị. FE-08/identity/Cloudinary adapter là dependency triển khai được sắp thứ tự, không phải câu hỏi nghiệp vụ phải hỏi lại. OpenAPI/SPI không chứng minh endpoint/adapter hiện hữu; nghiệm thu giữ chỗ/identity/media vẫn cần adapter thật, không dùng fixture thay integration evidence.
