# M.land customer journey prototype

Prototype tĩnh cho hành trình khách V1: booking workshop làm nhẫn, chọn ý tưởng nhẫn, trạng thái feasibility/estimate minh hoạ và tra cứu bằng mã demo.

## Chạy prototype

Mở `index.html` trực tiếp trong browser, hoặc từ thư mục `frontend/` chạy một static server, ví dụ:

```powershell
python -m http.server 8000
```

Sau đó mở `http://localhost:8000`.

## Giới hạn có chủ đích

- Không có backend, API, authentication, email thật, payment, AI Vision API hoặc staff console.
- Toàn bộ session, catalogue, availability, estimate và trạng thái trong UI là **minh hoạ**, không phải dữ liệu hay quy tắc đã được M.land xác nhận.
- `localStorage` chỉ lưu ngôn ngữ, lựa chọn demo và mã tracking. Email, tên, nội dung mô tả và ảnh tham khảo không được lưu hoặc gửi đi.
- Preview ảnh custom chỉ tồn tại trong tab hiện tại. Không có request mạng để upload ảnh hoặc gọi AI.
- V1 frontend chỉ hiển thị hành trình nhẫn. Ảnh workshop không xác nhận catalogue, giá, chất liệu hay availability.

## Assets

Hai ảnh trong `assets/` là ảnh workshop thực tế được chọn làm visual reference cục bộ. Chúng được sao chép từ `local-notes/` để prototype chạy độc lập; không dùng đường dẫn đến thư mục note bị Git ignore.
