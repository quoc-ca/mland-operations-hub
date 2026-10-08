# Mland — Screen Flow

Sơ đồ điều hướng toàn hệ thống, gồm các màn public/authentication và bốn điểm vào sau đăng nhập: **Member Dashboard**, **Staff Dashboard**, **Manager Dashboard**, **Admin Dashboard**. Các nhánh chi tiết bên dưới mở rộng sơ đồ tổng quan; tên màn trùng nhau giữa các sơ đồ chỉ cùng một màn hoặc cùng một bố cục theo quyền.

## Quy ước

- Hình chữ nhật: màn hình; hình bo tròn: tab, bước trong form, popup hoặc hành động được ghi rõ trên nhãn.
- Hình thoi: điều kiện điều hướng, không phải một màn hình.
- Khung kép: giao diện của hệ thống bên ngoài.
- Mũi tên liền: mở màn, gửi form hoặc quay lại; nhãn trên mũi tên nêu điều kiện cần thiết.
- Mũi tên đứt: chuyển xử lý giữa vai trò hoặc mở nội dung tham khảo, không cấp quyền truy cập vào màn của vai trò khác.
- Các nút và dữ liệu phải được kiểm tra quyền ở từng lần truy cập/submission. Member chỉ thao tác trên dữ liệu của mình; Staff xử lý retail order trong assigned branch; Manager thực hiện hành động được ủy quyền khi nghiệp vụ yêu cầu.

**Phạm vi đã chốt:** Revenue Dashboard chỉ dành cho Manager. Các chức năng bổ sung từ draft Member Authentication không được đưa vào sơ đồ: Policy Acceptance gate, liên kết thêm phương thức đăng nhập, import Guest bookings và xác nhận email booking trước thanh toán. Đăng ký, đăng nhập, xác minh đăng ký, khôi phục và đổi mật khẩu vẫn được thể hiện theo Account Management trong Report 3.

## 1. Tổng quan — Public → Authentication → Post Login

```mermaid
flowchart LR
    Home["Home Page"]
    Products["Product Catalogue"]
    Product["Product Details"]
    Packages["Workshop Package List"]
    Package["Workshop Package Details"]
    Booking["Workshop Booking Form"]
    Lookup["Booking Lookup"]
    BookingDetails["Booking Details / QR Ticket"]
    Store["Store Information"]
    AI(["Basic AI Chat — chatbox"])
    Login["User Login"]
    Register["User Register"]
    Verify["Email Verification — đăng ký"]
    Forgot["Forgot Password"]
    Reset["Reset Password"]
    SSO[["SSO Provider"]]
    Access{"Xác thực hợp lệ / account active / role nội bộ"}

    subgraph PostLogin["Post Login"]
        direction TB
        A["A. Member Dashboard"]
        B["B. Staff Dashboard"]
        C["C. Manager Dashboard"]
        D["D. Admin Dashboard"]
    end

    MemberEntry["Cart / My Orders / My Bookings / Custom Orders / Loyalty / Profile"]
    StaffEntry["Products / Check-in / Design Review / Order Operations / Consultation"]
    ManagerEntry["Workshop Operations / Approvals / Promotions / Custom Queue"]
    Revenue["Executive Revenue Dashboard"]
    AdminEntry["User Accounts / RBAC / Parameters / Integrations / Technical Support"]

    Home --> Products --> Product
    Home --> Packages --> Package --> Booking
    Home --> Lookup --> BookingDetails
    Home --> Store
    Home --> AI
    Home --> Login
    Product -->|"Guest muốn mua"| Login
    Login --> Register --> Verify -->|"Hoàn tất đăng ký"| Login
    Login --> Forgot --> Reset --> Login
    Login -->|"Chọn SSO"| SSO
    Register -->|"Chọn SSO"| SSO
    SSO -->|"Trả kết quả xác thực"| Access
    Login -->|"Email/password hợp lệ"| Access
    Access -->|"Member"| A
    Access -->|"Staff"| B
    Access -->|"Manager"| C
    Access -->|"Admin"| D
    A --> MemberEntry
    A --> Products
    A --> Packages
    B --> StaffEntry
    C --> ManagerEntry
    C --> Revenue
    D --> AdminEntry

    classDef public fill:#e7f4e4,stroke:#54844b,color:#172e12;
    classDef auth fill:#fce4e4,stroke:#b96565,color:#472020;
    classDef member fill:#e3efff,stroke:#4a78b8,color:#183354;
    classDef staff fill:#eee5fa,stroke:#8560aa,color:#38224e;
    classDef manager fill:#fff0d6,stroke:#bc8734,color:#51370e;
    classDef admin fill:#def3ef,stroke:#458d80,color:#173e36;
    class Home,Products,Product,Packages,Package,Booking,Lookup,BookingDetails,Store,AI public;
    class Login,Register,Verify,Forgot,Reset auth;
    class A,MemberEntry member;
    class B,StaffEntry staff;
    class C,ManagerEntry,Revenue manager;
    class D,AdminEntry admin;
```

