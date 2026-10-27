# Data Model: Product Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-26 (V1 không MFA theo 001; tên facade identity; tham chiếu 001 mới)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Approved — duyệt bởi Đặng Thị Khương ngày 2026-10-24; thiết kế dữ liệu vật lí là baseline, chưa có migration được tạo/chạy.   Phần bổ sung import Excel (US9) được duyệt bởi Đặng Thị Khương ngày 2026-10-24. Các cập nhật ngày 2026-10-25 và 2026-10-26 (sửa lỗi chữ, Manager quản lí danh mục, phạm vi ưu đãi FE-06, V1 không MFA theo 001, T102, kết quả speckit-analyze) được Đặng Thị Khương duyệt ngày 2026-10-26.
**Sources**: [spec](spec.md), [research decisions](research.md), [interfaces](contracts/interfaces.md).

## Ownership and representation

Catalogue sở hữu các bảng dưới đây. Mã MEMBER/STAFF/MANAGER/ADMIN đã chốt, Manager không đồng nhất OWNER. Actor IDs vẫn là opaque string từ identity facade, không giả FK sang members khi physical internal-account model chưa triển khai. Product/variant ID là positive signed BIGINT sinh bởi hệ thống, JSON decimal string; không encode actor/provider data vào SKU. Giá và lượng là DECIMAL(19,0), dùng BigInteger, không floating point; miền kỹ thuật 0–9999999999999999999, giá phải lớn hơn 0. Không tự đặt trần kinh doanh thấp hơn. Version là nonnegative signed BIGINT; timestamp UTC microsecond.

Text lưu utf8mb4, đo Unicode code point sau Unicode trim đầu/cuối. Tên danh mục normalized NFC + lowercase Locale.ROOT, giữ dấu, so unique bằng binary key thay vì collation bỏ dấu. Giá trị tên hiển thị vẫn giữ nguyên hoa/thường/dấu đã nhập sau trim.

## Entities and constraints

