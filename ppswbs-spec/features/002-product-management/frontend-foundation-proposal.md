# Đề xuất nền frontend dùng chung (T088/T089)

- **Trạng thái**: Đặng Thị Khương (owner 002) duyệt mục 1–8 làm cơ sở cho 002 ngày 2026-11-01; đã triển khai (T088/T089, xem [blocker-report](blocker-report.md) mục "Nền frontend và màn hình 002"). Còn chờ Hồ Hoàng Quốc (PM), Phan Sơn (mục 7) và Bùi Trọng Thịnh (mục 1).
- **Người soạn**: Đặng Thị Khương (owner 002), 2026-10-31.
- **Cần duyệt**: Hồ Hoàng Quốc (PM) cho toàn bộ; Phan Sơn (001) cho mục 7; Bùi Trọng Thịnh cho mục 1 (dùng lại prototype).
- **Phạm vi**: nền chung mà 002 T088/T089 phải cung cấp và các feature khác dùng lại (006 T077, 001 plan "frontend foundation supplies router, Firebase client, authenticated request client, and environment configuration"). Không gồm màn hình nghiệp vụ của bất kỳ feature nào.

## Vì sao cần

- Constitution III chỉ chốt "React + Vite, client-side rendering, REST". Version, cấu trúc, routing, state, i18n và test do plan quyết định (AGENT.md §4, gate F05–F09), nhưng chưa có plan frontend nào được duyệt.
- Plan 001 giả định đã có nền chung (router, Firebase client, request client có xác thực, cấu hình môi trường); chưa ai nhận làm.
- Prototype `e7657ff` (Thịnh) build được nhưng tự ghi là "presentation prototypes with local example data"; `ppswbs_frontend/ERROR-LOG.md` FE-DESIGN-0002 vẫn BLOCKED vì thiếu hợp đồng Firebase/session.
- Mọi task frontend của 002 (15 task) và 006 (9 task) đang chờ T088/T089.

## Hiện trạng prototype `e7657ff`

| Mục | Hiện có |
| --- | --- |
| Stack | React 19.3.1, React DOM 19.3.1, Vite 7.3.7 (khai báo `^`, lockfile có) |
| Cấu trúc | 109 màn hình trong một file `src/main.jsx`; `src/screenRegistry.js`; 6 file CSS |
| Routing | Tự viết bằng `window.location.hash` |
| API | `src/api/catalogue.js` (API công khai 002, đã nối thật), `src/api/retail.js` (route FE-08, chờ token) |
| Test | Không có |
| i18n | Không có, chuỗi tiếng Việt viết thẳng |
| Firebase | Không có |

## Các quyết định đề xuất

Mỗi mục có một **đề xuất** và phương án khác nếu nhóm không đồng ý. Ô "Duyệt" để người duyệt điền.

### 1. Nền và cấu trúc thư mục

**Đề xuất**: giữ prototype của Thịnh làm nền (không dựng bản thứ hai), tách lại như sau:

```
ppswbs_frontend/src/
  app/                 # khởi động, router, layout chung, provider (auth, i18n)
  shared/
    api/               # http client dùng chung (mục 8)
    auth/              # Firebase client + session (mục 7)
    i18n/              # từ điển vi/en (mục 5)
    ui/                # component dùng chung: button, field, trạng thái loading/empty/error
  features/
    productmanagement/{public,management,api}/
    loyaltypromotion/{public,member,management,api}/
    readyringsales/ ...   # mỗi feature một thư mục, owner feature tự làm bên trong
  styles/              # CSS hiện có của prototype
```

- Mỗi feature chỉ import từ `shared/` và `app/`, không import chéo vào thư mục feature khác.
- Màn hình mẫu của prototype được chuyển vào đúng thư mục feature khi owner feature đó nhận; trước đó giữ nguyên ở dạng prototype, ghi rõ là dữ liệu mẫu.

Phương án khác: dựng mới từ đầu (bỏ prototype). Không khuyến nghị vì mất phần catalogue đã nối thật và CSS.

Duyệt: ☐

### 2. Version và dependency

**Đề xuất**: ghim **đúng version** (bỏ `^`), nâng cấp chỉ qua task có duyệt. Node ≥ 20 LTS (máy dev hiện có Node 24).

