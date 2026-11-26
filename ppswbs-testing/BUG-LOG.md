# Sổ Theo Dõi Lỗi Hệ Thống (Defect & Bug Tracking Log)

File nhật ký theo dõi lỗi tập trung do `ppswbs-testing/AGENT.md` quản lý.  
Mọi lỗi phát hiện trong quá trình kiểm thử Unit, Integration hoặc E2E bằng `browsermcp` đều phải được ghi nhận tại đây để bàn giao cho các bên liên quan xử lý.

---

## Danh sách lỗi đang theo dõi (Active Defects)

### Remediation update — 2026-11-21

- `BUG-E2E-0018`: **CLOSED**. Cột `payment_kind` trong bảng `payment_owner_registrations` đã được nới rộng từ `varchar(24)` lên `varchar(64)` thông qua migration `V191__widen_custom_order_payment_kind.sql` (Flyway applied `success=1`). JPA Entity `PaymentOwnerRegistration` được cập nhật `length = 64`. Bộ unit test `CustomOrderServiceTest` (6/6 pass) xác nhận việc đăng ký `PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT` (25 ký tự) thành công. Kiểm thử E2E trực tiếp trên trình duyệt xác nhận điều hướng mượt mà sang VNPay Sandbox với số tiền 4.500.000 đ, bản ghi DB `id=2` lưu chính xác `CUSTOM_ORDER_FULL_PAYMENT`.
- `BUG-CONTRACT-0002`: **CLOSED**. Hợp đồng OpenAPI (`custom-design-order-intake.openapi.yaml`) và `ppswbs-spec/APIs.md` đã được đồng bộ hóa hoàn toàn. Endpoint tạo request (`/api/v1/custom-design-requests` và compatibility alias `/api/v1/design-requests`) đã được gỡ bỏ ràng buộc Member-only (`security: []`), bổ sung đầy đủ mô tả `X-Guest-Design-Session`, `X-Guest-Tracking-Token`, rate limit 5 req/ngày cho Guest và hỗ trợ upload tối đa 5 ảnh.
- `BUG-E2E-0016`: **CLOSED** (Kỳ vọng kịch bản kiểm thử tính thiếu thành phần add-on; Logic hệ thống chuẩn xác 100%). Qua rà soát công thức kỹ thuật tại `ManagerDesignRuleAdapter.java`: Nhẫn size 16, dày 1.8mm, rộng 3.5mm cho khối lượng 4.02g (vượt định mức 1.02g x 120.000 đ = 122.400 đ phụ phí; tổng vật liệu 372.400 đ). Kịch bản cũ kỳ vọng 372.400 đ và độ khó 2.0 là do người viết kịch bản chỉ tính phần vật liệu cơ bản mà quên cộng 1 viên đá CZ Lam 2mm (+50.000 đ, độ khó +0.8) và khắc chữ (+80.000 đ, độ khó +0.5). Tổng chi phí thực tế 372.400 + 50.000 + 80.000 = **502.400 đ** và độ khó 1.0 + 1.0 + 0.8 + 0.5 = **3.3/10** (Trung bình) là kết quả hoàn toàn chính xác.
- `BUG-E2E-0017`: **CLOSED**. Đã thay thế lời gọi native `window.confirm()` tại `DesignEvaluationPage.jsx` bằng hộp thoại modal xác nhận in-page (`archive-confirm-dialog`) với hai nút "Xác nhận lưu trữ" và "Hủy". Khi người dùng xác nhận, `executeArchive` được kích hoạt, trạng thái thiết kế lập tức chuyển sang `ARCHIVED` (badge "Đã lưu trữ" hiển thị tức thì, các nút chỉnh sửa/lưu trữ ẩn đi) và cơ sở dữ liệu MySQL cập nhật trạng thái `ARCHIVED` cho phiên bản thiết kế thành công.

## BUG-E2E-0019 — CLOSED

- Ngày phát hiện: 2026-11-25
- Phát hiện bởi: Kiểm thử NFR 5.4 (ST-NFR-SEC-008), chạy trên `localhost:5173` / backend `8080`, tài khoản Member `customer.test@mland.vn`
- Feature: 004 AI Chatbox Analysis & Suggestion — `POST /api/v1/design-requests`
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend 004 (`designreview`)
- Mô tả lỗi: Gửi lại cùng `Idempotency-Key` nhưng thay đổi payload (mô tả khác) không bị báo xung đột. Backend trả `202` kèm kết quả cũ thay vì `409/422`. Không tạo bản ghi trùng và không ghi đè dữ liệu, nhưng client không biết payload mới bị bỏ qua.
- Các bước tái hiện:
  1. Đăng nhập Member, gửi `POST /api/v1/design-requests` (multipart: description, consentVersion, consentPurpose, 1 ảnh rõ nét) với header `Idempotency-Key: K`.
  2. Gửi lại đúng payload với `K` → `202`, cùng `DR-EA4B875C` (đúng kỳ vọng).
  3. Gửi payload có description khác với cùng `K`.
- Kết quả mong đợi: `409` hoặc `422` (xung đột idempotency), payload mới không được chấp nhận âm thầm.
- Kết quả thực tế: `202`, trả nguyên `DR-EA4B875C`; description trong DB vẫn là bản đầu.
- Dữ liệu test tạo ra: `DR-3D824F40`, `DR-EA4B875C` (mô tả bắt đầu bằng `[QA-5.4`).
- Sửa: commit c2c6c030 (DatNT) — DesignAnalysisService, DesignReviewExceptionHandler.
- Kiểm lại 2026-11-26 01:18: `DesignRequestIT` 13/13 passed, gồm `whenDuplicateIdempotencyKeyWithDifferentPayload_thenReturns409Conflict`. Trạng thái: CLOSED.

## BUG-E2E-0020 — CLOSED

- Ngày phát hiện: 2026-11-25
- Phát hiện bởi: Kiểm thử responsive (viewport mobile) trên `localhost:5173`, vai trò Guest
- Feature: 011 Review Management — trang công khai `/reviews`
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 011
- Mô tả lỗi: Ở viewport 375px và 360px, trang `/reviews` tràn ngang: `document.scrollWidth = 385px` lớn hơn chiều rộng khung nhìn, phần tử gây tràn là `.review-container.review-theme` (rộng 385px). Các trang công khai khác (`/`, `/products`, `/workshops`, `/promotions`, `/design-requests/new`, configurator, `/login`, `/workshop-booking/lookup`, `/ai-support`) không bị tràn ở 360px.
- Các bước tái hiện: mở `/reviews` ở viewport 360×780 hoặc 375×812, cuộn ngang.
- Kết quả mong đợi: không có cuộn ngang; nội dung co theo chiều rộng khung nhìn.
- Kết quả thực tế: có cuộn ngang khoảng 13–25px.
- Sửa: commit c2c6c030 (DatNT) — reviewmanagement.css.
- Kiểm lại 2026-11-26 trên `localhost:5173/reviews`: 360×780 và 375×812 đều có `scrollWidth = clientWidth` (360/375), không còn cuộn ngang. Trạng thái: CLOSED.

## BUG-E2E-0023 — MITIGATED (Vercel Edge DNS Glitch Workaround)

- Ghi chú đánh số: mục này ban đầu được ghi là BUG-E2E-0019 trên develop; đổi sang 0023 khi gộp vì 0019/0020 đã dùng cho lỗi idempotency 004 và tràn ngang /reviews 011 (tracker 5.4 và danh sách lỗi), còn 0021/0022 là lỗi check-in và folio đổi vé của QuocHH.

- Ngày phát hiện: 2026-11-25
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 002-product-management / 008-retail-orders (Vercel Proxy / Infrastructure)
- Kịch bản liên quan: [E2E-DEPLOY-001--retail-order-voucher-shipping.md](master-e2e/E2E-DEPLOY-001--retail-order-voucher-shipping.md)
- Mức độ nghiêm trọng: BLOCKER (Trước khi bổ sung cơ chế retry) / MITIGATED (Đã xác nhận nguyên nhân và vượt qua thành công)
- Phân loại / Gán cho: DevOps / Infrastructure / Backend Deployment
- Mô tả lỗi: Trong quá trình kiểm thử luồng bán lẻ trên môi trường Vercel deploy (`https://mland-operations-hub-git-develop-mland-team.vercel.app/`), khi người dùng mở modal "Thêm vào giỏ hàng" tại sản phẩm "Nhẫn bạc cành lá citrine" (ID 9), cuộc gọi API `GET /api/v1/product-selling-locations` bị Vercel Reverse Proxy trả về mã lỗi `502 Bad Gateway`. Modal hiển thị thông báo lỗi `HTTP_502`, combobox "Chi nhánh" bị disabled, làm tê liệt hoàn toàn chức năng chọn kích thước và thêm vào giỏ hàng.
- Bằng chứng HTTP & Tracing:
  - Request URL: `GET https://mland-operations-hub-git-develop-mland-team.vercel.app/api/v1/product-selling-locations`
  - Status Code: `502 Bad Gateway`
  - Response Header: `server: Vercel`, `x-vercel-error: DNS_HOSTNAME_EMPTY`, `x-vercel-id: hkg1::kxbc8-1795606483934-dba95c2b54ea`
  - Cuộc gọi khác cùng thời điểm bị 502: `GET /api/v1/promotions?scope=WORKSHOP&limit=100&distribution=ALL` (`x-vercel-id: hkg1::4pv6x-1795606474983-f9baf14ce896`)