Các ô nhóm chức năng bên phải Dashboard là điểm dẫn tới những nhánh chi tiết bên dưới, không phải các màn mới bắt buộc phải xây dựng.

## 2. Public và authentication — chi tiết

```mermaid
flowchart LR
    Home["Home Page"]
    Login["User Login"]
    Register["User Register"]
    Verify["Email Verification — đăng ký"]
    Forgot["Forgot Password"]
    Reset["Reset Password"]
    SSO[["SSO Provider"]]
    Entry(["Dashboard theo role — xem mục 1"])
    Terms["Terms"]
    Privacy["Privacy Policy"]
    Catalogue["Product Catalogue"]
    Product["Product Details"]
    Cart(["Shopping Cart — Member, xem A1"])
    Packages["Workshop Package List"]
    Package["Workshop Package Details"]
    Booking["Workshop Booking Form"]
    Models["Available Ring Models"]
    Config["Workshop Ring Configurator"]
    Image(["Booking Design Image — Member, xem A2"])
    Deposit["Workshop Deposit Payment"]
    VNPay[["VNPay"]]
    Result["Payment Result"]
    Lookup["Booking Lookup"]
    Details["Booking Details / QR Ticket"]
    Store["Store Information"]
    Maps[["Google Maps / Review Link"]]
    AI(["Basic AI Chat — chatbox"])

    Home --> Login
    Login --> Register
    Register -->|"Email registration"| Verify
    Verify -->|"Xác minh thành công"| Login
    Verify -->|"Thử lại khi còn hợp lệ / yêu cầu mới"| Verify
    Register -->|"SSO registration"| SSO
    Login -->|"SSO sign-in"| SSO
    SSO -->|"Identity hợp lệ; kiểm tra account/role"| Entry
    Login -->|"Đăng nhập hợp lệ; kiểm tra account/role"| Entry
    Login --> Forgot
    Forgot -->|"Mở recovery link hợp lệ từ email"| Reset
    Reset -->|"Đổi mật khẩu thành công"| Login
    Reset -->|"Link không hợp lệ / hết hạn"| Forgot
    Home --> Terms
    Home --> Privacy
    Register -.-> Terms
    Register -.-> Privacy

    Home --> Catalogue --> Product
    Product -->|"Guest muốn mua"| Login
    Product -->|"Member; revalidate variant; Add to Cart"| Cart
    Home --> Packages --> Package -->|"Book Workshop; giữ package đã chọn"| Booking
    Booking -->|"Chọn mẫu có sẵn"| Models
    Models -->|"Xác nhận mẫu hợp lệ"| Booking
    Booking -->|"Cấu hình ring"| Config
    Config -->|"Xác nhận cấu hình hợp lệ"| Booking
    Booking -->|"Chỉ Member; upload design image"| Image
    Booking -->|"Submit đủ điều kiện; tạo pending booking/hold"| Deposit
    Deposit --> VNPay -->|"Browser return chỉ cung cấp thông tin"| Result
    Result -->|"Đọc trạng thái đã được hệ thống xác minh"| Details
    Home --> Lookup -->|"Kiểm tra quyền tra cứu phù hợp"| Details
    Details -->|"Pending và payment/hold còn đủ điều kiện"| Deposit
    Details -->|"Hết hạn; bắt đầu booking mới"| Packages
    Home --> Store --> Maps
    Home --> AI
```

