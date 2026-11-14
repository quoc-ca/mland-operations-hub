-- V143: Update ring design configuration pricing rules:
-- 1. All gemstones cost 50,000 VND each
-- 2. Ring design shapes/models are free of charge (price_adjustment = 0.00)
-- 3. All decorative attachments cost 100,000 VND each

UPDATE ring_gemstones 
SET unit_price = 50000.00;

UPDATE ring_shapes 
SET price_adjustment = 0.00;

UPDATE ring_attachments 
SET unit_price = 100000.00;
