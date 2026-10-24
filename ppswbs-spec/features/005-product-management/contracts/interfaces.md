# Interface Contracts: Product Management

**Date**: 2026-10-24 | **Status**: Proposed, provider/identity/FE-08 prerequisites chưa có implementation.  
**REST contract**: [OpenAPI 3.0.3](product-management.openapi.json). Trường, URL và errors ở đây phải khớp OpenAPI trước khi code; đây không phải endpoint đã hoạt động.

## Identity and capability boundary

Identity owner cung cấp qua members::facade một actor value gồm opaque actorId, verified identity reference, accountActive và capability result hiện hành. Catalogue không đọc members entity, không log raw token và không suy role từ Firebase/email. Staff/Manager là nhãn nghiệp vụ, không tự chuyển sang OWNER hoặc mã role khác.

Ngày 2026-10-24 người dùng đã chốt MEMBER/STAFF/MANAGER/ADMIN, MANAGER là vai trò riêng, Admin cấp quyền nội bộ và đăng ký khách không cấp quyền nội bộ. Người dùng cũng đồng ý hướng A: dùng chung primitive xác thực theo [spec 003](../../003-member-profile/spec.md) qua identity owner, bearer API/cookie web cố định 8 giờ không gia hạn trượt, revocation/trạng thái/CSRF và thành công về `/`. Phiên mới kế thừa auth_time tối đa 5 phút/tolerance tương lai 30 giây, cookie HttpOnly/Secure/SameSite=Lax/Path=/; không yêu cầu xác minh email lại trên session hợp lệ. Đây là quyết định FE-02, chưa phải RBAC/session đã triển khai; MFA/recovery cụ thể vẫn thuộc dependency thiết kế FE-01.

Không dùng Member allowlist ACTIVE/PENDING_POLICY_ACCEPTANCE hoặc Member bootstrap làm quyền nội bộ. `/member-auth/profile-session`, `__Host-mland_profile` và logout thuộc scope 003; không tự thay đổi hoặc coi cookie này là bằng chứng hoàn tất MFA. Identity owner cung cấp contract dùng chung/mở rộng, bao gồm gate đặc quyền và recovery theo FE-01; catalogue chỉ tiêu thụ actor/capability/xác thực đạt yêu cầu. Không tạo trang đăng nhập/MFA riêng trong catalogue hoặc ép người dùng Member xác minh email lại.

| Action | Required business authority | Denial |
| --- | --- | --- |
| Public catalogue/detail/media | Public eligibility, không cần identity | 404 nếu hidden/deleted/ineligible; 503 nếu dependency chưa xác minh được. |
| Catalogue management read/create/edit/media/quantity/status/delete | Staff hoặc Manager active có quyền action tương ứng | 401 thiếu/invalid identity, 403 thiếu quyền/inactive. |
| Product/price decision | Manager active có quyền approve | Staff bị 403; Manager tự tạo/tự duyệt được. |
| Price application/publication | Staff hoặc Manager active có quyền action tương ứng | Không coi approval là permission để bất kỳ người nào apply/publish. |
| Category management | Staff active có quyền category | Không tự mở thêm quyền Manager cho danh mục ngoài spec. |

Capability names đề xuất: catalogue.read-internal, product.create, product.edit, product.submit, product.approve, product.publish, product.delete, media.manage, variant.quantity, variant.sale-status, price.propose, price.approve, price.apply, category.manage. Đây là action vocabulary của hợp đồng, không phải các DB role/capability đã được provision.

