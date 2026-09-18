# Artisanal Atelier customer-journey prototype

Prototype thuần HTML, CSS và JavaScript cho hành trình workshop nhẫn và ready-ring retail. Đây là công cụ review UX, không phải ứng dụng production.

## Chạy

Mở `index.html` trực tiếp trong browser, hoặc chạy static server từ thư mục này:

```powershell
python -m http.server 8000
```

Sau đó mở `http://localhost:8000`.

## Luồng mô phỏng

- Guest/Member booking: chọn cơ sở demo → ngày/ba ca → package → thông tin liên hệ → có/không thiết kế → cọc demo.
- Thiết kế: mẫu minh hoạ, configurator component giả lập, hoặc ảnh tham khảo với consent, preview cục bộ và limit 5 lần/60 giây.
- Member: đăng ký/đăng nhập UI-only, history demo và ready-ring retail.
- Retail: Guest gate → Member checkout → full-payment demo → pickup hoặc carrier handoff.
- Tra cứu booking: mã booking cùng contact validation demo; QR chỉ xuất hiện sau booking `confirmed`.

## Quy tắc demo và riêng tư

- Không có backend, authentication, email, Payment Gateway, AI API, carrier API, upload hay network request.
- Payment outcome `pending`, `confirmed` và `failed` do reviewer chọn. Browser redirect không có giá trị xác nhận thanh toán.
- `localStorage` key `artisanal-atelier-prototype-v2` chỉ lưu ngôn ngữ, Member demo flag, lựa chọn không nhạy cảm, code và trạng thái demo. Email, phone, password, note và dữ liệu ảnh không được lưu.
- Ảnh tham khảo chỉ preview qua object URL trong tab hiện tại; không được gửi hay persist.
- QR là visual demo, không quét được. Booking code dạng text là mã tra cứu demo.
- `The Ring Atelier`, `The Silver Bench`, package và ready rings đều là hư cấu/nhãn demo. Ảnh workshop không xác nhận catalogue, vật liệu, capacity, availability hoặc giá bán.

## Visual system

Giao diện áp dụng hướng “Artisanal Atelier”: canvas trắng/silver, slate charcoal, champagne gold, hairline border và gallery grid. Font dùng system fallback để prototype chạy offline, không tải Google Fonts hay dependency ngoài.
