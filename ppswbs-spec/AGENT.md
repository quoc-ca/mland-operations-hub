# AGENTS.md — Project Context for AI Agents
# Version: 1.0 | Updated: 2026-09-19 | Project: Personalized Product Sales and Workshop Booking System

## 1. PROJECT OVERVIEW
Name: Personalized Product Sales and Workshop Booking System (PPSWBS)
Type: Backend REST API — modular monolith theo feature/domain
Domain: Workshop trang sức và bán sản phẩm cá nhân hóa; ưu tiên tài liệu đã phê duyệt trong `../documents/docs/`, giữ nguyên mọi `TBD`
Stage: Planning & Requirements Specification; `../frontend/` chỉ là UX prototype offline, không phải production backend

## 2. TECH STACK (STRICT — do not deviate)
Backend: Java 21 + Spring Boot
Frontend: Out of scope; không tạo hoặc thay đổi frontend nếu không được yêu cầu rõ
Database: MySQL (version sẽ được chốt trong feature plan)
ORM: Spring Data JPA / JPA; không dùng raw SQL trong application code
Auth: JWT Bearer + BCrypt cho password
Testing: JUnit 5 + Spring Boot Test khi cần test HTTP, Spring context hoặc persistence
Styling: N/A — backend-first

## 3. ARCHITECTURE PRINCIPLES
- Follow modular monolith theo feature/domain: mỗi feature sở hữu controller, DTO, service/use case và repository; chỉ triển khai sau constitution ratify và `$speckit-specify` → `$speckit-clarify` → `$speckit-plan` → `$speckit-tasks` → `$speckit-analyze`, dùng `$speckit-implement` rồi `$speckit-converge`
- API style: REST versioned, resource số nhiều và kebab-case, ví dụ `/api/v1/workshop-bookings`
- Error handling: dùng Bean Validation, typed exception và `@RestControllerAdvice`; không trả stack trace hoặc payload nhạy cảm cho client
- No raw SQL — always use JPA; mọi schema change phải có migration có version bằng công cụ được chốt trong feature plan
- No debug logging of secrets/PII — use structured logger; các mutation nghiệp vụ phải đánh giá audit-log impact

## 4. FILE NAMING & STRUCTURE
Components: N/A — backend-first; class/interface/enum Java dùng PascalCase
Utilities: PascalCase cho utility class; camelCase cho method, field và variable
API routes: kebab-case và plural resource (e.g. `/api/v1/workshop-bookings`)
DB tables: snake_case (e.g. `workshop_bookings`)

## 5. FORBIDDEN PATTERNS
- NEVER store secrets, passwords, JWT signing keys hoặc PII trong plain text hay file commit vào git
- NEVER bypass SpecKit gates, tự suy diễn `TBD`, hoặc dùng prototype frontend làm bằng chứng cho backend behavior
- NEVER skip input validation, server-side authorization hoặc transaction/transition rule đã nêu trong feature plan
- NEVER expose JPA entity trực tiếp qua API, nhét business logic vào controller, hoặc dùng raw SQL trong application code
- NEVER delete hoặc thay đổi dữ liệu, source boundary, publishing/mapping hay file ngoài phạm vi mà không có xác nhận rõ

## 6. DEFINITION OF DONE (per task)
- [ ] JUnit 5 test phù hợp được viết/chỉnh sửa và chạy pass, truy vết acceptance scenario gồm error/authorization path khi áp dụng
- [ ] Không có lỗi linting/build/quality gate; check liên quan được chạy mới, hoặc báo `Verification exception` và manual check
- [ ] API endpoint được cập nhật REST/OpenAPI contract, validation, authentication/authorization và HTTP error status
- [ ] Error, transaction, integrity rule, versioned migration và audit-log impact được xử lý khi phạm vi thay đổi áp dụng
- [ ] Không còn placeholder, TODO mơ hồ, secret hay quyết định bị suy đoán trong phần thay đổi

## 7. GIT CONVENTIONS
Branch: feat/[feature-name] | fix/[bug-name] | spec/[feature-name]
Commit: [type]: [scope] - [description]
Example: feat(booking): add workshop booking endpoint

## 8. CURRENT SPRINT CONTEXT
Sprint: Planning & Requirements Specification
Focus: Ratify constitution và hoàn thiện SpecKit artifacts trước khi triển khai backend Java
Active specs: Chưa có feature spec; `.specify/memory/constitution.md` chưa được ratify