API management chỉ nhận backend-verified bearer identity; cookie không thay bearer. Web management dùng phiên identity đã được xác minh, CSRF cho mọi POST, form có commandId và expectedVersion, cookie security thuộc identity owner. Chưa có session/capability facade thật nên đường quản trị không được enable bằng fallback permitAll hiện tại. Lỗi session không tự điều hướng sang hồ sơ/chính sách Member; identity owner phải cung cấp đích đăng nhập nội bộ cố định, không nhận arbitrary returnTo.

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
| GET/POST categories, PUT categories/{categoryId} | List/create/update name/description với unique normalized name toàn hệ thống. |
| GET categories/{categoryId}/impact | Số lượng và danh sách phân trang các sản phẩm bị ảnh hưởng; active/version hiện hành. Không public dữ liệu quản trị. |
| PUT categories/{categoryId}/activation | expectedVersion + active; phản ánh kết quả sau xác nhận impact. Không xóa product publication flag/history. |
| GET command-receipts/{commandId} | Chỉ actor sở hữu, có quyền hiện tại; query action bắt buộc để đúng receipt scope. Chưa có receipt trả 404 COMMAND_RESULT_NOT_FOUND, không khẳng định command chắc chắn chưa được xử lí. |

Decision reason đã được chốt tối đa 2.000 ký tự, bắt buộc không rỗng sau trim khi REJECT; contract đề xuất đếm code point. Không nhận số tiền ở REJECT. APPROVE product cần prices của mọi variant đúng snapshot, không duplicate; Manager có thể điều chỉnh approvedPrice hợp lệ. Danh sách variant bất biến nên update product không có variants. saleEnabled=true cho size mới và active=true cho danh mục mới đã được chốt, chưa cấp quyền mua cho product unpublished.

## Error contract

Giữ shape common.ErrorResponse: code, message an toàn, correlationId, timestamp, fieldErrors[{field,message}] tùy chọn. Không dùng message Member-policy cũ cho lỗi Staff/Manager. Mọi tình huống phải giữ dữ liệu trước đó khi mutation không được chấp nhận.

| HTTP | Codes | Behavior |
| --- | --- | --- |
| 400 | VALIDATION_ERROR, UNSUPPORTED_FILTER, INVALID_SORT, UNKNOWN_FIELD | Trường/dòng lỗi; không query/ghi với tiêu chí sai. |
| 401 | AUTHENTICATION_REQUIRED, INVALID_CREDENTIAL | Không tạo phiên/quyền từ credential lỗi. |
| 403 | CATALOGUE_ACCESS_DENIED, CSRF_INVALID | Không tiết lộ protected resource; CSRF_INVALID là web form path. |
| 404 | PRODUCT_NOT_FOUND, VARIANT_NOT_FOUND, CATEGORY_NOT_FOUND, MEDIA_NOT_FOUND, PROPOSAL_NOT_FOUND, COMMAND_RESULT_NOT_FOUND | Public hidden/deleted giống không có; không reveal draft. |
| 409 | STALE_VERSION, DUPLICATE_CATEGORY_NAME, PENDING_PRICE_PROPOSAL_EXISTS, REVIEW_NOT_CURRENT, PRICE_NOT_APPROVED, PRICE_TERMS_CHANGED, PRICE_APPLICATION_STALE, PUBLICATION_PREREQUISITES_MISSING, QUANTITY_BELOW_HELD, SIZE_LIST_IMMUTABLE, PRODUCT_DELETED, IDEMPOTENCY_KEY_REUSED | Tải latest trong quyền hiện tại, sửa/xác nhận lại; không ghi đè/hủy giao dịch cũ. |
| 413 | MEDIA_TOO_LARGE | Không attach object một phần; file quá giới hạn bị từ chối. |
| 415 | MEDIA_TYPE_UNSUPPORTED | Không nhận định dạng ảnh khác. |
| 503 | IDENTITY_DEPENDENCY_UNAVAILABLE, AVAILABILITY_DEPENDENCY_UNAVAILABLE, MEDIA_DEPENDENCY_UNAVAILABLE, CATALOGUE_SAVE_UNAVAILABLE, CATALOGUE_BUSY | Lỗi tạm thời riêng, không trả empty/held=0; cho kiểm tra receipt/thử lại cùng key. |

## Web journeys

