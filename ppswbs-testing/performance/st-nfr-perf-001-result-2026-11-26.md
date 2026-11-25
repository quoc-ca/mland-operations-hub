# ST-NFR-PERF-001: kết quả kiểm thử tải

- Kết quả: PASS (p95 < 2 giây, không có request lỗi)
- Thời gian chạy: 26/11/2026 03:05:12 – 03:07:55
- Người chạy: Hồ Hoàng Quốc
- Script: `ppswbs-testing/performance/st-nfr-perf-001.js`
- Tải: tăng lên 100 người dùng trong 30 giây, giữ 2 phút, giảm trong 10 giây
- API: `GET /api/v1/workshop-packages/{packageId}/sessions?locationId={branchId}` (các gói và chi nhánh đang có buổi đặt được)

| Chỉ số | Giá trị |
| --- | --- |
| Số request tra lịch | 8.545 (52,7 request/giây) |
| p50 | 691 ms |
| p95 | 885 ms |
| p99 | 1,13 s |
| Trung bình | 646 ms |
| Lớn nhất | 2,2 s |
| Tỷ lệ lỗi | 0% (check status 200: 8.545/8.545) |

## Cấu hình máy chạy

Backend, MySQL và k6 chạy chung một máy (localhost).

- CPU: Intel Core i7-12650H, 10 nhân / 16 luồng
- RAM: 15,6 GB
- Hệ điều hành: Windows 11 Home Single Language (10.0.26300)
- Java 21.0.12.1, MySQL Community Server 8.4.13, k6 v2.4.0
