-- V188: Add at least 10 band shape options with zero surcharge (0 VND),
-- fix all gemstone prices at 50,000 VND, and add new gemstone varieties.

-- 1. Ensure all ring shapes have price_adjustment = 0.00 and are active
UPDATE ring_shapes
SET price_adjustment = 0.00, is_active = TRUE;

-- 2. Insert new band shapes (bringing total to 13 distinct band profiles, all free of charge)
INSERT INTO ring_shapes (shape_code, shape_name, description, price_adjustment, currency, difficulty_score, is_active)
VALUES
('SHP-KNIFE-EDGE', 'Đai sống dao vát nhọn (Knife Edge)', 'Đai nhẫn vát đỉnh sắc sảo hình sống dao, tạo góc phản chiếu ánh kim độc đáo', 0.00, 'VND', 1.4000, TRUE),
('SHP-D-SHAPE', 'Đai chữ D truyền thống (Traditional D-Shape)', 'Mặt ngoài vòm tròn cổ điển, mặt trong phẳng ôm sát ngón tay tạo cảm giác chắc chắn', 0.00, 'VND', 1.1000, TRUE),
('SHP-BRAIDED', 'Đai bện xoắn dây thừng (Braided Rope)', 'Đai đan bện xoắn dây thừng thủ công tượng trưng cho sự gắn kết son sắt bền chặt', 0.00, 'VND', 2.2000, TRUE),
('SHP-MATTE-BEVEL', 'Đai vát cạnh hiện đại (Beveled Edge)', 'Bản đai phẳng góc cạnh với hai đường vát xiên tinh tế, phong cách công thái học', 0.00, 'VND', 1.3000, TRUE),
('SHP-CONCAVE', 'Đai lòng máng cong lõm (Concave Band)', 'Bề mặt uốn cong lòng máng mềm mại tạo điểm nhấn thanh mảnh và hiện đại', 0.00, 'VND', 1.5000, TRUE)
ON DUPLICATE KEY UPDATE
    shape_name = VALUES(shape_name),
    description = VALUES(description),
    price_adjustment = 0.00,
    difficulty_score = VALUES(difficulty_score),
    is_active = TRUE;

-- 3. Fix all gemstones at 50,000 VND
UPDATE ring_gemstones
SET unit_price = 50000.00;

-- 4. Add new gemstone varieties (all 50,000 VND each, max 10)
INSERT INTO ring_gemstones (gemstone_code, gemstone_name, description, gemstone_type, color, cut_name, unit_price, currency, difficulty_score, max_quantity, is_active)
VALUES
('GEM-CZ-EMERALD-2MM', 'Đá CZ Xanh Lục Bảo 2mm (Emerald Green)', 'Đá CZ xanh lục ngọc bích sang trọng, biểu tượng của sự may mắn và thịnh vượng', 'CZ', 'Xanh lục bảo', 'Round Cut', 50000.00, 'VND', 0.8000, 10, TRUE),
('GEM-CZ-PURPLE-2MM', 'Đá CZ Tím Thạch Anh 2mm (Amethyst Purple)', 'Sắc tím thạch anh kiêu sa, tượng trưng cho tình yêu chung thủy', 'CZ', 'Tím thạch anh', 'Round Cut', 50000.00, 'VND', 0.8000, 10, TRUE),
('GEM-CZ-BLACK-2MM', 'Đá CZ Đen Huyền Bí 2mm (Black Spinel)', 'Sắc đen huyền bí, mạnh mẽ và đầy cá tính', 'CZ', 'Đen huyền', 'Round Cut', 50000.00, 'VND', 0.8000, 10, TRUE),
('GEM-CZ-PINK-2MM', 'Đá CZ Hồng Ngọt Ngào 2mm (Pink Morganite)', 'Sắc hồng pastel ngọt ngào, lãng mạn', 'CZ', 'Hồng pastel', 'Round Cut', 50000.00, 'VND', 0.8000, 10, TRUE),
('GEM-CZ-YELLOW-2MM', 'Đá CZ Vàng Hổ Phách 2mm (Citrine Yellow)', 'Sắc vàng ấm áp như ánh nắng ban mai, thu hút năng lượng tích cực', 'CZ', 'Vàng hổ phách', 'Round Cut', 50000.00, 'VND', 0.8000, 10, TRUE),
('GEM-CZ-CHAMPAGNE-2MM', 'Đá CZ Champagne Quý Phái 2mm', 'Tone màu champagne ánh kim trang nhã và thanh lịch', 'CZ', 'Vàng champagne', 'Round Cut', 50000.00, 'VND', 0.8000, 10, TRUE)
ON DUPLICATE KEY UPDATE
    gemstone_name = VALUES(gemstone_name),
    description = VALUES(description),
    gemstone_type = VALUES(gemstone_type),
    color = VALUES(color),
    cut_name = VALUES(cut_name),
    unit_price = 50000.00,
    difficulty_score = VALUES(difficulty_score),
    max_quantity = VALUES(max_quantity),
    is_active = TRUE;