- Package phải được chọn trước khi chốt session/design path. Guest có thể đặt workshop, nhưng không upload booking-design image, mua retail hoặc tạo custom-manufacturing order.
- Booking confirmed mới hiển thị QR. Tra cứu Guest dùng bằng chứng truy cập phù hợp; Member truy cập bằng ownership. Link email mở lại Booking Details, không phải một màn nghiệp vụ mới.
- Đăng ký/xác thực lỗi giữ người dùng tại màn hiện tại với hướng dẫn an toàn. Recovery không tiết lộ sự tồn tại của tài khoản.
- Terms và Privacy là nội dung public; sơ đồ không thêm một Policy Acceptance gate từ draft Member Authentication.

## 3. A. Member Dashboard

### A1. Retail, hồ sơ, loyalty và tư vấn

```mermaid
flowchart LR
    A["A. Member Dashboard"]
    Catalogue["Product Catalogue"]
    Product["Product Details"]
    Cart["Shopping Cart"]
    Checkout["Retail Checkout"]
    Payment["Retail Payment"]
    VNPay[["VNPay"]]
    Result["Payment Result"]
    Orders["My Retail Orders"]
    Details["Retail Order Details"]
    Method(["Fulfilment Selection — form/tab"])
    Recipient(["GHTK Recipient Details — form"])
    Profile["Personal Profile"]
    Edit(["Edit Profile — form"])
    Password(["Change Password — form"])
    Loyalty["My Loyalty"]
    History(["Point History — tab"])
    Chat["Staff Consultation"]
    AI(["Basic AI Chat — chatbox"])

    A --> Catalogue --> Product
    Product -->|"Chọn configured variant; Add to Cart"| Cart
    A --> Cart
    Cart -->|"Cart không rỗng"| Checkout
    Checkout -->|"Chọn branch; review toàn quote; chấp nhận hợp lệ"| Payment
    Checkout -->|"Invalid line / changed quote: review và submit lại"| Cart
    Payment --> VNPay -->|"Browser return"| Result
    Result -->|"Hiển thị payment facts đã được xác minh"| Details
    A --> Orders --> Details
    Details -->|"Payment còn đủ điều kiện; deadline gốc"| Payment
    Details -->|"Verified paid; trước preparing"| Method
    Method -->|"Pickup; lưu lựa chọn"| Details
    Method -->|"GHTK"| Recipient
    Recipient -->|"Validate và lưu"| Details

    A --> Profile --> Edit
    Edit -->|"Lưu hợp lệ / hủy edit"| Profile
    A --> Password
    Profile --> Password
    Password -->|"Hoàn tất hoặc quay lại"| A
    A --> Loyalty --> History
    History --> Loyalty
    Loyalty -->|"Dùng points trong eligible checkout"| Cart
    A --> Chat
    A --> AI
```

- Retail Checkout revalidate toàn cart, hiển thị branch/variant/quantity/prices/benefits/payable trước khi chấp nhận. Không tạo partial order.
- Cart chỉ giữ giá tham khảo. Accepted order giữ frozen terms; payment link 10 phút và hold 15 phút dùng deadline gốc, không kéo dài khi mở lại màn.
- Fulfilment Selection chỉ mở sau verified paid và trước preparing. Chọn GHTK yêu cầu recipient data; preparation khóa việc sửa phương thức/dữ liệu. Không đổi processing branch qua form này.
- Order Details hiển thị payment, hold và fulfilment riêng; không có courier tracking. Payment pending/expired/exception là trạng thái của màn, không phải đường tắt tới fulfilment.
- Profile chỉ của Member hiện tại. Password form chỉ áp dụng cho credential đủ điều kiện; SSO-managed credentials được quản lý tại provider. Consultation là văn bản, không bắt buộc gắn booking/order.

### A2. Workshop và booking-design image

