# Quickstart Validation — FE-06

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Hướng dẫn kiểm chứng dự kiến sau implementation. Hiện chưa có loyalty code/tests/migration, không tuyên bố các lệnh test dưới đây đã chạy.  
**References**: [spec](spec.md), [model](data-model.md), [interfaces](contracts/interfaces.md), [gates](integration-gates.md).

## 1. Prerequisites

- JDK21 và Maven wrapper repository; MySQL8.4/InnoDB test database riêng, không dùng DB production hoặc reset dữ liệu có sẵn.
- G-01–G-05 đạt cho hành trình tích hợp muốn nghiệm thu; nếu chỉ có test doubles thì chỉ ghi unit/contract evidence.
- Secret Firebase/JDBC qua môi trường/kênh cho phép, không ghi vào guide/repo/log. Shared identity có Member, Manager, Admin và tài khoản thiếu quyền; Member/Manager/Admin không tự suy quyền của nhau.
- Spec/plan/tasks được review và speckit-analyze trước code. Scope apply/rollback migration được owner môi trường cho phép; không có quyền drop/reset ngầm.
- Classes/tests bên dưới là tên dự kiến, phải được tạo trong implementation; không chạy selector không tồn tại rồi coi không có test fail là pass.

## 2. Kiểm tra môi trường khi đã có code

PowerShell, từ backend repository:

```powershell
Set-Location 'D:\mland-operations-hub\mland-operations-hub\ppswbs_backend'
java -version
.\mvnw.cmd -version
```

Dùng JDBC_URL/JDBC_DRIVER/JDBC_USER/JDBC_PASSWORD do môi trường test cấp. Không nhập mật khẩu thật vào command transcript hoặc commit file config. MySQL profile phải đặt ddl-auto=none, Flyway bật; không để fallback H2 làm bằng chứng database tests.

Sau khi implementation có các test classes:

```powershell
.\mvnw.cmd '-Dtest=ModulithArchitectureTests' test
.\mvnw.cmd '-Dtest=LoyaltyCalculationTest,LoyaltyLifecycleTest,LoyaltyApiContractIT,LoyaltySecurityIT' test
.\mvnw.cmd '-Dtest=LoyaltyMySqlMigrationIT,LoyaltyMySqlConcurrencyIT,LoyaltyRetailSourceIntegrationIT,LoyaltyWorkshopSourceIntegrationIT,LoyaltyRecoverySourceIntegrationIT' test
```

Expected: số test thực >0 cho từng selector, zero failures và environment evidence xác nhận MySQL8.4 thật. Maven/pom hiện có surefire include IT classes; không giả định một profile Testcontainers hoặc Docker compose đã tồn tại. Không thêm Testcontainers/provider mới từ quickstart.

## 3. Fixture và phép tính bắt buộc

Dữ liệu chương trình sau là fixture, không cấu hình mặc định thương mại.

| Case | Input | Expected |
| --- | --- | --- |
| Retail mixed | Eligible600.000, other200.000, shipping30.000, voucher10% max50.000 min500.000,200points | Discount50.000; goods750.000; redeem20.000; total760.000; earn73 khi paid. |
| Eligible min | Cùng cart, min700.000 | Voucher không đủ ngưỡng vì eligible chỉ600.000, không dùng tổng800.000 để vượt ngưỡng. |
| Rounding | Eligible33.333, discount7% | Giảm2.333đ; không làm tròn lên hoặc nhân thêm theo quantity. |
| Min points | NetBeforePoints9.900/10.000, wallet đủ | Max9/10 điểm; case9 không cho dùng; case10 dùng tối đa10. |
| Workshop | Gross1.000.000, voucher100.000,200points, FE03 fixture deposit50% | Net880.000, deposit440.000, remain440.000; deposit không earn, completed+fullypaid earn88. |
| Earn floor | Net9.999/10.000/19.999 | 0/1/1 điểm, không carry remainder. |
| No promotion | D=0, P=0 hoặc điểm hợp lệ | Có accepted policy snapshot; earn theo tỷ lệ lúc accept. |
| Zero | Retail valid0; workshop0 | Retail chốt theo FE08 không gateway, earn0; workshop reject không giữ dở dang. |

