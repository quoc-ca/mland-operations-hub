# Error Log: ppswbs_backend

## Record BE-DESIGN-0001

- **ID**: `BE-DESIGN-0001`
- **Timestamp**: `2026-10-26T17:20:00+07:00`
- **Reporter**: Hồ Hoàng Quốc (tác nhân thực tế: Antigravity AI pair programmer)
- **Feature / Task**: `features/003-workshop-management/003.2-workshop-operations/tasks.md` - `T012`
- **Trạng thái**: `BLOCKED`

### 1. Phạm vi bị dừng và nguồn design
- **Phạm vi bị dừng**: Task `T012` (MySQL/Flyway migration verification và integration verification trên database MySQL thật).
- **Nguồn design**:
  - `ppswbs_backend/AGENT.md` §5 & §7 ("Không dùng H2 thay bằng chứng MySQL cần thiết", "Lỗi môi trường không phải pass", "Chỉ đánh dấu task hoàn tất khi có bằng chứng; không tick task đang dở lúc bị chặn").
  - `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/tasks.md` (Task T012).
  - Chỉ đạo reviewer trong user request: "T012 phải có bằng chứng migration chạy trên MySQL thật... Nếu môi trường hiện tại không có MySQL test container/database được phê duyệt, dừng theo AGENT.md và ghi ERROR-LOG; không tuyên bố T012 pass."

### 2. Khuyết / mâu thuẫn chính xác và kịch bản tái hiện
- **Vấn đề**: Môi trường cục bộ Windows có service MySQL 8.0 lắng nghe trên port 3306 (Process ID 6452 `mysqld.exe`), nhưng từ chối kết nối xác thực với cấu hình mặc định của repository (`root` / `sa123123` trong `application-test.properties` và `application.properties`). Đồng thời máy tính không có Docker engine / Testcontainers runtime trong PATH để khởi tạo database MySQL tạm thời.
- **Kịch bản tái hiện**:
  Chạy lệnh kiểm thử MySQL integration test:
  ```powershell
  mvn test "-Dtest=WorkshopOperationsMySQLIT"
  ```
  hoặc kiểm thử của feature trước:
  ```powershell
  mvn test "-Dtest=AccountManagementIntegrationTests"
  ```
  Cả hai đều thất bại ngay tại giai đoạn kết nối Flyway / HikariPool:
  ```
  Caused by: org.flywaydb.core.internal.exception.FlywaySqlException: Unable to obtain connection from database: Access denied for user 'root'@'localhost' (using password: YES)
  SQL State  : 28000
  Error Code : 1045
  Message    : Access denied for user 'root'@'localhost' (using password: YES)
  ```

### 3. Bằng chứng tài liệu / code / check đã chạy
- **FACT**:
  1. H2 In-Memory Suite (`WorkshopOperationsFoundationIT`) pass 24/24 tests (`BUILD SUCCESS` thời gian 18.538s), xác nhận toàn bộ business logic, schema DDL của Flyway V5/V6, authorization check hoạt động chính xác trên in-memory engine.
  2. Lệnh `mvn test "-Dtest=WorkshopOperationsMySQLIT"` thất bại với lỗi `Access denied for user 'root'@'localhost' (using password: YES)` (SQL State: 28000, Error Code: 1045).
  3. Lệnh `mvn test "-Dtest=AccountManagementIntegrationTests"` (từ Feature 001) cũng thất bại với đúng lỗi `Access denied for user 'root'@'localhost' (using password: YES)`.
  4. Lệnh PowerShell kiểm tra process xác nhận có tiến trình `mysqld` (PID 6452) đang chạy trên máy.
  5. Thử nghiệm kết nối tới MySQL localhost bằng các mật khẩu mặc định đều bị từ chối truy cập.
  6. Docker (`docker ps`) không tồn tại trong hệ điều hành (`The term 'docker' is not recognized`).
- **SUY LUẬN**:
  Instance MySQL cục bộ trên máy host đang cài đặt mật khẩu tài khoản `root` khác với `sa123123`, hoặc yêu cầu user/password riêng chưa được cấp phát trong biến môi trường `JDBC_USER` / `JDBC_PASSWORD`.
- **UNKNOWN**:
  Thông tin credentials thực tế của MySQL instance cục bộ hoặc đường dẫn/thông tin của MySQL test container được phê duyệt cho dự án.

