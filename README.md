# Mland Operations Hub

**Mland Operations Hub** là hệ sinh thái phần mềm quản lý và vận hành chuyển đổi số dành riêng cho chuỗi workshop trang sức thủ công **Mland**. Hệ thống chuẩn hóa quy trình đặt lịch trực tuyến, tối ưu hóa phân ca nhân viên và quản lý vận hành đa chi nhánh theo thời gian thực.

---

## 1. Bối cảnh Doanh nghiệp (Business Context)

* **Lĩnh vực hoạt động:** Workshop hướng dẫn tự làm trang sức thủ công (chuyên về nhẫn bạc) kết hợp kinh doanh trang sức hoàn thiện.
* **Khách hàng mục tiêu:** Khách nội địa (Việt Nam) và khách du lịch quốc tế (chủ yếu từ Anh, Pháp, Đức).
* **Quy mô hoạt động:**
  * Hiện tại: 02 chi nhánh.
  * Kế hoạch mở rộng: Chi nhánh thứ 3 tại Đà Nẵng.
  * Lưu lượng: 20 – 30 khách/ngày/chi nhánh.
  * Nhân sự: Tối thiểu 2 nhân viên CSKH thường trực tại mỗi địa điểm.

---

## 2. Vấn đề & Thách thức (Problem Statement)

Quy trình vận hành hiện tại phụ thuộc nhiều vào các thao tác thủ công, gây lãng phí thời gian và tiềm ẩn sai sót:

* **Đặt lịch thủ công:** Khách phải gọi điện hoặc nhắn tin riêng lẻ; chưa có nền tảng tự động kiểm tra chỗ trống và đặt slot trực tuyến (rào cản đối với khách du lịch quốc tế).
* **Quản lý nhân sự rời rạc:** Phân ca và bàn giao ca thực hiện qua tin nhắn cá nhân, thiếu tính minh bạch và khó theo dõi trách nhiệm.
* **Thiếu khả năng giám sát tập trung:** Việc quản lý đa cơ sở chưa được đồng bộ dữ liệu, gây khó khăn cho khâu kiểm toán (audit) và đối soát doanh thu.

---

## 3. Mục tiêu Dự án (Project Goals)

* **Quản trị tập trung:** Giúp chủ doanh nghiệp theo dõi, điều phối hoạt động kinh doanh đa chi nhánh trên cùng một hệ thống.
* **Tối ưu năng suất:** Số hóa quy trình chấm công, xếp ca làm việc và bàn giao nhiệm vụ của đội ngũ nhân viên.
* **Nâng tầm trải nghiệm:** Cung cấp cổng đặt lịch trực quan, đa ngôn ngữ, tự động hóa phản hồi cho khách hàng.
* **Tính minh bạch:** Lưu trữ nhật ký hệ thống (audit logs) cho mọi giao dịch, lịch đặt và thay đổi dữ liệu phục vụ hậu kiểm.

---

## 4. Các Phân hệ Tính năng Dự kiến (Core Modules)

| Phân hệ | Tính năng chính |
| :--- | :--- |
| **Booking Engine** | Khách tự chọn chi nhánh, khung giờ, số lượng người, loại workshop; hỗ trợ giao diện đa ngôn ngữ (VI/EN). |
| **Shift Management** | Quản lý lịch làm việc, đăng ký và phân ca trực nhật tại từng chi nhánh; thông báo ca làm việc tự động. |
| **Branch Management** | Quản lý thông tin chi nhánh (Hà Nội, Đà Nẵng...), sức chứa tối đa mỗi khung giờ. |
| **Audit & Logs** | Lưu vết toàn bộ lịch sử thao tác: thay đổi lịch hẹn, phân quyền, tác vụ quản trị. |

---

## 5. Trạng thái Dự án (Project Status)

* **Giai đoạn:** `Planning & Requirements Specification` (Thu thập yêu cầu & Thiết kế hệ thống).
* **Tài liệu liên quan:** Thông số kiến trúc và mô hình dữ liệu chi tiết sẽ được cập nhật trong các phiên bản tiếp theo.