## 4. Test groups và evidence

1. **Programs/quotas — US1–US2**: tạo đủ ba kind/scope; mã `TET10`/` tet10 ` trùng; không reuse mã cũ, không đổi immutable fields. Giảm limit6used+2held xuống7 reject; xuống8 accept. Pause rồi resume cùng mã giữ counters/history/deadline; hết hạn/hết lượt vẫn không dùng mới, same command không chuyển trạng thái thêm, stale version/thiếu quyền bị từ chối. Counter và audit rollback cùng update.
2. **Kho/UI — US3/US8.6**: public labels/terms/allowPoints/limits, Member own kho, nhập trực tiếp normalize, save lặp không quota. Empty khác error. Chạy frontend React + Vite với backend REST và hoàn thành các hành trình cốt lõi qua REST; token thiếu/hết hạn bị từ chối.
3. **Auth — US1.6/US3.6/US6.4/US8.3/US8.7**: bearer đúng chain, session theo 001/revoked, current DB role/state, privileged assurance; không Member cookie→Manager hoặc Manager→Admin. Private response no-store; không leak token/member data qua lỗi.
4. **Snapshots — US2.4/US8.4**: accept booking theo policyA, đổi policyB và dừng/hết hạn mã trước ngày hoàn thành; accepted giữ policyA và original deadline, booking mới dùngB/current terms.
5. **Same wallet/last slot — US7.2**: hai transaction DB độc lập đồng thời retail/workshop tranh point/quota cuối; không âm, không vượt limit, một bên nhận conflict/changed limits phù hợp.
6. **Atomic creation — US4.7/US5.4**: fault trước/sau reserve/source invoice/capacity/quantity hold; rollback toàn bộ, không accepted source thiếu benefit hoặc orphan held points.
7. **Lifecycle — US4.9/US5.5/US6.6/US7**: paid/deposit/expiry/cancel races dưới source lock; repeat/lost response; không release từ deadline nếu payment unresolved; source proof out-of-order/conflicting không sửa terminal.
8. **Restore — US7.3–US7.7, US2.9**: held chỉ release; consumed hoàn exactly points gốc và cả hai quota; points-only/voucher-only/no-benefit, mã hết hạn/dừng không reactivation. Simulate component đã hoàn và retry phần thiếu; không cộng hai lần. Một booking duy nhất đã giảm voucher100.000đ hủy hợp lệ thì report còn0 lượt/0đ; giữ lịch sử dùng/hoàn, repeat không làm âm, chương trình hết hạn/dừng không xóa usage giao dịch chưa hủy.
9. **Eligibility**: Guest booking import không retro-earn; workshop chưa đủ một điều kiện không grant; cùng giao dịch grant0/positive một lần; không có route public tự báo complete/refund.
10. **Migration/retention — FR-041**: database empty và populatedV1–V3 upgrade; preserve members/bookings/receipts, constraints/UTC/code uniqueness/decimal bounds. Audit/results tối thiểu5 năm, protected references/unfinished, không purge v1 hoặc xóa balances/tombstones.

## 5. Recording results

Mỗi run ghi commit/environment/MySQL version, selector và số tests, scenario IDs, expected/observed, pass/fail, correlation evidence đã che dữ liệu nhạy cảm. Database concurrency phải hai transaction thật; mocks/H2 không thay.

Nếu dependency chưa có hoặc test selector chưa tồn tại: ghi NOT RUN cùng G-ID và điều kiện cần, không đánh pass. Manual UI verification cần ghi flow, actor, trạng thái/source facts và ảnh/chứng cứ an toàn; không tự chụp token/PII.

Static checks ở bước lập plan chỉ kiểm tra cấu trúc tài liệu, OpenAPI refs và traceability. Chúng không chứng minh payment, migration, security, locks hoặc tích hợp đã hoạt động.