| Table / entity | Fields and relationships | Invariants |
| --- | --- | --- |
| catalogue_categories | id, name VARCHAR(200), name_key VARBINARY(1600), description TEXT nullable, active, version, created_at/updated_at | Unique name_key toàn hệ thống kể cả inactive; tên 1–200, mô tả tùy chọn tối đa 5.000 và active=true lúc tạo đã được chốt. Không xóa danh mục đã tham chiếu. |
| catalogue_products | id, name VARCHAR(200), description TEXT, category_id FK, published, first_published_at nullable, deleted_at/by nullable, content_version, version, created_at/updated_at | Tên 1–200, mô tả 1–5.000. deleted_at chặn public/commands. first_published_at chỉ được đặt lần đầu, không reset khi ẩn. Category hoạt động cần cho public, không sửa lịch sử khi category inactive. |
| catalogue_variants | id, product_id FK, ring_size SMALLINT, sku VARCHAR(25), remaining_unsold DECIMAL(19,0), sale_enabled, applied_price DECIMAL(19,0) nullable, applied_proposal_id nullable, applied_sequence, pending_proposal_id nullable, version | Unique(product_id, ring_size), unique sku; size 5–29 cố định sau tạo; 1–25 rows/product. SKU tối đa 2 + 19 + 2 + 2 = 25 ASCII chars. No engraving fields. remaining_unsold >= held under guard. Giá null trước áp dụng, không dùng giá đề xuất thay thế. |
| catalogue_product_reviews | id, product_id FK, revision, content_version, state, effective, submitted_by/at, decided_by/at nullable, rejection_reason nullable, snapshot JSON | Unique(product_id, revision), snapshot chứa content/category/media references/size IDs và proposal revisions đúng hồ sơ, không chứa raw identity tokens hoặc hold records. Decision/history bất biến; effective=false không xóa quyết định. |
| catalogue_price_proposals | id, variant_id FK, review_id nullable FK, revision, version, state, effective, proposed_amount, approved_amount nullable, currency=VND, base_applied_sequence, terms_digest, submitted_by/at, decided_by/at, reason nullable, supersedes_id nullable FK | Proposal revision mới là row mới, version bảo vệ cập nhật trạng thái; chỉ current effective PENDING được variant.pending_proposal_id tham chiếu. APPROVED không đồng nghĩa đã áp dụng. terms_digest gắn identity/size/currency và ngữ cảnh duyệt; quantity không phải điều khoản giá. |
| catalogue_price_applications | id, variant_id FK, proposal_id FK, applied_sequence, old_amount nullable, new_amount, actor_id, applied_at | Unique(variant_id, applied_sequence), unique proposal_id cho một lần áp dụng; new_amount khớp approved_amount của revision còn hiệu lực. Không áp dụng proposal có base sequence cũ sau giá mới hơn. |
| catalogue_media_objects | id opaque UUID, storage_key unique, provider_asset_id nullable, provider_version nullable, provider_format nullable, checksum, content_type, byte_count, state, uploaded_by, command_id, created_at, last_verified_at nullable, first_attached_at nullable, last_unreferenced_at nullable, delete_requested_at nullable, deleted_at nullable | STAGED/READY/UNAVAILABLE/DELETE_PENDING/DELETED; Cloudinary đã chốt. storage_key là deterministic public_id, resource_type=image/type=authenticated cố định trong adapter; các provider fields bắt buộc trước READY. Object không mở public trực tiếp; phục vụ qua application gate. JPEG/PNG/WebP, byte_count <= 5.000.000. Chưa từng attach: eligible cleanup sau 24h; đã detach: sau 30 ngày không còn tham chiếu; không suy đủ điều kiện từ product soft-delete. |
| catalogue_product_images | id, product_id FK, media_object_id FK, alt_text VARCHAR(200), display_position, version | Unique(product_id, display_position), tối đa 10 ảnh, alt text 1–200. Gỡ link không xóa object; reorder phải tránh unique collision trong cùng transaction qua cơ chế JPA được kiểm chứng. |
| catalogue_media_references | id, media_object_id FK, owner_module, owner_record_ref, linked_at, released_at nullable | Unique(media_object_id, owner_module, owner_record_ref). Product/review ghi cùng transaction; FE-08 retain qua productmanagement facade trước commit snapshot. Không release history chỉ vì product deleted. Reference chưa xác minh thì cleanup từ chối; không đọc bảng module khác hoặc giả count=0. |
| catalogue_command_receipts | id, actor_id, action, command_id UUID, request_digest, resource_type/id, result_status, result_version nullable, safe_result JSON, committed_at | Unique(actor_id, action, command_id). Same key/digest xác định kết quả cũ, khác digest 409. Giữ kết quả tối thiểu 5 năm từ commit, không full request/token. Kiểm tra quyền hiện tại trước replay/query, không dùng receipt cấp quyền. |
| catalogue_audit_events | id, actor_id nullable, action, resource_type/id, version/revision nullable, changed_fields/safe_changes JSON, outcome, correlation_id, occurred_at | Mutation + success audit cùng transaction; lỗi audit rollback mutation. Business audit giữ 5 năm từ sự kiện theo Report 3. Rejected business attempt ghi độc lập sau rollback, không token/secret/full upload/PII thừa. Security-authentication log thuộc identity owner. |
| catalogue_import_batches | id UUID, actor_id, file_name VARCHAR(255), file_sha256 CHAR(64), state, row_count, product_count, variant_count, error_count, errors JSON nullable (mọi lỗi, tối đa 500 dòng × 7 cột + lỗi nhóm = 4.000 phần tử, không cắt bớt), parsed_payload JSON nullable, created_command_id UUID, confirm_command_id UUID nullable, created_product_ids JSON nullable, expires_at, imported_at nullable, version, created_at/updated_at | State VALIDATED / REJECTED / IMPORTED / EXPIRED. Chỉ VALIDATED mới có parsed_payload (các dòng đã chuẩn hóa: groupKey, name, description, categoryId, size, proposedPrice, quantity); payload bị xóa (set null) khi IMPORTED/EXPIRED và không lưu file `.xlsx` gốc. `errors` (chứa giá trị ô gốc) chỉ tồn tại đến expires_at: job dọn đặt `errors` và `parsed_payload` về null cho mọi batch quá hạn; error_count và các số đếm vẫn giữ. Báo cáo lỗi của batch quá hạn không tải được (409 IMPORT_BATCH_EXPIRED). row_count ≤ 500, mỗi nhóm 1–25 size. expires_at = created_at + 60 phút (đã chốt tại spec A-003 ngày 2026-10-24). Metadata batch (không payload, không errors) giữ tối thiểu 5 năm cùng receipt/audit. Unique(actor_id, created_command_id) và unique(confirm_command_id). Không FK sang product (created_product_ids là danh sách id mô tả kết quả); không thay thế receipt. |