```mermaid
flowchart LR
    A["A. Member Dashboard"]
    Packages["Workshop Package List"]
    Package["Workshop Package Details"]
    Booking["Workshop Booking Form"]
    Models["Available Ring Models"]
    Config["Workshop Ring Configurator"]
    Image["Booking Design Image"]
    Analysis(["Image Analysis Result — bước trong form"])
    Decision["Booking Design Decision / Review Status"]
    Review(["Staff / Manager review — chuyển xử lý theo thẩm quyền"])
    Deposit["Workshop Deposit Payment"]
    VNPay[["VNPay"]]
    Result["Payment Result"]
    Mine["My Bookings"]
    Details["Booking Details / QR Ticket"]

    A --> Packages --> Package --> Booking
    Booking -->|"Chọn mẫu"| Models -->|"Xác nhận hợp lệ"| Booking
    Booking -->|"Tự cấu hình trong options hỗ trợ"| Config -->|"Xác nhận hợp lệ"| Booking
    Booking -->|"Upload image; chỉ Member"| Image
    Image -->|"File/context hợp lệ; xử lý phân tích"| Analysis
    Analysis -->|"Hiển thị kết quả và routing hiện có"| Decision
    Analysis -.->|"Nhánh cần human review"| Review
    Review -.->|"Ghi quyết định; Member xem kết quả"| Decision
    Decision -->|"Thiết kế hợp lệ; tiếp tục booking"| Booking
    Decision -->|"Rejected; chọn phương án khác"| Booking
    Booking -->|"Review summary; submit đủ điều kiện"| Deposit
    Deposit --> VNPay --> Result
    Result -->|"Đọc trạng thái xác minh"| Details
    A --> Mine -->|"Ownership hợp lệ"| Details
    Details -->|"Pending; payment/hold còn đủ điều kiện"| Deposit
    Details -->|"Bắt đầu booking mới"| Packages
```

- Ring models, configurator, booking form, payment và Booking Details được dùng lại từ public journey; Member có thêm nhánh upload image và ownership/history.
- Review pending giữ trạng thái chờ; quyết định thiết kế không tự tạo booking hoặc payment.
- Routing chi tiết của UC30 còn cần đồng bộ với business rules: Simple auto-accept và image-specific consent trong UC30 chưa được coi là quyết định thống nhất. Sơ đồ thể hiện màn kết quả/routing và nhánh cần human review, không tự chốt nhánh auto-accept hay một consent screen riêng.

### A3. Custom manufacturing

```mermaid
flowchart LR
    A["A. Member Dashboard"]
    Mine["My Custom Orders"]
    New["New Custom Order"]
    Input(["Reference Image / Manual Configuration — phần trong form"])
    Terms(["Requested Deadline / Fulfilment Data — phần trong form"])
    Deposit["Custom Deposit Payment"]
    GatewayDeposit[["VNPay — deposit"]]
    DepositResult["Payment Result — deposit"]
    Details["Custom Order Details"]
    Balance["Custom Balance Payment"]
    GatewayBalance[["VNPay — balance"]]
    BalanceResult["Payment Result — balance"]

    A --> Mine -->|"Chọn own order"| Details
    A --> New
    Mine --> New
    New --> Input --> Terms
    Terms -->|"Submit; deadline hợp lệ và queue còn capacity"| Deposit
    Terms -->|"Deadline / capacity / dữ liệu không hợp lệ"| New
    Deposit --> GatewayDeposit --> DepositResult
    DepositResult -->|"Hiển thị trạng thái xác minh"| Details
    Details -->|"Final balance đã được ghi; đủ điều kiện thanh toán"| Balance
    Balance --> GatewayBalance --> BalanceResult
    BalanceResult -->|"Hiển thị trạng thái xác minh"| Details
```

- Custom manufacturing là journey riêng; AI không định giá, approve/reject hoặc quyết định feasibility của đơn này.
- Member chọn phương thức nhận khi submit custom order. Deposit bằng 50% wax-package. Final balance là remaining 50% cộng actual surcharges do Staff/Manager ghi khi ready.
- Deposit queue hold tuân thủ payment contract; sau successful deposit, queue slot tiếp tục được giữ tới actual pickup/handoff. Final-balance payment không được vẽ thành thao tác tạo một queue reservation mới.
- Member xem trạng thái và kết quả fulfilment trong Details; việc ghi actual pickup/handoff thuộc shop-side operations.

## 4. B. Staff Dashboard

### B1. Product management

```mermaid
flowchart LR
    B["B. Staff Dashboard"]
    List["Product Management List"]
    New["New Product"]
    Details["Product Management Details"]
    General(["General Information — tab"])
    Images(["Images — tab"])
    Variants(["Variants and Quantity — tab"])
    Pricing(["Pricing — tab"])
    Proposal(["Price Proposal — form"])
    ManagerReview["Price Proposal Review — Manager"]
    Apply(["Apply Approved Price — form/action"])
    Publication(["Publish / Unpublish — confirmation"])

    B --> List
    List --> New -->|"Validate; lưu unpublished"| Details
    List --> Details
    Details --> General -->|"Lưu hợp lệ"| Details
    Details --> Images -->|"Lưu hợp lệ"| Details
    Details --> Variants -->|"Lưu hợp lệ"| Details
    Details --> Pricing --> Proposal
    Proposal -->|"Ghi proposal riêng"| Pricing
    Proposal -.->|"Chuyển xử lý; Staff không mở quyền approve"| ManagerReview
    ManagerReview -.->|"Approval khớp terms; không tự apply"| Pricing
    Pricing -->|"Có approval hợp lệ"| Apply -->|"Áp dụng giá mới"| Details
    Details --> Publication -->|"Quyền và prerequisites hợp lệ"| Details
```