### 4. Vì sao không thể implement đúng nếu không tự quyết và tác động
- Theo `AGENT.md` §7 và chỉ đạo bắt buộc của reviewer: "Không được dùng H2 thay thế bằng chứng MySQL/Flyway... không tuyên bố T012 pass". Nếu agent tự ý tick `[X] T012` chỉ dựa vào H2 hoặc mock MySQL, sẽ vi phạm trực tiếp tính toàn vẹn của bằng chứng migration (Flyway V6 chứa cú pháp MySQL như `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`, JSON column, DATETIME(6)).

### 5. File đã thay đổi, việc đang dở và kết quả tại lúc dừng
- **File đã thay đổi**:
  - `ppswbs_backend/src/main/resources/application.properties` & `ppswbs_backend/src/test/resources/application-test.properties`: Hỗ trợ binding biến môi trường `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`;
  - `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopOperationsMySQLIT.java`: Test xác minh Flyway V6 trên MySQL thật;
  - `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/tasks.md`: Giữ `- [ ] T012` chưa hoàn thành.
- **Việc đang dở**: Xác minh Flyway migration V6 chạy thành công trên instance MySQL thật để hoàn tất T012.
- **Kết quả lúc dừng**: H2 integration suite pass 24/24. MySQL integration test fail do access denied. T012 giữ trạng thái unchecked (`- [ ]`).

### 6. Quyết định / thiết kế / hành động cần bổ sung và điều kiện tiếp tục
- **Hành động cần thiết**: Cung cấp mật khẩu chính xác cho MySQL cục bộ (hoặc cấu hình các biến môi trường `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`), hoặc cấp Docker daemon / container MySQL đã phê duyệt.
- **Điều kiện tiếp tục**: Khi có thông tin kết nối MySQL hợp lệ, chạy lại `mvn test "-Dtest=WorkshopOperationsMySQLIT"`. Khi test này pass, cập nhật diễn biến trong `ERROR-LOG.md`, đánh dấu `[X] T012` trong `tasks.md` và bàn giao hoàn tất Phase 2.

### Diễn biến gỡ chặn / kiểm tra bổ sung
- **Thời điểm**: `2026-10-26T18:10:00+07:00`
- **Kiểm tra đã chạy**: Đã cập nhật `application-test.properties` để nhận các biến môi trường `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD` và thực thi:
  ```powershell
  mvn test "-Dtest=WorkshopOperationsMySQLIT"
  ```
- **Kết quả kiểm tra thật**:
  Quá trình khởi động Spring Boot context và Flyway initializer tiếp tục thất bại với lỗi:
  ```
  Caused by: org.flywaydb.core.internal.exception.FlywaySqlException: Unable to obtain connection from database: Access denied for user 'root'@'localhost' (using password: YES)
  SQL State  : 28000
  Error Code : 1045
  Message    : Access denied for user 'root'@'localhost' (using password: YES)
  ```
- **Kết luận diễn biến**: Môi trường hiện tại vẫn chưa thể xác thực thành công vào database MySQL (không có credentials hợp lệ và không có Docker runtime). Không ghi nhận secret/token/PII. Không dùng H2 thay thế MySQL evidence. T012 tiếp tục giữ nguyên trạng thái `BLOCKED` (`- [ ] T012`).

### Diễn biến gỡ chặn thành công (MySQL Evidence Verification)
- **Thời điểm**: `2026-10-26T20:01:42+07:00`
- **Reporter**: Hồ Hoàng Quốc
- **Môi trường xác minh**: MySQL 8.0 local thật, database cô lập `ppswbs_test` được cấu hình qua biến môi trường test của hệ thống.
- **Lệnh đã chạy từ repository root**:
  ```powershell
  mvn -f .\ppswbs_backend\pom.xml test "-Dtest=WorkshopOperationsMySQLIT"
  ```
- **Bằng chứng kiểm thử thật (MySQL Evidence)**:
  1. Kết nối thành công đến database MySQL 8.0 cô lập `ppswbs_test`.
  2. Flyway migration kiểm tra và áp dụng thành công:
     - `V5 - account management`
     - `V6 - workshop operations` (tạo đầy đủ bảng `workshop_branches`, `workshop_slots`, `workshop_sessions`, `session_staff_assignments`, `session_operational_leads`, `capacity_holds`, `attendance_tickets`, `ticket_session_entitlements`, `attendance_actions`, `operations_audit_events`, `cancellation_handoffs` cùng toàn bộ khóa ngoại, unique constraints và index).
  3. `WorkshopOperationsMySQLIT`:
     - `verifyV6MigrationAppliedOnMySQL`: Kiểm tra `flyway_schema_history` có version 6 success = 1 và bảng `workshop_sessions` tồn tại trong database MySQL thật.
     - Tests run: 1, Failures: 0, Errors: 0, Skipped: 0.
  4. Maven build outcome: `BUILD SUCCESS`.