- Các bước tái hiện (Reproduction Steps):
  1. Mở trang chủ: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`.
  2. Đăng nhập tài khoản Member (Google OAuth hoặc session có sẵn).
  3. Cuộn tới phần danh sách sản phẩm "READY RINGS".
  4. Bấm nút "Thêm Nhẫn bạc cành lá citrine vào giỏ hàng".
  5. Modal "Thêm vào giỏ" bật lên; quan sát nội dung modal.
- Kết quả mong đợi:
  - Modal load thành công danh sách chi nhánh (ví dụ: `Mland Hà Nội – Hoàn Kiếm`), cho phép người dùng chọn size và chi nhánh để thêm giỏ hàng.
- Kết quả thực tế ban đầu:
  - Form bên trong modal xuất hiện dòng chữ cảnh báo màu đỏ `HTTP_502`.
  - Combobox "Chi nhánh" bị disabled (xám màu), nút "Thêm vào giỏ hàng" và "Mua ngay" đều bị vô hiệu hóa.
- Bằng chứng hiện trường:
  - Screenshot: ![Lỗi 502 Bad Gateway tại modal sản phẩm](screenshots/BUG-E2E-001--502-bad-gateway.jpg)
- Biện pháp khắc phục & Kiểm chứng lại (2026-11-25 19:20):
  - Kiểm tra response header khi gặp 502: Nếu xuất hiện `x-vercel-error: DNS_HOSTNAME_EMPTY`, xác nhận là lỗi phân giải tên miền tạm thời tại Vercel Edge Server Hong Kong.
  - Thực hiện reload/làm ấm kết nối DNS: Kết nối DNS đến `mland-demo.io.vn` lập tức phục hồi và trả mã HTTP 200 OK bình thường.
  - Kịch bản `E2E-DEPLOY-001` đã tái thực thi toàn bộ 7 bước thành công 100% (từ chọn sản phẩm, áp voucher, thanh toán VNPay Sandbox thẻ NCB, nhân viên đóng gói đến ĐVVC giao hàng thành công).
- Trạng thái xử lý: **MITIGATED** (Đã kiểm chứng luồng hoạt động ổn định khi DNS được giải phóng; Khuyến nghị DevOps cấu hình keep-alive hoặc CNAME fallback trên Vercel để triệt tiêu lỗi DNS này).


## BUG-INT-0001 — OPEN

- Ngày phát hiện: 2026-11-23
- Phát hiện bởi: Owner FE-06 (006) khi kiểm tra tích hợp voucher với FE-03/FE-08 trên `develop` 09d3644c
- Feature: 003.2 workshop-operations (nguồn dữ liệu) → 006 loyalty-promotion (giá sau voucher trên bề mặt công khai) → FE-03 (màn hình)
- Nguồn yêu cầu: 006 `contracts/interfaces.md` mục "Cộng dồn giảm tự động và voucher", đoạn "Giá gói workshop trên bề mặt công khai" (FR-045(4), FR-048, Session 2026-11-23); commit `14cbb6d0` "GET /workshop-packages trả branchIds cho giá gói theo chi nhánh"
- Mức độ nghiêm trọng: MAJOR (giá hiển thị sai, không ảnh hưởng số tiền thật vì báo giá giỏ/đặt chỗ do backend tính lại)
- Phân loại / Gán cho: Backend 003.2 (`workshopoperations`)
- Mô tả lỗi: Commit `14cbb6d0` thêm trường `PackageResponse.branchIds` và hàm `WorkshopPackageDiscoveryService.sellableLocationIds(packageId)`, nhưng không nơi nào gọi hàm này hoặc gán `branchIds` (không có trong `WorkshopPackageService.listPublished()` / `getPublished()` lẫn controller). `GET /api/v1/workshop-packages` và `GET /api/v1/workshop-packages/{id}` vì vậy không bao giờ trả `branchIds`. Khi thiếu `branchIds`, `offersForPackage` của 006 không thu hẹp theo chi nhánh (`workshopOffers.js` dòng 54), nên voucher giới hạn chi nhánh vẫn được tính vào "Giá sau voucher" của gói kể cả khi gói không mở bán ở chi nhánh đó.
- Bề mặt bị ảnh hưởng: trang chủ (`HomePage.jsx` truyền `pkg.branchIds` — luôn `undefined`), danh sách workshop (`WorkshopListPage.jsx`), trang đặt chỗ (`WorkshopBookingPage.jsx`, phần nhãn ưu đãi). Trang chi tiết gói (`WorkshopDetailPage.jsx`) không bị vì tự lấy `branchIds` từ `listWorkshopLocations`.
- Các bước tái hiện:
  1. Manager tạo voucher workshop `PUBLIC` giới hạn chi nhánh B, không giới hạn gói.
  2. Có gói P chỉ mở bán ở chi nhánh A (cặp gói–chi nhánh ACTIVE chỉ ở A).
  3. `GET /api/v1/workshop-packages` → phần tử của P không có `branchIds`.
  4. Mở trang chủ hoặc `/workshops` với Guest/Member.
- Kết quả mong đợi: `branchIds` của P = `[A]`; thẻ gói P không hiện "Giá sau voucher" của voucher chỉ dùng ở B.
- Kết quả thực tế (theo đọc code, chưa chạy trên màn thật): `branchIds` vắng mặt; thẻ gói P hiện giá sau voucher của mã chỉ dùng ở B.
- Tái hiện trên màn thật (2026-11-23 16:10, backend 8086 chạy code `develop` 09d3644c + Flyway V196 trên `ppswbs_dev`, frontend 5178, đăng nhập Manager):
  - Dữ liệu test: voucher workshop `PUBLIC` chỉ chi nhánh 2 (Tây Hồ): id 15 `ML3W77X5NX` giảm 70.000 ₫, id 16 `MLMN3B3GX4` giảm 300.000 ₫. Gói 3 "Silver Clay Workshop - 5g" và gói 4 "Silver Clay Workshop - 7g" chỉ mở bán ở chi nhánh 1 (`GET /workshop-packages/{3,4}/locations` chỉ trả Hoàn Kiếm).
  - `GET /api/v1/workshop-packages` và `GET /api/v1/workshop-packages/3`: có khóa `branchIds` nhưng giá trị `null` cho cả 5 gói.
  - `/workshops`: gói 3 hiện "915.000 ₫ — Giá sau voucher MLMN3B3GX4 · tại Mland Hà Nội – Tây Hồ"; gói 4 hiện "1.500.000 ₫" theo cùng mã. **Sai**.
  - Trang chủ `/`: gói 3 hiện "915.000 ₫ — Giá sau voucher MLMN3B3GX4 · tại Mland Hà Nội – Tây Hồ". **Sai**.
  - `/workshops/3` (trang chi tiết, tự lấy chi nhánh): "1.032.750 ₫ — Giá sau voucher MLWS15". **Đúng**. Cùng một gói, hai bề mặt báo hai giá khác nhau.
  - Dọn dữ liệu sau test: voucher id 15 và 16 đã **tạm dừng** (không xóa) trên `ppswbs_dev`. Muốn tái hiện lại: Manager mở `/promotion-management/programs/16` → "Bật lại", chờ đến giờ hiệu lực rồi mở `/workshops`.
- Đề xuất sửa (owner 003.2/FE-03 tự sửa; FE-06 không sửa code của họ): gán `branchIds = discoveryService.sellableLocationIds(id)` khi dựng `PackageResponse` cho danh sách và chi tiết công khai; thêm test API khẳng định `branchIds` có trong response.
- Bằng chứng: `git grep "sellableLocationIds(\|branchIds("` trên `develop` và `origin/quoc` chỉ thấy định nghĩa, không có lời gọi.
- Trạng thái xử lý: **OPEN**

## BUG-INT-0002 — OPEN

- Ngày phát hiện: 2026-11-23
- Phát hiện bởi: Owner FE-06 (006) khi chạy thật kiểm tra tích hợp voucher với FE-08 (backend 8086 code `develop` 09d3644c, frontend 5178, đăng nhập Member)
- Feature: FE-08 retail-order (giỏ hàng) ← 006 loyalty-promotion
- Nguồn yêu cầu: 006 `contracts/interfaces.md` mục "Cộng dồn giảm tự động và voucher", "Việc của FE-08" bước 3 (mức của voucher là `voucherDiscount`, không phải `discountAmount` vì số đó đã gồm giảm tự động)
- Mức độ nghiêm trọng: MINOR (nhãn hiển thị sai; tổng tiền và báo giá đúng)
- Phân loại / Gán cho: Frontend FE-08
- Mô tả lỗi: Dòng "Voucher MLAND" ở giỏ hàng (`MemberCartPage.jsx` dòng 318) hiện `${voucherCode} · −${discount}` với `discount = quote.discountAmount`, là tổng giảm tự động + voucher. Trang checkout (`RetailBenefitPanel`) đã dùng đúng `voucherDiscountAmount`, nên cùng một mã hiện hai số khác nhau giữa giỏ và checkout.
- Các bước tái hiện:
  1. Member có trong giỏ các size thuộc giảm tự động "test" (id 14, giảm cố định 60.000 ₫) và đã lưu voucher `ML9KRGVFW3` (giảm 10%, trần 200.000 ₫).
  2. Mở `/member/cart`, chọn tất cả 8 dòng (Hoàn Kiếm + Tây Hồ); giỏ tự chọn `ML9KRGVFW3`.
  3. Bấm "Mua hàng" sang `/member/checkout`.
- Kết quả mong đợi: dòng voucher ở giỏ hiện "ML9KRGVFW3 · −200.000 ₫" như checkout.
- Kết quả thực tế:
  - `POST /api/v1/ready-ring-benefit-previews` trả `discountAmount: "260000"`, `autoDiscountAmount: "60000"`, `voucherDiscountAmount: "200000"` — backend đúng.
  - Giỏ: "ML9KRGVFW3 · −260.000 ₫" — **sai** (gộp 60.000 ₫ giảm tự động vào voucher).
  - Checkout: panel "ML9KRGVFW3 · −200.000 ₫"; Chi tiết thanh toán "Ưu đãi sản phẩm −60.000 ₫", "Voucher giảm giá (ML9KRGVFW3) −200.000 ₫", tổng 4.470.000 ₫ — **đúng**.
- Ghi chú thêm (không chặn): giỏ hàng tự chia `discountAmount` theo tỷ lệ tiền dòng trên `eligibleCartLineIds` để hiện giá từng dòng (`MemberCartPage.jsx` khoảng dòng 183–199), không theo `lineDiscounts` của 006; giá từng dòng ở giỏ có thể lệch với phần giảm thật của chi nhánh lúc chốt đơn. Số tiền thật không bị ảnh hưởng vì backend dùng `lineDiscounts`.
- Đề xuất sửa (owner FE-08 tự sửa): dòng 318 dùng `quote.voucherDiscountAmount ?? quote.discountAmount` như `RetailBenefitPanel`.
- Tái xác nhận 2026-11-23 16:25 (phiên kiểm thử chốt đơn): giỏ vẫn hiện "ML9KRGVFW3 · −260.000 ₫", checkout "−200.000 ₫".
- Trạng thái xử lý: **OPEN**

## BUG-INT-0003 — FIXED (phía 006, chờ commit)

- Ngày phát hiện: 2026-11-23
- Phát hiện bởi: Owner FE-06 (006) khi chạy thật luồng chốt đơn FE-08 sau tích hợp voucher (backend 8086 code `develop` 09d3644c + V196 trên `ppswbs_dev`, frontend 5178, Member `customer.test@mland.vn`)
- Feature: FE-08 retail-order (accept) ↔ 006 loyalty-promotion (terms digest khi cộng dồn)
- Nguồn yêu cầu: 006 `contracts/interfaces.md` mục "Trusted inputs" (owner dựng lại context từ dữ liệu authoritative, digest phải trùng) và mục "Cộng dồn giảm tự động và voucher" (D-015, `lineDiscounts`)
- Mức độ nghiêm trọng: BLOCKER (Member không đặt được đơn nhiều dòng có cả giảm tự động và voucher; bấm lại vẫn lỗi)
- Phân loại / Gán cho: Backend 006 (digest phụ thuộc vị trí dòng) và Backend FE-08 (accept gửi dòng khác thứ tự preview)
- Mô tả lỗi: Từ T127, khi đơn có cả giảm tự động và voucher, `RetailQuote.digest` (dòng 279–284) gắn `lineAuto`/`lineVoucher` là danh sách theo **chỉ số dòng**. FE-08 preview (`RetailCheckoutQuotePreviewService` dòng 87–92) gửi dòng theo thứ tự giỏ (`RetailCart.lines` `@OrderBy createdAt`), còn accept (`RetailCheckoutAcceptanceService` dòng 172–174) dựng lại từ `quote.getBranches()` → `getItems()`, hai `@OneToMany` không có `@OrderBy` và khóa là UUID, nên MySQL trả theo UUID. Cùng các dòng nhưng hoán vị → danh sách giảm theo dòng khác → 006 trả `QUOTE_CHANGED` → FE-08 409 `RETAIL_CONFLICT`. Mầm lỗi thứ tự có từ trước (voucher giới hạn chi nhánh gắn `lines=` cũng theo chỉ số), nhưng T127 làm nó xảy ra với mọi đơn cộng dồn nhiều dòng.
- Các bước tái hiện:
  1. Member có giỏ 8 dòng (7 dòng Hoàn Kiếm, 1 dòng Tây Hồ), có giảm tự động "test" (id 14, 60.000 ₫) và voucher `ML9KRGVFW3` (id 13, 10%, trần 200.000 ₫).
  2. `/member/cart` → Chọn tất cả → Mua hàng → `/member/checkout` → Tiền mặt tại quầy → Đặt hàng.
- Kết quả mong đợi: tạo đơn theo chi nhánh như báo giá (4.470.000 ₫).
- Kết quả thực tế:
  - `POST /api/v1/ready-ring-orders` → 409 `{"code":"RETAIL_CONFLICT","message":"Ưu đãi của đơn vừa thay đổi. Kiểm tra lại tổng tiền rồi bấm Đặt hàng."}`; bấm lại → 409. Preview ngay trước đó 200, tổng không đổi (`discountAmount` 260000, `autoDiscountAmount` 60000, `voucherDiscountAmount` 200000).
  - Log backend: `RetailCheckoutAcceptanceService - FE-06 refused the benefit of checkout quote 125648f0-… (voucher ML9KRGVFW3): QUOTE_CHANGED` (16:26:32, 16:26:56, 16:29:26).
  - Đưa dòng Tây Hồ xuống cuối giỏ (xóa rồi thêm lại) vẫn 409: quote `139d8c11-…` đọc lại dòng Hoàn Kiếm theo UUID (variant 9, 63, 70, 71, 157, 161, 192) trong khi preview theo giỏ (157, 70, 71, 63, 9, 161, 192).
  - Đối chứng: đơn 2 dòng, mỗi chi nhánh 1 dòng (không thể lệch thứ tự trong chi nhánh) → 201, phần chia đúng (xem "Đã kiểm đạt" bên dưới).
- Đề xuất sửa (chọn một hoặc cả hai):
  - 006: digest không phụ thuộc thứ tự — thay danh sách theo chỉ số bằng tập đã sắp xếp của `(productId, variantId, locationId, quantity, unitAmount, autoDiscount, voucherDiscount)`; áp cả cho `lines=` của voucher giới hạn chi nhánh. Thêm test: preview và accept cùng dòng nhưng khác thứ tự vẫn trùng digest.
  - FE-08: accept gửi dòng cùng thứ tự với preview (lưu thứ tự dòng trong quote, hoặc `@OrderBy` + sắp xếp cùng một khóa ở cả hai phía).
- Bằng chứng: `ppswbs-spec/features/006-loyalty-promotion/test-results/screenshots/INT-008-accept--quote-changed.jpg`; correlationId `f692163c-375b-4f19-aae4-373b86fc989d`.
- Đã kiểm đạt trong cùng phiên (không phải lỗi):
  - 008 accept + parts theo chi nhánh (đơn 2 dòng, gom `46ca1b59-a973-4665-a6c4-78ffd0282551`): `loyalty_accepted_benefit` id 2 gross 1.650.000, giảm tự động 60.000, voucher 159.000 (10% × 1.590.000). Part Hoàn Kiếm 1.100.000: tự động 40.000 + voucher 106.000 → đơn 954.000; part Tây Hồ 550.000: 20.000 + 53.000 → đơn 477.000. `part_ref` trùng id đơn chi nhánh; trang "Đơn mua của tôi" hiện đúng 954.000/477.000.
  - 003 booking từ `ticket_shares_json` (V196, nhóm `GRP-A8C16842FA9C408EAFD3`, 2 vé gói 1): quote `[{autoVnd 75000, voucherVnd 100000} × 2]`; `loyalty_accepted_benefit` id 3 tự động 150.000 + voucher 200.000 (`MLWS15`) + 1.450 điểm; part `L0-T1`/`L0-T2` mỗi vé 75.000 + 100.000 + 725 điểm → 652.500, khớp màn hình báo giá và thẻ vé.
  - 003.3 đổi phiên: **chưa chạy được trên màn thật** — VNPay sandbox trả mã 72 "Không tìm thấy website" (cấu hình merchant của backend 8086), backend không bật `payment-simulator.enabled`, nên vé không qua được trạng thái đã cọc. Thay bằng test hẹp: `TicketVoucherScopeServiceTest` 12/12, `TicketChangeServiceTest` 32/32, `GroupTicketChangeServiceTest` 9/9; đọc code: 006 trả `part.getVoucherDiscount()` (`TicketVoucherScopeService` dòng 65), 003.3 dùng nguyên giá trị (`TicketChangeService.lostVoucherShare`). Javadoc `TicketVoucherScope.java` dòng 12 còn ghi "programme discount" (nay là tổng hai khoản) — nhỏ, của 006.
  - Hủy riêng đơn chi nhánh: không chạy được vì nút Hủy đơn dùng `window.confirm()`, khung trình duyệt tự trả `false`.
- Dữ liệu test còn lại trên `ppswbs_dev`: 2 đơn chi nhánh `ACCEPTED_UNPAID` (giữ hàng tới 16:30 26/11/26, voucher `ML9KRGVFW3` đang HELD); nhóm workshop `GRP-A8C16842FA9C408EAFD3` chờ thanh toán (chỗ giữ tự trả khi hết hạn 10 phút, voucher `MLWS15` và 1.450 điểm trả theo outcome hết hạn). Giỏ Member 4 còn 6 dòng (dòng Tây Hồ size 7 đã được thêm lại rồi mua).
- Đã sửa (2026-11-23, owner FE-06, 006 T143): `RetailQuote.digest(result, lines, …)` cho bán lẻ gắn `lines=` (voucher giới hạn chi nhánh) và phần chia giảm tự động/voucher theo khóa dòng (sản phẩm, size, chi nhánh, số lượng, đơn giá) đã sắp xếp thay cho chỉ số; `splitByLineKey` chia phần dư bằng nhau theo khóa dòng thay cho thứ tự gửi vào, nên phần chia từng dòng không đổi khi hoán vị. Workshop giữ digest cũ. FE-08 không phải sửa.
  - Test: `LoyaltyRetailQuoteTest` 23/23 (thêm 2 test hoán vị; test cũ "hoán vị hai dòng làm đổi digest" đổi thành "hai size đổi chi nhánh làm đổi digest" — đúng ý contract, vì hoán vị cùng các dòng không phải đổi báo giá); toàn bộ test đơn vị 006 182/182.
  - Chạy lại trên màn thật (backend 8087 có bản sửa, frontend 5179): giỏ 6 dòng Hoàn Kiếm, giảm tự động 60.000 + voucher giới hạn chi nhánh `MLHK12` 90.000; quote đọc lại theo UUID (variant 9, 63, 71, 157, 161, 192) khác thứ tự giỏ (157, 71, 63, 9, 161, 192) → `POST /api/v1/ready-ring-orders` 201; `loyalty_accepted_benefit` id 4 gross 3.080.000, giảm 150.000 (tự động 60.000); đơn `abab6825-…` 2.930.000 `ACCEPTED_UNPAID`.
  - Thêm dữ liệu test: đơn `abab6825-81b8-4117-9f90-fe14f72bf4c0` (giữ hàng 3 ngày, voucher `MLHK12` HELD).
  - Bộ test 006 đầy đủ (đơn vị + IT MySQL trên DB riêng `ppswbs_it_006_1123`, kèm `ModulithArchitectureTests`): 352/352. `LoyaltyBranchVoucherFacadeIT.acceptIsQuoteChangedWhenALineMovedToAnotherBranchAfterThePreview` cũng mô phỏng "đổi chi nhánh" bằng hoán vị hai dòng cùng size → đổi sang hai size khác nhau đổi chi nhánh cho nhau; thêm `acceptTakesTheSameLinesInAnotherOrderThanThePreview`.
- Trạng thái xử lý: **FIXED** — chờ commit; backend 8086 của phiên khác vẫn chạy code cũ đến khi khởi động lại.
- Cập nhật cùng ngày cho các mục "chưa kiểm" ở trên:
  - Hết hạn chỗ giữ workshop: nhóm `GRP-A8C16842FA9C408EAFD3` hết hạn lúc 16:44:58 → `loyalty_accepted_benefit` id 3 voucher và điểm `RELEASED`, outcome `group-expired-GRP-A8C16842FA9C408EAFD3`. **Đạt**.
  - 003.3 đổi phiên: vẫn chưa chạy thật được — bộ giả lập thanh toán lỗi, xem BUG-INT-0004.

## BUG-INT-0004 — CLOSED

- Ngày phát hiện: 2026-11-23
- Phát hiện bởi: Owner FE-06 (006) khi chạy thật 003.3 đổi phiên (backend 8087 bật `payment-simulator.enabled=true`, frontend 5179, Member `customer.test@mland.vn`)
- Feature: payment-integration (`paymentintegration`, công cụ dev) — chặn kiểm thử thật mọi luồng cần thanh toán VNPay trên máy dev
- Mức độ nghiêm trọng: MAJOR cho kiểm thử (không ảnh hưởng production vì bộ giả lập mặc định tắt)
- Phân loại / Gán cho: Backend payment-integration
- Mô tả lỗi: `VnpayPaymentSimulatorController.confirm` (dòng 59) đặt `vnp_TransactionNo = "SIM" + System.currentTimeMillis()` = 16 ký tự, còn `provider_transaction_number` là `VARCHAR(15)` (`payment_ingress_receipts`, `vnpay_merchant_operations`, `settlement_evidence_conclusions`; V34/V40). Lưu IPN giả lập lỗi `Data too long for column 'provider_transaction_number'` → giao dịch rollback → 500.
- Các bước tái hiện:
  1. Chạy backend với `--payment-simulator.enabled=true`; Member đặt 1 vé workshop (nhóm `GRP-4C59D0C79D5245308D3E`, cọc 200.000 ₫), bấm "Thanh toán … qua VNPay" để tạo giao dịch.
  2. Mở `/api/v1/integrations/payments/simulator/{merchantReference}` (lấy ở `payment_owner_registrations`), bấm "Xác nhận chuyển khoản".
- Kết quả mong đợi: IPN giả lập được nhận, booking sang đã cọc.
- Kết quả thực tế: HTTP 500 `INTERNAL_ERROR` (correlationId `a8de6830-ecbd-4633-b445-07bdee4b6312`); log: `SqlExceptionHelper - Data truncation: Data too long for column 'provider_transaction_number'`, rồi `UnexpectedRollbackException`.
- Đề xuất sửa (owner payment-integration): mã giả lập tối đa 15 ký tự (ví dụ `"S" + 14 chữ số cuối của millis`, hoặc dãy số 8 chữ số như VNPay thật). VNPay sandbox cũng không dùng được trên máy dev (mã 72 "Không tìm thấy website" với cấu hình mặc định), nên hiện không có đường nào để booking qua bước đã cọc khi kiểm thử thật.
- Dữ liệu test: nhóm `GRP-4C59D0C79D5245308D3E` chờ thanh toán (voucher `MLWSHK50` đang giữ, tự trả khi hết hạn 10 phút).
- Trạng thái xử lý: **CLOSED**
- Ngày đóng: 2026-11-26
- Sửa tại: commit `3c11be2b` (2026-11-23); `VnpayPaymentSimulatorControllerTest` 5/5 Passed ngày 2026-11-25.


## BUG-E2E-0018 — CLOSED

- Ngày phát hiện: 2026-11-21
- Ngày đóng: 2026-11-21
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 004-design-request-and-human-review & Custom Manufacturing
- Kịch bản liên quan: [E2E-004-029](ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/e2e/E2E-004-029--member-custom-order-full-payment.md)
- Mức độ nghiêm trọng: CRITICAL / BLOCKER
- Phân loại / Gán cho: Backend / Database Schema
- Mô tả lỗi: Cột `payment_kind` trong bảng `payment_owner_registrations` là `varchar(24)`, trong khi chuỗi giá trị enum `CUSTOM_ORDER_FULL_PAYMENT` dài 25 ký tự, gây ra lỗi `Data truncation` khi Member bấm thanh toán toàn bộ đơn hàng gia công.
- Giải pháp:
  1. Thêm migration Flyway `V191__widen_custom_order_payment_kind.sql` nới rộng cột `payment_kind` thành `varchar(64)`.
  2. Cập nhật thuộc tính JPA `@Column(name = "payment_kind", nullable = false, length = 64)` trong `PaymentOwnerRegistration.java`.
  3. Bổ sung bộ kiểm thử đơn vị `CustomOrderServiceTest` bao phủ toàn diện việc đăng ký enum 25 ký tự, kiểm tra hết hạn quote, chặn thanh toán khi order đã trả tiền hoặc chưa được duyệt, và xác nhận thanh toán thành công.
- Bằng chứng xác minh:
  - Flyway history: V191 áp dụng thành công (`success = 1`).
  - Unit test: `CustomOrderServiceTest` (6/6 tests PASS).
  - Live E2E test `E2E-004-029`: Trình duyệt điều hướng thành công sang VNPay Sandbox, bảng `payment_owner_registrations` lưu bản ghi `id=2`, `merchant_reference=CUST-D8FD507B-1795272257029`, `payment_kind=CUSTOM_ORDER_FULL_PAYMENT`, `amount_vnd=4500000`.

## BUG-CONTRACT-0002 — CLOSED

- Ngày phát hiện: 2026-11-21
- Ngày đóng: 2026-11-21
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 004-design-request-and-human-review
- Kịch bản liên quan: [E2E-004-018](ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/e2e/E2E-004-018--guest-create-request-description.md)
- Mức độ nghiêm trọng: MINOR / CONTRACT-DEFECT
- Phân loại / Gán cho: Spec / OpenAPI Contract
- Mô tả lỗi: OpenAPI contract định nghĩa `POST /api/v1/design-requests` là Member-only trong khi `spec.md` cho phép Guest tạo request kèm daily rate limit và tracking token.
- Giải pháp: Cập nhật `custom-design-order-intake.openapi.yaml` và `APIs.md`: gỡ bỏ `security: [firebaseBearer]`, thêm `security: []`, mô tả `X-Guest-Design-Session`, `X-Guest-Tracking-Token`, rate limit 5 req/ngày và tải lên tối đa 5 ảnh.
- Bằng chứng xác minh: `git diff --check` sạch, tài liệu hợp đồng và spec hoàn toàn khớp với implementation backend/frontend.

## BUG-E2E-0016 — CLOSED

- Ngày phát hiện: 2026-11-17
- Ngày đóng: 2026-11-21
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 4 trên Mock Prod)
- Feature: 004-ring-design-configuration (FE-04 - Cấu hình nhẫn Workshop)
- Kịch bản liên quan: [JOURNEY-PHASE-4](master-e2e/JOURNEY-PHASE-4--custom-design-and-manufacturing.md)
- Mức độ nghiêm trọng: MAJOR (Đã thẩm định: Lỗi kỳ vọng kịch bản kiểm thử)
- Phân loại / Gán cho: Spec
- Kết luận điều tra: Kịch bản Phase 4 tính thiếu phụ phí và độ khó của đá quý (1 viên CZ Lam 2mm: +50.000 đ, +0.8 độ khó) và khắc chữ (+80.000 đ, +0.5 độ khó). Tổng chi phí thực tế 372.400 + 50.000 + 80.000 = 502.400 đ và độ khó 3.3/10 của hệ thống là hoàn toàn chính xác theo đúng rule engine tại `ManagerDesignRuleAdapter.java`.

## BUG-E2E-0017 — CLOSED

- Ngày phát hiện: 2026-11-17
- Ngày đóng: 2026-11-21
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 4 trên Mock Prod)
- Feature: 004-ring-design-configuration (FE-04 - Cấu hình nhẫn Workshop)
- Kịch bản liên quan: [JOURNEY-PHASE-4](master-e2e/JOURNEY-PHASE-4--custom-design-and-manufacturing.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Bấm "Lưu trữ mẫu thiết kế" dùng `window.confirm()` gốc không tạo phản hồi quan sát được trong kiểm thử tự động.
- Giải pháp: Thêm modal popup xác nhận in-page `archive-confirm-dialog`. Khi bấm "Xác nhận lưu trữ", trạng thái thiết kế chuyển sang `ARCHIVED`, badge đổi thành "Đã lưu trữ" và các nút thao tác tương ứng ẩn đi.
- Bằng chứng xác minh: Đã kiểm thử live trên trình duyệt với thiết kế `DSG-da7dbdaa-c9cd-44b7-8748-3ca18ca542fb`: Modal xuất hiện, xác nhận thành công, badge đổi thành "Đã lưu trữ", database ghi nhận `status = 'ARCHIVED'`.

---

## BUG-E2E-0014 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 3 trên Mock Prod - UX Audit)
- Feature: 008-retail-order-and-fulfillment (FE-08 - Thanh toán đơn bán lẻ `/member/checkout`)
- Kịch bản liên quan: [JOURNEY-PHASE-3](master-e2e/JOURNEY-PHASE-3--retail-loyalty-and-fulfillment.md), [UX-AUDIT-LOG](master-e2e/UX-AUDIT-LOG.md)
- Mức độ nghiêm trọng: CRITICAL (Gây bế tắc luồng thanh toán)
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Khi người dùng bấm nút "Đặt hàng" trên trang `/member/checkout`, nếu backend trả về HTTP 409 `RETAIL_CONFLICT` (`Checkout quote changed or expired; review a new quote`), frontend hoàn toàn KHÔNG hiển thị thông báo lỗi nào (không toast, không alert, không modal). Nút "Đặt hàng" chỉ dừng loading rồi trở lại trạng thái bình thường. Người dùng tưởng nút bị liệt hoặc click trượt nên bấm liên tục (Rage clicks), dẫn đến bị kẹt vô phương cứu chữa.
- Các bước tái hiện:
  1. Thêm một sản phẩm vào giỏ hàng và tiến hành vào trang `/member/checkout`.
  2. Chọn phương thức giao hàng GHTK hoặc Pick-up, để quote hết hạn hoặc thay đổi.
  3. Bấm "Đặt hàng".
- Kết quả mong đợi: Hiển thị thông báo hoặc Modal: "Báo giá đơn hàng đã thay đổi hoặc hết hạn. Vui lòng bấm [Cập nhật lại] để tiếp tục thanh toán".
- Thực tế quan sát: Bị nuốt lỗi hoàn toàn (Silent Failure), không có bất kỳ phản hồi thị giác nào.
- Bằng chứng:
  - Screenshot: ![Silent 409 Defect](master-e2e/screenshots/ux_p3_06_silent_409_defect.png)
  - Network Request #785: `POST /api/v1/ready-ring-orders` -> Status `409` Body: `{"code":"RETAIL_CONFLICT","message":"Checkout quote changed or expired; review a new quote"}`

---

## BUG-E2E-0015 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 2 trên Mock Prod - UX Audit)
- Feature: 003.1-workshop-booking (FE-03 - Giữ chỗ & Thanh toán cọc Workshop)
- Kịch bản liên quan: [JOURNEY-PHASE-2](master-e2e/JOURNEY-PHASE-2--workshop-booking-and-fulfillment.md), [UX-AUDIT-LOG](master-e2e/UX-AUDIT-LOG.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend Layout / CSS Sticky
- Mô tả lỗi: Trên màn hình xác nhận giữ chỗ workshop (`/workshops/:id/book`), sau khi hệ thống chuyển sang trạng thái "Đã giữ chỗ cho 1 vé" kèm thanh đếm ngược 10 phút, nút CTA chính "Thanh toán ... qua VNPay" bị thanh `div.main-topbar-wrapper` che khuất một phần hoặc toàn bộ ở vị trí cuộn tự nhiên. Trình duyệt tự động hoá báo lỗi: `button is covered by <div.main-topbar-wrapper>. Close or dismiss it first`. Người dùng trên laptop nếu không cuộn trang lên xuống sẽ click nhầm vào navbar thay vì nút thanh toán.
- Các bước tái hiện:
  1. Vào đặt chỗ 1 ca workshop bất kỳ, nhập số điện thoại và bấm "Xác nhận và giữ chỗ".
  2. Quan sát nút "Thanh toán ... qua VNPay".
- Kết quả mong đợi: Nút CTA nằm hoàn toàn trong vùng hiển thị an toàn, có khoảng cách đệm (padding/margin) cách xa header cố định.
- Thực tế quan sát: Header cố định đè lên vị trí nút bấm, chặn sự kiện click tự nhiên.
- Bằng chứng:
  - Screenshot: ![Hold banner header cover](master-e2e/screenshots/ux_p2_06_hold_banner.png)

---



## BUG-E2E-0010 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 3 trên Mock Prod)
- Feature: 008-retail-order-and-fulfillment (FE-08 - Giỏ hàng)
- Kịch bản liên quan: [JOURNEY-PHASE-3](master-e2e/JOURNEY-PHASE-3--retail-loyalty-and-fulfillment.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Thanh Header cố định (`header.main-topbar`) che khuất hoàn toàn checkbox "Chọn tất cả sản phẩm" ở đầu danh sách giỏ hàng (`/member/cart`). Khi kiểm thử qua trình duyệt, lệnh click bị chặn với lỗi `is covered by <header.main-topbar>`. Người dùng trên laptop không thể click chọn nhanh mà bắt buộc phải cuộn chuột xuống mới thao tác được.
- Các bước tái hiện:
  1. Thêm 1 sản phẩm vào giỏ hàng và truy cập `https://mland-operations-hub-git-develop-mland-team.vercel.app/member/cart`.
  2. Tại vị trí cuộn trang ở đỉnh (top: 0), bấm vào checkbox "Chọn tất cả sản phẩm" đầu tiên.
