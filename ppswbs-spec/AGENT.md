# AGENTS.md — Project Context for AI Agents
# Version: 1.0 | Updated: 2026-09-19 | Project: Personalized Product Sales and Workshop Booking System

## 1. PROJECT OVERVIEW
Name: Personalized Product Sales and Workshop Booking System (PPSWBS)
Type: Server-rendered web application + REST API — modular monolith theo feature/domain
Domain: Workshop trang sức và bán sản phẩm cá nhân hóa; ưu tiên tài liệu đã phê duyệt trong `../documents/docs/`, giữ nguyên mọi `TBD`
Stage: Planning & Requirements Specification; V1 frontend dùng Spring Boot + Thymeleaf + htmx, còn `../frontend/` chỉ là UX prototype offline

## 2. TECH STACK (STRICT — do not deviate)
Backend: Java 21 + Spring Boot
Frontend: Thymeleaf server-side templates + htmx progressive enhancement; HTML form/link cốt lõi phải hoạt động khi htmx hoặc JavaScript không tải được
Database: MySQL (version sẽ được chốt trong feature plan)
ORM: Spring Data JPA / JPA; không dùng raw SQL trong application code
Auth: Firebase Authentication quản lý credential, Google/Facebook OAuth và email/password khi được dùng; web client gửi Firebase ID token qua `Authorization: Bearer`, Spring xác minh bằng Firebase Admin SDK; MySQL giữ `external_user_id`, role và trạng thái tài khoản. Mland không lưu password hash hoặc tự ký JWT
Testing: JUnit 5 + Spring Boot Test khi cần test HTTP, Spring context hoặc persistence
Styling: Responsive CSS mobile-first; browser/device baseline và accessibility standard phải được chốt trong SRS trước khi triển khai

## 3. ARCHITECTURE PRINCIPLES
- Follow modular monolith theo feature/domain: mỗi feature sở hữu controller, DTO, service/use case và repository; chỉ triển khai sau constitution ratify và `$speckit-specify` → `$speckit-clarify` → `$speckit-plan` → `$speckit-tasks` → `$speckit-analyze`, dùng `$speckit-implement` rồi `$speckit-converge`
- API style: REST versioned, resource số nhiều và kebab-case, ví dụ `/api/v1/workshop-bookings`
- Error handling: dùng Bean Validation, typed exception và `@RestControllerAdvice`; không trả stack trace hoặc payload nhạy cảm cho client
- No raw SQL — always use JPA; mọi schema change phải có migration có version bằng công cụ được chốt trong feature plan
- Server-rendered UI: Spring Boot trả page hoặc Thymeleaf fragment; htmx chỉ thay vùng cần thiết, không thay server-side validation/authorization, và structured logger không log secrets/PII. Firebase Hosting chỉ là công cụ preview được chấp thuận về hướng sử dụng, chưa phải production deployment hoặc backend origin
- Identity boundary: Firebase chỉ xác thực danh tính. Spring phải xác minh Firebase ID token trước khi dùng UID; MySQL là nguồn quyền `MEMBER`, `STAFF`, `OWNER`, `ADMIN_TECHNICAL`, trạng thái tài khoản và audit. Social provider hoặc Firebase claim không được tự cấp quyền nội bộ

## 4. FILE NAMING & STRUCTURE
Components: Thymeleaf page/fragment files dùng kebab-case; class/interface/enum Java dùng PascalCase
Utilities: PascalCase cho utility class; camelCase cho method, field và variable
API routes: kebab-case và plural resource (e.g. `/api/v1/workshop-bookings`)
DB tables: snake_case (e.g. `workshop_bookings`)

## 5. FORBIDDEN PATTERNS
- NEVER store secrets, passwords, JWT signing keys hoặc PII trong plain text hay file commit vào git
- NEVER chỉ decode Firebase ID token hoặc tin email/role claim mà không xác minh token bằng Firebase Admin SDK; không tự gộp Member theo email khi Firebase UID khác nhau
- NEVER coi Firebase Hosting preview là quyết định deploy production, thêm `firebase.json`, Firebase project ID, OAuth secret, Hosting rewrite, Cloud Run/VPS configuration, hoặc Firebase Test Lab khi chưa có feature plan được duyệt
- NEVER bypass SpecKit gates, tự suy diễn `TBD`, dùng prototype frontend làm bằng chứng cho backend behavior, hoặc thêm client framework khác mà không có quyết định mới
- NEVER skip input validation, server-side authorization hoặc transaction/transition rule đã nêu trong feature plan
- NEVER expose JPA entity trực tiếp qua API, nhét business logic vào controller, hoặc dùng raw SQL trong application code
- NEVER delete hoặc thay đổi dữ liệu, source boundary, publishing/mapping hay file ngoài phạm vi mà không có xác nhận rõ

## 6. DEFINITION OF DONE (per task)
- [ ] JUnit 5 test phù hợp được viết/chỉnh sửa và chạy pass, truy vết acceptance scenario gồm error/authorization path khi áp dụng
- [ ] Không có lỗi linting/build/quality gate; check liên quan được chạy mới, hoặc báo `Verification exception` và manual check
- [ ] API endpoint được cập nhật REST/OpenAPI contract, validation, Firebase ID-token authentication, MySQL authorization và HTTP error status; form/link cốt lõi vẫn chạy không cần htmx
- [ ] Khi phạm vi có Firebase Auth, UID provisioning idempotent, invalid/expired token path, role boundary, account-linking collision và token/PII log hygiene được kiểm tra; chiến lược Auth Emulator hoặc Firebase development project chỉ được chọn trong feature plan
- [ ] Error, transaction, integrity rule, versioned migration và audit-log impact được xử lý khi phạm vi thay đổi áp dụng
- [ ] Không còn placeholder, TODO mơ hồ, secret hay quyết định bị suy đoán trong phần thay đổi

## 7. GIT CONVENTIONS
Branch: feat/[feature-name] | fix/[bug-name] | spec/[feature-name]
Commit: [type]: [scope] - [description]
Example: feat(booking): add workshop booking endpoint

## 8. CURRENT SPRINT CONTEXT
Sprint: Planning & Requirements Specification
Focus: Ratify constitution, hoàn thiện SpecKit artifacts và thiết kế V1 server-rendered UI bằng Thymeleaf + htmx trước khi triển khai. Firebase Auth và Firebase Hosting preview là working agreement trong `spec-general.md`; production deploy và Firebase Auth test environment vẫn cần feature plan
Active specs: Chưa có feature spec; `.specify/memory/constitution.md` chưa được ratify
