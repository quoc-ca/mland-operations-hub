-- Flyway Migration V3: Sample Data for Development

INSERT INTO workshop_packages (id, name, description, price, currency, deposit_percent, min_participants, max_participants, supported_options, status) 
VALUES 
(1, 'Workshop Chế Tác Nhẫn Bạc Cơ Bản (Basic Silver Ring Workshop)', 'Trải nghiệm tự tay đo ni, cưa, uốn, hàn, dập vân và mài bóng chiếc nhẫn bạc 925 thủ công mang về ngay trong ngày.', 750000, 'VND', 50, 1, 20, '["Dụng cụ chế tác", "Bạc 925 bao gồm", "Hộp đựng nhẫn"]', 'PUBLISHED'),
(2, 'Workshop Chế Tác Nhẫn Bằng Sáp (Wax Carving Ring Workshop)', 'Trải nghiệm điêu khắc tạo mẫu nhẫn bằng sáp phôi thủ công, đúc khuôn và hoàn thiện chiếc nhẫn theo phong cách độc bản.', 900000, 'VND', 50, 1, 15, '["Sáp phôi đúc", "Hướng dẫn tạo hình", "Hộp đựng cao cấp"]', 'PUBLISHED'),
(3, 'Workshop Chế Tác Nhẫn Đôi Kỷ Niệm (Couple Ring Workshop)', 'Trải nghiệm chế tác cặp nhẫn đôi kỷ niệm cho 2 người, hỗ trợ khắc chữ và hoàn thiện sản phẩm.', 1400000, 'VND', 50, 1, 10, '["Khắc chữ miễn phí", "Bộ 2 nhẫn bạc"]', 'DRAFT');

INSERT INTO workshop_sessions (id, package_id, session_date, location, capacity, reserved_participants, status)
VALUES
(1, 1, DATEADD('DAY', 7, CURRENT_DATE), 'Mland Atelier - Studio 1 (Tầng 1)', 20, 0, 'OPEN'),
(2, 1, DATEADD('DAY', 14, CURRENT_DATE), 'Mland Atelier - Studio 1 (Tầng 1)', 20, 0, 'OPEN'),
(3, 2, DATEADD('DAY', 5, CURRENT_DATE), 'Mland Atelier - Studio 2 (Tầng 2)', 15, 0, 'OPEN'),
(4, 2, DATEADD('DAY', 12, CURRENT_DATE), 'Mland Atelier - Studio 2 (Tầng 2)', 15, 0, 'OPEN');

