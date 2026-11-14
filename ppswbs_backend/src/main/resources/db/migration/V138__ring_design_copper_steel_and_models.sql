-- V120: Deactivate Gold, Add Copper & Stainless Steel materials, new shapes and 10+ catalogue models for workshop ring design

-- 1. Deactivate Gold materials
UPDATE ring_materials 
SET is_active = FALSE 
WHERE material_code IN ('MAT-GOLD-14K', 'MAT-ROSE-GOLD-14K', 'MAT-WHITE-GOLD-14K');

-- 2. Add Copper (Đồng) and Stainless Steel (Thép không gỉ)
INSERT INTO ring_materials (material_code, material_name, description, purity, unit_price, currency, difficulty_score, is_active)
VALUES
('MAT-COPPER', 'Đồng Đỏ / Đồng Thau (Copper)', 'Đồng nguyên chất vintage, mang tone màu ấm áp cổ điển và nét mộc mạc tự nhiên', '99.9%', 150000.00, 'VND', 1.0000, TRUE),
('MAT-STAINLESS-STEEL', 'Thép Không Gỉ (Stainless Steel 316L)', 'Thép y tế 316L siêu bền bỉ, không gỉ sét, chống trầy xước và an toàn cho mọi loại da', 'Inox 316L', 180000.00, 'VND', 1.2000, TRUE)
ON DUPLICATE KEY UPDATE 
    material_name = VALUES(material_name), 
    description = VALUES(description), 
    purity = VALUES(purity), 
    unit_price = VALUES(unit_price), 
    is_active = TRUE;

-- 3. Add new shapes
INSERT INTO ring_shapes (shape_code, shape_name, description, price_adjustment, currency, difficulty_score, is_active)
VALUES
('SHP-SINGLE-TWIST', 'Dáng xoắn đơn (Single Twist)', 'Đai nhẫn xoắn nhẹ 1 điểm tạo điểm nhấn thanh lịch và tinh tế', 80000.00, 'VND', 1.4000, TRUE),
('SHP-WAVE', 'Dáng lượn sóng (Ocean Wave)', 'Đai nhẫn uốn lượn hình con sóng mềm mại, phong cách tự do và phóng khoáng', 100000.00, 'VND', 1.6000, TRUE),
('SHP-BALL-PEEN', 'Bề mặt búa bi (Ball-peen Texture)', 'Kỹ thuật gõ búa bi thủ công tạo vô số vết lõm tròn li ti lấp lánh như bầu trời sao', 90000.00, 'VND', 1.8000, TRUE),
('SHP-FLAT-BAND', 'Đai phẳng công thái học (Flat Ergonomic)', 'Thiết kế bản phẳng vuông vức góc cạnh hiện đại, phong cách tối giản Bắc Âu', 60000.00, 'VND', 1.3000, TRUE)
ON DUPLICATE KEY UPDATE 
    shape_name = VALUES(shape_name), 
    description = VALUES(description), 
    price_adjustment = VALUES(price_adjustment), 
    difficulty_score = VALUES(difficulty_score), 
    is_active = TRUE;

-- 4. Deactivate old Gold catalogue models
UPDATE ring_catalogue_models 
SET is_active = FALSE 
WHERE material_reference IN ('MAT-GOLD-14K', 'MAT-ROSE-GOLD-14K', 'MAT-WHITE-GOLD-14K');