Giá proposal, approval, application và publication là các hành động riêng. Used variant options và frozen purchase history không bị viết lại; quantity edit không được xâm phạm active holds. Publish không tự phát sinh từ save hoặc approval.

### B2. Workshop, design review, retail/custom operations và consultation

```mermaid
flowchart LR
    B["B. Staff Dashboard"]
    Sessions["Workshop Session List"]
    Session["Workshop Session Details"]
    Checkin(["Participant Check-in — QR/manual form"])
    CheckResult(["Check-in Result — trạng thái trong form"])
    Designs["Booking Design Review List"]
    Design["Booking Design Review Details"]
    DesignDecision(["Record Design Decision — form"])
    Orders["Retail Order List"]
    Order["Retail Order Operations"]
    Preparing(["Start Preparing — confirmation"])
    Readiness(["Update Readiness — form"])
    Pickup(["Record Customer Pickup — form"])
    Carrier(["Record Actual GHTK Handoff — form"])
    Customs["Custom Order List"]
    Custom["Custom Order Operations"]
    Final(["Set Final Amount — form"])
    CustomHandoff(["Custom Pickup / GHTK Handoff — form"])
    Inbox["Consultation Inbox"]
    Chat["Consultation Conversation"]

    B --> Sessions --> Session --> Checkin
    Checkin -->|"Confirmed booking; đúng session; xác nhận có mặt"| CheckResult
    CheckResult -->|"Check-in tiếp / sửa lookup"| Checkin
    Checkin -->|"Quay lại"| Session
    B --> Designs -->|"Đúng phạm vi Staff; estimate không quá 3 triệu VND"| Design
    Design --> DesignDecision -->|"Lưu kết quả theo quyền; không tạo booking/payment"| Design

    B --> Orders -->|"Assigned branch"| Order
    Order -->|"Verified paid; method/data hợp lệ"| Preparing
    Preparing -->|"Ghi preparing; khóa owner edits"| Order
    Order -->|"Đang preparing; đúng method"| Readiness
    Readiness -->|"Ghi ready_for_pickup hoặc prepared_for_carrier"| Order
    Order -->|"Ready pickup; xác minh owning Member và actual handover"| Pickup
    Pickup -->|"Ghi picked_up"| Order
    Order -->|"Prepared for carrier; actual handoff"| Carrier
    Carrier -->|"Ghi handed_to_carrier và evidence"| Order

    B --> Customs -->|"Phạm vi xử lý được phép"| Custom
    Custom -->|"Sản phẩm ready; có quyền ghi balance"| Final
    Final -->|"Lưu balance; gửi payment request"| Custom
    Custom -->|"Đủ điều kiện fulfilment và verified final payment"| CustomHandoff
    CustomHandoff -->|"Ghi bàn giao thực tế"| Custom
    B --> Inbox --> Chat
    Chat -->|"Quay lại danh sách hội thoại"| Inbox
```

- Retail không có manual paid override, skip/backward transition hoặc courier delivery status.
- Pickup yêu cầu owning Member đăng nhập và mở đúng order tại quầy cùng actual handover; screenshot/reference đơn lẻ không đủ.
- Prepared for carrier khác actual handoff. GHTK chỉ được ghi nhận bàn giao thủ công, không có API/fee quote/tracking.
- Các màn session/list/custom operations là cách gom điều hướng đề xuất; dữ liệu và hành động phải giới hạn theo thẩm quyền đã xác định, không suy ra quyền đọc toàn hệ thống.

## 5. C. Manager Dashboard