FK vòng variant.pending_proposal_id/applied_proposal_id được thêm sau khi tạo bảng liên quan; validate proposal thuộc chính variant bằng invariant trong transaction. Nếu cần composite FK để tăng bảo vệ thì migration phải có supporting unique key tương ứng. Không export JPA relationships sang module khác; mọi FK xóa dùng restrict, không cascade hard-delete lịch sử.

Không tạo bảng hold, cart, retail order, user role hoặc media-provider credential trong migration catalogue. Hold lifecycle thuộc FE-08. Nội dung snapshot là immutable value data của review, không phải DTO/entity chia sẻ trong common.

## Business versions

- product.version bảo vệ stale edit; content_version chỉ đổi khi nội dung cần duyệt thay đổi, không tăng vì quantity/hold/sale-enabled thay đổi.
- Trước first_published_at, sửa tên/mô tả/category/ảnh hoặc giá đề xuất làm current review không còn hiệu lực; phải gửi lại và duyệt toàn bộ phiên bản mới. Quyết định và price applications lịch sử giữ nguyên nhưng không cấp quyền công khai hồ sơ đã thay đổi.
- Sau first_published_at, content edit hợp lệ được lưu trực tiếp; first approval lịch sử không buộc khớp content_version mới. Price proposal revision và application sequence vẫn bắt buộc cho mọi đổi giá.
- variant.version bảo vệ quantity/state edit, applied_sequence bảo vệ áp dụng giá cũ; không dùng một token duy nhất làm stock edit tự hủy quyết định giá. Số lượng trong snapshot hồ sơ là thông tin tại thời điểm gửi, không là điều khoản giá; màn hình duyệt cho đối chiếu lượng hiện tại mà không tự đưa hồ sơ về nháp chỉ vì lượng thay đổi.

## State transitions