| Gói | Version | Dùng cho |
| --- | --- | --- |
| `react`, `react-dom` | 19.3.1 (đang có) | UI |
| `vite`, `@vitejs/plugin-react` | 7.3.7 (đang có) / bản 5.x tương thích | build, dev server |
| `react-router` | 7.x (chốt bản cụ thể khi cài) | routing (mục 3) |
| `firebase` | 12.x (chốt bản cụ thể khi cài) | đăng nhập, ID token (mục 7) |
| `vitest`, `@testing-library/react`, `@testing-library/user-event`, `jsdom` | bản ổn định mới nhất lúc cài | test (mục 6) |

Không thêm thư viện state, form, UI kit hay i18n (mục 4, 5). Muốn thêm gói khác phải có duyệt.

Duyệt: ☐

### 3. Routing

**Đề xuất**: `react-router` 7, chế độ thư viện (`createBrowserRouter`), URL dạng `/products/123` thay cho `#/product-details`. Danh sách màn hình tiếp tục lấy từ `screenRegistry.js`.

- Khi deploy, máy chủ tĩnh phải trả `index.html` cho mọi đường dẫn không phải file (SPA fallback). Vite dev và `vite preview` đã tự làm việc này. Hosting chưa chốt (constitution không quyết định hosting) nên ghi điều kiện này vào ghi chú deploy.
- Route guard theo vai trò chỉ để điều hướng; backend vẫn quyết định quyền (constitution III).

Phương án khác: giữ hash URL (`createHashRouter`), không cần SPA fallback nhưng URL xấu và không chuẩn cho chia sẻ link.

Duyệt: ☐

### 4. Quản lý state

**Đề xuất**: **không dùng thư viện state**.

- Dữ liệu server: lấy qua `shared/api`, giữ trong state của màn hình (`useState`/`useReducer` + hook riêng của feature). **Không cache** dữ liệu nội bộ, giá, tồn kho, điểm, thanh toán giữa các màn hình (002 T089, AGENT.md §4); mở màn hình là tải lại.
- State dùng chung toàn app chỉ có: phiên đăng nhập (mục 7) và ngôn ngữ (mục 5), qua React Context.
- Không optimistic update; sau mỗi thao tác ghi, hiển thị theo dữ liệu server trả về.

Phương án khác: TanStack Query. Không khuyến nghị lúc này vì phải chốt thêm chính sách cache/invalidation cho từng loại dữ liệu.

Duyệt: ☐

### 5. Ngôn ngữ (vi/en)

**Đề xuất**: module từ điển nhỏ tự viết trong `shared/i18n/`, không thêm thư viện.

- Mỗi feature có file `vi.js`, `en.js` riêng; hàm `t('key', { biến })`; tiếng Việt là mặc định.
- Lựa chọn ngôn ngữ của người dùng lưu `localStorage` (không phải dữ liệu nhạy cảm).
- Thông báo lỗi: ưu tiên dịch theo `code` của backend; không có bản dịch thì hiện `message` an toàn của backend kèm `correlationId`.
- Định dạng tiền và ngày dùng `Intl` (`vi-VN`, `en-US`).

Phương án khác: `react-i18next`. Dùng khi nhóm cần số nhiều/định dạng phức tạp.

Duyệt: ☐

### 6. Test và lệnh chạy

**Đề xuất**:

| Lệnh | Việc |
| --- | --- |
| `npm run dev` | dev server Vite |
| `npm run build` | build production |
| `npm test` | `vitest run` (jsdom, Testing Library) |

- Test API client: giả lập `fetch`, kiểm tra header, Idempotency-Key, cách xử lý từng mã lỗi theo bảng lỗi trong `contracts/interfaces.md` của feature.
- Test màn hình: các trạng thái loading/empty/validation/success/error (constitution III, AGENT.md F04).
- Kiểm chứng tích hợp thật (gọi backend) ghi riêng trong `verification.md` của feature, không tính test giả lập là bằng chứng tích hợp.

Duyệt: ☐

### 7. Firebase và phiên đăng nhập (cần 001 – Sơn duyệt)

Plan 001 đã chốt: Firebase ID token gửi qua `Authorization: Bearer`; không có JWT riêng, không có refresh-token riêng, không bắt đăng nhập lại cố định. Phần frontend còn thiếu và **đề xuất** như sau:

| Câu hỏi | Đề xuất |
| --- | --- |
| Cấu hình Firebase | Biến môi trường `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_APP_ID` (cấu hình web công khai, không phải secret); `.env.example` liệt kê tên, không chứa giá trị thật |
| Lấy token | Mỗi request gọi `auth.currentUser.getIdToken()`; Firebase SDK tự làm mới token. Frontend **không tự lưu** token vào `localStorage`/`sessionStorage`/biến toàn cục, không đưa vào URL hay log |
| Giữ phiên | Persistence mặc định của Firebase (`browserLocalPersistence`) — **cần Sơn chốt** giữ hay chuyển `browserSessionPersistence` |
| Sau đăng nhập | Gọi API bootstrap của 001 để lấy Account (vai trò, trạng thái); lưu trong Auth Context, xóa khi đăng xuất |
| 401 | Thử lại **một lần** với `getIdToken(true)`; vẫn 401 thì đăng xuất Firebase, xóa Auth Context, chuyển tới màn đăng nhập kèm đường dẫn quay lại |
| 403 | Hiện thông báo không có quyền tại chỗ, không đăng xuất |
| Email chưa xác minh / tài khoản bị khóa | Theo `code` khôi phục mà 001 trả về — **cần Sơn cung cấp danh sách code** |
| Đăng xuất | Firebase `signOut()`, xóa Auth Context và mọi state của màn hình |

Duyệt (Sơn): ☐

### 8. HTTP client dùng chung (`shared/api`)

**Đề xuất**:

- `VITE_API_BASE_URL` (mặc định dev `http://localhost:8080`, như prototype).
- Gắn `Authorization: Bearer` cho route cần đăng nhập (mục 7); route công khai không gửi token.
- Lệnh ghi: sinh `Idempotency-Key` bằng `crypto.randomUUID()` **một lần cho mỗi hành động của người dùng**; bấm "Thử lại" dùng lại đúng key đó. Không tự động thử lại.
- Gửi `expectedVersion` theo hợp đồng của từng API.
- Đọc lỗi theo `common/ErrorResponse` của backend: `code`, `message`, `correlationId`, `timestamp`, `fieldErrors[{field, message}]`; trả về một lớp lỗi có kiểu để màn hình gắn lỗi vào đúng ô nhập và giữ nguyên dữ liệu đã nhập.
- 409 `STALE_VERSION`: báo dữ liệu đã thay đổi, tải bản mới nhất, không tự ghi đè.
- 503 / lỗi mạng / timeout: hiện nút "Thử lại" (cùng key).
- Không chặn nút gửi bằng debounce; chỉ khóa nút trong lúc request đang chạy.
- Mỗi feature viết client của mình trong `features/<feature>/api/` trên nền `shared/api`.

Duyệt: ☐

### 9. CORS (phụ thuộc, không phải quyết định của đề xuất này)

Request có `Authorization` từ trình duyệt phải qua preflight. Trên develop (31/10) preflight của mọi route không phải workshop bị `workshopOperationsCorsFilter` trả 403 (BE-DESIGN-0012, đã báo Quốc). Nền frontend làm xong vẫn không gọi được API cần đăng nhập cho tới khi lỗi này được sửa. Không thêm proxy Vite để né lỗi này (AGENT.md §4).

## Thứ tự thực hiện sau khi duyệt

1. **T088 (002)**: ghi các quyết định đã duyệt vào `blocker-report.md`; tách cấu trúc thư mục, ghim version, thêm router, i18n, Vitest; `npm run build` và `npm test` chạy được.
2. **T089 (002)**: `shared/api` + `shared/auth` + client của 002, kèm test theo bảng lỗi của 002.
3. Báo các feature khác dùng nền chung: 006 T077, 001, 008 (FE-DESIGN-0002), các feature còn lại.

Mỗi bước dừng và ghi blocker nếu gặp chỗ chưa được duyệt, không tự chọn thay.

## Cần trả lời để bắt đầu

1. Quốc: duyệt mục 1–6 và 8 (hoặc chọn phương án khác).
2. Sơn: duyệt mục 7, cho biết persistence và danh sách code khôi phục của 001, route bootstrap.
3. Thịnh: đồng ý tách prototype theo mục 1.
4. Quốc: sửa CORS (mục 9).