```mermaid
flowchart LR
    C["C. Manager Dashboard"]
    Revenue["Executive Revenue Dashboard"]
    Filters(["Period / Branch Filters — controls"])
    Schedule["Workshop Schedule"]
    Session["Workshop Session Details"]
    SessionForm["Workshop Session Form — new/edit"]
    Assignment(["Staff Assignment — tab/form"])
    Settings["Workshop Configuration"]
    Slots(["Time Slots — tab"])
    Capacity(["Capacity — tab"])
    Materials(["Branch Materials and Options — tab"])
    Holidays(["Holiday and Off-day Exceptions — tab"])
    Designs["Booking Design Review List"]
    Design["Booking Design Review Details"]
    DesignDecision(["Record Design Decision — form"])
    Proposals["Price Proposal List"]
    Proposal["Price Proposal Review"]
    Suggestion(["AI Price Suggestion — advisory panel"])
    Promotions["Promotion List"]
    Promotion["Promotion Details / Form"]
    Vouchers["Voucher List"]
    Voucher["Voucher Details / Form"]
    Queue["Custom Queue Configuration"]
    Customs["Custom Order List"]
    Custom["Custom Order Operations"]
    Final(["Set Final Amount — form"])

    C --> Revenue --> Filters -->|"Chọn scope hợp lệ; cập nhật dữ liệu"| Revenue
    C --> Schedule --> Session
    Schedule -->|"New session"| SessionForm
    Session -->|"Edit session"| SessionForm -->|"Validate; lưu hợp lệ"| Session
    Session --> Assignment -->|"Validate Staff/overlap; lưu"| Session
    Session -->|"Quay lại lịch"| Schedule
    C --> Settings
    Settings --> Slots -->|"Lưu / quay lại"| Settings
    Settings --> Capacity -->|"Lưu / quay lại"| Settings
    Settings --> Materials -->|"Lưu / quay lại"| Settings
    Settings --> Holidays -->|"Lưu / quay lại"| Settings
    C --> Designs -->|"Phạm vi Manager; estimate trên 3 triệu VND"| Design
    Design --> DesignDecision -->|"Lưu quyết định"| Design
    C --> Proposals --> Proposal
    Proposal -->|"Yêu cầu suggestion khi cần và đủ inputs"| Suggestion
    Suggestion -->|"Tham khảo; quyết định thuộc Manager"| Proposal
    Proposal -->|"Ghi quyết định; retail chưa tự apply/publish"| Proposals
    C --> Promotions --> Promotion -->|"Validate; lưu / đổi activation hợp lệ"| Promotions
    C --> Vouchers --> Voucher -->|"Validate; lưu / đổi activation hợp lệ"| Vouchers
    C --> Queue
    C --> Customs -->|"Phạm vi được phép"| Custom
    Custom -->|"Ready; quyền ghi final amount"| Final -->|"Lưu; gửi payment request"| Custom
```

- Revenue Dashboard chỉ hiển thị dữ liệu trong period/branch scope được phép; dữ liệu thiếu/chậm phải được đánh dấu.
- Workshop settings và schedule edits bảo vệ bookings/holds hiện có. Enabled Klook synchronization là xử lý hệ thống; có thể hiển thị trạng thái liên quan trong workshop screens, không thêm màn cancellation hoặc reconciliation.
- AI price suggestion hỗ trợ giá package/product, không phải giá custom manufacturing. Với retail, Manager approval vẫn cần authorized maintainer apply riêng và publication riêng.
- Product maintenance và retail-order operations của Manager chỉ mở khi có explicit applicable delegation. Khi có quyền, dùng lại màn B tương ứng; Dashboard không mặc định cấp các quyền này.

## 6. D. Admin Dashboard