- Kết quả mong đợi: Checkbox nằm dưới Header an toàn và nhận click bình thường.
- Thực tế quan sát: Header đè lên checkbox, không nhận click.
- Bằng chứng:
  - Screenshot: ![Header che checkbox](master-e2e/screenshots/P3-04--cart-selected.jpg)

---

## BUG-E2E-0011 — CLOSED

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 2 trên Mock Prod)
- Feature: 003.1-workshop-booking (VNPay Integration)
- Kịch bản liên quan: [JOURNEY-PHASE-2](master-e2e/JOURNEY-PHASE-2--workshop-booking-and-fulfillment.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend / DevOps Environment Config
- Mô tả lỗi: Sau khi thanh toán cọc Workshop qua cổng VNPay Sandbox, VNPay redirect về URL ngrok trung gian (`affront-copier-barrier.ngrok-free.dev/payment/vnpay/return`) thay vì domain Vercel chính thức. Vì dùng ngrok free tier, người dùng bị vướng màn hình cảnh báo `ERR_NGROK_6024` và phải bấm nút "Visit Site" thủ công mới trở về được website Mland. Trong khi đó, luồng đơn hàng bán lẻ (Phase 3) lại redirect trực tiếp về Vercel (`member/orders`) rất mượt mà.
- Các bước tái hiện:
  1. Đặt cọc 1 vé Workshop và chuyển sang cổng VNPay Sandbox.
  2. Hoàn tất thanh toán OTP `123456`.
- Kết quả mong đợi: Cổng VNPay chuyển hướng thẳng về `https://mland-operations-hub-git-develop-mland-team.vercel.app/...`.
- Thực tế quan sát: Bị đá sang domain ngrok và hiện trang cảnh báo `ERR_NGROK_6024`.
- Bằng chứng:
  - Screenshot: ![Ngrok warning](master-e2e/screenshots/P2-06--ngrok-warning.jpg)

---
- Ngày đóng: 2026-11-26
- Kết quả kiểm lại: staging đã chuyển sang backend `https://mland-demo.io.vn`. Hai lần thanh toán sandbox ngày 26/11 từ frontend `localhost:5173` quay thẳng về `/workshop-booking/{mã}/payment-return` của `localhost`, không qua ngrok, vé được xác nhận.
- Lưu ý: chưa thử thanh toán từ bản Vercel. Nếu URL quay về vẫn trỏ `localhost:5173` thì người dùng bản Vercel sẽ không về được trang kết quả; cần kiểm cấu hình trước khi dùng bản đó để demo.

## BUG-E2E-0012 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 3 trên Mock Prod)
- Feature: 002-product-management (Trang chi tiết sản phẩm `/products/:id`)
- Kịch bản liên quan: [JOURNEY-PHASE-3](master-e2e/JOURNEY-PHASE-3--retail-loyalty-and-fulfillment.md)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Trên trang chi tiết sản phẩm nhẫn (`/products/13`), combobox "Chi nhánh" hiển thị option đầu tiên ("Mland Hà Nội – Hoàn Kiếm") nhưng giá trị state nội bộ chưa được khởi tạo. Người dùng chọn Size nhẫn và bấm nút "Thêm Vào Giỏ Hàng" hoặc "Mua Ngay" thì không có phản ứng điều hướng, toast lỗi "Vui lòng chọn chi nhánh." biến mất quá nhanh và không có viền đỏ cảnh báo cạnh ô Chi nhánh, gây cảm giác tưởng như nút bấm bị đơ liệt.
- Các bước tái hiện:
  1. Mở `https://mland-operations-hub-git-develop-mland-team.vercel.app/products/13`.
  2. Chọn "Size 10" (không click vào dropdown Chi nhánh vì thấy nó đã hiển thị Hoàn Kiếm).
  3. Bấm "Thêm Vào Giỏ Hàng" hoặc "Mua Ngay".
- Kết quả mong đợi: Hoặc tự động nhận chi nhánh đang hiển thị làm mặc định, hoặc hiển thị lỗi viền đỏ trực quan tại ô Chi nhánh.
- Thực tế quan sát: Nút bấm không phản hồi, giỏ hàng vẫn giữ nguyên `(0)`.
- Bằng chứng:
  - Screenshot: ![Product detail](master-e2e/screenshots/P3-02--product-detail.jpg)

---

## BUG-E2E-0013 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 1 trên Mock Prod)
- Feature: 001-account-management (i18n Trang chủ)
- Kịch bản liên quan: [JOURNEY-PHASE-1](master-e2e/JOURNEY-PHASE-1--guest-discovery-and-onboarding.md)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Khi chọn ngôn ngữ "EN" trên combobox Language, chỉ một số nút bấm của phần Ready Rings đổi sang tiếng Anh ("See all rings", "Add to cart"), trong khi toàn bộ tiêu đề chính ("MỘT CHIẾC NHẪN, MỘT CÂU CHUYỆN RIÊNG.", "HỆ SINH THÁI DỊCH VỤ..."), các nút CTA chính ("ĐẶT WORKSHOP NGAY", "KHÁM PHÁ READY RINGS") và menu Navbar "Khám phá" vẫn hiển thị tiếng Việt, gây tình trạng song ngữ nửa nạc nửa mỡ.
- Bằng chứng:
  - Screenshot: ![Home EN](master-e2e/screenshots/P1-02--home-en.jpg)

---

## BUG-E2E-0009 — OPEN

- Ngày: 2026-11-15
- Phát hiện bởi: QA Agent (kiểm chứng cuối 006, `ppswbs-spec/features/006-loyalty-promotion/verification.md` mục 64)
- Feature: 003.1-workshop-booking (FE-03); liên quan 006 FR-012 (voucher đã lưu)
- Kịch bản liên quan: chưa có kịch bản E2E; phát hiện khi kiểm màn thật T109 của 006
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend — owner FE-03 (Hồ Hoàng Quốc)
- Mô tả lỗi: Picker "Voucher MLAND" trên trang đặt workshop gợi ý mọi voucher Member đã lưu còn `ACTIVE`, kể cả voucher phạm vi Sản phẩm và voucher workshop giới hạn gói khác. `ppswbs_frontend/src/workshops/CartBenefitPicker.jsx:26` chỉ lọc `savedVouchers` theo `availability`, không theo phạm vi (`scope`/`packageIds`). Member chọn mã đó thì báo giá của 006 sẽ từ chối.
- Các bước tái hiện:
  1. Đăng nhập Member có voucher đã lưu gồm cả voucher sản phẩm (ví dụ `MLGIAM50K`, `MLHK12`) và voucher workshop chỉ cho gói #1 (`SV8B634DC5CE`).
  2. Mở `http://localhost:5173/workshops/2/book`.
  3. Bấm "Voucher MLAND — Chọn hoặc nhập mã", xem mục "Voucher đề xuất".
- Kết quả mong đợi: Chỉ gợi ý voucher phạm vi WORKSHOP áp được cho gói đang đặt (không có `packageIds` hoặc `packageIds` chứa gói hiện tại); hoặc nếu vẫn liệt kê thì đánh dấu không dùng được cho gói này.
- Thực tế quan sát: Danh sách có `MLGIAM50K · Trong Voucher của tôi`, `MLHK12 · Trong Voucher của tôi` (voucher sản phẩm) và `SV8B634DC5CE · Trong Voucher của tôi` (chỉ gói #1) khi đặt gói 2, cạnh `WR1579F4EEF0` và `ITBBF8230857`.
- Bằng chứng:
  - Màn thật 2026-11-15 trên `ppswbs_dev` (backend `:8080` chạy Flyway tắt, frontend `:5173`); kết quả `find` trên trình duyệt ghi ở verification 006 mục 64. Không lưu screenshot vào repo.
  - Code: `CartBenefitPicker.jsx:26` `savedVouchers.filter(item => item.availability === 'ACTIVE')`.

---

## Danh sách lỗi đã đóng / nghiệm thu (Resolved & Closed Defects)

## BUG-E2E-0010 — CLOSED

- Ngày mở: 2026-11-18
- Ngày đóng / nghiệm thu: 2026-11-18
- Phát hiện bởi: User & Senior QA / System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 011-review-management
- Kịch bản liên quan: [E2E-011-006](../ppswbs-spec/features/011-review-management/test-results/E2E-011-006--review-edit-ownership-authorization.md)
- Mức độ nghiêm trọng: MAJOR / IDOR (Insecure Direct Object Reference)
- Phân loại / Gán cho: Frontend & Backend (Review Detail & Edit Form Authorization Guard)
- Mô tả lỗi ban đầu: Người dùng đã đăng nhập có thể truy cập vào màn hình chỉnh sửa đánh giá (`/reviews/:reviewId/edit`) của người dùng khác bằng cách sửa URL hoặc bấm nút "Chỉnh sửa đánh giá của bạn" (nút này luôn render cho bất kỳ user nào đã đăng nhập tại trang chi tiết `/reviews/:reviewId`, bất kể ai là người tạo đánh giá). Mặc dù Backend `PATCH` từ chối cập nhật khi kiểm tra quyền, giao diện Frontend vẫn để lộ form sửa với toàn bộ nội dung đánh giá của người khác và cho phép gửi request.
- Các bước tái hiện ban đầu:
  1. Đăng nhập tài khoản Member (`member.test@mland.vn` / `Member@123456`).
  2. Mở đánh giá của người dùng khác tại `http://localhost:5173/reviews/rev-101-5star`.
  3. Thấy nút "Chỉnh sửa đánh giá của bạn" vẫn hiển thị.
  4. Bấm nút hoặc truy cập trực tiếp `http://localhost:5173/reviews/rev-101-5star/edit`: Form chỉnh sửa mở ra và pre-fill toàn bộ nội dung của người khác.
- Biện pháp khắc phục đã triển khai:
  1. **Backend**:
     - Cập nhật `ReviewDto` bổ sung trường `private Boolean canEdit;`.
     - Trong `ReviewApplicationService.java`, logic kiểm tra quyền xác định: `boolean owner = actor != null && actor.role() == ActorRole.MEMBER && review.getOwnerType() == OwnerType.MEMBER && String.valueOf(actor.accountId()).equals(review.getOwnerReference());`.
     - Tính toán `boolean canEdit = owner && review.getStatus() != ReviewStatus.REMOVED;` và truyền qua `mapToReviewDto(...)` để trả về cho client.
  2. **Frontend**:
     - `ReviewDetailPage.jsx`:
       * Truyền cờ xác thực vào `api.getReview(reviewId, status === 'signed-in')` để gửi kèm Bearer token giúp backend nhận diện `actor`.
       * Ẩn hoàn toàn nút chỉnh sửa nếu `!review?.canEdit`: `{status === 'signed-in' && Boolean(review?.canEdit) && (<Link to={...}>...)}`.
     - `EditReviewPage.jsx`:
       * Gọi `api.getReview(reviewId, true)` kèm Bearer token.
       * Thiết lập Route / Component Guard: Nếu `!resource.data?.canEdit`, chặn render `ReviewForm` và hiển thị màn hình thông báo từ chối truy cập:
         *"Bạn không có quyền chỉnh sửa đánh giá này. Chỉ chủ sở hữu đánh giá mới có thể chỉnh sửa."* kèm nút điều hướng quay lại.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-18 qua Browser MCP):
  - Kịch bản `E2E-011-006` được thực thi trên Chrome thực tế bằng `browsermcp`:
    + Đăng nhập `member.test@mland.vn` và mở đánh giá của người khác (`/reviews/rev-101-5star`): Nút "Chỉnh sửa đánh giá của bạn" hoàn toàn biến mất (chỉ còn nút "Quay lại").
    + Cố tình đổi URL sang `/reviews/rev-101-5star/edit`: Form chỉnh sửa bị chặn hoàn toàn, hiển thị thông báo lỗi quyền sở hữu tiếng Việt rõ ràng, không để lộ dữ liệu form.
    + Mở đánh giá của chính mình (`/reviews/cc6446de-4851-4c95-8b81-7965213da398`): Nút "Chỉnh sửa đánh giá của bạn" hiển thị bình thường.
    + Bấm sửa: Form mở ra đầy đủ thông tin, chỉnh sửa nội dung và lưu thay đổi thành công (HTTP 200).
  - Trạng thái kiểm chứng: **PASS 100%**.
  - Bằng chứng screenshot:
    + [E2E-011-006--forbidden-edit-url-tampering.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-006--forbidden-edit-url-tampering.jpg)
    + [E2E-011-006--other-user-no-edit-btn.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-006--other-user-no-edit-btn.jpg)
    + [E2E-011-006--owner-edit-allowed.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-006--owner-edit-allowed.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0008 — CLOSED

- Ngày mở: 2026-11-14
- Ngày đóng / nghiệm thu: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-002](../ppswbs-spec/features/001-account-management/test-results/E2E-001-002--member-address-book-flow.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend & Backend (Chưa triển khai cập nhật đặc tả sổ địa chỉ 2 cấp ngày 2026-11-14)
- Mô tả lỗi ban đầu: Biểu mẫu modal "Thêm địa chỉ nhận hàng mới" trên Frontend và API lưu trữ Backend hoạt động theo mô hình 4 cấp GHTK cũ (Tỉnh -> Huyện -> Xã -> Thôn/Ấp), tự động pre-fill họ tên từ Profile, không tuân thủ luồng nhập liệu tuần tự Shopee-style. Mâu thuẫn với bản sửa đổi đặc tả ngày 2026-11-14 (FR-036, FR-037, FR-038, FR-041, FR-042) và OpenAPI schema `account-management.openapi.yaml`.
- Biện pháp khắc phục đã triển khai (Commit `b2b88bb6`):
  1. Backend: Bổ sung Flyway migration `V93__two_level_administrative_divisions_and_shipping_addresses.sql` và entities `AdministrativeProvince`, `AdministrativeCommune`, `AdministrativeDirectoryVersion`; refactor `MemberAddress` lưu mã và tên 2 cấp (`provinceCode`, `communeCode`, `detailAddress`); cập nhật `AdministrativeDivisionService` cung cấp API tra cứu địa giới 2 cấp và `MemberAddressService` lưu trữ snapshot 2 cấp.
  2. Frontend: Refactor `AddressModal.tsx` mở form ở trạng thái trống hoàn toàn (FR-036), kiểm soát luồng nhập liệu tuần tự Shopee-style theo cờ validation (`Họ tên` -> `SĐT` -> `Tỉnh/Thành` -> `Phường/Xã` -> `Địa chỉ chi tiết`) (FR-037), hỗ trợ ô lọc nhanh không dấu cho Phường/Xã (FR-041), và tự động reset các trường cấp con khi thay đổi cấp cha.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-14 qua Browser MCP & Automated Suites):
  - Kịch bản `E2E-001-002` chạy lại trên Chrome thực tế qua `browsermcp`:
    + Form mở ra ở trạng thái trống, không pre-fill họ tên từ Profile (PASS FR-036).
    + Nhập liệu tuần tự: SĐT chỉ mở khóa khi nhập Họ tên; Tỉnh/Thành chỉ mở khóa khi nhập SĐT di động hợp lệ (chặn số bàn 0241234567, chuẩn hóa +84 912 345 678); Phường/Xã chỉ mở khóa khi chọn Tỉnh/Thành; Địa chỉ cụ thể chỉ mở khóa khi chọn Phường/Xã (PASS FR-037).
    + Ô tìm kiếm Phường/Xã lọc nhanh không dấu (nhập "trong" lọc ra "Phường Hàng Trống") (PASS FR-041).
    + Chọn đổi Tỉnh từ Hà Nội sang TP.HCM: Phường/Xã và Địa chỉ cụ thể tự động bị xóa và khóa lại (PASS FR-037 cascade reset).
    + Lưu địa chỉ thành công, hiển thị đúng địa giới 2 cấp: "Số 2 Lê Duẩn, Phường Bến Nghé, Thành phố Hồ Chí Minh" kèm badge "Mặc định", badge "Văn phòng" và quota "Đã lưu 1 trên 10 địa chỉ".
    + Xóa địa chỉ duy nhất thành công và trở lại trạng thái danh sách trống.
  - Automated Tests:
    + Frontend Vitest: 7 test suites, 94/94 tests PASS (bao gồm 15/15 tests trong `addressBook.test.tsx`).
    + Backend JUnit: `MemberAddressServiceTest` PASS 2/2 tests (BUILD SUCCESS).
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0007 — CLOSED

- Ngày mở: 2026-11-13
- Ngày đóng / nghiệm thu: 2026-11-13
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 012-workshop-ring-design-configuration
- Kịch bản liên quan: [E2E-012-005](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-005--guest-expiry-and-member-ownership.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend (Auth Context Consumption in DesignEvaluationPage & RingConfigurationPage)
- Mô tả lỗi ban đầu: Trong `DesignEvaluationPage.jsx` (dòng 15-16) và `RingConfigurationPage.jsx` (dòng 17-18), mã nguồn sử dụng `const { user } = useAuth(); const isAuthenticated = Boolean(user);`. Tuy nhiên, `AuthProvider.jsx` trả về AuthContext không chứa thuộc tính `user` mà chứa `{ status, userId, source, signOut, subscribeOutcome }`. Do đó, `user` luôn là `undefined` và `isAuthenticated` luôn là `false`. Khi hội viên đã đăng nhập truy cập vào trang thẩm định thiết kế cá nhân (`/workshops/:packageRef/design/:designId`), hàm `api.getDesign(designId, null, isAuthenticated)` được gọi với `isAuthenticated = false`, không đính kèm header `Authorization: Bearer <token>`. Backend `WorkshopRingDesignController` không xác định được actor hội viên (`resolveMemberAccountId() == null`), coi request là khách vãng lai và ném ngoại lệ HTTP 403 `DESIGN_ACCESS_DENIED`, khiến hội viên không thể xem chi tiết hoặc lưu trữ (archive) thiết kế nhẫn của mình.
- Các bước tái hiện ban đầu:
  1. Đăng nhập tài khoản Member (`member.test@mland.vn` / `Member@123456`) tại `http://localhost:5173/login`.
  2. Điều hướng đến `http://localhost:5173/my/designs` (thấy danh sách thiết kế hợp lệ `DSG-DEMO-MEMBER-01`).
  3. Bấm "Xem chi tiết →" để điều hướng đến `http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/DSG-DEMO-MEMBER-01`.
- Biện pháp khắc phục đã triển khai:
  1. Sửa `ppswbs_frontend/src/features/ringcustomization/ui/DesignEvaluationPage.jsx` và `RingConfigurationPage.jsx`:
     - Thay `const { user } = useAuth(); const isAuthenticated = Boolean(user);`
     - Bằng `const { status, userId } = useAuth(); const isAuthenticated = status === 'signed-in' || Boolean(userId);`.
  2. Đảm bảo khi hội viên đã đăng nhập, `isAuthenticated` là `true` và đính kèm header `Authorization: Bearer <token>` vào request `GET /api/v1/workshop-designs/:id`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-13 qua Browser MCP):
  - Kịch bản `E2E-012-005` được chạy lại trên Chrome thực tế bằng `browsermcp`:
    + Member mở `/my/designs` thấy `DSG-DEMO-MEMBER-01`.
    + Bấm "Xem chi tiết →" mở `/workshops/PKG-COUPLE-SILVER/design/DSG-DEMO-MEMBER-01` thành công (HTTP 200 kèm Bearer token), hiển thị đầy đủ thông số nhẫn, độ khó 2.0/10 và nút "Lưu trữ mẫu thiết kế" (`archive-design-btn`).
    + Bấm "Lưu trữ mẫu thiết kế" gửi `POST /api/v1/workshop-designs/DSG-DEMO-MEMBER-01/archive` thành công với `If-Match-Version: 2`, status chuyển thành `ARCHIVED` trong DB và hiển thị badge "ĐÃ LƯU TRỮ".
  - Trạng thái: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-012-005--success.jpg](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-005--success.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0006 — CLOSED

- Ngày mở: 2026-11-13
- Ngày đóng / nghiệm thu: 2026-11-13
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 011-review-management
- Kịch bản liên quan: [E2E-011-004](../ppswbs-spec/features/011-review-management/test-results/E2E-011-004--staff-manager-moderation.md), [E2E-011-005](../ppswbs-spec/features/011-review-management/test-results/E2E-011-005--manager-reply-and-aggregate.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend & Frontend (Review Detail Authorization & Moderation Detail API)
- Mô tả lỗi ban đầu: Trong `ReviewApplicationService.getReviewDetail`, logic kiểm tra `if (review.getStatus() != ReviewStatus.PUBLIC && !owner)` chỉ cho phép `owner` (Member tác giả) đọc chi tiết review khi review không ở trạng thái `PUBLIC`. Khi review đã được Staff/Manager chuyển sang `HIDDEN` hoặc `REMOVED`, màn hình kiểm duyệt `/review-moderation/:reviewId` gọi `GET /api/v1/reviews/:reviewId` bị backend ném lỗi HTTP 404 `REVIEW_NOT_FOUND` (đồng thời Frontend `reviewManagementApi.getReview` mặc định không truyền `auth: true` và `IdentityGateFilter` bypass không trích xuất actor). Dẫn đến việc Manager không thể mở chi tiết đánh giá đã ẩn để khôi phục (`RESTORE`) hay phản hồi (`createReply`).
- Các bước tái hiện ban đầu:
  1. Thực hiện ẩn một đánh giá (chuyển sang `status = 'HIDDEN'`).
  2. Đăng nhập tài khoản Quản lý (`manager.test@mland.vn` / `ManagerTest@123456`).
  3. Mở trang `/review-moderation`, lọc trạng thái "HIDDEN" và bấm "Open".
- Biện pháp khắc phục đã triển khai:
  1. Backend `IdentityGateFilter.java`: Bổ sung điều kiện chỉ bypass xác thực cho `/api/v1/reviews/*` khi request KHÔNG có header `Authorization: Bearer`. Khi có header xác thực, request vẫn đi qua `AccountTokenAuthenticationFilter` để khởi tạo `ActorContext`.
  2. Backend `ReviewApplicationService.java`: Bổ sung điều kiện `isInternalModerator = actor != null && (actor.role() == AccountRole.STAFF || actor.role() == AccountRole.MANAGER)` cho phép nhân viên và quản lý nội bộ xem chi tiết review ngay cả khi review không ở trạng thái `PUBLIC`.
  3. Frontend `ModerationDetailPage.jsx`: Cập nhật gọi `api.getReview(reviewId, true)` (truyền cờ `auth = true`) để gửi kèm token xác thực.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-13 qua Browser MCP):
  - Kịch bản `E2E-011-004` được chạy lại trên Chrome thực tế bằng `browsermcp`:
    + Staff ẩn review (`status = HIDDEN`) thành công (HTTP 200).
    + Staff bấm Gỡ đánh giá bị từ chối chính xác với HTTP 403 Forbidden (PASS RBAC).
    + Manager đăng nhập lọc "HIDDEN" và click "Open" mở trang `/review-moderation/:id` thành công (HTTP 200).
    + Manager bấm "Khôi phục công khai" (RESTORE) với lý do hợp lệ, backend xử lý thành công, status cập nhật sang `PUBLIC` trong DB.
  - Trạng thái: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-011-004--moderation-success.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-004--moderation-success.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0005 — CLOSED

- Ngày mở: 2026-11-13
- Ngày đóng / nghiệm thu: 2026-11-13
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 004-ai-chatbox-analysis-and-suggestion
- Kịch bản liên quan: [E2E-004-002](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/E2E-004-002--member-design-request.md), [E2E-004-003](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/E2E-004-003--staff-manager-review.md)
- Mức độ nghiêm trọng: BLOCKER
- Phân loại / Gán cho: Backend (Database Schema / Flyway Migration)
- Mô tả lỗi ban đầu: Khi Member gửi yêu cầu phân tích thiết kế kèm ảnh và thỏa thuận xử lý ảnh (`POST /api/v1/design-requests`), Backend Spring Boot ném lỗi `DataIntegrityViolationException` và trả về HTTP 500. Bảng `temporary_image_references` (và các bảng phụ thuộc `design_analysis_results`, `human_review_decisions` từ `V4__ai_chatbox_and_design_review.sql`) vẫn giữ foreign key constraint cũ `fk_tir_request` tham chiếu tới bảng `booking_design_requests` thay vì bảng độc lập mới `design_requests` (được tạo ở `V62__independent_design_request.sql`).
- Các bước tái hiện ban đầu:
  1. Đăng nhập tài khoản Member (`member.test@mland.vn` / `Member@123456`) tại `http://localhost:5173/login`.
  2. Điều hướng đến `http://localhost:5173/design-requests/new`.
  3. Nhập mô tả ý tưởng, chọn tệp ảnh hợp lệ `ring-sample.png`, tích thỏa thuận consent và submit.
- Biện pháp khắc phục đã triển khai:
  1. Tạo append-only migration mới `ppswbs_backend/src/main/resources/db/migration/V119__independent_design_request_foreign_keys.sql`:
     - Xóa các foreign key constraint cũ trỏ về `booking_design_requests`.
     - Thêm các foreign key constraint mới `fk_tir_design_request`, `fk_dar_design_request`, `fk_hrd_design_request` trỏ về bảng độc lập `design_requests (id)`.
  2. Khởi động lại Spring Boot backend, Flyway tự động migrate thành công `119 | independent design request foreign keys | success = 1`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-13 qua Browser MCP):
  - Kịch bản `E2E-004-002` chạy lại trên Chrome thực tế bằng `browsermcp`:
    + Member tải ảnh `ring-sample.png`, tích consent, bấm "Gửi yêu cầu thiết kế".
    + Backend xử lý `POST /api/v1/design-requests` trả về HTTP 202 Accepted.
    + Giao diện chuyển hướng đến `/design-requests/:id`, hiển thị mã `DR-7B1BE554`, trạng thái `SUCCEEDED` / `NEED_REVIEW`, cùng toàn bộ candidate features và chỉ số chất lượng ảnh mà không để lộ raw provider payload.
  - Kịch bản `E2E-004-003` được unblock hoàn toàn:
    + Staff đăng nhập mở hàng đợi `/design-review-requests` thấy yêu cầu `DR-7B1BE554`.
    + Staff chọn quyết định `ACCEPTED`, nhập lý do và bấm xác nhận, lưu thành công vào `human_review_decisions` và chuyển `design_requests` sang `ACCEPTED` / `DESIGN_ACCEPTED`.
  - Trạng thái: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-004-002--success.jpg](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-002--success.jpg), [E2E-004-003--success.jpg](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-003--success.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0005 — CLOSED

- Ngày mở: 2026-11-14
- Ngày khắc phục: 2026-11-14
- Ngày đóng / nghiệm thu: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Khắc phục bởi: Senior React Frontend Implementation Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer (Phan Sơn)
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-007](../ppswbs-spec/features/001-account-management/test-results/E2E-001-007--admin-role-catalogue-rbac-guard.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi ban đầu: Lệch trường dữ liệu hợp đồng (Contract Misalignment) giữa Frontend và Backend tại API `/api/v1/roles`. Backend trả về `{ code, displayName }` theo đúng đặc tả OpenAPI (`RoleEntryResponse`), nhưng Frontend `RoleCataloguePage.tsx` và `AccountManagementApiClient.ts` lại truy xuất `{ r.role, r.description }`. Kết quả là bảng danh mục 4 vai trò trên `/admin/roles` bị rỗng toàn bộ nội dung tên vai trò và mô tả quyền hạn.
- Các bước tái hiện:
  1. Đăng nhập tài khoản có quyền Admin (ví dụ: `qa-e2e-feat007@test.com`).
  2. Truy cập đường dẫn: `http://localhost:5173/admin/roles`.
  3. Quan sát bảng "Danh mục vai trò hệ thống (Fixed Roles)".
- Kết quả mong đợi: Bảng hiển thị 4 vai trò (MEMBER, STAFF, MANAGER, ADMIN) cùng mô tả phạm vi và quyền hạn tương ứng.
- Thực tế quan sát trước khi sửa: Bảng hiển thị 4 dòng nhưng cả cột "Vai trò" (badge rỗng) và cột "Mô tả phạm vi và quyền hạn" đều trắng tinh.
- Bằng chứng lỗi ban đầu:
  - API response từ `GET /api/v1/roles`: `{"roles":[{"code":"MEMBER","displayName":"Khách hàng thành viên / Member"},{"code":"STAFF","displayName":"Nhân viên cơ sở / Staff"},{"code":"MANAGER","displayName":"Quản lý cơ sở / Manager"},{"code":"ADMIN","displayName":"Quản trị viên / Admin"}]}`
  - DOM Table quan sát được: `<tr><td><span class="account-badge account-badge-readonly"></span></td><td></td></tr>`
- Nguyên nhân gốc rễ (Root Cause):
  - Đặc tả OpenAPI `account-management.openapi.yaml` định nghĩa `RoleEntryResponse` gồm 2 thuộc tính bắt buộc: `code` (chuỗi enum vai trò) và `displayName` (tên hiển thị vai trò).
  - Backend Spring Boot (`RoleCatalogueController.java`) trả về DTO chuẩn `{ roles: [{ code, displayName }] }`.
  - Frontend `RoleCataloguePage.tsx` và interface `RoleCatalogueEntry` trong `AccountManagementApiClient.ts` trước đó định nghĩa và bóc tách các trường phi tiêu chuẩn `{ role, description }`, dẫn đến `r.role` và `r.description` nhận giá trị `undefined` khi nhận payload thật từ Backend.
- Biện pháp khắc phục đã triển khai (Frontend Dev):
  1. `ppswbs_frontend/src/features/accountmanagement/api/AccountManagementApiClient.ts`:
     - Cập nhật interface `RoleCatalogueEntry` bổ sung đầy đủ trường OpenAPI chuẩn `code?: 'MEMBER' | 'STAFF' | 'MANAGER' | 'ADMIN'` và `displayName?: string`, đồng thời duy trì fallback tương thích ngược.
  2. `ppswbs_frontend/src/features/accountmanagement/components/RoleCataloguePage.tsx`:
     - Cập nhật rendering JSX bóc tách an toàn theo chuẩn hợp đồng: `const roleCode = r.code || r.role;` và `const roleDesc = r.displayName || r.description;`.
     - Hiển thị badge vai trò với `roleCode` và cột mô tả với `roleDesc`.
  3. `ppswbs_frontend/src/features/accountmanagement/tests/accountManagementComponents.test.tsx`:
     - Bổ sung unit/component test ca: `renders OpenAPI standard response with code and displayName properties (BUG-E2E-0005)`.
     - Toàn bộ 12 test trong suite component test và 92/92 test trong toàn feature `accountmanagement` đều PASS.
- Kết quả nghiệm thu thực tế của QA (QA Verification):
  - **Trình duyệt**: Live Chrome qua `browsermcp` tại `http://localhost:5173/admin/roles`.
  - **DOM Table trích xuất**:
    ```html
    <table class="account-table">
      <thead><tr><th style="width: 180px;">Vai trò</th><th>Mô tả phạm vi và quyền hạn</th></tr></thead>
      <tbody>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">MEMBER</span></td><td style="line-height: 1.6;">Khách hàng thành viên / Member</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">STAFF</span></td><td style="line-height: 1.6;">Nhân viên cơ sở / Staff</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">MANAGER</span></td><td style="line-height: 1.6;">Quản lý cơ sở / Manager</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">ADMIN</span></td><td style="line-height: 1.6;">Quản trị viên / Admin</td></tr>
      </tbody>
    </table>
    ```
  - **Đánh giá**: Tất cả 4 vai trò cố định và mô tả hiển thị chính xác theo hợp đồng OpenAPI và Revision R-MGA.
- Trạng thái xử lý: **CLOSED**

---

## BUG-E2E-0004 — CLOSED

- Ngày mở: 2026-11-12
- Ngày đóng / nghiệm thu: 2026-11-12
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 012-workshop-ring-design-configuration
- Kịch bản liên quan: [E2E-012-003](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-003--save-evaluate-and-version-history.md), [E2E-012-005](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-005--guest-expiry-and-member-ownership.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Full-Stack (Frontend & Backend)
- Mô tả lỗi ban đầu: Khi người dùng vãng lai (Guest) bấm nút "Lưu & Thẩm định" trên trang cấu hình nhẫn (`/workshops/:packageRef/design/configure`), Frontend gửi request lưu phiên bản mà không khởi tạo/đính kèm header `X-Guest-Design-Session`. Backend ném ngoại lệ `RingDesignException` nhưng không được xử lý chuẩn mà rơi vào `AccountExceptionHandler` trả về HTTP 500 Unhandled Error. Giao diện bị kẹt với thông báo "An unexpected error occurred".
- Các bước tái hiện ban đầu:
  1. Điều hướng đến `http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK`
  2. Chọn chất liệu Vàng 14K, Dáng xoắn vô cực, Size 12, Đính đá CZ Lam 2mm, Khắc chữ "Forever Together 2026".
  3. Bấm nút "Lưu & Thẩm định".
- Nguyên nhân gốc rễ (Root Cause):
  1. Backend `WorkshopRingDesignExceptionHandler` thiếu annotation `@Order(Ordered.HIGHEST_PRECEDENCE)`. Khi có ngoại lệ `RingDesignException`, Spring MVC ưu tiên gọi `AccountExceptionHandler` bao quát `basePackages = "edu.fpt.sep490_g22.ppswbs_backend"` dẫn đến fallback vào `Exception.class` và trả về HTTP 500.
  2. Khi khách vãng lai gọi `POST /api/v1/workshop-designs` tạo bản nháp mới, Backend đã sinh `GuestDesignSession` trong DB nhưng không trả về mã `sessionReference` cho Frontend qua DTO hoặc HTTP Header `X-Guest-Design-Session`. Do đó, request lưu phiên bản tiếp theo thiếu header phiên làm việc.
- Biện pháp khắc phục đã triển khai:
  1. Backend: Bổ sung `@Order(Ordered.HIGHEST_PRECEDENCE)` trên `WorkshopRingDesignExceptionHandler`. Bổ sung trường `guestSession` vào `DesignResponseDto` và trả về kèm header `X-Guest-Design-Session` tại `WorkshopRingDesignController.createWorkshopDesign`.
  2. Frontend: Cập nhật `useWorkshopDesignCommand.js` để tự động lưu `res.guestSession` vào `sessionStorage` qua `setStoredGuestSession`. Cập nhật `DesignEvaluationPage.jsx` để dùng `getErrorMessage(err)` ánh xạ chuẩn xác thông báo hết hạn phiên 20 phút bằng tiếng Việt.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-12 qua Browser MCP):
  - Kịch bản `E2E-012-003` tái kiểm tra trên Chrome thực tế: Bấm "Lưu & Thẩm định" thành công chuyển sang `/design/{designId}`, hiển thị đầy đủ bảng chỉ số kỹ thuật và chi phí tham khảo ước tính (HTTP 201 + 200). Chuyển tiếp sang `/workshops/booking?...` đạt yêu cầu. Trạng thái: **PASS**. Bằng chứng JPEG: ![Thành công](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-003--success.jpg).
  - Kịch bản `E2E-012-005` (nhánh phiên hết hạn): Truy cập thiết kế với phiên hết hạn trả về HTTP 401 `DESIGN_SESSION_EXPIRED` và hiển thị alert tiếng Việt: *"Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động. Vui lòng bắt đầu lại để tiếp tục."*. Trạng thái: **PASS**. Bằng chứng JPEG: ![Phiên hết hạn](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-005--session-expired.jpg).

## BUG-E2E-0004 — CLOSED

- Ngày mở: 2026-11-14
- Ngày khắc phục: 2026-11-14
- Ngày đóng / nghiệm thu: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Khắc phục bởi: Senior Java/Spring Backend Implementation Engineer & Database Architect
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 007-notification-management
- Kịch bản liên quan: [E2E-007-011](../ppswbs-spec/features/007-notification-management/test-results/e2e/E2E-007-011--redeliver-with-refreshed-credential.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend / Database Schema
- Mô tả lỗi ban đầu: 
  Endpoint `POST /api/v1/notification-requests/{id}/redeliver` trả về lỗi HTTP 500 (Internal Server Error) do xung đột ràng buộc toàn vẹn cơ sở dữ liệu `DataIntegrityViolationException`.
  Khi một yêu cầu gửi thông báo gốc (`original_request_id`) đã từng phát sinh một yêu cầu gửi lại (resend hoặc redeliver trước đó, ví dụ tạo request #1005 từ request gốc #1001), việc thực hiện thao tác redeliver (hoặc resend lần thứ 2) trên cùng request gốc #1001 sẽ cố gắng chèn một bản ghi mới với cùng bộ giá trị:
  `(business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_request_id = 1001)`.
  Ràng buộc `uq_nr_idempotency` trong Flyway migration `V111__notification_management.sql`:
  ```sql
  UNIQUE KEY uq_nr_idempotency (business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_request_id)
  ```
  từ chối bản ghi thứ 2, dẫn tới Crash 500 ở backend thay vì xử lý mượt mà hoặc cấp định danh/chu kỳ phân biệt.
- Biện pháp khắc phục đã triển khai:
  1. Flyway migration `V140__notification_original_request_idempotency.sql`:
     - Chuẩn hóa các chuỗi phả hệ derived request trỏ trực tiếp về root request.
     - Drop chỉ mục unique `uq_nr_idempotency`.
     - Bổ sung cột sinh `original_idempotency_scope TINYINT GENERATED ALWAYS AS (CASE WHEN original_request_id IS NULL THEN 1 ELSE NULL END) STORED`.
     - Tạo unique constraint mới `uq_nr_original_idempotency (business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_idempotency_scope)`. Do SQL bỏ qua duplicate NULLs, constraint chỉ bảo vệ tính duy nhất của request gốc và cho phép tạo không giới hạn derived requests.
  2. JPA Entity `NotificationRequest.java`: Bổ sung mapping cột sinh `@Column(name = "original_idempotency_scope", insertable = false, updatable = false)`.
  3. Bổ sung test tích hợp `NotificationManagementIT#verifyMultipleResendAndRedeliverOnSameRootSucceeds` (Task T034).
- Kết quả kiểm chứng lại (QA Re-test 2026-11-14 qua Browser MCP trên live Docker environment):
  - Kịch bản `E2E-007-011` được thực thi lại trên môi trường Docker cục bộ (`http://localhost:5174` và backend `http://localhost:8081`):
    + Quản lý mở chi tiết yêu cầu gốc #1001 (đã có request gửi lại trước đó).
    + Bấm "Gửi lại với link/vé mới", nhập lý do "Khách hàng liên hệ xin link mới do link 24h đã hết hạn" và xác nhận.
    + Backend xử lý thành công, trả về HTTP 201 Created và sinh request mới #1009 với `original_request_id = 1001`.
    + Không còn lỗi HTTP 500 hay `DataIntegrityViolationException`.
    + Bảng `notification_operational_audit` lưu vết chính xác hành động `REDELIVER_REFRESHED_CREDENTIAL`.
  - Trạng thái kiểm chứng: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-007-011--redeliver-success.jpg](../ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-011--redeliver-success.jpg).
- Trạng thái xử lý: **CLOSED**


<!--
## BUG-E2E-0000 — OPEN

- Ngày: YYYY-MM-DD
- Phát hiện bởi: QA Agent (Tester)
- Feature: 003-workshop-management
- Kịch bản liên quan: [E2E-003-001](../ppswbs-spec/features/003-workshop-management/test-results/E2E-003-001--booking-flow.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Nút "Xác nhận đặt lịch" bị vô hiệu hóa dù đã chọn đủ thời gian và số lượng người.
- Các bước tái hiện:
  1. goto http://localhost:5173/workshops/1
  2. click getByRole('button', { name: 'Chọn suất 09:00' })
  3. fill getByLabel('Số lượng khách') "2"
  4. Quan sát nút "Xác nhận đặt lịch"
- Kết quả mong đợi: Nút "Xác nhận đặt lịch" chuyển sang trạng thái enabled và cho phép click.
- Thực tế quan sát: Nút bị disabled vĩnh viễn, console log báo lỗi "Uncaught TypeError: selectedSlot.capacity is undefined".
- Bằng chứng:
  - Screenshot: ![Lỗi](../ppswbs-spec/features/003-workshop-management/test-results/screenshots/E2E-003-001--slot-disabled.jpg)
-->

## BUG-QA-0003 — RESOLVED

- Ngày phát hiện/khắc phục: 2026-11-06
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Phân loại / Gán cho: Test artifact / QA
- Mô tả: Các E2E artifact lịch sử ghi credential/định danh fixture trực tiếp và giữ trạng thái PASS dù shared session/route contract đã đổi.
- Tác động: Có nguy cơ lộ secret test và tạo acceptance sai cho revision R-SL.
- Khắc phục: Redact fixture/credential, đánh dấu evidence cũ là historical, đặt case revision thành `NOT_RUN`, và thêm E2E-001-013 đến E2E-001-020.
- Bằng chứng: Static scan không còn fixture credential hoặc current `PASS` trong `ppswbs-spec/features/001-account-management/test-results/`.

## BUG-E2E-0002 — CLOSED

- Ngày mở: 2026-11-05
- Ngày đóng: 2026-11-05
- Phát hiện bởi: QA Agent & User Verification
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-008](../ppswbs-spec/features/001-account-management/test-results/E2E-001-008--google-signin-account-collision.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Luồng Google Sign-In redirect không kích hoạt thông báo xử lý lỗi xung đột tài khoản khi quay trở lại trang đăng nhập `/login` trên môi trường thực tế. Khi người dùng chọn tài khoản Google `minikie456@gmail.com` (trùng email đã có mật khẩu), trình duyệt quay lại trang `/login` nhưng hoàn toàn không hiển thị thông báo cảnh báo nào trong vòng 5 giây (xảy ra hiện tượng Silent Failure).
- Các bước tái hiện:
  1. Mở trang `http://localhost:5173/login`.
  2. Bấm nút "Đăng nhập với Google".
  3. Tại màn hình Google OAuth, chọn tài khoản `minikie456@gmail.com`.
  4. Trình duyệt chuyển hướng quay lại `http://localhost:5173/login`.
  5. Quan sát phản hồi giao diện trong vòng 5 giây.
- Kết quả mong đợi: Trong vòng tối đa 5 giây, giao diện phải hiển thị thông báo alert:
  "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
- Thực tế quan sát trước khi sửa: Giao diện im lặng (Silent Failure), form đăng nhập hiển thị bình thường không có bất kỳ dòng thông báo lỗi nào, khiến người dùng không biết đăng nhập thất bại vì lý do gì.
- Bằng chứng lỗi ban đầu:
  - Screenshot: ![Lỗi im lặng không thông báo](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-008--google-collision-silent-fail.jpg)
- Biện pháp khắc phục (Frontend Dev):
  1. Nguyên nhân gốc rễ: Chrome 115+ kích hoạt tính năng Phân vùng bộ nhớ (Storage Partitioning / third-party storage blocking) đối với iframe ẩn của Firebase Auth trên localhost, dẫn đến `getRedirectResult()` bị nuốt token và luôn trả về `null` trên localhost.
  2. Triển khai phương thức `signInWithGooglePopup()` trong `FirebaseAuthClient.ts` và sử dụng trong `AuthPage.tsx` khi chạy trên trình duyệt web:
     - Cho phép nhận phản hồi xác thực trực tiếp qua `postMessage` trong popup mà không bị chặn bởi phân vùng bộ nhớ iframe cross-origin.
  3. Cơ chế phát hiện xung đột & bảo vệ tài khoản (FR-001, FR-002, FR-022):
     - Kiểm tra nếu user trả về đã tồn tại provider `password`: Hệ thống nhận diện xung đột phương thức đăng nhập, lập tức gọi `firebaseAuthClient.signOut()`, hủy hoàn toàn phiên và kích hoạt thông báo tiếng Việt:
       "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
     - Tuyệt đối không gọi hàm bootstrap backend `bootstrapOwnAccount()`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-05):
  - Kịch bản `E2E-001-008` được thực thi lại trên trình duyệt Chrome thực tế bằng `browsermcp`.
  - Kết quả: **`PASS 100%`**. Thông báo cảnh báo xuất hiện ngay lập tức trong vòng < 2 giây sau khi xác nhận tài khoản Google (đáp ứng xuất sắc tiêu chí <= 5 giây của người dùng).
  - Trạng thái phiên: `currentUser = null` (Guest), không phát sinh bất kỳ request trái phép nào.
  - Bằng chứng khắc phục:
    + Screenshot: ![Màn hình cảnh báo xung đột Google hợp lệ](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-008--google-collision.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0001 — CLOSED

- Ngày mở: 2026-11-05
- Ngày đóng: 2026-11-05
- Phát hiện bởi: QA Agent (Senior QA & System Verification Engineer)
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-006](../ppswbs-spec/features/001-account-management/test-results/E2E-001-006--admin-accounts-rbac-guard.md), [E2E-001-007](../ppswbs-spec/features/001-account-management/test-results/E2E-001-007--admin-role-catalogue-rbac-guard.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Thiếu Route Guard phân quyền vai trò (Role-based Route Guard) ở tầng Frontend. Tài khoản người dùng vai trò `MEMBER` khi truy cập trực tiếp các đường dẫn quản trị nội bộ (`/admin/accounts` và `/admin/roles`) vẫn render toàn bộ giao diện quản trị Admin (Header, Admin Navigation Tabs, Nút tạo tài khoản nội bộ, Form bộ lọc và cấu trúc bảng), thay vì phải bị chặn hoàn toàn hoặc chuyển hướng về trang chủ (`/`) hoặc màn hình 403 Unauthorized.
- Các bước tái hiện:
  1. Đăng nhập hệ thống với tài khoản Member: `minikie456@gmail.com` / `123123123`.
  2. Truy cập trực tiếp đường dẫn: `http://localhost:5173/admin/accounts` hoặc `http://localhost:5173/admin/roles`.
  3. Quan sát giao diện hiển thị trên trình duyệt.
- Kết quả mong đợi: Hệ thống phải kiểm tra vai trò người dùng trước khi render trang. Nếu vai trò không phải `ADMIN`, Frontend phải chặn không hiển thị giao diện quản trị, thực hiện điều hướng an toàn về Trang chủ (`/`) hoặc hiển thị màn hình từ chối quyền truy cập (403 Unauthorized / Access Denied).
- Bằng chứng lỗi ban đầu:
  - Screenshot 1: ![Lộ giao diện Admin Accounts](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-006--admin-accounts-guard.jpg)
  - Screenshot 2: ![Lộ giao diện Admin Roles](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-007--admin-roles-guard.jpg)
- Biện pháp khắc phục (Frontend Dev):
  1. Khởi tạo component `AdminRouteGuard` (`src/features/accountmanagement/components/AdminRouteGuard.tsx`) tích hợp `useCurrentAccount()`:
     - Nếu chưa đăng nhập: Điều hướng về `/login`.
     - Nếu vai trò không phải `ADMIN`: Chặn hoàn toàn không render cây DOM con của Admin, hiển thị màn hình chuẩn 403 Forbidden kèm nút `← Quay lại` về trang chủ và thông báo `account-alert-error`.
     - Nếu vai trò là `ADMIN`: Render bình thường qua `<Outlet />`.
  2. Bọc toàn bộ các route quản trị Admin (`/admin/accounts`, `/admin/accounts/new`, `/admin/accounts/:accountId`, `/admin/roles`) dưới `AdminRouteGuard` trong `routes.tsx`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-05):
  - Kịch bản `E2E-001-006` và `E2E-001-007` được chạy lại trên Chrome thực tế bằng `browsermcp`.
  - Kết quả: **`PASS 100%`**. Toàn bộ giao diện Admin bị chặn hoàn toàn không render, hiển thị màn hình 403 Forbidden với nút "Về trang chủ".
  - Bằng chứng khắc phục:
    + Screenshot 1: ![Màn hình 403 chặn Admin Accounts](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-006--admin-accounts-guard-resolved.jpg)
    + Screenshot 2: ![Màn hình 403 chặn Admin Roles](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-007--admin-roles-guard-resolved.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0021 — CLOSED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent
- Feature: 003.2-workshop-operations
- Kịch bản liên quan: ST_WO_05 (check-in trong khung giờ, không check-in 2 lần)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Check-in lại một vé đã check-in, màn hình điều khiển ca vẫn báo "Đã ghi nhận check-in." giống lần đầu; Staff không biết đây là lần quét lặp.
- Các bước tái hiện:
  1. Manager mở `/operations/sessions/bc2ff85a-d92f-11f1-81a4-98bd80539bba` (giờ mở check-in đã đến).
  2. Nhập mã vé `GRP-960A60B3C5EF45148D37-L0-T1`, bấm "Check-in".
  3. Nhập lại đúng mã đó, bấm "Check-in".
- Kết quả mong đợi: báo vé đã check-in kèm giờ check-in (spec 003.2, "Staff scans an already checked-in ticket").
- Thực tế quan sát: hai request đều 200, thông báo "Đã ghi nhận check-in."; danh sách vẫn 1/8 và giờ 00:10 (backend không ghi check-in lần hai, trả `ALREADY_CHECKED_IN` theo `CheckInService`; `OperationsSessionPage.jsx` không phân biệt).
- Ngày đóng: 2026-11-26
- Sửa tại: `OperationsSessionPage.jsx` phân biệt `ALREADY_CHECKED_IN` và báo "Vé đã check-in trước đó lúc …" (commit `c2c8746c`, đã merge vào develop).
- Kiểm lại: `npm test -- src/workshop-operations` 155/155 Passed; quét lại vé `GRP-960A60B3C5EF45148D37-L0-T1` trên trình duyệt hiện "Vé đã check-in trước đó lúc 00:10 26 thg 11, 2026."

## BUG-E2E-0022 — CLOSED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: đổi phiên làm tăng giá (nhóm ST_TC; spec FR-045, FR-005)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend 003.3
- Mô tả lỗi: Sau khi đổi vé làm tăng giá, folio và thẻ vé hiện "Tổng" không đổi và "Đã thanh toán" bị giảm đúng bằng mức tăng. Số còn nợ đúng.
- Các bước tái hiện:
  1. Member đặt 2 vé Brass tại Hoàn Kiếm với voucher "Workshop Hoàn Kiếm – giảm 50.000 ₫", cọc 400.000 ₫ (nhóm `GRP-9FBC6B68D4CF4244B1CE`).
  2. Đổi vé T1 sang Tây Hồ 29/11 13:00, đồng ý mức tăng 25.000 ₫.
  3. Mở `/workshop-tickets/GRP-9FBC6B68D4CF4244B1CE-L0-T1/folio`.
- Kết quả mong đợi: đã thanh toán 200.000 ₫ (tổng các khoản đã xác minh), tổng gồm cả mức tăng 675.000 ₫, còn nợ 475.000 ₫.
- Thực tế quan sát: tổng 650.000 ₫, đã thanh toán 175.000 ₫, còn nợ 475.000 ₫; dòng "Thanh toán lúc đặt chỗ" vẫn 200.000 ₫.
- Nguyên nhân (đọc code): `TicketChangeService.recordAppliedChange` chỉ cộng mức tăng vào `remainingAmountVnd`; `TicketFolioApiService.toDto` (dòng 516–519) tính `totalAmount` không gồm mức tăng rồi suy `paidAmount = totalAmount − balanceDue`.
- Ngày đóng: 2026-11-26
- Sửa tại: `SettlementFinalBill.ticketPriceVnd()` (giá đóng băng + mức tăng đã xác nhận), dùng ở `TicketFolioApiService`, `TicketBalanceAdapter`, `AbsorbedUnderchargeService` (commit `58493f21`, đã merge vào develop). Cách hiểu: mức tăng đã xác nhận (FR-045) là khoản được duyệt trong `totalAmount` của FR-005.
- Kiểm lại: folio `…9FBC…-L0-T1` hiện Tổng 675.000, đã thanh toán 200.000, còn nợ 475.000; thẻ vé hiện đã thanh toán 200.000 ("Giá vé" 650.000 là giá lúc đặt). Test `workshopsettlement`: 576 ca, 5 ca lỗi về quyền yêu cầu hoàn tiền (`RefundRequestControllerTest`, `ManagerHomeWaitingCountsIT`, `WorkshopSettlementStatusContractIT`), không thuộc code vừa sửa.

## BUG-E2E-0024 — CLOSED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: [JOURNEY-PHASE-2 P2-S05c / P2-S05d nhánh 2](master-e2e/JOURNEY-PHASE-2--workshop-booking-and-fulfillment.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend 003.3 (hàng chờ yêu cầu đổi vé)
- Mô tả lỗi: Staff chi nhánh 1 chọn bộ lọc "Đã duyệt" hoặc "Đã từ chối" ở `/settlement/change-requests` thì bị đẩy về trang chủ với thông báo "Bạn không có quyền truy cập chức năng này."; Staff không xem được lịch sử yêu cầu đã quyết.
- Các bước tái hiện:
  1. Đăng nhập `staff.test@mland.vn` (chi nhánh 1).
  2. Mở `/settlement/change-requests`, đổi ô "Trạng thái" sang "Đã từ chối" (hoặc mở thẳng `?status=REJECTED` / `?status=APPROVED`).
- Kết quả mong đợi: bảng hiện các yêu cầu đã quyết thuộc chi nhánh của Staff, không còn nút Duyệt/Từ chối (P2-S05d nhánh 2).
- Thực tế quan sát: `GET /api/v1/workshop-change-requests?status=REJECTED` trả 200, sau đó trang chuyển về `/` kèm thông báo không có quyền. Danh sách có yêu cầu của chi nhánh 1 mà buổi đích ở chi nhánh 2 (`CHG-REQ-1cd04f59…` đích `7095a16d…`, `CHG-REQ-c91c7a90…` đích `70959ef0…`); trang gọi `GET /api/v1/workshop-sessions/{id}` cho buổi đó và nhận `403 LOCATION_SCOPE_DENIED`, rồi xử lý như cấm vào cả trang.
- Bằng chứng:
  - Screenshot: ![Bị đẩy về trang chủ](master-e2e/screenshots/P2-C6-03--loc-da-tu-choi-bi-day-ve-trang-chu.jpg)
  - Network: list 200; `workshop-sessions/7095a16d-e1ad-11f1-989a-98bd80539bba` 403 `location-scope-denied`.
- Sửa tại: `ChangeRequestsPage.jsx` đọc buổi bằng `getSession(reference, { suppressAccessDenied: true })` (`operationsApi.getSession` nhận thêm tùy chọn); 403 khi tra buổi chi nhánh khác chỉ làm ô buổi hiện mã rút gọn, không đẩy về trang chủ.
- Kiểm lại: test mới `operationsScreens.test.jsx` › "keeps Staff on the queue when a session of another branch answers 403" đạt, trượt khi bỏ bản sửa; `npx vitest run src/workshops src/workshop-settlement src/workshop-operations src/shared` 492/492 đạt (commit `5e3aa9c8`). Trình duyệt, Staff `staff.test@mland.vn`: `?status=REJECTED` hiện 3 yêu cầu, `?status=APPROVED` hiện 7 yêu cầu, ở lại trang, không có nút Duyệt/Từ chối; ô buổi chi nhánh khác hiện "Ca 7095a16d…". Ảnh: ![Đã từ chối](master-e2e/screenshots/P2-C6-04--sau-sua-0024-staff-loc-da-tu-choi.jpg), ![Đã duyệt](master-e2e/screenshots/P2-C6-05--sau-sua-0024-staff-loc-da-duyet.jpg)
- Ngày đóng: 2026-11-26

## BUG-E2E-0025 — CLOSED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.2-workshop-booking
- Kịch bản liên quan: [JOURNEY-PHASE-2 P2-S01(b)](master-e2e/JOURNEY-PHASE-2--workshop-booking-and-fulfillment.md)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.2 (trang chi tiết gói)
- Mô tả lỗi: Ở khung nhìn rộng 580px, khối ảnh gói (`.ring-visual`, cha `position: sticky`) đè lên danh sách ca; bấm vào thẻ ca không chọn được (đếm vẫn "đã chọn 0/1").
- Các bước tái hiện:
  1. Mở `/workshops/146?focus=schedule&locationId=1` ở cửa sổ rộng khoảng 580px.
  2. Cuộn tới "Chọn 1 ca workshop", bấm một thẻ ca.
- Kết quả mong đợi: thẻ ca được chọn; ảnh không che danh sách.
- Thực tế quan sát: `document.elementFromPoint` tại giữa thẻ ca trả `DIV.ring-visual gold`; ở 1280px thì chọn được bình thường.
- Bằng chứng:
  - Screenshot: ![Ảnh gói che danh sách ca](master-e2e/screenshots/P2-W1-00--viewport-580-anh-goi-che-danh-sach-ca.jpg)
- Sửa tại: `WorkshopDetailPage.jsx` cột trái dùng lớp `.workshop-detail-aside` (`figmaPublicShell.css`): sticky khi hai cột, `position: static` khi `max-width: 600px` (lúc `.detail-grid` thành một cột).
- Kiểm lại: trên trình duyệt ở bề rộng 280px, cột trái `static`, điểm giữa thẻ ca trả đúng nút ca; ở 1280px vẫn `sticky` (commit `5e3aa9c8`). Ảnh: ![Sau sửa](master-e2e/screenshots/P2-W1-04--sau-sua-0025-danh-sach-ca-khong-bi-che.jpg)
- Ngày đóng: 2026-11-26

## BUG-E2E-0026 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: [JOURNEY-PHASE-2 P2-S05c nhánh 2](master-e2e/JOURNEY-PHASE-2--workshop-booking-and-fulfillment.md)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.3 (hàng chờ yêu cầu đổi vé)
- Mô tả lỗi: Hộp thoại "Từ chối thay đổi này" vẫn ghi "Duyệt sẽ áp dụng thay đổi ngay; máy chủ vẫn kiểm tra sức chứa và hạn mức.", là câu của hộp thoại Duyệt.
- Các bước tái hiện:
  1. Staff mở `/settlement/change-requests`, bấm "Từ chối" ở một yêu cầu đang chờ.
- Kết quả mong đợi: câu mô tả nói rõ từ chối giữ nguyên lịch cũ của vé.
- Thực tế quan sát: câu mô tả giống hộp thoại Duyệt.
- Bằng chứng:
  - Screenshot: ![Hộp thoại từ chối](master-e2e/screenshots/P2-C6-01--hop-thoai-tu-choi.jpg)
- Sửa tại: thêm khóa `cr.rejectBody` (vi: "Vé {code}. Từ chối sẽ giữ nguyên lịch hiện tại của vé.", en tương ứng) trong `messages.ticket.js`; hộp thoại dùng khóa này khi từ chối.
- Kiểm lại: test mới "describes rejecting, not approving, in the reject dialog" đạt; `i18n.test.jsx` đạt (commit `5e3aa9c8`). Chưa xem hộp thoại trên trình duyệt vì hàng chờ không còn yêu cầu PENDING.

## BUG-E2E-0027 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR (spec chưa nói rõ khách phải thấy dòng add-on; cần owner 003.3 xác nhận)
- Phân loại / Gán cho: Frontend / Spec 003.3
- Mô tả lỗi: Folio phía Member không liệt kê dòng add-on, chỉ hiện tổng và các khoản thu. Chi tiết vé có hiện tổng "Add-on đã xác nhận 50.000 ₫" nhưng không có tên add-on.
- Các bước tái hiện:
  1. Staff thêm add-on 50.000 ₫ vào vé `GRP-DDCC15E83B9145F88651-L0-T1`, thu đủ.
  2. Member mở `/workshop-tickets/GRP-DDCC15E83B9145F88651-L0-T1/folio`.
- Kết quả mong đợi: Khách thấy từng khoản phụ thu (tên, số tiền) tạo nên tổng.
- Thực tế quan sát: Tổng 760.000 ₫ nhưng không có dòng "Bộ kit khăn lau..." 50.000 ₫; phía Staff thì có.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chưa sửa: cần owner 003.3 chốt folio khách có hiện dòng add-on (đổi dữ liệu API).
- Sửa tại: Folio khách hiện các khoản thêm chưa huỷ (tên, số tiền). Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.

## BUG-E2E-0028 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.1-workshop-booking / 006
- Kịch bản liên quan: S1, S0 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.1 / 006
- Mô tả lỗi: Giá tham khảo ở trang gói khác giá báo khi đặt.
- Các bước tái hiện:
  1. Mở gói Silver 925 (900.000 ₫).
  2. Chọn ca, 2 vé, "Đặt ngay".
- Kết quả mong đợi: Trang gói nói rõ trần giảm tự động và điều kiện voucher, hoặc giá khớp báo giá.
- Thực tế quan sát: Member thấy 810.000 ₫/vé nhưng báo giá 825.000 ₫ (trần giảm 150.000 ₫ cho cả giỏ). Guest thấy 710.000 ₫ "sau voucher MLWS100" (voucher chỉ cho Member) nhưng báo giá 825.000 ₫.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chưa sửa: cần chốt cách trình bày trần giảm và giá voucher.
- Sửa tại: Trang gói ghi trần giảm tự động "cho cả đơn"; khách chưa đăng nhập thấy "Dành cho thành viên" cạnh giá sau voucher. Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.

## BUG-E2E-0029 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.1-workshop-booking
- Kịch bản liên quan: S0 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.1
- Mô tả lỗi: Thông báo tra cứu thất bại chưa dịch.
- Các bước tái hiện:
  1. Guest tra cứu một mã nhóm với email sai trên giao diện tiếng Việt.
- Kết quả mong đợi: Thông báo tiếng Việt.
- Thực tế quan sát: Hiện "Chưa đọc được nhóm đặt chỗ" kèm "Booking not found" (API 404).
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Trang kết quả thanh toán dùng câu `lookup.notFound` cho lỗi 404. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. Chưa deploy.

## BUG-E2E-0030 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.1-workshop-booking
- Kịch bản liên quan: S1 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Banner "giao dịch chờ thanh toán" làm trang bị cuộn ngang ở khung hẹp.
- Các bước tái hiện:
  1. Cửa sổ rộng khoảng 670px, giữ chỗ 2 vé.
- Kết quả mong đợi: Banner nằm trong khung nhìn.
- Thực tế quan sát: Banner tràn phải, xuất hiện thanh cuộn ngang.
- Bằng chứng:
  - Screenshot: ![S1-0030--banner-cho-thanh-toan-tran-ngang.jpg](master-e2e/screenshots/S1-0030--banner-cho-thanh-toan-tran-ngang.jpg)
- Sửa tại: `.main-topbar-wrapper` xếp dọc để banner nằm dưới header. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt; chưa xem lại banner trên trình duyệt. Chưa deploy.

## BUG-E2E-0031 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 001-account-management
- Kịch bản liên quan: S0 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 001
- Mô tả lỗi: Dòng "Chưa có tài khoản? Đăng ký ngay" ở trang đăng nhập khó đọc.
- Các bước tái hiện:
  1. Mở `/login`.
- Kết quả mong đợi: Đủ tương phản (WCAG AA).
- Thực tế quan sát: Chữ liên kết xanh trên nền xanh đậm.
- Bằng chứng:
  - Screenshot: ![S0-0031--dang-ky-ngay-tuong-phan-thap.jpg](master-e2e/screenshots/S0-0031--dang-ky-ngay-tuong-phan-thap.jpg)
- Sửa tại: Chân form đăng nhập đổi từ `<footer>` sang `<div>` (style `footer` toàn cục tô nền xanh đậm). Đã xem lại trên trình duyệt local. File thuộc 001, cần báo owner. Chưa deploy.

## BUG-E2E-0032 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.1-workshop-booking
- Kịch bản liên quan: S2 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.1
- Mô tả lỗi: Ô mã đặt chỗ gợi ý sai định dạng.
- Các bước tái hiện:
  1. Guest mở `/workshop-booking/lookup`.
- Kết quả mong đợi: Gợi ý theo dạng `GRP-…`.
- Thực tế quan sát: Placeholder `e.g. WG-20261113-ABCD`.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Placeholder đổi thành `GRP-…`. Đã xem lại trên trình duyệt local. Chưa deploy.

## BUG-E2E-0033 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S2 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.3
- Mô tả lỗi: Trang folio của khách không có nút quay lại và hiện ô "Mã điều chỉnh" không có hướng dẫn.
- Các bước tái hiện:
  1. Member mở "Xem hóa đơn thanh toán của vé".
- Kết quả mong đợi: Có đường quay lại; ô điều chỉnh có giải thích hoặc chỉ hiện khi cần.
- Thực tế quan sát: Không có nút quay lại; ô "Mã điều chỉnh" đứng trơ.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Thêm liên kết quay lại ở folio Member và câu giải thích ô mã điều chỉnh. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. Chưa deploy.

## BUG-E2E-0034 — CLOSED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.1-workshop-booking
- Kịch bản liên quan: S2 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.1
- Mô tả lỗi: Ô "Email người nhận vé" không có tên truy cập (nhãn không gắn với input).
- Các bước tái hiện:
  1. Mở chi tiết vé, bấm "Gửi vé cho người đi cùng".
- Kết quả mong đợi: Input có accessible name.
- Thực tế quan sát: Cây truy cập ghi textbox không tên.
- Bằng chứng:
  - Screenshot: ![S2-0034--o-email-nguoi-nhan.jpg](master-e2e/screenshots/S2-0034--o-email-nguoi-nhan.jpg)
- Ghi chú xử lý: Không phải lỗi theo code: các input nằm trong `<label>` nên có accessible name; cây truy cập của công cụ test không hiện tên.

## BUG-E2E-0035 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.2-workshop-operations
- Kịch bản liên quan: S3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.2
- Mô tả lỗi: Bảng điều phối ca hiện hai chuỗi UUID ở cột "Ca".
- Các bước tái hiện:
  1. Staff mở `/operations/sessions`.
- Kết quả mong đợi: Hiện tên khung giờ / gói dễ đọc.
- Thực tế quan sát: Mỗi dòng hiện hai UUID.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Sửa một phần (2026-11-26): cột "Ca" chỉ còn 8 ký tự đầu của mã, mã đầy đủ ở tooltip. Hiện tên khung giờ cần API trả nhãn.
- Sửa tại: cột "Ca" hiện nhãn khung giờ đọc từ `GET /workshop-locations/{id}/slots`; không đọc được thì hiện mã rút gọn. Có test trong `sessionBoardLocations.test.jsx`. Ngày 2026-11-26, chưa deploy; chưa xem trên trình duyệt với phiên Staff.

## BUG-E2E-0036 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 014-navigation-shell / 003.2
- Kịch bản liên quan: S3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 014
- Mô tả lỗi: Việc "chưa chốt điểm danh" ở trang chủ Staff không ghi ngày.
- Các bước tái hiện:
  1. Staff mở `/staff/home`.
- Kết quả mong đợi: Mỗi việc ghi ngày và giờ.
- Thực tế quan sát: Chỉ có giờ, nên hai dòng "Buổi 09:30" trông như trùng.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Việc của ca không phải hôm nay ghi thêm ngày; có test `staffWork.test.js`. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. File thuộc 014/013, cần báo owner. Chưa deploy.

## BUG-E2E-0037 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend / Backend 003.3
- Mô tả lỗi: Thông báo `SESSION_NOT_STARTED` lộ trạng thái thô "IN PROGRESS".
- Các bước tái hiện:
  1. Staff thêm add-on cho vé của ca chưa bắt đầu.
- Kết quả mong đợi: Dùng nhãn "Đang diễn ra".
- Thực tế quan sát: Thông báo ghi "chuyển ca sang IN PROGRESS".
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Thông báo dùng "Đang diễn ra" / "In progress". Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. Chưa deploy.

## BUG-E2E-0038 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.3
- Mô tả lỗi: Nút "Chốt folio" vẫn bật trên folio đã chốt.
- Các bước tái hiện:
  1. Thu đủ số còn nợ để folio chuyển "Đã chốt".
- Kết quả mong đợi: Nút ẩn hoặc vô hiệu.
- Thực tế quan sát: `disabled = false` (không bấm thử).
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Nút "Chốt folio" bị vô hiệu khi folio FINALIZED hoặc CLOSED. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. Chưa deploy.

## BUG-E2E-0039 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.3
- Mô tả lỗi: Bảng dòng add-on phía Staff hiện trạng thái chưa dịch và mã nội bộ, cột bị bóp ở khung hẹp.
- Các bước tái hiện:
  1. Staff thêm add-on, xem bảng "Dòng / Mô tả" ở khung khoảng 670px.
- Kết quả mong đợi: Trạng thái "Đã xác nhận", cột đọc được.
- Thực tế quan sát: `CONFIRMED`, `SETTLE-CHARGE-…`, mô tả xuống dòng từng từ.
- Bằng chứng:
  - Screenshot: ![S3-0039--bang-addon-confirmed.jpg](master-e2e/screenshots/S3-0039--bang-addon-confirmed.jpg)
- Sửa tại: Thêm nhãn `lineStatus.CONFIRMED`, bảng có bề rộng tối thiểu và cuộn ngang. Mã dòng vẫn hiện dạng rút gọn. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt; chưa xem lại trên trình duyệt. Chưa deploy.

## BUG-E2E-0040 — CLOSED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.1-workshop-booking / 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S2 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: SPEC-GAP (chưa kết luận là lỗi)
- Phân loại / Gán cho: Spec 003
- Mô tả lỗi: Chưa rõ Guest có được quản lý đơn đặt bằng tài khoản Member chỉ bằng mã nhóm và một thông tin liên hệ hay không.
- Các bước tái hiện:
  1. Chưa đăng nhập, tra cứu đơn của Member bằng mã nhóm và đúng email.
- Kết quả mong đợi: Spec nói rõ quyền xem QR, đổi lịch, hủy vé, gửi vé của Guest với đơn thuộc Member.
- Thực tế quan sát: Thấy QR của cả 2 vé và các nút "Đổi lịch", "Hủy vé", "Gửi vé cho người đi cùng" (không bấm). FR-048/FR-049 và 003.3 cho Guest chứng minh bằng liên hệ nhưng không nói riêng đơn Member.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chờ quyết định của owner 003.
- Quyết định: Giữ nguyên theo quyết định 2026-11-26: Guest chỉ huỷ/đổi vé khi có mã xác minh người mua. Đã ghi vào spec 003.3.

## BUG-E2E-0041 — CLOSED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.2-workshop-operations
- Kịch bản liên quan: S3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: SPEC-GAP (chưa đối chiếu hết spec)
- Phân loại / Gán cho: Spec 003.2
- Mô tả lỗi: Cần xác nhận hạn cuối của check-in và việc ca quá giờ vẫn "Đang diễn ra".
- Các bước tái hiện:
  1. Ngày 26/11, 18:45, Staff check-in vé `GRP-DDCC15E83B9145F88651-L0-T1` của ca 25/11 09:30–12:00 chưa chốt điểm danh.
- Kết quả mong đợi: Spec nói rõ check-in còn hợp lệ đến khi nào.
- Thực tế quan sát: Check-in được chấp nhận (HTTP 200); ca vẫn "Đang diễn ra".
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chờ quyết định của owner 003.2.
- Quyết định: Giữ nguyên theo spec 003.2: chốt điểm danh thủ công, giờ kết thúc chỉ để tham khảo. Đã ghi vào spec 003.3.

## BUG-E2E-0042 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S3, S4 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: SPEC-GAP (chưa tìm thấy spec bắt buộc; cần owner 003.3 xác nhận)
- Phân loại / Gán cho: Spec / Backend 003.3
- Mô tả lỗi: Thêm add-on vào folio không có dòng nào trên màn "Nhật ký audit".
- Các bước tái hiện:
  1. Staff thêm add-on 50.000 ₫ vào vé `GRP-DDCC15E83B9145F88651-L0-T1` (khoảng 18:46 ngày 26/11).
  2. Manager mở `/operations/audit`, xem cả tab "Vận hành" và "Quyết toán".
- Kết quả mong đợi: Thao tác làm tăng tiền khách phải trả có dòng audit ghi người thực hiện và thời điểm, hoặc spec nói rõ không cần.
- Thực tế quan sát: Có `FOLIO_CASH_COLLECTED` và `FOLIO_FINALIZED` lúc 18:46:27, không có dòng thêm add-on. Tìm trong `workshopsettlement/service` cũng không thấy mã hành động audit cho việc thêm add-on.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chờ quyết định của owner 003.3.
- Sửa tại: Mỗi khoản add-on/phụ phí ghi audit `CHARGE_ADDED`. Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.

## BUG-E2E-0043 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes / 003.2 audit review
- Kịch bản liên quan: S4 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Backend 003.3
- Mô tả lỗi: Tab "Quyết toán" hiện người thực hiện là số trần ở các dòng đổi lịch.
- Các bước tái hiện:
  1. Manager mở `/operations/audit?tab=settlement`.
  2. Xem các dòng `TICKET_CHANGE_*` ngày 25/11 19:24–19:25.
- Kết quả mong đợi: FR-052: hiển thị người thực hiện như FR-051 (id tài khoản kèm tên).
- Thực tế quan sát: Hiện `2`, `8`; các dòng `FOLIO_CASH_COLLECTED` thì hiện đủ tên kèm id.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Đổi lịch, duyệt yêu cầu đổi và điều chỉnh folio ghi `account:<id>`; 151 test backend liên quan đạt ngày 2026-11-26. Các dòng audit cũ vẫn là số trần. Chưa deploy.

## BUG-E2E-0044 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.2-workshop-operations
- Kịch bản liên quan: S4 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR (chưa đối chiếu spec)
- Phân loại / Gán cho: Frontend / Spec 003.2
- Mô tả lỗi: "Chốt điểm danh" thực hiện ngay sau một lần bấm, không hỏi xác nhận.
- Các bước tái hiện:
  1. Staff mở một ca "Đang diễn ra", bấm "Chốt điểm danh".
- Kết quả mong đợi: Hộp xác nhận nêu số vé đã và chưa check-in trước khi chốt.
- Thực tế quan sát: Ca chuyển ngay sang "Đã chốt điểm danh"; các nút đổi trạng thái bị khoá.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chờ quyết định: có thêm hộp xác nhận không, nội dung gì.
- Sửa tại: Thêm hộp xác nhận trước khi chốt điểm danh. Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.

## BUG-E2E-0045 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.1-workshop-booking / 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S6, S5 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend 003.1 / 003.3 (chưa xác định tầng)
- Mô tả lỗi: Vé đã đổi lịch rồi huỷ hiển thị lại buổi cũ.
- Các bước tái hiện:
  1. Member đổi vé `GRP-56841B2845374F45B043-L0-T1` từ 28/11 09:30–12:00 sang 28/11 13:00–16:00; trang chi tiết hiện buổi mới.
  2. Huỷ chính vé đó.
  3. Xem chi tiết nhóm đặt chỗ.
- Kết quả mong đợi: Vé đã huỷ hiện buổi tại thời điểm huỷ: 28/11 13:00–16:00, Mland Hà Nội – Hoàn Kiếm.
- Thực tế quan sát: Hiện "Buổi 1: Thứ 7, 28/11/2026 (09:30)", không có giờ kết thúc và chi nhánh.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chưa sửa: vé đã huỷ không còn entitlement nên 003.1 lấy buổi lúc đặt; cần 003.2 cung cấp buổi đã nhả qua facade (đổi contract, cần chốt).
- Sửa tại: Thêm `getCancelledTicketSessions` ở 003.2; 003.1 hiện đúng buổi của vé đã huỷ. Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.

## BUG-E2E-0046 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S6, S5 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend / Spec 003.3
- Mô tả lỗi: Vé đã huỷ và folio đã đóng vẫn hiện số còn phải trả.
- Các bước tái hiện:
  1. Member huỷ vé `GRP-56841B2845374F45B043-L0-T1` (đã trả 200.000 ₫ trên 825.000 ₫).
  2. Xem chi tiết vé và trang folio của vé.
- Kết quả mong đợi: Không trình bày như khoản còn nợ: ẩn, hiện 0, hoặc ghi rõ không phải thu.
- Thực tế quan sát: Chi tiết vé "Còn phải trả 625.000 ₫"; folio "Đã đóng" nhưng "Số còn nợ 625.000 ₫".
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Ghi chú xử lý: Chờ quyết định: ẩn, hiện 0 hay ghi "không phải thu".
- Sửa tại: Vé đã huỷ và folio `CLOSED` hiện "Không phải thu". Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.

## BUG-E2E-0047 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.2-workshop-operations
- Kịch bản liên quan: N4 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend / Backend 003.2
- Mô tả lỗi: Thông báo từ chối check-in vé đã huỷ chưa dịch.
- Các bước tái hiện:
  1. Manager mở ca `/operations/sessions/c8742ce2-e69e-11f1-b811-c2df278ecca3` (25/11 13:00).
  2. Nhập mã vé đã huỷ `GRP-56841B2845374F45B043-L0-T1`, bấm "Check-in".
- Kết quả mong đợi: Thông báo tiếng Việt.
- Thực tế quan sát: HTTP 409, giao diện hiện "Ticket is CANCELLED, check-in rejected".
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Frontend dịch mã `TICKET_CONFLICT` (một câu chung cho các lý do cùng mã). Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. Chưa deploy.

## BUG-E2E-0048 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: N3 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.3
- Mô tả lỗi: Member mở folio của vé không thuộc mình bị đưa về trang chủ mà không có thông báo.
- Các bước tái hiện:
  1. Member đăng nhập, mở `/workshop-tickets/GRP-3046E8AD870F4A849DDA-L0-T1/folio`.
- Kết quả mong đợi: Trang báo không có quyền xem hoặc không tìm thấy vé; không lộ dữ liệu.
- Thực tế quan sát: API trả 403 (không lộ dữ liệu), trình duyệt chuyển về `/` không có thông báo.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang quan sát được.
- Sửa tại: Folio của Member đọc với `suppressAccessDenied`, nên 403 hiện câu "không tìm thấy vé" thay vì chuyển về trang chủ. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. Chưa deploy.

## BUG-E2E-0049 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S7 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: BLOCKER
- Phân loại / Gán cho: Backend 006 loyalty-promotion / 003.3
- Mô tả lỗi: Member chọn hoàn tiền cho vé bị Mland huỷ ca thì API trả 500; không mở được yêu cầu hoàn tiền.
- Các bước tái hiện:
  1. Member đặt và trả cọc 200.000 ₫ cho vé `GRP-12E056299860454A8137-L0-T1`, ca 10/12/2026 16:00–19:00 Hoàn Kiếm (vé duy nhất của ca).
  2. Manager "Yêu cầu hủy ca", lý do `INSTRUCTOR_UNAVAILABLE` (API 202, ca "Đang yêu cầu hủy").
  3. Member mở `/workshop-tickets/GRP-12E056299860454A8137-L0-T1/remedy`, chọn "Hoàn tiền vé này (100%)", "Gửi câu trả lời", "Xác nhận".
- Kết quả mong đợi: Ghi nhận câu trả lời, mở yêu cầu hoàn 200.000 ₫ trong hàng đợi hoàn tiền, gửi liên kết nhập tài khoản.
- Thực tế quan sát: `POST /api/v1/workshop-tickets/{ticketCode}/mland-remedy/answers` trả 500 cả 2 lần thử; giao diện hiện "An unexpected error occurred". Sau đó folio vẫn "Đang mở", hàng đợi hoàn tiền trống, ca vẫn "Đang yêu cầu hủy".
- Nguyên nhân (từ stack trace staging và đọc code, chưa tái hiện bằng test): `TicketMlandRemedyService.executeRemedyRefund` gọi `restoreVoucherForCancelledTicket(new RestoreVoucherCommand(ctx.groupCode(), ticketCode, …))`. `LoyaltyPartLifecycleService.ticketMlandOutcome` dùng `ticketCode` (`GRP-…-L0-T1`) làm `partRef`, trong khi phần quyền lợi được khoá theo mã `L0-T1` (`WorkshopBookingFacadeImpl` dựng `partRef = "L{line}-T{ordinal}"`). `find(parts, partRef)` ném `LoyaltyException` "This branch order is not part of the accepted checkout." nên cả giao dịch rollback và trả 500. `LoyaltyWorkshopFacadeIT` dùng `t-1` cho cả hai mã nên không bắt được; `TicketMlandRemedyServiceTest` mock `LoyaltyFacade`.
- Phạm vi suy ra từ code (chưa chạy): nhánh tự hoàn tiền khi hết hạn trả lời cũng đi qua `executeRemedyRefund`, nên khả năng lỗi tương tự với nhóm Member có quyền lợi loyalty.
- Hướng sửa đề xuất (owner 006 / 003.3 tự quyết): truyền `ctx` partRef vào lệnh, hoặc 006 đổi mã vé sang partRef; thêm test với mã vé thật dạng `GRP-…-L0-T1`.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang và request quan sát được.
  - Console / Log (backend staging 26/11): `19:31:57 … correlationId=bdcd0722-9fd3-4e62-ab66-8da99cd60436: This branch order is not part of the accepted checkout.` và `19:32:53 … correlationId=a843aec0-dbe4-41ea-8a59-c7b35d4dd642` (cùng lỗi), stack `LoyaltyPartLifecycleService.find:318 ← cancelTicket:225 ← cancelTicketByMland:207`. Mã hiển thị trên UI (`21d0a696-…`, `2425a362-…`) khác mã trong log vì `AccountExceptionHandler.resolveCorrelationId` sinh UUID mới mỗi lần gọi khi request không có `X-Correlation-Id`; khớp theo thời điểm.
- Sửa tại: thêm `partRef` vào `RestoreVoucherCommand` (006), 003.3 truyền `ctx.partRef()`, cập nhật contract facade (BE-DESIGN-0014, phương án A).
- Kiểm lại: test tái hiện `restoreVoucherFindsTheTicketByItsPartReferenceNotItsTicketCode` đỏ trước khi sửa (cùng thông báo như staging), sau sửa đạt; 95 test của các lớp liên quan và 18 test Modulith đạt ngày 2026-11-26. Chưa commit, chưa deploy, chưa chạy lại S7 trên staging.

## BUG-E2E-0050 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.3-workshop-settlement-and-changes
- Kịch bản liên quan: S7 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.3
- Mô tả lỗi: Trang chọn buổi thay thế là một danh sách phẳng rất dài, lựa chọn hoàn tiền nằm cuối cùng.
- Các bước tái hiện:
  1. Member mở trang `/remedy` của vé bị huỷ ca.
- Kết quả mong đợi: Lọc hoặc phân trang theo ngày/chi nhánh, ẩn ca không khả dụng, lựa chọn hoàn tiền dễ thấy.
- Thực tế quan sát: 252 radio (251 ca gồm cả ca "không khả dụng", cộng hoàn tiền ở cuối).
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang và request quan sát được.
- Ghi chú xử lý: Chờ quyết định thiết kế trang chọn buổi thay thế.
- Sửa tại: Hoàn tiền lên đầu; chỉ liệt kê ca chọn được; lọc theo chi nhánh và ngày. Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.

## BUG-E2E-0051 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.2-workshop-operations
- Kịch bản liên quan: S7 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend 003.2
- Mô tả lỗi: Danh sách lý do huỷ ca hiện mã hệ thống tiếng Anh.
- Các bước tái hiện:
  1. Manager mở một ca, mở "Xử lý ngoại lệ", xem "Chuyển giao hủy ca".
- Kết quả mong đợi: Chỉ hiện mô tả tiếng Việt.
- Thực tế quan sát: Mỗi mục có tiền tố như `INSTRUCTOR_UNAVAILABLE —`.
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang và request quan sát được.
- Sửa tại: Ba danh sách lý do (check-in, khôi phục, huỷ ca) chỉ hiện mô tả. Ngày 2026-11-26: toàn bộ test frontend 1396/1396 đạt, build đạt. Chưa deploy.

## BUG-E2E-0052 — FIXED

- Ngày: 2026-11-26
- Phát hiện bởi: QA Agent (Hồ Hoàng Quốc chạy)
- Feature: 003.2-workshop-operations
- Kịch bản liên quan: S7 (staging, kịch bản gợi ý trong phiên test; chưa có file E2E v2)
- Mức độ nghiêm trọng: MINOR (chưa đối chiếu spec)
- Phân loại / Gán cho: Frontend / Spec 003.2
- Mô tả lỗi: "Yêu cầu hủy ca" gửi ngay sau một lần bấm, không hỏi xác nhận.
- Các bước tái hiện:
  1. Manager điền lý do và ghi chú, bấm "Yêu cầu hủy ca".
- Kết quả mong đợi: Hộp xác nhận nêu số vé bị ảnh hưởng.
- Thực tế quan sát: Gửi ngay; ca chuyển "Đang yêu cầu hủy".
- Bằng chứng:
  - Screenshot: chưa lưu ảnh; bằng chứng là văn bản trang và request quan sát được.
- Ghi chú xử lý: Chờ quyết định cùng BUG-E2E-0044.
- Sửa tại: Thêm hộp xác nhận trước khi huỷ ca. Ngày 2026-11-26, chưa deploy; test và build ở mục ghi chú cuối đợt.