- **Kết luận**: Blocker môi trường MySQL của `BE-DESIGN-0001` đã được gỡ bỏ hoàn toàn bằng bằng chứng thực tế trên MySQL 8.0 thật. Không sử dụng H2 để thay thế bằng chứng MySQL. Không ghi nhận secret, credential hay PII. Task `T012` đủ điều kiện nghiệm thu và chuyển sang hoàn thành `[X]`.

---

## Record BE-DESIGN-0002

- **ID**: `BE-DESIGN-0002`
- **Timestamp**: `2026-10-26T18:15:00+07:00`
- **Reporter**: Hồ Hoàng Quốc (tác nhân thực tế: Antigravity AI pair programmer)
- **Feature / Task**: `features/003-workshop-management/003.2-workshop-operations/tasks.md` - `T006`
- **Trạng thái**: `BLOCKED`

### 1. Phạm vi bị dừng và nguồn design
- **Phạm vi bị dừng**: Task `T006` (Triển khai `WorkshopOperationsFacade` và hợp đồng Idempotency cho các facade commands).
- **Nguồn design**:
  - `ppswbs-spec/features/003-workshop-management/contracts/workshop-interfeature-facades.md` (§Shared command/outcome rules: "Every command that changes business state includes an opaque caller-generated operationReference and a safe correlationReference").
  - `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/data-model.md` (§CapacityHold: "The grouping identity and persistence mechanism remain deferred to the approved cross-feature facade and implementation design", §Cross-module facade boundary).
  - `ppswbs_backend/AGENT.md` §5 & §6 (Quy định dừng và ghi ERROR-LOG khi phát hiện thiếu sót kiến trúc, không tự ý suy diễn hoặc repurpose schema).

### 2. Khuyết / mâu thuẫn chính xác và kịch bản phát hiện
- **Vấn đề 1 (Mất `correlationReference`)**: Hợp đồng facade dùng chung (`workshop-interfeature-facades.md`) quy định rõ mọi command thay đổi trạng thái đều bao gồm đồng thời `operationReference` (dùng để đảm bảo tính idempotent của nghiệp vụ) và `correlationReference` (dùng cho truy vết phân tán). Tuy nhiên mã nguồn hiện tại đang ghi `operationReference` vào cột `operations_audit_events.correlation_id`, khiến `correlationReference` thực sự của caller bị mất hoàn toàn, không được bảo lưu.
- **Vấn đề 2 (Thiếu bảo đảm Unique & Concurrency)**: Bảng `operations_audit_events` không có ràng buộc duy nhất (UNIQUE constraint) trên cặp `(action, correlation_id)`. Logic hiện tại dựa trên `findTopByActionAndCorrelationIdOrderByOccurredAtDesc` chỉ kiểm tra sau khi truy vấn. Khi có hai command trùng lặp gửi đồng thời (simultaneous concurrent calls with identical `operationReference`), cả hai luồng đều đọc thấy chưa có audit event và cùng tiến hành trừ capacity, tạo duplicate hold/ticket -> vi phạm nghiêm trọng tính toàn vẹn dữ liệu.
- **Vấn đề 3 (Cơ chế lưu trữ còn Deferred)**: Tài liệu thiết kế gốc `003.2/data-model.md` (dòng 66–67) xác nhận rõ: *"The grouping identity and persistence mechanism remain deferred to the approved cross-feature facade and implementation design."* Thiết kế cơ chế lưu trữ bền vững và khóa đồng thời cho idempotency vẫn đang bị hoãn (deferred), chưa được định nghĩa và phê duyệt chính thức.
- **Vấn đề 4 (Vi phạm ranh giới nghiệp vụ của Audit Event)**: Bảng `operations_audit_events` được thiết kế phục vụ mục đích ghi nhật ký kiểm toán hành vi vận hành (`action`, `actor_id`, `session_id`, `outcome`, `occurred_at`). Việc tự ý biến đổi (repurpose) bảng audit này cùng trường JSON `metadata` thành một sổ cái idempotency (idempotency ledger) là giải pháp tạm bợ, vi phạm nguyên tắc thiết kế dữ liệu và chưa có sự đồng thuận từ kiến trúc sư/owner.

