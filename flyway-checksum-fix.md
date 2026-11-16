# Sửa lỗi Flyway "Migration checksum mismatch" trên DB dev local

Nếu backend của bạn không khởi động được vì Flyway báo **"Migration checksum mismatch"**,
hãy kiểm tra hai trường hợp dưới đây. Chạy các câu SQL trên **DB dev local của chính bạn**
(schema trong `JDBC_URL` của `.env`), **không** chạy trên DB dùng chung.

Mỗi máy chỉ cần xử lý một lần. Code trên `develop` hiện đã đúng.

## 1. Lệch ở V144

**Ai có thể bị:** người đã pull `develop` trong khoảng **19:05–19:47 ngày 14/11** rồi chạy backend,
hoặc người từng chạy nhánh `quoc` trong khoảng 18:46–19:45 cùng ngày.

**Nguyên nhân:** `V144__settlement_folio_vnpay_checkout.sql` được sửa cột `provider_create_date`
từ `CHAR(14)` sang `VARCHAR(14)` sau khi đã chạy trên một số máy.

**Kiểm tra:**

```sql
SELECT column_type FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'settlement_folio_collections'
  AND column_name = 'provider_create_date';
```

- Ra `char(14)`: bạn bị. Chạy 2 câu sau (không mất dữ liệu):

  ```sql
  ALTER TABLE settlement_folio_collections MODIFY provider_create_date VARCHAR(14) NULL DEFAULT NULL;
  UPDATE flyway_schema_history SET checksum = -734471421 WHERE version = '144';
  ```

- Ra `varchar(14)` hoặc không có dòng nào: bạn không bị, bỏ qua.

## 2. Lệch ở V146–V149

**Ai có thể bị:** người từng chạy nhánh `quoc` trước ngày 15/11.

**Nguyên nhân:** migration của `quoc` đã đổi số từ V146–V149 sang **V151–V154**, vì `develop`
đã dùng V146–V150.

**Kiểm tra:**

```sql
SELECT version, description FROM flyway_schema_history WHERE version IN ('146','147','148','149');
```

- `description` có dạng `settlement refund ...`, `settlement mland remedy ...`,
  `settlement ticket change ...`, `attendance ticket sale ...`: bạn bị. Không sửa tại chỗ được;
  cần **xoá schema dev rồi tạo lại** để backend chạy migration từ đầu. Dữ liệu dev trong schema đó sẽ mất.

  ```sql
  DROP DATABASE <ten_schema_dev>;
  CREATE DATABASE <ten_schema_dev> CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  ```

  Sau đó chạy lại backend, Flyway sẽ dựng lại schema.

- `description` là `notification ...`, `align review ...` hoặc không có dòng nào: bạn không bị.

## Lưu ý

- Nếu bị cả hai trường hợp, chỉ cần làm bước xoá và tạo lại schema ở mục 2 (mục 1 sẽ tự đúng).
- Schema test cố định (ví dụ `*_test_it`) bị lệch thì cũng xử lý như trên.

Có gì vướng nhắn Quốc.
