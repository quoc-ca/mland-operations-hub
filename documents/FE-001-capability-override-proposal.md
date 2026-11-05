# Đề xuất FE-001: Capability Override theo từng Account

## 1. Bối cảnh

Feature 003.2 Workshop Operations có thao tác **check-in override** cho các
trường hợp khách không thể sử dụng ticket bình thường sau khi resend thất bại.
Đây là thao tác ngoại lệ, có thể làm thay đổi trạng thái attendance và phải có
reason/audit.

Hiện tại `STAFF` chỉ là role chung. Role này chưa trả lời được các câu hỏi:

- Staff nào được phép override?
- Quyền đó có áp dụng ở mọi location hay chỉ một location?
- Quyền có thể cấp tạm thời rồi tự hết hạn không?
- Khi xảy ra sự cố, ai đã cấp quyền và vì lý do gì?

Vì vậy, 003 cần FE-001 xuất bản một capability-decision boundary:

```text
AccountFacade.can(accountId, "workshop.checkin.override")
→ typed ALLOWED / DENIED result
```

003 chỉ consume kết quả semantic này; không đọc entity, repository, Firebase
claim hoặc tự suy luận `STAFF` thành quyền.

## 2. Vì sao role là chưa đủ?

Role trả lời “người này thuộc nhóm nào”, còn capability trả lời “người này có
được phép thực hiện thao tác cụ thể hay không”. Hai Staff cùng có role `STAFF`
nhưng có thể có năng lực và trách nhiệm khác nhau:

- Staff mới chưa được đào tạo về xử lý ticket lỗi.
- Staff hỗ trợ tạm thời chỉ được thao tác tại một location.
- Staff đang bị tạm đình chỉ quyền override nhưng vẫn cần quyền check-in thông
  thường.
- Một Staff senior được ủy quyền xử lý ngoại lệ trong một ca cụ thể.

Nếu 003 tự dùng điều kiện `role == STAFF`, mọi Staff active đều có thể đi vào
đường override. Điều này vi phạm least privilege và làm mất ranh giới bảo mật
giữa check-in bình thường với thao tác ngoại lệ.

## 3. Lợi ích của per-account capability override

### 3.1. Phân quyền chính xác

Chỉ những Staff được đào tạo hoặc được Manager/Admin ủy quyền mới có thể dùng
`workshop.checkin.override`. Staff khác vẫn làm check-in bình thường nhưng bị
từ chối ở đường override.

### 3.2. Giảm gian lận và sai sót

Override có thể bỏ qua một phần quy trình ticket. Giới hạn quyền theo account
giúp giảm nguy cơ:

- check-in nhầm người;
- sử dụng ticket không hợp lệ;
- bỏ qua quy trình resend mà không có lý do;
- nhân viên tự ý sửa attendance.

### 3.3. Cấp quyền tạm thời

Có thể cấp quyền trong một khoảng thời gian cụ thể:

- một ca làm việc;
- thời gian hỗ trợ sự kiện đông khách;
- thời gian xử lý incident;
- thời gian thay thế nhân sự chính.

Hết hạn thì quyền tự động bị từ chối, không cần nhớ thu hồi thủ công.

### 3.4. Thu hồi từng người

Khi một Staff vi phạm hoặc chuyển nhiệm vụ, FE-001 có thể thu hồi riêng quyền
override của người đó mà không ảnh hưởng toàn bộ Staff trong hệ thống.

### 3.5. Giới hạn theo location

Capability có thể gắn với location scope. Một Staff có thể được override tại
location A nhưng không được override tại location B. Điều này bổ sung cho việc
003 vẫn kiểm tra session location trực tiếp.

### 3.6. Audit và truy trách nhiệm

Hệ thống có thể lưu:

- ai cấp hoặc thu hồi quyền;
- quyền nào được thay đổi;
- lý do và evidence reference;
- thời điểm bắt đầu/kết thúc;
- account hoặc location nào bị ảnh hưởng.

Khi có tranh chấp attendance, có thể trả lời được cả hai câu hỏi: “ai thực hiện
override?” và “tại thời điểm đó người đó có quyền hay không?”.

### 3.7. Least privilege và compliance

Mỗi account chỉ nhận quyền cần thiết. Đây là mô hình phù hợp hơn với các thao
tác có thể thay đổi dữ liệu attendance, refund hoặc settlement sau này.

## 4. Đề xuất mô hình dữ liệu

FE-001 có thể giữ role mặc định và bổ sung override riêng cho từng account:

```text
account_capability_overrides
- id
- account_id
- capability_key
- decision: GRANT | DENY
- location_id (nullable nếu áp dụng toàn bộ scope)
- valid_from
- valid_until (nullable nếu không hết hạn)
- reason_code
- reason_note/evidence_reference
- granted_by_account_id
- created_at
- revoked_at
```

Quy tắc quyết định nên được FE-001 sở hữu:

1. Account phải tồn tại và đang `ACTIVE`.
2. Override còn hiệu lực tại thời điểm gọi facade.
3. DENY cụ thể có thể ưu tiên hơn GRANT mặc định.
4. Location-specific decision chỉ áp dụng cho location tương ứng.
5. Mọi thay đổi quyền phải có audit bất biến.
6. Facade chỉ trả semantic result, không trả entity hoặc dữ liệu nội bộ.

Tên bảng, migration version và chi tiết locking cần FE-001/Tech Lead chốt theo
baseline chung; đây chỉ là đề xuất design.

## 5. Contract đề xuất cho 003 consume

```java
CapabilityDecision can(Long accountId, String capabilityKey, Long locationId);
```

Kết quả nên là typed result, ví dụ:

```text
ALLOWED
DENIED_ACCOUNT_NOT_FOUND
DENIED_ACCOUNT_INACTIVE
DENIED_CAPABILITY
DENIED_LOCATION_SCOPE
```

003 chỉ cần xử lý allow/deny và map sang lỗi nghiệp vụ an toàn. 003 không cần
biết quyền được cấp từ role mặc định hay từ override riêng.

## 6. Phạm vi trách nhiệm

### FE-001 sở hữu

- nguồn dữ liệu capability;
- policy role mặc định;
- per-account GRANT/DENY;
- expiry và thu hồi;
- audit thay đổi quyền;
- public `AccountFacade.can(...)`.

### 003.2 sở hữu

- kiểm tra actor đang active;
- kiểm tra Staff được assign đúng session/location;
- gọi `AccountFacade.can(...)` cho `workshop.checkin.override`;
- kiểm tra ticket, resend failure, reason và evidence;
- ghi audit business của hành động override.

003 không được tự tạo bảng capability hoặc tự map role thành capability.

## 7. Triển khai theo giai đoạn

### Giai đoạn tối thiểu để unblock 003 V1

FE-001 công bố `AccountFacade.can(...)` với policy nội bộ đơn giản, chẳng hạn
capability được cấu hình theo role hoặc cấu hình server-side. 003 có thể hoàn
thành contract mà chưa cần giao diện quản trị quyền.

### Giai đoạn đầy đủ sau V1

Bổ sung per-account override, expiry, location scope, admin command và audit
history. Khi đó không cần thay đổi contract của 003; chỉ thay đổi cách FE-001
ra quyết định bên trong.

## 8. Trade-off và chi phí

### Lợi ích

- bảo mật tốt hơn;
- giảm quyền dư thừa;
- xử lý được ngoại lệ thực tế;
- audit và truy trách nhiệm rõ;
- có thể tái sử dụng cho các capability nhạy cảm khác.

### Chi phí

- thêm migration và bảng dữ liệu;
- thêm facade/service và test;
- cần API hoặc màn hình quản trị;
- cần quy định ưu tiên giữa role default, GRANT và DENY;
- cần xử lý expiry, revoke và cache nếu có.

## 9. Kiến nghị gửi FE-001

Đề nghị FE-001 đồng ý tách hai việc:

1. **Bắt buộc ngay:** công bố `AccountFacade.can(...)` như semantic boundary để
   003 không tự suy role thành capability.
2. **Nên triển khai:** per-account capability override để hỗ trợ phân quyền
   Staff cụ thể, cấp quyền tạm thời, thu hồi và audit đầy đủ.

Per-account override không phải điều kiện duy nhất để hoàn thành 003 V1, nhưng
nó là nền tảng phù hợp nếu hệ thống cần vận hành nhiều location, nhiều nhóm
Staff và các tình huống ngoại lệ có kiểm soát.

## 10. Các quyết định cần FE-001/Tech Lead xác nhận

- Có cho phép DENY một Staff cụ thể dù role mặc định được GRANT không?
- Capability có giới hạn theo location không?
- Ai được cấp/thu hồi capability?
- Có bắt buộc thời hạn cho GRANT tạm thời không?
- Retention của capability audit là bao lâu?
- Có cần cache decision hay luôn đọc quyết định mới nhất?
- Contract typed result cuối cùng dùng tên và mã lỗi nào?