-- 5. Seed at least 10 active catalogue models for workshop
INSERT INTO ring_catalogue_models (model_code, package_reference, name, description, material_reference, shape_reference, default_components, img_url, is_active)
VALUES
('MOD-WS-SILVER-CLASSIC', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Tròn Cổ Điển', 'Thiết kế nhẫn tròn cổ điển thanh lịch, hoàn hảo cho buổi trải nghiệm chế tác đầu tiên', 'MAT-SILVER-925', 'SHP-CLASSIC-ROUND', '{"components": [], "attachments": []}', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e', TRUE),
('MOD-WS-SILVER-TWIST', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Xoắn Vô Cực', 'Thiết kế nhẫn đai xoắn mềm mại, điểm xuyết một viên đá CZ trắng tinh tế', 'MAT-SILVER-925', 'SHP-TWISTED', '{"components": [{"reference": "GEM-CZ-WHITE-2MM", "quantity": 1, "placement": "CENTER"}], "attachments": []}', 'https://images.unsplash.com/photo-1603561591411-07134e71a2a9', TRUE),
('MOD-WS-SINGLE-TWIST', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Xoắn Đơn Tối Giản', 'Đai nhẫn xoắn nhẹ một vòng thanh thoát, mang vẻ đẹp hiện đại và trẻ trung', 'MAT-SILVER-925', 'SHP-SINGLE-TWIST', '{"components": [], "attachments": []}', 'https://images.unsplash.com/photo-1603561591411-07134e71a2a9', TRUE),
('MOD-WS-WAVE-FLOW', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Lượn Sóng Đại Dương', 'Đường nét uốn lượn mềm mại như sóng biển, đính kèm 2 viên đá CZ xanh lam nổi bật', 'MAT-SILVER-925', 'SHP-WAVE', '{"components": [{"reference": "GEM-CZ-BLUE-2MM", "quantity": 2, "placement": "ACCENT_LEFT"}], "attachments": []}', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e', TRUE),
('MOD-WS-BALL-HAMMERED', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Búa Bi Điểm Lỗ Tròn', 'Bề mặt được gõ thủ công bằng đầu búa bi tạo hiệu ứng vô số lỗ tròn nhỏ phản chiếu ánh sáng lấp lánh', 'MAT-SILVER-925', 'SHP-BALL-PEEN', '{"components": [], "attachments": []}', 'https://images.unsplash.com/photo-1598560917505-59a3ad559071', TRUE),
('MOD-WS-FACET-HAMMERED', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Giác Búa Cắt Thô', 'Bề mặt giác cắt góc cạnh mạnh mẽ, cá tính và đậm chất xưởng rèn thủ công', 'MAT-SILVER-925', 'SHP-HAMMERED', '{"components": [], "attachments": []}', 'https://images.unsplash.com/photo-1598560917505-59a3ad559071', TRUE),
('MOD-WS-COPPER-RUSTIC', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Đồng Mộc Vintage', 'Chất liệu đồng ấm áp, đai tròn bo viền êm ái, phù hợp nhẫn đôi phong cách hoài niệm', 'MAT-COPPER', 'SHP-CLASSIC-ROUND', '{"components": [], "attachments": []}', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e', TRUE),
('MOD-WS-COPPER-TWIST', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Đồng Xoắn Nghệ Thuật', 'Đồng uốn xoắn mềm mại kết hợp đính đá CZ đỏ ruby nổi bật trên nền ánh đồng', 'MAT-COPPER', 'SHP-SINGLE-TWIST', '{"components": [{"reference": "GEM-CZ-RED-2MM", "quantity": 1, "placement": "CENTER"}], "attachments": []}', 'https://images.unsplash.com/photo-1603561591411-07134e71a2a9', TRUE),
('MOD-WS-STEEL-BRUSHED', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Thép Phay Xước Hiện Đại', 'Thép không gỉ 316L siêu bền, bề mặt phay xước satin mờ chống trầy và phong cách tối giản', 'MAT-STAINLESS-STEEL', 'SHP-COMFORT-FIT', '{"components": [], "attachments": [{"reference": "ATT-SATIN-FINISH", "quantity": 1, "placement": "OUTER_SURFACE"}]}', 'https://images.unsplash.com/photo-1598560917505-59a3ad559071', TRUE),
('MOD-WS-STEEL-MINIMAL', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Thép Đai Phẳng Tối Giản', 'Thiết kế bản phẳng công thái học thanh lịch, phong cách tối giản hiện đại', 'MAT-STAINLESS-STEEL', 'SHP-FLAT-BAND', '{"components": [], "attachments": []}', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e', TRUE),
('MOD-WS-MILGRAIN-VINTAGE', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Viền Bi Milgrain Cổ Điển', 'Hàng viền hạt bi milgrain tinh xảo hai bên mép đai, kết hợp viên đá Moissanite sang trọng', 'MAT-SILVER-925', 'SHP-CLASSIC-ROUND', '{"components": [{"reference": "GEM-MOISSANITE-3MM", "quantity": 1, "placement": "CENTER"}], "attachments": [{"reference": "ATT-BEAD-EDGE", "quantity": 1, "placement": "EDGES"}]}', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e', TRUE),
('MOD-WS-HAMMERED-HEART', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Búa Gõ Khắc Tim Chìm', 'Bề mặt búa gõ mộc mạc kết hợp dập chìm họa tiết trái tim nhỏ đầy ý nghĩa', 'MAT-SILVER-925', 'SHP-HAMMERED', '{"components": [], "attachments": [{"reference": "ATT-HEART-EMBOSS", "quantity": 1, "placement": "OUTER_SURFACE"}]}', 'https://images.unsplash.com/photo-1598560917505-59a3ad559071', TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    material_reference = VALUES(material_reference),
    shape_reference = VALUES(shape_reference),
    default_components = VALUES(default_components),
    img_url = VALUES(img_url),
    is_active = TRUE;

