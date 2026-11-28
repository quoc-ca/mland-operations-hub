-- V209__add_7_suggested_ring_models.sql
-- Seed 7 additional suggested ring models collected from workshop input tracker
-- Linked with closest matching product images from live merchandise store

INSERT INTO ring_catalogue_models (model_code, package_reference, name, description, material_reference, shape_reference, default_components, img_url, is_active)
VALUES
('MOD-WS-CHIA-KHOANG-MAI', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Chia Khoang Mài', 'Thiết kế chia khoang mài hình học sắc nét, đai dày 2.7mm, bề mặt phẳng tối giản hiện đại', 'MAT-SILVER-925', 'SHP-FLAT-BAND', '{"components": [], "attachments": []}', '/api/v1/product-media/2b3bc822-f55d-493e-86f0-1e19263b3ad6', TRUE),
('MOD-WS-TRON-TRON-LAZER', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Tròn Trơn Khắc Lazer Vân Tay', 'Đai tròn trơn thanh thoát, dày 1.1mm, rộng 3.1mm, cá nhân hóa khắc lazer vân tay tinh xảo', 'MAT-SILVER-925', 'SHP-CLASSIC-ROUND', '{"components": [], "attachments": []}', '/api/v1/product-media/1b83213c-2617-44e2-858f-126f42177005', TRUE),
('MOD-WS-CAN-7-XOAN-KEP', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Cán 7 Xoắn Kép', 'Thiết kế xoắn kép nghệ thuật từ thanh bạc cán 7, đường nét vặn xoắn mềm mại ôm tay', 'MAT-SILVER-925', 'SHP-TWISTED', '{"components": [], "attachments": []}', '/api/v1/product-media/a419904e-f035-4623-89d2-e179e51de87d', TRUE),
('MOD-WS-DAP-BUA-GAN-TRAI-TIM', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Vân Búa Gắn Trái Tim', 'Bề mặt đập búa thủ công mộc mạc kết hợp charm dập trái tim chìm ý nghĩa, bản rộng 4.9mm', 'MAT-SILVER-925', 'SHP-HAMMERED', '{"components": [], "attachments": [{"reference": "ATT-HEART-EMBOSS", "quantity": 1, "placement": "OUTER_SURFACE"}]}', '/api/v1/product-media/ddc93f57-3e47-4778-aef3-ab9a6be038ae', TRUE),
('MOD-WS-DAP-BUA-THUONG', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Đập Búa Truyền Thống', 'Vân búa đều tay tạo hiệu ứng tán xạ ánh sáng mộc mạc, phong cách phi giới tính tối giản', 'MAT-SILVER-925', 'SHP-HAMMERED', '{"components": [], "attachments": []}', '/api/v1/product-media/88a46e90-2a00-4c5d-a70a-8c1b3dd61963', TRUE),
('MOD-WS-CUON-GAN-DA-TO', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Cuộn Uốn Gắn Đá To', 'Khung nhẫn uốn cong cuộn nhẹ tinh tế ôm trọn viên đá CZ chủ sáng lấp lánh', 'MAT-SILVER-925', 'SHP-WAVE', '{"components": [{"reference": "GEM-CZ-WHITE-2MM", "quantity": 1, "placement": "CENTER"}], "attachments": []}', '/api/v1/product-media/ae57c551-032d-4f91-a808-2f2fed7721fd', TRUE),
('MOD-WS-HAN-CHU-H', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Hàn Chữ H Kiến Trúc', 'Kết cấu hai dải đai song song liên kết cấu trúc chữ H độc đáo, dày 1.3mm, rộng 1.9mm', 'MAT-SILVER-925', 'SHP-FLAT-BAND', '{"components": [], "attachments": []}', '/api/v1/product-media/0d5cf1c7-615f-4b52-99fd-2c5a2802261b', TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    material_reference = VALUES(material_reference),
    shape_reference = VALUES(shape_reference),
    default_components = VALUES(default_components),
    img_url = VALUES(img_url),
    is_active = TRUE;