| GET page | POST actions on the same web prefix | Result |
| --- | --- | --- |
| `/products`, `/products/{id}` | Chuyển lựa chọn sang FE-08 khi chọn mua | Public; GET không tạo cart/hold. Không phát minh route checkout của FE-08 nếu chưa có contract riêng. |
| `/catalogue-management/products`, `/catalogue-management/products/new`, `/catalogue-management/products/{id}` | `/products`, `/products/{id}/edit`, `/products/{id}/images`, `/products/{id}/submit`, `/products/{id}/review`, `/products/{id}/publication`, `/products/{id}/delete` | Normal HTML forms, CSRF + hidden commandId/version; review self-approval permitted for Manager. |
| `/catalogue-management/products/{id}` | `/media-uploads`, `/variants/{id}/quantity`, `/variants/{id}/sale-status`, `/variants/{id}/price-proposals` | Multipart normal form + subsequent attach references; no arbitrary URL/JS-only uploader. |
| `/catalogue-management/price-proposals/{id}` | `/price-proposals/{id}/revise`, `/price-proposals/{id}/submit`, `/price-proposals/{id}/decide`, `/price-proposals/{id}/apply` | Proposed/approved/applied amounts and states shown separately. |
| `/catalogue-management/categories`, `/catalogue-management/categories/{id}/impact` | `/categories`, `/categories/{id}/edit`, `/categories/{id}/activation` | Unique name, impact confirmation and no lost history. |

Danh sách web suffix ở cột POST được nối vào `/catalogue-management` trừ khi ghi prefix đầy đủ. Thành công redirect 303 tới fixed local detail/list page; validation render lỗi + escaped submitted fields; conflict render latest facts để xác nhận lại; error có correlation ID. Delete là form xác nhận riêng. Các link/labels/input có tên rõ và thông báo lỗi gắn trường; hỗ trợ vi/en theo message mechanism hiện có, không tự tuyên bố đạt một accessibility standard chưa phê chuẩn.

## Module facade contracts

### CatalogueSalesFacade — only named facade export

- `withLockedSaleContext(variantIds, work)`: FE-08 consumer đã xác minh vào category/product/variant gates theo sorted IDs, kiểm tra đủ public/sale/price prerequisites, trả immutable facts (productId, variantId, sku, size, appliedPrice, currency, remainingUnsold, versions). Callback trong cùng local transaction, sau đó FE-08 adapter khóa guard nguồn trước check availability/create hold. Không trả entities hoặc để lock scope kết thúc trước insert hold.
- `withLockedHistoricalContext(acceptedTransactionRef, variantIds, work)`: Không đòi product còn public hoặc chưa deleted, nhưng chỉ cho settlement/release của giao dịch đã chấp nhận, identity/reference do trusted FE-08 consumer xác minh; không cấp quyền new hold. Dùng cùng lock order.
- `recordAcceptedSale(operationRef, variantId, quantity)`: Internal FE-08 call trong guarded transaction, nhận operationRef duy nhất; chốt remaining đúng một lần cùng xử lí hold của FE-08. Không phải public REST, không nhận operationRef từ Guest. Validation quantity >=0 sau settlement xét snapshot post-settlement cùng transaction; rollback cả hai phía nếu lỗi.

Không dùng facade kiểu getEntity/setField và không cho frontend gọi method nội bộ này để né quyền. FE-08 phải hoàn thiện boundary invocation/authentication của consumer trước integration; facade chỉ nhận records/scalars và callback nghiệp vụ giới hạn.

### AvailabilityGuardPort — catalogue owns SPI, FE-08 implements

- `readSnapshots(variantIds, asOf)`: Batch exact active held quantity + source revision theo variant; không cache và không thay unknown bằng 0. Bộ chưa có hold trả 0 chỉ khi nguồn thật xác minh thành công.
- `withGuard(variantIds, asOf, work)`: Join transaction bắt buộc; giữ guard row ổn định trên từng variant kể cả khi chưa có hold, đọc snapshot hợp lệ và giữ khóa xuyên callback/commit. Không trả lazy entity, không mở REQUIRES_NEW tách source khỏi quantity save.
- FE-08 tạo/chốt/giải phóng giữ cũng phải dùng guard trên. asOf là thời điểm UTC của operation, cùng deadline semantics FE-08; event late/uncertain không được catalogue tự giải thích hoặc hủy giữ.
- Adapter readyringsales có compile dependency vào catalogue::facade, không gọi ngược catalogue khi đã giữ source guard. Catalogue không import readyringsales và không sao chép hold lifecycle. Thiếu adapter trả typed 503; test-only fixture phải được gắn rõ, không enable trong runtime thường.