```mermaid
flowchart LR
    D["D. Admin Dashboard"]
    Accounts["User Account List"]
    New["New User / Account Form"]
    Account["User Account Details"]
    Edit(["Edit Account — form"])
    Role(["Assign Role — confirmation"])
    Lock(["Lock / Unlock Account — confirmation"])
    RBAC["Role and Permission Configuration"]
    Parameters["Operational Parameters"]
    Queue["Custom Queue Configuration"]
    Integrations["Integration List"]
    Integration["Integration Details"]
    Test(["Safe Connection / Credential Test — action"])
    Support["Technical Order Support"]
    Diagnostics(["Minimized Technical Diagnostics — result panel"])

    D --> Accounts
    Accounts --> New -->|"Validate; tạo account hợp lệ"| Account
    Accounts --> Account
    Account --> Edit -->|"Lưu thông tin được phép"| Account
    Account --> Role -->|"Kiểm tra quyền; xác nhận riêng"| Account
    Account --> Lock -->|"Kiểm tra quyền; xác nhận riêng"| Account
    D --> RBAC
    RBAC -->|"Validate policy; xác nhận và lưu"| RBAC
    D --> Parameters
    Parameters -->|"Validate tập giá trị; xác nhận và lưu"| Parameters
    Parameters --> Queue -->|"Lưu / quay lại"| Parameters
    D --> Integrations --> Integration
    Integration -->|"Khi được hỗ trợ; không tạo business transaction"| Test
    Test -->|"Hiển thị kết quả an toàn"| Integration
    Integration -->|"Validation hợp lệ; secure save; masked result"| Integrations
    D --> Support -->|"Reference và mục đích hỗ trợ hợp lệ"| Diagnostics
    Diagnostics -->|"Tra cứu tiếp"| Support
```

- Admin không có đường vào Revenue Dashboard, quyền xác nhận thanh toán hay business approval mặc định.
- Assign Role và Lock/Unlock là hành động riêng; Edit Account không âm thầm thay đổi role/status. RBAC chỉ thay đổi các permission được hỗ trợ, không tạo quyền nghiệp vụ chưa được duyệt.
- Integration Details che secrets và không hiển thị credentials trong logs/errors.
- Technical Support chỉ hiển thị internal order/attempt reference, branch code, timestamps, states và sanitized technical codes. Không hiển thị cart/product/options hoặc customer identity/contact/address/notes, không impersonation hoặc business mutation.

## 7. Hành vi chung

- Session hết hạn dẫn về User Login; dữ liệu bảo vệ chỉ được đọc lại sau khi kiểm tra quyền. Tài khoản inactive/locked hoặc không đủ quyền nhận kết quả từ chối an toàn.
- Validation/save failure giữ trạng thái đã lưu; edit xung đột yêu cầu tải lại/review. Empty/error/pending là trạng thái trong màn, không được hiển thị như success.
- Browser return không xác nhận payment. IPN/eligible QueryDR là xử lý phía hệ thống; kết quả được đọc trong Payment Result và booking/order details. Không tạo màn IPN, QueryDR hoặc payment reconciliation.
- Email thất bại không đảo ngược một payment/booking/order hợp lệ. Email links mở màn chi tiết với đúng access checks; không thêm Notification Center.
- Logout là hành động trong account menu của cả bốn Dashboard, kết thúc phiên và về Home/Login. Không cần một trang Logout riêng.
- Dữ liệu recipient và media hiển thị theo chính sách retention; không lộ lại dữ liệu đã đến hạn retire qua màn details hoặc technical support.

## 8. Căn cứ và điểm cần đồng bộ

- [Use-case inventory](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md), [Actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md), [Permission Matrix](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/04-permission-matrix.md).
- [Product Management](../documents/docs/report-3-software-requirement-specification/sections/04-ii-use-case-specifications/02-product-management/00-overview.md), [Workshop Management](../documents/docs/report-3-software-requirement-specification/sections/04-ii-use-case-specifications/03-workshop-management/00-overview.md), [Order Management](../documents/docs/report-3-software-requirement-specification/sections/04-ii-use-case-specifications/08-order-management-and-fulfillment/00-overview.md).
- [Business rules](../documents/docs/report-3-software-requirement-specification/sections/07-v-requirement-appendix/01-business-rules.md), [V1 scope](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md).
- Quyết định của người dùng cho sơ đồ này: dùng bốn tên Dashboard, không đưa các chức năng bổ sung trong draft Member Authentication vào flow, Admin không được xem Revenue Dashboard. Permission Matrix còn ghi Admin Full cho executive dashboard và cần được đồng bộ trong một thay đổi tài liệu riêng.
- UC30 còn khác business rules/scope ở image-specific consent và Simple auto-accept; sơ đồ không tự giải quyết mâu thuẫn đó. Những quyền override hoặc routing khác chưa được chốt không được thêm thành đường đi mặc định.
- Tên UI **Price Proposal Review** diễn đạt đúng phạm vi product/package của UC31. **Prepared for carrier** và **actual carrier handoff** được thể hiện riêng để tránh nhầm tên UC53 với sự kiện bàn giao thực tế.