| Subject | Transition | Conditions and effect |
| --- | --- | --- |
| New review | DRAFT → PENDING | Hồ sơ đủ field, active category, READY usable image và proposal mọi size. |
| New review | PENDING → APPROVED / REJECTED | Manager hiện có quyền; cùng revision/content version, giá cuối hợp lệ; reject có reason. Duyệt hồ sơ và mọi quyết định giá chung transaction. |
| New review/proposal | PENDING/APPROVED → ineffective, new revision DRAFT; DRAFT → PENDING khi gửi lại | Sửa nội dung/ảnh/giá trước lần công khai đầu làm revision hiện tại mất hiệu lực và hồ sơ trở về DRAFT; chỉ gửi lại mới sang PENDING. Giữ old revision/history. Một pending price/size, khóa variant khi thay pointer. |
| Price | DRAFT → PENDING → APPROVED / REJECTED | Tách quyết định khỏi áp dụng; price reject không thay giá áp dụng. |
| Price application | APPROVED effective → application receipt | Check correct variant, immutable amount, base_applied_sequence và current initial-review validity; tăng sequence, update applied price atomically. |
| Product | UNPUBLISHED → PUBLISHED | Chưa deleted, initial review valid nếu chưa từng published; mọi size có approved applied price, category/media/content valid. 0 quantity không chặn. |
| Product | PUBLISHED → UNPUBLISHED | Chặn new public reads/holds; giữ first_published_at và các hold/đơn cũ. |
| Product | Any not-deleted → DELETED | User xác nhận; set deleted_at/by, effective publication=false; block new commands; không xóa identity/FK/image history. Không restore. |
| Import batch | (kiểm tra file) → VALIDATED / REJECTED | VALIDATED khi mọi dòng hợp lệ; REJECTED khi có lỗi dòng (kèm báo cáo lỗi). Không tạo product/variant, không cấp product id/SKU. Lỗi mức file (sai định dạng, quá dung lượng/dòng) bị từ chối trước khi tạo batch. |
| Import batch | VALIDATED → IMPORTED | Xác nhận với expectedVersion + command UUID; kiểm tra lại toàn bộ payload với dữ liệu mới nhất (category còn active), tạo mọi product/variant/review draft/proposal draft + audit + receipt trong một transaction. Lỗi tái kiểm tra → batch REJECTED (409 IMPORT_PREVIEW_STALE), không tạo gì. |
| Import batch | VALIDATED → EXPIRED | Quá expires_at hoặc được dọn; xóa parsed_payload; xác nhận trả 409 IMPORT_BATCH_EXPIRED, phải tải file lại. |
| Variant | ENABLED ↔ DISABLED | Check version/quyền; không sửa size/SKU/quantity từ hành động này. Mở bán lại không tự publish. |
| Category | ACTIVE ↔ INACTIVE | Show impact, version/quyền; tác động public eligibility, không rewrite product publication flags/history. |

Không có mutation thêm/đổi/xóa size; size không còn bán dùng DISABLED. Không cho SET price trực tiếp; update DTO chỉ nhận các trường của action đó.

## Transaction and concurrency protocol

1. Identity owner xác nhận quyền/status hiện tại; nếu cần role/account lock thì khóa theo protocol identity đã phê chuẩn trước domain locks.
2. Category IDs tăng → product IDs tăng → variant IDs tăng → AvailabilityGuardPort source guard IDs tăng. Khi đổi category, khóa cả source/target theo ID. Không giữ callback qua request khác/remote call.
3. FE-08 new-hold gọi CatalogueSalesFacade.withLockedSaleContext trước khi chạm hold guard. Callback tạo/chốt/release hold và thay quantity liên quan phải ở cùng transaction local. Catalogue không biết bảng hold; adapter cung cấp snapshot mới nhất cùng asOf và giữ stable source guard cả khi chưa có hold row.
4. Quantity edit kiểm tra latest held sum dưới guard, cập nhật remaining_unsold, audit, receipt rồi commit. Compare không tách khỏi write. Delete serialize bằng product lock với hold creation.
5. FE-08 settlement hợp lệ được phép dùng historical sale context dù product đã hidden/deleted; chỉ mutation lượng theo order/operation reference đã được chấp nhận, không cấp new-hold. Guard snapshot loại lượng vừa settle trước so sánh remaining_after; mọi cập nhật trong cùng transaction.
6. Khoảng request lost-response: receipt commit cùng aggregate. Concurrent unique command receipt loser đọc kết quả đã commit sau rollback transaction của mình; không catch unique violation rồi tiếp tục cùng failed transaction. Command cũ không có receipt phải validate state/version lại.
7. State command đã ở đúng trạng thái có thể trả no-change theo spec sau auth và kiểm tra resource; nếu cần chuyển trạng thái thì expectedVersion bắt buộc khớp. Replay receipt không bỏ qua auth/deletion visibility.

## Import từ Excel (FR-039–FR-045)

