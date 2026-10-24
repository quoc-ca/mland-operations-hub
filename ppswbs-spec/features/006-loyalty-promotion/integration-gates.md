# Integration Gates — FE-06

**Date**: 2026-10-24  
**Kết luận**: CÔNG VIỆC TÀI LIỆU hoàn thành cho plan; chưa đủ điều kiện triển khai/bật toàn feature. Không có câu hỏi nghiệp vụ FE-06 còn chờ trả lời. Gates dưới đây là dependency thiết kế/implementation cần giao đúng owner, không phải bằng chứng quyền code/deploy.

| Gate | Evidence hiện có | Owner/việc còn phải có | Điều kiện đạt |
| --- | --- | --- | --- |
| G-01 Identity/capability | MemberFacade chỉ lookup/entitlement; SecurityConfiguration fallback permitAll/CSRF disabled; 003/005 có shared-auth design | FE-01/identity cung cấp verified bearer/cookie session fixed8h, revocation/state, current Member/Manager/Admin capabilities, privileged assurance và authorization guard | Contract/API/web checks với identity thật, deny thiếu quyền/assurance, CSRF/expiry/revocation và existing Member journeys không regression. Không chọn MFA method từ loyalty. |
| G-02 Catalogue targets/source sales | Catalogue/readyringsales code hiện shell; 005 có design/catalogue SPI | FE-02 cung cấp product/variant facts và guard; FE-08 sở hữu price/quantity source snapshot và accepted order | Không raw repository access/fake provider; create/quote/accept scope và source guard races qua provider thật, Module verify pass. |
| G-03 Retail owner | FE-08 chưa có order/payment runtime | FE-08 tích hợp preview/accept reserve atomic, full-paid/zero outcome, original deadline, expiry/arbitration và source delivery receipts nếu cần bridge | Rollback cả hai chiều, zero không gateway, callback duplicate/late, expired/unresolved không release sai, snapshot tiền/points không đổi. |
| G-04 Booking acceptance/deposit | WorkshopBookingService đang tính invoice/cọc theo giá gốc, có callback/expiry nhưng chưa owner lock contract đủ | FE-03 thêm selection/quote acknowledgement, net fee trước cọc, snapshot/boundary, lifecycle source lock | Invoice/cọc/remain đúng after-voucher/points, deposit settle một lần, không grant sớm; atomic creation/capacity/loyalty rollback và payment-vs-expiry test thật. |
| G-05 Workshop hậu xử lí | Spec002 hoãn completion/cancel/no-show/final-payment; runtime chưa cấp authoritative outcomes đủ FE06 | FE-03 cung cấp xác nhận completed AND fully-paid, valid paid-cancellation trước completion, no-show final và phân xử late/conflicting outcomes | Chứng minh both conditions, policy cũ, valid cancellation restore đúng, no-show không auto restore; Guest import không retro-earn; source và loyalty history đồng bộ. |
| G-06 Data/module verification | Loyalty chưa có production/test/migration; V1–V3 hiện có | FE-06 implementation sau spec/plan/tasks review và analyze; migration number mới, MySQL8.4 upgrade, locks/receipts/audit | Empty + upgrade không mất data, invariants/concurrency và ApplicationModules.verify(); không H2-only evidence. |
| G-07 Governance before implementation | Nghiệp vụ/retention/shared-auth đã có xác nhận; plan/contracts này Draft | Review technical design, tạo tasks theo dependency, chạy speckit-analyze; yêu cầu triển khai riêng | Không unknown ảnh hưởng task, thiết kế public contract/locking/data được review, task dependencies đủ bằng chứng. Không dùng checklist spec thay phê duyệt plan. |

## Không hỏi lại các quyết định đã có

- Voucher/points dùng chung hai dịch vụ, không hạng.
- 10.000đ→1 điểm, 1 điểm→100đ, tối thiểu10, trần10%, floor từng giao dịch.
- Quyết định 1–10 trong hội thoại đã chốt trước specify.
- Lịch sử/audit/receipts tối thiểu5 năm, bảo vệ refs/unfinished transactions.
- Dùng chung FE-01/003, session fixed8h, current rights/CSRF và privileged assurance dependency.

## Handoff boundaries

- Tác vụ speckit-plan không sửa production/test code, migration, pom, auth config hoặc feature 002/005.
- Không production adapter trả allowAll/zero-held/fully-paid giả. Test double chỉ kiểm tra logic độc lập, không đóng G-01–G-05.
- Các gate có thể được đưa thành dependency-ordered tasks sau khi review; chưa tự chia scope triển khai hoặc bật một nửa feature từ lần lập plan này.
- Khi muốn triển khai, đánh giá readiness lại bằng [kit](../../agent-design-kit/readiness-checklist.md). Không tự mark ready chỉ vì bộ tài liệu đủ file.