### MediaStoragePort — catalogue infrastructure boundary

- Cloudinary đã được người dùng chọn. MediaStoragePort vẫn độc lập provider; adapter dùng signed backend upload, resource_type=image/type=authenticated, deterministic public_id và overwrite=false. Cloudinary cloud_name/api_key/api_secret lấy từ môi trường; SDK version pin sau kiểm tra tương thích, chưa thêm dependency từ tác vụ tài liệu.
- `putIfAbsent(commandKey, verifiedContent)`: Object được bảo vệ theo deterministic key + checksum, retry cùng key/content không sinh thêm object; key khác nội dung báo conflict. Reserve media/command và checksum trước remote call, reconcile asset đã tồn tại sau timeout; chưa xác minh được đúng object thì không đánh READY. Không dựa riêng vào overwrite=false để khẳng định nội dung giống nhau.
- `verifyReadable(objectKey)`, `open(objectKey)`: Trả trạng thái/read stream an toàn, không expose credential; phân biệt unavailable với missing. Backend lấy signed authenticated URL từ metadata tin cậy và chỉ dùng nội bộ để mở stream; application/web kiểm tra quyền trước mỗi lần phục vụ bytes qua same-origin endpoint, no-store, không redirect/lộ provider URL. Object vẫn authenticated sau publish; không dựa vào CDN invalidation để thi hành unpublish/delete.
- Không xóa ngay trên detach/product-delete. Policy đã chốt: ảnh chưa từng gắn đủ 24 giờ không còn sử dụng, ảnh đã gỡ đủ 30 ngày không còn tham chiếu mới đủ điều kiện dọn; ảnh lịch sử giữ theo bản ghi liên quan. Cleanup phải khóa media, kiểm tra registry tham chiếu, đặt DELETE_PENDING và commit trước remote delete; attach/retain từ chối state đang xóa. `deleteIfExists(objectKey)` là idempotent: provider missing được coi đã xóa, transient failure giữ DELETE_PENDING để thử lại, không tự đưa về READY. Không gọi provider dưới khóa DB.
- CatalogueSalesFacade cung cấp `retainMediaReferences(ownerRecordRef, mediaIds)` cho FE-08 trong transaction tạo snapshot, và release chỉ qua owning record đã đủ điều kiện hết giữ. Registry thuộc catalogue, tham chiếu unknown/unverifiable chặn cleanup, không mặc định không có tham chiếu. Product/review cũng ghi registry qua application cùng transaction. Xóa mềm product không release historical refs.
- Business audit giữ 5 năm từ sự kiện theo Report 3; command receipt tối thiểu giữ 5 năm từ commit theo quyết định người dùng. Chỉ giữ kết quả/metadata cần thiết, không full request/token. Authentication/security log riêng vẫn thuộc chính sách identity owner. Provider timeout/failure không đổi catalogue state đã commit; metadata READY đòi xác minh thành công.

## Contract dependencies and evidence limits

Trong [blocker-report](../blocker-report.md), G-01 đã chốt hướng xác thực dùng chung/phiên 8 giờ/roles, G-03 Cloudinary và G-04 retention đã chốt. Phương thức MFA/recovery và contract đạt xác thực đặc quyền là dependency thiết kế identity/FE-01 cần hoàn thiện trước enable quản trị; không tự chọn TOTP hoặc bỏ kiểm tra. FE-08/identity/Cloudinary adapter là dependency triển khai được sắp thứ tự, không phải câu hỏi nghiệp vụ phải hỏi lại. OpenAPI/SPI không chứng minh endpoint/adapter hiện hữu; nghiệm thu giữ chỗ/identity/media vẫn cần adapter thật, không dùng fixture thay integration evidence.