- Import chỉ là đường tạo khác của create: dùng chung `CatalogueValues` và phương thức lõi `createDraft` của `CreateProductService` (không tự mở receipt/transaction; cùng giới hạn tên/mô tả/size/giá/số lượng, cùng cấp product id + SKU, cùng khởi tạo review DRAFT, proposal DRAFT mỗi size, `remaining_unsold`, `sale_enabled=true`, `published=false`, `applied_price` null, không ảnh). Không có đường ghi tắt vào bảng product/variant.
- Preview không insert product/variant nên không tiêu thụ id/SKU. Khi xác nhận, id và SKU do cùng cơ chế sinh khi tạo tay; transaction thất bại rollback toàn bộ (id auto-increment có thể bị bỏ qua, SKU không bị tái cấp vì gắn product id).
- Lock order khi xác nhận: category IDs tăng dần (mọi category trong payload, kiểm tra còn active) → product mới (chưa có lock cạnh tranh) → variant mới. Không giữ guard FE-08 (variant mới chưa thể có hold).
- parsed_payload là dữ liệu nghiệp vụ tối thiểu (không chứa công thức, hyperlink, nội dung ô thô). Giá trị bắt đầu bằng `=`, `+`, `-`, `@` chỉ bị vô hiệu hóa ở báo cáo lỗi xuất file; trong payload/DB lưu nguyên giá trị đã trim.
- Dọn payload: job nền xóa parsed_payload và errors của batch quá hạn, chuyển VALIDATED → EXPIRED (REJECTED giữ trạng thái, errors null); không xóa metadata batch/audit/receipt (5 năm).

## Public reads and indexes

Public predicates gồm published, not-deleted, category.active, usable image và giá áp dụng của mọi size. Buyability thêm sale_enabled và remaining_unsold - valid_held > 0 từ nguồn FE-08. Cùng variant phải thỏa price range và buyability. Không join proposal/review history vào public DTO.

Indexes dự kiến: product(category_id, published, deleted_at, name, id); product(created_at, id); variant(product_id, ring_size); unique sku; proposal(variant_id, state, effective); media usage(product_id, display_position); receipt unique actor/action/command; import_batch(actor_id, created_at), import_batch(state, expires_at) cho job dọn payload. Query IDs/sort-key theo filter trước, batch fetch payload sau; không paginate trên fetch-join ảnh/variants. Batch availability phải có nguồn thật; nếu nguồn không khả dụng trả lỗi riêng, không trả catalogue rỗng hoặc giả remaining=available.

## Migration and preservation

Migration tiếp theo được cấp sau khi kiểm tra thư mục migration lúc triển khai (hiện trống vì backend đã bị xóa) và migration mới của feature khác; chưa ghi một file SQL từ bước plan. Validate schema fresh/upgrade MySQL 8.4, giữ members/workshop invoices/holds đang tồn tại. Import dữ liệu sản phẩm có sẵn từ hệ thống cũ không có trong phạm vi (khác với import Excel của US9, chỉ tạo sản phẩm mới); không tự migrate dữ liệu giả từ README/prototype. Không đổi migration cũ hoặc data reset.

## Open prerequisite decisions

Roles, hướng xác thực dùng chung G-01, Cloudinary G-03 và retention G-04 đã chốt ngày 2026-10-24. V1 không yêu cầu MFA cho vai trò nội bộ (chốt 2026-10-26 theo 001); không tạo bảng session/MFA/role nội bộ trong productmanagement. FK/account model và adapter FE-08/Cloudinary là dependency thiết kế/triển khai, không cần hỏi lại hướng A, role, provider hoặc hold semantics. Các bảng/fields vẫn là thiết kế vật lí đề xuất, chưa có migration/deploy. Cleanup khóa media IDs theo thứ tự tăng, kiểm tra registry và state trước attach/retain/delete; không gọi sang hold source khi đang giữ media lock. Mark DELETE_PENDING rồi commit trước provider call; remote failure retry cùng key, không phục hồi public eligibility.