### 3. Bằng chứng tài liệu / code / check đã chạy
- **FACT**:
  1. Hợp đồng `workshop-interfeature-facades.md` định nghĩa:
     - Rule 1: `operationReference` và `correlationReference` là 2 trường riêng biệt trên mọi state-changing command.
     - Rule 2: Cùng `operationReference` và nội dung semantic giống nhau trả original outcome; khác nội dung thì reject an toàn.
  2. DDL `V6__workshop_operations.sql` tạo bảng `operations_audit_events` không có `UNIQUE KEY (action, correlation_id)`.
  3. `data-model.md` dòng 66–67 khẳng định cơ chế persistence mechanism cho idempotency vẫn đang deferred.
  4. Code hiện tại trong `WorkshopOperationsFacadeImpl.java`:
     - Gán `operationReference` vào `correlationId`;
     - Lưu kết quả serialized JSON vào `metadata`;
     - Không có transaction isolation mức Serializable hoặc distributed/pessimistic lock ngăn duplicate đồng thời.
- **SUY LUẬN**:
  Không thể đạt được bảo đảm idempotency an toàn trước race condition nếu không có một cơ chế chuyên biệt (ví dụ: bảng riêng `operations_idempotency_keys` có unique index hoặc database constraint kết hợp commit boundary).
- **UNKNOWN**:
  Quyết định chính thức từ feature owner về: cấu trúc schema lưu trữ idempotency riêng biệt, vị trí lưu trữ `correlationReference`, và cơ chế xử lý khóa đồng thời.

### 4. Vì sao không thể implement đúng nếu không tự quyết và tác động
- Nếu agent tự ý tạo thêm bảng migration mới (ví dụ Flyway V7) hoặc tự tạo bảng khóa idempotency mà chưa có thiết kế đã duyệt, sẽ vi phạm trực tiếp `AGENT.md` và chỉ đạo reviewer ("không tự tạo contract/schema/migration mới ngoài thiết kế đã duyệt"). Ngược lại, nếu giữ nguyên cách dùng audit event thì hệ thống sẽ có lỗ hổng race condition khi production chịu tải đồng thời. Do đó, bắt buộc phải dừng và xin quyết định kiến trúc từ owner.

### 5. File đã thay đổi, việc đang dở và kết quả tại lúc dừng
- **File đã thay đổi**:
  - `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/WorkshopOperationsFacadeImpl.java`: Giữ nguyên, không sửa thêm implementation idempotency;
  - `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/tasks.md`: Đưa T006 về trạng thái unchecked `- [ ] T006` kèm lý do chặn kiến trúc;
  - `ppswbs_backend/ERROR-LOG.md`: Ghi nhận bản ghi `BE-DESIGN-0002`.
- **Việc đang dở**: T006 chưa thể coi là hoàn thành do thiếu cơ chế persistence và concurrency đã phê duyệt.
- **Kết quả lúc dừng**: T006 chuyển sang trạng thái `BLOCKED` (`- [ ] T006`). Không triển khai T013+.

### 6. Quyết định / thiết kế cần bổ sung và điều kiện tiếp tục
- **Quyết định cần bổ sung từ Feature Owner / Architect**:
  1. Thống nhất cơ chế persistence cho Idempotency Ledger: Cần một bảng riêng (ví dụ: `workshop_operations_idempotency_records` với unique constraint `(action, operation_reference)`) thay vì repurpose `operations_audit_events`.
  2. Bổ sung trường lưu trữ riêng biệt cho `correlationReference` (để truy vết log/metrics) độc lập với `operationReference`.
  3. Xác định chiến lược xử lý đồng thời (Concurrent Request Strategy): Khi 2 request cùng `operationReference` đến cùng thời điểm, request thứ hai phải chờ khóa hay nhận phản hồi `IN_PROGRESS` / `CONFLICT`.
- **Điều kiện tiếp tục**: Chỉ tiếp tục triển khai T006 khi có artifact thiết kế/schema bổ sung được phê duyệt và hướng dẫn migration rõ ràng từ owner.
