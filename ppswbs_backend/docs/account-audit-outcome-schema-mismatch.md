# Account Audit Outcome Schema Mismatch

## Phạm vi

Đây là blocker của Account Management (FE-001), không phải business logic của
Workshop Operations (003.2).

## Lỗi hiện tại

Migration Account Management tạo cột:

```sql
account_audit_events.outcome ENUM('SUCCESS', 'FAILURE') NOT NULL
```

Entity `AccountAuditEvent` lại map cột này thành Java `String`:

```java
@Column(name = "outcome", nullable = false)
private String outcome;
```

Khi chạy với:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Hibernate so sánh schema MySQL với mapping JPA và phát hiện:

```text
database: ENUM
entity:   VARCHAR/String
```

Ứng dụng dừng ở bước khởi tạo `EntityManagerFactory`, trước khi integration
test của Workshop Operations có thể chạy.

## Bằng chứng

```text
Schema-validation: wrong column type encountered in column [outcome]
in table [account_audit_events]; found [enum], but expecting [varchar]
```

Migration nguồn:

```text
ppswbs_backend/src/main/resources/db/migration/V5__account_management.sql
```

Entity nguồn:

```text
ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/entity/AccountAuditEvent.java
```

## Quyết định cần FE-001/Tech Lead chốt

Chọn một contract duy nhất:

1. Giữ entity Java `String` và chuẩn hóa database column thành
   `VARCHAR(32)` bằng migration hợp lệ; hoặc
2. Giữ MySQL `ENUM` và sửa entity mapping để Hibernate nhận đúng kiểu enum,
   kèm test schema validation trên MySQL.

## Ràng buộc

- Không sửa trực tiếp migration đã áp dụng trong môi trường data-bearing.
- Không dùng `ddl-auto=update` để che mismatch.
- Không dùng Flyway ignore hoặc Maven exclude.
- Sau khi chốt, phải chạy trên fresh MySQL database và chạy lại
  `WorkshopOperationsMySQLIT`.

## Ảnh hưởng tới 003.2

T037 đã compile và migration V16 đã được Flyway apply thành công, nhưng chưa
được nghiệm thu MySQL vì Hibernate không khởi tạo được do mismatch của bảng
Account Management.

Sau khi FE-001/Tech Lead sửa baseline, chạy lại:

```powershell
mvn -f .\ppswbs_backend\pom.xml test "-Dtest=WorkshopOperationsMySQLIT"
```
